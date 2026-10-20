package me.leon.tvlauncher;

import android.animation.ValueAnimator;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

/** Native focus reveal ignores content padding and cover scale on Android 10. */
final class FocusScrollMotion {
    private final ViewGroup scroll;
    private final boolean horizontal;
    private ValueAnimator animation;

    FocusScrollMotion(ViewGroup scroll, boolean horizontal) {
        this.scroll = scroll;
        this.horizontal = horizontal;
    }

    void reveal(View focused) {
        reveal(focused, false);
    }

    void reveal(View focused, boolean restoreStart) {
        if (focused == null || focused == scroll || focused.getWidth() == 0
                || scroll.getWidth() == 0 || scroll.getChildCount() == 0) return;
        Rect bounds = new Rect();
        focused.getDrawingRect(bounds);
        scroll.offsetDescendantRectToMyCoords(focused, bounds);
        if (!horizontal) {
            View cover = focused.findViewWithTag("cover");
            if (cover != null) bounds.top -= Math.round(cover.getHeight() * .05f) + 1;
        }
        int current = horizontal ? scroll.getScrollX() : scroll.getScrollY();
        int destination = ScrollBounds.target(current, horizontal ? bounds.left : bounds.top,
                horizontal ? bounds.right : bounds.bottom,
                horizontal ? scroll.getWidth() : scroll.getHeight(),
                horizontal ? scroll.getPaddingLeft() : scroll.getPaddingTop(),
                horizontal ? scroll.getPaddingRight() : scroll.getPaddingBottom(),
                horizontal ? scroll.getChildAt(0).getWidth() : scroll.getChildAt(0).getHeight(), restoreStart);
        cancel();
        if (current == destination) return;
        animation = ValueAnimator.ofInt(current, destination);
        animation.setDuration(180);
        animation.setInterpolator(new DecelerateInterpolator());
        animation.addUpdateListener(value -> {
            int position = (int) value.getAnimatedValue();
            scroll.scrollTo(horizontal ? position : 0, horizontal ? 0 : position);
        });
        animation.start();
    }

    void cancel() { if (animation != null) animation.cancel(); }
}
