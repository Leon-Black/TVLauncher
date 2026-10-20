package me.leon.tvlauncher;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;

/** Native drawable states with a fixed 80px layout slot and independent visual feedback. */
final class ActionButton extends FrameLayout {
    static final int NORMAL = 0x1a000000, HOVERED = Color.WHITE, PRESSED = Color.WHITE,
            ACTIVE = 0xff007aff;
    private final ButtonShadow shadow;
    private boolean initialized, clickPending;
    private Runnable action;
    private final Runnable clickAction = () -> {
        clickPending = false;
        if (action != null) action.run();
    };

    ActionButton(Context context, WallpaperBackdropView backdrop, int icon, float unit) {
        super(context);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setClickable(true);
        setDefaultFocusHighlightEnabled(false);
        setClipChildren(false);
        setClipToPadding(false);
        shadow = new ButtonShadow(context, unit);
        shadow.setTag("action-shadow");
        int padding = Math.round(24 * unit);
        LayoutParams shadowParams = new LayoutParams(Math.round(80 * unit) + 2 * padding,
                Math.round(80 * unit) + 2 * padding);
        shadowParams.leftMargin = shadowParams.topMargin = -padding;
        addView(shadow, shadowParams);
        backdrop.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        backdrop.setClipToOutline(true);
        addView(backdrop, new LayoutParams(-1, -1));
        View shade = new View(context);
        shade.setTag("action-fill");
        shade.setDuplicateParentStateEnabled(true);
        StateListDrawable fill = new StateListDrawable();
        int[][] states = {{android.R.attr.state_pressed}, {android.R.attr.state_activated},
                {android.R.attr.state_focused}, {android.R.attr.state_hovered}, {}};
        int[] colors = {PRESSED, ACTIVE, HOVERED, HOVERED, NORMAL};
        for (int i = 0; i < states.length; i++) fill.addState(states[i], shape(colors[i], 40 * unit));
        shade.setBackground(fill);
        addView(shade, new LayoutParams(-1, -1));
        ImageView image = new ImageView(context);
        image.setTag("action-icon");
        image.setImageResource(icon);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        image.setDuplicateParentStateEnabled(true);
        image.setImageTintList(new ColorStateList(states,
                new int[]{Color.BLACK, Color.WHITE, Color.BLACK, Color.BLACK, Color.WHITE}));
        int size = Math.round(48 * unit);
        addView(image, new LayoutParams(size, size, Gravity.CENTER));
        setOnClickListener(view -> {
            if (!clickPending) {
                clickPending = true;
                postDelayed(clickAction, 120);
            }
        });
        initialized = true;
        updateFeedback(0);
    }

    void setAction(Runnable action) { this.action = action; }

    @Override public void setPressed(boolean pressed) {
        boolean changed = pressed != isPressed();
        super.setPressed(pressed);
        if (initialized && changed) updateFeedback(pressed ? 70 : 110);
    }

    @Override protected void onFocusChanged(boolean focused, int direction, Rect previous) {
        super.onFocusChanged(focused, direction, previous);
        if (initialized) updateFeedback(160);
    }

    @Override public void setActivated(boolean activated) {
        boolean changed = activated != isActivated();
        super.setActivated(activated);
        if (initialized && changed) updateFeedback(160);
    }

    @Override public void setHovered(boolean hovered) {
        boolean changed = hovered != isHovered();
        super.setHovered(hovered);
        if (initialized && changed) updateFeedback(160);
    }

    private void updateFeedback(long duration) {
        boolean prominent = isPressed() || isActivated() || hasFocus() || isHovered();
        float scale = isPressed() ? 88f / 80f : prominent ? 96f / 80f : 1f;
        FocusFeedback.update(this, scale, duration);
        shadow.setVisibility(prominent ? VISIBLE : INVISIBLE);
        shadow.update(isActivated() && !isPressed() ? ACTIVE : HOVERED, scale);
    }

    @Override public boolean onHoverEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_HOVER_ENTER) requestFocus();
        return super.onHoverEvent(event);
    }

    void resetPress() {
        removeCallbacks(clickAction);
        clickPending = false;
        super.setPressed(false);
        updateFeedback(0);
    }

    @Override protected void onDetachedFromWindow() {
        resetPress();
        super.onDetachedFromWindow();
    }

    static StateListDrawable background(float radius) {
        // Keep the gallery status control at its existing 20 percent opacity.
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_activated}, shape(ACTIVE, radius));
        states.addState(new int[]{android.R.attr.state_pressed}, shape(0x66000000, radius));
        states.addState(new int[]{}, shape(0x33000000, radius));
        return states;
    }

    private static GradientDrawable shape(int color, float radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(radius);
        shape.setColor(color);
        return shape;
    }

    private static final class ButtonShadow extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float unit;

        ButtonShadow(Context context, float unit) {
            super(context);
            this.unit = unit;
            setLayerType(LAYER_TYPE_SOFTWARE, null);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        void update(int color, float scale) {
            paint.setColor(color);
            // Compensate for the parent's scale so the visible shadow remains 0/4/16.
            paint.setShadowLayer(16 * unit / scale, 0, 4 * unit / scale, 0x33000000);
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, 40 * unit, paint);
        }
    }
}
