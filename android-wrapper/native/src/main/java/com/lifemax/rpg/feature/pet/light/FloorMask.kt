package com.lifemax.rpg.feature.pet.light

import com.lifemax.rpg.feature.pet.room.HouseMap
import com.lifemax.rpg.feature.pet.room.LightTuning
import com.lifemax.rpg.feature.pet.room.RoomLayout
import kotlin.math.floor

/**
 * The window light on the floor as a whole-pixel mask (docs/specs/CAT_LIGHTING_PLAN.md WP-L4,
 * section 4.2): the same four panes per window the room paints today as anti-aliased quads, but as
 * a picture of house pixels, each pixel either in the light or not (its centre inside a pane), with
 * the slide held on whole pixels by [HouseLight]. So a still light is still, a moving light moves
 * one pixel column at a time, and nothing has a soft, crawling edge.
 *
 * [dither] is the FULL look: the two columns on each pane's edge away from the window, and the
 * whole patch on an overcast day, are a checker of the light anchored to the house's pixel grid
 * (ordered dithering) instead of solid. Without it the mask is the classic room's colours, solid.
 *
 * Not drawn by the room yet: the room's painter (DepthScene) is being changed by other work; this
 * is the tested picture it will draw with one overlay, in place of the eight quads (section 5.1).
 */
internal object FloorMask {
    /** The first and last floor rows the light can lie on. */
    val TOP: Int = LightTuning.LIGHT_TOP.toInt()
    val BOTTOM: Int = LightTuning.LIGHT_BOTTOM.toInt()

    /** Rows of the mask. */
    val HEIGHT: Int
        get() = BOTTOM - TOP

    /** The classic room's colours: rgba(255,236,160,.16) by day, rgba(150,170,255,.12) at night. */
    const val DAY = (41 shl 24) or 0xFFECA0
    const val NIGHT = (31 shl 24) or 0x96AAFF

    /**
     * Paint the light of [light] into [out] ([width] house pixels x [HEIGHT] rows from row [TOP],
     * house x 0 at the house's left edge); clear where there is none. Allocates nothing.
     */
    fun paint(light: HouseLight, out: IntArray, width: Int, dither: Boolean) {
        out.fill(0)
        val colour =
            when (light.phase) {
                LightPhase.DAY -> DAY
                LightPhase.NIGHT -> NIGHT
                LightPhase.TWILIGHT -> return
            }
        if (light.sun <= 0.0) return
        val grey = dither && light.sun < 1.0
        val lit = HouseMap.current.floorLights
        val l = LightTuning.LIGHT_LEFT
        val r = LightTuning.LIGHT_RIGHT
        val mid = (l + r) / 2f
        val g = LightTuning.LIGHT_GAP / 2f
        for (w in lit) {
            val dx = w - RoomLayout.HOUSE_X0 + light.driftPx
            for (row in TOP until BOTTOM) {
                val y = row + 0.5f
                if (y > LightTuning.LIGHT_MID - g && y < LightTuning.LIGHT_MID + g) continue
                val lean = lean(y)
                for (col in 0..1) {
                    val x0 = dx + (if (col == 0) l else mid + g) + lean
                    val x1 = dx + (if (col == 0) mid - g else r) + lean
                    // a pixel is lit when its centre is inside: columns ceil(x0 - .5) until ceil(x1
                    // - .5)
                    val from = ceilOf(x0 - HALF)
                    val until = ceilOf(x1 - HALF)
                    for (x in from.coerceAtLeast(0) until until.coerceAtMost(width)) {
                        // the panes lean right going down: their far edge is the right one
                        val checker = grey || (dither && x >= until - EDGE)
                        if (checker && ((x + row) and 1) == 1) continue
                        out[(row - TOP) * width + x] = colour
                    }
                }
            }
        }
    }

    /** True if the centre of house pixel ([x], [row]) is inside a pane, the classic quad's test. */
    fun inQuad(light: HouseLight, x: Int, row: Int): Boolean {
        val y = row + 0.5f
        val g = LightTuning.LIGHT_GAP / 2f
        if (y < LightTuning.LIGHT_TOP || y > LightTuning.LIGHT_BOTTOM) return false
        if (y > LightTuning.LIGHT_MID - g && y < LightTuning.LIGHT_MID + g) return false
        val l = LightTuning.LIGHT_LEFT
        val r = LightTuning.LIGHT_RIGHT
        val mid = (l + r) / 2f
        for (w in HouseMap.current.floorLights) {
            val px = x + 0.5f - (w - RoomLayout.HOUSE_X0 + light.driftPx) - lean(y)
            if ((px >= l && px < mid - g) || (px >= mid + g && px < r)) return true
        }
        return false
    }

    private fun lean(y: Float): Float =
        LightTuning.LIGHT_LEAN * (y - LightTuning.LIGHT_TOP) /
            (LightTuning.LIGHT_BOTTOM - LightTuning.LIGHT_TOP)

    private fun ceilOf(v: Float): Int = -floor(-v.toDouble()).toInt()

    private const val HALF = 0.5f

    /** Columns of checker on a pane's far edge in the FULL look. */
    private const val EDGE = 2
}
