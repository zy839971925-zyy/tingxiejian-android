package com.example.tingxiejian;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.Process;
import android.util.Log;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuBinderWrapper;
import rikka.shizuku.SystemServiceHelper;

/**
 * Opt-in, reversible XMSF firewall window using a Shizuku-proxied system Binder. A hidden API
 * reflection bypass alone cannot grant the app UID NETWORK_SETTINGS; this bridge sends the Binder
 * transaction under Shizuku's authorized UID instead. No HTTP or network transport is used here.
 *
 * This is an experimental OEM compatibility path, NOT proof that SystemUI rendered an island.
 * A persisted snapshot allows the next app launch to repair a rule after process death. Neither
 * that repair nor a watchdog can restore the rule while Shizuku is down: warn the user explicitly.
 */
final class ShizukuIslandBridge {
    private static final String TAG = "IslandShizuku";
    private static final String PREFS = "island_firewall_recovery";
    private static final String PENDING = "pending";
    private static final String OLD_RULE = "old_rule";
    private static final String OLD_CHAIN = "old_chain";
    private static final String TARGET_UID = "target_uid";
    private static final int OEM_DENY_3 = 9; // AOSP FIREWALL_CHAIN_OEM_DENY_3
    private static final int RULE_DENY = 2;
    private static final ScheduledExecutorService WATCHDOG = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "island-firewall-watchdog");
        thread.setDaemon(true);
        return thread;
    });

    /** Prevent another bridge instance from mistaking an in-flight window for a stale crash. */
    private static boolean activeWindow;

    private final Context app;
    private final SharedPreferences recovery;
    private boolean armed;
    private String failure = "尚未检查 Shizuku";

    ShizukuIslandBridge(Context context) {
        app = context.getApplicationContext();
        recovery = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean recoveryPending(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(PENDING, false);
    }

    static boolean optedIn(Context context) {
        return context.getSharedPreferences(SettingsActivity.PREFS, Context.MODE_PRIVATE)
                .getBoolean("island_shizuku", false);
    }

    static String permissionState(Context context) {
        if (recoveryPending(context)) return "警告：仍有 XMSF 防火墙恢复记录，启动 Shizuku 后返回本页重试";
        if (!optedIn(context)) return "Shizuku 岛门禁未开启";
        try {
            if (!Shizuku.pingBinder()) return "Shizuku 未运行；重启后通常需重新启动";
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED)
                return "Shizuku 尚未授权听写间";
            int uid = Shizuku.getUid();
            if (uid != Process.SHELL_UID && uid != 0) return "Shizuku 未以 shell/root 身份运行";
            return "Shizuku 已授权（UID " + uid + "）；仍需验证 XMSF 规则能否读写";
        } catch (Throwable e) {
            return "Shizuku 不可用：" + e.getClass().getSimpleName();
        }
    }

    static boolean requestPermission() {
        try {
            if (!Shizuku.pingBinder()) return false;
            Shizuku.requestPermission(9715);
            return true;
        } catch (Throwable e) {
            Log.w(TAG, "Shizuku permission request failed", e);
            return false;
        }
    }

    synchronized String failure() { return failure; }

    synchronized boolean windowHeld() {
        synchronized (ShizukuIslandBridge.class) {
            return armed && activeWindow && recovery.getBoolean(PENDING, false);
        }
    }

    synchronized boolean isSupported() {
        synchronized (ShizukuIslandBridge.class) {
        // Repair first even if the user has switched the feature off. Never interrupt a live post.
        if (activeWindow) { failure = "焦点通知发布中；请稍后检查"; return false; }
        if (recovery.getBoolean(PENDING, false) && !restoreIfNeeded()) {
            failure = "上次 XMSF 网络规则未恢复；请保持 Shizuku 运行并重新检查";
            return false;
        }
        if (!optedIn(app)) { failure = "未选择使用 Shizuku；回落普通通知"; return false; }
        if (!permissionState(app).startsWith("Shizuku 已授权")) {
            failure = permissionState(app);
            return false;
        }
        try {
            Object remote = service();
            int uid = xmsfUid();
            if (uid < Process.FIRST_APPLICATION_UID) {
                failure = "找不到 XMSF UID";
                return false;
            }
            // Readback probes require permission but do not modify the system.
            readRule(remote, uid);
            readChain(remote);
            failure = "已验证 Shizuku 可读取 XMSF 规则；提交时再检查实际写入";
            return true;
        } catch (Throwable e) {
            Log.w(TAG, "Shizuku firewall probe failed", e);
            failure = "Shizuku 无法读取 OEM 防火墙：" + e.getClass().getSimpleName();
            return false;
        }
        }
    }

    synchronized boolean setXmsfBlocked(boolean blocked) {
        synchronized (ShizukuIslandBridge.class) {
        if (!blocked) return restoreIfNeeded();
        if (armed || activeWindow || !isSupported()) return false;
        try {
            final Object remote = service();
            final int uid = xmsfUid();
            final int oldRule = readRule(remote, uid);
            final boolean oldChain = readChain(remote);
            if (oldChain && oldRule == RULE_DENY) {
                failure = "XMSF 已被其他规则阻断；不会覆盖现有状态";
                return false;
            }
            // Commit BEFORE touching system state; a crash must leave a recovery record.
            if (!recovery.edit().putInt(TARGET_UID, uid).putInt(OLD_RULE, oldRule)
                    .putBoolean(OLD_CHAIN, oldChain).putBoolean(PENDING, true).commit()) {
                failure = "无法保存门禁恢复记录";
                return false;
            }
            armed = true;
            activeWindow = true;
            // The system can disable an OEM chain by default. Preserve its prior state.
            call(remote, "setFirewallChainEnabled", OEM_DENY_3, true);
            call(remote, "setUidFirewallRule", OEM_DENY_3, uid, RULE_DENY);
            if (!readChain(remote) || readRule(remote, uid) != RULE_DENY) {
                failure = "XMSF 阻断未通过系统读回验证";
                return false;
            }
            WATCHDOG.schedule(this::restoreIfNeeded, 900, TimeUnit.MILLISECONDS);
            failure = "XMSF 规则已验证；等待通知提交后立即恢复";
            return true;
        } catch (Throwable e) {
            Log.w(TAG, "Unable to arm XMSF rule", e);
            failure = "Shizuku 写入防火墙失败：" + e.getClass().getSimpleName();
            return false;
        } // The publisher's finally ALWAYS calls restoreIfNeeded(), including failed arms.
        }
    }

    synchronized boolean restoreIfNeeded() {
        synchronized (ShizukuIslandBridge.class) {
        if (!armed && !recovery.getBoolean(PENDING, false)) return true;
        try {
            boolean restored = restoreSnapshot(service());
            if (!restored) activeWindow = false;
            return restored;
        } catch (Throwable e) {
            activeWindow = false;
            Log.e(TAG, "XMSF firewall recovery unavailable", e);
            failure = "警告：XMSF 网络规则待恢复；请启动 Shizuku 并返回本页重试";
            return false;
        }
        }
    }

    private boolean restoreSnapshot(Object remote) {
        int uid = recovery.getInt(TARGET_UID, -1);
        if (uid < Process.FIRST_APPLICATION_UID) return false;
        int oldRule = recovery.getInt(OLD_RULE, 0);
        boolean oldChain = recovery.getBoolean(OLD_CHAIN, false);
        try {
            // Clear our deny first; only then restore the chain's previous state.
            call(remote, "setUidFirewallRule", OEM_DENY_3, uid, oldRule);
            if (readRule(remote, uid) != oldRule) return false;
            call(remote, "setFirewallChainEnabled", OEM_DENY_3, oldChain);
            if (readChain(remote) != oldChain) return false;
            if (!recovery.edit().clear().commit()) return false;
            armed = false;
            activeWindow = false;
            failure = "XMSF 网络规则已恢复并通过读回验证";
            return true;
        } catch (Throwable e) {
            Log.e(TAG, "Failed to restore XMSF firewall snapshot", e);
            failure = "警告：XMSF 规则恢复失败：" + e.getClass().getSimpleName();
            return false;
        }
    }

    private int xmsfUid() throws PackageManager.NameNotFoundException {
        return app.getPackageManager().getApplicationInfo("com.xiaomi.xmsf", 0).uid;
    }

    private Object service() throws Exception {
        if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED)
            throw new SecurityException("Shizuku not authorized");
        IBinder original = SystemServiceHelper.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (original == null) throw new IllegalStateException("connectivity Binder missing");
        Class<?> stub = Class.forName("android.net.IConnectivityManager$Stub");
        Object remote = HiddenApiBypass.invoke(stub, null, "asInterface", new ShizukuBinderWrapper(original));
        if (remote == null) throw new IllegalStateException("Shizuku Binder proxy missing");
        return remote;
    }

    private int readRule(Object remote, int uid) throws Exception {
        return (Integer) call(remote, "getUidFirewallRule", OEM_DENY_3, uid);
    }

    private boolean readChain(Object remote) throws Exception {
        return (Boolean) call(remote, "getFirewallChainEnabled", OEM_DENY_3);
    }

    private Object call(Object remote, String name, Object... args) throws Exception {
        return HiddenApiBypass.invoke(Class.forName("android.net.IConnectivityManager"), remote, name, args);
    }
}
