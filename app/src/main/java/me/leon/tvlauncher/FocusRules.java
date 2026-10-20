package me.leon.tvlauncher;

import android.view.KeyEvent;

final class FocusRules {
    static int gridNeighbor(int index, int count, int key) {
        if (count == 0) return -1;
        return switch (key) {
            case KeyEvent.KEYCODE_DPAD_LEFT -> index % 4 == 0 ? index : index - 1;
            case KeyEvent.KEYCODE_DPAD_RIGHT -> index % 4 == 3 || index + 1 >= count
                    ? index : index + 1;
            case KeyEvent.KEYCODE_DPAD_UP -> index < 4 ? -1 : index - 4;
            case KeyEvent.KEYCODE_DPAD_DOWN -> index + 4 < count ? index + 4 : index;
            default -> index;
        };
    }

    static boolean isDirection(int key) {
        return key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT
                || key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN;
    }
}
