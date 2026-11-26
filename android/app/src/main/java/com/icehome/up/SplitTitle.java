package com.icehome.up;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.view.View;

import java.util.ArrayList;

public final class SplitTitle extends View {

    private static final long CHAR_MS = 820, STAGGER_MS = 26;

    private final TextPaint paint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private final String text;
    private final ArrayList<String> lines = new ArrayList<>();

    private final ArrayList<float[]> xs = new ArrayList<>();
    private float lineH, ascent;

    private final float baseSize;
    private int laidW = -1;
    private boolean centered;
    private ValueAnimator anim;

    private float t = 1e9f;

    public SplitTitle(Context c, String text, float sp, int color, int weight) {
        super(c);
        this.text = text;
        baseSize = sp * c.getResources().getDisplayMetrics().scaledDensity;
        paint.setTextSize(baseSize);
        paint.setColor(color);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, weight, false));

        paint.setLetterSpacing(-0.025f);
        setContentDescription(text);
    }

    public void setCentered(boolean c) {
        centered = c;
        laidW = -1;
        requestLayout();
    }

    public void hideForReveal() {
        if (anim != null) anim.cancel();
        t = -1f;
        invalidate();
    }

    public void play(long delay) {
        if (anim != null) anim.cancel();
        if (!ValueAnimator.areAnimatorsEnabled()) {
            t = 1e9f;
            invalidate();
            return;
        }
        int chars = text.length();
        final long total = CHAR_MS + STAGGER_MS * chars;
        t = -1f;
        anim = ValueAnimator.ofFloat(0f, total);
        anim.setDuration(total);
        anim.setStartDelay(delay);
        anim.setInterpolator(null);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                t = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        anim.start();
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (anim != null) anim.end();
        super.onDetachedFromWindow();
    }

    private void layout(int w) {
        if (w == laidW) return;
        laidW = w;
        lines.clear();
        xs.clear();
        String[] words = text.split(" ");

        paint.setTextSize(baseSize);
        float widest = 0f;
        for (String word : words) widest = Math.max(widest, paint.measureText(word));
        if (widest > w) paint.setTextSize(baseSize * w / widest * 0.98f);
        StringBuilder cur = new StringBuilder();
        for (String word : words) {
            String tryLine = cur.length() == 0 ? word : cur + " " + word;
            if (cur.length() > 0 && paint.measureText(tryLine) > w) {
                lines.add(cur.toString());
                cur.setLength(0);
                cur.append(word);
            } else {
                cur.setLength(0);
                cur.append(tryLine);
            }
        }
        if (cur.length() > 0) lines.add(cur.toString());
        for (String l : lines) {
            float[] x = new float[l.length() + 1];
            for (int i = 0; i <= l.length(); i++) x[i] = paint.measureText(l, 0, i);
            xs.add(x);
        }
        TextPaint.FontMetrics fm = paint.getFontMetrics();
        ascent = -fm.ascent;
        lineH = (fm.descent - fm.ascent) * 1.02f;
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        layout(Math.max(1, w - getPaddingLeft() - getPaddingRight()));
        int h = Math.round(lines.size() * lineH) + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas c) {
        int avail = getWidth() - getPaddingLeft() - getPaddingRight();
        float y0 = getPaddingTop();

        if (t >= CHAR_MS + STAGGER_MS * (text.length() + lines.size())) {
            for (int li = 0; li < lines.size(); li++) {
                String l = lines.get(li);
                float left = getPaddingLeft() + (centered ? (avail - xs.get(li)[l.length()]) / 2f : 0f);
                c.drawText(l, left, y0 + li * lineH + ascent, paint);
            }
            return;
        }
        int k = 0;
        for (int li = 0; li < lines.size(); li++) {
            String l = lines.get(li);
            float[] x = xs.get(li);
            float left = getPaddingLeft() + (centered ? (avail - x[l.length()]) / 2f : 0f);
            float top = y0 + li * lineH, base = top + ascent;
            c.save();

            c.clipRect(0, top - lineH * 0.2f, getWidth(), top + lineH);
            for (int i = 0; i < l.length(); i++, k++) {
                char ch = l.charAt(i);
                if (ch == ' ') continue;
                float p = Motion.clamp01((t - k * STAGGER_MS) / CHAR_MS);
                if (p <= 0f) continue;
                float e = Motion.EXPO_OUT.getInterpolation(p);
                float dy = (1f - e) * lineH;
                float cx = left + (x[i] + x[i + 1]) / 2f;
                paint.setAlpha(Math.round(255 * Math.min(1f, p * 2.5f)));
                if (e < 1f) {
                    c.save();
                    c.rotate(14f * (1f - e), cx, base + dy);
                    c.drawText(l, i, i + 1, left + x[i], base + dy, paint);
                    c.restore();
                } else {
                    c.drawText(l, i, i + 1, left + x[i], base, paint);
                }
            }
            k++;
            c.restore();
        }
        paint.setAlpha(255);
    }
}
