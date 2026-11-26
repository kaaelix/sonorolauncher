package com.icehome.up;

import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;

final class Spring implements TimeInterpolator {

    private final double zw;
    private final double wd;
    private final double b;
    private final double durSec;

    private Spring(double zw, double wd, double b, double durSec) {
        this.zw = zw;
        this.wd = wd;
        this.b = b;
        this.durSec = durSec;
    }

    @Override
    public float getInterpolation(float input) {
        double t = input * durSec;
        double n = Math.exp(-zw * t) * (Math.cos(wd * t) + b * Math.sin(wd * t));
        return (float) (1.0 - n);
    }

    static void apply(ValueAnimator va, float start, float end, float velocity,
                      float response, float damping) {
        double delta = end - start;
        if (Math.abs(delta) < 1e-4) {
            va.setDuration(1);
            return;
        }
        double zeta = Math.max(0.05, Math.min(0.99, damping));
        double w0 = 2 * Math.PI / Math.max(0.05, response);
        double wd = w0 * Math.sqrt(1 - zeta * zeta);
        double zw = zeta * w0;

        double n1 = -velocity / delta;
        double b = (n1 + zw) / wd;
        double amp = Math.sqrt(1 + b * b);
        double settle = Math.log(amp / 0.002) / zw;
        settle = Math.max(0.16, Math.min(0.95, settle));
        va.setInterpolator(new Spring(zw, wd, b, settle));
        va.setDuration((long) (settle * 1000));
    }
}
