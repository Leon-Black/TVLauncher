package me.leon.tvlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.view.View;

/** Maps every surface to the same wallpaper coordinates, including the bottom Dock. */
final class WallpaperBackdropView extends View {
    private final View screen;
    private final int tint;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final int[] origin = new int[2], location = new int[2];
    private Bitmap bitmap;

    WallpaperBackdropView(Context context, View screen, Bitmap bitmap, int tint) {
        super(context);
        this.screen = screen;
        this.bitmap = bitmap;
        this.tint = tint;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setBitmap(Bitmap bitmap) { this.bitmap = bitmap; invalidate(); }

    @Override protected void onDraw(Canvas canvas) {
        canvas.drawColor(Color.GRAY);
        if (bitmap != null && screen.getWidth() > 0 && screen.getHeight() > 0) {
            screen.getLocationInWindow(origin);
            getLocationInWindow(location);
            int save = canvas.save();
            canvas.translate(origin[0] - location[0], origin[1] - location[1]);
            drawCover(canvas, bitmap, screen.getWidth(), screen.getHeight(), paint);
            canvas.restoreToCount(save);
        }
        canvas.drawColor(tint);
    }

    static Bitmap blur(Context context, Bitmap source, int width, int height) {
        return blur(context, source, width, height, 32);
    }

    @SuppressWarnings("deprecation") // Native Android 10 API; RenderEffect requires API 31.
    static Bitmap blur(Context context, Bitmap source, int width, int height, float radius) {
        int w = Math.max(1, Math.min(480, width));
        int h = Math.max(1, Math.round(height * (w / (float) width)));
        Bitmap small = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(small);
        canvas.drawColor(Color.GRAY);
        drawCover(canvas, source, w, h, new Paint(Paint.FILTER_BITMAP_FLAG));
        RenderScript runtime = null;
        Allocation input = null, output = null;
        ScriptIntrinsicBlur blur = null;
        try {
            runtime = RenderScript.create(context);
            input = Allocation.createFromBitmap(runtime, small);
            output = Allocation.createTyped(runtime, input.getType());
            blur = ScriptIntrinsicBlur.create(runtime, Element.U8_4(runtime));
            // Convert the design's reference blur into the downsampled cache.
            blur.setRadius(Math.max(.1f, Math.min(25f, radius * w / 1920f)));
            blur.setInput(input);
            blur.forEach(output);
            output.copyTo(small);
            return small;
        } finally {
            if (blur != null) blur.destroy();
            if (output != null) output.destroy();
            if (input != null) input.destroy();
            if (runtime != null) runtime.destroy();
        }
    }

    private static void drawCover(Canvas canvas, Bitmap bitmap, int width, int height, Paint paint) {
        float scale = Math.max(width / (float) bitmap.getWidth(), height / (float) bitmap.getHeight());
        float w = bitmap.getWidth() * scale, h = bitmap.getHeight() * scale;
        canvas.drawBitmap(bitmap, null, new RectF((width - w) / 2, (height - h) / 2,
                (width + w) / 2, (height + h) / 2), paint);
    }
}
