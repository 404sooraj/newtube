package com.newtube.mobile.ui.common;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;

import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * "Liquid glass" for a rounded host view: what is under it is re-rendered through a blur, a colour
 * boost and (Android 13+) an AGSL lens that bends the backdrop at the rim - the pixels at the edge
 * come from further inside, like a thick glass slab - plus a specular rim light, a tint and a
 * hairline edge. Android 12 gets the blur without the lens; older gets a translucent pill.
 */
final class LiquidGlass {
    private static final String LENS = ""
            + "uniform shader content;\n"
            + "uniform float2 size;\n"
            + "uniform float radius;\n"
            + "uniform float refH;\n"
            + "uniform float refAmt;\n"
            + "half4 main(float2 p) {\n"
            + "  float2 c = size * 0.5;\n"
            + "  float2 q = abs(p - c) - (c - float2(radius));\n"
            + "  float sd = length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - radius;\n"
            + "  float depth = -sd;\n"
            + "  float2 s = p;\n"
            + "  float rim = 0.0;\n"
            + "  if (depth < refH) {\n"
            + "    float t = clamp(1.0 - depth / refH, 0.0, 1.0);\n"
            + "    float2 n = (q.x > 0.0 && q.y > 0.0) ? normalize(q) : (q.x > q.y ? float2(1.0, 0.0) : float2(0.0, 1.0));\n"
            + "    n *= float2(p.x < c.x ? -1.0 : 1.0, p.y < c.y ? -1.0 : 1.0);\n"
            + "    float k = t * t;\n"
            + "    s = p - n * refAmt * k;\n"
            + "    rim = k * clamp(0.5 + 0.5 * dot(n, normalize(float2(-0.6, -0.8))), 0.0, 1.0);\n"
            + "  }\n"
            + "  s = clamp(s, float2(0.5), size - float2(0.5));\n"
            + "  half4 col = content.eval(s);\n"
            + "  col.rgb = clamp(col.rgb + half3(rim * 0.30), half3(0.0), half3(1.0));\n"
            + "  return col;\n"
            + "}\n";

    private final View mHost;
    private final float mDensity;
    private final float mCornerPx;
    private final Paint mBase = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private final int[] mSelfLoc = new int[2];
    private final int[] mSrcLoc = new int[2];
    private final int mBackground;
    @Nullable private RenderNode mNode;
    @Nullable private View mSource;
    private int mEffectW = -1;
    private int mEffectH = -1;

    LiquidGlass(View host, float cornerDp) {
        mHost = host;
        Context context = host.getContext();
        mDensity = context.getResources().getDisplayMetrics().density;
        mCornerPx = cornerDp * mDensity;
        TypedValue tv = new TypedValue();
        context.getTheme().resolveAttribute(R.attr.newtubeColorBackground, tv, true);
        mBackground = tv.data;
        mBase.setColor(mBackground);
        mTint.setColor(context.getColor(R.color.mobile_nav_pill));
        mEdge.setStyle(Paint.Style.STROKE);
        mEdge.setStrokeWidth(mDensity);
        mEdge.setColor(context.getColor(R.color.mobile_nav_edge));
    }

    void setSource(@Nullable View source) {
        mSource = source;
        mHost.invalidate();
    }

    float radius() {
        return Math.min(mCornerPx, Math.min(mHost.getWidth(), mHost.getHeight()) / 2f);
    }

    /** Call from the host's onDraw, before its content. */
    void draw(Canvas canvas) {
        int w = mHost.getWidth();
        int h = mHost.getHeight();
        if (w == 0 || h == 0) {
            return;
        }
        float r = radius();
        mRect.set(0, 0, w, h);
        canvas.drawRoundRect(mRect, r, r, mBase);
        drawBackdrop(canvas, w, h, r);
        canvas.drawRoundRect(mRect, r, r, mTint);
        mRect.inset(0.5f, 0.5f);
        canvas.drawRoundRect(mRect, r, r, mEdge);
    }

    private void drawBackdrop(Canvas canvas, int w, int h, float radius) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || mSource == null
                || !canvas.isHardwareAccelerated()) {
            return;
        }
        if (mNode == null) {
            mNode = new RenderNode("liquid-glass");
        }
        if (w != mEffectW || h != mEffectH) {
            mEffectW = w;
            mEffectH = h;
            mNode.setRenderEffect(buildEffect(w, h, radius));
        }
        mHost.getLocationInWindow(mSelfLoc);
        mSource.getLocationInWindow(mSrcLoc);
        mNode.setPosition(0, 0, w, h);
        Canvas rec = mNode.beginRecording();
        try {
            rec.drawColor(mBackground);
            rec.translate(mSrcLoc[0] - mSelfLoc[0], mSrcLoc[1] - mSelfLoc[1]);
            mSource.draw(rec);
        } finally {
            mNode.endRecording();
        }
        canvas.drawRenderNode(mNode);
    }

    private RenderEffect buildEffect(int w, int h, float radius) {
        ColorMatrix sat = new ColorMatrix();
        sat.setSaturation(1.7f);
        float blur = 9f * mDensity;
        RenderEffect base = RenderEffect.createColorFilterEffect(new ColorMatrixColorFilter(sat),
                RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP));
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return base;
        }
        RuntimeShader shader = new RuntimeShader(LENS);
        shader.setFloatUniform("size", (float) w, (float) h);
        shader.setFloatUniform("radius", radius);
        shader.setFloatUniform("refH", Math.min(h * 0.5f, 18f * mDensity));
        shader.setFloatUniform("refAmt", 22f * mDensity);
        return RenderEffect.createChainEffect(
                RenderEffect.createRuntimeShaderEffect(shader, "content"), base);
    }
}
