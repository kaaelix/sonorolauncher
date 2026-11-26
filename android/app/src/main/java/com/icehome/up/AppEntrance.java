package com.icehome.up;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.util.ArrayList;

public final class AppEntrance {

    static final String TILE_ROW = "tileRow";

    private static final float FALL_MS = 300f;
    private static final float WAVE_MS = 1050f;
    private static final float BAR_AT = 950f;
    private static final float BAR_MS = 1100f;

    private static final class Item {
        final View v;
        final float at;
        final boolean tile;

        Item(View v, float at, boolean tile) {
            this.v = v;
            this.at = at;
            this.tile = tile;
        }
    }

    private AppEntrance() {
    }

    public static void play(final FrameLayout root, final ViewGroup page, final View bar, final float dp) {
        if (!ValueAnimator.areAnimatorsEnabled() || page.getChildCount() == 0) return;
        final ViewGroup list = (ViewGroup) page.getChildAt(0);

        page.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                page.getViewTreeObserver().removeOnPreDrawListener(this);
                start(root, page, list, bar, dp);
                return true;
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private static void start(final FrameLayout root, final ViewGroup page, ViewGroup list, final View bar, final float dp) {

        View title = null;
        for (int i = 0; i < list.getChildCount() && title == null; i++) {
            if (list.getChildAt(i) instanceof SplitTitle) title = list.getChildAt(i);
        }
        final float ox, oy;
        if (title != null) {
            ox = title.getLeft() + title.getWidth() * 0.3f;
            oy = title.getTop() + title.getHeight() * 0.8f;
        } else {
            ox = page.getWidth() / 2f;
            oy = 0f;
        }
        final float maxR = (float) Math.hypot(Math.max(ox, page.getWidth() - ox), page.getHeight() - oy) + 90f * dp;

        final ArrayList<Item> items = new ArrayList<>();
        collect(list, list, title, ox, oy, maxR, items);
        float last = 0f;
        for (Item it : items) last = Math.max(last, it.at);
        final float total = Math.max(BAR_AT + BAR_MS, last + 900f);

        final LiveBackground bg = page.getBackground() instanceof LiveBackground ? (LiveBackground) page.getBackground() : null;
        final View titleView = title;
        final float barDrop = 140f * dp, rise = 26f * dp;

        final ValueAnimator clock = ValueAnimator.ofFloat(0f, total);
        clock.setDuration((long) total);
        clock.setInterpolator(null);

        final View skip = new View(root.getContext());
        skip.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) clock.end();
                return true;
            }
        });
        root.addView(skip, new FrameLayout.LayoutParams(-1, -1));

        clock.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                frame((Float) a.getAnimatedValue(), titleView, items, bg, bar, ox, oy, maxR, barDrop, rise);
            }
        });
        clock.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {

                frame(1e6f, titleView, items, bg, bar, ox, oy, maxR, barDrop, rise);
                root.removeView(skip);
            }
        });
        frame(0f, titleView, items, bg, bar, ox, oy, maxR, barDrop, rise);
        clock.start();
    }

    private static void collect(ViewGroup g, ViewGroup list, View title, float ox, float oy, float maxR, ArrayList<Item> out) {
        boolean tiles = TILE_ROW.equals(g.getTag()) || hasRows(g);
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (c == title || c.getVisibility() != View.VISIBLE || Motion.PINNED.equals(c.getTag())) continue;

            boolean column = g == list && c instanceof LinearLayout
                    && ((LinearLayout) c).getOrientation() == LinearLayout.VERTICAL && c.getTag() == null;
            if (c instanceof ViewGroup && (column || TILE_ROW.equals(c.getTag()) || hasRows((ViewGroup) c))) {
                collect((ViewGroup) c, list, title, ox, oy, maxR, out);
                continue;
            }
            float[] xy = posIn(c, list);
            float d = (float) Math.hypot(xy[0] + c.getWidth() / 2f - ox, xy[1] - oy);
            float p = 1f - (float) Math.sqrt(Math.max(0f, 1f - Math.min(1f, d / maxR)));
            out.add(new Item(c, FALL_MS + p * WAVE_MS - 80f, tiles));
        }
    }

    private static boolean hasRows(ViewGroup g) {
        for (int i = 0; i < g.getChildCount(); i++) if (TILE_ROW.equals(g.getChildAt(i).getTag())) return true;
        return false;
    }

    private static float[] posIn(View v, ViewGroup list) {
        float x = 0f, y = 0f;
        View cur = v;
        while (cur != null && cur != list) {
            x += cur.getLeft();
            y += cur.getTop();
            cur = cur.getParent() instanceof View ? (View) cur.getParent() : null;
        }
        return new float[]{x, y};
    }

    private static void frame(float t, View title, ArrayList<Item> items, LiveBackground bg, View bar,
                              float ox, float oy, float maxR, float barDrop, float rise) {

        if (title != null) {
            float f = Motion.clamp01(t / FALL_MS);
            float h = title.getHeight();
            title.setAlpha(Math.min(1f, f * 3f));
            title.setTranslationY(-(h * 1.8f + 40f) * (1f - f * f));
            float r = Motion.rubber(t - FALL_MS);
            title.setPivotX(title.getWidth() * 0.3f);
            title.setPivotY(h * 0.85f);
            title.setScaleY(1f - 0.30f * r);
            title.setScaleX(1f + 0.16f * r);
        }

        if (bg != null) {
            float p = Motion.clamp01((t - FALL_MS) / WAVE_MS);
            float radius = maxR * (1f - (1f - p) * (1f - p));
            float alpha = t < FALL_MS ? 0f : 0.9f * (1f - Motion.seg(p, 0.55f, 1f));
            bg.wave(ox, oy, radius, alpha);
        }

        for (Item it : items) {
            float lt = t - it.at;
            View v = it.v;
            if (lt <= 0f) {
                v.setAlpha(0f);
                v.setTranslationY(rise);
                continue;
            }
            float e = Motion.POWER4_OUT.getInterpolation(Motion.clamp01(lt / 620f));
            v.setAlpha(Math.min(1f, lt / 240f));
            v.setTranslationY(rise * (1f - e));
            if (it.tile) {
                float r = Motion.rubber(lt - 150f);
                float s = Motion.lerp(0.9f, 1f, e);
                v.setScaleX(s * (1f + 0.06f * r));
                v.setScaleY(s * (1f - 0.08f * r));
            }
        }

        if (bar != null) {
            float s = Motion.spring((t - BAR_AT) / 1000f, 0.6f, 0.7f);
            bar.setTranslationY(t >= 1e5f ? 0f : barDrop * (1f - s));
            bar.invalidate();
        }
    }
}
