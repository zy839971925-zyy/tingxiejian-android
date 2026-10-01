package com.example.tingxiejian;

import android.content.Context;
import android.util.AtomicFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/** Thread-safe, atomic, app-private session metadata shared by file and live dictation. */
public final class SessionRepository {
    private static final Object LOCK = new Object();

    private SessionRepository() { }

    public static String begin(Context context, String kind, String name) throws IOException {
        synchronized (LOCK) { return open(context).begin(kind, name, System.currentTimeMillis()); }
    }

    /** Returns false for an unknown session, a repeated state, or a late/backward transition. */
    public static boolean transition(Context context, String id, SessionState.State state, String message)
            throws IOException {
        synchronized (LOCK) { return open(context).transition(id, state, message, System.currentTimeMillis()); }
    }

    public static SessionState get(Context context, String id) throws IOException {
        synchronized (LOCK) { return open(context).get(id); }
    }

    public static SessionState latest(Context context) throws IOException {
        synchronized (LOCK) { return open(context).latest(); }
    }

    /** Call once from Application startup, before any new workers can begin. */
    public static int recoverInterrupted(Context context) throws IOException {
        synchronized (LOCK) { return open(context).recoverInterrupted(System.currentTimeMillis()); }
    }

    private static SessionLedger open(Context context) throws IOException {
        final File base = new File(context.getFilesDir(), "sessions-v1.bin");
        final AtomicFile atomic = new AtomicFile(base);
        return new SessionLedger(new SessionLedger.Store() {
            @Override public byte[] read() throws IOException {
                try (FileInputStream in = atomic.openRead();
                     ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] block = new byte[4096];
                    int count;
                    while ((count = in.read(block)) != -1) {
                        if (out.size() + count > SessionLedger.MAX_BYTES) throw new IOException("会话记录过大");
                        out.write(block, 0, count);
                    }
                    if (out.size() == 0) throw new IOException("会话记录为空");
                    return out.toByteArray();
                } catch (FileNotFoundException missing) {
                    // Never treat an unreadable existing file as a fresh, empty ledger.
                    if (base.exists() || new File(base + ".bak").exists()) throw missing;
                    return new byte[0];
                }
            }

            @Override public void write(byte[] bytes) throws IOException {
                FileOutputStream out = null;
                try {
                    out = atomic.startWrite();
                    out.write(bytes);
                    atomic.finishWrite(out);
                } catch (IOException | RuntimeException failure) {
                    if (out != null) atomic.failWrite(out);
                    throw failure;
                }
            }
        });
    }
}
