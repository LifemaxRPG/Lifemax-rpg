package com.lifemax.rpg.feature.pet.room

import kotlin.math.sqrt

/** The look of the soft contact shadow: its colour and the three steps it grows darker in. */
internal object ShadowTuning {
    /** The art's own outline colour, so a shadow is the room's darkest dark and not black. */
    const val COLOUR = 0x1E162D

    /** Alpha of the outer fringe, the middle ring and the core, at full strength (0 to 1). */
    const val FRINGE = 0.10f
    const val RING = 0.12f
    const val CORE = 0.16f

    /** Each ring's width as a share of the whole, outside in: the middle ring and the core. */
    const val RING_SCALE = 0.74f
    const val CORE_SCALE = 0.42f

    /** A shadow's height as a share of its half width, and the least it is (art pixels). */
    const val FLATNESS = 0.26f
    const val MIN_HALF_HEIGHT = 1
}

/**
 * A soft contact shadow in the room's own pixel-art style: an ellipse made of three nested steps (a
 * dithered fringe, a ring, a core), each step a whole number of art pixels, no anti-aliasing, so it
 * still reads as pixel art but has none of the hard edge of the old two-row blob. The steps add up
 * (each is drawn over the last), so the core is the darkest and the centre of the shadow is where
 * the thing touches the floor.
 *
 * [spans] works the shape out (pure, tested); [paint] draws it with rectangles on a [RoomCanvas].
 */
internal object ContactShadow {
    /**
     * The half width of the ellipse a step covers at row [dy] from the centre of an ellipse with
     * half width [halfW] and half height [halfH], in whole pixels (0 when the row misses it).
     */
    fun span(halfW: Int, halfH: Int, dy: Int, scale: Float): Int {
        val a = halfW * scale
        val b = (halfH + 0.5f) * scale
        if (a < 0.5f || b <= 0f || Math.abs(dy) > b) return 0
        val q = dy / b
        return Math.round(a * sqrt(1f - q * q))
    }

    /**
     * The half height for a shadow with half width [halfW]: flat, at least
     * [ShadowTuning.MIN_HALF_HEIGHT].
     */
    fun halfHeightFor(halfW: Int): Int =
        maxOf(ShadowTuning.MIN_HALF_HEIGHT, Math.round(halfW * ShadowTuning.FLATNESS))

    /**
     * Paint the shadow centred at house pixel ([cx], [cy]) with half width [halfW] and half height
     * [halfH], [strength] 0 to 1 (0 draws nothing).
     */
    fun paint(c: RoomCanvas, cx: Int, cy: Int, halfW: Int, halfH: Int, strength: Float) {
        if (strength <= 0f || halfW < 1) return
        val s = strength.coerceAtMost(1f)
        for (dy in -halfH..halfH) {
            val y = cy + dy
            val outer = span(halfW, halfH, dy, 1f)
            val ring = span(halfW, halfH, dy, ShadowTuning.RING_SCALE)
            val core = span(halfW, halfH, dy, ShadowTuning.CORE_SCALE)
            fringe(c, cx, y, ring, outer, argb(ShadowTuning.FRINGE * s * 2f))
            if (ring > 0) c.fill(cx - ring, y, 2 * ring, 1, argb(ShadowTuning.RING * s))
            if (core > 0) c.fill(cx - core, y, 2 * core, 1, argb(ShadowTuning.CORE * s))
        }
    }

    /**
     * The fringe between the ring and the outer edge: every other pixel, so it fades in a checker.
     */
    private fun fringe(c: RoomCanvas, cx: Int, y: Int, from: Int, to: Int, colour: Int) {
        for (d in from until to) {
            if ((d + y) and 1 != 0) continue
            c.fill(cx + d, y, 1, 1, colour)
            c.fill(cx - d - 1, y, 1, 1, colour)
        }
    }

    private fun argb(alpha: Float): Int =
        (Math.round(alpha.coerceIn(0f, 1f) * 255f) shl 24) or ShadowTuning.COLOUR
}
