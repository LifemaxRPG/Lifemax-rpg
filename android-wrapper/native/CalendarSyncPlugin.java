package com.lifemax.rpg;

import android.Manifest;
import android.content.ContentResolver;
import android.database.Cursor;
import android.provider.CalendarContract;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

/** Reads upcoming events from the phone's calendar (read only), so something added by Gemini or any calendar app can reach the Life Director. */
@CapacitorPlugin(
        name = "CalendarSync",
        permissions = {@Permission(strings = {Manifest.permission.READ_CALENDAR}, alias = "calendar")})
public class CalendarSyncPlugin extends Plugin {

    private boolean granted() {
        return getPermissionState("calendar") == PermissionState.GRANTED;
    }

    @PluginMethod
    public void status(PluginCall call) {
        JSObject r = new JSObject();
        r.put("granted", granted());
        call.resolve(r);
    }

    @PluginMethod
    public void request(PluginCall call) {
        if (granted()) {
            status(call);
            return;
        }
        requestPermissionForAlias("calendar", call, "permResult");
    }

    @PermissionCallback
    private void permResult(PluginCall call) {
        status(call);
    }

    /** events({from, to}) in epoch milliseconds: every visible, not cancelled instance (repeating events already expanded). */
    @PluginMethod
    public void events(PluginCall call) {
        if (!granted()) {
            call.reject("Calendar permission is not granted");
            return;
        }
        long now = System.currentTimeMillis();
        Long from = call.getLong("from", now);
        Long to = call.getLong("to", now + 14L * 86400000L);
        String[] proj = {
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
                CalendarContract.Instances.SELF_ATTENDEE_STATUS,
                CalendarContract.Instances.VISIBLE,
                CalendarContract.Instances.STATUS
        };
        JSArray out = new JSArray();
        ContentResolver cr = getContext().getContentResolver();
        try (Cursor c = CalendarContract.Instances.query(cr, proj, from, to)) {
            while (c != null && c.moveToNext() && out.length() < 400) {
                if (c.getInt(7) == 0) continue;                                            // calendar hidden by the user
                if (c.getInt(8) == CalendarContract.Events.STATUS_CANCELED) continue;
                JSObject e = new JSObject();
                e.put("id", c.getLong(0));
                e.put("title", c.isNull(1) ? "" : c.getString(1));
                e.put("start", c.getLong(2));
                e.put("end", c.getLong(3));
                e.put("allDay", c.getInt(4) == 1);
                e.put("calendar", c.isNull(5) ? "" : c.getString(5));
                e.put("declined", c.getInt(6) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED);
                out.put(e);
            }
        } catch (Exception ex) {
            call.reject("Could not read the calendar: " + ex);
            return;
        }
        JSObject r = new JSObject();
        r.put("events", out);
        call.resolve(r);
    }
}
