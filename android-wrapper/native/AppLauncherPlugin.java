package com.lifemax.rpg;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.util.Base64;
import android.view.View;
import android.view.Window;

import androidx.core.view.WindowInsetsControllerCompat;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Lets the web app list and launch other apps, and colour the system bars. */
@CapacitorPlugin(name = "AppLauncher")
public class AppLauncherPlugin extends Plugin {
    private static final int ICON_PX = 96;

    @PluginMethod
    public void getApps(final PluginCall call) {
        new Thread(() -> {
            try {
                PackageManager pm = getContext().getPackageManager();
                Intent main = new Intent(Intent.ACTION_MAIN, null);
                main.addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> found = pm.queryIntentActivities(main, 0);
                final String self = getContext().getPackageName();
                List<JSObject> rows = new ArrayList<>();
                for (ResolveInfo ri : found) {
                    String pkg = ri.activityInfo.packageName;
                    if (pkg.equals(self)) continue;
                    JSObject o = new JSObject();
                    o.put("pkg", pkg);
                    o.put("label", String.valueOf(ri.loadLabel(pm)));
                    o.put("icon", encode(ri.loadIcon(pm)));
                    rows.add(o);
                }
                Collections.sort(rows, (a, b) -> {
                    String x = a.getString("label"), y = b.getString("label");
                    return String.CASE_INSENSITIVE_ORDER.compare(x == null ? "" : x, y == null ? "" : y);
                });
                JSArray arr = new JSArray();
                for (JSObject o : rows) arr.put(o);
                JSObject ret = new JSObject();
                ret.put("apps", arr);
                call.resolve(ret);
            } catch (Exception e) {
                call.reject("Could not list apps: " + e.getMessage());
            }
        }).start();
    }

    @PluginMethod
    public void launch(PluginCall call) {
        String pkg = call.getString("pkg");
        if (pkg == null) { call.reject("pkg required"); return; }
        Intent i = getContext().getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) { call.reject("not installed"); return; }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
        call.resolve();
    }

    /** Opens the system screen where the default home app is chosen. */
    @PluginMethod
    public void openHomeSettings(PluginCall call) {
        Intent i = new Intent(Settings.ACTION_HOME_SETTINGS);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            getContext().startActivity(i);
        } catch (Exception e) {
            Intent s = new Intent(Settings.ACTION_SETTINGS);
            s.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(s);
        }
        call.resolve();
    }

    /** colour: "#rrggbb" behind the status and navigation bars; light: dark icons on a light bar. */
    @PluginMethod
    public void setSystemBars(final PluginCall call) {
        final String color = call.getString("color", "#000000");
        final boolean light = Boolean.TRUE.equals(call.getBoolean("light", false));
        getActivity().runOnUiThread(() -> {
            try {
                Window w = getActivity().getWindow();
                View decor = w.getDecorView();
                int c = Color.parseColor(color);
                decor.setBackgroundColor(c);
                View content = decor.findViewById(android.R.id.content);
                if (content != null) content.setBackgroundColor(c);
                WindowInsetsControllerCompat ctl = new WindowInsetsControllerCompat(w, decor);
                ctl.setAppearanceLightStatusBars(light);
                ctl.setAppearanceLightNavigationBars(light);
                call.resolve();
            } catch (Exception e) {
                call.reject("setSystemBars failed: " + e.getMessage());
            }
        });
    }

    private static String encode(Drawable d) {
        Bitmap bmp = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        d.setBounds(0, 0, ICON_PX, ICON_PX);
        d.draw(c);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        bmp.recycle();
        return "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }
}
