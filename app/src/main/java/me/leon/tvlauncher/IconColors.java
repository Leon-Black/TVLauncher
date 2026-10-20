package me.leon.tvlauncher;

import android.app.WallpaperColors;
import android.graphics.Bitmap;
import android.graphics.Color;

/** Uses the platform quantizer; prefer a colored accent over white or transparent icon padding. */
final class IconColors {
    static final int FALLBACK = 0xffe5ddd9;

    static int from(Bitmap icon) {
        if (icon == null) return FALLBACK;
        WallpaperColors palette = WallpaperColors.fromBitmap(icon);
        Color primary = palette.getPrimaryColor();
        Color[] candidates = {primary, palette.getSecondaryColor(), palette.getTertiaryColor()};
        float[] hsv = new float[3];
        for (Color candidate : candidates) {
            if (candidate == null) continue;
            Color.colorToHSV(candidate.toArgb(), hsv);
            if (hsv[1] >= .15f && hsv[2] >= .18f) return candidate.toArgb() | 0xff000000;
        }
        return primary.toArgb() | 0xff000000;
    }
}
