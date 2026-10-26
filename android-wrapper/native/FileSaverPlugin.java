package com.lifemax.rpg;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import androidx.activity.result.ActivityResult;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** "Save as" through Android's document picker, so the user chooses the folder and file name. */
@CapacitorPlugin(name = "FileSaver")
public class FileSaverPlugin extends Plugin {

    @PluginMethod
    public void saveFile(PluginCall call) {
        String text = call.getString("text");
        if (text == null) { call.reject("text required"); return; }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(call.getString("mime", "application/octet-stream"));
        intent.putExtra(Intent.EXTRA_TITLE, call.getString("name", "lifemax-export"));
        startActivityForResult(call, intent, "saveResult");
    }

    @ActivityCallback
    private void saveResult(PluginCall call, ActivityResult result) {
        if (call == null) return;
        JSObject ret = new JSObject();
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) {
            ret.put("saved", false);
            call.resolve(ret);
            return;
        }
        Uri uri = result.getData().getData();
        try (OutputStream os = getContext().getContentResolver().openOutputStream(uri, "wt")) {
            if (os == null) { call.reject("Could not open the file for writing"); return; }
            os.write(call.getString("text", "").getBytes(StandardCharsets.UTF_8));
            ret.put("saved", true);
            ret.put("uri", uri.toString());
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Could not save: " + e);
        }
    }
}
