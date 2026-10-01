package com.example.tingxiejian;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Pure persistence and transition logic; Android supplies the atomic storage adapter. */
public final class SessionLedger {
    public static final int MAX_BYTES = 1024 * 1024;
    private static final int MAX_SESSIONS = 256;
    private static final int MAGIC = 0x54584a53;
    private static final int VERSION = 1;

    public interface Store {
        byte[] read() throws IOException;
        void write(byte[] bytes) throws IOException;
    }
    private final Store store;
    private LinkedHashMap<String, SessionState> states;

    public SessionLedger(Store store) throws IOException {
        this.store = store;
        this.states = decode(store.read());
    }

    public synchronized String begin(String kind, String name, long now) throws IOException {
        String id = UUID.randomUUID().toString();
        LinkedHashMap<String, SessionState> next = new LinkedHashMap<>(states);
        while (next.size() >= MAX_SESSIONS) {
            boolean pruned = false;
            Iterator<Map.Entry<String, SessionState>> it = next.entrySet().iterator();
            while (it.hasNext()) {
                if (it.next().getValue().isTerminal()) {
                    it.remove();
                    pruned = true;
                    break;
                }
            }
            if (!pruned) throw new IOException("仍有过多活动会话");
        }
        next.put(id, new SessionState(id, bounded(kind, "file", 64), bounded(name, "录音", 512),
                SessionState.State.RUNNING, "", now, now));
        persist(next);
        return id;
    }

    public synchronized SessionState get(String id) { return states.get(id); }

    public synchronized SessionState latest() {
        SessionState latest = null;
        for (SessionState state : states.values()) latest = state;
        return latest;
    }

    public synchronized boolean transition(String id, SessionState.State state, String message, long now)
            throws IOException {
        SessionState prior = states.get(id);
        if (prior == null) return false;
        SessionState updated = prior.transition(state, bounded(message, "", 2048), now);
        if (prior == updated) return false;
        LinkedHashMap<String, SessionState> next = new LinkedHashMap<>(states);
        next.put(id, updated);
        persist(next);
        return true;
    }

    public synchronized int recoverInterrupted(long now) throws IOException {
        int interrupted = 0;
        LinkedHashMap<String, SessionState> next = new LinkedHashMap<>(states);
        for (SessionState prior : states.values()) {
            if (!prior.isTerminal()) {
                next.put(prior.id, prior.transition(SessionState.State.INTERRUPTED,
                        "上次会话被系统中断，请重新开始", now));
                interrupted++;
            }
        }
        if (interrupted > 0) persist(next);
        return interrupted;
    }

    private void persist(LinkedHashMap<String, SessionState> next) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(buffer)) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(next.size());
            for (SessionState session : next.values()) {
                out.writeUTF(session.id);
                out.writeUTF(session.kind);
                out.writeUTF(session.name);
                out.writeUTF(session.state.name());
                out.writeUTF(session.message);
                out.writeLong(session.createdAt);
                out.writeLong(session.updatedAt);
            }
        }
        byte[] bytes = buffer.toByteArray();
        if (bytes.length > MAX_BYTES) throw new IOException("会话记录过大");
        store.write(bytes);
        states = next; // Only advertise a state that storage accepted.
    }

    private static LinkedHashMap<String, SessionState> decode(byte[] bytes) throws IOException {
        LinkedHashMap<String, SessionState> states = new LinkedHashMap<>();
        if (bytes.length == 0) return states;
        if (bytes.length > MAX_BYTES) throw new IOException("会话记录过大");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION) throw new IOException("会话记录版本异常");
            int size = in.readInt();
            if (size < 0 || size > MAX_SESSIONS) throw new IOException("会话数量异常");
            for (int i = 0; i < size; i++) {
                SessionState session = new SessionState(in.readUTF(), in.readUTF(), in.readUTF(),
                        SessionState.State.valueOf(in.readUTF()), in.readUTF(), in.readLong(), in.readLong());
                if (states.put(session.id, session) != null) throw new IOException("会话 ID 重复");
            }
            if (in.available() != 0) throw new IOException("会话记录包含多余数据");
            return states;
        } catch (IllegalArgumentException invalid) {
            throw new IOException("会话状态异常", invalid);
        }
    }

    private static String bounded(String text, String fallback, int maxLength) {
        if (text == null) return fallback;
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
