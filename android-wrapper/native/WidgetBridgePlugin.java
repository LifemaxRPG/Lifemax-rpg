package com.lifemax.rpg;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.util.Base64;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Hands the Life Director dial picture to the home-screen widget and reports where it is placed. */
@CapacitorPlugin(name = "WidgetBridge")
public class WidgetBridgePlugin extends Plugin {

    private static void write(File f, byte[] data) throws Exception {
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) { out.write(data); }
        if (!tmp.renameTo(f)) throw new Exception("Could not save " + f.getName());
    }

    @PluginMethod
    public void update(PluginCall call) {
        String png = call.getString("png"), json = call.getString("json");
        if (png == null || json == null) { call.reject("Missing picture"); return; }
        try {
            Context c = getContext();
            write(new File(c.getFilesDir(), "dial.png"), Base64.decode(png, Base64.DEFAULT));
            write(new File(c.getFilesDir(), "dial.json"), json.getBytes(StandardCharsets.UTF_8));
            LifeDialWidget.updateAll(c);
            call.resolve();
        } catch (Exception e) {
            call.reject(String.valueOf(e.getMessage()));
        }
    }

    @PluginMethod
    public void status(PluginCall call) {
        Context c = getContext();
        AppWidgetManager mgr = AppWidgetManager.getInstance(c);
        JSObject r = new JSObject();
        r.put("count", LifeDialWidget.ids(c).length);
        r.put("canPin", Build.VERSION.SDK_INT >= 26 && mgr.isRequestPinAppWidgetSupported());
        call.resolve(r);
    }

    @PluginMethod
    public void pin(PluginCall call) {
        Context c = getContext();
        AppWidgetManager mgr = AppWidgetManager.getInstance(c);
        JSObject r = new JSObject();
        boolean ok = false;
        if (Build.VERSION.SDK_INT >= 26 && mgr.isRequestPinAppWidgetSupported()) {
            ok = mgr.requestPinAppWidget(new ComponentName(c, LifeDialWidget.class), null, null);
        }
        r.put("requested", ok);
        call.resolve(r);
    }

    /** The screen the user asked for by tapping the widget (once). */
    @PluginMethod
    public void consumeLaunch(PluginCall call) {
        JSObject r = new JSObject();
        r.put("open", MainActivity.launchTarget);
        MainActivity.launchTarget = null;
        call.resolve(r);
    }
}
