package com.example.tingxiejian;

import org.json.JSONArray;
import org.json.JSONObject;

/** Desktop-only stand-in: the production History also persists Android app data. */
final class History {
    static JSONArray segments(JSONObject result) { return result.optJSONArray("segments") == null
            ? new JSONArray() : result.optJSONArray("segments"); }
}
