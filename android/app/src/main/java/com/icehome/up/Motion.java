package com.icehome.up;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;

public final class Motion {

    public static final PathInterpolator EXPO_OUT = new PathInterpolator(0.16f, 1f, 0.3f, 1f);

    public static final PathInterpolator EASE = new PathInterpolator(0.2f, 0f, 0f, 1f);

    public static final PathInterpolator POWER3_OUT = new PathInterpolator(0.215f, 0.61f, 0.355f, 1f);
    public static final PathInterpolator POWER4_OUT = new PathInterpolator(0.165f, 0.84f, 0.44f, 1f);

    public static final PathInterpolator BACK_OUT = new PathInterpolator(0.34f, 1.56f, 0.64f, 1f);

    public static final int ACCENT = 0xFF9AB8F5;

    static final int[] BRAND = {ACCENT, ACCENT, ACCENT};

    static android.graphics.drawable.GradientDrawable brandPill(float radius) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(ACCENT);
        g.setCornerRadius(radius);
        return g;
    }

    public static final String CASCADE = "cascade";

    public static final String PINNED = "pinned";

    static float spring(float t, float response, float damping) {
        if (t <= 0f) return 0f;
        double z = Math.max(0.05, Math.min(0.999, damping));
        double w0 = 2 * Math.PI / Math.max(0.05, response);
        double wd = w0 * Math.sqrt(1 - z * z), zw = z * w0;
        return (float) (1 - Math.exp(-zw * t) * (Math.cos(wd * t) + zw / wd * Math.sin(wd * t)));
    }

    static float seg(float t, float a, float b) {
        return Math.max(0f, Math.min(1f, (t - a) / (b - a)));
    }

    static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    static void reveal(View v, long delay, float dy) {

        if (v instanceof SplitTitle) {
            ((SplitTitle) v).play(delay);
            return;
        }
        if (PINNED.equals(v.getTag())) dy = 0f;
        v.setAlpha(0f);
        v.setTranslationY(dy);
        ObjectAnimator a = ObjectAnimator.ofPropertyValuesHolder(v,
                PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f),
                PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, dy, 0f));
        a.setStartDelay(delay);
        a.setDuration(760);
        a.setInterpolator(POWER4_OUT);
        a.start();
    }

    public static void scrub(android.widget.ScrollView sv, ViewGroup list, float top, float dp) {
        int vh = sv.getHeight();
        if (vh <= 0) return;
        float sy = sv.getScrollY();
        float enter = vh * 0.24f, leave = 120f * dp;
        for (int i = 0; i < list.getChildCount(); i++) {
            View c = list.getChildAt(i);
            if (c.getVisibility() != View.VISIBLE || c.getHeight() == 0 || c instanceof SplitTitle
                    || PINNED.equals(c.getTag())) continue;

            if (c.getHeight() > vh * 0.8f) continue;
            float y = list.getTop() + c.getTop() - sy;
            float bottom = y + c.getHeight();

            float in = clamp01((vh - y) / enter);
            in = 1f - (1f - in) * (1f - in) * (1f - in);

            float out = clamp01((top + leave - bottom) / leave);
            c.setCameraDistance(7000 * dp);
            c.setPivotX(c.getWidth() / 2f);
            c.setPivotY(in < 1f ? 0f : c.getHeight());
            c.setRotationX(18f * (1f - in));
            float s = (0.9f + 0.1f * in) * (1f - 0.06f * out);
            c.setScaleX(s);
            c.setScaleY(s);
            if (out > 0f || c.getTag(R_FADE) != null) {
                c.setTag(R_FADE, out > 0f ? Boolean.TRUE : null);
                c.setAlpha(1f - 0.85f * out);
            }
        }
    }

    static void scramble(final android.widget.TextView tv, final CharSequence target) {
        Object old = tv.getTag(R_SCRAMBLE);
        if (old instanceof android.animation.ValueAnimator) ((android.animation.ValueAnimator) old).cancel();
        if (!android.animation.ValueAnimator.areAnimatorsEnabled() || target.length() == 0) {
            tv.setText(target);
            return;
        }
        final String goal = target.toString();
        final String glyphs = "ABCDEFGHJKLMNPQRSTUVWXYZ0123456789#%&*+=<>";
        final java.util.Random rnd = new java.util.Random();
        final char[] buf = new char[goal.length()];
        android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(Math.min(1100, 300 + goal.length() * 28L));
        a.setInterpolator(POWER3_OUT);
        a.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(android.animation.ValueAnimator va) {
                int n = Math.round((Float) va.getAnimatedValue() * goal.length());
                for (int i = 0; i < buf.length; i++) {
                    char c = goal.charAt(i);
                    buf[i] = i < n || c == ' ' ? c : glyphs.charAt(rnd.nextInt(glyphs.length()));
                }
                tv.setText(new String(buf));
            }
        });
        a.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator an) {
                tv.setText(target);
                tv.setTag(R_SCRAMBLE, null);
            }
        });
        tv.setTag(R_SCRAMBLE, a);
        a.start();
    }

    private static final int R_SCRAMBLE = 0x7f0f0002;

    private static final int R_FADE = 0x7f0f0001;

    public static float clamp01(float v) {
        return v < 0f ? 0f : v > 1f ? 1f : v;
    }

    /** Directional GPU motion blur (horizontal dx / vertical dy in px) on API 31+, cleared below 0.8px. */
    public static void motionBlur(View v, float bx, float by) {
        if (v == null || Build.VERSION.SDK_INT < 31) return;
        try {
            if (bx < 0.8f && by < 0.8f) {
                v.setRenderEffect(null);
            } else {
                v.setRenderEffect(RenderEffect.createBlurEffect(
                    Math.max(0.5f, bx), Math.max(0.5f, by), Shader.TileMode.CLAMP));
            }
        } catch (Throwable ignored) {
        }
    }

    public static long cascade(ViewGroup col, long delay, float dy) {
        long d = delay;
        for (int i = 0; i < col.getChildCount(); i++) {
            View c = col.getChildAt(i);
            if (c.getVisibility() != View.VISIBLE) continue;
            reveal(c, d, dy);
            if (CASCADE.equals(c.getTag()) && c instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) c;
                for (int j = 0; j < g.getChildCount(); j++) reveal(g.getChildAt(j), d + 70 + j * 38L, dy * 0.5f);
                d += Math.min(g.getChildCount(), 8) * 30L;
            }
            d += 55;
            if (d - delay > 700) d = delay + 700;
        }
        return d;
    }

    static float rubber(float ms) {
        if (ms <= 0f) return 0f;
        return (float) (Math.exp(-ms / 200f) * Math.sin(2 * Math.PI * ms / 340f));
    }

    private Motion() {
    }
}
