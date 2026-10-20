package me.leon.tvlauncher;

final class ScrollBounds {
    static int target(int current, int low, int high, int viewport,
                      int before, int after, int content) {
        return target(current, low, high, viewport, before, after, content, false);
    }

    static int target(int current, int low, int high, int viewport,
                      int before, int after, int content, boolean restoreStart) {
        if (restoreStart) return 0;
        int target = current;
        if (low < current + before) target = low - before;
        else if (high > current + viewport - after) target = high - viewport + after;
        return Math.max(0, Math.min(target, Math.max(0, content + before + after - viewport)));
    }
}
