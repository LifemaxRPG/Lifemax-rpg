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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Checks the GitHub release for a newer build and hands the downloaded APK to Android's installer. */
@CapacitorPlugin(name = "Updater")
public class UpdaterPlugin extends Plugin {
    private static final String VERSION_URL =
            "https://github.com/LifemaxRPG/Lifemax-rpg/releases/download/latest/version.json";

    private long installedBuild() {
        try {
            PackageInfo pi = getContext().getPackageManager().getPackageInfo(getContext().getPackageName(), 0);
            return android.os.Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    /* Follows redirects by hand so each hop is a fresh, plain request: GitHub sends release downloads to a signed storage address that rejects extra headers (it answers "bad request"). */
    private HttpURLConnection open(String url) throws Exception {
        for (int hop = 0; hop < 6; hop++) {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", "LifeMax-Updater");
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null || loc.isEmpty()) throw new Exception("redirect without a address");
                url = new URL(new URL(url), loc).toString();
                continue;
            }
            return c;
        }
        throw new Exception("too many redirects");
    }

    @PluginMethod
    public void check(final PluginCall call) {
        new Thread(() -> {
            try {
                HttpURLConnection c = open(VERSION_URL);
                if (c.getResponseCode() != 200) { call.reject("Server answered " + c.getResponseCode()); return; }
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                try (InputStream in = c.getInputStream()) {
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                }
                JSONObject j = new JSONObject(bo.toString("UTF-8"));
                JSObject ret = new JSObject();
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
        new Thread(() -> {
            try {
                File dir = new File(getContext().getCacheDir(), "update");
                if (!dir.exists()) dir.mkdirs();
                File apk = new File(dir, "LifeMax-RPG.apk");
                HttpURLConnection c = open(url);
                if (c.getResponseCode() != 200) { call.reject("Download answered " + c.getResponseCode()); return; }
                long total = c.getContentLengthLong(), got = 0;
                int lastPct = -1;
                try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(apk)) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        got += n;
                        int pct = total > 0 ? (int) (got * 100 / total) : -1;
                        if (pct != lastPct) {
                            lastPct = pct;
                            JSObject p = new JSObject();
                            p.put("pct", pct);
                            notifyListeners("progress", p);
                        }
                    }
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
                call.reject("Update failed: " + e);
            }
        }).start();
    }
}
