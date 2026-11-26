package com.icehome.up;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

public final class Icons extends Drawable {

    public static final int HOME = 0, GRID = 1, PHONE = 2, INFO = 3, SPARKLE = 4, LAYERS = 5, FOLDER = 6, DROP = 7,
            TOGGLES = 8, CURTAIN = 9, SLIDERS = 10, LIST = 11, RESTART = 12, CHEVRON = 13, BACK = 14, WIDGETS = 15,
            LIGHT = 16, PALETTE = 17, CODE = 18, SHIELD = 19, DOWNLOAD = 20, GLOBE = 21, PLAY = 22, TERMINAL = 23,
            DATABASE = 24;

    private static final float GESTURE_MS = 1400f;

    private final Matrix gm = new Matrix();

    private static final float REST = 1e6f;

    private final int kind;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path(), part = new Path();
    private final Matrix pm = new Matrix();
    private final RectF r = new RectF();

    private long playStart = -1;
    private final Runnable nextFrame = new Runnable() {
        @Override
        public void run() {
            invalidateSelf();
        }
    };

    public Icons(int kind, int color) {
        this.kind = kind;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.8f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(color);
        dot.setStyle(Paint.Style.FILL);
    }

    void setColor(int c) {
        paint.setColor(c);
        invalidateSelf();
    }

    public void play() {
        if (!android.animation.ValueAnimator.areAnimatorsEnabled()) return;
        playStart = android.view.animation.AnimationUtils.currentAnimationTimeMillis();
        invalidateSelf();
    }

    private float gestureTime() {
        if (playStart < 0) return -1f;
        float scale = android.animation.ValueAnimator.getDurationScale();
        float t = (android.view.animation.AnimationUtils.currentAnimationTimeMillis() - playStart) / (scale > 0f ? scale : 1f);
        if (t >= GESTURE_MS) {
            playStart = -1;
            return -1f;
        }
        return t;
    }

    private static float sp(float t, float delay, float response, float damping) {
        return t <= delay ? 0f : Motion.spring((t - delay) / 1000f, response, damping);
    }

    private static float hump(float t, float a, float b) {
        return (float) Math.sin(Math.PI * Motion.seg(t, a, b));
    }

    private static float bump(float t, float delay) {
        if (t <= delay) return 0f;
        float u = t - delay;
        return (float) (Math.exp(-u / 170f) * Math.sin(2 * Math.PI * u / 380f));
    }

    private static float jelly(float t, float at) {
        return t <= at ? 0f : Motion.rubber(t - at);
    }

    private static float wave(float t, float delay, float ms) {
        float u = t - delay;
        if (u <= 0f) return 0f;
        if (u < ms) return (float) Math.sin(Math.PI * Motion.EASE.getInterpolation(u / ms));
        return -0.25f * Motion.rubber(u - ms);
    }

    private void gesture(Matrix g, float t) {
        switch (kind) {
            case HOME: {

                float crouch = hump(t, 0, 150);
                g.preScale(1f + 0.12f * crouch, 1f - 0.16f * crouch, 12, 20);
                float j = Motion.seg(t, 110, 430);
                g.preTranslate(0, -6.5f * 4f * j * (1f - j));
                float air = hump(t, 110, 430);
                g.preScale(1f - 0.06f * air, 1f + 0.1f * air, 12, 20);
                float s = jelly(t, 430);
                g.preScale(1f + 0.22f * s, 1f - 0.24f * s, 12, 20);
                break;
            }
            case GRID:

                g.preRotate(90f * sp(t, 60, 0.45f, 0.55f), 12, 12);
                break;
            case PHONE: {

                float sq = hump(t, 0, 240);
                g.preScale(1f - 0.16f * sq, 1f - 0.16f * sq, 12, 12);
                float u = t - 170f;
                if (u > 0f) g.preRotate((float) (12 * Math.exp(-u / 200f) * Math.sin(2 * Math.PI * u / 110f)), 12, 12);
                break;
            }
            case INFO: {

                float f = Motion.EASE.getInterpolation(Motion.clamp01(t / 520f));
                float sx = (float) Math.cos(2 * Math.PI * f);
                g.preScale(Math.abs(sx) < 0.08f ? 0.08f : sx, 1f, 12, 12);
                float r = Motion.rubber(t);
                g.preScale(1f + 0.12f * r, 1f + 0.12f * r, 12, 12);
                break;
            }
            case SPARKLE: {

                float pre = hump(t, 0, 160);
                float k = 1f - 0.22f * pre + 0.5f * bump(t, 120);
                g.preRotate(180f * sp(t, 90, 0.55f, 0.6f), 12, 12);
                g.preScale(k, k, 12, 12);
                break;
            }
            case LAYERS: {

                float k = (float) (Math.exp(-t / 240f) * Math.sin(2 * Math.PI * t / 320f));
                g.preTranslate(12, 14);
                g.preSkew(0.3f * k, 0f);
                g.preTranslate(-12, -14);
                break;
            }
            case FOLDER: {

                float crouch = hump(t, 0, 140);
                g.preScale(1f + 0.14f * crouch, 1f - 0.16f * crouch, 12, 19.5f);
                float j = Motion.seg(t, 100, 360);
                g.preTranslate(0, -4.5f * 4f * j * (1f - j));
                float air = hump(t, 100, 360);
                g.preScale(1f - 0.06f * air, 1f + 0.08f * air, 12, 19.5f);
                float s = jelly(t, 360);
                g.preScale(1f + 0.26f * s, 1f - 0.26f * s, 12, 19.5f);
                break;
            }
            case DROP: {

                float y;
                if (t < 130f) y = -9f * Motion.POWER3_OUT.getInterpolation(t / 130f);
                else {
                    float f = Motion.seg(t, 130, 330);
                    y = -9f * (1f - f * f);
                }
                g.preTranslate(0, y);
                float fs = hump(t, 150, 340);
                g.preScale(1f - 0.1f * fs, 1f + 0.16f * fs, 12, 20.5f);
                float s = jelly(t, 330);
                g.preScale(1f + 0.3f * s, 1f - 0.3f * s, 12, 20.5f);
                break;
            }
            case LIST: {

                float k = t < 100f ? 1f - 0.25f * Motion.EASE.getInterpolation(t / 100f)
                        : 0.75f + 0.25f * sp(t, 100, 0.42f, 0.45f);
                g.preScale(k, k, 4, 20.5f);
                break;
            }
            case LIGHT:

                g.preRotate(45f * sp(t, 0, 0.5f, 0.6f), 12, 12);
                break;
            case RESTART: {

                float a = t < 120f ? -30f * Motion.EASE.getInterpolation(t / 120f)
                        : -30f + 390f * Motion.BACK_OUT.getInterpolation(Motion.seg(t, 120, 820));
                g.preRotate(a, 12, 12);
                break;
            }
            case CURTAIN:
            case SLIDERS:
            case TOGGLES:
            case DOWNLOAD:
            case WIDGETS:
            case PALETTE:
            case CODE:
            case GLOBE:
            case PLAY:
            case TERMINAL:
            case DATABASE:
                break;
            default: {
                float k = 1f + 0.3f * bump(t, 0);
                g.preScale(k, k, 12, 12);
                break;
            }
        }
    }

    private void effects(Canvas c, float t) {
        switch (kind) {
            case SPARKLE:

                burst(c, t, 140, 560, 12, 12, 4, 45f, 7f, 12.5f, 1.4f, 0f);
                break;
            case DROP:

                splash(c, t, 330, 640, 12, 20.5f, 3f, 5.5f, 4.5f, 1.3f);
                break;
            case HOME:

                splash(c, t, 430, 700, 12, 20.5f, 5f, 4f, 1.5f, 1.1f);
                break;
            default:
                break;
        }
    }

    private void burst(Canvas c, float t, float a, float b, float cx, float cy, int n, float angle0,
                       float r0, float r1, float size, float lift) {
        if (t <= a || t >= b) return;
        float f = Motion.POWER3_OUT.getInterpolation(Motion.seg(t, a, b));
        float rad = r0 + (r1 - r0) * f;
        dot.setColor(paint.getColor());
        dot.setAlpha(Math.round(paint.getAlpha() * (1f - Motion.seg(t, a, b))));
        for (int i = 0; i < n; i++) {
            double ang = Math.toRadians(angle0 + 360f / n * i);
            c.drawCircle(cx + (float) Math.cos(ang) * rad, cy + (float) Math.sin(ang) * rad - lift * f,
                    size * (1f - 0.7f * f), dot);
        }
    }

    private void splash(Canvas c, float t, float a, float b, float cx, float cy, float gap, float out,
                        float up, float size) {
        if (t <= a || t >= b) return;
        float f = Motion.seg(t, a, b);
        float x = gap + out * Motion.POWER3_OUT.getInterpolation(f);
        float y = -up * 4f * f * (1f - f);
        dot.setColor(paint.getColor());
        dot.setAlpha(Math.round(paint.getAlpha() * (1f - f * f)));
        float s = size * (1f - 0.6f * f);
        c.drawCircle(cx - x, cy + y, s, dot);
        c.drawCircle(cx + x, cy + y, s, dot);
    }

    @Override
    public void draw(Canvas c) {
        android.graphics.Rect b = getBounds();
        if (b.isEmpty()) return;
        float t = gestureTime();

        float T = t >= 0f ? t : REST;
        c.save();
        c.translate(b.left, b.top);
        float s = Math.min(b.width(), b.height()) / 24f;
        c.scale(s, s);

        gm.reset();
        if (t >= 0f) gesture(gm, t);
        path.reset();
        switch (kind) {
            case HOME:
                path.moveTo(4, 10.5f);
                path.lineTo(12, 4);
                path.lineTo(20, 10.5f);
                path.lineTo(20, 19);
                path.quadTo(20, 20, 19, 20);
                path.lineTo(15, 20);
                path.lineTo(15, 14.5f);
                path.lineTo(9, 14.5f);
                path.lineTo(9, 20);
                path.lineTo(5, 20);
                path.quadTo(4, 20, 4, 19);
                path.close();
                break;
            case GRID: {

                float[][] q = {{4, 4, 10.5f, 10.5f}, {13.5f, 4, 20, 10.5f}, {13.5f, 13.5f, 20, 20}, {4, 13.5f, 10.5f, 20}};
                for (int i = 0; i < 4; i++) {
                    float k = 1f + 0.45f * bump(T, 70f * i);
                    rr(part, q[i][0], q[i][1], q[i][2], q[i][3], 2.2f);
                    addPart(0, 0, 0, k, (q[i][0] + q[i][2]) / 2f, (q[i][1] + q[i][3]) / 2f);
                }
                break;
            }
            case PHONE:
                rr(path, 6.5f, 3, 17.5f, 21, 3);
                path.moveTo(10.5f, 18);
                path.lineTo(13.5f, 18);
                break;
            case INFO: {
                path.addCircle(12, 12, 8.5f, Path.Direction.CW);
                path.moveTo(12, 11);
                path.lineTo(12, 16.5f);

                path.addCircle(12, 7.8f - 3.5f * hump(T, 420, 660), 0.4f, Path.Direction.CW);
                break;
            }
            case SPARKLE:
                path.moveTo(12, 3.5f);
                path.quadTo(13, 11, 20.5f, 12);
                path.quadTo(13, 13, 12, 20.5f);
                path.quadTo(11, 13, 3.5f, 12);
                path.quadTo(11, 11, 12, 3.5f);
                path.close();
                break;
            case LAYERS: {
                rr(path, 5, 8, 19, 20, 2.5f);

                float l1 = -2.2f * hump(T, 40, 330), l2 = -3.2f * hump(T, 100, 400);
                path.moveTo(7, 5.5f + l1);
                path.lineTo(17, 5.5f + l1);
                path.moveTo(9, 3 + l2);
                path.lineTo(15, 3 + l2);
                break;
            }
            case FOLDER:
                path.moveTo(3.5f, 7);
                path.quadTo(3.5f, 5, 5.5f, 5);
                path.lineTo(9.5f, 5);
                path.lineTo(11.5f, 7.5f);
                path.lineTo(18.5f, 7.5f);
                path.quadTo(20.5f, 7.5f, 20.5f, 9.5f);
                path.lineTo(20.5f, 17.5f);
                path.quadTo(20.5f, 19.5f, 18.5f, 19.5f);
                path.lineTo(5.5f, 19.5f);
                path.quadTo(3.5f, 19.5f, 3.5f, 17.5f);
                path.close();
                break;
            case DROP:
                path.moveTo(12, 3.5f);
                path.cubicTo(12, 3.5f, 5.5f, 10.5f, 5.5f, 14.5f);
                path.cubicTo(5.5f, 18.2f, 8.4f, 20.5f, 12, 20.5f);
                path.cubicTo(15.6f, 20.5f, 18.5f, 18.2f, 18.5f, 14.5f);
                path.cubicTo(18.5f, 10.5f, 12, 3.5f, 12, 3.5f);
                path.close();
                path.moveTo(9, 15);
                path.quadTo(9.2f, 17.3f, 11.5f, 17.6f);
                break;
            case TOGGLES: {

                rr(path, 3.5f, 5, 20.5f, 11, 3);
                path.addCircle(17.5f - 11f * wave(T, 0, 560), 8, 1.6f, Path.Direction.CW);
                rr(path, 3.5f, 13, 20.5f, 19, 3);
                path.addCircle(6.5f + 11f * wave(T, 90, 560), 16, 1.6f, Path.Direction.CW);
                break;
            }
            case CURTAIN: {

                rr(path, 4, 4, 20, 20, 3);
                float a = 3.2f * wave(T, 0, 520), bb = 3.2f * wave(T, 60, 520);
                path.moveTo(9 + a, 4);
                path.quadTo(8 + a, 13, 11 + a * 0.4f, 20);
                path.moveTo(15 - bb, 4);
                path.quadTo(16 - bb, 13, 13 - bb * 0.4f, 20);
                break;
            }
            case SLIDERS:

                path.moveTo(4, 7);
                path.lineTo(20, 7);
                path.moveTo(4, 17);
                path.lineTo(20, 17);
                path.addCircle(9 + 6f * wave(T, 0, 600), 7, 2.2f, Path.Direction.CW);
                path.addCircle(15 - 6f * wave(T, 90, 600), 17, 2.2f, Path.Direction.CW);
                break;
            case LIST: {
                rr(path, 4, 3.5f, 20, 20.5f, 2.5f);

                float[] ys = {9, 12.5f, 16}, ends = {16, 16, 12.5f};
                for (int i = 0; i < 3; i++) {
                    float k = sp(T, 140 + 70 * i, 0.32f, 0.75f);
                    if (k <= 0.01f) continue;
                    path.moveTo(8, ys[i]);
                    path.lineTo(8 + (ends[i] - 8) * k, ys[i]);
                }
                break;
            }
            case RESTART:
                r.set(4.5f, 4.5f, 19.5f, 19.5f);
                path.addArc(r, -60, 300);
                path.moveTo(15.8f, 3.6f);
                path.lineTo(16.3f, 6.6f);
                path.lineTo(13.3f, 7.2f);
                break;
            case CHEVRON:
                path.moveTo(9.5f, 6);
                path.lineTo(15.5f, 12);
                path.lineTo(9.5f, 18);
                break;
            case BACK:
                path.moveTo(14.5f, 5.5f);
                path.lineTo(8, 12);
                path.lineTo(14.5f, 18.5f);
                break;
            case WIDGETS: {

                float h = wave(T, 0, 440);
                rr(part, 4, 4, 20, 11, 2.5f);
                addPart(7f * h, -1f * h, 12f * h, 1f, 12, 7.5f);
                rr(path, 4, 13.5f - 2.6f * hump(T, 220, 440), 11, 20 - 2.6f * hump(T, 220, 440), 2.5f);
                rr(path, 13.5f, 13.5f - 2.6f * hump(T, 310, 530), 20, 20 - 2.6f * hump(T, 310, 530), 2.5f);
                break;
            }
            case LIGHT: {

                path.addCircle(12, 12, 3.5f * (1f + 0.35f * bump(T, 0)), Path.Direction.CW);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i;
                    float g = hump(T, 40 * i, 260 + 40 * i);
                    float in = 6.5f + 0.8f * g, out = 8.5f + 2.2f * g;
                    path.moveTo(12 + (float) Math.cos(a) * in, 12 + (float) Math.sin(a) * in);
                    path.lineTo(12 + (float) Math.cos(a) * out, 12 + (float) Math.sin(a) * out);
                }
                break;
            }
            case PALETTE: {
                path.addCircle(12, 12, 8.5f, Path.Direction.CW);

                float[][] d = {{8.5f, 10}, {12, 7.5f}, {15.5f, 10}};
                for (int i = 0; i < 3; i++) {
                    path.addCircle(d[i][0], d[i][1], 1.1f * (1f + 0.9f * bump(T, 60 + 80 * i)), Path.Direction.CW);
                }
                break;
            }
            case CODE: {

                float o = 2.2f * wave(T, 0, 500);
                path.moveTo(9 - o, 7);
                path.lineTo(4 - o, 12);
                path.lineTo(9 - o, 17);
                path.moveTo(15 + o, 7);
                path.lineTo(20 + o, 12);
                path.lineTo(15 + o, 17);
                break;
            }
            case SHIELD:
                path.moveTo(12, 3.5f);
                path.lineTo(19, 6.5f);
                path.lineTo(19, 11.5f);
                path.quadTo(19, 17.5f, 12, 20.5f);
                path.quadTo(5, 17.5f, 5, 11.5f);
                path.lineTo(5, 6.5f);
                path.close();
                {

                    float k = Motion.clamp01(sp(T, 140, 0.35f, 0.85f));
                    if (k > 0.01f) {
                        float u = Math.min(1f, k * 2f), v = Math.max(0f, k * 2f - 1f);
                        path.moveTo(8.8f, 12.2f);
                        path.lineTo(8.8f + 2f * u, 12.2f + 2f * u);
                        if (v > 0f) path.lineTo(10.8f + 4.4f * v, 14.2f - 4.6f * v);
                    }
                }
                break;
            case DOWNLOAD: {

                float fy = 3.5f * wave(T, 0, 420);
                float ty = 1.2f * hump(T, 200, 480);
                path.moveTo(12, 4 + fy);
                path.lineTo(12, 14.5f + fy);
                path.moveTo(7.5f, 10.5f + fy);
                path.lineTo(12, 15 + fy);
                path.lineTo(16.5f, 10.5f + fy);
                path.moveTo(4.5f, 15.5f + ty);
                path.lineTo(4.5f, 18.5f + ty);
                path.quadTo(4.5f, 20 + ty, 6, 20 + ty);
                path.lineTo(18, 20 + ty);
                path.quadTo(19.5f, 20 + ty, 19.5f, 18.5f + ty);
                path.lineTo(19.5f, 15.5f + ty);
                break;
            }
            case GLOBE: {

                path.addCircle(12, 12, 8.5f, Path.Direction.CW);
                path.moveTo(3.5f, 12);
                path.lineTo(20.5f, 12);

                float spin = (float) (Math.PI * sp(T, 0, 0.6f, 0.7f));
                float w = Math.abs(8.5f * (float) Math.sin(0.517f + spin));
                if (w < 0.3f) {
                    path.moveTo(12, 3.5f);
                    path.lineTo(12, 20.5f);
                } else {
                    r.set(12 - w, 3.5f, 12 + w, 20.5f);
                    path.addOval(r, Path.Direction.CW);
                }
                break;
            }
            case PLAY: {

                path.addCircle(12, 12, 8.5f, Path.Direction.CW);
                float push = 2.2f * wave(T, 0, 380);
                part.moveTo(10, 8.3f);
                part.lineTo(16, 12);
                part.lineTo(10, 15.7f);
                part.close();
                addPart(push, 0, 0, 1f - 0.18f * hump(T, 0, 160), 12, 12);
                break;
            }
            case TERMINAL: {

                rr(path, 3.8f, 4.8f, 20.2f, 19.2f, 3.2f);
                float push = 1.6f * wave(T, 0, 420);
                path.moveTo(7.8f + push, 9.4f);
                path.lineTo(11.3f + push, 12f);
                path.lineTo(7.8f + push, 14.6f);
                float cursor = 1f + 0.35f * hump(T, 120, 480);
                path.moveTo(12.6f, 14.6f);
                path.lineTo(12.6f + 3.6f * cursor, 14.6f);
                break;
            }
            case DATABASE: {

                float liftTop = -1.6f * wave(T, 0, 420);
                float liftMid = -0.8f * wave(T, 70, 420);
                r.set(4.5f, 4.2f + liftTop, 19.5f, 9.0f + liftTop);
                path.addOval(r, Path.Direction.CW);
                path.moveTo(4.5f, 6.6f + liftTop);
                path.lineTo(4.5f, 17.6f);
                path.moveTo(19.5f, 6.6f + liftTop);
                path.lineTo(19.5f, 17.6f);
                r.set(4.5f, 9.6f + liftMid, 19.5f, 14.4f + liftMid);
                path.addArc(r, 0f, 180f);
                r.set(4.5f, 15.2f, 19.5f, 20.0f);
                path.addArc(r, 0f, 180f);
                break;
            }
            default:
                break;
        }
        path.transform(gm);
        c.drawPath(path, paint);
        if (kind == PHONE && t >= 0f) {

            float a = hump(t, 60, 320);
            if (a > 0.01f) {
                dot.setColor(paint.getColor());
                dot.setAlpha(Math.round(paint.getAlpha() * 0.55f * a));
                r.set(8.4f, 4.9f, 15.6f, 16.2f);
                part.reset();
                part.addRoundRect(r, 1.6f, 1.6f, Path.Direction.CW);
                part.transform(gm);
                c.drawPath(part, dot);
                part.reset();
            }
        }
        if (t >= 0f) effects(c, t);
        c.restore();

        if (playStart >= 0) scheduleSelf(nextFrame, android.os.SystemClock.uptimeMillis() + 8);
    }

    private void rr(Path p, float l, float t, float rr, float b, float rad) {
        r.set(l, t, rr, b);
        p.addRoundRect(r, rad, rad, Path.Direction.CW);
    }

    private void addPart(float dx, float dy, float deg, float scale, float px, float py) {
        pm.setScale(scale, scale, px, py);
        pm.postRotate(deg, px, py);
        pm.postTranslate(dx, dy);
        part.transform(pm);
        path.addPath(part);
        part.reset();
    }

    @Override
    public void setAlpha(int a) {
        paint.setAlpha(a);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        paint.setColorFilter(cf);
        dot.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return -1;
    }

    @Override
    public int getIntrinsicHeight() {
        return -1;
    }
}
