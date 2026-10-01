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
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Saved transcripts, one JSON file per result in app-private storage.
 *
 * Shape:
 * {"id","name","createdAt","durationSeconds","engine","speakers","warning","text",
 *  "raw_text","final_text","polished_text", optional model/hotword metadata,
 *  "segments":[{"segment_id","start","end","speaker","text","original_text",
 *                "final_text","polished_text"}]}
 * Legacy text/segments remain canonical faithful content; reading does not migrate a file.
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

    // Preserve large valid entries written by older versions; avoid allocating an int-sized
    // array from corrupt/hostile file metadata. Very large histories need a streaming parser.
    private static final long MAX_RESULT_BYTES = 128L * 1024 * 1024;

    static File dir(Context context) {
        return new File(context.getFilesDir(), "history");
    }

    /** Stores the result and returns its id. */
    static synchronized String save(Context context, JSONObject result) throws Exception {
        if (!dir(context).isDirectory() && !dir(context).mkdirs()) {
            throw new java.io.IOException("无法创建历史记录目录");
        }
        String id = System.currentTimeMillis() + "-" + UUID.randomUUID();
        result.put("id", id);
        write(file(context, id), result);
        return id;
    }

    /** Atomic replacement of the same record; optional AI edits cannot rewrite any source layer. */
    static synchronized void update(Context context, String id, JSONObject result) throws Exception {
        File target = file(context, id);
        if (!target.isFile() && !new File(target.getPath() + ".bak").isFile())
            throw new IOException("历史记录不存在，无法更新");
        if (result == null || !id.equals(result.optString("id", ""))) throw new IOException("历史记录标识不匹配");
        JSONObject previous = loadFile(target);
        preserve(previous, result, "text");
        preserve(previous, result, "raw_text");
        preserve(previous, result, "final_text");
        JSONArray oldSegments = segments(previous), newSegments = segments(result);
        if (oldSegments.length() != newSegments.length()) throw new IOException("整理不能改变原文段落");
        for (int i = 0; i < oldSegments.length(); i++) {
            JSONObject before = oldSegments.optJSONObject(i), after = newSegments.optJSONObject(i);
            if (before == null || after == null) {
                if (before != after) throw new IOException("整理不能改变原文段落");
                continue;
            }
            for (String key : new String[]{"segment_id", "id", "text", "raw_text", "original_text", "final_text", "start", "end", "speaker"})
                preserve(before, after, key);
            // New explicit faithful fields on old entries must agree with their existing text.
            if (!before.has("final_text") && after.has("final_text")
                    && !before.optString("text", "").equals(after.optString("final_text", "")))
                throw new IOException("整理不能改变忠实稿");
        }
        if (!previous.has("final_text") && result.has("final_text")
                && !previous.optString("text", "").equals(result.optString("final_text", "")))
            throw new IOException("整理不能改变忠实稿");
        write(target, result);
    }

    private static void preserve(JSONObject before, JSONObject after, String key) throws IOException {
        if (before.has(key) && (!after.has(key) || !before.opt(key).equals(after.opt(key))))
            throw new IOException("整理不能改变原文字段：" + key);
    }

    private static void write(File file, JSONObject result) throws Exception {
        byte[] data = result.toString().getBytes(StandardCharsets.UTF_8);
        if (data.length > MAX_RESULT_BYTES) throw new IOException("转写记录超过 128 MB");
        AtomicFile atomic = new AtomicFile(file);
        FileOutputStream out = null;
        try {
            out = atomic.startWrite();
            out.write(data);
            atomic.finishWrite(out);
        } catch (Exception error) {
            if (out != null) atomic.failWrite(out);
            throw error;
        }
    }

    static synchronized List<Entry> list(Context context, int limit) {
        List<Entry> out = new ArrayList<>();
        File[] files = dir(context).listFiles();
        if (files == null) {
            return out;
        }
        Set<String> loaded = new HashSet<>();
        for (File item : files) {
            String filename = item.getName();
            if (filename.endsWith(".json.bak")) filename = filename.substring(0, filename.length() - 4);
            if (!filename.endsWith(".json")) continue;
            try {
                String fileId = filename.substring(0, filename.length() - 5);
                if (!validId(fileId) || loaded.contains(fileId)) continue;
                File file = file(context, fileId);
                JSONObject json = loadFile(file);
                loaded.add(fileId);
                out.add(new Entry(
                        fileId,
                        json.optString("name", "录音"),
                        json.optLong("createdAt", file.lastModified()),
                        json.optDouble("durationSeconds", 0),
                        json.optInt("speakers", 0)));
            } catch (Exception ignored) {
                // A damaged entry must never break the list; it just does not appear.
            }
        }
        out.sort(Comparator.comparingLong((Entry e) -> e.createdAt).reversed());
        while (out.size() > Math.max(0, limit)) {
            out.remove(out.size() - 1);
        }
        return out;
    }

    static synchronized JSONObject load(Context context, String id) throws Exception {
        return loadFile(file(context, id));
    }

    static synchronized void delete(Context context, String id) {
        try {
            File target = file(context, id);
            target.delete();
            new File(target.getPath() + ".bak").delete();
        } catch (IOException ignored) { /* Untrusted identifiers never reach the filesystem. */ }
    }

    static synchronized void clear(Context context) {
        File[] files = dir(context).listFiles();
        if (files != null) {
            for (File file : files) {
                file.delete();
            }
        }
    }

    private static JSONObject loadFile(File file) throws Exception {
        try (FileInputStream in = new AtomicFile(file).openRead()) {
            long length = in.getChannel().size();
            if (length < 1 || length > MAX_RESULT_BYTES)
                throw new IOException("历史记录大小异常");
            byte[] data = new byte[(int) length];
            int off = 0;
            while (off < data.length) {
                int n = in.read(data, off, data.length - off);
                if (n == -1) throw new EOFException("历史记录不完整");
                off += n;
            }
            return new JSONObject(new String(data, StandardCharsets.UTF_8));
        }
    }

    private static boolean validId(String id) { return id != null && id.matches("[A-Za-z0-9_-]{1,128}"); }

    private static File file(Context context, String id) throws IOException {
        if (!validId(id)) throw new IOException("历史记录标识无效");
        File directory = dir(context).getCanonicalFile();
        File target = new File(directory, id + ".json");
        // AtomicFile also accesses sidecars; none may redirect to another record or directory.
        for (String suffix : new String[]{"", ".bak", ".new"}) {
            File candidate = new File(target.getPath() + suffix);
            if (!candidate.equals(candidate.getCanonicalFile())) throw new IOException("历史记录路径无效");
        }
        return target;
    }

    /** A detached display/export projection. Original History and all three stored layers survive. */
    static JSONObject view(JSONObject source, boolean polished) throws Exception {
        JSONObject result = new JSONObject(source.toString());
        JSONArray items = segments(result);
        if (items.length() == 0) {
            String faithful = result.optString("final_text", result.optString("text", ""));
            if (!faithful.isEmpty()) {
                JSONObject single = new JSONObject().put("text", faithful);
                if (result.has("polished_text")) single.put("polished_text", result.opt("polished_text"));
                items = new JSONArray().put(single);
                result.put("segments", items);
            }
        }
        StringBuilder full = new StringBuilder(), faithfulFull = new StringBuilder();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            String faithful = item.optString("final_text", item.optString("text", ""));
            if (!item.has("final_text")) item.put("final_text", faithful);
            String selected = polished ? item.optString("polished_text", faithful) : faithful;
            if (selected.trim().isEmpty()) selected = faithful;
            item.put("text", selected);
            if (full.length() > 0) full.append('\n');
            full.append(selected);
            if (faithfulFull.length() > 0) faithfulFull.append('\n');
            faithfulFull.append(faithful);
        }
        if (!result.has("final_text")) result.put("final_text", result.optString("text", faithfulFull.toString()));
        result.put("text", full.toString());
        result.put("selected_layer", polished ? "polished" : "faithful");
        return result;
    }

    /** Segment helper shared with the exporter and the chat context builder. */
    static JSONArray segments(JSONObject result) {
        return result.optJSONArray("segments") != null ? result.optJSONArray("segments") : new JSONArray();
    }
}
