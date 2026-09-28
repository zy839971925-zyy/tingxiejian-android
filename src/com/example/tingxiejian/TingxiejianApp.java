package com.example.tingxiejian;

import android.app.Application;
import android.content.Context;

/**
 * Installs the crash reporter as early as Android allows, so a failure while the first Activity is
 * being created is still written to Downloads instead of vanishing with the process.
 */
public final class TingxiejianApp extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        Report.install(base);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Motion.init(this);
        Report.mark("application.onCreate");
        // If a process died during the short XMSF window, attempt recovery immediately and again
        // whenever Shizuku reconnects. Never block application startup or the transcription UI.
        if (ShizukuIslandBridge.recoveryPending(this)) {
            new Thread(() -> new ShizukuIslandBridge(this).restoreIfNeeded(),
                    "island-firewall-repair").start();
        }
        try {
            rikka.shizuku.Shizuku.addBinderReceivedListenerSticky(() -> {
                if (ShizukuIslandBridge.recoveryPending(this))
                    new Thread(() -> new ShizukuIslandBridge(this).restoreIfNeeded(),
                            "island-firewall-reconnect").start();
            });
        } catch (Throwable error) {
            Report.problem("Shizuku 连接监听不可用", error);
        }
        Report.flush(this);
    }
}
