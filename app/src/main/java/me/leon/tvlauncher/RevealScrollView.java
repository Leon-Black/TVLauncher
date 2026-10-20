package me.leon.tvlauncher;

import android.content.Context;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ScrollView;

final class RevealScrollView extends ScrollView {
    private final FocusScrollMotion motion = new FocusScrollMotion(this, false);

    RevealScrollView(Context context) { super(context); }

    @Override public void requestChildFocus(View child, View focused) {
        super.requestChildFocus(child, focused);
        reveal(focused);
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        reveal(findFocus());
    }

    private void reveal(View focused) {
        boolean firstRow = false;
        if (focused != null && focused.getParent() instanceof GridLayout) {
            GridLayout cells = (GridLayout) focused.getParent();
            int index = cells.indexOfChild(focused);
            firstRow = index >= 0 && index < cells.getColumnCount();
        }
        motion.reveal(focused, firstRow);
    }

    @Override protected void onDetachedFromWindow() {
        motion.cancel();
        super.onDetachedFromWindow();
    }
}
