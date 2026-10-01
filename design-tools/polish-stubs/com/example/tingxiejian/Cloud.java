package com.example.tingxiejian;
import android.content.Context;
/** No-network boundary used only to exercise production TranscriptPolisher orchestration. */
final class Cloud {
    static String key = "", response = "", lastUser = "";
    static int calls, lastTimeout;
    static boolean configured(Context context) { return !key.isEmpty(); }
    static String chat(Context context, String system, String user) { calls++; lastUser = user; return response; }
    static String chat(Context context, String system, String user, int timeout) { lastTimeout = timeout; return chat(context, system, user); }
}
