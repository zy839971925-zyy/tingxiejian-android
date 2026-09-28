package com.example.tingxiejian;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.util.Log;

/**
 * Facade that hangs the Xiaomi Super Island payload off a normal notification.
 *
 * <p>All HyperOS specifics are split into the adapter layer next door:
 * {@link XiaomiIslandCapability} (can this device?), {@link XiaomiIslandPayloadBuilder} (what bytes?),
 * {@link XiaomiXmsfValidationGate} (XMSF validation window), {@link XiaomiIslandPublisher} (when to
 * post). This class only wires them into a built {@link Notification} and keeps the rest of the app
 * free of HyperOS types — business code never imports those classes directly.
 *
 * <p>Extras used (all documented MIUI focus-notification keys, shapes from
 * HuberHaYu/Xiaomi-SuperIsland-Playground, Apache-2.0):
 * <ul>
 *   <li>{@code miui.focus.param} — FocusTemplate V3 JSON, see {@link XiaomiIslandPayloadBuilder}</li>
 *   <li>{@code miui.focus.pics} — {@code Bundle} of key → {@link Icon} for the payload's pic fields</li>
 *   <li>{@code miui.island.firstFloat / updateNoFloat / miui.enableFloat} — float control</li>
 * </ul>
 */
final class IslandNotification {
    private static final String TAG = "IslandNotification";

    private IslandNotification() {}

    /** What this ROM + app combination can actually do. Kept as the UI-facing snapshot. */
    static final class Capability {
        final boolean xiaomi;
        final boolean island;
        /** 0 unknown, 1 OS1, 2 OS2, 3 OS3+ (island templates). */
        final int protocol;
        final boolean focusPermission;
        final boolean gateSupported;
        private final String summaryText;

        Capability(boolean xiaomi, boolean island, int protocol, boolean focusPermission,
                   boolean gateSupported, String summaryText) {
            this.xiaomi = xiaomi;
            this.island = island;
            this.protocol = protocol;
            this.focusPermission = focusPermission;
            this.gateSupported = gateSupported;
            this.summaryText = summaryText;
        }

        String summary() {
            return summaryText;
        }
    }

    static Capability capability(Context context) {
        XiaomiIslandCapability.Info info = XiaomiIslandCapability.info(context);
        return new Capability(info.xiaomi, info.state == XiaomiIslandCapability.State.READY,
                info.protocol, info.focusPermission, info.gateSupported, info.summary());
    }

    static void invalidateCache() {
        XiaomiIslandCapability.invalidateCache();
    }

    /** Diagnostics snapshot for the settings screen. */
    static String toJson(Context context) {
        XiaomiIslandCapability.Info info = XiaomiIslandCapability.info(context);
        return "{\"state\":\"" + info.state + "\",\"xiaomi\":" + info.xiaomi
            + ",\"protocol\":" + info.protocol + ",\"protocolMissing\":" + info.protocolMissing
            + ",\"islandFeature\":" + info.islandFeature
            + ",\"gate\":" + info.gateSupported + ",\"focus\":" + info.focusPermission
            + ",\"summary\":\"" + info.summary() + "\"}";
    }
}
