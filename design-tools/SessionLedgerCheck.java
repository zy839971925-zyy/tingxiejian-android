import com.example.tingxiejian.SessionLedger;
import com.example.tingxiejian.SessionState;
import com.example.tingxiejian.SessionState.State;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

public final class SessionLedgerCheck {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static final class MemoryStore implements SessionLedger.Store {
        byte[] bytes = new byte[0];
        boolean fail;
        public synchronized byte[] read() { return bytes.clone(); }
        public synchronized void write(byte[] next) throws IOException {
            if (fail) throw new IOException("disk unavailable");
            bytes = next.clone();
        }
    }
    public static void main(String[] args) throws Exception {
        MemoryStore store = new MemoryStore();
        SessionLedger live = new SessionLedger(store);
        String file = live.begin("file", "会议录音", 10);
        check(live.get(file) != null && live.get(file).state == State.RUNNING, "begin exposes persisted RUNNING");
        check(live.transition(file, State.FINALIZING, "finalizing", 20), "advance file finalizer");
        String cancelled = live.begin("dictation", "实时听写", 30);
        live.transition(cancelled, State.CANCELLED, "user stop", 40);
        String done = live.begin("file", "done", 50);
        live.transition(done, State.DONE, "saved", 60);
        String running = live.begin("live", "未结束的录音", 65);
        String polishing = live.begin("file", "校正中", 70);
        live.transition(polishing, State.POLISHING, "checking", 80);
        SessionLedger restarted = new SessionLedger(store);
        check(restarted.get(file).state == State.FINALIZING, "disk retains stage across process death");
        check(restarted.get(file).name.equals("会议录音"), "UTF-8 names round trip");
        check(restarted.recoverInterrupted(90) == 3, "restart interrupts all stale active stages");
        check(restarted.get(running).state == State.INTERRUPTED, "stale running becomes INTERRUPTED");
        check(restarted.get(file).state == State.INTERRUPTED, "stale finalizing becomes INTERRUPTED");
        check(restarted.get(polishing).state == State.INTERRUPTED, "stale polishing becomes INTERRUPTED");
        check(restarted.get(cancelled).state == State.CANCELLED, "restart preserves cancellation");
        check(restarted.get(done).state == State.DONE, "restart preserves completed history");
        check(restarted.recoverInterrupted(100) == 0, "process recovery is idempotent");
        check(!restarted.transition(cancelled, State.DONE, "late callback", 100), "late success rejected");
        check(!restarted.transition(file, State.DONE, "old process callback", 100), "interrupted result rejected");
        check(!restarted.transition("unknown", State.DONE, "", 100), "unknown session cannot become done");
        check(restarted.latest().id.equals(polishing), "latest remains latest begun session");
        String failing = restarted.begin("file", "failure", 100);
        byte[] before = store.bytes.clone();
        store.fail = true;
        boolean threw = false;
        try { restarted.transition(failing, State.DONE, "saved", 110); }
        catch (IOException expected) { threw = true; }
        check(threw, "write failure reaches caller");
        check(restarted.get(failing).state == State.RUNNING, "failed write cannot announce in-memory DONE");
        check(Arrays.equals(before, store.bytes), "failed write preserves durable prior state");
        threw = false;
        try { restarted.begin("file", "cannot begin", 111); } catch (IOException expected) { threw = true; }
        check(threw && restarted.latest().id.equals(failing), "failed begin cannot fabricate an active session");
        store.fail = false;
        String race = restarted.begin("file", "cancel race", 120);
        AtomicReference<Throwable> threadFailure = new AtomicReference<>();
        Thread cancel = new Thread(() -> {
            try { restarted.transition(race, State.CANCELLED, "stop", 121); }
            catch (Throwable error) { threadFailure.set(error); }
        });
        cancel.start(); cancel.join();
        Thread late = new Thread(() -> {
            try { restarted.transition(race, State.DONE, "late", 122); }
            catch (Throwable error) { threadFailure.set(error); }
        });
        late.start(); late.join();
        check(threadFailure.get() == null && restarted.get(race).state == State.CANCELLED,
                "worker completion after another thread cancels is absorbed");
        MemoryStore corrupt = new MemoryStore();
        corrupt.bytes = Arrays.copyOf(store.bytes, store.bytes.length - 3);
        threw = false;
        try { new SessionLedger(corrupt); } catch (IOException expected) { threw = true; }
        check(threw, "truncated persistence is detected rather than forged RUNNING");
        MemoryStore bounded = new MemoryStore();
        SessionLedger retained = new SessionLedger(bounded);
        String active = retained.begin("live", "active", 1);
        String oldest = retained.begin("file", "old", 2);
        retained.transition(oldest, State.DONE, "saved", 3);
        for (int i = 0; i < 260; i++) {
            String id = retained.begin("file", "terminal", 4 + i);
            retained.transition(id, State.DONE, "saved", 5 + i);
        }
        check(retained.get(active).state == State.RUNNING, "retention never evicts an active session");
        check(retained.get(oldest) == null, "old terminal sessions are bounded");
        check(bounded.bytes.length < SessionLedger.MAX_BYTES, "metadata remains bounded");
        System.out.println("PASS: session persistence (" + checks + " assertions)");
    }
}
