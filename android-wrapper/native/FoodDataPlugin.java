package com.lifemax.rpg;

import android.util.Base64;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.lifemax.rpg.app.fooddata.FoodDataFile;
import com.lifemax.rpg.app.update.DownloadRules;
import com.lifemax.rpg.app.update.KeepAwake;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.HttpURLConnection;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Downloads the USDA branded-foods file from the GitHub release "usda-foods" for the Food database card. The web page cannot
 * fetch release files itself (CORS), so this plugin downloads the file into the cache, checks it against the manifest and
 * hands it to the page in pieces; the page imports it into its own database and then discards the file. Both addresses are
 * fixed in FoodDataFile and downloads follow DownloadRules (GitHub only, size cap).
 */
@CapacitorPlugin(name = "FoodData")
public class FoodDataPlugin extends Plugin {
    /** One download at a time: two would write the same file. */
    private static final AtomicBoolean DOWNLOADING = new AtomicBoolean(false);

    private File staged() {
        return new File(new File(getContext().getCacheDir(), "fooddata"), FoodDataFile.FILE_NAME);
    }

    /** The manifest's claims, checked. */
    private JSObject manifest() throws Exception {
        HttpURLConnection c = DownloadRules.open(DownloadRules.freshUrl(FoodDataFile.MANIFEST_URL));
        try {
            if (c.getResponseCode() != 200) throw new Exception("GitHub answered " + c.getResponseCode());
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            DownloadRules.copy(c, bo, DownloadRules.MANIFEST_MAX_BYTES, null);
            JSONObject j = new JSONObject(bo.toString("UTF-8"));
            long foods = j.optLong("foods", 0), bytes = j.optLong("bytes", 0);
            String sha = FoodDataFile.checkManifest(foods, bytes, j.optString("sha256", ""), DownloadRules.FOOD_MAX_BYTES);
            JSObject ret = new JSObject();
            ret.put("foods", foods);
            ret.put("bytes", bytes);
            ret.put("sha256", sha);
            return ret;
        } finally {
            c.disconnect();
        }
    }

    @PluginMethod
    public void check(final PluginCall call) {
        new Thread(() -> {
            try {
                call.resolve(manifest());
            } catch (Throwable e) {
                call.reject("Could not reach GitHub: " + e.getMessage());
            }
        }).start();
    }

    @PluginMethod
    public void download(final PluginCall call) {
        if (!DOWNLOADING.compareAndSet(false, true)) { call.reject("The food list is already downloading"); return; }
        try {
            new Thread(() -> {
                try {
                    fetch(call);
                } finally {
                    DOWNLOADING.set(false);
                }
            }).start();
        } catch (Throwable t) {
            DOWNLOADING.set(false);
            call.reject("Could not start the download: " + t);
        }
    }

    private void fetch(PluginCall call) {
        File file = staged();
        KeepAwake awake = null;
        boolean fetched = false;
        try {
            JSObject m = manifest();
            File dir = file.getParentFile();
            if (!dir.exists()) dir.mkdirs();
            file.delete();
            awake = KeepAwake.start(getActivity());
            DownloadRules.fetch(DownloadRules.freshUrl(FoodDataFile.FILE_URL), file, DownloadRules.FOOD_MAX_BYTES, pct -> {
                JSObject p = new JSObject();
                p.put("pct", pct);
                notifyListeners("progress", p);
            }, 6);
            fetched = true;
            FoodDataFile.verify(file, m.getLong("bytes"), m.getString("sha256"));
            call.resolve(m);
        } catch (Throwable e) {
            file.delete();
            call.reject(fetched ? String.valueOf(e.getMessage()) : "Could not reach GitHub. Check the connection and try again.");
        } finally {
            if (awake != null) awake.release();
        }
    }

    @PluginMethod
    public void read(PluginCall call) {
        try {
            FoodDataFile.Chunk c = FoodDataFile.read(staged(), call.getLong("offset", 0L), call.getLong("length", 0L));
            JSObject ret = new JSObject();
            ret.put("data", Base64.encodeToString(c.data, Base64.NO_WRAP));
            ret.put("done", c.done);
            call.resolve(ret);
        } catch (Throwable e) {
            call.reject(String.valueOf(e.getMessage()));
        }
    }

    @PluginMethod
    public void discard(PluginCall call) {
        staged().delete();
        call.resolve();
    }
}
