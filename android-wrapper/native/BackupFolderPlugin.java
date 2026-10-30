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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Remembers a folder the user picked and keeps rotating backup files in it. The web app checks every file it reads back
 * before the backup counts, so a new file is only pruned against older ones after that check (ADR 0004).
 */
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
            ret.put("name", displayName(getContext().getContentResolver(), rootDoc(tree), "Selected folder"));
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Could not keep access to that folder: " + e);
        }
    }

    private static Uri rootDoc(Uri tree) {
        return DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
    }

    private static String displayName(ContentResolver cr, Uri doc, String fallback) {
        try (Cursor c = cr.query(doc, new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) { }
        return fallback;
    }

    /** Creates a new file in the folder and answers its uri, so it can be read back. */
    @PluginMethod
    public void createBackup(final PluginCall call) {
        final String uriStr = call.getString("uri"), name = call.getString("name"), text = call.getString("text");
        if (uriStr == null || name == null || text == null) { call.reject("uri, name and text required"); return; }
        inBackground(call, () -> {
            ContentResolver cr = getContext().getContentResolver();
            Uri created = DocumentsContract.createDocument(cr, rootDoc(Uri.parse(uriStr)), "application/json", name);
            if (created == null) { call.reject("The folder refused the new file"); return; }
            try (OutputStream os = cr.openOutputStream(created, "wt")) {
                if (os == null) throw new IOException("Could not open the new file");
                os.write(text.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                // a file cut short must not stay behind, where it would push a good one out of the rotation
                try { DocumentsContract.deleteDocument(cr, created); } catch (Exception ignored) { }
                throw e;
            }
            JSObject ret = new JSObject();
            ret.put("file", created.toString());
            ret.put("name", displayName(cr, created, name));
            call.resolve(ret);
        });
    }

    /** Reads a backup file back, as the folder now holds it. */
    @PluginMethod
    public void readBackup(final PluginCall call) {
        final String file = call.getString("file");
        if (file == null) { call.reject("file required"); return; }
        inBackground(call, () -> {
            try (InputStream in = getContext().getContentResolver().openInputStream(Uri.parse(file))) {
                if (in == null) { call.reject("Could not open the file"); return; }
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] b = new byte[65536];
                int n;
                while ((n = in.read(b)) > 0) bytes.write(b, 0, n);
                JSObject ret = new JSObject();
                ret.put("text", new String(bytes.toByteArray(), StandardCharsets.UTF_8));
                call.resolve(ret);
            }
        });
    }

    /** Deletes the oldest files whose names start with prefix beyond the newest keep. */
    @PluginMethod
    public void pruneBackups(final PluginCall call) {
        final String uriStr = call.getString("uri"), prefix = call.getString("prefix", "");
        final int keep = Math.max(1, call.getInt("keep", 7));
        if (uriStr == null || prefix.isEmpty()) { call.reject("uri and prefix required"); return; }
        inBackground(call, () -> {
            JSObject ret = new JSObject();
            ret.put("pruned", prune(getContext().getContentResolver(), Uri.parse(uriStr), prefix, keep));
            call.resolve(ret);
        });
    }

    /** Deletes one file, such as a backup that failed its check, so it never pushes a good one out of the rotation. */
    @PluginMethod
    public void deleteBackup(final PluginCall call) {
        final String file = call.getString("file");
        if (file == null) { call.reject("file required"); return; }
        inBackground(call, () -> {
            JSObject ret = new JSObject();
            ret.put("deleted", DocumentsContract.deleteDocument(getContext().getContentResolver(), Uri.parse(file)));
            call.resolve(ret);
        });
    }

    private interface FolderWork { void run() throws Exception; }

    /** Folder access is slow, so it runs off the main thread; a folder Android no longer lets us use answers ACCESS_LOST. */
    private static void inBackground(PluginCall call, FolderWork work) {
        new Thread(() -> {
            try {
                work.run();
            } catch (SecurityException e) {
                call.reject("ACCESS_LOST");
            } catch (Throwable e) {
                call.reject(String.valueOf(e));
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
