package com.icehome.up;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.BlendMode;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RecordingCanvas;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Choreographer;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

public final class GlassBar extends View {

    public interface OnSelect {
        public void onSelect(int index);
    }

    static final String LENS =
            "uniform shader content;\n"
            + "uniform float2 center;\n"
            + "uniform float2 halfSize;\n"
            + "uniform float radius;\n"
            + "uniform float refractionHeight;\n"
            + "uniform float refractionAmount;\n"
            + "uniform float magnify;\n"
            + "uniform float chroma;\n"
            + "float sdRoundedRect(float2 coord, float2 hs, float r) {\n"
            + "  float2 cc = abs(coord) - (hs - float2(r));\n"
            + "  return length(max(cc, 0.0)) + min(max(cc.x, cc.y), 0.0) - r;\n"
            + "}\n"
            + "float2 gradSdRoundedRect(float2 coord, float2 hs, float r) {\n"
            + "  float2 cc = abs(coord) - (hs - float2(r));\n"

            + "  if (cc.x >= 0.0 || cc.y >= 0.0) return sign(coord) * normalize(max(cc, 0.0) + 0.0001);\n"
            + "  float gx = step(cc.y, cc.x);\n"
            + "  return sign(coord) * float2(gx, 1.0 - gx);\n"
            + "}\n"
            + "float circleMap(float x) { return 1.0 - sqrt(1.0 - x * x); }\n"
            + "half4 main(float2 coord) {\n"
            + "  float2 c = coord - center;\n"
            + "  float sd = sdRoundedRect(c, halfSize, radius);\n"
            + "  float2 q = center + c / magnify;\n"
            + "  if (-sd >= refractionHeight) return content.eval(q);\n"
            + "  sd = min(sd, 0.0);\n"
            + "  float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;\n"
            + "  float gr = min(radius * 1.5, min(halfSize.x, halfSize.y));\n"
            + "  float2 grad = normalize(gradSdRoundedRect(c, halfSize, gr));\n"

            + "  float2 off = d * grad;\n"
            + "  float k = chroma * (1.0 - clamp(-sd / refractionHeight, 0.0, 1.0));\n"
            + "  if (k <= 0.0) return content.eval(q + off);\n"

            + "  half3 acc = half3(0.0); half3 wsum = half3(0.0); half alpha = 0.0;\n"
            + "  for (int i = 0; i < 7; i++) {\n"
            + "    float t = float(i) / 3.0 - 1.0;\n"
            + "    half4 s = content.eval(q + off * (1.0 + k * t));\n"
            + "    half3 w = half3(max(0.0, 1.0 - abs(t - 0.6)), 1.0 - abs(t) * 0.7, max(0.0, 1.0 - abs(t + 0.6)));\n"
            + "    acc += s.rgb * w; wsum += w; alpha += s.a;\n"
            + "  }\n"
            + "  return half4(acc / wsum, alpha / 7.0);\n"
            + "}\n";

    static final String CAPSULE =
            "uniform shader content;\n"
            + "uniform float2 size;\n"
            + "uniform float radius;\n"
            + "uniform float refractionHeight;\n"
            + "uniform float refractionAmount;\n"
            + "uniform float depth;\n"
            + "uniform float chroma;\n"
            + "float sdRoundedRect(float2 coord, float2 hs, float r) {\n"
            + "  float2 cc = abs(coord) - (hs - float2(r));\n"
            + "  return length(max(cc, 0.0)) + min(max(cc.x, cc.y), 0.0) - r;\n"
            + "}\n"
            + "float2 gradSdRoundedRect(float2 coord, float2 hs, float r) {\n"
            + "  float2 cc = abs(coord) - (hs - float2(r));\n"
            + "  if (cc.x >= 0.0 || cc.y >= 0.0) return sign(coord) * normalize(max(cc, 0.0) + 0.0001);\n"
            + "  float gx = step(cc.y, cc.x);\n"
            + "  return sign(coord) * float2(gx, 1.0 - gx);\n"
            + "}\n"
            + "float circleMap(float x) { return 1.0 - sqrt(1.0 - x * x); }\n"
            + "half4 main(float2 coord) {\n"
            + "  float2 hs = size * 0.5;\n"
            + "  float2 c = coord - hs;\n"
            + "  float sd = sdRoundedRect(c, hs, radius);\n"

            + "  if (-sd >= refractionHeight) return content.eval(coord);\n"
            + "  sd = min(sd, 0.0);\n"
            + "  float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;\n"
            + "  float gr = min(radius * 1.5, min(hs.x, hs.y));\n"
            + "  float2 grad = normalize(gradSdRoundedRect(c, hs, gr) + depth * c / max(length(c), 0.001));\n"
            + "  float2 q = coord + d * grad;\n"

            + "  float k = chroma * (c.x * c.y) / (hs.x * hs.y);\n"
            + "  if (abs(k) < 0.001) return content.eval(q);\n"
            + "  float2 disp = d * grad * k;\n"

            + "  half4 s0 = content.eval(q + disp);\n"
            + "  half4 s1 = content.eval(q + disp * 0.6667);\n"
            + "  half4 s2 = content.eval(q + disp * 0.3333);\n"
            + "  half4 s3 = content.eval(q);\n"
            + "  half4 s4 = content.eval(q - disp * 0.3333);\n"
            + "  half4 s5 = content.eval(q - disp * 0.6667);\n"
            + "  half4 s6 = content.eval(q - disp);\n"
            + "  half r = (s0.r + s1.r + s2.r) / 3.5 + s6.r / 7.0;\n"
            + "  half g = s1.g / 7.0 + (s2.g + s3.g + s4.g) / 3.5;\n"
            + "  half b = (s4.b + s5.b + s6.b) / 3.0;\n"
            + "  half a = (s0.a + s1.a + s2.a + s3.a + s4.a + s5.a + s6.a) / 7.0;\n"
            + "  return half4(r, g, b, a);\n"
            + "}\n";

    static final String RIM =
            "uniform float2 size;\n"
            + "uniform float radius;\n"
            + "uniform float2 light;\n"
            + "float sdRoundedRect(float2 coord, float2 hs, float r) {\n"
            + "  float2 cc = abs(coord) - (hs - float2(r));\n"
            + "  return length(max(cc, 0.0)) + min(max(cc.x, cc.y), 0.0) - r;\n"
            + "}\n"
            + "float2 gradSdRoundedRect(float2 coord, float2 hs, float r) {\n"
            + "  float2 cc = abs(coord) - (hs - float2(r));\n"
            + "  if (cc.x >= 0.0 || cc.y >= 0.0) return sign(coord) * normalize(max(cc, 0.0) + 0.0001);\n"
            + "  float gx = step(cc.y, cc.x);\n"
            + "  return sign(coord) * float2(gx, 1.0 - gx);\n"
            + "}\n"
            + "half4 main(float2 coord) {\n"
            + "  float2 hs = size * 0.5;\n"
            + "  float gr = min(radius * 1.5, min(hs.x, hs.y));\n"
            + "  float i = abs(dot(gradSdRoundedRect(coord - hs, hs, gr), light));\n"
            + "  return half4(half(i));\n"
            + "}\n";

    private static final float SCALE = 0.35f;
    private static final float BLUR_DP = 3f;
    private static final float CHROMA = 0.6f;
    private static final float SATURATION = 1.5f;
    private static final float LENS_DP = 28f;
    private static final int TINT = 0x800B111C;
    private static final float EDGE_ALPHA = 0.12f, RIM_ALPHA = 0.5f;
    private static final float GLOW_ALPHA = 0.25f, GLOW_DP = 64f;
    private static final float PILL_INSET_DP = 4f, PILL_LIFT_DP = 6f;
    private static final int PILL_FILL = 0x29FFFFFF;
    private static final float PRESS_SCALE = 0.02f, SHIFT_X_DP = 5f, SHIFT_Y_DP = 2.5f;
    private static final float STRETCH_X = 0.015f, STRETCH_Y = 0.006f;
    private static final float COMPACT_SCALE = 0.86f, COMPACT_DROP_DP = 4f;
    private static final float COMPACT_AFTER_DP = 24f, EXPAND_AFTER_DP = 12f;

    private final String[] labels;
    private final Icons[] icons;
    private final float dp, padH, padV;
    private final int textColor, selectedColor, base;
    private final Paint tint = new Paint(), flat = new Paint(Paint.ANTI_ALIAS_FLAG),
            edge = new Paint(Paint.ANTI_ALIAS_FLAG), glow = new Paint(Paint.ANTI_ALIAS_FLAG),
            pillPaint = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint rim;
    private RuntimeShader lens, rimShader;
    private RenderNode backdrop;
    private int effectW = -1, effectH = -1;
    private boolean failed;

    private final RectF bar = new RectF(), pill = new RectF();
    private final Path local = new Path();
    private final Matrix m = new Matrix(), inv = new Matrix(), glowAt = new Matrix();
    private final float[] pt = new float[2];
    private final int[] myLoc = new int[2], srcLoc = new int[2];
    private View source;
    private OnSelect listener;
    private int selected;

    private final Spring travel = Spring.period(0.4, 0.85);
    private final Spring liftH = Spring.period(0.3, 0.55), liftV = Spring.period(0.3, 0.7);
    private final Spring press = new Spring(500, 36), shiftX = new Spring(260, 18), shiftY = new Spring(260, 18),
            compact = new Spring(220, 28);

    private double stretch;
    private boolean pillDrag;
    private double fingerTarget;
    private boolean ticking;
    private long lastFrame = -1;
    private final Choreographer.FrameCallback tick = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long now) {
            onFrame(now);
        }
    };

    private final int slop;
    private boolean tracking, dragging;
    private float downX, downY, touchX, touchY;
    private int startIndex, lastHaptic;
    private float scrollAcc;

    private int morphIndex = -1;
    private float morphT = 1f;
    private ValueAnimator morphAnim;

    public GlassBar(Context c, String[] labels, int[] iconKinds, int baseColor, int textColor, int selectedColor) {
        super(c);
        this.labels = labels.clone();
        this.textColor = textColor;
        this.selectedColor = selectedColor;
        this.base = baseColor | 0xFF000000;
        dp = c.getResources().getDisplayMetrics().density;
        padH = 12f * dp;
        padV = 12f * dp;
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        icons = new Icons[labels.length];
        for (int i = 0; i < labels.length; i++) {
            icons[i] = new Icons(iconKinds[i], textColor);
            icons[i].setCallback(this);
        }
        tint.setColor(TINT);
        flat.setColor(0xF0121212);

        float stroke = (float) Math.ceil(0.5f * dp) * 2f;
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(stroke);
        edge.setColor(Color.WHITE);
        edge.setAlpha(Math.round(255 * EDGE_ALPHA));
        glow.setShader(new RadialGradient(0, 0, GLOW_DP * dp, Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP));
        pillPaint.setColor(PILL_FILL);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(12f * dp);
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        backdrop = new RenderNode("glassBarBackdrop");
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                lens = new RuntimeShader(CAPSULE);
                rimShader = new RuntimeShader(RIM);
                rim = new Paint(Paint.ANTI_ALIAS_FLAG);
                rim.setStyle(Paint.Style.STROKE);
                rim.setStrokeWidth(stroke);
                rim.setBlendMode(BlendMode.PLUS);
                rim.setShader(rimShader);
                rim.setAlpha(Math.round(255 * RIM_ALPHA));
                rim.setMaskFilter(new BlurMaskFilter(0.25f * dp, BlurMaskFilter.Blur.NORMAL));
            } catch (Throwable t) {
                android.util.Log.w("Sonoro", "GlassBar: shader rejected", t);
                lens = rimShader = null;
                rim = null;
            }
        }
        setClickable(true);
    }

    public int heightFor(float barDp) {
        return Math.round(barDp * dp + 2 * padV);
    }

    public void setSource(View v) {
        source = v;
        invalidate();
    }

    public void setOnSelect(OnSelect l) {
        listener = l;
    }

    public void select(int index, boolean animate) {
        if (animate && index != selected) icons[index].play();
        selected = index;
        setCompact(false);
        if (animate) animateTo(index);
        else snapTo(index);
    }

    /** Morphs a tab's label and icon in-place with a smooth shrink-swap-spring pop. */
    public void morphTab(final int index, final String newLabel, final int newIconKind) {
        if (index < 0 || index >= labels.length) return;
        if (morphAnim != null) {
            morphAnim.cancel();
            morphAnim = null;
        }
        if (!ValueAnimator.areAnimatorsEnabled()) {
            labels[index] = newLabel;
            icons[index] = new Icons(newIconKind, textColor);
            icons[index].setCallback(this);
            morphIndex = -1;
            morphT = 1f;
            invalidate();
            return;
        }
        morphIndex = index;
        morphT = 0f;
        final boolean[] swapped = {false};
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(460);
        a.setInterpolator(null);
        a.addUpdateListener(va -> {
            morphT = (Float) va.getAnimatedValue();
            if (!swapped[0] && morphT >= 0.45f) {
                swapped[0] = true;
                labels[index] = newLabel;
                icons[index] = new Icons(newIconKind, textColor);
                icons[index].setCallback(GlassBar.this);
                icons[index].play();
            }
            invalidate();
        });
        a.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator an) {
                labels[index] = newLabel;
                if (!swapped[0]) {
                    icons[index] = new Icons(newIconKind, textColor);
                    icons[index].setCallback(GlassBar.this);
                }
                if (morphAnim == a) morphAnim = null;
                morphIndex = -1;
                morphT = 1f;
                invalidate();
            }
        });
        morphAnim = a;
        a.start();
    }

    public void onPageScroll(int y, int dy) {
        if (y <= 0) {
            scrollAcc = 0;
            setCompact(false);
            return;
        }
        if (dy == 0) return;
        if (Math.signum(dy) != Math.signum(scrollAcc)) scrollAcc = 0;
        scrollAcc += dy;
        if (scrollAcc > COMPACT_AFTER_DP * dp) setCompact(true);
        else if (scrollAcc < -EXPAND_AFTER_DP * dp) setCompact(false);
    }

    private void setCompact(boolean on) {
        if (tracking && on) return;
        double to = on ? 1 : 0;
        if (compact.target == to) return;
        compact.target = to;
        if (ValueAnimator.areAnimatorsEnabled()) kick();
        else {
            compact.reset(to);
            invalidate();
        }
    }

    private void animateTo(int index) {
        if (!ValueAnimator.areAnimatorsEnabled()) {
            snapTo(index);
            return;
        }
        travel.target = index;
        lift(true);
        kick();
    }

    private void snapTo(int index) {
        pillDrag = false;
        travel.reset(index);
        liftH.reset(0);
        liftV.reset(0);
        stretch = 0;
        invalidate();
    }

    private void lift(boolean up) {
        liftH.target = liftV.target = up ? 1 : 0;
    }

    private void kick() {
        if (ticking) return;
        ticking = true;
        lastFrame = -1;
        Choreographer.getInstance().postFrameCallback(tick);
    }

    private void onFrame(long now) {
        if (!ticking) return;
        double dt = lastFrame < 0 ? 0 : Math.min((now - lastFrame) / 1e9, 0.1);
        lastFrame = now;

        float scale = ValueAnimator.getDurationScale();
        if (scale > 0f) dt /= scale;
        boolean moving;
        try {
            moving = step(dt);
        } catch (Throwable t) {
            moving = false;
        }
        invalidate();
        if (moving) Choreographer.getInstance().postFrameCallback(tick);
        else ticking = false;
    }

    private boolean step(double dt) {
        if (pillDrag) {

            double before = travel.value;
            travel.value += (fingerTarget - travel.value) * (1 - Math.exp(-dt / 0.04));
            travel.target = travel.value;
            travel.velocity = dt > 0 ? (travel.value - before) / dt : 0;
        } else {
            travel.step(dt);
            if (Math.abs(travel.target - travel.value) < 0.12) lift(false);
        }
        liftH.step(dt);
        liftV.step(dt);

        float stepDp = labels.length > 0 ? bar.width() / labels.length / dp : 0f;
        double want = Math.min(Math.abs(travel.velocity) * stepDp * 6e-5, 0.12);
        stretch += (want - stretch) * (1 - Math.exp(-dt / 0.06));
        press.step(dt);
        shiftX.step(dt);
        shiftY.step(dt);
        compact.step(dt);
        boolean pillRest = !pillDrag && travel.atRest() && liftH.atRest() && liftV.atRest()
                && liftH.target == 0 && stretch < 5e-4;
        if (pillRest) {
            travel.reset(travel.target);
            stretch = 0;
        }
        boolean barRest = press.atRest() && shiftX.atRest() && shiftY.atRest() && compact.atRest();
        return !(pillRest && barRest);
    }

    private void barMatrix(Matrix out) {
        float p = (float) press.value, sx = (float) shiftX.value, sy = (float) shiftY.value;
        float c = (float) compact.value;
        float stX = Math.abs(sx) / (SHIFT_X_DP * dp) * STRETCH_X;
        float stY = Math.abs(sy) / (SHIFT_Y_DP * dp) * STRETCH_Y;
        float f = 1f - (1f - COMPACT_SCALE) * c;
        float scaleX = f * (1f + PRESS_SCALE * p + stX - 0.5f * stY);
        float scaleY = f * (1f + PRESS_SCALE * p + stY - 0.25f * stX);
        float drop = bar.height() * (1f - f) / 2f + COMPACT_DROP_DP * dp * c;
        out.setScale(scaleX, scaleY, bar.centerX(), bar.centerY());
        out.postTranslate(sx, sy + drop);
    }

    private static float rubber(float distance, float max) {
        if (max <= 0f) return 0f;
        return Math.signum(distance) * max * (1f - 1f / (Math.abs(distance) / max * 0.55f + 1f));
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int n = labels.length;
        if (n == 0 || bar.isEmpty()) return false;

        barMatrix(m);
        m.invert(inv);
        pt[0] = e.getX();
        pt[1] = e.getY();
        inv.mapPoints(pt);
        float seg = bar.width() / n;
        boolean anim = ValueAnimator.areAnimatorsEnabled();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!bar.contains(pt[0], pt[1])) return false;
                tracking = true;
                dragging = false;
                downX = e.getX();
                downY = e.getY();
                touchX = pt[0];
                touchY = pt[1];
                setCompact(false);
                if (anim) {
                    press.target = 1;
                    shiftX.target = shiftY.target = 0;
                    kick();
                }
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (!tracking) return false;
                float dx = e.getX() - downX, dy = e.getY() - downY;
                touchX = pt[0];
                touchY = pt[1];
                if (anim) {
                    shiftX.target = rubber(dx, SHIFT_X_DP * dp);
                    shiftY.target = rubber(dy, SHIFT_Y_DP * dp);
                }
                if (!dragging && Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) && n > 1) {

                    dragging = true;
                    startIndex = lastHaptic = selected;
                    pillDrag = true;
                    fingerTarget = travel.value;
                    travel.velocity = 0;
                    if (anim) lift(true);
                }
                if (dragging) {

                    float d = dx;
                    if ((d > 0 && startIndex == n - 1) || (d < 0 && startIndex == 0)) d *= 0.33f;
                    float pos = startIndex + d / seg;
                    fingerTarget = Math.max(0f, Math.min(n - 1f, pos));
                    int near = Math.round(Math.max(0f, Math.min(n - 1f, pos)));
                    if (near != lastHaptic) {
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        lastHaptic = near;
                    }
                    if (!anim) travel.reset(fingerTarget);
                }
                kick();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (!tracking) return false;
                tracking = false;
                press.target = shiftX.target = shiftY.target = 0;
                int i;
                if (e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    i = selected;
                } else if (dragging) {

                    float steps = (e.getX() - downX) / seg;
                    int jump = steps > 0.3f ? Math.max(1, Math.round(steps))
                            : steps < -0.3f ? Math.min(-1, Math.round(steps)) : 0;
                    i = Math.max(0, Math.min(n - 1, startIndex + jump));
                } else {
                    i = Math.max(0, Math.min(n - 1, (int) ((pt[0] - bar.left) / seg)));
                }
                boolean wasDrag = dragging;
                dragging = false;
                pillDrag = false;
                if (i != selected) {
                    if (!wasDrag) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    icons[i].play();
                    selected = i;
                    if (listener != null) listener.onSelect(i);
                }
                if (!anim) snapTo(i);
                else if (wasDrag) {
                    travel.target = i;
                } else if (Math.abs(travel.value - i) > 0.001) {
                    animateTo(i);
                }
                kick();
                return true;
            }
            default:
                return true;
        }
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        for (Icons ic : icons) if (ic == who) return true;
        return super.verifyDrawable(who);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (ticking) Choreographer.getInstance().removeFrameCallback(tick);
        ticking = false;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        bar.set(padH, padV, w - padH, h - padV);
        float r = bar.height() / 2f;
        local.rewind();
        local.addRoundRect(0, 0, bar.width(), bar.height(), r, r, Path.Direction.CW);
        if (rimShader != null) {
            rimShader.setFloatUniform("size", bar.width(), bar.height());
            rimShader.setFloatUniform("radius", r);
            float a = (float) Math.toRadians(45);
            rimShader.setFloatUniform("light", (float) Math.cos(a), (float) Math.sin(a));
        }
        effectW = effectH = -1;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (bar.isEmpty() || labels.length == 0) return;
        barMatrix(m);
        canvas.save();
        canvas.concat(m);
        drawCapsule(canvas);
        drawPill(canvas);
        drawTabs(canvas);
        canvas.restore();
    }

    private void drawCapsule(Canvas canvas) {
        canvas.save();
        canvas.translate(bar.left, bar.top);
        canvas.clipPath(local);
        boolean glass = !failed && source != null && canvas.isHardwareAccelerated() && Build.VERSION.SDK_INT >= 31;
        if (glass) {
            try {
                record();
            } catch (Throwable t) {
                failed = true;
                glass = false;
                android.util.Log.w("Sonoro", "GlassBar: unable to read backdrop", t);
            }
        }
        if (glass) {
            canvas.save();
            canvas.scale(1f / SCALE, 1f / SCALE);
            canvas.drawRenderNode(backdrop);
            canvas.restore();
            canvas.drawRect(0, 0, bar.width(), bar.height(), tint);
        } else {
            canvas.drawRect(0, 0, bar.width(), bar.height(), flat);
        }

        float g = Math.max(0f, Math.min(1f, (float) press.value)) * GLOW_ALPHA;
        if (g > 0.004f) {
            glowAt.setTranslate(touchX - bar.left, touchY - bar.top);
            glow.getShader().setLocalMatrix(glowAt);
            glow.setAlpha(Math.round(255 * g));
            canvas.drawPaint(glow);
        }
        canvas.drawPath(local, edge);
        if (rim != null) canvas.drawPath(local, rim);
        canvas.restore();
    }

    private void record() {
        int w = Math.max(1, (int) Math.ceil(bar.width() * SCALE));
        int h = Math.max(1, (int) Math.ceil(bar.height() * SCALE));
        backdrop.setPosition(0, 0, w, h);
        RecordingCanvas rc = backdrop.beginRecording(w, h);
        try {
            rc.drawColor(base);
            rc.scale(SCALE, SCALE);
            rc.translate(-bar.left, -bar.top);
            m.invert(inv);
            rc.concat(inv);
            getLocationInWindow(myLoc);
            source.getLocationInWindow(srcLoc);
            rc.translate(srcLoc[0] - myLoc[0] - source.getScrollX(), srcLoc[1] - myLoc[1] - source.getScrollY());
            source.draw(rc);
        } finally {
            backdrop.endRecording();
        }
        if (w != effectW || h != effectH) {
            backdrop.setRenderEffect(effectFor(w, h));
            effectW = w;
            effectH = h;
        }
    }

    private RenderEffect effectFor(int w, int h) {
        float blur = BLUR_DP * dp * SCALE;
        ColorMatrix sat = new ColorMatrix();
        sat.setSaturation(SATURATION);
        RenderEffect effect = RenderEffect.createChainEffect(
                RenderEffect.createColorFilterEffect(new ColorMatrixColorFilter(sat)),
                RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP));
        if (lens != null) {
            lens.setFloatUniform("size", w, h);
            lens.setFloatUniform("radius", h / 2f);
            lens.setFloatUniform("refractionHeight", LENS_DP * dp * SCALE);
            lens.setFloatUniform("refractionAmount", -LENS_DP * dp * SCALE);
            lens.setFloatUniform("depth", 1f);
            lens.setFloatUniform("chroma", CHROMA);
            effect = RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(lens, "content"), effect);
        }
        return effect;
    }

    private void drawPill(Canvas canvas) {
        int n = labels.length;
        float seg = bar.width() / n;
        float pos = Math.max(0f, Math.min(n - 1f, (float) travel.value));
        float inset = PILL_INSET_DP * dp;
        float w0 = seg - 2f * inset, h0 = bar.height() - 2f * inset;
        if (w0 <= 0f || h0 <= 0f) return;
        float hl = h0 + PILL_LIFT_DP * dp;
        float lx = (float) liftH.value, ly = (float) liftV.value, dev = (float) stretch;
        float pw = (w0 + (hl * w0 / h0 - w0) * lx) * (1f + dev);
        float ph = (h0 + (hl - h0) * ly) * (1f - dev);
        float cx = bar.left + (pos + 0.5f) * seg, cy = bar.centerY();
        pill.set(cx - pw / 2f, cy - ph / 2f, cx + pw / 2f, cy + ph / 2f);
        float r = pill.height() / 2f;
        canvas.drawRoundRect(pill, r, r, pillPaint);
    }

    private void drawTabs(Canvas c) {
        int n = labels.length;
        float seg = bar.width() / n;
        float pos = (float) travel.value;
        float iconSize = 24f * dp;
        float iconTop = bar.centerY() - iconSize / 2f - 8f * dp;
        float baseline = bar.centerY() + 19f * dp;
        for (int i = 0; i < n; i++) {
            float near = Math.max(0f, 1f - Math.abs(pos - i));
            int col = Controls.blend(textColor, selectedColor, near);
            float cx = bar.left + seg * (i + 0.5f);
            Icons ic = icons[i];
            if (i == morphIndex && morphT < 1f) {
                float slotScale, slotDy, slotAlpha;
                if (morphT < 0.45f) {
                    float f = Motion.EASE.getInterpolation(morphT / 0.45f);
                    slotScale = 1f - 0.28f * f;
                    slotDy = -7f * dp * f;
                    slotAlpha = 1f - f;
                } else {
                    float p = (morphT - 0.45f) / 0.55f;
                    float b = Motion.BACK_OUT.getInterpolation(p);
                    slotScale = 0.72f + 0.28f * b;
                    slotDy = 8f * dp * (1f - b);
                    slotAlpha = Math.min(1f, p * 2.2f);
                }
                int a255 = Math.max(0, Math.min(255, Math.round(255f * slotAlpha)));
                c.save();
                c.translate(0f, slotDy);
                c.scale(slotScale, slotScale, cx, bar.centerY());
                ic.setColor(col);
                ic.setAlpha(a255);
                ic.setBounds(Math.round(cx - iconSize / 2f), Math.round(iconTop), Math.round(cx + iconSize / 2f), Math.round(iconTop + iconSize));
                ic.draw(c);
                ic.setAlpha(255);
                text.setColor(col);
                text.setAlpha(a255);
                c.drawText(labels[i], cx, baseline, text);
                text.setAlpha(255);
                c.restore();
                continue;
            }
            ic.setColor(col);
            ic.setAlpha(255);
            ic.setBounds(Math.round(cx - iconSize / 2f), Math.round(iconTop), Math.round(cx + iconSize / 2f), Math.round(iconTop + iconSize));
            ic.draw(c);
            text.setColor(col);
            text.setAlpha(255);
            c.drawText(labels[i], cx, baseline, text);
        }
    }

    private static final class Spring {
        final double k, c;
        double value, velocity, target;

        Spring(double stiffness, double damping) {
            k = stiffness;
            c = damping;
        }

        static Spring period(double response, double damping) {
            double w = 2 * Math.PI / response;
            return new Spring(w * w, 2 * damping * w);
        }

        boolean atRest() {
            return Math.abs(value - target) < 0.001 && Math.abs(velocity) < 0.005;
        }

        void reset(double to) {
            value = target = to;
            velocity = 0;
        }

        void step(double dt) {
            while (dt > 0) {
                double h = Math.min(dt, 1.0 / 240);
                velocity += (-k * (value - target) - c * velocity) * h;
                value += velocity * h;
                dt -= h;
            }
            if (atRest()) {
                value = target;
                velocity = 0;
            }
        }
    }
}
