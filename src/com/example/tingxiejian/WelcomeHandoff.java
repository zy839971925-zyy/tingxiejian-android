package com.example.tingxiejian;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/** One-shot native handoff: the guide's green full-screen reveal becomes a home cover. */
final class WelcomeHandoff {
    private WelcomeHandoff() {}

    static void play(Activity activity, FrameLayout root, Bundle savedState) {
        if (savedState != null || activity.getIntent() == null
                || !activity.getIntent().getBooleanExtra("welcome_portal", false)
                || !Motion.animatorsEnabled()) return;
        View[] parts = {activity.findViewById(R.id.appbar),
                activity.findViewById(R.id.main), activity.findViewById(R.id.bottombar)};
        for (View part : parts) {
            if (part == null) return;
        }
        for (View part : parts) {
            part.setAlpha(0f);
            part.setTranslationY(Motion.dp(activity, 12));
            part.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }
        View cover = new View(activity);
        cover.setBackgroundColor(activity.getColor(R.color.primary_fill));
        cover.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        root.addView(cover, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.post(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) {
                clean(root, cover, parts);
                return;
            }
            cover.animate().alpha(0f).setStartDelay(40).setDuration(235)
                    .withEndAction(() -> {
                        if (cover.getParent() == root) root.removeView(cover);
                    }).start();
            for (int i = 0; i < parts.length; i++) {
                View part = parts[i];
                part.animate().alpha(1f).translationY(0f)
                        .setStartDelay(80L + 65L * i).setDuration(360)
                        .setInterpolator(Motion.ENTER)
                        .withEndAction(() -> part.setImportantForAccessibility(
                                View.IMPORTANT_FOR_ACCESSIBILITY_AUTO)).start();
            }
        });
        // If a lifecycle interruption cancels an animator, never strand an opaque cover or
        // untouchable home controls. The fallback is idempotent after a normal completion.
        root.postDelayed(() -> clean(root, cover, parts), 900);
    }

    private static void clean(FrameLayout root, View cover, View[] parts) {
        if (cover.getParent() == root) root.removeView(cover);
        for (View part : parts) {
            part.animate().cancel();
            part.setAlpha(1f);
            part.setTranslationY(0f);
            part.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        }
    }
}
