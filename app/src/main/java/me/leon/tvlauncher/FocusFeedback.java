package me.leon.tvlauncher;

import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Shared cover/button feedback without changing layout geometry or activation. */
final class FocusFeedback {
    static void update(View target, boolean focused, boolean pressed, long duration) {
        float scale = pressed ? 1.04f : focused ? 1.1f : 1f;
        update(target, scale, duration);
    }

    static void update(View target, float scale, long duration) {
        target.animate().cancel();
        if (duration > 0) {
            target.animate().scaleX(scale).scaleY(scale).setDuration(duration)
                    .setInterpolator(new DecelerateInterpolator()).start();
        } else {
            target.setScaleX(scale);
            target.setScaleY(scale);
        }
    }
}
