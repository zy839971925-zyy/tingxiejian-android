package com.example.tingxiejian;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Keep raw crash/diagnostic text app-private. Paths, device identifiers and server errors can
 * appear in exception messages; a crash must never silently publish a report to Downloads. */
final class Diagnostics {
    private static final String TAG = "Tingxiejian";

    private Diagnostics() {}

    static void write(Context context, String name, String mime, String body) {
        // Callers use fixed app-owned names, not user-controlled relative paths.
        try (FileOutputStream out = new FileOutputStream(new File(context.getFilesDir(), name))) {
            out.write(body.getBytes(StandardCharsets.UTF_8));
        } catch (Throwable error) {
            Log.w(TAG, "private write failed: " + name, error);
        }
    }
}
