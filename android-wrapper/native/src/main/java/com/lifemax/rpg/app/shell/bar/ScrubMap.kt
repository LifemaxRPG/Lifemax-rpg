package com.lifemax.rpg.app.shell.bar

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * How fast a finger has to travel along the bottom bar to run through the tabs: the owner's "scrub
 * speed" setting (Settings, Bottom bar). [gain] 1 is the old pace, where the bar's full width
 * reaches the end tab; higher means the same movement passes more tabs. These are the page's
 * LMA_SCRUB_GAINS; a name the page does not know is [Fast], as it is there.
 */
internal enum class ScrubSpeed(val key: String, val gain: Float) {
    Normal("normal", 1f),
    Fast("fast", 1.8f),
    Faster("faster", 2.6f);

    companion object {
        fun of(key: String?): ScrubSpeed = entries.firstOrNull { it.key == key } ?: Fast
    }
}

/**
 * Where a scrubbing finger is on the bar, as a tab: the page's own mapping (42-scrubnav.js), so the
 * bar feels as it did before it was native. The whole bar is the whole scrub. The finger starts on
 * the tab the page is on ([start]); the left edge of the bar is the first tab and the right edge
 * the last, with the stretch on each side of the start spread evenly over the tabs on that side.
 * That is why a swipe across the whole bar reaches the last tab, however far the strip scrolls
 * under it. All positions are in the bar's own width (dp from its left edge).
 */
internal object ScrubMap {
    /** The start of a touch is held this far from either edge, so there is room to go both ways. */
    const val EDGE = 40f

    /** The narrowest bar width the mapping works with. */
    const val MIN_WIDTH = 200f

    /**
     * The index of the tab under a finger at [x] that went down at [downX], on a bar [width] wide
     * with [count] tabs, starting from tab [start], at the speed [gain].
     */
    fun indexAt(x: Float, downX: Float, width: Float, start: Int, count: Int, gain: Float): Int {
        if (count <= 1) return 0
        val w = max(MIN_WIDTH, width)
        val anchor = downX.coerceIn(EDGE, w - EDGE)
        val reach = (anchor + (x - anchor) * gain).coerceIn(0f, w)
        val first = start.coerceIn(0, count - 1)
        val position =
            if (reach <= anchor) first * (reach / anchor)
            else first + (count - 1 - first) * ((reach - anchor) / (w - anchor))
        return position.coerceIn(0f, (count - 1).toFloat()).roundToInt()
    }
}

/**
 * Keeping the target tab in view while the strip scrolls under a scrub: nudge it just enough that
 * the tab is fully visible, with a little air at the edge (the page's lmaScrubReveal).
 */
internal object ScrubFollow {
    const val MARGIN = 10f

    /**
     * The scroll position (dp) that shows the tab spanning [left] to [right] in a viewport
     * [viewport] wide that is scrolled to [scroll]; the same when it is in view already. Never
     * below 0 or beyond [maxScroll].
     */
    fun reveal(
        scroll: Float,
        viewport: Float,
        left: Float,
        right: Float,
        maxScroll: Float = Float.MAX_VALUE,
    ): Float {
        val wanted =
            when {
                left - MARGIN < scroll -> left - MARGIN
                right + MARGIN > scroll + viewport -> right + MARGIN - viewport
                else -> scroll
            }
        return min(max(0f, wanted), max(0f, maxScroll))
    }
}
