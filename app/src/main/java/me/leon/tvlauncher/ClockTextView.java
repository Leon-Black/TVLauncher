package me.leon.tvlauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.text.Layout;
import android.text.TextPaint;
import android.widget.TextView;

/** Adds a fine shadow while retaining native text layout and the main TextView shadow. */
final class ClockTextView extends TextView {
    private final float detailRadius, detailDy;

    ClockTextView(Context context, float detailRadius, float detailDy) {
        super(context);
        this.detailRadius = detailRadius;
        this.detailDy = detailDy;
    }

    @Override protected void onDraw(Canvas canvas) {
        Layout layout = getLayout();
        if (layout != null) {
            TextPaint paint = getPaint();
            int color = paint.getColor();
            int save = canvas.save();
            canvas.translate(getCompoundPaddingLeft(), getBaseline() - layout.getLineBaseline(0));
            paint.setColor(Color.TRANSPARENT);
            paint.setShadowLayer(detailRadius, 0, detailDy, 0x1f000000);
            layout.draw(canvas);
            canvas.restoreToCount(save);
            paint.setColor(color);
            paint.setShadowLayer(getShadowRadius(), getShadowDx(), getShadowDy(), getShadowColor());
        }
        super.onDraw(canvas);
    }
}
