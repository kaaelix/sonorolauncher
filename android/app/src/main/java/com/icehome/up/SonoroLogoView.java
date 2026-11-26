package com.icehome.up;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Animated geometric "S" logo for Sonoro.
 */
public final class SonoroLogoView extends View {
    private static final int BLUE = 0xFF2D5CE0;
    private static final int BLUE_GLOW = 0xFF5C8BFF;
    private static final float TOTAL_MS = 1480f;

    private final float dp;
    private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint orb = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint aura = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF r = new RectF();
    private final Path sPath = new Path();
    private final Path sPart = new Path();
    private final Path clip = new Path();
    private final PathMeasure sMeasure;
    private final float sLen;
    private final float[] tip = new float[2];

    private float t = TOTAL_MS;
    private ValueAnimator anim;

    public SonoroLogoView(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;

        body.setColor(BLUE);
        body.setStyle(Paint.Style.FILL);

        stroke.setColor(0xFFFFFFFF);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setStrokeWidth(56f);

        ring.setStyle(Paint.Style.STROKE);
        orb.setStyle(Paint.Style.FILL);
        aura.setStyle(Paint.Style.FILL);

        // Exact 512x512 centerline matching Sonoro "S" icon
        sPath.moveTo(328f, 174f);
        sPath.cubicTo(322f, 128f, 186f, 126f, 182f, 190f);
        sPath.cubicTo(178f, 250f, 334f, 258f, 330f, 322f);
        sPath.cubicTo(326f, 386f, 188f, 384f, 184f, 338f);
        sMeasure = new PathMeasure(sPath, false);
        sLen = sMeasure.getLength();
    }

    public void play() {
        play(null);
    }

    public void play(final Runnable onMoveUpReady) {
        if (!ValueAnimator.areAnimatorsEnabled()) {
            t = TOTAL_MS;
            invalidate();
            if (onMoveUpReady != null) onMoveUpReady.run();
            return;
        }
        if (anim != null) anim.cancel();
        t = 0f;
        final boolean[] fired = {false};
        anim = ValueAnimator.ofFloat(0f, TOTAL_MS);
        anim.setDuration((long) TOTAL_MS);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> {
            t = (Float) a.getAnimatedValue();
            invalidate();
            if (!fired[0] && t >= 820f) {
                fired[0] = true;
                if (onMoveUpReady != null) onMoveUpReady.run();
            }
        });
        anim.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (anim != null) anim.cancel();
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        float size = Math.min(w, h) * 0.64f;
        float cx = w / 2f;
        float cy = h / 2f;

        float auraQ = Motion.seg(t, 120f, 1180f);
        if (auraQ > 0f && auraQ < 1f) {
            float rAura = size * (0.65f + 0.45f * Motion.EXPO_OUT.getInterpolation(auraQ));
            int a = Math.round(92 * (1f - auraQ));
            aura.setShader(new RadialGradient(cx, cy, rAura, (a << 24) | (BLUE_GLOW & 0x00FFFFFF), 0x00000000, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, rAura, aura);
        }

        for (int i = 0; i < 2; i++) {
            float q = Motion.seg(t, 220f + i * 130f, 1120f + i * 140f);
            if (q > 0f && q < 1f) {
                float e = Motion.EXPO_OUT.getInterpolation(q);
                ring.setColor(i == 0 ? BLUE_GLOW : BLUE);
                ring.setStrokeWidth((i == 0 ? 2.4f : 4.2f) * dp * (1f - 0.5f * q));
                ring.setAlpha(Math.round((i == 0 ? 175 : 110) * (1f - q) * (1f - q)));
                c.drawCircle(cx, cy, size * (0.42f + e * 0.46f), ring);
            }
        }
        float spk = Motion.seg(t, 230f, 760f);
        if (spk > 0f && spk < 1f) {
            float e = Motion.POWER3_OUT.getInterpolation(spk);
            float rad = size * (0.48f + e * 0.34f);
            float dotR = (3.4f - 2.2f * spk) * dp;
            orb.setColor(0xFFCFE0FF);
            orb.setAlpha(Math.round(210 * (1f - spk)));
            for (int i = 0; i < 4; i++) {
                double ang = Math.toRadians(45.0 + 90.0 * i);
                c.drawCircle(cx + (float) Math.cos(ang) * rad, cy + (float) Math.sin(ang) * rad, dotR, orb);
            }
        }

        float sp = t >= TOTAL_MS ? 1f : Motion.spring(t / 1000f, 0.36f, 0.50f);
        float rub = t >= TOTAL_MS ? 0f : Motion.rubber(t - 210f);
        float tilt = t >= TOTAL_MS ? 0f : -14f * (1f - sp);
        float sx = sp * (1f + 0.14f * rub);
        float sy = sp * (1f - 0.14f * rub);
        float dropY = t >= TOTAL_MS ? 0f : (size * 0.12f * (1f - Motion.EXPO_OUT.getInterpolation(Motion.clamp01(t / 320f))));

        float drawP = t >= TOTAL_MS ? 1f : Motion.EXPO_OUT.getInterpolation(Motion.seg(t, 170f, 840f));

        c.save();
        c.translate(cx, cy - dropY);
        c.rotate(tilt);
        c.scale(sx, sy);
        float s = size / 512f;
        c.scale(s, s);
        c.translate(-256f, -256f);

        r.set(32f, 32f, 480f, 480f);
        c.drawRoundRect(r, 112f, 112f, body);

        if (drawP > 0.001f) {
            float snapPulse = (float) Math.sin(Math.PI * Motion.seg(t, 760f, 1040f));
            stroke.setStrokeWidth(56f + 7f * snapPulse);
            sPart.reset();
            sMeasure.getSegment(0f, sLen * drawP, sPart, true);
            c.drawPath(sPart, stroke);

            if (drawP < 0.995f && sMeasure.getPosTan(sLen * drawP, tip, null)) {
                float tipFade = 1f - Motion.seg(drawP, 0.85f, 1.0f);
                orb.setColor(0xFFFFFFFF);
                orb.setAlpha(Math.round(90 * tipFade));
                c.drawCircle(tip[0], tip[1], 46f, orb);
                orb.setAlpha(Math.round(235 * tipFade));
                c.drawCircle(tip[0], tip[1], 24f, orb);
            }
        }

        float sh = Motion.seg(t, 820f, 1380f);
        if (sh > 0f && sh < 1f) {
            float pos = Motion.lerp(-360f, 880f, Motion.EASE.getInterpolation(sh));
            shine.setShader(new LinearGradient(
                pos - 150f, pos - 150f, pos + 150f, pos + 150f,
                new int[]{0x00FFFFFF, 0x58FFFFFF, 0x00FFFFFF},
                null,
                Shader.TileMode.CLAMP
            ));
            clip.reset();
            clip.addRoundRect(r, 112f, 112f, Path.Direction.CW);
            c.save();
            c.clipPath(clip);
            c.drawRect(r, shine);
            c.restore();
        }
        c.restore();
    }
}
