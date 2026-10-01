package com.example.tingxiejian;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Exercises production validation, layer projection, and persisted History without Android. */
public final class PolishCheck {
    interface Throwing { void run() throws Exception; }
    private static int assertions;
    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
    private static void rejects(Throwing action, String message) throws Exception {
        try { action.run(); } catch (Exception expected) { assertions++; return; }
        throw new AssertionError(message);
    }
    private static String reply(String id, String original, String polished) throws Exception {
        return new JSONObject().put("edits", new JSONArray().put(new JSONObject()
                .put("segment_id", id).put("original", original).put("polished", polished))).toString();
    }
    private static List<PolishValidator.Segment> input(String text) {
        return Collections.singletonList(new PolishValidator.Segment("s1", text));
    }
    private static void validation() throws Exception {
        String source = "嗯，张三将在2026年10月1日向北京交100.50元，增长5%，使用Qwen3和听写间。";
        String polished = "张三将在2026年10月1日向北京交100.50元；增长5%，使用Qwen3和听写间。";
        List<String> hotwords = Arrays.asList("听写间", "Qwen3");
        Map<String, String> accepted = PolishValidator.validate(reply("s1", source, polished), input(source), hotwords);
        check(polished.equals(accepted.get("s1")), "accept punctuation and standalone filler edits");
        check(source.equals(PolishValidator.validate(reply("s1", source, source), input(source), hotwords).get("s1")), "no-op valid");
        rejects(() -> PolishValidator.validate(reply("s9", source, polished), input(source), hotwords), "unknown id");
        rejects(() -> PolishValidator.validate(reply("s1", "wrong original", polished), input(source), hotwords), "exact original required");
        rejects(() -> PolishValidator.validate("{\"edits\":[]}", input(source), hotwords), "missing edit");
        JSONObject duplicate = new JSONObject(reply("s1", source, polished));
        duplicate.getJSONArray("edits").put(duplicate.getJSONArray("edits").getJSONObject(0));
        rejects(() -> PolishValidator.validate(duplicate.toString(), input(source), hotwords), "duplicate id");
        rejects(() -> PolishValidator.validate("```json\n" + reply("s1", source, polished) + "\n```", input(source), hotwords), "markdown forbidden");
        rejects(() -> PolishValidator.validate(reply("s1", source, polished) + " trailing", input(source), hotwords), "trailing data forbidden");
        rejects(() -> PolishValidator.validate("{edits:[]}", input(source), hotwords), "unquoted keys forbidden");
        rejects(() -> PolishValidator.validate("{'edits':[]}", input(source), hotwords), "single quotes forbidden");
        rejects(() -> PolishValidator.validate("{\"edits\":[],}", input(source), hotwords), "trailing comma forbidden");
        rejects(() -> PolishValidator.validate("{\"edits\":[],\"edits\":[]}", input(source), hotwords), "duplicate JSON key forbidden");
        rejects(() -> PolishValidator.validate("{\"edits\":[],\"\\u0065dits\":[]}", input(source), hotwords), "escaped duplicate key forbidden");
        rejects(() -> PolishValidator.validate(reply("s1", source, polished.replace("100.50", "100.5")), input(source), hotwords), "amount protected");
        rejects(() -> PolishValidator.validate(reply("s1", source, polished.replace("2026", "2027")), input(source), hotwords), "date protected");
        rejects(() -> PolishValidator.validate(reply("s1", source, polished.replace("5%", "5")), input(source), hotwords), "percent protected");
        rejects(() -> PolishValidator.validate(reply("s1", source, polished.replace("张三", "李四")), input(source), hotwords), "arbitrary Chinese person protected");
        rejects(() -> PolishValidator.validate(reply("s1", source, polished.replace("北京", "上海")), input(source), hotwords), "place protected");
        rejects(() -> PolishValidator.validate(reply("s1", source, polished.replace("Qwen3", "Qwen 3")), input(source), hotwords), "Latin name protected");
        rejects(() -> PolishValidator.validate(reply("s1", source, polished.replace("听写间", "听写")), input(source), hotwords), "hotword protected");
        rejects(() -> PolishValidator.validate(reply("s1", source, polished + "大家都同意。"), input(source), hotwords), "added facts rejected");
        rejects(() -> PolishValidator.validate(reply("s1", source, ""), input(source), hotwords), "empty output rejected");
        rejects(() -> PolishValidator.validate(reply("s1", source, String.join("", Collections.nCopies(5000, "字"))), input(source), hotwords), "length bounds");
        rejects(() -> PolishValidator.validate(reply("s1", "阿嗯是我的名字。", "阿是我的名字。"), input("阿嗯是我的名字。"), Collections.emptyList()), "internal filler-like character protected");
        String semantic = "他说，不买。";
        rejects(() -> PolishValidator.validate(reply("s1", semantic, "他说不，买。"), input(semantic), Collections.emptyList()), "punctuation cannot move negation boundary");
        rejects(() -> PolishValidator.validate(reply("s1", "忠实正文", "忠实正文。"), input("忠实正文"), Collections.nCopies(257, "热词")), "hotword request bounded");
        rejects(() -> PolishValidator.validate(reply("s1", "忠实正文", "忠实正文。"), input("忠实正文"), Collections.singletonList(String.join("", Collections.nCopies(129, "字")))), "individual hotword bounded");
        String latinName = "使用example.com平台。";
        rejects(() -> PolishValidator.validate(reply("s1", latinName, "使用example,com平台。"), input(latinName), Collections.emptyList()), "dotted proper name protected");
        List<PolishValidator.Segment> pair = Arrays.asList(new PolishValidator.Segment("s1", "第一段"), new PolishValidator.Segment("s2", "第二段"));
        JSONObject batch = new JSONObject(reply("s2", "第二段", "第二段。"));
        batch.getJSONArray("edits").put(new JSONObject().put("segment_id", "s1").put("original", "第一段").put("polished", "第一段。"));
        check(PolishValidator.validate(batch.toString(), pair, Collections.emptyList()).get("s1").equals("第一段。"), "batch ids may arrive reordered");
        rejects(() -> PolishValidator.validate("{\"edits\":[{\"segment_id\":\"s1\",\"original\":\"第一段\",\"polished\":null}]}", input("第一段"), Collections.emptyList()), "null field rejected");
        rejects(() -> PolishValidator.validate(reply("s1", "第一段", "第一段。") .replace("\"polished\":", "\"unexpected\":"), input("第一段"), Collections.emptyList()), "unknown fields rejected");
        rejects(() -> PolishValidator.validate(reply("s1", "第一段", "第一段\u202e。"), input("第一段"), Collections.emptyList()), "bidi control rejected");
    }
    private static JSONObject legacy() throws Exception {
        return new JSONObject().put("id", "old-1").put("name", "旧录音").put("text", "旧正文")
                .put("segments", new JSONArray().put(new JSONObject().put("start", 0).put("end", 1).put("text", "旧正文")));
    }
    private static void layers() throws Exception {
        JSONObject old = legacy();
        String oldBytes = old.toString();
        check(Exporter.plain(History.view(old, false)).equals("旧正文\n"), "old faithful copy");
        check(Exporter.plain(History.view(old, true)).equals("旧正文\n"), "old polished fallback");
        check(oldBytes.equals(old.toString()), "view must not mutate old History");
        JSONObject modern = legacy().put("raw_text", "raw").put("final_text", "旧正文").put("polished_text", "旧正文。");
        modern.getJSONArray("segments").getJSONObject(0).put("original_text", "raw").put("final_text", "旧正文").put("polished_text", "旧正文。");
        String modernBytes = modern.toString();
        check(Exporter.plain(History.view(modern, false)).equals("旧正文\n"), "faithful layer");
        check(Exporter.plain(History.view(modern, true)).equals("旧正文。\n"), "polished layer");
        check(History.view(modern, true).optString("selected_layer").equals("polished"), "JSON export explicit layer");
        check(Exporter.srt(History.view(modern, true)).contains("旧正文。"), "selected SRT layer");
        check(modernBytes.equals(modern.toString()), "layers are separate immutable source");
        JSONObject oldWithPolish = legacy();
        oldWithPolish.getJSONArray("segments").getJSONObject(0).put("polished_text", "旧正文。");
        JSONObject exported = History.view(oldWithPolish, true);
        check(exported.getJSONArray("segments").getJSONObject(0).optString("final_text", "").equals("旧正文"), "legacy polished JSON retains faithful segment");
        check(exported.optString("final_text", "").equals("旧正文"), "legacy polished JSON retains faithful full text");
        JSONObject textOnly = new JSONObject().put("text", "古老全文");
        check(Exporter.plain(History.view(textOnly, true)).equals("古老全文\n"), "text-only old History opens and exports");
    }
    private static void history() throws Exception {
        File root = Files.createTempDirectory("history-check").toFile();
        Context context = new Context(root);
        History.dir(context).mkdirs();
        JSONObject old = legacy();
        File file = new File(History.dir(context), "old-1.json");
        Files.write(file.toPath(), old.toString().getBytes(StandardCharsets.UTF_8));
        check(Exporter.plain(History.load(context, "old-1")).equals("旧正文\n"), "legacy load");
        File outside = new File(root, "private.json");
        Files.write(outside.toPath(), new JSONObject(old.toString()).put("name", "private").toString().getBytes(StandardCharsets.UTF_8));
        File linked = new File(History.dir(context), "linked-1.json");
        Files.createSymbolicLink(linked.toPath(), outside.toPath());
        rejects(() -> History.load(context, "linked-1"), "symlink cannot escape history");
        check(History.list(context, 10).size() == 1, "list excludes symlink escaping history");
        Files.delete(linked.toPath());
        File linkedBackup = new File(History.dir(context), "linked-1.json.bak");
        Files.createSymbolicLink(linkedBackup.toPath(), outside.toPath());
        rejects(() -> History.load(context, "linked-1"), "backup symlink cannot escape history");
        Files.deleteIfExists(linkedBackup.toPath());
        Files.deleteIfExists(linked.toPath());
        File backup = new File(file.getPath() + ".bak");
        Files.move(file.toPath(), backup.toPath());
        check(History.list(context, 10).size() == 1 && file.isFile(), "list recovers AtomicFile backup after interrupted write");
        JSONObject update = new JSONObject(old.toString());
        update.getJSONArray("segments").getJSONObject(0).put("polished_text", "旧正文。");
        History.update(context, "old-1", update);
        check(History.list(context, 10).size() == 1, "polish creates no new records");
        check(History.load(context, "old-1").getJSONArray("segments").getJSONObject(0).optString("text").equals("旧正文"), "faithful persists unchanged");
        check(History.load(context, "old-1").getJSONArray("segments").getJSONObject(0).optString("polished_text").equals("旧正文。"), "polished persists");
        String bytes = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        JSONObject tampered = new JSONObject(update.toString());
        tampered.getJSONArray("segments").getJSONObject(0).put("text", "changed original");
        rejects(() -> History.update(context, "old-1", tampered), "update immutable final");
        check(bytes.equals(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)), "invalid update leaves file intact");
        rejects(() -> History.update(context, "missing", update), "cannot create history through update");
        rejects(() -> History.update(context, "old-1", new JSONObject(update.toString()).put("id", "other")), "id binding");
        rejects(() -> History.load(context, "../private"), "path traversal load");
        rejects(() -> History.update(context, "../private", update), "path traversal update");
        History.delete(context, "../private");
        check(file.isFile(), "invalid delete is inert");
        android.util.AtomicFile.failNextFinish = true;
        JSONObject failedWrite = new JSONObject(update.toString()).put("warning", "changed");
        rejects(() -> History.update(context, "old-1", failedWrite), "write failure propagated");
        check(bytes.equals(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)), "atomic rollback");
        JSONObject modern = new JSONObject(update.toString()).put("raw_text", "raw").put("final_text", "旧正文");
        modern.getJSONArray("segments").getJSONObject(0).put("original_text", "raw").put("final_text", "旧正文");
        History.update(context, "old-1", modern);
        JSONObject rawTampered = new JSONObject(modern.toString()).put("raw_text", "corrupt raw");
        rejects(() -> History.update(context, "old-1", rawTampered), "raw layer immutable");
        JSONObject finalTampered = new JSONObject(modern.toString()).put("final_text", "corrupt final");
        rejects(() -> History.update(context, "old-1", finalTampered), "final layer immutable");
    }
    private static void orchestration() throws Exception {
        Context context = new Context(Files.createTempDirectory("polisher-check").toFile());
        JSONObject segment = new JSONObject().put("segment_id", "s1").put("original_text", "流式原文")
                .put("final_text", "忠实正文").put("text", "忠实正文");
        String originalBytes = segment.toString();
        Cloud.key = ""; Cloud.calls = 0;
        rejects(() -> TranscriptPolisher.polishSegment(context, segment, Collections.emptyList()), "no key skipped explicitly");
        check(Cloud.calls == 0, "no-key makes no cloud call");
        check(TranscriptPolisher.NO_KEY_MESSAGE.contains("跳过"), "clear no-key explanation");
        Cloud.key = "configured";
        Cloud.response = reply("s1", "忠实正文", "忠实正文。");
        check(TranscriptPolisher.polishSegment(context, segment, Collections.emptyList()).equals("忠实正文。"), "configured per-segment polish");
        check(Cloud.calls == 1, "configured one cloud request");
        check(Cloud.lastTimeout == 20_000, "optional polish network read timeout bounded");
        check(Cloud.lastUser.contains("忠实正文") && !Cloud.lastUser.contains("流式原文"), "prompt uses final faithful source");
        Cloud.response = reply("s1", "忠实正文", "伪造正文。");
        rejects(() -> TranscriptPolisher.polishSegment(context, segment, Collections.emptyList()), "invalid cloud response fails closed");
        check(segment.toString().equals(originalBytes), "success and rejection cannot mutate final source");
    }
    public static void main(String[] args) throws Exception {
        validation(); layers(); history(); orchestration();
        System.out.println("PASS: " + assertions + " polish/entity/layer/history behavior assertions");
    }
}
