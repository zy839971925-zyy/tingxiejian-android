import com.example.tingxiejian.Job;
import com.example.tingxiejian.SpringCurve;

/** Runs the real shipped logic classes on a desktop JDK: no Android, no device, no guessing. */
public final class NativeLogicCheck {
    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError("FAILED: " + message);
    }

    private static void equal(Object actual, Object expected, String message) {
        check(String.valueOf(actual).equals(String.valueOf(expected)),
                message + " (expected " + expected + ", got " + actual + ")");
    }

    public static void main(String[] args) {
        springs();
        clock();
        progress();
        phases();
        monotonic();
        System.out.println("PASS: native logic (" + checks + " assertions: spring curves, clock format, "
                + "progress mapping, phase mapping, non-decreasing progress)");
    }

    private static void springs() {
        SpringCurve press = new SpringCurve(1f, 22f, 0.24f);
        SpringCurve gentle = new SpringCurve(0.80f, 13f, 0.50f);
        SpringCurve bounce = new SpringCurve(0.58f, 13f, 0.42f);

        for (SpringCurve curve : new SpringCurve[]{press, gentle, bounce}) {
            equal(curve.at(0f), 0f, "curve starts at 0");
            equal(curve.at(curve.duration()), 1f, "curve lands exactly on the target");
            equal(curve.at(-1f), 0f, "negative time clamps to 0");
            equal(curve.at(curve.duration() * 5), 1f, "past the end stays at the target");
            for (int i = 0; i <= 100; i++) {
                float value = curve.at(curve.duration() * i / 100f);
                check(value >= 0f && value <= 1.2f, "curve stays in a sane range");
            }
        }
        equal(press.overshoot(), 0f, "the press curve is critically damped: no overshoot");
        check(gentle.overshoot() > 0.001f && gentle.overshoot() < 0.03f,
                "the panel curve overshoots slightly but stays under 3 percent");
        check(bounce.overshoot() > 0.05f, "the confirmation curve really springs past the target");
    }

    private static void clock() {
        equal(Job.clock(0), "0:00", "clock zero");
        equal(Job.clock(9.4), "0:09", "clock rounds down");
        equal(Job.clock(59.6), "1:00", "clock rounds up across a minute");
        equal(Job.clock(61), "1:01", "clock minutes");
        equal(Job.clock(3661), "1:01:01", "clock hours");
        equal(Job.clock(-5), "0:00", "clock never negative");
    }

    private static void progress() {
        long now = 10_000_000L;
        Job early = Job.of("progress", "", 28, 56, 2, now - 14_000, now);
        equal(early.percent, 45, "recognition owns 0-90 percent");
        equal(early.stage, "正在转写", "progress stage");
        check(early.detail.contains("0:28 / 0:56"), "detail carries real audio positions: " + early.detail);
        equal(early.eta, "剩不到 1 分", "eta from measured speed");

        Job slow = Job.of("progress", "", 60, 3600, 5, now - 60_000, now);
        equal(slow.eta, "剩约 59 分", "long recordings get a minute estimate");

        check(Job.of("progress", "", 5, 0, 0, now - 1000, now) == null, "unknown duration yields no percentage");
        check(Job.of("error", "boom", 0, 0, 0, now, now) == null, "errors carry no progress");
        check(Job.of("phase", "未知阶段", 0, 0, 0, now, now) == null, "unknown phase leaves progress alone");
        check(Job.of("progress", "", 10, 100, 1, now, now).eta.equals("估算中"),
                "eta is not invented before speed is measurable");
    }

    private static void phases() {
        long now = 10_000_000L;
        equal(Job.of("phase", "准备流式识别模型", 0, 0, 0, now, now).percent, 1, "model preparation");
        equal(Job.of("phase", "首次准备离线模型：punct.onnx（10 MB）", 0, 0, 0, now, now).percent, 1,
                "first-run extraction also counts as preparation");
        equal(Job.of("phase", "正在逐段解码并识别录音", 0, 0, 0, now, now).percent, 3, "decoding");
        equal(Job.of("phase", "流式识别完成，正在校正文字", 0, 0, 0, now, now).percent, 90,
                "streaming done hands over at 90");
        equal(Job.of("phase", "校正文字 · 5/10", 5, 10, 0, now, now).percent, 93, "correction shares 90-96");
        equal(Job.of("phase", "校正文字 · 10/10", 10, 10, 0, now, now).percent, 96,
                "correction never crosses 96");
        Job diar = Job.of("phase", "正在辨认录音中的不同发言人", 0, 0, 0, now, now);
        equal(diar.percent, 97, "completed work before diarization");
        check(diar.indeterminate, "blocking diarization is indeterminate");
        check(diar.eta.contains("无法估算"), "diarization ETA is not invented");
        check(diar.atLeast(98).indeterminate, "monotonic floor preserves indeterminate state");
        check(Job.of("phase", "正在恢复标点", 0, 0, 0, now, now).indeterminate,
                "punctuation has no measurable inner fraction");
        Job done = Job.of("result", "", 0, 56, 10, now - 42_000, now);
        equal(done.percent, 100, "result completes the job");
        equal(done.stage, "转写完成", "result stage");
        check(done.eta.contains("0:42"), "result reports the real elapsed time: " + done.eta);
    }

    /** A realistic event stream must never make the ring move backwards. */
    private static void monotonic() {
        long start = 1_000_000L;
        int previous = 0;
        // The real order of a run: prepare, decode+recognise, correct, diarize, finish.
        String[] phases = {"准备流式识别模型", "正在逐段解码并识别录音"};
        for (String phase : phases) {
            Job job = Job.of("phase", phase, 0, 0, 0, start, start);
            if (job == null) continue;
            job = job.atLeast(previous);
            check(job.percent >= previous, "phase " + phase + " must not go backwards");
            previous = job.percent;
        }
        for (int second = 1; second <= 56; second++) {
            Job job = Job.of("progress", "", second, 56, second / 6, start, start + second * 1000L)
                    .atLeast(previous);
            check(job.percent >= previous, "progress must not go backwards at " + second + "s");
            previous = job.percent;
        }
        for (String phase : new String[]{"流式识别完成，正在校正文字", "校正文字 · 1/8", "校正文字 · 8/8",
                "正在辨认录音中的不同发言人"}) {
            Job job = Job.of("phase", phase, 8, 8, 0, start, start + 60_000L).atLeast(previous);
            check(job.percent >= previous, "phase " + phase + " must not go backwards");
            previous = job.percent;
        }
        Job done = Job.of("result", "", 0, 56, 8, start, start + 60_000);
        check(done.atLeast(previous).percent >= previous, "result completes the monotonic ramp");
        // The clamp itself: a lower estimate never drags the displayed number down.
        Job high = Job.of("phase", "正在辨认录音中的不同发言人", 0, 0, 0, start, start);
        Job low = Job.of("progress", "", 1, 56, 0, start, start + 1000L);
        check(low.percent < high.percent, "the raw estimates really do disagree, so the clamp matters");
        equal(low.atLeast(high.percent).percent, high.percent, "atLeast keeps the highest estimate");
        equal(high.atLeast(high.percent).stage, "正在分人", "atLeast keeps the stage text");
    }
}
