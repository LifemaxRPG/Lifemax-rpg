package com.lifemax.rpg;

import android.content.SharedPreferences;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.lifemax.rpg.app.update.DownloadRules;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.concurrent.atomic.AtomicBoolean;

/** The JavaScript side of over-the-air web updates: what is running, download a new build, restart into it, report healthy. */
@CapacitorPlugin(name = "LiveWeb")
public class LiveWebPlugin extends Plugin {
    /** One download at a time (backlog SEC-5): two would write the same download.tmp. */
    private static final AtomicBoolean FETCHING = new AtomicBoolean(false);

    @PluginMethod
    public void status(PluginCall call) {
        SharedPreferences p = LiveWeb.prefs(getContext());
        JSObject r = new JSObject();
        r.put("nativeApi", NativeApi.VALUE);
        r.put("installedBuild", LiveWeb.installedBuild(getContext()));
        r.put("activeBuild", LiveWeb.activeBuild(getContext()));
        r.put("staged", LiveWeb.valid(getContext(), p.getLong("staged", 0)) ? p.getLong("staged", 0) : 0);
        r.put("state", p.getString("state", "ok"));
        r.put("bad", p.getLong("bad", 0));
        call.resolve(r);
    }

    /**
     * Download a build, check its fingerprint, keep it ready for the next start. The address must be on github.com and may
     * only redirect to GitHub's file storage (DownloadRules, backlog SEC-1); the SHA-256 from version.json must match.
     */
    @PluginMethod
    public void fetch(final PluginCall call) {
        final String url = call.getString("url", "");
        final String sha = String.valueOf(call.getString("sha256", "")).toLowerCase();
        final long build = call.getData().optLong("build", 0L);   /* getLong() answers 0 for a small whole number, which made every web update "bad request" */
        if (!url.startsWith("https://") || sha.length() != 64 || build <= 0) { call.reject("bad request"); return; }
        if (!DownloadRules.isStart(url)) { call.reject("web updates are only downloaded from https://github.com"); return; }
        final SharedPreferences p = LiveWeb.prefs(getContext());
        if (build <= p.getLong("bad", 0)) { call.reject("that build was rejected earlier"); return; }
        if (!FETCHING.compareAndSet(false, true)) { call.reject("a web update is already downloading"); return; }
        try {
            new Thread(() -> {
                try {
                    download(call, url, sha, build, p);
                } finally {
                    FETCHING.set(false);
                }
            }).start();
        } catch (Throwable t) {
            FETCHING.set(false);
            call.reject("download failed: " + t);
        }
    }

    private void download(PluginCall call, String url, String sha, long build, SharedPreferences p) {
        File tmp = new File(LiveWeb.root(getContext()), "download.tmp");
        try {
            HttpURLConnection c = DownloadRules.open(DownloadRules.freshUrl(url));
            if (c.getResponseCode() != 200) { call.reject("server answered " + c.getResponseCode()); return; }
            try (OutputStream out = new FileOutputStream(tmp)) {
                DownloadRules.copy(c, out, DownloadRules.WEB_MAX_BYTES, pct -> {
                    JSObject e = new JSObject();
                    e.put("pct", pct);
                    notifyListeners("progress", e);
                });
            }
            if (tmp.length() < 100000) { tmp.delete(); call.reject("the download was too small"); return; }
            if (!sha.equals(LiveWeb.sha256(tmp))) { tmp.delete(); call.reject("the download did not match its fingerprint"); return; }
            File dir = LiveWeb.dirOf(getContext(), build);
            LiveWeb.deleteTree(dir); dir.mkdirs();
            File dest = new File(dir, "index.html");
            if (!tmp.renameTo(dest)) { tmp.delete(); call.reject("could not store the download"); return; }
            long old = p.getLong("staged", 0);
            if (old != 0 && old != build) LiveWeb.deleteTree(LiveWeb.dirOf(getContext(), old));
            p.edit().putLong("staged", build).apply();
            JSObject ret = new JSObject(); ret.put("staged", build); call.resolve(ret);
        } catch (Throwable t) {
            tmp.delete(); call.reject("download failed: " + t);
        }
    }

    /** Switch to the downloaded build right now (the page reloads). */
    @PluginMethod
    public void restart(PluginCall call) {
        SharedPreferences p = LiveWeb.prefs(getContext());
        long staged = p.getLong("staged", 0), active = p.getLong("active", 0);
        if (!LiveWeb.valid(getContext(), staged)) { call.reject("nothing is ready"); return; }
        if (active != 0 && active != staged) LiveWeb.deleteTree(LiveWeb.dirOf(getContext(), active));
        p.edit().putLong("active", staged).putLong("staged", 0).putString("state", "pending").putInt("tries", 0).apply();
        call.resolve();
        final Plugin self = this;
        getActivity().runOnUiThread(() -> LiveWeb.apply(self.getBridge(), getContext()));
    }

    /** The web app calls this once it has started properly; until then a new build counts as on trial. */
    @PluginMethod
    public void healthy(PluginCall call) {
        SharedPreferences p = LiveWeb.prefs(getContext());
        p.edit().putString("state", "ok").putInt("tries", 0).apply();
        call.resolve();
    }
}
