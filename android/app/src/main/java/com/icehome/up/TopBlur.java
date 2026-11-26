package com.icehome.up;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RecordingCanvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;

public final class TopBlur extends View {

    private static final String VBLUR =
            "uniform shader content;\n"
            + "uniform float2 size;\n"
            + "uniform float2 dir;\n"
            + "uniform float maxSigma;\n"
            + "half4 sampleAt(float2 p) {\n"

            + "  return content.eval(clamp(p, float2(0.5), size - float2(0.5)));\n"
            + "}\n"
            + "half4 main(float2 coord) {\n"
            + "  float f = clamp(1.0 - coord.y / size.y, 0.0, 1.0);\n"
            + "  float s = maxSigma * f * f * (3.0 - 2.0 * f);\n"
            + "  if (s < 0.4) return sampleAt(coord);\n"

            + "  float stp = s * 0.38;\n"
            + "  half4 acc = sampleAt(coord);\n"
            + "  float wsum = 1.0;\n"
            + "  for (int i = 1; i <= 8; i++) {\n"
            + "    float x = float(i) * stp;\n"
            + "    float w = exp(-0.5 * x * x / (s * s));\n"
            + "    acc += (sampleAt(coord + dir * x) + sampleAt(coord - dir * x)) * half(w);\n"
            + "    wsum += 2.0 * w;\n"
            + "  }\n"
            + "  return acc / half(wsum);\n"
            + "}\n";

    private static final float SCALE = 0.35f;
    private final float dp;
    private final int bg;
    private final Paint veil = new Paint();
    private View source;
    private RenderNode node;
    private RuntimeShader hPass, vPass;
    private int effectW = -1, effectH = -1;
    private final int[] myLoc = new int[2], srcLoc = new int[2];

    public TopBlur(Context c, int bg) {
        super(c);
        this.bg = bg;
        dp = c.getResources().getDisplayMetrics().density;
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                hPass = new RuntimeShader(VBLUR);
                vPass = new RuntimeShader(VBLUR);
                hPass.setFloatUniform("dir", 1f, 0f);
                vPass.setFloatUniform("dir", 0f, 1f);
                node = new RenderNode("topBlur");
            } catch (Throwable t) {
                android.util.Log.w("Sonoro", "TopBlur shader failed", t);
                hPass = vPass = null;
                node = null;
            }
        }
    }

    public void setSource(View v) {
        source = v;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {

        boolean blur = node != null;
        veil.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{(bg & 0x00FFFFFF) | (blur ? 0x8C000000 : 0xF2000000),
                        (bg & 0x00FFFFFF) | (blur ? 0x40000000 : 0xB3000000), bg & 0x00FFFFFF},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        effectW = effectH = -1;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        if (node != null && source != null && canvas.isHardwareAccelerated() && source.getVisibility() == VISIBLE) {
            try {
                int rw = Math.max(1, Math.round(w * SCALE));
                int rh = Math.max(1, Math.round(h * SCALE));
                getLocationInWindow(myLoc);
                source.getLocationInWindow(srcLoc);
                node.setPosition(0, 0, rw, rh);
                RecordingCanvas rc = node.beginRecording(rw, rh);
                rc.scale(SCALE, SCALE);
                rc.translate(srcLoc[0] - myLoc[0] - source.getScrollX(), srcLoc[1] - myLoc[1] - source.getScrollY());
                source.draw(rc);
                node.endRecording();
                if (rw != effectW || rh != effectH) {
                    float sigma = 11f * dp * SCALE;
                    hPass.setFloatUniform("size", rw, rh);
                    hPass.setFloatUniform("maxSigma", sigma);
                    vPass.setFloatUniform("size", rw, rh);
                    vPass.setFloatUniform("maxSigma", sigma);
                    node.setRenderEffect(RenderEffect.createChainEffect(
                            RenderEffect.createRuntimeShaderEffect(vPass, "content"),
                            RenderEffect.createRuntimeShaderEffect(hPass, "content")));
                    effectW = rw;
                    effectH = rh;
                }
                canvas.save();
                canvas.scale(1f / SCALE, 1f / SCALE);
                canvas.drawRenderNode(node);
                canvas.restore();
            } catch (Throwable t) {
                android.util.Log.w("Sonoro", "TopBlur disabled", t);
                node = null;
                onSizeChanged(w, h, w, h);
            }
        }
        canvas.drawRect(0, 0, w, h, veil);
    }
}
