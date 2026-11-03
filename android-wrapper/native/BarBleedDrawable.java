package com.lifemax.rpg;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/**
 * The app's background behind the page: one colour everywhere, except the gesture area at the bottom of the screen, which
 * is painted the bottom bar's colour. The page ends above the gesture area (the window pads it by the system insets), so a
 * bar of its own colour would otherwise stand above a strip of the page's background. With this behind it the bar looks
 * flush with the screen's bottom edge. The native bars do the same for themselves (app/shell/bar).
 */
final class BarBleedDrawable extends Drawable {
    private static volatile int inset;
    private final Paint page = new Paint();
    private final Paint bar = new Paint();
    private final boolean same;

    BarBleedDrawable(int pageColor, int barColor) {
        page.setColor(pageColor);
        bar.setColor(barColor);
        same = pageColor == barColor;
    }

    /** The gesture area's height in px (0 while the keyboard is up); the owner of the window tells it on every inset change. */
    static void inset(int px) {
        inset = Math.max(0, px);
    }

    @Override
    public void draw(Canvas canvas) {
        android.graphics.Rect b = getBounds();
        canvas.drawRect(b, page);
        int h = inset;
        if (!same && h > 0) canvas.drawRect(b.left, b.bottom - h, b.right, b.bottom, bar);
    }

    @Override public void setAlpha(int alpha) { }
    @Override public void setColorFilter(ColorFilter filter) { }
    @Override public int getOpacity() { return PixelFormat.OPAQUE; }
}
