package com.example.tingxiejian;

import android.content.Context;
import android.util.AtomicFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/** Actual Android adapter APIs with test-only Context/AtomicFile filesystem stand-ins. */
public final class SessionRepositoryCheck {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        Path folder = Files.createTempDirectory("session-adapter-");
        try {
            Context context = new Context(folder.toFile());
            check(SessionRepository.latest(context) == null, "empty app data has no fictitious active session");
            String id = SessionRepository.begin(context, "file", "保存会议");
            Path base = folder.resolve("sessions-v1.bin");
            check(Files.size(base) > 0 && SessionRepository.get(context, id).state == SessionState.State.RUNNING,
                    "begin commits app-private session metadata");
            byte[] before = Files.readAllBytes(base);
            AtomicFile.failNextFinish = true;
            boolean failed = false;
            try { SessionRepository.transition(context, id, SessionState.State.DONE, "saved"); }
            catch (IOException expected) { failed = true; }
            check(failed && Arrays.equals(before, Files.readAllBytes(base)), "failed atomic write rolls back durable state");
            check(SessionRepository.get(context, id).state == SessionState.State.RUNNING,
                    "next API read observes rollback rather than cached DONE");
            check(SessionRepository.recoverInterrupted(context) == 1, "application recovery interrupts stale session");
            check(!SessionRepository.transition(context, id, SessionState.State.DONE, "late old worker"),
                    "adapter rejects stale-process completion after recovery");
            Set<String> ids = java.util.Collections.synchronizedSet(new HashSet<>());
            AtomicReference<Throwable> error = new AtomicReference<>();
            Thread[] workers = new Thread[8];
            for (int i = 0; i < workers.length; i++) {
                workers[i] = new Thread(() -> {
                    try { ids.add(SessionRepository.begin(context, "live", "concurrent")); }
                    catch (Throwable failure) { error.set(failure); }
                });
                workers[i].start();
            }
            for (Thread worker : workers) worker.join();
            check(error.get() == null && ids.size() == 8, "concurrent callers acquire unique durable sessions");
            for (String concurrent : ids) check(SessionRepository.get(context, concurrent) != null,
                    "concurrent snapshots do not lose writes");
            Path backup = folder.resolve("sessions-v1.bin.bak");
            Files.copy(base, backup, StandardCopyOption.REPLACE_EXISTING);
            Files.write(base, new byte[]{1, 2, 3});
            check(SessionRepository.get(context, id).state == SessionState.State.INTERRUPTED,
                    "adapter openRead uses atomic rollback recovery before decoding");
            Files.write(base, new byte[]{1, 2, 3});
            failed = false;
            try { SessionRepository.begin(context, "file", "corrupt"); } catch (IOException expected) { failed = true; }
            check(failed && Files.size(base) == 3, "corrupt ledger cannot be silently overwritten by a fresh RUNNING session");
            System.out.println("PASS: session Android adapter (" + checks + " assertions; filesystem stand-ins)");
        } finally {
            try (java.util.stream.Stream<Path> files = Files.walk(folder)) {
                files.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (IOException error) { throw new RuntimeException(error); }
                });
            }
        }
    }
}
