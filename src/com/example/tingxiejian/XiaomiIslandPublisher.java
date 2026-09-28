package com.example.tingxiejian;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

/**
 * Publish / update / cancel entry for the Xiaomi Super Island notification.
 *
 * <p>Ported from Xiaomi-SuperIsland-Playground (Apache-2.0, see THIRD_PARTY_NOTICES.md) with the
 * same lifecycle contract: a stable notification id (create → update → update → cancel), the XMSF
 * validation window around every island post, and a plain fallback when any step fails. Business code
 * calls only this class; every HyperOS idiom stays behind it.
 *
 * <p>Critical detail from the reference: the payload's picture keys are REFERENCES — the
 * notification must also carry a {@code miui.focus.pics} Bundle of Icon parcelables keyed
 * {@code miui.focus.pic_island_logo / _primary / _secondary}. Without it SystemUI has no island
 * imagery at all.
 */
public final class XiaomiIslandPublisher {
    private static final String TAG = "IslandPublisher";

    static final String KEY_FOCUS_PARAM = "miui.focus.param";
    static final String KEY_FOCUS_PICS = "miui.focus.pics";
    static final String KEY_ISLAND_UPDATE_NO_FLOAT = "miui.island.updateNoFloat";
    static final String KEY_ISLAND_FIRST_FLOAT = "miui.island.firstFloat";
    static final String KEY_MIUI_ENABLE_FLOAT = "miui.enableFloat";
    /** Island timeout in the payload's minute unit (reference clamps at 720). */
    static final long TIMEOUT_MINUTES = 720L;
    /** Reference XMSF validation window: arm → notify → this long → restore. */
    static final long XMSF_VALIDATION_WINDOW_MS = 220L;

    private XiaomiIslandPublisher() {}

    /** Allows the Service's first island post to be a NEW foreground notification, not an update. */
    interface FirstPoster { void post(Notification notification); }

    /** Optional XMSF validation gate: present on HyperOS builds that expose it, null elsewhere. */
    private static XiaomiXmsfValidationGate gate;
    /** True means a focus notification was submitted, NOT that SystemUI displayed an island. */
    private static volatile boolean active;
    private static volatile String lastOutcome = "尚未尝试发布超级岛";

    static String lastOutcome() { return lastOutcome; }

    public static boolean isSupported(Context context) {
        return XiaomiIslandCapability.info(context).state == XiaomiIslandCapability.State.READY;
    }

    public static boolean isActive() {
        return active;
    }

    static synchronized XiaomiXmsfValidationGate gate(Context context) {
        if (gate == null) {
            gate = new XiaomiXmsfValidationGate(context.getApplicationContext());
        }
        return gate;
    }

    /**
     * Publish or update the progress notification on the stable notification id. The payload is
     * rebuilt from the plain business values on every call, exactly like the reference's draft
     * pipeline; the notification object is the one LocalService built.
     */
    static boolean publish(Context context, NotificationManager manager, int notificationId, Notification notification,
            boolean firstFrame, String title, String content, String subTitle, String digitText,
            int progress, String actionTitle, String actionIntentUri) {
        return publish(context, manager, notificationId, notification, firstFrame, title, content,
                subTitle, digitText, progress, actionTitle, actionIntentUri, null);
    }

    static boolean publish(Context context, NotificationManager manager, int notificationId, Notification notification,
            boolean firstFrame, String title, String content, String subTitle, String digitText,
            int progress, String actionTitle, String actionIntentUri, FirstPoster firstPoster) {
        try {
            boolean shouldFloat = firstFrame;
            String payload = XiaomiIslandPayloadBuilder.build(title, content, subTitle, digitText,
                    progress, TIMEOUT_MINUTES, shouldFloat, actionTitle, actionIntentUri);

            Bundle extras = new Bundle();
            extras.putString(KEY_FOCUS_PARAM, payload);
            extras.putBundle(KEY_FOCUS_PICS, pictures(context));
            extras.putBoolean(KEY_ISLAND_UPDATE_NO_FLOAT, !shouldFloat);
            extras.putBoolean(KEY_ISLAND_FIRST_FLOAT, shouldFloat);
            extras.putBoolean(KEY_MIUI_ENABLE_FLOAT, shouldFloat);
            // Upstream attaches extras to Builder BEFORE build; mutating a built Notification's
            // Bundle is not a reliable contract across OEM NotificationManager implementations.
            notification = Notification.Builder.recoverBuilder(context, notification)
                    .addExtras(extras).build();
            if (!notification.extras.containsKey(KEY_FOCUS_PARAM)
                    || notification.extras.getBundle(KEY_FOCUS_PICS) == null) {
                throw new IllegalStateException("focus extras lost during Notification build");
            }
        } catch (Throwable t) {
            Report.problem("build-island-payload", t);
            Log.w(TAG, "island payload failed; posting plain notification", t);
            lastOutcome = "构建焦点载荷失败：" + t.getClass().getSimpleName();
            Diagnostics.write(context, "tingxiejian-island-status.txt", "text/plain", lastOutcome);
            return false;
        }
        // Follow the repository, not a first-frame-only approximation: every focus post is
        // validated, serialized and restored. The 5s notice throttle avoids shade jitter.
        return publishWithValidation(context, manager, notificationId, notification,
                firstFrame ? firstPoster : null);
    }

    /**
     * Post inside the XMSF validation window when the gate exists (reference: arm → notify →
     * ~220ms window → finally restore). Gate failure returns false; the Service posts its
     * ordinary fallback itself, and this method never claims an island was submitted.
     */
    static void publishPlain(NotificationManager manager, int notificationId, Notification notification) {
        try {
            manager.notify(notificationId, notification);
        } catch (Throwable t) {
            Report.problem("update-island-notification", t);
            Log.w(TAG, "island update failed", t);
        }
    }

    static synchronized boolean publishWithValidation(Context context, NotificationManager manager, int notificationId,
            Notification notification, FirstPoster poster) {
        XiaomiXmsfValidationGate g = gate(context);
        boolean windowed = false;
        boolean posted = false;
        try {
            if (isMainThread() || !g.isSupported() || !g.setXmsfBlocked(true)) {
                lastOutcome = "Shizuku XMSF 门禁未通过：" + g.status() + "；回落普通通知";
            } else {
                windowed = true;
                if (poster == null) manager.notify(notificationId, notification);
                else poster.post(notification);
                posted = true;
                try {
                    Thread.sleep(XMSF_VALIDATION_WINDOW_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (!g.windowHeld()) {
                    posted = false;
                    lastOutcome = "焦点通知已提交，但 XMSF 门禁提前恢复；不将其计为岛提交成功";
                } else {
                    active = true;
                    lastOutcome = "焦点通知已提交，Shizuku 阻断读回成功；SystemUI 是否显示仍须观察";
                }
            }
        } catch (Throwable t) {
            // One guarded publication attempt; keep the service alive and fall back to plain UI.
            Report.problem("publish-notification", t);
            Log.w(TAG, "publish failed", t);
            lastOutcome = "焦点通知提交失败：" + t.getClass().getSimpleName();
        } finally {
            try {
                if (!g.restoreIfNeeded()) {
                    lastOutcome += "；警告：XMSF 门禁恢复失败";
                    Log.e(TAG, "XMSF validation rule restoration not confirmed");
                    posted = false;
                }
            } catch (Throwable ignored) {
                // restoreIfNeeded() itself is defensive; nothing else to do here.
            }
        }
        // MediaStore I/O must happen AFTER the 220ms gate is restored, never inside the window.
        Diagnostics.write(context, "tingxiejian-island-status.txt", "text/plain", lastOutcome);
        if (!posted) active = false;
        return posted;
    }

    private static boolean isMainThread() {
        return android.os.Looper.getMainLooper().getThread() == Thread.currentThread();
    }

    static void cancel(Context context, NotificationManager manager, int notificationId) {
        try {
            manager.cancel(notificationId);
        } catch (Throwable t) {
            Log.w(TAG, "cancel failed", t);
        } finally {
            active = false;
        }
    }

    /** The picture Bundle the payload's {@code pic} keys point at (reference {@code miui.focus.pics}). */
    private static Bundle pictures(Context context) {
        Bundle pics = new Bundle();
        Icon logo = appIcon(context);
        pics.putParcelable(XiaomiIslandPayloadBuilder.LOGO_KEY, logo);
        pics.putParcelable(XiaomiIslandPayloadBuilder.PRIMARY_IMAGE_KEY, logo);
        pics.putParcelable(XiaomiIslandPayloadBuilder.SECONDARY_IMAGE_KEY, logo);
        return pics;
    }

    private static Icon appIcon(Context context) {
        try {
            Drawable d = context.getApplicationInfo().loadIcon(context.getPackageManager());
            return Icon.createWithBitmap(drawableToBitmap(d));
        } catch (Throwable t) {
            return Icon.createWithResource(context, R.drawable.ic_notification);
        }
    }

    private static Bitmap drawableToBitmap(Drawable d) {
        if (d instanceof BitmapDrawable) {
            Bitmap b = ((BitmapDrawable) d).getBitmap();
            if (b != null) return b;
        }
        int w = Math.max(1, d.getIntrinsicWidth());
        int h = Math.max(1, d.getIntrinsicHeight());
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, c.getWidth(), c.getHeight());
        d.draw(c);
        return b;
    }

    /** Diagnostic line for the report screen. */
    static String toJson(Context context) {
        XiaomiIslandCapability.Info info = XiaomiIslandCapability.info(context);
        return "{\"state\":\"" + info.state + "\",\"xiaomi\":" + info.xiaomi
            + ",\"protocol\":" + info.protocol + ",\"protocolMissing\":" + info.protocolMissing
            + ",\"islandFeature\":" + info.islandFeature
            + ",\"gate\":" + info.gateSupported + ",\"focus\":" + info.focusPermission
            + ",\"summary\":\"" + info.summary() + "\"}";
    }
}
