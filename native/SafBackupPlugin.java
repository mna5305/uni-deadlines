package com.naeem.unideadlines;

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

@CapacitorPlugin(name = "SafBackup")
public class SafBackupPlugin extends Plugin {

    @PluginMethod
    public void pickFolder(PluginCall call) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        );
        startActivityForResult(call, intent, "pickFolderResult");
    }

    @ActivityCallback
    private void pickFolderResult(PluginCall call, ActivityResult result) {
        if (call == null) return;
        if (result.getResultCode() != Activity.RESULT_OK
            || result.getData() == null
            || result.getData().getData() == null) {
            call.reject("cancelled");
            return;
        }
        Uri tree = result.getData().getData();
        try {
            getContext().getContentResolver().takePersistableUriPermission(
                tree,
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            );
        } catch (Exception ignored) {
        }
        String docId = DocumentsContract.getTreeDocumentId(tree);
        String name = docId;
        int colon = docId.indexOf(':');
        if (colon >= 0 && colon < docId.length() - 1) {
            name = docId.substring(colon + 1);
        }
        JSObject ret = new JSObject();
        ret.put("uri", tree.toString());
        ret.put("name", name);
        call.resolve(ret);
    }

    @PluginMethod
    public void saveFile(PluginCall call) {
        String treeStr = call.getString("treeUri");
        String folder = call.getString("folder", "UniDeadlines");
        String fileName = call.getString("fileName");
        String data = call.getString("data");
        if (treeStr == null || fileName == null || data == null) {
            call.reject("missing arguments");
            return;
        }
        try {
            ContentResolver cr = getContext().getContentResolver();
            Uri tree = Uri.parse(treeStr);
            Uri rootDoc = DocumentsContract.buildDocumentUriUsingTree(
                tree, DocumentsContract.getTreeDocumentId(tree)
            );
            Uri dir = findOrCreate(cr, tree, rootDoc, folder, true);
            if (dir == null) {
                call.reject("could not create folder");
                return;
            }
            Uri file = findOrCreate(cr, tree, dir, fileName, false);
            if (file == null) {
                call.reject("could not create file");
                return;
            }
            OutputStream os = cr.openOutputStream(file, "wt");
            if (os == null) {
                call.reject("could not open file");
                return;
            }
            try {
                os.write(data.getBytes(StandardCharsets.UTF_8));
                os.flush();
            } finally {
                os.close();
            }
            JSObject ret = new JSObject();
            ret.put("saved", true);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("save failed: " + e.getMessage());
        }
    }

    private Uri findOrCreate(ContentResolver cr, Uri tree, Uri parentDoc, String name, boolean isDir) throws Exception {
        String parentId = DocumentsContract.getDocumentId(parentDoc);
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId);
        Cursor c = cr.query(
            children,
            new String[] {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            },
            null, null, null
        );
        if (c != null) {
            try {
                while (c.moveToNext()) {
                    String docId = c.getString(0);
                    String disp = c.getString(1);
                    String mime = c.getString(2);
                    boolean childIsDir = DocumentsContract.Document.MIME_TYPE_DIR.equals(mime);
                    if (name.equals(disp) && childIsDir == isDir) {
                        return DocumentsContract.buildDocumentUriUsingTree(tree, docId);
                    }
                }
            } finally {
                c.close();
            }
        }
        return DocumentsContract.createDocument(
            cr, parentDoc,
            isDir ? DocumentsContract.Document.MIME_TYPE_DIR : "application/json",
            name
        );
    }
}
