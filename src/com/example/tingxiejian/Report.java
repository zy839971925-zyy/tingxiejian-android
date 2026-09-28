package com.example.tingxiejian;

import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

/**
 * Turns "it crashes on launch" into evidence.
 *
 * <p>The app cannot read another app's logcat, and on this device the DropBox and dumpsys crash
 * channels are closed to it, so the only way a failure can be seen at all is if the app writes it out
 * itself. Raw reports stay inside app-private files; a crash must not silently expose user
 * filenames, endpoints or device details through shared Downloads.
 *
 * <p>{@link #mark} records a startup milestone in memory; the trail is flushed with any crash, so a
 * failure halfway through {@code onCreate} still shows exactly how far the app got.
 */
final class Report {
    private static final String TAG = "Tingxiejian";
    private static final String BOOT = "tingxiejian-boot.txt";
    private static final String CRASH = "tingxiejian-last-crash.txt";
    private static final String PROBLEM = "tingxiejian-problem.txt";

    private static final long START = SystemClock.uptimeMillis();
    private static final java.util.Set<String> REPORTED = new java.util.HashSet<>();
    private static final StringBuilder TRAIL = new StringBuilder();
    private static Context app;

    private Report() {}

    /** Keeps the application context and routes uncaught failures into a file. */
    static void install(Context context) {
        app = context.getApplicationContext() == null ? context : context.getApplicationContext();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                mark("UNCAUGHT in " + thread.getName());
                write(CRASH, crashText(thread, error));
            } catch (Throwable ignored) {
                // Reporting must never replace the real failure.
            } finally {
                if (previous != null) previous.uncaughtException(thread, error);
            }
        });
    }

    static synchronized void mark(String step) {
        TRAIL.append(String.format(java.util.Locale.ROOT, "%6d ms  %s%n",
                SystemClock.uptimeMillis() - START, step));
    }

    static synchronized String trail() {
        return TRAIL.toString();
    }

    /** Full picture: what the app is, what it was doing, and what went wrong. */
    /** Same content as the crash file, for on-screen fallbacks. */
    public static String crashText(String what, Throwable error) {
        return what + "\n\n" + crashText(Thread.currentThread(), error);
    }

    private static String crashText(Thread thread, Throwable error) {
        StringBuilder text = new StringBuilder();
        text.append("听写间 启动/运行失败报告\n");
        text.append("=====================\n");
        text.append(header());
        text.append("thread      ").append(thread == null ? "?" : thread.getName()).append('\n');
        text.append("\n--- 启动里程碑 ---\n").append(trail());
        text.append("\n--- 异常链 ---\n");
        Throwable current = error;
        int depth = 0;
        while (current != null && depth < 8) {
            text.append(depth == 0 ? "" : "\ncaused by: ");
            text.append(current.getClass().getName());
            if (current.getMessage() != null) text.append(": ").append(current.getMessage());
            text.append('\n');
            for (StackTraceElement frame : current.getStackTrace()) {
                text.append("    at ").append(frame).append('\n');
            }
            current = current.getCause();
            depth++;
        }
        return text.toString();
    }

    private static String header() {
        StringBuilder text = new StringBuilder();
        text.append("version     ").append(version()).append('\n');
        text.append("android     ").append(Build.VERSION.RELEASE)
                .append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n");
        text.append("device      ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        text.append("build       ").append(Build.DISPLAY).append('\n');
        text.append("time        ").append(new java.util.Date().toString()).append('\n');
        return text.toString();
    }

    private static String version() {
        if (app == null) return "?";
        try {
            return app.getPackageManager().getPackageInfo(app.getPackageName(), 0).versionName;
        } catch (Throwable ignored) {
            return "?";
        }
    }

    /** A failure that did not kill the process, but that the user or I need to know about. */
    static synchronized void problem(String where, Throwable error) {
        mark("problem: " + where);
        if (app == null) return;
        // A failing render loop must not turn into a write loop; the first report is the useful one.
        if (!REPORTED.add(where)) return;
        String text = header() + "\n--- 非致命问题 ---\n" + where + "\n\n"
                + Log.getStackTraceString(error) + "\n--- 启动里程碑 ---\n" + trail();
        write(PROBLEM, text);
    }

    /** Writes the current trail without any exception: proves how far a *successful* start got. */
    static synchronized void flush(Context context) {
        Context target = context == null ? app : context;
        if (target == null) return;
        String text = header() + "\n--- 启动里程碑 ---\n" + trail();
        write(BOOT, text);
    }

    private static void write(String name, String body) {
        if (app == null) return;
        Diagnostics.write(app, name, "text/plain", body);
    }
}
