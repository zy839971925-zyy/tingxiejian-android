package com.example.tingxiejian;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.IConnectivityManager;
import rikka.shizuku.Shizuku;
import rikka.shizuku.SystemServiceHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Executes production bridge/gate methods against a deterministic privileged Binder fake. */
public final class IslandBridgeBehaviorCheck {
    private static final int XMSF_UID = 10123;
    private static int cases;

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class MemoryPreferences implements SharedPreferences {
        final Map<String, Object> values = new HashMap<>();
        boolean failCommit;
        public synchronized boolean getBoolean(String key, boolean fallback) {
            Object value = values.get(key);
            return value instanceof Boolean ? (Boolean) value : fallback;
        }
        public synchronized int getInt(String key, int fallback) {
            Object value = values.get(key);
            return value instanceof Integer ? (Integer) value : fallback;
        }
        public Editor edit() {
            return new Editor() {
                final Map<String, Object> changes = new HashMap<>();
                boolean clear;
                public Editor putBoolean(String key, boolean value) { changes.put(key, value); return this; }
                public Editor putInt(String key, int value) { changes.put(key, value); return this; }
                public Editor clear() { clear = true; return this; }
                public boolean commit() {
                    synchronized (MemoryPreferences.this) {
                        if (failCommit) return false;
                        if (clear) values.clear();
                        values.putAll(changes);
                        return true;
                    }
                }
            };
        }
    }

    private static final class TestContext extends Context {
        final Map<String, MemoryPreferences> preferences = new HashMap<>();
        boolean xmsfMissing;
        public SharedPreferences getSharedPreferences(String name, int mode) {
            return preferences.computeIfAbsent(name, key -> new MemoryPreferences());
        }
        MemoryPreferences recovery() {
            return (MemoryPreferences) getSharedPreferences("island_firewall_recovery", MODE_PRIVATE);
        }
        void optIn(boolean value) {
            getSharedPreferences(SettingsActivity.PREFS, MODE_PRIVATE).edit()
                    .putBoolean("island_shizuku", value).commit();
        }
        public PackageManager getPackageManager() {
            return new PackageManager() {
                public ApplicationInfo getApplicationInfo(String name, int flags) throws NameNotFoundException {
                    check(name.equals("com.xiaomi.xmsf"), "target must be XMSF");
                    if (xmsfMissing) throw new NameNotFoundException();
                    ApplicationInfo info = new ApplicationInfo();
                    info.uid = XMSF_UID;
                    return info;
                }
            };
        }
    }

    public static final class Firewall extends IConnectivityManager {
        final TestContext context;
        final List<String> writes = new ArrayList<>();
        boolean chain;
        int xmsfRule;
        final int otherUidRule = 2;
        boolean ignoreDeny;
        boolean failDeny;
        boolean ignoreRestore;
        boolean snapshotSeenBeforeWrite = true;
        Firewall(TestContext context) { this.context = context; }
        public int getUidFirewallRule(int id, int uid) {
            check(id == 9 && uid == XMSF_UID, "only OEM_DENY_3 XMSF rule is read");
            return xmsfRule;
        }
        public boolean getFirewallChainEnabled(int id) { check(id == 9, "chain 9"); return chain; }
        private void writing() {
            snapshotSeenBeforeWrite &= context.recovery().getBoolean("pending", false);
        }
        public void setUidFirewallRule(int id, int uid, int rule) {
            check(id == 9 && uid == XMSF_UID, "only the XMSF UID rule is written");
            writing();
            writes.add("rule:" + rule);
            if (rule == 2 && failDeny) throw new IllegalStateException("simulated Binder write failure");
            if ((rule == 2 && ignoreDeny) || (rule != 2 && ignoreRestore)) return;
            xmsfRule = rule;
        }
        public void setFirewallChainEnabled(int id, boolean enabled) {
            check(id == 9, "chain 9");
            writing();
            writes.add("chain:" + enabled);
            chain = enabled;
        }
        boolean otherUidBlocked() { return chain && otherUidRule == 2; }
    }

    private static final class Environment {
        final TestContext context = new TestContext();
        final Firewall firewall = new Firewall(context);
        final ShizukuIslandBridge bridge;
        Environment() {
            Shizuku.running = true;
            Shizuku.permission = PackageManager.PERMISSION_GRANTED;
            Shizuku.uid = 2000;
            SystemServiceHelper.binderAvailable = true;
            SystemServiceHelper.remote = firewall;
            context.optIn(true);
            bridge = new ShizukuIslandBridge(context);
        }
    }

    private static void disabledChainLegacySuccess() {
        Environment env = new Environment();
        check(env.bridge.isSupported(), "readable disabled chain remains eligible in baseline");
        check(env.firewall.writes.isEmpty(), "capability probe must not write");
        check(!env.firewall.otherUidBlocked(), "other UID initially not blocked");
        check(env.bridge.setXmsfBlocked(true), "baseline legacy arm succeeds from disabled chain");
        check(env.firewall.chain && env.firewall.xmsfRule == 2, "chain enable and XMSF deny");
        check(env.firewall.otherUidBlocked(), "global chain also activates other UID's existing deny");
        check(env.bridge.windowHeld(), "readback-verified window held");
        check(env.context.recovery().getBoolean("pending", false), "pending snapshot survives arm");
        check(!env.context.recovery().getBoolean("old_chain", true), "old disabled chain saved");
        check(env.bridge.restoreIfNeeded(), "restore succeeds");
        check(env.firewall.writes.equals(Arrays.asList("chain:true", "rule:2", "rule:0", "chain:false")),
                "arm/restore ordering must remain baseline");
        check(env.firewall.snapshotSeenBeforeWrite, "every write occurs after persisted recovery snapshot");
        check(!env.firewall.chain && env.firewall.xmsfRule == 0, "original states restored");
        check(env.context.recovery().values.isEmpty() && !env.bridge.windowHeld(), "clear only after readback");
        check(env.bridge.restoreIfNeeded(), "restore is idempotent");
        cases++;
    }

    private static void enabledChainAndExistingRule() {
        Environment env = new Environment();
        env.firewall.chain = true;
        env.firewall.xmsfRule = 1;
        check(env.bridge.setXmsfBlocked(true), "enabled chain / previous allow can arm");
        check(env.bridge.restoreIfNeeded(), "enabled chain restored");
        check(env.firewall.chain && env.firewall.xmsfRule == 1, "previous allow + enabled preserved");
        env.firewall.xmsfRule = 2;
        env.firewall.writes.clear();
        check(!env.bridge.setXmsfBlocked(true), "already denied by active chain cannot be overwritten");
        check(env.firewall.writes.isEmpty() && env.context.recovery().values.isEmpty(), "decline has no writes");
        cases++;
    }

    private static void rejectedPreconditions() {
        Environment env = new Environment();
        env.context.optIn(false);
        check(!env.bridge.setXmsfBlocked(true), "opt-out declines");
        env.context.optIn(true);
        Shizuku.permission = -1;
        check(!env.bridge.setXmsfBlocked(true), "unauthorized declines");
        Shizuku.permission = 0;
        Shizuku.uid = 12345;
        check(!env.bridge.setXmsfBlocked(true), "non shell/root declines");
        Shizuku.uid = 2000;
        Shizuku.running = false;
        check(!env.bridge.setXmsfBlocked(true), "Binder offline declines");
        Shizuku.running = true;
        env.context.xmsfMissing = true;
        check(!env.bridge.setXmsfBlocked(true), "missing XMSF declines");
        env.context.xmsfMissing = false;
        SystemServiceHelper.binderAvailable = false;
        check(!env.bridge.setXmsfBlocked(true), "missing system Binder declines");
        check(env.firewall.writes.isEmpty(), "precondition failures never mutate firewall");
        cases++;
    }

    private static void commitBeforeMutate() {
        Environment env = new Environment();
        env.context.recovery().failCommit = true;
        check(!env.bridge.setXmsfBlocked(true), "snapshot commit failure declines");
        check(env.firewall.writes.isEmpty(), "never write without durable recovery record");
        check(env.bridge.restoreIfNeeded(), "no partial arm needs recovery");
        cases++;
    }

    private static void failedArmRestorable() {
        for (boolean throwsOnDeny : new boolean[] {false, true}) {
            Environment env = new Environment();
            env.firewall.ignoreDeny = !throwsOnDeny;
            env.firewall.failDeny = throwsOnDeny;
            XiaomiXmsfValidationGate gate = new XiaomiXmsfValidationGate(env.context);
            check(!gate.setXmsfBlocked(true), "failed deny/readback reports failed arm");
            check(env.context.recovery().getBoolean("pending", false), "partial chain enable has recovery snapshot");
            check(gate.restoreIfNeeded(), "publisher finally can recover a failed partial arm");
            check(!env.firewall.chain && env.firewall.xmsfRule == 0, "partial arm restored");
            cases++;
        }
    }

    private static void restorationFailureIsRetryable() {
        Environment env = new Environment();
        check(env.bridge.setXmsfBlocked(true), "arm before restore readback failure");
        env.firewall.ignoreRestore = true;
        check(!env.bridge.restoreIfNeeded(), "silent failed restore is detected by readback");
        check(env.context.recovery().getBoolean("pending", false), "failed restore retains recovery snapshot");
        env.firewall.ignoreRestore = false;
        Shizuku.running = false;
        check(!env.bridge.restoreIfNeeded(), "Shizuku down cannot restore");
        check(ShizukuIslandBridge.recoveryPending(env.context), "Binder loss retains pending state");
        Shizuku.running = true;
        check(env.bridge.restoreIfNeeded(), "restore recovers after authorization service returns");
        cases++;
    }

    private static void staleRecoveryEvenAfterOptOut() {
        Environment env = new Environment();
        env.context.optIn(false);
        env.firewall.chain = true;
        env.firewall.xmsfRule = 2;
        env.context.recovery().edit().putInt("target_uid", XMSF_UID).putInt("old_rule", 1)
                .putBoolean("old_chain", false).putBoolean("pending", true).commit();
        check(!env.bridge.isSupported(), "opt-out declines new posts after stale repair");
        check(env.firewall.xmsfRule == 1 && !env.firewall.chain, "stale snapshot repair occurs before opt-in check");
        check(!ShizukuIslandBridge.recoveryPending(env.context), "stale recovery cleared after successful readback");
        cases++;
    }

    private static void anotherInstanceCannotInterruptWindow() {
        Environment env = new Environment();
        check(env.bridge.setXmsfBlocked(true), "first instance arm");
        ShizukuIslandBridge other = new ShizukuIslandBridge(env.context);
        int writes = env.firewall.writes.size();
        check(!other.isSupported() && !other.setXmsfBlocked(true), "second instance cannot arm/recover live window");
        check(env.firewall.writes.size() == writes && env.bridge.windowHeld(), "probe did not interrupt active gate");
        check(env.bridge.restoreIfNeeded(), "owner restores");
        cases++;
    }

    private static void watchdogRestoresAbandonedWindow() throws Exception {
        Environment env = new Environment();
        check(env.bridge.setXmsfBlocked(true), "arm abandoned window");
        long deadline = System.nanoTime() + 2_500_000_000L;
        while (ShizukuIslandBridge.recoveryPending(env.context) && System.nanoTime() < deadline)
            Thread.sleep(20);
        check(!ShizukuIslandBridge.recoveryPending(env.context), "900ms watchdog eventually restores");
        check(!env.bridge.windowHeld() && !env.firewall.chain && env.firewall.xmsfRule == 0,
                "watchdog restores original states");
        cases++;
    }

    public static void main(String[] args) throws Exception {
        disabledChainLegacySuccess();
        enabledChainAndExistingRule();
        rejectedPreconditions();
        commitBeforeMutate();
        failedArmRestorable();
        restorationFailureIsRetryable();
        staleRecoveryEvenAfterOptOut();
        anotherInstanceCannotInterruptWindow();
        watchdogRestoresAbandonedWindow();
        System.out.println("PASS: shipped Island bridge behavior (" + cases + " scenarios, including legacy global-chain activation)");
    }
}
