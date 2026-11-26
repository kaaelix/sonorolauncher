package com.icehome.up;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.ComposeShader;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

public final class LiveBackground extends Drawable {

    static final int CORAL = Motion.ACCENT;

    private final int base;
    private final Paint dots = new Paint();

    private final boolean reactive;
    private final Paint lit = new Paint();
    private BitmapShader litDots;
    private final float dp;
    private float gx, gy, glow;
    private ValueAnimator fade;
    private float fadeGoal;

    private float wx, wy, wr, wa;
    private final Paint wave = new Paint();

    private LiveBackground(int base, float dp, int dim, boolean reactive) {
        this.base = base;
        this.dp = dp;
        this.reactive = false;
    }

    void touch(View host, MotionEvent e) {
    }

    public static LiveBackground dots(int base, float dp) {
        return new LiveBackground(base, dp, 0, false);
    }

    public static LiveBackground hero(int base, float dp) {
        return new LiveBackground(base, dp, 0, false);
    }

    public static android.widget.ScrollView scrollView(android.content.Context c) {
        return new android.widget.ScrollView(c);
    }

    void wave(float x, float y, float r, float alpha) {
    }

    public void attachTo(View page) {
        page.setBackground(this);
    }

    private boolean ambient;
    private ValueAnimator drift;
    private float clock;
    private long lastTickMs;
    private final Paint amb1 = new Paint(), amb2 = new Paint();
    private RadialGradient mask1, mask2, touchMask;
    private final android.graphics.Matrix lightM = new android.graphics.Matrix();

    public static LiveBackground ambient(int base, float dp, boolean hero) {
        return new LiveBackground(base, dp, 0, false);
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.drawColor(base);
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
