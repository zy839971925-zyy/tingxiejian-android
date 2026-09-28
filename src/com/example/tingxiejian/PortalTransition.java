package com.example.tingxiejian;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.SharedElementCallback;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Build;
import android.window.OnBackInvokedDispatcher;
import android.transition.ChangeBounds;
import android.transition.ChangeClipBounds;
import android.transition.ChangeTransform;
import android.transition.Fade;
import android.transition.Transition;
import android.transition.TransitionListenerAdapter;
import android.transition.TransitionSet;
import android.view.View;
import java.util.List;
import java.util.Map;
import android.view.animation.PathInterpolator;

/** A native, reversible shared-element container morph with no bitmap overlays. */
final class PortalTransition {
    private static final String EXTRA_NAME = "portal_transition_name";
    private static final String EXTRA_RADIUS = "portal_source_radius_px";
    private static final java.util.WeakHashMap<Activity, Long> recentOpens = new java.util.WeakHashMap<>();
    private static final java.util.Set<Activity> closing = java.util.Collections.newSetFromMap(
            new java.util.WeakHashMap<Activity, Boolean>());
    private static final long OPEN_MS = 340;
    private static final long CLOSE_MS = 250;
    private static final PathInterpolator OPEN_CURVE = new PathInterpolator(.22f, 1f, .36f, 1f);
    private static final PathInterpolator CLOSE_CURVE = new PathInterpolator(.25f, 1f, .5f, 1f);

    private PortalTransition() {}

    static void open(Activity activity, View source, Intent intent, String name) {
        long now = android.os.SystemClock.uptimeMillis();
        synchronized (recentOpens) {
            Long previous = recentOpens.get(activity);
            if (previous != null && now - previous < 480) return;
            recentOpens.put(activity, now);
        }
        if (!Motion.animatorsEnabled() || source.getWidth() == 0 || source.getHeight() == 0) {
            activity.startActivity(intent);
            activity.overridePendingTransition(0, 0);
            return;
        }
        source.animate().cancel();
        source.setScaleX(1f);
        source.setScaleY(1f);
        source.setTransitionName(name);
        intent.putExtra(EXTRA_NAME, name);
        float radius = Motion.dp(activity, 18);
        if (source.getBackground() instanceof GradientDrawable) {
            GradientDrawable shape = (GradientDrawable) source.getBackground();
            radius = shape.getShape() == GradientDrawable.OVAL
                    ? Math.min(source.getWidth(), source.getHeight()) / 2f
                    : shape.getCornerRadius();
        }
        intent.putExtra(EXTRA_RADIUS, radius);
        final float sourceRadius = radius;
        // Explicitly map the *same* source on reentry. The parent can run onStart before the
        // returning child finishes; lists must preserve this View rather than rebuilding it.
        activity.setExitSharedElementCallback(new SharedElementCallback() {
            @Override public void onMapSharedElements(List<String> names, Map<String, View> elements) {
                if (!names.contains(name)) return;
                if (source.isAttachedToWindow() && source.getVisibility() == View.VISIBLE) {
                    elements.put(name, source);
                } else {
                    android.util.Log.w("PortalTransition", "return target detached: " + name);
                }
            }
        });
        activity.getWindow().setSharedElementReenterTransition(morph(source, false,
                CLOSE_MS, sourceRadius));
        activity.startActivity(intent,
                ActivityOptions.makeSceneTransitionAnimation(activity, source, name).toBundle());
    }

    static void install(Activity activity, View surface, View content, Bundle savedState) {
        String name = activity.getIntent().getStringExtra(EXTRA_NAME);
        if (name == null) return;
        // Android 13+ gesture back is not guaranteed to call Activity.onBackPressed(). Route both
        // the gesture and our visible Back button through finishAfterTransition(), not finish().
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                        OnBackInvokedDispatcher.PRIORITY_DEFAULT, () -> close(activity));
            } catch (Throwable error) {
                android.util.Log.w("PortalTransition", "predictive back callback unavailable", error);
            }
        }
        if (!Motion.animatorsEnabled()) return;
        surface.setTransitionName(name);
        float sourceRadius = activity.getIntent().getFloatExtra(EXTRA_RADIUS,
                Motion.dp(activity, 18));
        // An opaque decor background would cover the original button while its surface grows.
        activity.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        activity.getWindow().setSharedElementEnterTransition(morph(surface, true,
                savedState == null ? OPEN_MS : 0, sourceRadius));
        activity.getWindow().setSharedElementReturnTransition(morph(surface, false,
                CLOSE_MS, sourceRadius));
        activity.getWindow().setEnterTransition(new Fade(Fade.IN).addTarget(content)
                .setStartDelay(savedState == null ? 135 : 0).setDuration(savedState == null ? 150 : 0));
        // Keep content legible while the shared surface begins to shrink. An 85ms exit made a
        // correctly-running return transition look like an instantaneous page disappearance.
        activity.getWindow().setReturnTransition(new Fade(Fade.OUT).addTarget(content)
                .setStartDelay(25).setDuration(185).setInterpolator(CLOSE_CURVE));
        activity.getWindow().setAllowReturnTransitionOverlap(true);
    }

    private static Transition morph(View surface, boolean opening, long duration, float sourceRadius) {
        TransitionSet set = new TransitionSet()
                .addTransition(new ChangeBounds())
                .addTransition(new ChangeTransform())
                .addTransition(new ChangeClipBounds());
        set.setOrdering(TransitionSet.ORDERING_TOGETHER);
        set.setDuration(duration);
        set.setInterpolator(opening ? OPEN_CURVE : CLOSE_CURVE);
        set.addListener(new TransitionListenerAdapter() {
            private ValueAnimator radius;
            @Override public void onTransitionStart(Transition transition) {
                if (!(surface.getBackground() instanceof GradientDrawable)) return;
                GradientDrawable background = (GradientDrawable) surface.getBackground().mutate();
                float from = opening ? sourceRadius : 0f;
                float to = opening ? 0f : sourceRadius;
                background.setCornerRadius(from);
                surface.setClipToOutline(true);
                radius = ValueAnimator.ofFloat(from, to);
                radius.setDuration(duration);
                radius.setInterpolator(opening ? OPEN_CURVE : CLOSE_CURVE);
                radius.addUpdateListener(animation -> {
                    background.setCornerRadius((float) animation.getAnimatedValue());
                    surface.invalidateOutline();
                });
                radius.start();
            }
            @Override public void onTransitionEnd(Transition transition) {
                if (radius != null) radius.cancel();
                if (surface.getBackground() instanceof GradientDrawable) {
                    ((GradientDrawable) surface.getBackground()).setCornerRadius(
                            opening ? 0f : sourceRadius);
                    surface.invalidateOutline();
                }
                transition.removeListener(this);
            }
        });
        return set;
    }

    static void close(Activity activity) {
        synchronized (closing) {
            if (!closing.add(activity)) return;
        }
        if (Motion.animatorsEnabled() && activity.getIntent().hasExtra(EXTRA_NAME)) {
            activity.finishAfterTransition();
        } else {
            activity.finish();
            activity.overridePendingTransition(0, 0);
        }
    }
}
