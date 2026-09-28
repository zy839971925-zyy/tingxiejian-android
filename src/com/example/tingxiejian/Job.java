package com.example.tingxiejian;

import java.util.Locale;

/**
 * The single source of truth for "how far along is this job".
 *
 * <p>Recognition is the bulk of the work, so it owns 0-90 percent; offline correction, punctuation
 * and diarization share the rest. Pure Java on purpose: the same mapping feeds the phone UI and the
 * island, and it is tested on a desktop JDK instead of being eyeballed on a device.
 */
public final class Job {
    public static final int NONE = -1;

    public final int percent;
    public final String stage;
    public final String detail;
    public final String eta;
    /** A stage with no measurable internal fraction: never pretend the displayed percent is live. */
    public final boolean indeterminate;

    private Job(int percent, String stage, String detail, String eta) {
        this(percent, stage, detail, eta, false);
    }

    private Job(int percent, String stage, String detail, String eta, boolean indeterminate) {
        this.percent = percent;
        this.stage = stage;
        this.detail = detail;
        this.eta = eta;
        this.indeterminate = indeterminate;
    }

    /**
     * Monotonic view of the same job. Phase estimates and the first recognition samples can disagree
     * by a percent or two; a ring that ticks backwards reads as a bug, so progress only ever rises.
     */
    public Job atLeast(int floor) {
        if (percent >= floor) return this;
        return new Job(floor, stage, detail, eta, indeterminate);
    }

    /**
     * @param type      transcriber event type: phase / progress / result / error
     * @param phase     the phase message (only meaningful for {@code phase} events)
     * @param processed audio seconds recognised so far
     * @param total     audio seconds in the recording (0 when unknown)
     * @param completed finished segments
     * @param startedAt wall-clock ms when the run started
     * @param now       wall-clock ms now
     */
    /** A starting state before the first measurable event arrives. */
    public static Job pending(String detail) {
        return new Job(0, "准备中", detail, "估算中");
    }

    public static Job of(String type, String phase, double processed, double total, int completed,
                         long startedAt, long now) {
        if ("result".equals(type)) {
            return new Job(100, "转写完成", completed + " 段 · " + clock(total), "用时 " + clock((now - startedAt) / 1000.0));
        }
        if ("error".equals(type)) return null;
        if ("progress".equals(type)) {
            if (total <= 0) return null;
            double ratio = Math.max(0, Math.min(1, processed / total));
            int percent = (int) Math.round(ratio * 90);
            return new Job(percent, "正在转写",
                    "已听 " + clock(processed) + " / " + clock(total) + " · " + completed + " 段",
                    eta(processed, total, startedAt, now));
        }
        if ("phase".equals(type)) {
            if (phase == null) return null;
            if (phase.indexOf("校正") >= 0) {
                int done = (int) processed, all = Math.max(1, (int) total);
                return new Job(90 + Math.min(6, Math.round(6f * done / all)), "正在校正",
                        "高精度重新识别 " + done + "/" + all, "剩不到 1 分");
            }
            if (phase.indexOf("发言人") >= 0) return new Job(97, "正在分人", "按声音区分说话人", "计算中 · 此阶段无法估算剩余时间", true);
            if (phase.indexOf("标点") >= 0) return new Job(96, "恢复标点", "正在整理语句", "计算中 · 此阶段无法估算剩余时间", true);
            if (phase.indexOf("准备") >= 0 || phase.indexOf("解压") >= 0) return new Job(1, "准备模型", phase, "首次使用较慢");
            if (phase.indexOf("解码") >= 0) return new Job(3, "正在解码", phase, "剩不到 1 分");
            if (phase.indexOf("流式识别完成") >= 0) return new Job(90, "识别完成", phase, "剩不到 1 分");
        }
        return null;
    }

    private static String eta(double processed, double total, long startedAt, long now) {
        double elapsed = (now - startedAt) / 1000.0;
        if (processed <= 0 || elapsed < 0.5 || total <= 0) return "估算中";
        double speed = processed / elapsed;
        if (speed <= 0.01) return "估算中";
        double remain = (total - processed) / speed;
        if (remain < 45) return "剩不到 1 分";
        return "剩约 " + Math.max(1, Math.round(remain / 60)) + " 分";
    }

    /** m:ss, or h:mm:ss past an hour. Tabular digits so the UI does not jitter. */
    public static String clock(double seconds) {
        long value = Math.max(0, Math.round(seconds));
        long hours = value / 3600, minutes = (value % 3600) / 60, secs = value % 60;
        if (hours > 0) return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs);
        return String.format(Locale.ROOT, "%d:%02d", minutes, secs);
    }
}
