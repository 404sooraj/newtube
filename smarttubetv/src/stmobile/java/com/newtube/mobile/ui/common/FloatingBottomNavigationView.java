package com.newtube.mobile.ui.common;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewOutlineProvider;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Momentum-style floating tab bar: a rounded pill hovering over the content with a "bubble" behind
 * the selected tab. The bubble glides between tabs, and a press-and-drag across the bar carries it
 * under the finger with a tick at every tab boundary; releasing selects the tab it settled on.
 * Taps are left to the stock item views, so everything the activity wires on them still works.
 */
public class FloatingBottomNavigationView extends BottomNavigationView implements GlassSurface {

    private static final float PRESSED_SCALE = 1.12f;

    private final Paint mBubbleEdgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final LiquidGlass mGlass;
    private final Paint mBubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mBubble = new RectF();
    private final float mBubbleInset;
    private final float mBubbleRadius;
    private final int mTouchSlop;

    /** Bubble position in tab units (0 .. n-1), fractional while moving. */
    private float mPos = -1f;
    private float mTarget = -1f;
    private float mScale = 1f;
    private ValueAnimator mGlide;
    private boolean mDragging;
    private float mDownX;
    private int mLastTick = -1;

    public FloatingBottomNavigationView(@NonNull Context context) {
        this(context, null);
    }

    public FloatingBottomNavigationView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mGlass = new LiquidGlass(this, 36f);
        float d = context.getResources().getDisplayMetrics().density;
        mBubbleInset = 5f * d;
        mBubbleRadius = 32f * d;
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mBubblePaint.setColor(context.getColor(R.color.mobile_nav_bubble));
        mBubbleEdgePaint.setStyle(Paint.Style.STROKE);
        mBubbleEdgePaint.setStrokeWidth(d);
        mBubbleEdgePaint.setColor(context.getColor(R.color.mobile_nav_edge));
        setBackground(null); // Material installs its own; the glass pill is drawn in onDraw
        setWillNotDraw(false);
        setItemActiveIndicatorEnabled(false);
        // Material pads the bar by the gesture inset; this pill floats ABOVE it (the activity turns the
        // inset into a margin), so that padding only pushed the icons up inside the pill.
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(this, (v, insets) -> insets);
        setPadding(0, 0, 0, 0);
        final float radius = 36f * d;
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        setClipToOutline(true);
    }

    /** The view whose pixels scroll under the pill; re-rendered as liquid glass behind the bar. */
    @Override
    public void setBackdropSource(@Nullable View source) {
        mGlass.setSource(source);
    }

    private int tabCount() {
        return getMenu().size();
    }

    private int selectedIndex() {
        int id = getSelectedItemId();
        for (int i = 0; i < tabCount(); i++) {
            if (getMenu().getItem(i).getItemId() == id) {
                return i;
            }
        }
        return -1;
    }

    private float tabWidth() {
        int n = tabCount();
        return n == 0 ? 0f : (getWidth() - getPaddingLeft() - getPaddingRight()) / (float) n;
    }

    private void glideTo(float target) {
        mTarget = target;
        if (mGlide != null) {
            mGlide.cancel();
        }
        if (mPos < 0f) { // first layout: no glide from nowhere
            mPos = target;
            invalidate();
            return;
        }
        mGlide = ValueAnimator.ofFloat(mPos, target);
        mGlide.setDuration(320);
        mGlide.setInterpolator(new OvershootInterpolator(0.9f));
        mGlide.addUpdateListener(a -> {
            mPos = (float) a.getAnimatedValue();
            invalidate();
        });
        mGlide.start();
    }

    private void animateScale(float to) {
        ValueAnimator s = ValueAnimator.ofFloat(mScale, to);
        s.setDuration(180);
        s.addUpdateListener(a -> {
            mScale = (float) a.getAnimatedValue();
            invalidate();
        });
        s.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        mGlass.draw(canvas);
        int n = tabCount();
        if (n == 0) {
            return;
        }
        if (!mDragging) {
            int sel = selectedIndex();
            if (sel >= 0 && sel != mTarget) {
                glideTo(sel);
            }
        }
        if (mPos < 0f) {
            return;
        }
        float w = tabWidth();
        float left = getPaddingLeft() + mPos * w;
        mBubble.set(left + mBubbleInset, mBubbleInset, left + w - mBubbleInset, getHeight() - mBubbleInset);
        canvas.save();
        canvas.scale(mScale, mScale, mBubble.centerX(), mBubble.centerY());
        canvas.drawRoundRect(mBubble, mBubbleRadius, mBubbleRadius, mBubblePaint);
        canvas.drawRoundRect(mBubble, mBubbleRadius, mBubbleRadius, mBubbleEdgePaint);
        canvas.restore();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownX = ev.getX();
                mDragging = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (!mDragging && Math.abs(ev.getX() - mDownX) > mTouchSlop && tabCount() > 1) {
                    beginDrag(ev.getX());
                    return true;
                }
                break;
            default:
                break;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!mDragging) {
            return super.onTouchEvent(ev);
        }
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                follow(ev.getX());
                return true;
            case MotionEvent.ACTION_UP:
                endDrag(true);
                return true;
            case MotionEvent.ACTION_CANCEL:
                endDrag(false);
                return true;
            default:
                return true;
        }
    }

    private void beginDrag(float x) {
        mDragging = true;
        getParent().requestDisallowInterceptTouchEvent(true);
        mLastTick = Math.round(fraction(x));
        animateScale(PRESSED_SCALE);
        follow(x);
    }

    private float fraction(float x) {
        float w = tabWidth();
        if (w <= 0f) {
            return 0f;
        }
        float f = (x - getPaddingLeft()) / w - 0.5f;
        return Math.max(0f, Math.min(tabCount() - 1f, f));
    }

    private void follow(float x) {
        float f = fraction(x);
        if (mGlide != null) {
            mGlide.cancel();
        }
        mPos = f;
        mTarget = f;
        int idx = Math.round(f);
        if (idx != mLastTick) {
            mLastTick = idx;
            Haptics.tick(this);
        }
        invalidate();
    }

    private void endDrag(boolean select) {
        mDragging = false;
        animateScale(1f);
        int idx = Math.round(Math.max(0f, Math.min(tabCount() - 1f, mPos)));
        if (select && tabCount() > 0) {
            int id = getMenu().getItem(idx).getItemId();
            if (id != getSelectedItemId()) {
                setSelectedItemId(id);
            }
        }
        glideTo(idx);
    }
}
