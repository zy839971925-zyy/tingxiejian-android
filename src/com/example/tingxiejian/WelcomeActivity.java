package com.example.tingxiejian;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;

/** First-use guidance, not a prerequisite for local transcription or an automatic privilege grant. */
public class WelcomeActivity extends Activity {
    private static final String PREFS = "first_run";
    private static final String COMPLETED = "completed";
    private static final String FROM_SETTINGS = "from_settings";
    private static final int NOTIFICATION_REQUEST = 72;
    private boolean permissionRequested;
    private TextView notificationAction;

    /** Existing v0.17 installations go straight to their familiar home screen after an update. */
    static boolean shouldShow(Context context) {
        if (context.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(COMPLETED, false)) return false;
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            if (info.lastUpdateTime - info.firstInstallTime > 2000L) {
                context.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(COMPLETED, true).apply();
                return false;
            }
        } catch (Throwable error) {
            Report.problem("检查首次安装状态失败", error);
        }
        return true;
    }

    static void openGuide(Activity activity) {
        activity.startActivity(new Intent(activity, WelcomeActivity.class).putExtra(FROM_SETTINGS, true));
    }

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(UiTheme.wrap(base));
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            setContentView(R.layout.activity_welcome);
            UiTheme.padForSystemBars(this, need(R.id.guide_root));
            notificationAction = need(R.id.notification_permission);
            notificationAction.setOnClickListener(v -> requestNotification());
            need(R.id.shizuku_settings).setOnClickListener(v -> {
                try { startActivity(new Intent(this, SettingsActivity.class)); }
                catch (Throwable error) { Report.problem("打开超级岛设置失败", error); }
            });
            need(R.id.terms_review).setOnClickListener(v -> Consent.show(this, null));
            need(R.id.guide_start).setOnClickListener(v -> finishSetup());
            need(R.id.guide_skip).setOnClickListener(v -> finishSetup());
            // One calm entrance for the numbered cards, then the page stays still.
            View scroll = need(R.id.guide_scroll);
            if (scroll instanceof android.view.ViewGroup
                    && ((android.view.ViewGroup) scroll).getChildCount() > 0
                    && ((android.view.ViewGroup) scroll).getChildAt(0) instanceof android.view.ViewGroup) {
                Motion.stagger((android.view.ViewGroup) ((android.view.ViewGroup) scroll).getChildAt(0), 55);
            }
            Report.mark("first-run guide ready");
            Report.flush(this);
        } catch (Throwable error) {
            Report.problem("首次使用指南初始化失败", error);
            // A broken optional guide must never block the already-working offline app.
            finishSetup();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (notificationAction != null) refreshPermission();
    }

    private <T extends View> T need(int id) {
        T view = findViewById(id);
        if (view == null) throw new IllegalStateException("缺少指南视图 " + id);
        return view;
    }

    private void refreshPermission() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        boolean enabled = manager != null && manager.areNotificationsEnabled();
        notificationAction.setText(enabled ? "已允许 · 管理通知"
                : permissionRequested ? "前往系统通知设置" : "允许通知");
    }

    private void requestNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null && manager.areNotificationsEnabled()) {
            openNotificationSettings();
        } else if (Build.VERSION.SDK_INT >= 33 && !permissionRequested
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            permissionRequested = true;
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_REQUEST);
        } else {
            openNotificationSettings();
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == NOTIFICATION_REQUEST && notificationAction != null) refreshPermission();
    }

    private void openNotificationSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
        } catch (Throwable error) {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            } catch (Throwable second) {
                Report.problem("打开系统通知设置失败", second);
            }
        }
    }

    private void finishSetup() {
        if (getIntent() != null && getIntent().getBooleanExtra(FROM_SETTINGS, false)) {
            finish();
            return;
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(COMPLETED, true).commit();
        startActivity(new Intent(this, MainActivity.class).putExtra("skip_welcome_once", true));
        finish();
    }

    @Override public void onBackPressed() { finishSetup(); }
}
