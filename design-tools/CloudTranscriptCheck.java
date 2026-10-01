package com.example.tingxiejian;

import java.io.InterruptedIOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONObject;

/** Runs the actual chunk assembler with injected network responses, without an Android runtime. */
public final class CloudTranscriptCheck {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CloudTranscript noKey = new CloudTranscript("run", Arrays.asList("听写间"), false, "asr", "llm");
        JSONObject faithful = noKey.append("会议日期为2026年10月1日", 0, 4, (segment, words) -> {
            calls.incrementAndGet(); return "changed";
        });
        check(faithful.optString("segment_id").equals("run-chunk-0"), "segment has stable session identity");
        check(calls.get() == 0, "without configured key no cloud polishing call occurs");
        check(faithful.optString("raw_text").equals(faithful.optString("final_text")), "faithful layers retained");
        JSONObject skipped = noKey.result(4);
        check(skipped.optString("polish_status").equals("SKIPPED_NO_KEY"), "no-key status explicit");
        check(skipped.optString("warning").contains("API Key"), "no-key warning explicit");
        CloudTranscript cloud = new CloudTranscript("session", Arrays.asList("听写间"), true, "audio-asr", "text-llm");
        String source = "张三支付100.50元";
        JSONObject edited = cloud.append(source, 0, 3, (segment, words) -> {
            check(segment.getString("final_text").equals(source), "polish receives final faithful source");
            check(words.equals(Arrays.asList("听写间")), "protected hotwords forwarded");
            segment.put("text", "malicious mutation");
            segment.put("final_text", "malicious mutation");
            return source + "。";
        });
        check(edited.getString("text").equals(source) && edited.getString("final_text").equals(source),
                "polisher cannot overwrite faithful segment");
        check(edited.getString("polished_text").equals(source + "。"), "validated edited layer stored separately");
        edited.put("text", "external mutation");
        JSONObject fallback = cloud.append("保持忠实稿", 3, 5, (segment, words) -> { throw new Exception("HTTP failed"); });
        check(!fallback.has("polished_text") && fallback.getString("text").equals("保持忠实稿"),
                "network or validation failure preserves ASR text");
        JSONObject result = cloud.result(5);
        check(result.getString("text").equals(source + "\n保持忠实稿"), "text remains faithful across chunks");
        check(result.getString("raw_text").equals(result.getString("final_text")), "complete faithful layers retained");
        check(result.getString("polished_text").equals(source + "。\n保持忠实稿"), "failed edit uses faithful fallback");
        check(result.getString("polish_status").equals("PARTIAL"), "partial polish is reported honestly");
        check(result.getJSONObject("model_metadata").getString("asr").equals("audio-asr"), "ASR model metadata retained");
        check(result.getJSONArray("hotwords").getString(0).equals("听写间"), "hotword metadata retained");
        check(result.getJSONArray("segments").getJSONObject(0).getString("text").equals(source),
                "returned segment cannot mutate stored faithful history");
        int before = result.getJSONArray("segments").length();
        boolean interrupted = false;
        try { cloud.append("late chunk", 5, 7, (segment, words) -> { throw new InterruptedIOException("cancelled"); }); }
        catch (InterruptedIOException expected) { interrupted = true; }
        check(interrupted, "cancellation is propagated rather than hidden as optional polish failure");
        check(cloud.result(5).getJSONArray("segments").length() == before,
                "cancelled chunk cannot become a committed segment");
        System.out.println("PASS: cloud chunk transcript (" + checks + " assertions)");
    }
}
