package com.example.tingxiejian;

/**
 * Analytic solution of a damped harmonic oscillator, used to time every motion in the app.
 *
 * <p>x(t) = 1 - e^(-z*w*t) * ( cos(wd*t) + (z*w/wd) * sin(wd*t) ), with wd = w * sqrt(1 - z^2).
 * The curve is normalised by its own value at {@code duration} so it lands exactly on the target,
 * which is what makes it safe to use as an Android interpolator.
 *
 * <p>Deliberately free of Android imports: the maths is unit-tested on a desktop JDK, and the same
 * curve is what generated the CSS spring samples in the previous web build.
 */
public final class SpringCurve {
    private final float zeta;
    private final float omega;
    private final float duration;

    public SpringCurve(float zeta, float omega, float duration) {
        this.zeta = Math.max(0.2f, zeta);
        this.omega = Math.max(1f, omega);
        this.duration = Math.max(0.05f, duration);
    }

    private static float raw(float zeta, float omega, float t) {
        if (zeta >= 1f) {
            // Critically damped (and the zeta > 1 case is close enough to it for timing purposes).
            return (float) (1 - Math.exp(-omega * t) * (1 + omega * t));
        }
        double wd = omega * Math.sqrt(1 - zeta * zeta);
        return (float) (1 - Math.exp(-zeta * omega * t)
                * (Math.cos(wd * t) + (zeta * omega / wd) * Math.sin(wd * t)));
    }

    /** Normalised position at {@code t} seconds: 0 at the start, exactly 1 at {@link #duration()}. */
    public float at(float t) {
        if (t <= 0f) return 0f;
        if (t >= duration) return 1f;
        return raw(zeta, omega, t) / raw(zeta, omega, duration);
    }

    public float duration() {
        return duration;
    }

    /** Peak overshoot as a fraction of the distance travelled (0 for a critically damped curve). */
    public float overshoot() {
        float peak = 1f;
        for (int i = 0; i <= 400; i++) {
            peak = Math.max(peak, at(duration * i / 400f));
        }
        return peak - 1f;
    }

    public String describe() {
        return String.format(java.util.Locale.ROOT, "zeta=%.2f omega=%.0f dur=%.0fms peak=%.3f",
                zeta, omega, duration * 1000f, 1f + overshoot());
    }
}
