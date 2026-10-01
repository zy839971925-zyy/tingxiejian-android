package com.example.tingxiejian;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/** First-run configuration stays in its own modal; never launches the app Settings activity. */
final class RealtimeSetupDialog {
    private final Activity activity;
    private final RealtimeDeviceProfile profile;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final SharedPreferences prefs;
    private final AlertDialog dialog;
    private final TextView status;
    private AlertDialog consent;
    private int refreshGeneration;
    private final rikka.shizuku.Shizuku.OnRequestPermissionResultListener permissionListener =
            (code, result) -> { if (code == 9715) ui.post(this::refresh); };

    RealtimeSetupDialog(Activity activity, Runnable notificationPermission) {
        this.activity = activity;
        profile = RealtimeDeviceProfile.of(Build.BRAND, Build.MANUFACTURER, Build.VERSION.SDK_INT);
        prefs = activity.getSharedPreferences(SettingsActivity.PREFS, 0);
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) Motion.dp(activity, 24);
        body.setPadding(pad, pad / 2, pad, pad / 2);
        text(body, profile.description());
        status = text(body, "正在检查通知状态…");
        if (profile.xiaomi) {
            Switch island = toggle(body, "转写时尝试超级岛", prefs.getBoolean("island", true));
            island.setOnCheckedChangeListener((v, checked) -> {
                prefs.edit().putBoolean("island", checked).apply(); refresh();
            });
            Switch bridge = toggle(body, "Shizuku 兼容方式（可选）", prefs.getBoolean("island_shizuku", false));
            TextView authorize = action(body, "授权 Shizuku");
            authorize.setEnabled(bridge.isChecked());
            bridge.setOnCheckedChangeListener((v, checked) -> {
                if (checked && !prefs.getBoolean("island_shizuku", false)) {
                    consent = new AlertDialog.Builder(activity).setTitle("启用 Shizuku 兼容方式？")
                            .setMessage("仅用于小米超级岛。提交时会短暂更改 XMSF 网络规则，可能影响同期推送或连接；"
                                    + "异常会尝试恢复，但 Shizuku 中断时可能需重新启动服务或手机。授权不能保证显示超级岛。")
                            .setNegativeButton("取消", (d, w) -> bridge.setChecked(false))
                            .setOnCancelListener(d -> bridge.setChecked(false))
                            .setPositiveButton("了解并启用", (d, w) -> {
                                prefs.edit().putBoolean("island_shizuku", true).apply();
                                authorize.setEnabled(true); refresh();
                            }).show();
                } else {
                    prefs.edit().putBoolean("island_shizuku", checked).apply();
                    authorize.setEnabled(checked); refresh();
                }
            });
            authorize.setOnClickListener(v -> {
                if (!ShizukuIslandBridge.requestPermission()) {
                    // Keep onboarding in place. Opening the separate manager is an explicit choice.
                    consent = new AlertDialog.Builder(activity).setTitle("先启动 Shizuku")
                            .setMessage("在 Shizuku 中通过无线调试启动服务，然后回到这个弹窗点击授权。也可以跳过，使用普通通知。")
                            .setNegativeButton("暂时跳过", null)
                            .setPositiveButton("打开 Shizuku", (d, w) -> {
                                try {
                                    Intent launch = activity.getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
                                    if (launch != null) activity.startActivity(launch);
                                    else status.setText("尚未安装 Shizuku；普通通知与转写不受影响。");
                                } catch (RuntimeException error) { status.setText("无法打开 Shizuku，请手动启动；可继续使用普通通知。"); }
                            }).show();
                }
            });
        }
        Switch live = toggle(body, profile.standardLabel(), prefs.getBoolean(AndroidLiveUpdateCapability.PREF, true));
        live.setEnabled(profile.standardLiveUpdate);
        live.setOnCheckedChangeListener((v, checked) -> {
            prefs.edit().putBoolean(AndroidLiveUpdateCapability.PREF, checked).apply(); refresh();
        });
        action(body, "允许 / 管理系统通知").setOnClickListener(v -> notificationPermission.run());
        if (profile.standardLiveUpdate) action(body, "系统实时活动权限").setOnClickListener(v -> {
            Intent destination = new Intent("android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS")
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, activity.getPackageName());
            if (destination.resolveActivity(activity.getPackageManager()) == null)
                destination = new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, activity.getPackageName());
            try { activity.startActivity(destination); }
            catch (RuntimeException error) { status.setText("系统未提供此入口，请在系统通知设置中管理实时活动。"); }
        });
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(body);
        dialog = new AlertDialog.Builder(activity).setTitle(profile.title()).setView(scroll)
                .setPositiveButton("完成，继续引导", null).create();
        dialog.setOnDismissListener(d -> {
            refreshGeneration++;
            if (consent != null) consent.dismiss();
            if (profile.xiaomi) try { rikka.shizuku.Shizuku.removeRequestPermissionResultListener(permissionListener); }
            catch (Throwable ignored) { }
        });
    }
    void show() {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        if (profile.xiaomi) try { rikka.shizuku.Shizuku.addRequestPermissionResultListener(permissionListener); }
        catch (Throwable error) { Report.problem("引导 Shizuku 回调不可用", error); }
        dialog.show(); refresh();
    }
    void dismiss() { dialog.dismiss(); }
    void refresh() {
        if (!dialog.isShowing() || activity.isFinishing() || activity.isDestroyed()) return;
        NotificationManager manager = (NotificationManager) activity.getSystemService(Activity.NOTIFICATION_SERVICE);
        String ordinary = manager != null && manager.areNotificationsEnabled() ? "系统通知：已允许" : "系统通知：尚未允许";
        String standard = AndroidLiveUpdateCapability.inspect(activity, manager, LocalService.progressChannel()).summary();
        String base = ordinary + "\n" + standard;
        status.setText(base);
        if (!profile.xiaomi) return; // No Shizuku listener or Xiaomi probe on any other OEM.
        final int generation = ++refreshGeneration;
        new Thread(() -> {
            String island;
            try {
                XiaomiIslandCapability.invalidateCache();
                island = "超级岛：" + (prefs.getBoolean("island", true)
                        ? XiaomiIslandCapability.info(activity).summary() : "用户已关闭")
                        + "\n" + ShizukuIslandBridge.permissionState(activity);
            } catch (Throwable error) { island = "超级岛检测失败，保留系统通知。"; }
            final String result = base + "\n" + island;
            ui.post(() -> {
                if (generation == refreshGeneration && dialog.isShowing()
                        && !activity.isFinishing() && !activity.isDestroyed()) status.setText(result);
            });
        }, "guide-island-status").start();
    }
    private TextView text(LinearLayout body, String value) {
        TextView view = new TextView(activity);
        view.setText(value); view.setTextSize(14); view.setTextColor(activity.getColor(R.color.ink));
        view.setPadding(0, (int) Motion.dp(activity, 10), 0, (int) Motion.dp(activity, 10));
        body.addView(view, new LinearLayout.LayoutParams(-1, -2)); return view;
    }
    private Switch toggle(LinearLayout body, String label, boolean checked) {
        Switch view = new Switch(activity);
        view.setText(label); view.setTextSize(16); view.setTextColor(activity.getColor(R.color.ink));
        view.setMinHeight((int) Motion.dp(activity, 56)); view.setChecked(checked);
        body.addView(view, new LinearLayout.LayoutParams(-1, -2)); return view;
    }
    private TextView action(LinearLayout body, String label) {
        TextView view = text(body, label);
        view.setTextSize(16); view.setGravity(Gravity.CENTER);
        view.setBackgroundResource(R.drawable.bg_row);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) view.getLayoutParams();
        params.topMargin = (int) Motion.dp(activity, 8); view.setLayoutParams(params);
        // Listener is installed by caller; explicitly provide semantics now.
        UiControls.button(view); return view;
    }
}
