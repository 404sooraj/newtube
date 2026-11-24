package com.newtube.mobile.ui.common;

import android.view.View;

import androidx.annotation.Nullable;

/** A view that paints a glass effect from the pixels of another view (the feed) scrolling under it. */
public interface GlassSurface {
    void setBackdropSource(@Nullable View source);
}
