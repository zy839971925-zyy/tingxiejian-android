package com.example.tingxiejian;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.util.Locale;

/**
 * Decides what this device can do: native Super Island, plain notifications, or something in between.
 *
 * <p>Detection follows the verified sequence from
 * <a href="https://github.com/HuberHaYu/Xiaomi-SuperIsland-Playground">Xiaomi-SuperIsland-Playground</a>
 * (Apache-2.0) — manufacturer first, then the focus-notification protocol version, then the
 * {@code persist.sys.feature.island} feature property, then whether the XMSF validation gate exists.
 * It never decides from {@code Build.MANUFACTURER} alone. The gate is best-effort on HyperOS 4;
 * a missing gate cannot prove that private SystemUI will reject a syntactically valid focus
 * notification. READY means "eligible for a submission attempt", never "island rendered".
 */
final class XiaomiIslandCapability {
    private static final String TAG = "IslandCapability";
    private static final String FOCUS_PROTOCOL_SETTING = "notification_focus_protocol";
    private static final String ISLAND_FEATURE_PROPERTY = "persist.sys.feature.island";
    private static final android.net.Uri FOCUS_URI =
            android.net.Uri.parse("content://miui.statusbar.notification.public");

    /** Three-state result — NOT a boolean, because "Xiaomi without island" must be distinguishable. */
    enum State {
        /** Runtime probes permit a focus submission; actual SystemUI rendering is not observable. */
        READY,
        /** Focus protocol below V3, feature off, or XMSF gate unavailable → plain notice. */
        XIAOMI_FALLBACK,
        /** Not a Xiaomi device at all → plain notification. */
        OTHER_ANDROID
    }

    private static volatile State cachedState;
    private static volatile Info cachedInfo;
    private static volatile long cachedAt;
    private static final long CACHE_MS = 60_000;

    private XiaomiIslandCapability() {}

    /** Full snapshot for the settings screen / diagnostics. */
    static final class Info {
        final State state;
        final boolean xiaomi;
        final int protocol;
        /** True when the ROM simply does not publish the protocol setting (seen on HyperOS 4). */
        final boolean protocolMissing;
        final boolean islandFeature;
        final boolean featureKnown;
        final boolean gateSupported;
        final String gateReason;
        /** Informational only: the per-app focus-notification switch. Not used for the decision. */
        final boolean focusPermission;

        Info(State state, boolean xiaomi, int protocol, boolean protocolMissing,
             boolean islandFeature, boolean featureKnown, boolean gateSupported, String gateReason,
             boolean focusPermission) {
            this.state = state;
            this.xiaomi = xiaomi;
            this.protocol = protocol;
            this.protocolMissing = protocolMissing;
            this.islandFeature = islandFeature;
            this.featureKnown = featureKnown;
            this.gateSupported = gateSupported;
            this.gateReason = gateReason;
            this.focusPermission = focusPermission;
        }

        String summary() {
            String proto = protocolMissing ? "协议未提供" : ("协议 " + protocol);
            switch (state) {
                case OTHER_ANDROID: return "非小米设备，使用系统通知";
                case XIAOMI_FALLBACK:
                    if (!islandFeature) return "系统未开启超级岛特性，使用普通通知";
                    if (protocolMissing) return "应用内读不到焦点协议 V3；按 GitHub 方案不能发布岛";
                    if (protocol < 3) return "焦点协议 " + protocol + " < 3；按 GitHub 方案不能发布岛";
                    return "XMSF 特权门禁不可用：" + gateReason + "；改用普通通知";
                default:
                    return "岛特性" + (featureKnown ? "已开启" : "状态未读到")
                        + "（" + proto + "），Shizuku 门禁"
                        + (gateSupported ? "读回可用" : "不可用") + "，焦点权限"
                        + (focusPermission ? "允许" : "未确认")
                        + "。仅代表可尝试提交，系统显示尚未验证";
            }
        }
    }

    static Info info(Context context) {
        Info local = cachedInfo;
        if (local != null && System.currentTimeMillis() - cachedAt < CACHE_MS) return local;

        boolean xiaomi = isXiaomi();
        int protocol = 0;
        boolean protocolMissing = true;
        try {
            // getString, not getInt: on HyperOS 4 the setting can be absent altogether, and
            // "absent" must stay distinguishable from "present but old".
            String raw = Settings.System.getString(context.getContentResolver(), FOCUS_PROTOCOL_SETTING);
            if (raw != null) {
                protocolMissing = false;
                protocol = Integer.parseInt(raw.trim());
            }
        } catch (Throwable ignored) {
            // Unreadable (hardened ROM) → treated as missing; the other probes decide.
        }
        XiaomiXmsfValidationGate gate = XiaomiIslandPublisher.gate(context);
        boolean gateSupported = xiaomi && gate.isSupported();
        Boolean featureProperty = islandFeatureProperty();
        boolean islandFeature = xiaomi && featureProperty != Boolean.FALSE;

        State state;
        if (!xiaomi) {
            state = State.OTHER_ANDROID;
        } else if (!islandFeature) {
            state = State.XIAOMI_FALLBACK;
        } else if (protocolMissing || protocol < 3 || !gateSupported) {
            // Match upstream detectCapability(): getInt(..., 0) < 3 OR missing gate is fallback.
            // A shell's SecurityException is not proof of what THIS app can read; this query
            // runs with our own UID, and a missing value must not be called READY.
            state = State.XIAOMI_FALLBACK;
        } else {
            state = State.READY;
        }

        boolean focusPermission = xiaomi && hasFocusPermission(context);
        Info fresh = new Info(state, xiaomi, protocol, protocolMissing, islandFeature,
                featureProperty != null, gateSupported, gate.status(), focusPermission);
        cachedState = state;
        cachedInfo = fresh;
        cachedAt = System.currentTimeMillis();
        Log.i(TAG, "detect state=" + state + " protocol=" + (protocolMissing ? "missing" : protocol)
                + " islandFeature=" + islandFeature + " gate=" + gateSupported
                + " focusPermission=" + focusPermission);
        return fresh;
    }

    static State detect(Context context) {
        State local = cachedState;
        if (local != null && System.currentTimeMillis() - cachedAt < CACHE_MS) return local;
        return info(context).state;
    }

    static void invalidateCache() {
        cachedState = null;
        cachedInfo = null;
    }

    /** Documented query for the per-app focus-notification switch (informational). */
    private static boolean hasFocusPermission(Context context) {
        try {
            Bundle extras = new Bundle();
            extras.putString("package", context.getPackageName());
            Bundle result = context.getContentResolver().call(FOCUS_URI, "canShowFocus", null, extras);
            return result != null && result.getBoolean("canShowFocus", false);
        } catch (Throwable e) {
            return false;
        }
    }

    private static boolean isXiaomi() {
        String brand = (Build.BRAND + " " + Build.MANUFACTURER).toLowerCase(Locale.ROOT);
        return brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco");
    }

    /** {@code android.os.SystemProperties} is hidden API; HiddenApiBypass is the supported route. */
    private static Boolean islandFeatureProperty() {
        try {
            Object value = HiddenApiBypass.invoke(Class.forName("android.os.SystemProperties"), null,
                    "getBoolean", ISLAND_FEATURE_PROPERTY, false);
            return value instanceof Boolean ? (Boolean) value : null;
        } catch (Throwable e) {
            Log.i(TAG, "island feature property unreadable", e);
            return null; // exactly upstream: only an explicit false disables the feature
        }
    }
}
