package com.example.tingxiejian;

import android.media.AudioFormat;
import android.media.AudioRecord;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Exercises the actual shipped microphone source/queue with controlled AudioRecord stand-ins. */
public final class MicrophoneCheck {
    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void await(CountDownLatch latch, String message) throws Exception {
        check(latch.await(3, TimeUnit.SECONDS), "timed out: " + message);
    }

    private static void readAwait(CountDownLatch latch) throws Exception {
        if (!latch.await(3, TimeUnit.SECONDS)) throw new IOException("controlled read timeout");
    }

    private static final class Run {
        final MicrophoneAudioSource source = new MicrophoneAudioSource();
        final List<float[]> frames = new ArrayList<>();
        final List<Double> positions = new ArrayList<>();
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        volatile double seconds = -1;

        void start(AudioRecord.Plan plan) { start(plan, null); }
        void start(AudioRecord.Plan plan, PcmDecoder.Sink custom) {
            AudioRecord.nextPlan = plan;
            Thread consumer = new Thread(() -> {
                try {
                    seconds = source.pump((samples, length, time, total) -> {
                        frames.add(Arrays.copyOf(samples, length));
                        positions.add(time);
                        if (custom != null) custom.accept(samples, length, time, total);
                    });
                } catch (Throwable error) {
                    failure.set(error);
                } finally { done.countDown(); }
            }, "test-audio-consumer");
            consumer.setDaemon(true);
            consumer.start();
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--stop-tail")) {
            lastPositiveRead();
        } else if (args.length > 0 && args[0].equals("--overload")) {
            overloadTail();
        } else if (args.length > 0 && args[0].equals("--constructor-stop")) {
            stopDuringConstructor();
        } else {
            lastPositiveRead();
            normalStop();
            stoppedBeforePump();
            stopDuringConstructor();
            permissionAndInitialization();
            partialFailure();
            pauseResume();
            overloadTail();
            consumerFailure();
            directQueue();
        }
        System.out.println("PASS: microphone capture (" + checks + " assertions; AudioRecord stand-in, no Android proof)");
    }

    private static void lastPositiveRead() throws Exception {
        Run run = new Run();
        AudioRecord.Plan plan = new AudioRecord.Plan();
        CountDownLatch allowReturn = new CountDownLatch(1);
        plan.onStop = allowReturn::countDown;
        plan.reader = (block, offset, length) -> {
            readAwait(allowReturn);
            Arrays.fill(block, offset, offset + length, (short) 1000);
            block[offset] = Short.MIN_VALUE;
            block[offset + 1] = Short.MAX_VALUE;
            return length;
        };
        run.start(plan);
        await(plan.readEntered, "positive read entered");
        run.source.stop();
        await(run.done, "stop drains final positive read");
        check(run.failure.get() == null, "concurrent stop does not fail completed read");
        check(run.frames.size() == 1, "last positive read is retained after concurrent stop");
        check(run.frames.get(0).length == 640, "last read keeps every PCM sample");
        check(run.frames.get(0)[0] == -1f && run.frames.get(0)[1] == 32767 / 32768f,
                "16-bit PCM scales both signed endpoints correctly");
        check(Math.abs(run.seconds - .04) < 1e-9 && run.positions.get(0) == .04,
                "duration and sink position include final captured frame");
        check(plan.released.getCount() == 0 && run.source.warning().isEmpty(), "normal tail drain releases recorder without false warning");
        check(plan.sampleRate == 16000 && plan.channels == AudioFormat.CHANNEL_IN_MONO
                && plan.encoding == AudioFormat.ENCODING_PCM_16BIT && plan.bufferSize >= 1280,
                "capture uses supported 16 kHz mono PCM configuration");
    }

    private static void normalStop() throws Exception {
        Run run = new Run();
        AudioRecord.Plan plan = new AudioRecord.Plan();
        CountDownLatch release = new CountDownLatch(1);
        plan.onStop = release::countDown;
        plan.reader = (block, offset, length) -> { readAwait(release); return AudioRecord.ERROR_INVALID_OPERATION; };
        run.start(plan);
        await(plan.readEntered, "stop read entered");
        run.source.stop();
        await(run.done, "normal stop exits");
        check(run.failure.get() == null && run.seconds == 0, "negative read caused by stop is normal termination");
        check(run.source.warning().isEmpty() && plan.released.getCount() == 0, "normal stop cleans resources without error warning");
    }

    private static void stoppedBeforePump() throws Exception {
        Run run = new Run();
        AudioRecord.Plan plan = new AudioRecord.Plan();
        run.source.stop();
        run.start(plan);
        await(run.done, "pre-start stop exits");
        check(run.failure.get() == null && run.seconds == 0 && plan.constructed.getCount() == 1,
                "stop before start never opens microphone or creates false failure");
    }

    private static void stopDuringConstructor() throws Exception {
        Run run = new Run();
        AudioRecord.Plan plan = new AudioRecord.Plan();
        plan.constructorBlock = new CountDownLatch(1);
        plan.reader = (block, offset, length) -> -3;
        run.start(plan);
        await(plan.constructorEntered, "slow native constructor entered");
        run.source.stop();
        plan.constructorBlock.countDown();
        await(run.done, "stop during construction exits after constructor returns");
        check(plan.starts.get() == 0, "stop during native constructor prevents a later startRecording");
        check(run.failure.get() == null && run.frames.isEmpty() && run.seconds == 0,
                "cancelled initialization does not capture audio or become false failure");
        check(plan.released.getCount() == 0 && plan.reads.get() == 0,
                "cancelled constructor releases recorder without reading microphone");
    }

    private static void permissionAndInitialization() throws Exception {
        AudioRecord.Plan denied = new AudioRecord.Plan();
        denied.startFailure = new SecurityException("microphone permission revoked");
        Run run = new Run(); run.start(denied); await(run.done, "permission failure");
        check(run.failure.get() instanceof SecurityException && run.frames.isEmpty(), "permission failure before audio is visible to caller");
        check(denied.released.getCount() == 0, "permission failure releases initialized recorder");
        AudioRecord.Plan unavailable = new AudioRecord.Plan(); unavailable.state = 0;
        run = new Run(); run.start(unavailable); await(run.done, "initialization failure");
        check(run.failure.get() instanceof IOException && run.failure.get().getMessage().contains("初始化"),
                "uninitialized microphone fails visibly");
        check(unavailable.released.getCount() == 0 && unavailable.reads.get() == 0, "initialization failure never reads and releases recorder");
        AudioRecord.Plan unsupported = new AudioRecord.Plan(); unsupported.minBufferSize = -2;
        run = new Run(); run.start(unsupported); await(run.done, "unsupported sample rate");
        check(run.failure.get() instanceof IOException && unsupported.constructed.getCount() == 1,
                "unsupported 16 kHz input fails visibly before opening recorder");
        AudioRecord.Plan lost = new AudioRecord.Plan(); lost.reader = (block, offset, length) -> -3;
        run = new Run(); run.start(lost); await(run.done, "read failure");
        check(run.failure.get() instanceof IOException && lost.released.getCount() == 0,
                "read error without explicit stop is visible and cleaned up");
    }

    private static void partialFailure() throws Exception {
        AudioRecord.Plan plan = new AudioRecord.Plan();
        plan.reader = (block, offset, length) -> {
            if (plan.reads.get() > 1) throw new SecurityException("permission revoked mid-capture");
            Arrays.fill(block, (short) 1024); return length;
        };
        Run run = new Run(); run.start(plan); await(run.done, "partial permission failure");
        check(run.failure.get() == null && run.frames.size() == 1 && run.seconds == .04,
                "mid-capture failure preserves already captured audio");
        check(run.source.warning().contains("SecurityException") && run.source.warning().contains("已保存"),
                "mid-capture interruption is visibly reported, not called complete capture");
        check(plan.released.getCount() == 0, "partial failure releases microphone");
    }

    private static void pauseResume() throws Exception {
        Run run = new Run();
        AudioRecord.Plan plan = new AudioRecord.Plan();
        CountDownLatch releaseRead = new CountDownLatch(1);
        CountDownLatch firstFrame = new CountDownLatch(1);
        plan.onStop = () -> { if (plan.reads.get() > 0) releaseRead.countDown(); };
        plan.reader = (block, offset, length) -> {
            if (plan.reads.get() == 1) { Arrays.fill(block, (short) 2048); return length; }
            readAwait(releaseRead); return AudioRecord.ERROR_INVALID_OPERATION;
        };
        run.source.pause(true);
        run.start(plan, (data, length, time, total) -> firstFrame.countDown());
        await(plan.stopCalled, "paused recorder stops before reading");
        check(plan.reads.get() == 0 && plan.starts.get() == 1, "paused source does not capture PCM");
        run.source.pause(false);
        await(plan.resumed, "recorder restarts on resume");
        await(firstFrame, "resumed audio delivered");
        run.source.stop();
        await(run.done, "resumed source stops");
        check(run.failure.get() == null && run.frames.size() == 1 && run.seconds == .04,
                "resume captures audio once and later stop is clean");
        check(plan.starts.get() == 2 && plan.released.getCount() == 0, "resume reuses recorder and final cleanup releases it");
    }

    private static void overloadTail() throws Exception {
        Run run = new Run();
        AudioRecord.Plan plan = new AudioRecord.Plan();
        CountDownLatch holdConsumer = new CountDownLatch(1);
        CountDownLatch consumerBlocked = new CountDownLatch(1);
        plan.reader = (block, offset, length) -> { Arrays.fill(block, (short) plan.reads.get()); return length; };
        run.start(plan, (data, length, time, total) -> {
            if (consumerBlocked.getCount() != 0) { consumerBlocked.countDown(); readAwait(holdConsumer); }
        });
        await(consumerBlocked, "consumer held while capture overruns");
        await(plan.released, "overload ends capture and releases recorder");
        holdConsumer.countDown();
        await(run.done, "queued audio including overrun tail drains");
        check(run.failure.get() == null, "overload remains a faithful partial capture rather than throwing away text");
        check(plan.reads.get() == 130, "bounded queue ends capture at 128 queued plus consumer and tail frames");
        check(run.frames.size() == 130, "overload retains every already-read frame including tail");
        for (int i = 0; i < run.frames.size(); i++) {
            check(run.frames.get(i).length == 640 && run.frames.get(i)[0] == (i + 1) / 32768f,
                    "overload PCM is delivered once in FIFO order at frame " + i);
        }
        check(Math.abs(run.seconds - 130 * .04) < 1e-9, "overload duration includes overrun tail audio");
        check(run.source.warning().contains("设备跟不上") && run.source.warning().contains("完整保留"),
                "overload is visibly reported with recorded audio retention");
    }

    private static void consumerFailure() throws Exception {
        Run run = new Run();
        AudioRecord.Plan plan = new AudioRecord.Plan();
        CountDownLatch releaseRead = new CountDownLatch(1);
        plan.onStop = releaseRead::countDown;
        plan.reader = (block, offset, length) -> {
            if (plan.reads.get() == 1) { Arrays.fill(block, (short) 3000); return length; }
            readAwait(releaseRead); return -3;
        };
        run.start(plan, (data, length, time, total) -> { throw new IOException("recognizer failed"); });
        await(run.done, "consumer failure stops capture");
        check(run.failure.get() instanceof IOException && run.failure.get().getMessage().equals("recognizer failed"),
                "consumer failure reaches caller");
        check(plan.released.getCount() == 0, "consumer failure still stops and releases recorder");
    }

    private static void directQueue() throws Exception {
        BoundedAudioQueue queue = new BoundedAudioQueue(2);
        check(queue.offer(new float[]{1}, 0) && queue.offer(new float[]{2}, 0), "queue fills to configured bound");
        check(!queue.offer(new float[]{3}, 0) && queue.size() == 2, "overload retains tail without growing bounded queue");
        queue.finish();
        for (int i = 1; i <= 3; i++) check(queue.take()[0] == i, "queue drains original data and tail in order");
        check(queue.take() == null, "finished queue ends after tail drain");
        boolean rejected = false;
        try { queue.offer(new float[]{4}, 0); } catch (IllegalStateException expected) { rejected = true; }
        check(rejected, "finished queue rejects another producer write");
    }
}
