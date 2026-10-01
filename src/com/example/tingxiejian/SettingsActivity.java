package com.example.tingxiejian;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

/**
 * Settings: recognition options, appearance, the island switch with its real status,
 * model preparation, and the optional cloud AI connection (presets + custom + test).
 */
public class SettingsActivity extends Activity {
    static final String PREFS = "ui";

    private final Handler ui = new Handler(Looper.getMainLooper());

    private LinearLayout speakerSeg, appearanceSeg, providerSeg;
    private Switch diarize, islandSwitch, shizukuIslandSwitch, cloudAsrSwitch, reduceMotionSwitch;
    private View islandDot, islandTest;
    private TextView islandDetail, shizukuStatus, modelStatus, modelButton, cloudStatus, versionLine;
    private final rikka.shizuku.Shizuku.OnRequestPermissionResultListener shizukuPermissionListener =
            (requestCode, result) -> {
                if (requestCode == 9715 && shizukuStatus != null) {
                    XiaomiIslandCapability.invalidateCache();
                    refreshIslandStatus();
                }
            };
    private EditText cloudKey, cloudUrl, cloudModel, cloudAsrModel;

    private static final int REQ_QWEN_IMPORT = 106;
    private EditText userHotwords;
    private TextView realtimeStatus;
    private TextView localModelChoice, qwenStatus, prepareQwen;
    private boolean qwenBusy;
    private RealtimeDeviceProfile deviceProfile;
    private int speakerCount;
    private String provider;
    private boolean testing;
    private boolean cloudKeyDirty;
    private boolean cloudFieldsReady;
    private final Runnable saveCloudMetadata = () -> persistCloud(false);
    private volatile boolean islandTesting;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(UiTheme.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_settings);
            PortalTransition.install(this, findViewById(R.id.portal_surface),
                    findViewById(R.id.portal_content), savedInstanceState);
            UiTheme.padForSystemBars(this, findViewById(R.id.portal_content));
            Report.mark("settings.onCreate");
            bindViews();
            step("读取设置", this::loadState);
            step("绑定交互", this::wireActions);
            step("刷新模型状态", this::refreshModelStatus);
            String version = "当前版本";
            try {
                version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            } catch (android.content.pm.PackageManager.NameNotFoundException ignored) { }
            versionLine.setText("版本 " + version
                    + " · 本地识别优先；保存 Key 后自动校正文字，Shizuku 仅用于可选超级岛");
            deviceProfile = RealtimeDeviceProfile.of(android.os.Build.BRAND, android.os.Build.MANUFACTURER, android.os.Build.VERSION.SDK_INT);
            need(R.id.xiaomi_island_section).setVisibility(deviceProfile.xiaomi ? View.VISIBLE : View.GONE);
            setupRecognitionOptions();
            setupRealtimeStatus();
            setupMotionRhythm();
            UiControls.apply(findViewById(R.id.portal_content));
            try { if (deviceProfile.xiaomi) rikka.shizuku.Shizuku.addRequestPermissionResultListener(shizukuPermissionListener); }
            catch (Throwable error) { Report.problem("Shizuku 授权回调不可用", error); }
            Report.mark("settings.onCreate complete");
            Report.flush(this);
        } catch (Throwable error) {
            Report.problem("设置页初始化失败", error);
            TextView fallback = new TextView(this);
            fallback.setText(Report.crashText("设置页初始化失败", error));
            fallback.setTextIsSelectable(true);
            fallback.setPadding(32, 32, 32, 32);
            fallback.setTextSize(11);
            setContentView(fallback);
        }
    }

    @Override
    protected void onPause() {
        if (cloudFieldsReady) {
            ui.removeCallbacks(saveCloudMetadata);
            persistCloud(true);
        }
        if (userHotwords != null) prefs().edit().putString("hotwords", userHotwords.getText().toString()).apply();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacks(saveCloudMetadata);
        try { if (deviceProfile != null && deviceProfile.xiaomi) rikka.shizuku.Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener); }
        catch (Throwable ignored) { }
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (islandDetail != null && !islandTesting) step("刷新超级岛状态", this::refreshIslandStatus);
        if (realtimeStatus != null) refreshRealtimeStatus();
        if (qwenStatus != null && !qwenBusy) refreshQwenStatus();
    }

    /** Each step fails on its own: a broken island probe must not blank the settings screen. */
    private void step(String what, Runnable work) {
        try {
            work.run();
        } catch (Throwable error) {
            Report.problem("设置页 · " + what, error);
            if (cloudStatus != null) {
                cloudStatus.setText(what + "出错：" + error.getMessage());
            }
        }
    }

    private void bindViews() {
        View back = need(R.id.back);
        speakerSeg = need(R.id.speaker_seg);
        appearanceSeg = need(R.id.appearance_seg);
        providerSeg = need(R.id.cloud_provider_seg);
        diarize = need(R.id.diarize);
        reduceMotionSwitch = need(R.id.reduce_motion);
        cloudAsrSwitch = need(R.id.cloud_asr);
        islandSwitch = need(R.id.island_switch);
        shizukuIslandSwitch = need(R.id.shizuku_island_switch);
        shizukuStatus = need(R.id.shizuku_status);
        islandDot = need(R.id.island_dot);
        islandDetail = need(R.id.island_detail);
        islandDetail.setTextIsSelectable(true);
        islandTest = need(R.id.island_test);
        modelStatus = need(R.id.model_status);
        modelButton = need(R.id.model_button);
        cloudStatus = need(R.id.cloud_status);
        versionLine = need(R.id.version_line);
        cloudKey = need(R.id.cloud_key);
        cloudUrl = need(R.id.cloud_url);
        cloudModel = need(R.id.cloud_model);
        cloudAsrModel = need(R.id.cloud_asr_model);
        back.setOnClickListener(v -> PortalTransition.close(this));
    }

    private <T extends View> T need(int id) {
        T view = findViewById(id);
        if (view == null) {
            throw new IllegalStateException("视图 " + getResources().getResourceEntryName(id) + " 找不到");
        }
        return view;
    }

    private void loadState() {
        SharedPreferences prefs = prefs();
        diarize.setChecked(prefs.getBoolean("diarize", true));
        reduceMotionSwitch.setChecked(Motion.reduced());
        speakerCount = prefs.getInt("speakerCount", 0);
        islandSwitch.setChecked(prefs.getBoolean("island", true));
        shizukuIslandSwitch.setChecked(prefs.getBoolean("island_shizuku", false));
        cloudAsrSwitch.setChecked(prefs.getBoolean("cloudAsr", false));
        provider = Cloud.provider(this);
        // Passwords must not enter the Activity instance-state Bundle or autofill snapshots.
        cloudKey.setSaveEnabled(false);
        cloudKey.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        cloudKey.setText(Cloud.apiKey(this));
        cloudUrl.setText(Cloud.baseUrl(this));
        cloudModel.setText(Cloud.chatModel(this));
        cloudAsrModel.setText(Cloud.asrModel(this));
        cloudModel.setHint(Cloud.modelHint(provider));
        cloudAsrModel.setHint(Cloud.asrHint(provider));
        buildSpeakerChips();
        buildAppearanceChips();
        buildProviderChips();
    }

    private void wireActions() {
        View guide = need(R.id.guide_entry);
        Motion.press(guide);
        guide.setOnClickListener(v -> step("打开首次使用指南", () -> WelcomeActivity.openGuide(this)));
        diarize.setOnCheckedChangeListener((button, checked) -> prefs().edit().putBoolean("diarize", checked).apply());
        reduceMotionSwitch.setOnCheckedChangeListener((button, checked) -> Motion.setReduced(this, checked));
        cloudAsrSwitch.setOnCheckedChangeListener((button, checked) -> {
            prefs().edit().putBoolean("cloudAsr", checked).apply();
            cloudStatus.setText(checked
                    ? "转写时会把音频分段发到云端识别（本地识别仍可用，可随时关掉）。"
                    : "音频只在本地识别；已保存 API Key 时仍会自动发送文本做轻度校正。");
        });
        islandSwitch.setOnCheckedChangeListener((button, checked) -> {
            prefs().edit().putBoolean("island", checked).apply();
            islandDetail.setAlpha(checked ? 1f : 0.5f);
        });
        shizukuIslandSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (checked && !prefs().getBoolean("island_shizuku", false)) {
                new android.app.AlertDialog.Builder(this)
                        .setTitle("启用实验性 Shizuku 门禁？")
                        .setMessage("每次尝试超级岛时会短暂更改系统 XMSF 网络规则，可能影响其他应用的连接与推送。"
                                + "异常时会尝试恢复；若 Shizuku 中断，请重启它并回到本页，必要时重启手机。"
                                + "这也不能保证系统显示超级岛。")
                        .setNegativeButton("取消", (dialog, which) -> shizukuIslandSwitch.setChecked(false))
                        .setOnCancelListener(dialog -> shizukuIslandSwitch.setChecked(false))
                        .setPositiveButton("我了解，启用", (dialog, which) -> setShizukuIslandEnabled(true))
                        .show();
                return;
            }
            setShizukuIslandEnabled(checked);
        });
        need(R.id.shizuku_authorize).setOnClickListener(v -> {
            if (!ShizukuIslandBridge.requestPermission()) {
                try {
                    Intent launch = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
                    if (launch != null) startActivity(launch);
                    else shizukuStatus.setText("请先安装并通过无线调试启动 Shizuku，再回到此页授权。");
                } catch (Throwable e) {
                    shizukuStatus.setText("请先在 Shizuku 应用里启动服务，再回来授权。");
                }
            } else {
                shizukuStatus.setText("已请求 Shizuku 权限；请确认授权弹窗。");
            }
        });
        islandTest.setOnClickListener(v -> step("测试超级岛", this::testIsland));
        need(R.id.island_permission_settings).setOnClickListener(v -> step("打开系统通知设置", () -> {
            Intent settings = new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(settings);
        }));
        TextWatcher rememberMetadata = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable s) {
                ui.removeCallbacks(saveCloudMetadata);
                ui.postDelayed(saveCloudMetadata, 500L);
            }
        };
        cloudKey.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable s) {
                cloudKeyDirty = true;
                cloudStatus.setText("API Key 尚未保存；离开输入框或点击保存并测试后生效。保存后转写文本会自动分段发送以轻度校正。");
            }
        });
        cloudKey.setOnFocusChangeListener((view, focused) -> {
            if (!focused && cloudKeyDirty) persistCloud(true);
        });
        cloudUrl.addTextChangedListener(rememberMetadata);
        cloudModel.addTextChangedListener(rememberMetadata);
        cloudAsrModel.addTextChangedListener(rememberMetadata);
        cloudFieldsReady = true;
        cloudStatus.setText(configSummary());

        ((TextView) need(R.id.cloud_test)).setText("保存并测试连接");
        click(need(R.id.cloud_test), "保存并测试云端连接", this::testCloud);
        click(need(R.id.cloud_clear), "清空云端配置", () -> {
            ui.removeCallbacks(saveCloudMetadata);
            if (!Cloud.clear(this)) {
                cloudStatus.setText(Cloud.secretStatus(this));
                return;
            }
            cloudKey.setText("");
            cloudKeyDirty = false;
            cloudUrl.setText("");
            cloudModel.setText(Cloud.preset(provider).chatModel);
            cloudAsrModel.setText(Cloud.preset(provider).asrModel);
            Cloud.saveMetadata(this, provider, "", Cloud.preset(provider).chatModel, Cloud.preset(provider).asrModel);
            prefs().edit().putBoolean("cloudAsr", false).apply();
            cloudAsrSwitch.setChecked(false);
            cloudStatus.setText("配置已清空；自动文本校正已跳过，本应用不会联网。");
        });
        click(modelButton, "准备模型", this::prepareModels);
        click(need(R.id.diagnostics_view), "查看本机诊断", this::showDiagnostics);
        click(need(R.id.clear_audio_cache), "清除临时音频", this::clearAudioCache);
    }

    private void clearAudioCache() {
        if (LocalService.isBusy()) {
            android.widget.Toast.makeText(this, "请等待转写结束再清理临时音频", android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        new android.app.AlertDialog.Builder(this).setTitle("清除临时音频？")
                .setMessage("试听将不再可用；已保存的文字记录不会删除。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清除", (dialog, which) -> {
                    // The job may have started while the confirmation dialog was open.
                    if (LocalService.isBusy()) {
                        android.widget.Toast.makeText(this, "转写已开始，未清除音频",
                                android.widget.Toast.LENGTH_LONG).show();
                        return;
                    }
                    int removed = 0;
                    File[] files = getCacheDir().listFiles();
                    if (files != null) for (File file : files) {
                        if ((file.getName().startsWith("input-") && file.getName().endsWith(".audio"))
                                || file.getName().equals("input")) {
                            if (file.delete()) removed++;
                        }
                    }
                    android.widget.Toast.makeText(this, "已清除 " + removed + " 个临时音频文件",
                            android.widget.Toast.LENGTH_SHORT).show();
                }).show();
    }

    private void showDiagnostics() {
        StringBuilder body = new StringBuilder("仅显示在本机。日志可能含文件名、设备信息或服务端错误；分享前请脱敏。\n");
        for (String name : new String[]{"tingxiejian-problem.txt", "tingxiejian-last-crash.txt",
                "tingxiejian-island-status.txt", "tingxiejian-layout.json", "tingxiejian-boot.txt"}) {
            File file = new File(getFilesDir(), name);
            if (!file.isFile()) continue;
            body.append("\n--- ").append(name).append(" ---\n");
            try {
                if (file.length() > 64 * 1024) body.append("日志过大，暂不显示");
                else body.append(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            } catch (Exception error) {
                body.append("读取失败：").append(error.getClass().getSimpleName());
            }
        }
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setTitle("本机诊断 · 请勿直接分享")
                .setMessage(body.toString()).setPositiveButton("关闭", null).create();
        dialog.show();
        TextView message = dialog.findViewById(android.R.id.message);
        if (message != null) message.setTextIsSelectable(true);
    }

    private void setShizukuIslandEnabled(boolean checked) {
        prefs().edit().putBoolean("island_shizuku", checked).apply();
        XiaomiIslandCapability.invalidateCache();
        shizukuStatus.setText(checked
                ? "已同意实验门禁；请启动并授权 Shizuku。网络规则会在通知提交后尝试恢复。"
                : "Shizuku 门禁已关闭，离线转写不受影响；未获官方授权时仍可能只有普通通知。");
        refreshIslandStatus();
    }

    private void click(View view, String what, Runnable action) {
        view.setOnClickListener(v -> {
            try {
                action.run();
            } catch (Throwable error) {
                Report.problem(what, error);
                cloudStatus.setText(what + "失败：" + error.getMessage());
            }
        });
    }

    // ---------------------------------------------------------------- chips

    private void setupRealtimeStatus() {
        ((TextView) need(R.id.realtime_title)).setText("实时进度 · " + deviceProfile.title());
        Switch enabled=need(R.id.live_updates);
        enabled.setText(deviceProfile.standardLabel());
        enabled.setEnabled(deviceProfile.standardLiveUpdate);
        realtimeStatus=need(R.id.realtime_status);
        enabled.setChecked(prefs().getBoolean(AndroidLiveUpdateCapability.PREF,true));
        enabled.setOnCheckedChangeListener((button,checked)->{
            prefs().edit().putBoolean(AndroidLiveUpdateCapability.PREF,checked).apply();refreshRealtimeStatus();
        });
        need(R.id.promotion_settings).setOnClickListener(v->{
            Intent destination=new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName());
            if(android.os.Build.VERSION.SDK_INT>=36){
                // Official standard intent; the constant is absent from the base API36 stub jar.
                Intent promotion=new Intent("android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS")
                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName());
                if(promotion.resolveActivity(getPackageManager())!=null)destination=promotion;
            }
            try{startActivity(destination);}catch(android.content.ActivityNotFoundException|SecurityException error){
                realtimeStatus.setText("系统未提供此设置入口，请在系统应用通知页检查实时活动权限。");
            }
        });
        refreshRealtimeStatus();
    }

    private void refreshRealtimeStatus() {
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        realtimeStatus.setText(AndroidLiveUpdateCapability.inspect(this,nm,LocalService.progressChannel()).summary()
                +"\n最近提交："+AndroidLiveUpdatePublisher.lastStatus()
                +"\n"+deviceProfile.description());
    }

    private void buildSpeakerChips() {
        speakerSeg.removeAllViews();
        String[] labels = {"自动", "2", "3", "4", "5", "6", "8"};
        int[] values = {0, 2, 3, 4, 5, 6, 8};
        for (int i = 0; i < labels.length; i++) {
            final int value = values[i];
            TextView chip = new TextView(this);
            chip.setText(labels[i]);
            chip.setTextSize(13);
            chip.setPadding((int) Motion.dp(this, 14), 0, (int) Motion.dp(this, 14), 0);
            chip.setMinHeight((int) Motion.dp(this, 48));
            chip.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.rightMargin = (int) Motion.dp(this, 8);
            speakerSeg.addView(chip, params);
            chip.setOnClickListener(v -> {
                speakerCount = value;
                prefs().edit().putInt("speakerCount", value).apply();
                paintSpeakerChips();
                Motion.selected(chip);
                Motion.haptic(chip, 8);
            });
        }
        paintSpeakerChips();
    }

    private void setupMotionRhythm() {
        TextView rhythm = need(R.id.motion_rhythm);
        Runnable refresh = () -> rhythm.setText("动效节奏 · " + ("quick".equals(Motion.rhythm()) ? "轻快" : "自然"));
        refresh.run();
        rhythm.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                .setTitle("动效节奏")
                .setSingleChoiceItems(new String[]{"自然：舒缓过渡与克制回弹", "轻快：更短的转场与反馈"},
                        "quick".equals(Motion.rhythm()) ? 1 : 0,
                        (dialog, choice) -> {
                            Motion.setRhythm(this, choice == 1 ? "quick" : "natural");
                            refresh.run(); dialog.dismiss(); Motion.selected(rhythm);
                        }).setNegativeButton("关闭", null).show());
    }

    private void paintSpeakerChips() {
        for (int i = 0; i < speakerSeg.getChildCount(); i++) {
            View child = speakerSeg.getChildAt(i);
            boolean selected = ((TextView) child).getText().toString()
                    .equals(speakerCount == 0 ? "自动" : String.valueOf(speakerCount));
            child.setBackgroundResource(selected ? R.drawable.bg_seg_on : R.drawable.bg_seg_off);
            child.setSelected(selected);
            UiControls.button(child);
            ((TextView) child).setTextColor(selected ? getColorCompat(R.color.on_accent) : getColorCompat(R.color.ink));
        }
    }

    private void buildAppearanceChips() {
        appearanceSeg.removeAllViews();
        String[] labels = {"跟随系统", "浅色", "深色"};
        String[] values = {UiTheme.SYSTEM, UiTheme.LIGHT, UiTheme.DARK};
        for (int i = 0; i < labels.length; i++) {
            final String value = values[i];
            TextView chip = new TextView(this);
            chip.setText(labels[i]);
            chip.setTextSize(13);
            chip.setPadding((int) Motion.dp(this, 14), 0, (int) Motion.dp(this, 14), 0);
            chip.setMinHeight((int) Motion.dp(this, 48));
            chip.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.rightMargin = (int) Motion.dp(this, 8);
            appearanceSeg.addView(chip, params);
            chip.setOnClickListener(v -> {
                if (!value.equals(UiTheme.mode(this))) {
                    UiTheme.set(this, value);
                    Motion.haptic(chip, 8);
                    recreate();
                }
            });
            boolean selected = value.equals(UiTheme.mode(this));
            chip.setBackgroundResource(selected ? R.drawable.bg_seg_on : R.drawable.bg_seg_off);
            chip.setSelected(selected);
            UiControls.button(chip);
            chip.setTextColor(selected ? getColorCompat(R.color.on_accent) : getColorCompat(R.color.ink));
        }
    }

    private void buildProviderChips() {
        providerSeg.removeAllViews();
        for (Cloud.Provider preset : Cloud.PRESETS) {
            final Cloud.Provider target = preset;
            TextView chip = new TextView(this);
            chip.setText(preset.name);
            chip.setTextSize(12);
            chip.setPadding((int) Motion.dp(this, 14), 0, (int) Motion.dp(this, 14), 0);
            chip.setMinHeight((int) Motion.dp(this, 48));
            chip.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.rightMargin = (int) Motion.dp(this, 8);
            providerSeg.addView(chip, params);
            chip.setOnClickListener(v -> {
                provider = target.id;
                prefs().edit().putString("cloudProvider", target.id).apply();
                // Presets fill the fields once; the user can still edit everything.
                if (!target.baseUrl.isEmpty()) {
                    cloudUrl.setText(target.baseUrl);
                }
                if (!target.chatModel.isEmpty()) {
                    cloudModel.setText(target.chatModel);
                }
                if (!target.asrModel.isEmpty()) {
                    cloudAsrModel.setText(target.asrModel);
                }
                persistCloud(true);
                cloudModel.setHint(Cloud.modelHint(provider));
                cloudAsrModel.setHint(Cloud.asrHint(provider));
                paintProviderChips();
                Motion.selected(chip);
                cloudStatus.setText(configSummary());
            });
        }
        paintProviderChips();
    }

    private void paintProviderChips() {
        for (int i = 0; i < providerSeg.getChildCount(); i++) {
            View child = providerSeg.getChildAt(i);
            boolean selected = ((TextView) child).getText().toString().equals(Cloud.preset(provider).name);
            child.setBackgroundResource(selected ? R.drawable.bg_seg_on : R.drawable.bg_seg_off);
            child.setSelected(selected);
            UiControls.button(child);
            ((TextView) child).setTextColor(selected ? getColorCompat(R.color.on_accent) : getColorCompat(R.color.ink));
        }
    }

    // ---------------------------------------------------------------- status

    private boolean persistCloud(boolean saveKey) {
        if (!cloudFieldsReady) return true;
        boolean saved = true;
        if (saveKey && cloudKeyDirty) {
            saved = Cloud.save(this, provider, cloudUrl.getText().toString().trim(),
                    cloudKey.getText().toString().trim(), cloudModel.getText().toString().trim(),
                    cloudAsrModel.getText().toString().trim());
            if (saved) cloudKeyDirty = false;
        } else {
            Cloud.saveMetadata(this, provider, cloudUrl.getText().toString().trim(),
                    cloudModel.getText().toString().trim(), cloudAsrModel.getText().toString().trim());
        }
        cloudStatus.setText(saved ? configSummary() : Cloud.secretStatus(this));
        return saved;
    }

    private String configSummary() {
        boolean configured = Cloud.configured(this);
        String problem = Cloud.secretStatus(this);
        if (!problem.isEmpty()) return problem;
        if (!configured) return "未配置 API Key · 自动文本校正已跳过，本应用当前不会联网。";
        return "已配置 " + Cloud.preset(Cloud.provider(this)).name + "：" + Cloud.chatModel(this)
                + "。转写过程中会自动把文本分段发送到此接口轻度校正；忠实稿仍会保留。"
                + (prefs().getBoolean("cloudAsr", false) && Cloud.hasAsr(this)
                ? "云端识别已开启，也会发送音频。" : "云端识别未开启，不会发送音频。")
                + "清空配置可停止后续发送。";
    }

    private void testCloud() {
        if (testing) {
            return;
        }
        if (!persistCloud(true)) return;
        if (!Cloud.configured(this)) {
            cloudStatus.setText("先填 API Key 再保存并测试；无 Key 时自动文本校正已跳过。");
            return;
        }
        testing = true;
        cloudStatus.setText("正在测试连接…");
        new Thread(() -> {
            try {
                String result = Cloud.test(this);
                ui.post(() -> cloudStatus.setText(result));
            } catch (Throwable error) {
                String hint = error instanceof Cloud.ApiException ? ((Cloud.ApiException) error).hint
                        : String.valueOf(error.getMessage());
                ui.post(() -> cloudStatus.setText("连接失败 · " + hint));
            } finally {
                testing = false;
            }
        }, "cloud-test").start();
    }

    private void refreshModelStatus() {
        try {
            boolean ready = ModelPrep.ready(this);
            long total = ModelPrep.totalBytes(this);
            if (ready) {
                modelStatus.setText(String.format(Locale.US, "已就绪 · %.0f MB", total / 1048576.0));
            } else {
                modelStatus.setText(String.format(Locale.US, "未就绪 · 还需 %.0f MB",
                        (total - ModelPrep.doneBytes(this)) / 1048576.0));
            }
        } catch (Exception e) {
            modelStatus.setText("模型状态未知：" + e.getMessage());
        }
    }

    private void prepareModels() {
        modelStatus.setText("正在准备…");
        new Thread(() -> {
            try {
                ModelPrep.prepare(this, (done, total, file) -> ui.post(() -> {
                    int percent = total > 0 ? (int) (done * 100 / total) : 0;
                    modelStatus.setText("准备中 " + percent + "% · " + file);
                }));
                ui.post(this::refreshModelStatus);
            } catch (Throwable error) {
                Report.problem("准备模型失败", error);
                ui.post(() -> modelStatus.setText("准备失败：" + error.getMessage()));
            }
        }, "model-prep").start();
    }

    /** Explicit, user-triggered 12s probe: separates OEM eligibility from ASR/model progress. */
    private void testIsland() {
        if (islandTesting) return;
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(
                android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            islandDetail.setText("先允许通知权限，再点测试。未授权时系统不会显示岛或普通通知。");
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 71);
            return;
        }
        islandTesting = true;
        islandDetail.setText("正在检查焦点协议、XMSF 门禁和系统通知频道…");
        new Thread(() -> {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            final int testId = 9714;
            try {
                if (nm == null) throw new IllegalStateException("通知管理器不可用");
                XiaomiIslandCapability.invalidateCache();
                XiaomiIslandCapability.Info capability = XiaomiIslandCapability.info(this);
                NotificationChannel oldChannel = nm.getNotificationChannel(LocalService.CHANNEL_ISLAND);
                String evidence = capability.summary() + "\n协议=" + capability.protocol
                        + "，Shizuku=" + ShizukuIslandBridge.permissionState(this)
                        + "，恢复记录=" + ShizukuIslandBridge.recoveryPending(this)
                        + "，XMSF 读取=" + capability.gateSupported
                        + "，焦点权限=" + capability.focusPermission
                        + "，通知总开关=" + nm.areNotificationsEnabled()
                        + "，频道重要性=" + (oldChannel == null ? "未创建" : oldChannel.getImportance());
                if (capability.state != XiaomiIslandCapability.State.READY || !nm.areNotificationsEnabled()
                        || oldChannel != null && oldChannel.getImportance() == NotificationManager.IMPORTANCE_NONE) {
                    Diagnostics.write(this, "tingxiejian-island-status.txt", "text/plain", evidence);
                    ui.post(() -> step("岛探测受限", () -> {
                        if (!isFinishing() && !isDestroyed())
                            islandDetail.setText(evidence + "\n请检查 Shizuku 授权、系统焦点权限与通知；资格不足不能靠 JSON 修复。");
                    }));
                    return;
                }
                ui.post(() -> step("岛探测通过", () -> {
                    if (!isFinishing() && !isDestroyed())
                        islandDetail.setText(evidence + "\n正在提交 12 秒焦点通知，请观察顶部状态栏。");
                }));
                NotificationChannel channel = new NotificationChannel(LocalService.CHANNEL_ISLAND,
                        "超级岛进度（静音）", NotificationManager.IMPORTANCE_DEFAULT);
                channel.setSound(null, null);
                channel.enableVibration(false);
                channel.setShowBadge(false);
                nm.createNotificationChannel(channel);
                PendingIntent open = PendingIntent.getActivity(this, 71,
                        new Intent(this, SettingsActivity.class),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                Notification notification = new Notification.Builder(this, LocalService.CHANNEL_ISLAND)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle("听写间 · 超级岛测试")
                        .setContentText("本地测试 · 进度 35%")
                        .setContentIntent(open)
                        .setCategory(Notification.CATEGORY_STATUS)
                        .setVisibility(Notification.VISIBILITY_PUBLIC)
                        .setColor(getColor(R.color.accent))
                        .setShowWhen(false).setOnlyAlertOnce(true)
                        .setOngoing(true).setTimeoutAfter(12_000L).build();
                String intentUri = new Intent(this, MainActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK).toUri(Intent.URI_INTENT_SCHEME);
                XiaomiIslandPublisher.publish(this, nm, testId, notification, true,
                        "听写间测试", "本地测试进度", "12 秒后自动结束", "35%", 35,
                        "打开", intentUri);
                ui.post(() -> step("岛测试状态", () -> {
                    if (!isFinishing() && !isDestroyed())
                        islandDetail.setText(evidence + "\n" + XiaomiIslandPublisher.lastOutcome()
                                + "\n只有普通通知、顶部无岛时，请核对系统焦点开关及官方测试白名单。");
                }));
                Thread.sleep(12_000L);
            } catch (Throwable error) {
                Report.problem("超级岛测试", error);
                ui.post(() -> step("岛测试失败", () -> {
                    if (!isFinishing() && !isDestroyed())
                        islandDetail.setText("岛测试失败：" + error.getClass().getSimpleName());
                }));
            } finally {
                if (nm != null) XiaomiIslandPublisher.cancel(this, nm, testId);
                islandTesting = false;
            }
        }, "island-probe").start();
    }

    private void refreshIslandStatus() {
        RealtimeDeviceProfile device = RealtimeDeviceProfile.of(android.os.Build.BRAND, android.os.Build.MANUFACTURER, android.os.Build.VERSION.SDK_INT);
        if (!device.xiaomi) return;
        new Thread(() -> {
            XiaomiIslandCapability.invalidateCache();
            IslandNotification.Capability capability = IslandNotification.capability(this);
            boolean eligible = capability.island && capability.focusPermission && capability.gateSupported;
            ui.post(() -> step("显示岛探测", () -> {
                if (isFinishing() || isDestroyed()) return;
                shizukuStatus.setText(ShizukuIslandBridge.permissionState(this)
                        + "。短时更改网络规则可能影响同期推送或连接；授权不代表系统会显示岛。");
                islandDetail.setText(capability.summary() + "\n"
                        + XiaomiIslandPublisher.lastOutcome());
                islandDot.getBackground().mutate().setTint(getColorCompat(
                        eligible ? R.color.dot_ready : capability.island
                                ? R.color.dot_unsupported : R.color.dot_waiting));
            }));
        }, "island-status").start();
    }

    @Override
    public void onBackPressed() {
        PortalTransition.close(this);
    }

    private int getColorCompat(int color) {
        return getResources().getColor(color, getTheme());
    }

    private void setupRecognitionOptions() {
        Switch haptic = need(R.id.haptic_feedback);
        haptic.setChecked(Motion.haptics());
        haptic.setOnCheckedChangeListener((view, checked) -> Motion.setHaptics(this, checked));
        userHotwords = need(R.id.user_hotwords);
        userHotwords.setText(prefs().getString("hotwords", ""));
        userHotwords.setOnFocusChangeListener((view, focused) -> {
            if (!focused) prefs().edit().putString("hotwords", userHotwords.getText().toString()).apply();
        });
        localModelChoice = need(R.id.local_model_choice);
        qwenStatus = need(R.id.qwen_status);
        prepareQwen = need(R.id.prepare_qwen);
        localModelChoice.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                .setTitle("选择本地复核模型")
                .setSingleChoiceItems(new String[]{"Qwen3-ASR 0.6B int8 · 高精度，内存需求较高",
                        "Paraformer · 标准复核，较轻量"},
                        "qwen3".equals(prefs().getString("local_finalizer", "qwen3")) ? 0 : 1,
                        (dialog, which) -> {
                            prefs().edit().putString("local_finalizer", which == 0 ? "qwen3" : "paraformer").apply();
                            refreshQwenStatus(); dialog.dismiss();
                        }).setNegativeButton("取消", null).show());
        prepareQwen.setOnClickListener(v -> installQwen(null));
        need(R.id.import_qwen).setOnClickListener(v -> startActivityForResult(
                new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), REQ_QWEN_IMPORT));
        refreshQwenStatus();
    }

    private void refreshQwenStatus() {
        boolean selected = "qwen3".equals(prefs().getString("local_finalizer", "qwen3"));
        localModelChoice.setText("当前模型 · " + (selected ? "Qwen3-ASR 0.6B" : "Paraformer") + "\n点击切换复核模型");
        boolean ready = ModelManager.readyQwen(this), bundled = ModelManager.bundledQwen(this);
        qwenStatus.setText(ready ? "Qwen3 模型文件已准备，转写时按需加载；无需导入。"
                : bundled ? "Qwen3 已完整内置，无需下载或导入。可点下方准备，也会在首次复核时自动准备。"
                : "此安装包未内置完整 Qwen3。可导入匹配模型目录；未准备时使用标准复核。");
        prepareQwen.setText(ready ? "内置 / 已导入模型已准备" : "准备内置 Qwen3 模型（无需下载）");
        prepareQwen.setEnabled(!qwenBusy && bundled && !ready);
        need(R.id.import_qwen).setEnabled(!qwenBusy);
    }

    /** Shared progress and cleanup for bundled setup and optional SAF import. No UI-thread hashing. */
    private void installQwen(android.net.Uri tree) {
        if (qwenBusy) return;
        qwenBusy = true;
        prepareQwen.setEnabled(false);
        need(R.id.import_qwen).setEnabled(false);
        qwenStatus.setText("正在校验并准备 Qwen3 模型…");
        Context app = getApplicationContext();
        new Thread(() -> {
            final long[] lastUpdate = {0};
            ModelPrep.Progress progress = (done, total, file) -> {
                long now = android.os.SystemClock.uptimeMillis();
                if (done < total && now - lastUpdate[0] < 250L) return;
                lastUpdate[0] = now;
                int percent = total > 0 ? (int) (done * 100 / total) : 0;
                ui.post(() -> {
                    if (!isFinishing() && !isDestroyed()) qwenStatus.setText("准备 Qwen3 · " + percent + "%（完成后校验）");
                });
            };
            String failure = null;
            try {
                if (tree == null) ModelManager.prepare(app, ModelManager.Pack.QWEN3, progress);
                else ModelManager.importQwen(app, name -> SafModelImporter.open(app, tree, name), progress);
            } catch (Exception error) { failure = error.getMessage(); }
            final String problem = failure;
            ui.post(() -> {
                qwenBusy = false;
                if (isFinishing() || isDestroyed()) return;
                refreshQwenStatus();
                if (problem != null) qwenStatus.setText("准备失败，原模型保留：" + problem);
            });
        }, "qwen-setup").start();
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == REQ_QWEN_IMPORT && result == RESULT_OK && data != null && data.getData() != null)
            installQwen(data.getData());
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
