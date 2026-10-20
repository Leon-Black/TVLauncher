package me.leon.tvlauncher;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/** Only the cover transforms. The name and the layout slot keep their original geometry. */
final class AppCardView extends FrameLayout {
    interface Navigation { void move(AppCardView card, int direction); }
    private final FrameLayout cover;
    private final ImageView image;
    private final boolean picture;
    private Bitmap displayed;
    private final float unit;
    private final Runnable clickAction;
    private boolean clickPending;
    private Navigation navigation;

    AppCardView(Context context, AppCatalog.Entry app, int width, int height,
                boolean showName, float unit) {
        this(context, app.name(), app.icon(), width, height, showName, false, unit);
        ((GradientDrawable) cover.getBackground()).setColor(app.backgroundColor());
    }

    AppCardView(Context context, String name, Bitmap bitmap, int width, int height,
                boolean showName, boolean picture, float unit) {
        super(context);
        this.unit = unit;
        this.picture = picture;
        setFocusable(true);
        setFocusableInTouchMode(true);
        setRevealOnFocusHint(false);
        setClickable(true);
        setClipChildren(false);
        setClipToPadding(false);
        setContentDescription(name);
        setDefaultFocusHighlightEnabled(false);
        cover = new FrameLayout(context);
        cover.setTag("cover");
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(229, 221, 217));
        background.setCornerRadius((picture ? 28 : 40) * unit);
        cover.setBackground(background);
        cover.setClipToOutline(true);
        cover.setElevation(12 * unit);
        if (!picture) setCoverBorder(Math.round(2 * unit), 0x1fffffff);
        addView(cover, new LayoutParams(width, height));

        image = new ImageView(context);
        displayed = bitmap;
        image.setImageBitmap(bitmap);
        image.setScaleType(picture ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
        int iconSize = Math.round(Math.min(height * .55f, 128 * unit));
        cover.addView(image, new LayoutParams(picture ? -1 : iconSize,
                picture ? -1 : iconSize, Gravity.CENTER));

        if (showName) {
            TextView label = new TextView(context);
            label.setTag("name");
            label.setText(name);
            label.setTextSize(TypedValue.COMPLEX_UNIT_PX, 28 * unit);
            label.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            label.setTextColor(Color.WHITE);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setIncludeFontPadding(false);
            label.setShadowLayer(16 * unit, 0, 4 * unit, 0x66000000);
            LayoutParams nameParams = new LayoutParams(width, Math.round(36 * unit));
            nameParams.topMargin = height + Math.round(16 * unit);
            addView(label, nameParams);
        }
        clickAction = () -> {
            clickPending = false;
            AppCardView.super.performClick();
        };
    }

    void setNavigation(Navigation navigation) { this.navigation = navigation; }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (FocusRules.isDirection(keyCode) && navigation != null) {
            navigation.move(this, keyCode);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (FocusRules.isDirection(keyCode)) return true;
        return super.onKeyUp(keyCode, event);
    }

    @Override public void setPressed(boolean pressed) {
        boolean changed = pressed != isPressed();
        super.setPressed(pressed);
        if (cover != null && changed) updateScale(pressed ? 70 : 110);
    }

    @Override public boolean performClick() {
        if (!clickPending) {
            clickPending = true;
            // Let the release rebound finish before the launched app covers this window.
            postDelayed(clickAction, 120);
        }
        return true;
    }

    @Override protected void onFocusChanged(boolean gain, int direction, Rect previous) {
        super.onFocusChanged(gain, direction, previous);
        if (cover != null) updateScale(160);
    }

    @Override public boolean onHoverEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_HOVER_ENTER) requestFocus();
        return super.onHoverEvent(event);
    }

    private void updateScale(long duration) {
        FocusFeedback.update(cover, hasFocus(), isPressed(), duration);
        setElevation(hasFocus() ? 36 * unit : 0);
    }

    void setPicture(Bitmap bitmap) {
        if (displayed == bitmap) return;
        displayed = bitmap;
        image.setImageBitmap(bitmap);
    }

    @Override public void setSelected(boolean selected) {
        super.setSelected(selected);
        if (cover == null || !picture) return;
        setCoverBorder(selected ? Math.round(6 * unit) : 0, 0xff007aff);
    }

    private void setCoverBorder(int stroke, int color) {
        GradientDrawable border = new GradientDrawable();
        border.setColor(Color.TRANSPARENT);
        // GradientDrawable insets the stroke centerline by half its width.
        // Keep its outer edge on the same outline used to clip the cover.
        border.setCornerRadius(Math.max(0, (picture ? 28 : 40) * unit - stroke / 2f));
        if (stroke > 0) border.setStroke(stroke, color);
        cover.setForeground(border);
    }

    void resetPress() {
        removeCallbacks(clickAction);
        clickPending = false;
        super.setPressed(false);
        updateScale(0);
    }

    @Override protected void onDetachedFromWindow() {
        resetPress();
        super.onDetachedFromWindow();
    }

}
