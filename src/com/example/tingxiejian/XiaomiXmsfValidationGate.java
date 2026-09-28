package com.example.tingxiejian;

import android.content.Context;
import android.util.Log;

/**
 * Compatibility facade for the optional Shizuku XMSF gate. Merely finding HyperOS' hidden
 * updateAurogonUidRule method on ConnectivityManager never proved that our ordinary app UID had
 * permission to change its network rule. ShizukuIslandBridge instead proxies the system Binder,
 * reads back the previous rule and restores it after every posting attempt.
 */
final class XiaomiXmsfValidationGate {
    private static final String TAG = "IslandXmsfGate";
    private final ShizukuIslandBridge bridge;
    private boolean blockedByThisInstance;

    XiaomiXmsfValidationGate(Context context) {
        bridge = new ShizukuIslandBridge(context);
    }

    synchronized boolean isSupported() {
        return bridge.isSupported();
    }

    synchronized boolean setXmsfBlocked(boolean blocked) {
        try {
            if (blocked) {
                // Mark a failed partial arm too: the publisher's finally must attempt recovery.
                blockedByThisInstance = true;
                return bridge.setXmsfBlocked(true);
            }
            return restoreIfNeeded();
        } catch (Throwable error) {
            Log.e(TAG, "Shizuku gate failed", error);
            return false;
        }
    }

    synchronized boolean restoreIfNeeded() {
        try {
            boolean restored = bridge.restoreIfNeeded();
            if (restored) blockedByThisInstance = false;
            return restored;
        } catch (Throwable error) {
            Log.e(TAG, "XMSF gate restoration failed", error);
            return false;
        }
    }

    boolean windowHeld() { return bridge.windowHeld(); }

    String status() { return bridge.failure(); }
}
