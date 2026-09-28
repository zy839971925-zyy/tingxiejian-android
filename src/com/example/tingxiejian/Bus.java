package com.example.tingxiejian;

import org.json.JSONObject;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-process event bus between the transcription service and whatever UI is attached. Replaces the
 * loopback HTTP endpoint the web build needed: no port, no token, no page to reload.
 *
 * <p>{@link #post} may be called from the worker thread; listeners are responsible for hopping to the
 * main thread. The last state is cached so a re-attached Activity renders immediately instead of
 * showing an empty screen.
 */
final class Bus {
    interface Listener {
        void onEvent(JSONObject event);
    }

    private static final CopyOnWriteArrayList<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile JSONObject snapshot;

    private Bus() {}

    static void register(Listener listener) {
        LISTENERS.add(listener);
        JSONObject current = snapshot;
        if (current != null) listener.onEvent(current);
    }

    static void unregister(Listener listener) {
        LISTENERS.remove(listener);
    }

    static void post(JSONObject event) {
        String type = event.optString("type");
        if ("progress".equals(type) || "result".equals(type) || "phase".equals(type)) {
            snapshot = event;
        }
        for (Listener listener : LISTENERS) {
            listener.onEvent(event);
        }
    }

    /** Called when a fresh run starts, so a stale result never flashes before the new one. */
    /** True while a UI is attached; the service uses this to release itself when nobody is watching. */
    static boolean isAttached() {
        return !LISTENERS.isEmpty();
    }

    static void reset() {
        snapshot = null;
    }
}
