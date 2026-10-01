package com.example.tingxiejian;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * Real peak envelope of the audio that has actually been decoded, drawn on a full-width baseline.
 * Bars grow from left to right as recognition advances, so the drawing is evidence, not decoration.
 */
public final class WaveView extends View {
    private final Paint bars = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint baseline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint head = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float[] peaks = new float[0];
    private float fraction;
    private float playhead = -1f;
    private boolean playing;

    public WaveView(Context context, AttributeSet attrs) {
        super(context, attrs);
        bars.setColor(context.getColor(R.color.accent));
        bars.setStrokeCap(Paint.Cap.ROUND);
        bars.setStrokeWidth(Motion.dp(context, 2.2f));
        baseline.setColor(context.getColor(R.color.line));
        baseline.setStrokeWidth(Motion.dp(context, 1f));
        head.setColor(context.getColor(R.color.ink));
        head.setStrokeWidth(Motion.dp(context, 1.6f));
    }

    public void setEnvelope(float[] values, float recognizedFraction) {
        if (values != null) peaks = values;
        fraction = Math.max(0f, Math.min(1f, recognizedFraction));
        postInvalidateOnAnimation();
    }

    public void setPlayhead(float value) {
        playhead = value;
        playing = value >= 0f;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float width = getWidth();
        float height = getHeight();
        float middle = height / 2f;
        canvas.drawLine(0, middle, width, middle, baseline);
        if (peaks.length == 0) return;
        float span = Math.max(width * fraction, Motion.dp(getContext(), 4));
        float slot = span / peaks.length;
        float barWidth = Math.max(Motion.dp(getContext(), 1.6f), Math.min(slot * 0.62f, Motion.dp(getContext(), 3.4f)));
        float maxHeight = middle - Motion.dp(getContext(), 2);
        bars.setStrokeWidth(barWidth);
        for (int i = 0; i < peaks.length; i++) {
            float x = slot * (i + 0.5f);
            float amplitude = Math.max(0.06f, Math.min(1f, peaks[i])) * maxHeight;
            canvas.drawLine(x, middle - amplitude, x, middle + amplitude, bars);
        }
        if (playing) {
            float x = Math.max(0f, Math.min(1f, playhead)) * width;
            canvas.drawLine(x, Motion.dp(getContext(), 3), x, height - Motion.dp(getContext(), 3), head);
        }
    }
}
