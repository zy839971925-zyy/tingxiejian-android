package com.example.tingxiejian;

/**
 * Test-only driver: prints island payloads as JSON lines so design-tools/island-payload-check.sh can
 * validate the real shipped builder with a plain JDK (no device, no Android runtime).
 *
 * <p>Signature mirrors the reference builder exactly (title, content, subTitle, digit, progress,
 * timeoutMinutes, firstFrame, actionTitle, actionIntentUri).
 */
public final class IslandPayloadDump {
    private static final String ACTION_URI =
        "intent:#Intent;action=com.example.tingxiejian.action.STOP_JOB;component=com.example.tingxiejian/.MainActivity;end";

    public static void main(String[] args) throws Exception {
        // case 0: create (firstFrame → floats open)
        System.out.println(XiaomiIslandPayloadBuilder.build("正在转写", "已听 0:00 / 0:00 · 0 段", "—",
                "0%", 0, 720, true, "停止", ACTION_URI));
        // case 1: update (firstFrame false → must NOT re-float)
        System.out.println(XiaomiIslandPayloadBuilder.build("正在转写", "已听 3:20 / 8:00 · 7 段", "约 2 分",
                "42%", 42, 720, false, "停止", ACTION_URI));
        // case 2: done
        System.out.println(XiaomiIslandPayloadBuilder.build("转写完成", "12 段", "完成",
                "100%", 100, 720, false, "停止", ""));
        // case 3: clamping low
        System.out.println(XiaomiIslandPayloadBuilder.build("正在准备模型", "首次使用会把模型解压到本机", "—",
                "-5%", -5, 720, true, "停止", ""));
        // case 4: clamping high
        System.out.println(XiaomiIslandPayloadBuilder.build("正在校正", "高精度重新识别 3/9", "剩不到 1 分",
                "150%", 150, 720, false, "停止", ACTION_URI));
        // case 5: escaping — quotes, backslashes and a newline must survive the JSON round-trip
        System.out.println(XiaomiIslandPayloadBuilder.build("阶段\"引号\"", "反斜杠\\与换行\n结束", "<标记>",
                "7%", 7, 720, false, "停止", ""));
    }
}
