package me.leon.tvlauncher;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;

/** The entire surface clips once; padding sets content edges and focus reveal alignment. */
final class GutterScrollFrame extends FrameLayout {
    final HorizontalScrollView scroll;

    GutterScrollFrame(Context context, int gutter, int top, int bottom) {
        super(context);
        setClipChildren(true);
        scroll = new RevealHorizontalScrollView(context);
        scroll.setPadding(gutter, top, gutter, bottom);
        scroll.setClipToPadding(false);
        scroll.setClipChildren(false);
        scroll.setFocusable(false);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setHorizontalFadingEdgeEnabled(false);
        scroll.setFadingEdgeLength(0);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setSmoothScrollingEnabled(true);
        addView(scroll, new LayoutParams(-1, -1));
    }

    private static final class RevealHorizontalScrollView extends HorizontalScrollView {
        private final FocusScrollMotion motion = new FocusScrollMotion(this, true);

        RevealHorizontalScrollView(Context context) { super(context); }

        @Override public void requestChildFocus(View child, View focused) {
            super.requestChildFocus(child, focused);
            motion.reveal(focused);
        }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            super.onLayout(changed, l, t, r, b);
            motion.reveal(findFocus());
        }

        @Override protected void onDetachedFromWindow() {
            motion.cancel();
            super.onDetachedFromWindow();
        }
    }
}
