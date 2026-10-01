package com.example.tingxiejian;

import java.util.Locale;

/** Display names only. OEM family is never evidence that a presentation is supported. */
final class RealtimeDeviceProfile {
    final boolean xiaomi, colorOs, standardLiveUpdate;
    private RealtimeDeviceProfile(boolean xiaomi, boolean colorOs, int sdk) {
        this.xiaomi = xiaomi; this.colorOs = colorOs; standardLiveUpdate = sdk >= 36;
    }
    static RealtimeDeviceProfile of(String brand, String manufacturer, int sdk) {
        String name = (brand + " " + manufacturer).toLowerCase(Locale.ROOT);
        boolean xiaomi = name.contains("xiaomi") || name.contains("redmi") || name.contains("poco");
        return new RealtimeDeviceProfile(xiaomi, !xiaomi && name.contains("oppo"), sdk);
    }
    String title() {
        return xiaomi ? "小米 / HyperOS · 超级岛" : colorOs ? "OPPO / ColorOS · 流体云" : "Android · 原生通知";
    }
    String description() {
        String fallback = "普通前台通知始终保留，展示失败不影响转写。";
        if (xiaomi) return "超级岛是否可用还需检测系统协议、权限与 XMSF 门禁。"
                + (standardLiveUpdate ? "失败后可尝试 Android 实时活动。" : "不支持或失败时使用普通通知。") + fallback;
        if (colorOs) return (standardLiveUpdate
                ? "通过 Android 16 标准实时活动请求展示；ColorOS 决定是否呈现为流体云，不能保证显示。"
                : "当前系统低于 Android 16，使用普通通知；不接入旧版流体云私有接口。") + fallback;
        return (standardLiveUpdate ? "可请求 Android 16 原生实时活动，需系统允许；否则使用普通通知。"
                : "当前系统使用 Android 原生普通通知。Android 16 起可请求实时活动。") + fallback;
    }
    String standardLabel() {
        return colorOs && standardLiveUpdate ? "流体云 · 标准实时活动" : "Android 原生实时活动";
    }
}
