package com.example.tingxiejian;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

/** Text export: plain text with timestamps, SRT subtitles, and pretty JSON. */
final class Exporter {
    /** Legacy persisted kinds are TXT=1, JSON=2, SRT=3; menu order is TXT/SRT/JSON. */
    enum Format {
        TXT(1, ".txt", "text/plain", "TXT（带时间）"),
        SRT(3, ".srt", "text/plain", "SRT（字幕）"),
        JSON(2, ".json", "application/json", "JSON（结构化）");
        final int kind;
        final String suffix, mime, label;
        Format(int kind, String suffix, String mime, String label) {
            this.kind = kind; this.suffix = suffix; this.mime = mime; this.label = label;
        }
        String render(JSONObject record) {
            return this == TXT ? txt(record) : this == SRT ? srt(record) : json(record);
        }
    }
    static Format menuFormat(int position) {
        if (position < 0 || position >= Format.values().length) throw new IllegalArgumentException("无效的导出选项");
        return Format.values()[position];
    }
    static Format fromKind(int kind) {
        for (Format format : Format.values()) if (format.kind == kind) return format;
        throw new IllegalArgumentException("无效的导出格式，请重新选择");
    }
    static String[] menuLabels() {
        return new String[]{Format.TXT.label, Format.SRT.label, Format.JSON.label};
    }

    static String speakerName(int speaker) {
        return speaker >= 0 ? "发言人 " + (char) ('A' + (speaker % 26)) : "说话人";
    }

    static String clock(double seconds) {
        long total = Math.max(0, (long) seconds);
        return String.format(Locale.US, "%02d:%02d", total / 60, total % 60);
    }

    static String txt(JSONObject result) {
        StringBuilder out = new StringBuilder();
        out.append(result.optString("name", "录音")).append('\n');
        out.append("时长 ").append(clock(result.optDouble("durationSeconds", 0)))
                .append(" · 共 ").append(History.segments(result).length()).append(" 段");
        int speakers = result.optInt("speakers", 0);
        if (speakers > 1) {
            out.append(" · ").append(speakers).append(" 位发言人");
        }
        out.append(result.optString("engine", "local").equals("cloud")
                ? "\n由听写间云端转写\n\n" : "\n由听写间离线转写\n\n");
        JSONArray segments = History.segments(result);
        for (int i = 0; i < segments.length(); i++) {
            JSONObject segment = segments.optJSONObject(i);
            if (segment == null) {
                continue;
            }
            out.append('[').append(clock(segment.optDouble("start", 0))).append("] ");
            if (segment.optInt("speaker", -1) >= 0) {
                out.append(speakerName(segment.optInt("speaker", -1))).append('：');
            }
            out.append(segment.optString("text", "")).append('\n');
        }
        return out.toString();
    }

    static String srt(JSONObject result) {
        StringBuilder out = new StringBuilder();
        JSONArray segments = History.segments(result);
        for (int i = 0; i < segments.length(); i++) {
            JSONObject segment = segments.optJSONObject(i);
            if (segment == null) {
                continue;
            }
            double start = Math.max(0, segment.optDouble("start", 0));
            double end = Math.max(start + .001, segment.optDouble("end", 0));
            out.append(i + 1).append('\n')
                    .append(stamp(start)).append(" --> ")
                    .append(stamp(end))
                    .append('\n');
            if (segment.optInt("speaker", -1) >= 0) {
                out.append(speakerName(segment.optInt("speaker", -1))).append('：');
            }
            out.append(segment.optString("text", "")).append("\n\n");
        }
        return out.toString();
    }

    static String json(JSONObject result) {
        try {
            return result.toString(2);
        } catch (Exception e) {
            return result.toString();
        }
    }

    /** Compact plain transcript, used as the chat context and for the clipboard. */
    static String plain(JSONObject result) {
        JSONArray segments = History.segments(result);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < segments.length(); i++) {
            JSONObject segment = segments.optJSONObject(i);
            if (segment == null) {
                continue;
            }
            if (segment.optInt("speaker", -1) >= 0) {
                out.append(speakerName(segment.optInt("speaker", -1))).append('：');
            }
            out.append(segment.optString("text", "")).append('\n');
        }
        return out.toString();
    }

    private static String stamp(double seconds) {
        long ms = Math.max(0, Math.round(seconds * 1000));
        long hour = ms / 3600000;
        long minute = (ms % 3600000) / 60000;
        long second = (ms % 60000) / 1000;
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hour, minute, second, ms % 1000);
    }
}
