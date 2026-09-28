package com.example.tingxiejian;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * Writes small text reports (layout measurement, crash reports) to the app's private files and to
 * Downloads via MediaStore, which needs no permission and is readable outside the app.
 *
 * <p>Works from a bare application context, because a crash can happen before any Activity exists.
 */
final class Diagnostics {
    private static final String TAG = "Tingxiejian";

    private Diagnostics() {}

    static void write(Context context, String name, String mime, String body) {
        try (FileOutputStream out = new FileOutputStream(new File(context.getFilesDir(), name))) {
            out.write(body.getBytes("UTF-8"));
        } catch (Throwable error) {
            Log.w(TAG, "private write failed: " + name, error);
        }
        if (Build.VERSION.SDK_INT >= 29) {
            writeShared(context, name, mime, body);
        }
    }

    /**
     * Drops a copy in Downloads, replacing any earlier file of the same name so the path a reader
     * uses stays stable across runs.
     */
    private static void writeShared(Context context, String name, String mime, String body) {
        ContentResolver resolver = context.getContentResolver();
        if (resolver == null) return;
        OutputStream out = null;
        try {
            removeExisting(resolver, name);
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) return;
            out = resolver.openOutputStream(uri, "wt");
            if (out == null) return;
            out.write(body.getBytes("UTF-8"));
            out.flush();
        } catch (Throwable error) {
            Log.w(TAG, "shared write failed: " + name, error);
        } finally {
            try {
                if (out != null) out.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static void removeExisting(ContentResolver resolver, String name) {
        try {
            Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            resolver.delete(collection, MediaStore.MediaColumns.DISPLAY_NAME + "=? AND "
                    + MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?",
                    new String[]{name, Environment.DIRECTORY_DOWNLOADS + "%"});
        } catch (Throwable ignored) {
            // A leftover file would just be numbered by MediaStore; never fatal.
        }
    }
}
