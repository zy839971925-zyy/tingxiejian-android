package android.media;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/** Controlled test-only AudioRecord stand-in; latches coordinate read/stop races. */
public final class AudioRecord {
    public static final int STATE_INITIALIZED = 1;
    public static final int READ_BLOCKING = 0;
    public static final int ERROR_INVALID_OPERATION = -3;

    public interface Reader { int read(short[] samples, int offset, int length) throws Exception; }
    public static final class Plan {
        public int minBufferSize = 1280;
        public int state = STATE_INITIALIZED;
        public RuntimeException constructorFailure;
        public RuntimeException startFailure;
        public Reader reader;
        public Runnable onStop;
        public CountDownLatch constructorBlock;
        public final AtomicInteger reads = new AtomicInteger();
        public final AtomicInteger starts = new AtomicInteger();
        public final AtomicInteger stops = new AtomicInteger();
        public final CountDownLatch constructed = new CountDownLatch(1);
        public final CountDownLatch constructorEntered = new CountDownLatch(1);
        public final CountDownLatch started = new CountDownLatch(1);
        public final CountDownLatch resumed = new CountDownLatch(1);
        public final CountDownLatch readEntered = new CountDownLatch(1);
        public final CountDownLatch stopCalled = new CountDownLatch(1);
        public final CountDownLatch released = new CountDownLatch(1);
        public volatile int sampleRate;
        public volatile int channels;
        public volatile int encoding;
        public volatile int bufferSize;
    }

    public static volatile Plan nextPlan;
    private final Plan plan;
    private volatile boolean recording;

    public static int getMinBufferSize(int rate, int channels, int encoding) {
        if (nextPlan == null) throw new IllegalStateException("test plan missing");
        return nextPlan.minBufferSize;
    }

    public AudioRecord(int input, int rate, int channels, int encoding, int bufferSize) {
        plan = nextPlan;
        if (plan == null) throw new IllegalStateException("test plan missing");
        if (plan.constructorFailure != null) throw plan.constructorFailure;
        plan.constructorEntered.countDown();
        if (plan.constructorBlock != null) {
            try {
                if (!plan.constructorBlock.await(3, java.util.concurrent.TimeUnit.SECONDS))
                    throw new IllegalStateException("controlled constructor timeout");
            } catch (InterruptedException interruption) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("controlled constructor interrupted", interruption);
            }
        }
        plan.sampleRate = rate;
        plan.channels = channels;
        plan.encoding = encoding;
        plan.bufferSize = bufferSize;
        plan.constructed.countDown();
    }

    public int getState() { return plan.state; }

    public void startRecording() {
        if (plan.startFailure != null) throw plan.startFailure;
        recording = true;
        int starts = plan.starts.incrementAndGet();
        plan.started.countDown();
        if (starts >= 2) plan.resumed.countDown();
    }

    public int read(short[] samples, int offset, int length, int mode) {
        if (!recording) throw new IllegalStateException("read outside recording");
        plan.reads.incrementAndGet();
        plan.readEntered.countDown();
        try {
            return plan.reader.read(samples, offset, length);
        } catch (RuntimeException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("controlled read failed", failure);
        }
    }

    public void stop() {
        recording = false;
        plan.stops.incrementAndGet();
        plan.stopCalled.countDown();
        if (plan.onStop != null) plan.onStop.run();
    }

    public void release() { plan.released.countDown(); }
}
