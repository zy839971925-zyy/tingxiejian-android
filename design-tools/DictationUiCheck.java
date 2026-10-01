package com.example.tingxiejian;

import android.content.pm.PackageManager;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.json.JSONObject;

/** Runs the real Activity against lightweight Android views; no native models or device required. */
public final class DictationUiCheck {
    private static final Method EVENT;
    static {
        try {
            EVENT = DictationActivity.class.getDeclaredMethod("event", JSONObject.class);
            EVENT.setAccessible(true);
        } catch (Exception failure) { throw new ExceptionInInitializerError(failure); }
    }

    private static final class Fixture {
        final DictationActivity activity = new DictationActivity();
        final TextView text = new TextView(), status = new TextView();
        Fixture() throws Exception {
            for (String field : new String[]{"status", "hint", "text", "timer", "action", "pause", "result", "orb"})
                set(activity, field, "text".equals(field) ? text : "status".equals(field) ? status : new TextView());
            set(activity, "wave", new WaveView());
        }
        void send(JSONObject event) throws Exception { EVENT.invoke(activity, event); }
    }

    public static void main(String[] arguments) throws Exception {
        oldFinalUpdateKeepsCurrentPhraseAndPause();
        emptyPolishKeepsFaithfulSentence();
        permissionCallbackRequiresForeground();
        System.out.println("PASS: real DictationActivity partial/final/polish events and foreground permission lifecycle");
    }

    private static void oldFinalUpdateKeepsCurrentPhraseAndPause() throws Exception {
        Fixture fixture = new Fixture();
        fixture.send(segment("s1", "第一句"));
        fixture.send(new JSONObject().put("type", "progress").put("stable", "第二句").put("unstable", "仍在说"));
        check(fixture.text.getText().toString().contains("第二句仍在说"), "current speech appeared before final update");
        set(fixture.activity, "paused", true);
        fixture.status.setText("已暂停录音");
        fixture.send(segment("s1", "第一句复核完成"));
        check("第一句复核完成\n第二句仍在说".equals(fixture.text.getText().toString()),
                "background finalization updates the older sentence without erasing current stable/unstable speech");
        check("已暂停录音".equals(fixture.status.getText().toString()), "background finalization cannot report paused capture as listening");
        fixture.send(segment("s2", "第二句完成"));
        check("第一句复核完成\n第二句完成".equals(fixture.text.getText().toString()),
                "new sentence commit consumes only its own current partial, without duplicating it");
    }

    private static void emptyPolishKeepsFaithfulSentence() throws Exception {
        Fixture fixture = new Fixture();
        fixture.send(segment("s1", "忠实原文"));
        for (Object value : new Object[]{JSONObject.NULL, "", "  \n"}) {
            fixture.send(new JSONObject().put("type", "polished-segment").put("segment_id", "s1").put("polished_text", value));
            check("忠实原文".equals(fixture.text.getText().toString()), "null/blank correction cannot erase faithful text");
        }
        fixture.send(new JSONObject().put("type", "polished-segment").put("segment_id", "unknown").put("polished_text", "额外文字"));
        check("忠实原文".equals(fixture.text.getText().toString()), "unknown correction cannot insert a new sentence");
        fixture.send(new JSONObject().put("type", "polished-segment").put("segment_id", "s1").put("polished_text", "忠实原文。"));
        check("忠实原文。".equals(fixture.text.getText().toString()), "valid correction still updates the known sentence");
    }

    private static void permissionCallbackRequiresForeground() throws Exception {
        DictationController.starts = DictationController.stops = 0;
        Fixture fixture = new Fixture();
        grant(fixture.activity);
        check(DictationController.starts == 0, "permission arriving before foreground cannot start capture");
        fixture.activity.onStart();
        grant(fixture.activity);
        check(DictationController.starts == 1, "permission arriving in foreground starts the requested dictation");
        fixture.activity.onStop();
        check(DictationController.stops == 1, "leaving foreground stops active capture");
        grant(fixture.activity);
        check(DictationController.starts == 1, "late permission after onStop cannot create a background recorder");
        Fixture denied = new Fixture();
        denied.activity.onStart();
        denied.activity.onRequestPermissionsResult(105, new String[]{"record"}, new int[]{-1});
        check(DictationController.starts == 1, "denying permission creates no recorder");
        check(denied.status.getText().toString().contains("未授权"), "permission denial remains visible and recoverable");
    }

    private static void grant(DictationActivity activity) {
        activity.onRequestPermissionsResult(105, new String[]{"record"}, new int[]{PackageManager.PERMISSION_GRANTED});
    }

    private static JSONObject segment(String id, String text) throws Exception {
        return new JSONObject().put("type", "segment").put("segment", new JSONObject().put("segment_id", id).put("final_text", text));
    }

    private static void set(Object target, String field, Object value) throws Exception {
        Field member = DictationActivity.class.getDeclaredField(field);
        member.setAccessible(true);
        member.set(target, value);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
