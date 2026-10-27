package com.lifemax.rpg;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;

import com.getcapacitor.Bridge;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;

/**
 * Over-the-air updates of the web app (everything except native code). A downloaded build lives in
 * files/live/<build>/index.html. The bundled copy inside the APK is always the fallback: a build that does not
 * report itself healthy within two starts is dropped, and an APK that is newer than the stored build wins.
 */
final class LiveWeb {
    private static final String PREFS = "lifemax_live";
    private LiveWeb() {}

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    static long installedBuild(Context c) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return android.os.Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
        } catch (Exception e) { return 0; }
    }

    static File root(Context c) { File d = new File(c.getFilesDir(), "live"); if (!d.exists()) d.mkdirs(); return d; }
    static File dirOf(Context c, long build) { return new File(root(c), String.valueOf(build)); }
    static boolean valid(Context c, long build) { File f = new File(dirOf(c, build), "index.html"); return build > 0 && f.isFile() && f.length() > 100000; }

    static void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        f.delete();
    }

    /** Run before the web view starts: decide which web build to show. */
    static void prepare(Context c) {
        try {
            SharedPreferences p = prefs(c);
            long installed = installedBuild(c), active = p.getLong("active", 0), staged = p.getLong("staged", 0);
            SharedPreferences.Editor e = p.edit();
            /* an APK at least as new as anything stored carries the newest web build already */
            if (active != 0 && installed >= active) { deleteTree(dirOf(c, active)); active = 0; e.putLong("active", 0); }
            if (staged != 0 && installed >= staged) { deleteTree(dirOf(c, staged)); staged = 0; e.putLong("staged", 0); }
            /* promote a build that was downloaded last time */
            if (staged > installed && valid(c, staged)) {
                if (active != 0 && active != staged) deleteTree(dirOf(c, active));
                active = staged; staged = 0;
                e.putLong("active", active).putLong("staged", 0).putString("state", "pending").putInt("tries", 0);
            }
            /* a build that never reported healthy: two tries, then back to the bundled one */
            if (active != 0) {
                if (!valid(c, active)) { active = 0; e.putLong("active", 0); }
                else if ("pending".equals(p.getString("state", "ok"))) {
                    int tries = p.getInt("tries", 0) + 1;
                    if (tries > 2) { deleteTree(dirOf(c, active)); e.putLong("bad", Math.max(p.getLong("bad", 0), active)).putLong("active", 0); active = 0; }
                    else e.putInt("tries", tries);
                }
            }
            e.apply();
        } catch (Throwable t) { /* never block the app from starting */ }
    }

    static long activeBuild(Context c) { long a = prefs(c).getLong("active", 0); return valid(c, a) ? a : 0; }

    /** Point the web view at the stored build (reloads the page). Call after the bridge exists. */
    static void apply(Bridge bridge, Context c) {
        try {
            long a = activeBuild(c);
            if (a != 0 && bridge != null) bridge.setServerBasePath(dirOf(c, a).getAbsolutePath());
        } catch (Throwable t) { /* stay on the bundled copy */ }
    }

    static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(f)) { byte[] b = new byte[65536]; int n; while ((n = in.read(b)) > 0) md.update(b, 0, n); }
        StringBuilder sb = new StringBuilder();
        for (byte x : md.digest()) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
