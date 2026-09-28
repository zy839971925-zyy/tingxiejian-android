package com.example.tingxiejian;

import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

/** Motion + touch feedback. Every curve comes from {@link SpringCurve}, never a hand-picked bezier. */
final class Motion {
    static final long PRESS_MS = 240;
    static final long GENTLE_MS = 500;
    static final long BOUNCE_MS = 420;
    static final long ENTER_MS = 460;

    static final TimeInterpolator FAST_OUT = new android.view.animation.AccelerateInterpolator(1.6f);
    static final TimeInterpolator PRESS = curve(new SpringCurve(1f, 22f, PRESS_MS / 1000f));
    static final TimeInterpolator GENTLE = curve(new SpringCurve(0.80f, 13f, GENTLE_MS / 1000f));
    static final TimeInterpolator BOUNCE = curve(new SpringCurve(0.58f, 13f, BOUNCE_MS / 1000f));
    static final TimeInterpolator ENTER = curve(new SpringCurve(0.92f, 14f, ENTER_MS / 1000f));

    private Motion() {}

    private static TimeInterpolator curve(final SpringCurve c) {
        return t -> c.at(t * c.duration());
    }

    static float dp(Context context, float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics());
    }

    static boolean animatorsEnabled() {
        return Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled();
    }

    /** Scale-on-press driven by the critically damped curve. Returns false so clicks still fire. */
    static void press(final View view) {
        view.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().cancel();
                    v.setScaleX(0.982f);  // 0ms: touch must feel received immediately
                    v.setScaleY(0.982f);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    scale(v, 1f, 130, PRESS);
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    private static void scale(View view, float value, long duration, TimeInterpolator interpolator) {
        if (!animatorsEnabled()) {
            view.setScaleX(value);
            view.setScaleY(value);
            return;
        }
        view.animate().scaleX(value).scaleY(value).setDuration(duration).setInterpolator(interpolator).start();
    }

    /** One-shot icon rotation on the rare model-preparation state; no idle animation loop. */
    static void turnOnce(View icon) {
        if (!animatorsEnabled()) return;
        icon.animate().cancel();
        icon.setRotation(-45f);
        icon.animate().rotation(0f).setDuration(260).setInterpolator(ENTER).start();
    }

    /** One entrance per container: rise a little, fade in, settle. */
    static void enter(View view) {
        enter(view, 0);
    }

    static void enter(View view, long delayMs) {
        if (!animatorsEnabled()) {
            view.setAlpha(1f);
            view.setTranslationY(0f);
            return;
        }
        view.setAlpha(0f);
        view.setTranslationY(dp(view.getContext(), 10));
        view.animate().alpha(1f).translationY(0f).setStartDelay(delayMs)
                .setDuration(ENTER_MS).setInterpolator(ENTER).start();
    }

    /** A small, one-shot entrance for a newly delivered message; never replay old messages. */
    static void messageIn(View bubble) {
        if (!animatorsEnabled()) return;
        bubble.animate().cancel();
        bubble.setAlpha(0f);
        bubble.setTranslationY(dp(bubble.getContext(), 6));
        bubble.animate().alpha(1f).translationY(0f).setDuration(195)
                .setInterpolator(ENTER).start();
    }

    /** Immediate icon swap with a brief confirmation, not a delayed action. */
    static void iconSwap(android.widget.ImageView icon, int drawableId) {
        icon.animate().cancel();
        icon.setImageResource(drawableId);
        if (!animatorsEnabled()) { icon.setAlpha(1f); return; }
        icon.setAlpha(0.48f);
        icon.animate().alpha(1f).setDuration(140).setInterpolator(ENTER).start();
    }

    /** Fast feedback for an explicitly selected settings choice. */
    static void selected(View chip) {
        if (!animatorsEnabled()) return;
        chip.animate().cancel();
        chip.setScaleX(0.96f);
        chip.setScaleY(0.96f);
        chip.animate().scaleX(1f).scaleY(1f).setDuration(165)
                .setInterpolator(PRESS).start();
    }

    /** A short confirmation pop, used when a control changes state. */
    static void pop(View view) {
        if (!animatorsEnabled()) return;
        view.animate().cancel();
        view.setScaleX(0.9f);
        view.setScaleY(0.9f);
        view.animate().scaleX(1f).scaleY(1f).setDuration(BOUNCE_MS).setInterpolator(BOUNCE).start();
    }

    /** Exits are faster than entrances (the motion spec): fade out, then call back. */
    static void exit(View view, Runnable after) {
        if (!animatorsEnabled()) {
            view.setAlpha(1f);
            after.run();
            return;
        }
        view.animate().cancel();
        view.animate().alpha(0f).translationY(dp(view.getContext(), -6))
                .setDuration(110).setInterpolator(FAST_OUT)
                .withEndAction(() -> {
                    view.setAlpha(1f);
                    view.setTranslationY(0f);
                    after.run();
                }).start();
    }

    /** Rows arrive one after another - once per container, never on every rebind. */
    static void stagger(android.view.ViewGroup group, int stepMs) {
        if (!animatorsEnabled()) return;
        for (int i = 0; i < group.getChildCount(); i++) {
            enter(group.getChildAt(i), (long) Math.min(i, 6) * stepMs);
        }
    }

    /** One decaying shake, for "this needs your attention" — never a loop. */
    static void shake(final View view) {
        if (!animatorsEnabled()) return;
        final float distance = dp(view.getContext(), 7);
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(320);
        animator.addUpdateListener(animation -> {
            float t = (float) animation.getAnimatedValue();
            view.setTranslationX((float) (Math.sin(t * Math.PI * 3) * distance * (1 - t)));
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                view.setTranslationX(0f);
            }
        });
        animator.start();
    }

    static void haptic(View view, int milliseconds) {
        if (!animatorsEnabled()) return;
        vibrate(view.getContext(), milliseconds);
    }

    private static void vibrate(Context context, int milliseconds) {
        try {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator == null || !vibrator.hasVibrator()) return;
            vibrator.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE));
        } catch (Throwable ignored) {
            // Haptics are decoration; never let them break an action.
        }
    }

    static void hapticPattern(View view, long[] pattern) {
        if (!animatorsEnabled()) return;
        try {
            Vibrator vibrator = (Vibrator) view.getContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator == null || !vibrator.hasVibrator()) return;
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
        } catch (Throwable ignored) {
        }
    }
}
