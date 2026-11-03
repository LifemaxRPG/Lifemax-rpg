package com.lifemax.rpg.app.shell.bar

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.abs

/**
 * The selected pill drawn on the tab under a scrubbing finger, ahead of the page's own scene: the
 * bar is a drawing of the page's, which only moves its pill once it has switched, a moment behind
 * the finger. While this is up the page's pill (the filled box on the tab it is on, [covered]) is
 * left out and this one is drawn on [over] in its colour, under the tab's icon and label.
 */
internal class ScrubPill(
    private val over: BarTab,
    private val covered: BarTab?,
    private val color: Int,
    private val radius: Float,
) {
    private val rect = RectF(over.left, over.top, over.right, over.bottom)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = this@ScrubPill.color }

    /** Whether a filled box of the scene at ([x], [y], [w], [h]) is the page's pill on its tab. */
    fun hides(x: Float, y: Float, w: Float, h: Float, fill: Int): Boolean =
        covered != null &&
            fill != 0 &&
            abs(x - covered.left) < TOLERANCE &&
            abs(y - covered.top) < TOLERANCE &&
            abs(x + w - covered.right) < TOLERANCE &&
            abs(y + h - covered.bottom) < TOLERANCE

    /**
     * Whether a drawn node at this place belongs to the tab this pill is over (so it comes first).
     */
    fun reaches(x: Float, w: Float): Boolean =
        w < over.right - over.left + TOLERANCE && x + w / 2 in over.left..over.right

    fun draw(canvas: Canvas) {
        canvas.drawRoundRect(rect, radius, radius, paint)
    }

    private companion object {
        const val TOLERANCE = 1f
    }
}
