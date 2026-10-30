package com.lifemax.rpg;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Checks the GitHub release for a newer build and hands the downloaded APK to Android's installer. Downloads come only
 * from GitHub and have a size cap (DownloadRules).
 */
@CapacitorPlugin(name = "Updater")
public class UpdaterPlugin extends Plugin {
    private static final String VERSION_URL =
            "https://github.com/LifemaxRPG/Lifemax-rpg/releases/download/latest/version.json";
    /** One APK download at a time (backlog SEC-5): a second tap must not rewrite the file the installer is given. */
    private static final AtomicBoolean INSTALLING = new AtomicBoolean(false);

    private long installedBuild() {
        try {
            PackageInfo pi = getContext().getPackageManager().getPackageInfo(getContext().getPackageName(), 0);
            return android.os.Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    @PluginMethod
    public void check(final PluginCall call) {
        new Thread(() -> {
            try {
                HttpURLConnection c = DownloadRules.open(DownloadRules.freshUrl(VERSION_URL));
                if (c.getResponseCode() != 200) { call.reject("Server answered " + c.getResponseCode()); return; }
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                DownloadRules.copy(c, bo, DownloadRules.MANIFEST_MAX_BYTES, null);
                JSONObject j = new JSONObject(bo.toString("UTF-8"));
                if (j.optLong("build", 0) <= 0 || !j.optString("apk", "").startsWith("https://"))
                    throw new Exception("The server did not provide a valid release version. Try again shortly.");
                JSObject ret = new JSObject();
                ret.put("checkedAt", System.currentTimeMillis());
                ret.put("current", installedBuild());
                ret.put("latest", j.optLong("build", 0));
                ret.put("sha", j.optString("sha", ""));
                ret.put("apk", j.optString("apk", ""));
                ret.put("stable", j.optBoolean("stable", false));
                ret.put("nativeApi", NativeApi.VALUE);                         /* this APK's abilities */
                ret.put("latestNativeApi", j.optInt("nativeApi", 0));          /* what the newest web build was made for (0: old file, no web update) */
                JSONObject w = j.optJSONObject("web");
                if (w != null) {
                    ret.put("webBuild", w.optLong("build", 0));
                    ret.put("webUrl", w.optString("url", ""));
                    ret.put("webSha", w.optString("sha256", ""));
                    ret.put("webSize", w.optLong("size", 0));
                    ret.put("webMinApi", w.optInt("minNativeApi", 0));
                }
                call.resolve(ret);
            } catch (Throwable e) {
                call.reject("Update check failed: " + e);
            }
        }).start();
    }

    @PluginMethod
    public void install(final PluginCall call) {
        final String url = call.getString("url");
        if (url == null || !url.startsWith("https://")) { call.reject("bad url"); return; }
        if (!DownloadRules.isStart(url)) { call.reject("Updates are only downloaded from https://github.com"); return; }
        if (android.os.Build.VERSION.SDK_INT >= 26 && !getContext().getPackageManager().canRequestPackageInstalls()) {
            // Send the user to the one switch Android requires, then they tap Update again.
            Intent s = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getContext().getPackageName()));
            s.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(s);
            JSObject ret = new JSObject();
            ret.put("needsPermission", true);
            call.resolve(ret);
            return;
        }
        if (!INSTALLING.compareAndSet(false, true)) { call.reject("An update is already downloading"); return; }
        try {
            new Thread(() -> {
                try {
                    download(call, url);
                } finally {
                    INSTALLING.set(false);
                }
            }).start();
        } catch (Throwable t) {
            INSTALLING.set(false);
            call.reject("Update failed: " + t);
        }
    }

    /* The APK goes to cache/update/, the one cache folder res/xml/file_paths.xml shares with Android's installer. */
    private void download(PluginCall call, String url) {
        File apk = new File(new File(getContext().getCacheDir(), "update"), "LifeMax-RPG.apk");
        try {
            File dir = apk.getParentFile();
            if (!dir.exists()) dir.mkdirs();
            HttpURLConnection c = DownloadRules.open(DownloadRules.freshUrl(url));
            if (c.getResponseCode() != 200) { call.reject("Download answered " + c.getResponseCode()); return; }
            try (OutputStream out = new FileOutputStream(apk)) {
                DownloadRules.copy(c, out, DownloadRules.APK_MAX_BYTES, pct -> {
                    JSObject p = new JSObject();
                    p.put("pct", pct);
                    notifyListeners("progress", p);
                });
            }
            Uri uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", apk);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            JSObject ret = new JSObject();
            ret.put("started", true);
            call.resolve(ret);
        } catch (Throwable e) {
            apk.delete();
            call.reject("Update failed: " + e);
        }
    }
}
