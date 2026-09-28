package com.example.tingxiejian;

import android.content.Context;
import android.util.AtomicFile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Saved transcripts, one JSON file per result in app-private storage.
 *
 * Shape:
 * {"id","name","createdAt","durationSeconds","engine","speakers","warning","text",
 *  "segments":[{"start","end","speaker","text"}]}
 */
final class History {
    static final class Entry {
        final String id;
        final String name;
        final long createdAt;
        final double durationSeconds;
        final int speakers;

        Entry(String id, String name, long createdAt, double durationSeconds, int speakers) {
            this.id = id;
            this.name = name;
            this.createdAt = createdAt;
            this.durationSeconds = durationSeconds;
            this.speakers = speakers;
        }
    }

    private static final long MAX_RESULT_BYTES = 16L * 1024 * 1024;

    static File dir(Context context) {
        return new File(context.getFilesDir(), "history");
    }

    /** Stores the result and returns its id. */
    static String save(Context context, JSONObject result) throws Exception {
        if (!dir(context).isDirectory() && !dir(context).mkdirs()) {
            throw new java.io.IOException("无法创建历史记录目录");
        }
        String id = System.currentTimeMillis() + "-" + UUID.randomUUID();
        result.put("id", id);
        byte[] data = result.toString().getBytes(StandardCharsets.UTF_8);
        if (data.length > MAX_RESULT_BYTES) throw new IOException("转写记录超过 16 MB");
        AtomicFile atomic = new AtomicFile(new File(dir(context), id + ".json"));
        FileOutputStream out = null;
        try {
            out = atomic.startWrite();
            out.write(data);
            atomic.finishWrite(out);
        } catch (Exception error) {
            if (out != null) atomic.failWrite(out);
            throw error;
        }
        return id;
    }

    static List<Entry> list(Context context, int limit) {
        List<Entry> out = new ArrayList<>();
        File[] files = dir(context).listFiles();
        if (files == null) {
            return out;
        }
        for (File file : files) {
            if (!file.getName().endsWith(".json")) {
                continue;
            }
            try {
                JSONObject json = loadFile(file);
                out.add(new Entry(
                        json.optString("id", file.getName().replace(".json", "")),
                        json.optString("name", "录音"),
                        json.optLong("createdAt", file.lastModified()),
                        json.optDouble("durationSeconds", 0),
                        json.optInt("speakers", 0)));
            } catch (Exception ignored) {
                // A damaged entry must never break the list; it just does not appear.
            }
        }
        out.sort(Comparator.comparingLong((Entry e) -> e.createdAt).reversed());
        while (out.size() > limit) {
            out.remove(out.size() - 1);
        }
        return out;
    }

    static JSONObject load(Context context, String id) throws Exception {
        return loadFile(new File(dir(context), id + ".json"));
    }

    static void delete(Context context, String id) {
        new File(dir(context), id + ".json").delete();
    }

    static void clear(Context context) {
        File[] files = dir(context).listFiles();
        if (files != null) {
            for (File file : files) {
                file.delete();
            }
        }
    }

    private static JSONObject loadFile(File file) throws Exception {
        long length = file.length();
        if (length < 1 || length > MAX_RESULT_BYTES)
            throw new IOException("历史记录大小异常");
        byte[] data = new byte[(int) length];
        try (FileInputStream in = new FileInputStream(file)) {
            int off = 0;
            while (off < data.length) {
                int n = in.read(data, off, data.length - off);
                if (n == -1) throw new EOFException("历史记录不完整");
                off += n;
            }
        }
        return new JSONObject(new String(data, StandardCharsets.UTF_8));
    }

    /** Segment helper shared with the exporter and the chat context builder. */
    static JSONArray segments(JSONObject result) {
        return result.optJSONArray("segments") != null ? result.optJSONArray("segments") : new JSONArray();
    }
}
