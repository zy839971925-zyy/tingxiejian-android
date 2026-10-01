package com.example.tingxiejian;

/** Immutable, Android-independent state of a file or dictation session. */
public final class SessionState {
    public enum State { RUNNING, FINALIZING, POLISHING, DONE, CANCELLED, INTERRUPTED, FAILED }

    public final String id;
    public final String kind;
    public final String name;
    public final State state;
    public final String message;
    public final long createdAt;
    public final long updatedAt;

    public SessionState(String id, String kind, String name, State state, String message,
                        long createdAt, long updatedAt) {
        if (id == null || id.isEmpty() || state == null) throw new IllegalArgumentException("session identity/state");
        this.id = id;
        this.kind = kind == null ? "file" : kind;
        this.name = name == null ? "录音" : name;
        this.state = state;
        this.message = message == null ? "" : message;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public boolean isTerminal() {
        return state == State.DONE || state == State.CANCELLED
                || state == State.INTERRUPTED || state == State.FAILED;
    }

    public SessionState transition(State target, String nextMessage, long now) {
        if (target == null) throw new IllegalArgumentException("target state");
        if (isTerminal()) return this;
        if (target.ordinal() < state.ordinal()) return this;
        String message = nextMessage == null ? "" : nextMessage;
        if (target == state && this.message.equals(message)) return this;
        return new SessionState(id, kind, name, target, message, createdAt, Math.max(updatedAt, now));
    }
}
