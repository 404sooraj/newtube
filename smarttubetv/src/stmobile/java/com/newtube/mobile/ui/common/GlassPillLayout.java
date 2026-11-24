package com.newtube.mobile.ui.common;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** A horizontal pill of buttons (Browse's cast + search) drawn as liquid glass over the feed. */
public class GlassPillLayout extends LinearLayout implements GlassSurface {
    private final LiquidGlass mGlass;

    public GlassPillLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mGlass = new LiquidGlass(this, 32f);
        setWillNotDraw(false);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), mGlass.radius());
            }
        });
        setClipToOutline(true);
    }

    @Override
    public void setBackdropSource(@Nullable View source) {
        mGlass.setSource(source);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        mGlass.draw(canvas);
    }
}
