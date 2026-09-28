package com.example.tingxiejian;

import org.json.JSONArray;
import org.json.JSONObject;

/** Desktop regression checks for shipped export formatting without Android dependencies. */
public final class ExportCheck {
    private static void check(boolean expected, String description) {
        if (!expected) throw new AssertionError(description);
    }

    public static void main(String[] args) throws Exception {
        JSONObject local = new JSONObject().put("name", "test").put("engine", "local")
                .put("segments", new JSONArray().put(new JSONObject().put("start", 1.2)
                        .put("end", 1.2).put("text", "测试")));
        check(Exporter.txt(local).contains("离线转写"), "local export label");
        JSONObject cloud = new JSONObject(local.toString()).put("engine", "cloud");
        check(Exporter.txt(cloud).contains("云端转写"), "cloud export label");
        check(!Exporter.txt(cloud).contains("离线转写"), "cloud must not claim offline");
        check(Exporter.srt(local).contains("00:00:01,200 --> 00:00:01,201"),
                "zero-length segments must have a positive subtitle duration");
        check(Exporter.json(local).contains("测试"), "JSON transcript remains intact");
        System.out.println("PASS: export labels, subtitle duration and JSON");
    }
}
