package com.example.tingxiejian;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

/**
 * Foreground service that owns the transcription job.
 *
 * <p>Progress reaches the UI through {@link Bus} in-process, so the app no longer opens a loopback
 * HTTP port: nothing is listening, nothing to authenticate, nothing to reach from another app.
 * The same {@link Job} mapping feeds the screen, the notification and the Xiaomi island.
 */
public final class LocalService extends Service {
    static final String ACTION_START = "com.example.tingxiejian.action.START";
    static final String ACTION_STOP = "com.example.tingxiejian.action.STOP";
    static final String ACTION_CANCEL = "com.example.tingxiejian.action.CANCEL";
    static final String EXTRA_PATH = "path";
    static final String EXTRA_SPEAKERS = "speakers";
    static final String EXTRA_COUNT = "count";
    static final String EXTRA_ISLAND = "island";
    static final String EXTRA_NAME = "name";
    static final String EXTRA_ENGINE = "engine";

    private static final String TAG = "Tingxiejian";
    // Channels are immutable after creation. Island needs DEFAULT (upstream IslandController.kt);
    // the ordinary foreground notice stays LOW to avoid an intrusive popup on every update.
    private static final String CHANNEL_QUIET = "transcribe_quiet_v2";
    static final String CHANNEL_ISLAND = "transcribe_island_v1";
    private static final int NOTIFICATION_ID = 11;
    /** FGS placeholder: the first focus notification must use a *new* ID, not update this. */
    private static final int BOOTSTRAP_ID = 12;
    private static final long DONE_LINGER_MS = 4000;
    /** Fixed notification timestamp: keeps the notice from re-sorting to the top on every update. */
    private static final long NOTIFY_WHEN = System.currentTimeMillis();

    private static volatile boolean running;
    private static volatile boolean busy;

    /** Set once the platform refuses a foreground service, so the UI stops asking for one. */
    private static volatile boolean foregroundRefused;

    static boolean foregroundRefused() {
        return foregroundRefused;
    }

    /** True while the service is alive (drives the app-bar switch). */
    static boolean isRunning() {
        return running;
    }

    /** True while a transcription is in flight; the UI must not stop the service then. */
    static boolean isBusy() {
        return busy;
    }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private NotificationManager manager;
    private Thread worker;
    private volatile boolean cancelled;
    private boolean islandWanted = true;
    private boolean bootstrapActive;
    private long startedAt;
    /** First island publish of a run may float open; later updates must not jump the island. */
    private boolean islandFirstFrame = true;
    private int lastPercent = -2;
    private int floorPercent;
    private String lastStage = "";

    /** No idle foreground service: the result has already been delivered through Bus/History. */
    private final Runnable idleShutdown = () -> {
        try { if (!busy) shutDown(); }
        catch (Throwable error) { Report.problem("空闲时释放服务", error); }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createChannel();
        running = true;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            return handleCommand(intent);
        } catch (Throwable error) {
            // A refused foreground start, or a rejected notification, must never kill the process that
            // is also drawing the interface: that is what "it crashes on launch" looked like.
            Log.w(TAG, "service command failed", error);
            Report.problem("服务命令失败", error);
            return START_NOT_STICKY;
        }
    }

    private int handleCommand(Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_CANCEL.equals(action)) {
            cancelled = true;
            return START_NOT_STICKY;
        }
        if (ACTION_STOP.equals(action)) {
            cancelled = true;
            if (!busy) shutDown();
            return START_NOT_STICKY;
        }
        String path = intent == null ? null : intent.getStringExtra(EXTRA_PATH);
        // FGS must start promptly: never query Xiaomi ContentProviders on the service main thread.
        // Manufacturer is only a cheap *channel* hint; real capability is checked on the worker
        // before attaching any private island payload.
        boolean requestIsland = path != null && intent.getBooleanExtra(EXTRA_ISLAND, true)
                && !"cloud".equals(intent.getStringExtra(EXTRA_ENGINE))
                && android.os.Build.MANUFACTURER.toLowerCase(java.util.Locale.ROOT).contains("xiaomi");
        ensureForeground(requestIsland);
        if (path != null && !busy) {
            startRun(new File(path),
                    intent.getStringExtra(EXTRA_NAME),
                    intent.getBooleanExtra(EXTRA_SPEAKERS, true),
                    intent.getIntExtra(EXTRA_COUNT, 0),
                    intent.getBooleanExtra(EXTRA_ISLAND, true),
                    intent.getStringExtra(EXTRA_ENGINE));
        }
        return START_NOT_STICKY;
    }

    private void ensureForeground(boolean useIslandChannel) {
        lastPercent = -2;
        floorPercent = 0;
        try {
            // Same channel from the first foreground post through the last progress update.
            bootstrapActive = useIslandChannel;
            startForeground(useIslandChannel ? BOOTSTRAP_ID : NOTIFICATION_ID,
                    buildNotification(Job.pending("正在读取录音"), false));
            foregroundRefused = false;
        } catch (Throwable error) {
            foregroundRefused = true;
            Log.w(TAG, "startForeground refused", error);
            Report.problem("系统拒绝了前台服务，改以普通服务继续", error);
        }
    }

    private long lastNotifyAt;
    private boolean firstNotify = true;

    /** Notification delivery is best effort; it must never take the app down.
     *  Updates are also throttled: re-posting a notice every few hundred ms is what makes
     *  it jump around the notification shade on some ROMs. */
    private void notify(Job job) {
        if (manager == null) return;
        long now = System.currentTimeMillis();
        // Do not gate XMSF on the service main thread; the first worker progress event publishes
        // the focus notification. Pending/idle notices always use the quiet, plain channel.
        boolean useIsland = job != null && islandWanted && XiaomiIslandPublisher.isSupported(this)
                && Thread.currentThread() != Looper.getMainLooper().getThread();
        boolean firstFrame = useIsland && islandFirstFrame;
        if (now - lastNotifyAt < 5000 && !firstNotify && !firstFrame) return;
        firstNotify = false;
        lastNotifyAt = now;
        try {
            Notification n = buildNotification(job, useIsland);
            String title, content, subTitle, digits;
            int progress = 0;
            if (job == null) {
                title = "听写间待命";
                content = "随时可以开始转写";
                subTitle = null;
                digits = "0%";
            } else {
                title = job.stage;
                content = job.detail;
                subTitle = job.eta;
                digits = job.indeterminate ? "处理中" : job.percent + "%";
                progress = job.percent;
            }
            if (useIsland) {
                boolean posted = XiaomiIslandPublisher.publish(this, manager, NOTIFICATION_ID, n,
                        firstFrame, title, content, subTitle, digits, progress, "停止", stopActionUri(),
                        firstFrame && bootstrapActive ? focus -> startForeground(NOTIFICATION_ID, focus) : null);
                if (posted) {
                    if (firstFrame) islandFirstFrame = false;
                } else {
                    // An unavailable gate or ROM rejection is a plain-notification fallback,
                    // not a silently "successful" island. Stop retrying the private API.
                    islandWanted = false;
                    n = buildNotification(job, false);
                    if (bootstrapActive) startForeground(NOTIFICATION_ID, n);
                    else XiaomiIslandPublisher.publishPlain(manager, NOTIFICATION_ID, n);
                }
            } else if (bootstrapActive) {
                startForeground(NOTIFICATION_ID, n);
            } else {
                XiaomiIslandPublisher.publishPlain(manager, NOTIFICATION_ID, n);
            }
            if (bootstrapActive) {
                bootstrapActive = false;
                manager.cancel(BOOTSTRAP_ID);
            }
        } catch (Throwable error) {
            Log.w(TAG, "notify failed", error);
        }
    }

    /** Island action target: an activity intent URI, same shape as the reference's actionIntent. */
    private String stopActionUri() {
        Intent stopJob = new Intent(this, MainActivity.class)
                .setAction(MainActivity.ACTION_STOP_JOB)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return stopJob.toUri(Intent.URI_INTENT_SCHEME);
    }

    private void shutDown() {
        busy = false;
        XiaomiIslandPublisher.cancel(this, manager, NOTIFICATION_ID);
        if (manager != null) manager.cancel(BOOTSTRAP_ID);
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        running = false;
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // Swiping the app away must not leave a job or a notification behind.
        cancelled = true;
        if (!busy) shutDown();
        super.onTaskRemoved(rootIntent);
    }

    private void createChannel() {
        if (manager == null) return;
        // LOW: no sound, no heads-up popup - a progress notice must never grab attention.
        for (String id : new String[]{CHANNEL_QUIET, CHANNEL_ISLAND}) {
            NotificationChannel channel = new NotificationChannel(id,
                    id.equals(CHANNEL_ISLAND) ? "超级岛进度（静音）" : "转写进度（静音）",
                    id.equals(CHANNEL_ISLAND)
                            ? NotificationManager.IMPORTANCE_DEFAULT : NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("本机转写的进度，不播放声音或振动");
            channel.enableVibration(false);
            channel.setSound(null, null);
            channel.enableLights(false);
            channel.setShowBadge(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            manager.createNotificationChannel(channel);
        }
    }

    private void startRun(File input, String name, boolean wantSpeakers, int count, boolean island, String engine) {
        if ("cloud".equals(engine)) {
            startCloudRun(input);
            return;
        }
        startLocalRun(input, wantSpeakers, count, island);
    }

    /** The offline path: decode, stream-recognize, punctuate, cluster speakers. */
    private void startLocalRun(File input, boolean wantSpeakers, int count, boolean island) {
        Bus.reset();
        cancelled = false;
        busy = true;
        islandWanted = island;
        startedAt = System.currentTimeMillis();
        islandFirstFrame = true;
        lastPercent = -2;
        floorPercent = 0;
        lastStage = "准备中";
        // The foreground notification is already on the correct channel. First island payload
        // goes out with the first measurable worker event so the XMSF gate can hold for 220ms.
        final boolean speakers = wantSpeakers;
        worker = new Thread(() -> {
            try {
                new Transcriber(this).transcribe(input, speakers, count, event -> {
                    if (cancelled) throw new CancelledException();
                    handle(event);
                });
            } catch (CancelledException e) {
                postError("已停止");
            } catch (Throwable t) {
                Log.w(TAG, "transcription failed", t);
                postError(describe(t));
            } finally {
                busy = false;
                ui.post(() -> {
                    ui.removeCallbacks(idleShutdown);
                    ui.postDelayed(idleShutdown, DONE_LINGER_MS);
                });
            }
        }, "transcribe");
        worker.start();
    }

    /**
     * Optional cloud transcription (MiMo-style audio input): the recording is decoded locally,
     * uploaded as short WAV chunks with progress per chunk, and the text is stitched back together.
     * Off by default - it needs a key in Settings, and local stays the primary path.
     */
    private void startCloudRun(File input) {
        Bus.reset();
        cancelled = false;
        busy = true;
        islandWanted = false;
        startedAt = System.currentTimeMillis();
        islandFirstFrame = true;
        lastPercent = -2;
        floorPercent = 0;
        lastStage = "云端识别";
        // Foreground notification was already posted by ensureForeground().
        worker = new Thread(() -> {
            try {
                if (!Cloud.hasAsr(this)) {
                    throw new IllegalStateException("还没有设置云端识别模型：设置 → 云端 AI → 识别模型");
                }
                CloudChunker chunker = new CloudChunker();
                PcmDecoder.decode(input, chunker);
                chunker.finish();
            } catch (CancelledException e) {
                postError("已停止");
            } catch (Throwable t) {
                Log.w(TAG, "cloud transcription failed", t);
                postError(describe(t));
            } finally {
                busy = false;
                ui.post(() -> {
                    ui.removeCallbacks(idleShutdown);
                    ui.postDelayed(idleShutdown, DONE_LINGER_MS);
                });
            }
        }, "cloud-asr");
        worker.start();
    }

    /** Accumulates decoded audio into uploadable chunks and reports honest audio-time progress. */
    private final class CloudChunker implements PcmDecoder.Sink {
        final int sampleRate = 16000;
        final float[] buffer = new float[sampleRate * 120];
        int filled;
        double chunkStart;
        int completed;
        double total;
        long lastEvent;
        final StringBuilder text = new StringBuilder();
        final JSONArray segments = new JSONArray();

        @Override
        public void accept(float[] samples, int length, double seconds, double totalSecondsIn) throws Exception {
            if (cancelled) throw new CancelledException();
            if (totalSecondsIn > 0) total = totalSecondsIn;
            int offset = 0;
            while (offset < length) {
                int take = Math.min(length - offset, buffer.length - filled);
                System.arraycopy(samples, offset, buffer, filled, take);
                filled += take;
                offset += take;
                if (filled == buffer.length) {
                    uploadChunk();
                }
            }
            long now = System.currentTimeMillis();
            if (now - lastEvent > 250) {
                lastEvent = now;
                postProgress(seconds);
            }
        }

        void uploadChunk() throws Exception {
            if (filled == 0) return;
            float[] chunk = new float[filled];
            System.arraycopy(buffer, 0, chunk, 0, filled);
            double end = chunkStart + (double) filled / sampleRate;
            byte[] wav = Cloud.toWav(chunk);
            String chunkText = Cloud.asr(LocalService.this, wav, null);
            if (chunkText != null && !chunkText.trim().isEmpty()) {
                String clean = chunkText.trim();
                text.append(clean);
                JSONObject segment = new JSONObject();
                segment.put("start", chunkStart);
                segment.put("end", end);
                segment.put("speaker", -1);
                segment.put("text", clean);
                segments.put(segment);
            }
            completed++;
            chunkStart = end;
            filled = 0;
            postProgress(end);
        }

        void postProgress(double processed) throws Exception {
            JSONObject event = new JSONObject();
            event.put("type", "progress");
            event.put("processed", processed);
            event.put("total", total);
            event.put("completed", completed);
            event.put("message", "云端识别");
            handle(event);
        }

        void finish() throws Exception {
            uploadChunk();
            JSONObject result = new JSONObject();
            result.put("type", "result");
            result.put("segments", segments);
            result.put("duration", total);
            result.put("text", text.toString());
            result.put("warning", segments.length() == 0
                    ? "云端识别没有返回文本"
                    : "云端识别：未区分发言人。");
            handle(result);
        }
    }

    /** Cancellation travels back through the transcriber's own event callback. */
    private static final class CancelledException extends IOException {
        CancelledException() {
            super("cancelled");
        }
    }

    private void handle(JSONObject event) {
        String type = event.optString("type");
        Job job = Job.of(type, event.optString("message"), event.optDouble("processed", 0),
                event.optDouble("total", 0), event.optInt("completed"), startedAt, System.currentTimeMillis());
        if (job != null) {
            job = job.atLeast(floorPercent);
            floorPercent = job.percent;
            try {
                // job travels with the event so the screen and the island show the same number.
                event.put("job", job.percent);
                event.put("stage", job.stage);
                event.put("detail", job.detail);
                event.put("eta", job.eta);
                event.put("indeterminate", job.indeterminate);
            } catch (Exception ignored) {
            }
            updateNotice(job);
        }
        Bus.post(event);
    }

    private void postError(String message) {
        JSONObject event = new JSONObject();
        try {
            event.put("type", "error");
            event.put("message", message);
        } catch (Exception ignored) {
        }
        updateNotice(null);
        Bus.post(event);
    }

    private void updateNotice(Job job) {
        if (job == null) {
            lastPercent = -2;
            floorPercent = 0;
            lastStage = "";
            // Keep the stable foreground ID/channel until idleShutdown, never repost an
            // unrelated "待命" item at a different rank while the shade is open.
            return;
        }
        // Rebuild only when something a human would notice changes.
        if (job.percent == lastPercent && job.stage.equals(lastStage)) return;
        lastPercent = job.percent;
        lastStage = job.stage;
        notify(job);
    }

    private Notification buildNotification(Job job) {
        return buildNotification(job, false);
    }

    private Notification buildNotification(Job job, boolean useIsland) {
        Intent open = new Intent(this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openIntent = PendingIntent.getActivity(this, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, LocalService.class).setAction(ACTION_CANCEL);
        PendingIntent stopIntent = PendingIntent.getService(this, 2, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Field choices mirror the verified reference (XiaomiSuperIslandPublisher.kt): STATUS
        // category, public visibility, coloured but not colourised, never auto-cancel.
        Notification.Builder builder = new Notification.Builder(this,
                useIsland ? CHANNEL_ISLAND : CHANNEL_QUIET)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(openIntent)
                .setWhen(NOTIFY_WHEN)            // one constant: a changing `when` re-sorts and re-alerts
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setAutoCancel(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setColorized(false)
                .setColor(getColor(R.color.accent))
                .setOngoing(job != null)
                .setCategory(Notification.CATEGORY_STATUS);
        if (!useIsland) builder.setDefaults(0).setSound(null).setVibrate(null);
        if (job == null) {
            builder.setContentTitle("听写间待命").setContentText("随时可以开始转写");
        } else {
            builder.setContentTitle(job.stage + (job.indeterminate ? " · 处理中" : " · " + job.percent + "%"))
                    .setContentText(job.detail)
                    .setSubText(job.eta)
                    .setProgress(job.indeterminate ? 0 : 100, job.indeterminate ? 0 : job.percent,
                            job.indeterminate)
                    .addAction(new Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_stop),
                            "停止", stopIntent).build());
        }
        return builder.build();
    }

    private static String describe(Throwable error) {
        if (error instanceof Cloud.ApiException) return ((Cloud.ApiException) error).hint;
        String message = error.getMessage();
        if (message == null || message.isEmpty()) message = error.getClass().getSimpleName();
        return message.length() > 160 ? message.substring(0, 160) : message;
    }
}
