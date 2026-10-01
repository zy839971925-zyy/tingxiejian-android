package com.example.tingxiejian;

import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Cloud chunk assembly is independent of Android and keeps faithful text immutable. */
final class CloudTranscript {
    interface Polisher { String polish(JSONObject segment, List<String> hotwords) throws Exception; }
    private final String sessionId;
    private final List<String> hotwords;
    private final boolean autoPolish;
    private final String asrModel;
    private final String polishModel;
    private final List<JSONObject> segments = new ArrayList<>();
    private final Set<String> warnings = new LinkedHashSet<>();

    CloudTranscript(String sessionId, List<String> hotwords, boolean autoPolish, String asrModel, String polishModel) {
        this.sessionId = sessionId;
        this.hotwords = Collections.unmodifiableList(new ArrayList<>(hotwords));
        this.autoPolish = autoPolish;
        this.asrModel = asrModel;
        this.polishModel = polishModel;
        if (!autoPolish) warnings.add("未配置云端 API Key，已跳过 AI 校正，保留忠实稿。");
    }

    JSONObject append(String text, double start, double end, Polisher polisher) throws Exception {
        checkInterrupted();
        JSONObject row = new JSONObject().put("segment_id", sessionId + "-chunk-" + segments.size())
                .put("start", start).put("end", end).put("speaker", -1)
                .put("text", text).put("raw_text", text).put("original_text", text).put("final_text", text);
        if (autoPolish) {
            try {
                String edited = polisher.polish(new JSONObject(row.toString()), hotwords);
                checkInterrupted();
                if (edited == null || edited.trim().isEmpty()) throw new IllegalArgumentException("empty polish");
                row.put("polished_text", edited);
            } catch (InterruptedIOException interruption) {
                throw interruption;
            } catch (InterruptedException interruption) {
                Thread.currentThread().interrupt();
                InterruptedIOException failure = new InterruptedIOException("cancelled");
                failure.initCause(interruption);
                throw failure;
            } catch (Exception optionalFailure) {
                checkInterrupted();
                warnings.add("AI 文字校正未完成或未通过保护校验，忠实稿保留。");
            }
        }
        checkInterrupted();
        segments.add(new JSONObject(row.toString()));
        return new JSONObject(row.toString());
    }

    JSONObject result(double duration) throws Exception {
        JSONArray rows = new JSONArray();
        StringBuilder faithful = new StringBuilder();
        StringBuilder edited = new StringBuilder();
        int polished = 0;
        for (JSONObject row : segments) {
            rows.put(new JSONObject(row.toString()));
            appendText(faithful, row.getString("final_text"));
            appendText(edited, row.optString("polished_text", row.getString("final_text")));
            if (row.has("polished_text")) polished++;
        }
        String status = !autoPolish ? "SKIPPED_NO_KEY" : polished == 0 ? "FALLBACK"
                : polished == segments.size() ? "DONE" : "PARTIAL";
        String baseWarning = segments.isEmpty() ? "云端识别没有返回文本" : "云端识别：未区分发言人。";
        JSONObject result = new JSONObject().put("type", "result").put("segments", rows).put("duration", duration)
                .put("text", faithful.toString()).put("raw_text", faithful.toString()).put("final_text", faithful.toString())
                .put("polish_status", status).put("hotwords", new JSONArray(hotwords))
                .put("model_metadata", new JSONObject().put("asr", asrModel).put("polish", autoPolish ? polishModel : "none"))
                .put("warning", baseWarning + (warnings.isEmpty() ? "" : "；" + String.join("；", warnings)));
        if (polished > 0) result.put("polished_text", edited.toString());
        return result;
    }

    private static void appendText(StringBuilder text, String piece) {
        if (text.length() > 0) text.append('\n');
        text.append(piece);
    }

    private static void checkInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("cancelled");
    }
}
