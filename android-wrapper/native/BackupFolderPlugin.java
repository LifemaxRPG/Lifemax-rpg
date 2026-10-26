package com.lifemax.rpg;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.activity.result.ActivityResult;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Remembers a folder the user picked and writes rotating backup files into it. */
@CapacitorPlugin(name = "BackupFolder")
public class BackupFolderPlugin extends Plugin {

    @PluginMethod
    public void pickFolder(PluginCall call) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(call, intent, "folderResult");
    }

    @ActivityCallback
    private void folderResult(PluginCall call, ActivityResult result) {
        if (call == null) return;
        JSObject ret = new JSObject();
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) {
            ret.put("picked", false);
            call.resolve(ret);
            return;
        }
        Uri tree = result.getData().getData();
        try {
            getContext().getContentResolver().takePersistableUriPermission(tree,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            ret.put("picked", true);
            ret.put("uri", tree.toString());
            ret.put("name", displayName(getContext().getContentResolver(), tree));
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Could not keep access to that folder: " + e);
        }
    }

    private static Uri rootDoc(Uri tree) {
        return DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
    }

    private static String displayName(ContentResolver cr, Uri tree) {
        try (Cursor c = cr.query(rootDoc(tree), new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) { }
        return "Selected folder";
    }

    /** Writes name into the folder, then deletes the oldest files that share prefix beyond keep. */
    @PluginMethod
    public void writeBackup(final PluginCall call) {
        final String uriStr = call.getString("uri"), name = call.getString("name"), text = call.getString("text");
        final String prefix = call.getString("prefix", "");
        final int keep = Math.max(1, call.getInt("keep", 7));
        if (uriStr == null || name == null || text == null) { call.reject("uri, name and text required"); return; }
        new Thread(() -> {
            try {
                ContentResolver cr = getContext().getContentResolver();
                Uri tree = Uri.parse(uriStr);
                Uri created = DocumentsContract.createDocument(cr, rootDoc(tree), "application/json", name);
                if (created == null) { call.reject("The folder refused the new file"); return; }
                try (OutputStream os = cr.openOutputStream(created, "wt")) {
                    if (os == null) { call.reject("Could not open the new file"); return; }
                    os.write(text.getBytes(StandardCharsets.UTF_8));
                }
                int pruned = prune(cr, tree, prefix, keep);
                JSObject ret = new JSObject();
                ret.put("saved", true);
                ret.put("pruned", pruned);
                ret.put("name", displayName(cr, created));
                call.resolve(ret);
            } catch (SecurityException e) {
                call.reject("ACCESS_LOST");
            } catch (Throwable e) {
                call.reject("Backup failed: " + e);
            }
        }).start();
    }

    private int prune(ContentResolver cr, Uri tree, String prefix, int keep) {
        List<String[]> files = new ArrayList<>(); // {docId, lastModified}
        Uri kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
        try (Cursor c = cr.query(kids, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_LAST_MODIFIED}, null, null, null)) {
            while (c != null && c.moveToNext()) {
                String n = c.getString(1);
                if (n != null && n.startsWith(prefix) && n.endsWith(".json"))
                    files.add(new String[]{c.getString(0), String.valueOf(c.getLong(2))});
            }
        } catch (Exception e) {
            return 0;
        }
        Collections.sort(files, (a, b) -> Long.compare(Long.parseLong(b[1]), Long.parseLong(a[1]))); // newest first
        int removed = 0;
        for (int i = keep; i < files.size(); i++) {
            try {
                if (DocumentsContract.deleteDocument(cr, DocumentsContract.buildDocumentUriUsingTree(tree, files.get(i)[0]))) removed++;
            } catch (Exception ignored) { }
        }
        return removed;
    }
}
