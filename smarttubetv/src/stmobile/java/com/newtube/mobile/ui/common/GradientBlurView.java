package com.newtube.mobile.ui.common;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Progressive ("gradient") blur strip: the feed behind it is fully blurred at the screen edge and
 * eases out to sharp towards the content, so rows dissolve under the top bar / tab bar instead of
 * being cut by a hard edge. Draws no solid bar and no tint.
 */
public class GradientBlurView extends View implements GlassSurface {
    private final Paint mMask = new Paint();
    private final int mBackground;
    private final boolean mFromTop;
    private final int[] mSelfLoc = new int[2];
    private final int[] mSrcLoc = new int[2];
    @Nullable private RenderNode mNode;
    @Nullable private View mSource;
    private int mMaskH = -1;

    public GradientBlurView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        android.content.res.TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.GradientBlurView);
        mFromTop = a.getBoolean(R.styleable.GradientBlurView_blurFromTop, true);
        a.recycle();
        TypedValue tv = new TypedValue();
        context.getTheme().resolveAttribute(R.attr.newtubeColorBackground, tv, true);
        mBackground = tv.data;
        mMask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        setWillNotDraw(false);
    }

    @Override
    public void setBackdropSource(@Nullable View source) {
        mSource = source;
        invalidate();
    }

    private void buildMask(int h) {
        // Eased falloff: solid blur at the edge, nothing where the strip meets the content.
        int[] colors = {0xFF000000, 0xF2000000, 0xC0000000, 0x70000000, 0x25000000, 0x00000000};
        float[] stops = {0f, 0.35f, 0.55f, 0.75f, 0.9f, 1f};
        mMask.setShader(mFromTop
                ? new LinearGradient(0, 0, 0, h, colors, stops, Shader.TileMode.CLAMP)
                : new LinearGradient(0, h, 0, 0, colors, stops, Shader.TileMode.CLAMP));
        mMaskH = h;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0 || mSource == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || !canvas.isHardwareAccelerated()) {
            return;
        }
        if (h != mMaskH) {
            buildMask(h);
        }
        if (mNode == null) {
            mNode = new RenderNode("gradient-blur");
            float blur = 14f * getResources().getDisplayMetrics().density;
            mNode.setRenderEffect(RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP));
        }
        getLocationInWindow(mSelfLoc);
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
        int layer = canvas.saveLayer(0, 0, w, h, null);
        canvas.drawRenderNode(mNode);
        canvas.drawRect(0, 0, w, h, mMask);
        canvas.restoreToCount(layer);
    }
}
