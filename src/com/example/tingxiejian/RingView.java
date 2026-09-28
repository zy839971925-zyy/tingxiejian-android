package com.example.tingxiejian;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.View;

/**
 * One progress arc; the sole percentage label lives in activity_main.xml's ring_label.
 * The critically damped spring is an exact analytic solution, including velocity continuity when
 * a new progress value arrives mid-flight. No permanent hardware layer or layout animation.
 */
public final class RingView extends View implements Choreographer.FrameCallback {
    private static final float OMEGA = 13.0f;
    private static final float EPSILON = .06f;
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private float target, shown, velocity, startOffset, startVelocity;
    private boolean chasing;
    private long startFrameNanos;

    public RingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        float stroke = Motion.dp(context, 4.5f);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(stroke);
        track.setColor(context.getColor(R.color.line));
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(stroke);
        arc.setStrokeCap(Paint.Cap.ROUND);
        arc.setColor(context.getColor(R.color.accent));
        setContentDescription("转写进度");
    }

    public void setProgress(float value) {
        float next = Math.max(0f, Math.min(100f, value));
        if (next == target && chasing) return;
        target = next;
        if (!Motion.animatorsEnabled()) {
            stopChasing();
            shown = target;
            velocity = 0;
            invalidate();
            return;
        }
        startOffset = shown - target;
        startVelocity = velocity;
        startFrameNanos = 0;
        if (!chasing) {
            chasing = true;
            Choreographer.getInstance().postFrameCallback(this);
        }
    }

    public float progress() { return shown; }

    private void stopChasing() {
        if (chasing) {
            chasing = false;
            Choreographer.getInstance().removeFrameCallback(this);
        }
    }

    @Override public void doFrame(long frameTimeNanos) {
        if (!chasing) return;
        if (startFrameNanos == 0) startFrameNanos = frameTimeNanos;
        float t = Math.max(0f, (frameTimeNanos - startFrameNanos) / 1e9f);
        float coefficient = startVelocity + OMEGA * startOffset;
        double decay = Math.exp(-OMEGA * t);
        shown = target + (float) ((startOffset + coefficient * t) * decay);
        velocity = (float) ((coefficient - OMEGA * (startOffset + coefficient * t)) * decay);
        if (Math.abs(shown - target) < EPSILON && Math.abs(velocity) < EPSILON) {
            shown = target;
            velocity = 0f;
            chasing = false;
        } else {
            Choreographer.getInstance().postFrameCallback(this);
        }
        invalidate();
    }

    @Override protected void onDetachedFromWindow() {
        stopChasing();
        super.onDetachedFromWindow();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desired = (int) Motion.dp(getContext(), 196);
        int size = Math.min(resolveSize(desired, widthMeasureSpec),
                resolveSize(desired, heightMeasureSpec));
        setMeasuredDimension(size, size);
    }

    @Override protected void onDraw(Canvas canvas) {
        float inset = arc.getStrokeWidth() / 2f;
        box.set(inset, inset, getWidth() - inset, getHeight() - inset);
        canvas.drawArc(box, 0, 360, false, track);
        if (shown > .1f) canvas.drawArc(box, -90, 360f * shown / 100f, false, arc);
    }
}
