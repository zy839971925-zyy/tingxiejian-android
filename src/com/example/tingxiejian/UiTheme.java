package com.example.tingxiejian;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

/**
 * Dark / light / follow-system, without AppCompat.
 *
 * The base context of every Activity is wrapped so that {@code values-night/} resolves
 * automatically. Toggling the preference calls {@code recreate()} on the activity.
 */
final class UiTheme {
    static final String PREFS = "ui";
    static final String KEY = "appearance";
    static final String SYSTEM = "system";
    static final String LIGHT = "light";
    static final String DARK = "dark";

    static String mode(Context context) {
        return prefs(context).getString(KEY, SYSTEM);
    }

    /**
     * Edge-to-edge (targetSdk 35) puts content behind the system bars. MainActivity measures its
     * own insets; the other screens delegate here so their toolbars stop sliding under the status
     * bar — the layout-evidence bug of v0.12.
     */
    static void padForSystemBars(android.app.Activity activity, android.view.View content) {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            activity.getWindow().setDecorFitsSystemWindows(false);
            applyBarAppearance(activity);
            final int left = content.getPaddingLeft(), top = content.getPaddingTop();
            final int right = content.getPaddingRight(), bottom = content.getPaddingBottom();
            content.setOnApplyWindowInsetsListener((view, insets) -> {
                // The controller can be null before decor attaches in onCreate.
                applyBarAppearance(activity);
                android.graphics.Insets safe = insets.getInsets(
                        android.view.WindowInsets.Type.systemBars()
                        | android.view.WindowInsets.Type.displayCutout()
                        | android.view.WindowInsets.Type.ime());
                // Keep design padding, including side cutouts in landscape. Always use the
                // initial values so repeated IME/system-bar dispatch never accumulates padding.
                content.setPadding(left + safe.left, top + safe.top,
                        right + safe.right, bottom + safe.bottom);
                return insets;
            });
            content.requestApplyInsets();
        }
    }

    /** Dark backgrounds need light status icons; light backgrounds need dark status icons. */
    static void applyBarAppearance(android.app.Activity activity) {
        if (android.os.Build.VERSION.SDK_INT < 30) return;
        android.view.WindowInsetsController bars = activity.getWindow().getInsetsController();
        if (bars == null) return;
        int flags = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
        bars.setSystemBarsAppearance(isDark(activity) ? 0 : flags, flags);
    }

    static void set(Context context, String value) {
        prefs(context).edit().putString(KEY, value).apply();
    }

    /** Wrap a base context so the uiMode matches the stored preference. */
    static Context wrap(Context base) {
        String mode = mode(base);
        if (SYSTEM.equals(mode)) {
            return base;
        }
        Configuration config = new Configuration(base.getResources().getConfiguration());
        int night = DARK.equals(mode) ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO;
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | night;
        return base.createConfigurationContext(config);
    }

    static boolean isDark(Context context) {
        int mask = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mask == Configuration.UI_MODE_NIGHT_YES;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
