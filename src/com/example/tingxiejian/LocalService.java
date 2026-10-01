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
import java.io.InterruptedIOException;

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
    private static volatile String activeName = "";
    private static volatile String activePath = "";
    private static volatile String activeEngine = "local";
    private static volatile LocalService owner;

    static String activeName() { return activeName; }
    static String activePath() { return activePath; }
    static String activeEngine() { return activeEngine; }
    static String progressChannel() { return CHANNEL_QUIET; }

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
    private final AndroidLiveUpdatePublisher liveUpdates=new AndroidLiveUpdatePublisher();
    private volatile Thread worker;
    private volatile boolean cancelled;
    private volatile boolean destroyed;
    private volatile boolean shutdownRequested;
    private volatile SessionState.State forcedTerminal;
    private volatile String sessionId;
    private final Object completionLock = new Object();
    private boolean terminalPosted;
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
        owner = this;
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
            finishFailure(SessionState.State.FAILED, "服务命令失败：" + describe(error));
            shutDown();
            return START_NOT_STICKY;
        }
    }

    private int handleCommand(Intent intent) throws IOException {
        if (destroyed || shutdownRequested) {
            stopSelf();
            return START_NOT_STICKY;
        }
        String action = intent == null ? null : intent.getAction();
        if (ACTION_CANCEL.equals(action)) {
            cancelRun(SessionState.State.CANCELLED, "已停止");
            shutDown();
            return START_NOT_STICKY;
        }
        if (ACTION_STOP.equals(action)) {
            cancelRun(SessionState.State.CANCELLED, "已停止");
            shutDown();
            return START_NOT_STICKY;
        }
        String path = intent == null ? null : intent.getStringExtra(EXTRA_PATH);
        if (path != null && busy) return START_NOT_STICKY; // Don't reset an active foreground notice.
        if (path == null) {
            shutDown(); // START_NOT_STICKY never recreates a job, nor an idle dataSync service.
            return START_NOT_STICKY;
        }
        // FGS must start promptly: never query Xiaomi ContentProviders on the service main thread.
        // Manufacturer is only a cheap *channel* hint; real capability is checked on the worker
        // before attaching any private island payload.
        boolean requestIsland = path != null && intent.getBooleanExtra(EXTRA_ISLAND, true)
                && !"cloud".equals(intent.getStringExtra(EXTRA_ENGINE))
                && android.os.Build.MANUFACTURER.toLowerCase(java.util.Locale.ROOT).contains("xiaomi");
        ensureForeground(requestIsland);
        if(requestIsland&&!foregroundRefused){
            // ID12 is now the legal FGS base. Retire a preceding run's lingering ID11 so the
            // verified first focus post is a NEW notification even for consecutive tasks.
            XiaomiIslandPublisher.cancel(this,manager,NOTIFICATION_ID);
        }
        if (foregroundRefused) {
            postError("系统拒绝了前台服务，请保持应用在前台后重试");
            shutDown();
            return START_NOT_STICKY;
        }
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
            islandWanted = false; // A failed OEM submission must not retry its private API this run.
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
        shutdownRequested = true;
        liveUpdates.reset();
        if (owner == this) {
            busy = false;
            running = false;
        }
        try {
            XiaomiIslandPublisher.cancel(this, manager, NOTIFICATION_ID);
            if (manager != null) manager.cancel(BOOTSTRAP_ID);
            stopForeground(true);
        } catch (Throwable error) {
            Log.w(TAG, "service cleanup failed", error);
        } finally {
            stopSelf(); // Notification cleanup failure must not prevent the service from stopping.
        }
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        if (forcedTerminal == null) forcedTerminal = SessionState.State.INTERRUPTED;
        cancelled = true;
        interruptWorker();
        ui.removeCallbacksAndMessages(null);
        if (owner == this) {
            shutDown();
            owner = null;
        }
        // A native decoder may take time to return. It cannot publish another result, and disk
        // recovery runs separately so destruction never waits for inference or a file write.
        persistTerminationAsync(forcedTerminal, forcedTerminal == SessionState.State.CANCELLED
                ? "已停止" : "转写服务被系统中断，请重新开始");
        super.onDestroy();
    }

    /** Android 15 dataSync budget expiry: stop immediately, never wait for native inference. */
    @Override
    public void onTimeout(int startId, int fgsType) {
        forcedTerminal = SessionState.State.INTERRUPTED;
        cancelled = true;
        interruptWorker();
        ui.removeCallbacksAndMessages(null);
        stopSelf(); // Meet the few-second platform deadline before even notification cleanup.
        shutDown();
        persistTerminationAsync(SessionState.State.INTERRUPTED, "系统前台服务时限已到，请重新开始转写");
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // Swiping the app away must not leave a job or a notification behind.
        cancelRun(SessionState.State.CANCELLED, "已停止");
        shutDown();
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

    private void startRun(File input, String name, boolean wantSpeakers, int count, boolean island, String engine)
            throws IOException {
        // Dictation uses the same monitor when acquiring its single active slot.
        synchronized (DictationController.class) {
            if (DictationController.isBusy()) {
                postError("实时听写尚未结束，请先结束并保存后再转写文件");
                shutDown();
                return;
            }
            busy = true;
        }
        activePath = input.getAbsolutePath();
        activeName = name == null ? "录音" : name;
        activeEngine = "cloud".equals(engine) ? "cloud" : "local";
        sessionId = null;
        forcedTerminal = null;
        terminalPosted = false;
        cancelled = false;
        liveUpdates.reset();
        sessionId = SessionRepository.begin(this, "file", activeName);
        shutdownRequested = false;
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
                    checkCancellation();
                    handle(event);
                });
            } catch (CancelledException e) {
                finishFailure(failureState(), "已停止");
            } catch (Throwable t) {
                Log.w(TAG, "transcription failed", t);
                finishFailure(failureState(),
                        cancelled ? "已停止" : describe(t));
            } finally {
                workerFinished();
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
                checkCancellation();
                new CloudFileTranscriber(this, sessionId, this::checkCancellation, this::handle).transcribe(input);
            } catch (CancelledException e) {
                finishFailure(failureState(), "已停止");
            } catch (Throwable t) {
                Log.w(TAG, "cloud transcription failed", t);
                finishFailure(failureState(),
                        cancelled ? "已停止" : describe(t));
            } finally {
                workerFinished();
            }
        }, "cloud-asr");
        worker.start();
    }

    /** Cancellation travels back through the transcriber's own event callback. */
    static final class CancelledException extends InterruptedIOException {
        CancelledException() {
            super("cancelled");
        }
    }

    private void checkCancellation() throws CancelledException {
        if (cancelled || destroyed || shutdownRequested || Thread.currentThread().isInterrupted())
            throw new CancelledException();
    }

    private void interruptWorker() {
        Thread current = worker;
        if (current != null) current.interrupt();
    }

    private SessionState.State failureState() {
        if (forcedTerminal != null) return forcedTerminal;
        if (destroyed || shutdownRequested) return SessionState.State.INTERRUPTED;
        return cancelled ? SessionState.State.CANCELLED : SessionState.State.FAILED;
    }

    private void cancelRun(SessionState.State state, String message) {
        synchronized (completionLock) {
            if (terminalPosted) return; // Completion already won; stopping cannot rewrite DONE.
            forcedTerminal = state;
            cancelled = true;
            interruptWorker();
            finishFailure(state, message);
        }
    }

    private void persistTerminationAsync(SessionState.State state, String message) {
        if (sessionId == null) return;
        new Thread(() -> finishFailure(state, message), "session-stop").start();
    }

    private void finishFailure(SessionState.State state, String message) {
        synchronized (completionLock) {
            if (terminalPosted) return;
            if (sessionId != null) {
                try {
                    SessionState prior = SessionRepository.get(this, sessionId);
                    if (prior != null && prior.isTerminal() && prior.state != state) {
                        terminalPosted = true;
                        return;
                    }
                    SessionRepository.transition(this, sessionId, state, message);
                } catch (Exception error) {
                    Report.problem("保存会话状态失败", error);
                    message += "（会话状态保存失败）";
                }
            }
            terminalPosted = true;
            Bus.reset(); // A re-attached screen must not inherit a stale RUNNING progress snapshot.
            try { postError(message); }
            catch (Throwable error) { Report.problem("发布会话结束状态失败", error); }
        }
    }

    private void workerFinished() {
        // Returning without a result is a failure, not an eternal RUNNING session.
        finishFailure(failureState(),
                cancelled ? "已停止" : "识别结束，但没有返回转写结果");
        if (worker == Thread.currentThread()) worker = null;
        if (owner == this) busy = false;
        if (destroyed || shutdownRequested) return;
        ui.post(() -> {
            if (destroyed || shutdownRequested || owner != this) return;
            ui.removeCallbacks(idleShutdown);
            ui.postDelayed(idleShutdown, DONE_LINGER_MS);
        });
    }

    private void handle(JSONObject event) throws CancelledException {
        checkCancellation();
        String type = event.optString("type");
        if ("result".equals(type)) {
            synchronized (completionLock) {
                checkCancellation();
                if (terminalPosted) return;
            }
            String savedId;
            try {
                // Persist before broadcasting. If the Activity was stopped or killed during the
                // job, a completed transcript must still be in History when it returns.
                savedId = History.save(this, savedResult(event));
                event.put("saved_id", savedId);
            } catch (Exception error) {
                Report.problem("保存转写结果失败", error);
                finishFailure(SessionState.State.FAILED, "转写完成，但保存结果失败：" + error.getMessage());
                return;
            }
            synchronized (completionLock) {
                // History I/O stays outside this lock: timeout must never wait for a large result.
                if (cancelled || destroyed || shutdownRequested || terminalPosted) {
                    History.delete(this, savedId);
                    checkCancellation();
                    return;
                }
                try {
                    if (!SessionRepository.transition(this, sessionId, SessionState.State.DONE, "转写已保存")) {
                        History.delete(this, savedId);
                        return;
                    }
                    terminalPosted = true;
                    event.put("session_id", sessionId);
                    event.put("session_state", SessionState.State.DONE.name());
                } catch (Exception error) {
                    History.delete(this, savedId);
                    finishFailure(SessionState.State.FAILED, "保存完成状态失败：" + describe(error));
                    return;
                }
                publishEvent(event);
            }
            return;
        }
        synchronized (completionLock) {
            checkCancellation();
            if (terminalPosted) return;
            if ("error".equals(type)) {
                finishFailure(SessionState.State.FAILED, event.optString("message", "转写失败"));
                return;
            }
            try {
                if ("phase".equals(type)) {
                    String state = event.optString("state");
                    String message = event.optString("message");
                    SessionState.State target = null;
                    if ("POLISHING".equals(state)) target = SessionState.State.POLISHING;
                    else if ("FINALIZING".equals(state)) target = SessionState.State.FINALIZING;
                    if (target != null) SessionRepository.transition(this, sessionId, target, message);
                }
                event.put("session_id", sessionId);
            } catch (Exception error) {
                throw new IllegalStateException("保存转写阶段失败", error);
            }
            publishEvent(event);
        }
    }

    private void publishEvent(JSONObject event) throws CancelledException {
        checkCancellation();
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
            publishProgress(job);
        }
        if (cancelled || destroyed || shutdownRequested) {
            if (owner == this || owner == null) shutDown(); // Remove an OEM notice that returned late.
            throw new CancelledException();
        }
        Bus.post(event);
    }

    private JSONObject savedResult(JSONObject event) throws Exception {
        JSONArray segments = event.optJSONArray("segments");
        if (segments == null) segments = new JSONArray();
        java.util.Set<Integer> speakers = new java.util.HashSet<>();
        for (int i = 0; i < segments.length(); i++) {
            JSONObject segment = segments.optJSONObject(i);
            if (segment != null && segment.optInt("speaker", -1) >= 0)
                speakers.add(segment.optInt("speaker"));
        }
        // Preserve raw/final/polished segments, model and hotword metadata from the pipeline.
        JSONObject saved = new JSONObject(event.toString());
        for (String transport : new String[]{"type", "saved_id", "job", "stage", "detail", "eta", "indeterminate"})
            saved.remove(transport);
        saved.put("name", activeName);
        saved.put("createdAt", System.currentTimeMillis());
        saved.put("durationSeconds", event.optDouble("duration", 0));
        saved.put("engine", activeEngine);
        saved.put("speakers", speakers.size());
        saved.put("warning", event.isNull("warning") ? "" : event.optString("warning", ""));
        saved.put("segments", segments);
        saved.put("text", event.optString("text", ""));
        saved.put("session_id", sessionId);
        return saved;
    }

    private void postError(String message) {
        JSONObject event = new JSONObject();
        try {
            event.put("type", "error");
            event.put("message", message);
            if (sessionId != null) event.put("session_id", sessionId);
            event.put("session_state", (forcedTerminal == null ? SessionState.State.FAILED : forcedTerminal).name());
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

    /** Keep the verified Xiaomi implementation/timing intact, then use one standard FGS surface. */
    private void publishProgress(Job job) {
        if(job==null||cancelled||destroyed||shutdownRequested||manager==null)return;
        if(islandWanted&&Thread.currentThread()!=Looper.getMainLooper().getThread()){
            try{
                if(XiaomiIslandPublisher.isSupported(this)){
                    updateNotice(job);
                    if(islandWanted)return;
                }else islandWanted=false; // Route downward for this run; do not later replace ID11 with a "first" focus update.
            }catch(RuntimeException|LinkageError error){
                islandWanted=false;Log.w(TAG,"island capability/publication failed; using standard progress",error);
            }
        }
        try{
            liveUpdates.publish(this,manager,CHANNEL_QUIET,job,buildNotification(job,false),notification->{
                if(cancelled||destroyed||shutdownRequested)return;
                // A Xiaomi first-frame failure may already have moved ID12 to ID11. Never remove
                // the foreground notification because an alternate presentation was submitted.
                if(bootstrapActive){startForeground(NOTIFICATION_ID,notification);bootstrapActive=false;manager.cancel(BOOTSTRAP_ID);}
                else manager.notify(NOTIFICATION_ID,notification);
            },android.os.SystemClock.elapsedRealtime());
        }catch(RuntimeException|LinkageError error){Log.w(TAG,"standard progress notification failed",error);}
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
