package com.example.tingxiejian;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.SeekBar;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Main screen. One state is visible at a time - idle, first-run model preparation, running,
 * done - so whatever the user is waiting for is always in the middle of the screen.
 *
 * Everything that runs off the UI thread is wrapped in {@link #guard}: a failure is reported to
 * the diagnostics files and shown in the status line instead of taking the process down.
 */
public class MainActivity extends Activity {
    static final String TAG = "Tingxiejian";

    static final int STATE_IDLE = 0;
    static final int STATE_PREP = 1;
    static final int STATE_RUN = 2;
    static final int STATE_DONE = 3;

    private static final int REQ_PICK = 81;
    private static final int REQ_SAVE = 82;
    private static final String PREFS = "ui";
    private static final String STATE_EXPORT_KIND = "export_kind";
    private static final String STATE_EXPORT_ID = "export_id";
    private static final String STATE_INPUT_PATH = "input_path";
    private static final String STATE_INPUT_NAME = "input_name";
    private static final String STATE_ENGINE = "engine";
    private static final String STATE_DURATION = "duration";

    private final Handler ui = new Handler(Looper.getMainLooper());

    // shell
    private View root, column, main, drop;
    private LinearLayout historyList;
    private TextView run, actionNote, dropTitle, dropHint, fileMeta, fileError, historyEmpty;
    // panels
    private View stateIdle, statePrep, stateRun, stateDone;
    private ProgressBar prepBar, phaseSpinner;
    private TextView prepPercent, prepDetail;
    private RingView ring;
    private WaveView wave;
    private TextView stage, detail, eta, waveDone, elapsed, live;
    private TextView resultSummary, resultWarning, resultPreview, playerTime, ringLabel;
    private View copy, share, save, openFull;
    private ImageView play;
    private SeekBar seek;

    // service conversation
    private final Bus.Listener busListener = event -> ui.post(guard("处理服务事件", () -> handle(event)));

    // run state
    private File input;
    private int importGeneration;
    private boolean importing;
    private String inputName = "";
    private double durationSeconds;
    private long startedAt;
    private boolean running;
    private boolean phaseIndeterminate;
    private long phaseStartedAt;
    private int state = STATE_IDLE;
    private String engineUsed = "local";
    private Runnable ticker;

    // player
    private MediaPlayer player;
    private boolean playerReady;
    private boolean scrubbing;
    private boolean historyEntered;

    // prefs
    private boolean diarizeWanted = true;
    private boolean islandWanted = true;
    private int speakerCount = 0;

    // result
    private JSONObject resultJson;
    private String resultId = "";
    // Persist the chosen record/format rather than an in-memory export payload across the picker.
    private String exportResultId = "";
    private int exportKind = -1;

    // Keep source Views alive across onStart so the shared element has somewhere to shrink to.
    private final java.util.Map<String, TextView> historyRowCache = new java.util.HashMap<>();

    // diagnostics
    private int statusInset;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(UiTheme.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            if ((getIntent() == null || !getIntent().getBooleanExtra("skip_welcome_once", false))
                    && WelcomeActivity.shouldShow(this)) {
                startActivity(new Intent(this, WelcomeActivity.class));
                finish();
                return;
            }
            setContentView(R.layout.activity_main);
            Report.mark("setContentView(activity_main)");
            bindViews();
            Report.mark("views bound");
            setUpWindow();
            Report.mark("window insets applied");
            loadPreferences();
            wireActions();
            showState(STATE_IDLE, false);
            if (savedInstanceState != null) {
                exportKind = savedInstanceState.getInt(STATE_EXPORT_KIND, -1);
                exportResultId = savedInstanceState.getString(STATE_EXPORT_ID, "");
                String path = savedInstanceState.getString(STATE_INPUT_PATH, "");
                if (!path.isEmpty() && new File(path).isFile()) input = new File(path);
                inputName = savedInstanceState.getString(STATE_INPUT_NAME, "");
                engineUsed = savedInstanceState.getString(STATE_ENGINE, "local");
                durationSeconds = savedInstanceState.getDouble(STATE_DURATION, 0);
            }
            if (LocalService.isBusy()) {
                String activePath = LocalService.activePath();
                if (!activePath.isEmpty()) input = new File(activePath);
                inputName = LocalService.activeName();
                engineUsed = LocalService.activeEngine();
                running = true; // Bus.register in onStart will now accept the cached progress.
                showState(STATE_RUN, false);
                stage.setText("正在转写…");
            } else if (input != null) {
                dropHint.setText(inputName);
                fileMeta.setText("已选好录音 · " + Job.clock(durationSeconds));
                syncBottomBar();
            }
            handleIntent(getIntent());
            Report.mark("activity.onCreate complete");
            Report.flush(this);
            root.postDelayed(this::afterFirstFrame, 450);
        } catch (Throwable error) {
            Report.problem("界面初始化失败", error);
            showFallback(error);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putInt(STATE_EXPORT_KIND, exportKind);
        state.putString(STATE_EXPORT_ID, exportResultId);
        state.putString(STATE_INPUT_PATH, input == null ? "" : input.getAbsolutePath());
        state.putString(STATE_INPUT_NAME, inputName);
        state.putString(STATE_ENGINE, engineUsed);
        state.putDouble(STATE_DURATION, durationSeconds);
        super.onSaveInstanceState(state);
    }

    // ---------------------------------------------------------------- plumbing

    /** Island action target: the notification's stop button opens us with this action. */
    public static final String ACTION_STOP_JOB = "com.example.tingxiejian.action.STOP_JOB";

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        try {
            if (intent != null && ACTION_STOP_JOB.equals(intent.getAction())) {
                sendService(LocalService.ACTION_CANCEL, null);
            }
        } catch (Throwable error) {
            Report.problem("岛按钮处理失败", error);
        }
    }

    private void bindViews() {
        root = need(R.id.root);
        column = need(R.id.column);
        main = need(R.id.main);
        drop = need(R.id.drop);
        run = need(R.id.run);
        actionNote = need(R.id.action_note);
        dropTitle = need(R.id.drop_title);
        dropHint = need(R.id.drop_hint);
        fileMeta = need(R.id.file_meta);
        fileError = need(R.id.file_error);
        historyList = need(R.id.history_list);
        historyEmpty = need(R.id.history_empty);
        stateIdle = need(R.id.state_idle);
        statePrep = need(R.id.state_prep);
        stateRun = need(R.id.state_run);
        stateDone = need(R.id.state_done);
        prepBar = need(R.id.prep_bar);
        prepPercent = need(R.id.prep_percent);
        prepDetail = need(R.id.prep_detail);
        ring = need(R.id.ring);
        wave = need(R.id.wave);
        stage = need(R.id.stage);
        detail = need(R.id.detail);
        eta = need(R.id.eta);
        waveDone = need(R.id.wave_done);
        elapsed = need(R.id.elapsed);
        live = need(R.id.live);
        ringLabel = need(R.id.ring_label);
        phaseSpinner = need(R.id.phase_spinner);
        resultSummary = need(R.id.result_summary);
        resultWarning = need(R.id.result_warning);
        resultPreview = need(R.id.result_preview);
        playerTime = need(R.id.player_time);
        copy = need(R.id.copy);
        share = need(R.id.share);
        save = need(R.id.save);
        openFull = need(R.id.open_full);
        play = need(R.id.play);
        seek = need(R.id.seek);
    }

    private <T extends View> T need(int id) {
        T view = findViewById(id);
        if (view == null) {
            throw new IllegalStateException("视图 " + getResources().getResourceEntryName(id)
                    + " 找不到：布局里没有它");
        }
        return view;
    }

    private void click(View view, String what, Runnable action) {
        view.setOnClickListener(v -> guard(what, action).run());
    }

    private Runnable guard(String what, Runnable work) {
        return () -> {
            try {
                work.run();
            } catch (Throwable error) {
                android.util.Log.w(TAG, what + " failed", error);
                Report.problem(what, error);
                fileError.setText(what + "出错：" + error.getMessage());
            }
        };
    }

    private void setUpWindow() {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            UiTheme.applyBarAppearance(this);
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                int top = insets.getInsets(WindowInsets.Type.statusBars()).top;
                int bottom = Math.max(insets.getInsets(WindowInsets.Type.navigationBars()).bottom,
                        insets.getInsets(WindowInsets.Type.ime()).bottom);
                statusInset = top;
                column.setPadding(column.getPaddingLeft(), top,
                        column.getPaddingRight(), bottom);
                return insets;
            });
            root.requestApplyInsets();
        }
    }

    private void dumpLayout() {
        try {
            View appBar = need(R.id.appbar);
            View bottom = need(R.id.bottombar);
            JSONObject json = new JSONObject();
            json.put("version", 1);
            json.put("sdk", Build.VERSION.SDK_INT);
            json.put("device", Build.MANUFACTURER + " " + Build.MODEL);
            json.put("screenWidth", root.getWidth());
            json.put("screenHeight", root.getHeight());
            json.put("statusBarInset", statusInset);
            json.put("columnTop", column.getTop());
            json.put("appBarTop", appBar.getTop());
            json.put("appBarBottom", appBar.getBottom());
            json.put("appBarHeight", appBar.getHeight());
            json.put("titleTop", dropTitle.getTop());
            json.put("listTop", main.getTop());
            json.put("listBottom", main.getBottom());
            json.put("bottombarTop", bottom.getTop());
            json.put("overlapPx", Math.max(0, statusInset - appBar.getTop()));
            json.put("statusBarCoversTitle", appBar.getTop() < statusInset);
            json.put("appBarClearsList", appBar.getBottom() <= main.getTop());
            json.put("listClearsBottombar", main.getBottom() <= bottom.getTop());
            json.put("fontScale", getResources().getConfiguration().fontScale);
            Diagnostics.write(this, "tingxiejian-layout.json", "application/json", json.toString(2));
        } catch (Throwable error) {
            Report.problem("布局测量失败", error);
        }
    }

    private void showFallback(Throwable error) {
        try {
            ScrollView scroll = new ScrollView(this);
            TextView text = new TextView(this);
            text.setText(Report.crashText("界面初始化失败", error));
            text.setTextIsSelectable(true);
            text.setTextSize(11);
            text.setTextColor(Color.BLACK);
            text.setBackgroundColor(Color.WHITE);
            text.setPadding(32, 32, 32, 32);
            scroll.addView(text);
            setContentView(scroll);
        } catch (Throwable ignored) {
        }
    }

    // ---------------------------------------------------------------- states

    private void showState(int next, boolean animate) {
        View[] panels = {stateIdle, statePrep, stateRun, stateDone};
        for (int i = 0; i < panels.length; i++) {
            final int index = i;
            final View panel = panels[i];
            if (panel == null || i == next) {
                continue;
            }
            if (animate && panel.getVisibility() == View.VISIBLE && Motion.animatorsEnabled()) {
                // exit is faster than the entrance that follows it
                Motion.exit(panel, () -> {
                    if (state != index) {
                        panel.setVisibility(View.GONE);
                    }
                });
            } else {
                panel.animate().cancel();
                panel.setVisibility(View.GONE);
            }
        }
        View target = panels[next];
        if (target != null) {
            target.setVisibility(View.VISIBLE);
            if (animate && Motion.animatorsEnabled()) {
                // One beat per state: either the icon or the panel, never both at once.
                if (next == STATE_PREP) Motion.turnOnce(need(R.id.prep_icon));
                else if (next == STATE_DONE) Motion.pop(need(R.id.done_icon));
                else Motion.enter(target);
            }
        }
        state = next;
        syncBottomBar();
    }

    private void syncBottomBar() {
        switch (state) {
            case STATE_PREP:
                run.setText("准备模型");
                actionNote.setText("首次使用需解压约 500 MB 模型，只在这台手机上进行。");
                break;
            case STATE_RUN:
                run.setText("取消转写");
                actionNote.setText("转写过程可以随时取消。");
                break;
            case STATE_DONE:
                run.setText("再转一段");
                actionNote.setText("结果已保存在“最近”。");
                break;
            default:
                run.setText(input == null ? "选择录音" : "开始转写");
                actionNote.setText(input == null
                        ? "本地转写 · 分人与标点在本机完成"
                        : "已选好录音，点“开始转写”。");
        }
    }

    private void afterFirstFrame() {
        guard("检查模型准备", this::checkModel).run();
        root.postDelayed(guard("首帧几何自测", this::dumpLayout), 200);
    }

    private void checkModel() {
        if (LocalService.isBusy() || ModelPrep.ready(this)) {
            return;
        }
        showState(STATE_PREP, true);
        startModelPrep();
    }

    private void startModelPrep() {
        prepDetail.setText("正在解压模型…");
        new Thread(() -> {
            try {
                ModelPrep.prepare(this, (done, total, file) -> ui.post(() -> {
                    int percent = total > 0 ? (int) (done * 100 / total) : 0;
                    prepBar.setProgress(percent * 10);
                    prepPercent.setText(percent + "%");
                    prepDetail.setText(String.format(Locale.US, "%.0f / %.0f MB",
                            done / 1048576.0, total / 1048576.0));
                }));
                ui.post(() -> {
                    showState(STATE_IDLE, true);
                    fileMeta.setText("模型已就绪，可以开始转写。");
                });
            } catch (Throwable error) {
                Report.problem("准备模型失败", error);
                ui.post(() -> prepDetail.setText("准备失败：" + error.getMessage() + "，点下方按钮重试。"));
            }
        }, "model-prep").start();
    }

    // ---------------------------------------------------------------- wiring

    private void wireActions() {
        View settingsButton = need(R.id.settings);
        Motion.press(drop);
        Motion.press(run);
        Motion.press(settingsButton);
        click(drop, "选择录音", this::pickAudio);
        click(run, "主操作", this::primaryAction);
        click(settingsButton, "打开设置", () -> PortalTransition.open(this, settingsButton,
                new Intent(this, SettingsActivity.class), "settings_portal"));
        click(copy, "复制全文", () -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard != null && resultJson != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("转写", Exporter.plain(resultJson)));
                fileMeta.setText("已复制全文。");
            }
        });
        click(share, "分享转写", this::shareResult);
        click(save, "导出", this::askExportFormat);
        click(need(R.id.hero), "选择录音", () -> {
            if (state == STATE_IDLE) pickAudio();
        });
        click(openFull, "查看全文", () -> {
            if (!resultId.isEmpty()) {
                TranscriptActivity.open(this, resultId, need(R.id.open_full_icon));
            }
        });
        click(play, "播放/暂停", this::togglePlayback);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (fromUser && player != null && playerReady) {
                    player.seekTo(value);
                    playerTime.setText(Job.clock(value / 1000.0));
                }
            }

            public void onStartTrackingTouch(SeekBar bar) {
                scrubbing = true;
            }

            public void onStopTrackingTouch(SeekBar bar) {
                scrubbing = false;
            }
        });
    }

    private void primaryAction() {
        switch (state) {
            case STATE_PREP:
                startModelPrep();
                break;
            case STATE_RUN:
                Motion.haptic(run, 12);
                sendService(LocalService.ACTION_CANCEL, null);
                break;
            case STATE_DONE:
                showState(STATE_IDLE, true);
                refreshHistory();
                break;
            default:
                if (input == null) {
                    pickAudio();
                } else {
                    startRun();
                }
        }
    }

    private void loadPreferences() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        diarizeWanted = prefs.getBoolean("diarize", true);
        islandWanted = prefs.getBoolean("island", true);
        speakerCount = prefs.getInt("speakerCount", 0);
    }

    // ---------------------------------------------------------------- import

    private void pickAudio() {
        if (running || LocalService.isBusy()) {
            fileError.setText("已有转写任务，请等待完成或先取消。");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"audio/*", "video/mp4", "video/*"});
        startActivityForResult(intent, REQ_PICK);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        try {
            super.onActivityResult(requestCode, resultCode, data);
            if (requestCode == REQ_PICK && resultCode == RESULT_OK && data != null && data.getData() != null) {
                importAudio(data.getData());
            } else if (requestCode == REQ_SAVE && resultCode == RESULT_OK && data != null && data.getData() != null) {
                writeExport(data.getData());
            }
        } catch (Throwable error) {
            Report.problem("选择文件失败", error);
            fileError.setText("选择文件失败：" + error.getMessage());
        }
    }

    private void importAudio(Uri uri) {
        if (running || LocalService.isBusy()) {
            fileError.setText("已有转写任务，不能替换正在读取的录音。");
            return;
        }
        final int generation = ++importGeneration;
        importing = true;
        fileError.setText("");
        fileMeta.setText("正在读取录音…");
        new Thread(() -> {
            File cache = null;
            try {
                String name = queryName(uri);
                // Every import gets a distinct immutable path: a new selection cannot truncate
                // the file that the service is currently reading, even after UI recreation.
                cache = File.createTempFile("input-", ".audio", getCacheDir());
                long copied = 0;
                try (InputStream in = getContentResolver().openInputStream(uri);
                     OutputStream out = new FileOutputStream(cache)) {
                    if (in == null) {
                        throw new java.io.IOException("无法读取这个文件");
                    }
                    byte[] buffer = new byte[256 * 1024];
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        out.write(buffer, 0, n);
                        copied += n;
                        if (copied > 200L * 1024 * 1024) {
                            throw new java.io.IOException("文件超过 200 MB，请先裁剪或压缩");
                        }
                    }
                }
                double seconds = probeDuration(cache);
                final long copiedBytes = copied;
                final File imported = cache;
                ui.post(() -> {
                    if (generation != importGeneration || isDestroyed() || LocalService.isBusy()) {
                        imported.delete();
                        if (generation == importGeneration) importing = false;
                        return;
                    }
                    importing = false;
                    File previous = input;
                    releasePlayer();
                    input = imported;
                    if (previous != null && !previous.equals(imported)) previous.delete();
                    inputName = name;
                    durationSeconds = seconds;
                    dropHint.setText(name);
                    fileMeta.setText(String.format(Locale.US, "%.1f MB · %s · 已就绪",
                            copiedBytes / 1048576.0, Job.clock(seconds)));
                    Motion.haptic(drop, 12);
                    syncBottomBar();
                });
            } catch (Throwable error) {
                if (cache != null) cache.delete();
                Report.problem("读取录音失败", error);
                ui.post(() -> {
                    if (generation != importGeneration || isDestroyed()) return;
                    importing = false;
                    fileError.setText("读取录音失败：" + error.getMessage());
                    fileMeta.setText("");
                });
            }
        }, "import-audio").start();
    }

    private String queryName(Uri uri) {
        String name = "录音";
        try (android.database.Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                name = cursor.getString(0);
            }
        } catch (Exception ignored) {
        }
        return name;
    }

    private double probeDuration(File file) {
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(file.getAbsolutePath());
            String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            retriever.release();
            return duration == null ? 0 : Long.parseLong(duration) / 1000.0;
        } catch (Exception e) {
            return 0;
        }
    }

    // ---------------------------------------------------------------- running

    private void startRun() {
        if (importing) {
            fileError.setText("正在读取录音，请稍候。");
            return;
        }
        if (running || LocalService.isBusy()) {
            fileError.setText("已有转写任务，请等待完成或先取消。");
            return;
        }
        if (input == null || !input.isFile()) {
            fileError.setText("请先选择一段录音。");
            return;
        }
        if (!ModelPrep.ready(this)) {
            showState(STATE_PREP, true);
            startModelPrep();
            return;
        }
        fileError.setText("");
        running = true;
        startedAt = System.currentTimeMillis();
        engineUsed = (Cloud.hasAsr(this) && cloudAsrEnabled()) ? "cloud" : "local";
        showState(STATE_RUN, true);
        ring.setProgress(0f);
        phaseIndeterminate = false;
        phaseSpinner.setVisibility(View.GONE);
        ringLabel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 42);
        ringLabel.setText("0%");
        stage.setText(engineUsed.equals("cloud") ? "云端识别" : "正在准备");
        detail.setText("");
        eta.setText("");
        live.setText("");
        waveDone.setText("");
        elapsed.setText("00:00");
        stopTicker();
        ticker = () -> {
            if (running) {
                long seconds = (System.currentTimeMillis() - startedAt) / 1000;
                elapsed.setText(Job.clock(seconds));
                if (phaseIndeterminate) {
                    eta.setText("本阶段已用 " + Job.clock((System.currentTimeMillis() - phaseStartedAt) / 1000.0)
                            + " · 剩余时间无法估算");
                }
                ui.postDelayed(guard("计时器", ticker), 1000);
            }
        };
        ui.post(guard("计时器", ticker));

        JSONObject extras = new JSONObject();
        try {
            extras.put(LocalService.EXTRA_PATH, input.getAbsolutePath());
            extras.put("name", inputName);
            extras.put(LocalService.EXTRA_SPEAKERS, diarizeWanted);
            extras.put(LocalService.EXTRA_COUNT, speakerCount);
            extras.put(LocalService.EXTRA_ISLAND, islandWanted);
            extras.put("engine", engineUsed);
        } catch (Exception ignored) {
        }
        sendService(LocalService.ACTION_START, extras);
    }

    private boolean cloudAsrEnabled() {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("cloudAsr", false);
    }

    private void sendService(String action, JSONObject extras) {
        Intent intent = new Intent(this, LocalService.class);
        intent.setAction(action);
        if (extras != null) {
            JSONArray names = extras.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String key = names.optString(i);
                    Object value = extras.opt(key);
                    if (value instanceof String) {
                        intent.putExtra(key, (String) value);
                    } else if (value instanceof Boolean) {
                        intent.putExtra(key, (Boolean) value);
                    } else if (value instanceof Integer) {
                        intent.putExtra(key, (Integer) value);
                    } else if (value instanceof Double) {
                        intent.putExtra(key, ((Double) value).doubleValue());
                    }
                }
            }
        }
        startService(intent);
    }

    // ---------------------------------------------------------------- events

    private void handle(JSONObject event) {
        String type = event.optString("type");
        if ("result".equals(type)) {
            render(event);
            return;
        }
        if ("error".equals(type)) {
            finishRun();
            String message = event.optString("message", "转写失败");
            showState(STATE_IDLE, true);
            fileError.setText(message);
            return;
        }
        if (!running) {
            return;
        }
        if (event.has("indeterminate")) {
            boolean pending = event.optBoolean("indeterminate");
            if (pending && !phaseIndeterminate) phaseStartedAt = System.currentTimeMillis();
            phaseIndeterminate = pending;
            phaseSpinner.setVisibility(phaseIndeterminate && Motion.animatorsEnabled()
                    ? View.VISIBLE : View.GONE);
        }
        if (event.has("job")) {
            float percent = (float) event.optDouble("job");
            ring.setProgress(percent); // completed phases; the active phase is not fabricated
            ringLabel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP,
                    phaseIndeterminate ? 25 : 42);
            ringLabel.setText(phaseIndeterminate
                    ? (event.optString("stage").contains("分人") ? "分人中" : "处理中")
                    : Math.round(percent) + "%");
        }
        if (event.has("stage")) {
            stage.setText(event.optString("stage"));
        }
        if (event.has("detail")) {
            detail.setText(event.optString("detail"));
        }
        if (event.has("eta")) {
            eta.setText(event.optString("eta"));
        }
        double processed = event.optDouble("processed", 0);
        double total = event.optDouble("total", 0);
        if (total > 0) {
            waveDone.setText("音频 " + Job.clock(processed) + " / " + Job.clock(total));
        }
        JSONArray peaks = event.optJSONArray("wave");
        if (peaks != null) {
            float[] values = new float[peaks.length()];
            for (int i = 0; i < values.length; i++) {
                values[i] = (float) peaks.optDouble(i);
            }
            wave.setEnvelope(values, total > 0 ? (float) (processed / total) : 0f);
        }
        String partial = event.optString("partial", "");
        String recent = event.optString("recent", "");
        if (!partial.isEmpty()) {
            live.setText(partial);
        } else if (!recent.isEmpty()) {
            live.setText(recent);
        }
    }

    private void render(JSONObject result) {
        finishRun();
        try {
            String savedId = result.optString("saved_id", "");
            if (savedId.isEmpty()) throw new java.io.IOException("服务未提供已保存的转写结果");
            if (savedId.equals(resultId) && state == STATE_DONE) return; // cached Bus event
            JSONObject saved = History.load(this, savedId);
            resultId = savedId;
            resultJson = saved;
            inputName = saved.optString("name", inputName);
            engineUsed = saved.optString("engine", engineUsed);
            durationSeconds = saved.optDouble("durationSeconds", durationSeconds);
            if (input == null) {
                String path = LocalService.activePath();
                if (!path.isEmpty() && new File(path).isFile()) input = new File(path);
            }
            JSONArray segments = History.segments(saved);
            int speakers = saved.optInt("speakers", 0);
            String warning = saved.optString("warning", "");

            StringBuilder summary = new StringBuilder();
            summary.append(segments.length()).append(" 段");
            if (speakers > 0) {
                summary.append(" · ").append(speakers).append(" 位发言人");
            }
            summary.append(" · 时长 ").append(Job.clock(durationSeconds));
            resultSummary.setText(summary.toString());
            resultPreview.setText(Exporter.plain(saved));
            if (warning.isEmpty()) {
                resultWarning.setVisibility(View.GONE);
            } else {
                resultWarning.setText(warning);
                resultWarning.setVisibility(View.VISIBLE);
            }
            preparePlayer();
            showState(STATE_DONE, true);
            refreshHistory();
        } catch (Throwable error) {
            Report.problem("保存结果失败", error);
            fileError.setText("结果保存失败：" + error.getMessage());
            showState(STATE_DONE, true);
        }
    }

    private void finishRun() {
        running = false;
        phaseIndeterminate = false;
        if (phaseSpinner != null) phaseSpinner.setVisibility(View.GONE);
        stopTicker();
    }

    private void stopTicker() {
        if (ticker != null) {
            ui.removeCallbacks(ticker);
            ticker = null;
        }
    }

    // ---------------------------------------------------------------- player

    private void preparePlayer() {
        releasePlayer();
        if (input == null || !input.isFile()) {
            return;
        }
        try {
            player = new MediaPlayer();
            player.setDataSource(input.getAbsolutePath());
            player.prepare();
            playerReady = true;
            seek.setMax(Math.max(1, player.getDuration()));
            playerTime.setText("00:00 / " + Job.clock(player.getDuration() / 1000.0));
            player.setOnCompletionListener(mp -> {
                Motion.iconSwap(play, R.drawable.ic_play);
                if (player != null) {
                    player.seekTo(0);
                }
            });
            play.setImageResource(R.drawable.ic_play);
        } catch (Exception e) {
            Report.problem("播放器准备失败", e);
            playerReady = false;
        }
    }

    private void togglePlayback() {
        if (player == null || !playerReady) {
            return;
        }
        if (player.isPlaying()) {
            player.pause();
            Motion.iconSwap(play, R.drawable.ic_play);
        } else {
            player.start();
            Motion.iconSwap(play, R.drawable.ic_pause);
            trackPlayhead();
        }
    }

    private void trackPlayhead() {
        if (player == null || !playerReady) {
            return;
        }
        if (player.isPlaying() && !scrubbing) {
            int position = player.getCurrentPosition();
            seek.setProgress(position);
            playerTime.setText(Job.clock(position / 1000.0) + " / " + Job.clock(player.getDuration() / 1000.0));
        }
        ui.postDelayed(guard("播放进度", this::trackPlayhead), 400);
    }

    private void releasePlayer() {
        if (player != null) {
            try {
                player.release();
            } catch (Exception ignored) {
            }
        }
        player = null;
        playerReady = false;
    }

    // ---------------------------------------------------------------- export

    private void askExportFormat() {
        if (resultJson == null) {
            return;
        }
        String[] formats = {"TXT（带时间）", "SRT（字幕）", "JSON（结构化）"};
        new android.app.AlertDialog.Builder(this)
                .setTitle("导出格式")
                .setItems(formats, (dialog, which) -> beginExport(which + 1))
                .show();
    }

    private void beginExport(int kind) {
        if (resultJson == null) {
            return;
        }
        exportKind = kind;
        exportResultId = resultId;
        String name = safeName(kind == 1 ? ".txt" : kind == 2 ? ".json" : ".srt");
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(kind == 2 ? "application/json" : "text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, name);
        startActivityForResult(intent, REQ_SAVE);
    }

    private String safeName(String suffix) {
        String name = (resultJson == null ? "transcript" : resultJson.optString("name", "transcript"))
                .replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        if (name.length() > 100) name = name.substring(0, 100);
        if (name.trim().isEmpty()) name = "transcript";
        return name + suffix;
    }

    private void writeExport(Uri uri) {
        final int kind = exportKind;
        final String recordId = exportResultId;
        exportKind = -1;
        exportResultId = "";
        new Thread(() -> {
            try {
                if (kind < 1 || kind > 3 || recordId == null || recordId.isEmpty())
                    throw new IllegalStateException("导出状态已丢失，请重新选择导出格式");
                JSONObject record = History.load(this, recordId);
                String name = record.optString("name", "transcript");
                String body = kind == 1 ? Exporter.txt(record)
                        : kind == 2 ? Exporter.json(record) : Exporter.srt(record);
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new java.io.IOException("文档提供方未返回输出流");
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                }
                ui.post(() -> { if (!isDestroyed()) fileMeta.setText("已导出 " + name); });
            } catch (Throwable error) {
                Report.problem("导出失败", error);
                ui.post(() -> { if (!isDestroyed()) fileError.setText("导出失败：" + error.getMessage()); });
            }
        }, "export").start();
    }

    private void shareResult() {
        if (resultJson == null) {
            return;
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, resultJson.optString("name", "转写结果"));
        send.putExtra(Intent.EXTRA_TEXT, Exporter.txt(resultJson));
        startActivity(Intent.createChooser(send, "分享转写"));
    }

    // ---------------------------------------------------------------- history & service

    private void refreshHistory() {
        try {
            java.util.List<History.Entry> entries = History.list(this, 5);
            historyEmpty.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
            float density = getResources().getDisplayMetrics().density;
            java.util.Set<String> visibleIds = new java.util.HashSet<>();
            for (int index = 0; index < entries.size(); index++) {
                History.Entry entry = entries.get(index);
                final String id = entry.id;
                visibleIds.add(id);
                TextView row = historyRowCache.get(id);
                if (row == null) {
                    row = new TextView(this);
                    row.setTextSize(14);
                    row.setTextColor(getColor(R.color.ink));
                    row.setBackgroundResource(R.drawable.bg_row);
                    row.setMinHeight((int) (48 * density));
                    row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                    row.setPadding((int) (14 * density), (int) (14 * density),
                            (int) (14 * density), (int) (14 * density));
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    params.topMargin = (int) (8 * density);
                    row.setLayoutParams(params);
                    Motion.press(row);
                    final TextView source = row;
                    row.setOnClickListener(v -> guard("打开历史记录",
                            () -> TranscriptActivity.open(this, id, source)).run());
                    historyRowCache.put(id, row);
                }
                row.setText(entry.name + "  ·  " + Job.clock(entry.durationSeconds)
                        + "  ·  " + (entry.speakers > 0 ? entry.speakers + " 人" : "未分人"));
                int current = historyList.indexOfChild(row);
                if (current != index) {
                    if (current >= 0) historyList.removeView(row);
                    historyList.addView(row, Math.min(index, historyList.getChildCount()));
                }
            }
            java.util.Iterator<java.util.Map.Entry<String, TextView>> iterator =
                    historyRowCache.entrySet().iterator();
            while (iterator.hasNext()) {
                java.util.Map.Entry<String, TextView> cached = iterator.next();
                if (!visibleIds.contains(cached.getKey())) {
                    historyList.removeView(cached.getValue());
                    iterator.remove();
                }
            }
            if (!historyEntered && !entries.isEmpty()) {
                Motion.stagger(historyList, 36);
                historyEntered = true;
            }
        } catch (Throwable error) {
            Report.problem("刷新历史失败", error);
        }
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onStart() {
        super.onStart();
        if (historyList == null) return; // First launch handed over to the optional guide.
        Bus.register(busListener);
        guard("刷新历史", this::refreshHistory).run();
    }

    @Override
    protected void onStop() {
        Bus.unregister(busListener);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        stopTicker();
        releasePlayer();
        super.onDestroy();
    }
}
