package com.example.tingxiejian;

import android.content.Context;
import org.json.JSONObject;

/** Capture is a stand-in; the check executes the production Activity's event and lifecycle code. */
class DictationController {
    interface Listener { void event(JSONObject event); }
    static int starts, stops;
    DictationController(Context context, Listener listener) { }
    static boolean isBusy() { return false; }
    void start() { starts++; }
    void pause(boolean paused) { }
    void stop() { stops++; }
    void detach() { }
}
