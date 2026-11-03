package com.lifemax.rpg.feature.pet.room

import kotlin.math.abs

/** The numbers behind the 2.5D room's light. */
internal object LightTuning {
    /**
     * The skirting meets the floor on this row in all three rooms; the floor runs to the bottom.
     */
    const val SEAM_ROW = 117

    /** Alpha of the dark that settles on the floor under the skirting, row by row going down. */
    val SEAM_FLOOR = floatArrayOf(0.20f, 0.15f, 0.11f, 0.07f, 0.04f, 0.02f)

    /** Alpha of the dark in the wall's last rows above the seam, going up from it. */
    val SEAM_WALL = floatArrayOf(0.07f, 0.04f, 0.02f)

    /**
     * The window light on the floor: pane columns, mullion gaps and where the light lies (house
     * rows).
     */
    const val LIGHT_TOP = 118f
    const val LIGHT_BOTTOM = 142f
    const val LIGHT_MID = 130f
    const val LIGHT_GAP = 2f

    /** The light's left and right edges at the top and how far it leans going down (art pixels). */
    const val LIGHT_LEFT = 30f
    const val LIGHT_RIGHT = 90f
    const val LIGHT_LEAN = 22f

    /** The light slides this many pixels per hour away from noon (the sun crosses the sky). */
    const val LIGHT_DRIFT_PER_HOUR = 2.4f

    /** A cat's window side gets a rim this strong in daylight, at night, and at dusk and dawn. */
    const val RIM_DAY = 0.62f
    const val RIM_NIGHT = 0.34f
    const val RIM_TWILIGHT = 0.30f

    /** A window lights a cat within this many world pixels, fading out with the distance. */
    const val RIM_REACH = 150f

    /** Nearer than this to the window's centre a cat is lit from the front: no rim. */
    const val RIM_DEAD_ZONE = 6f

    /** The rim colours: sunlight, moonlight, the low orange of morning and evening. */
    const val RIM_COLOUR_DAY = 0xFFE2A0
    const val RIM_COLOUR_NIGHT = 0x9FB4FF
    const val RIM_COLOUR_TWILIGHT = 0xFFB27A

    /** Half the glass of a window, to find its centre from its offset. */
    const val GLASS_HALF = RoomLayout.GLASS_X + RoomLayout.GLASS_W / 2
}

/**
 * The window light lying on the floor in the 2.5D room: the glass's four panes cast four patches,
 * with the bars of the window between them, leaning across the floor and sliding with the hour
 * (they are the same shape the classic room paints as one quad, so the colours and the strength are
 * the classic room's).
 */
internal object FloorLight {
    /** How far the light is shifted at [hour]: 0 at noon, negative in the morning. */
    fun drift(hour: Double): Float =
        ((hour.coerceIn(6.0, 20.0) - 12.0) * LightTuning.LIGHT_DRIFT_PER_HOUR).toFloat()

    /** Paint the four patches of window [x] (the window's house x) in [colour]. */
    fun paint(c: RoomCanvas, x: Float, hour: Double, colour: Int) {
        val dx = x + drift(hour)
        val l = LightTuning.LIGHT_LEFT
        val r = LightTuning.LIGHT_RIGHT
        val mid = (l + r) / 2f
        val g = LightTuning.LIGHT_GAP / 2f
        val top = LightTuning.LIGHT_TOP
        val cut = LightTuning.LIGHT_MID
        val bottom = LightTuning.LIGHT_BOTTOM
        for (row in 0..1) {
            val y0 = if (row == 0) top else cut + g
            val y1 = if (row == 0) cut - g else bottom
            for (col in 0..1) {
                val x0 = if (col == 0) l else mid + g
                val x1 = if (col == 0) mid - g else r
                quad(c, dx, x0, x1, y0, y1, colour)
            }
        }
    }

    /**
     * One patch: a parallelogram that leans right going down, as the glass leans onto the floor.
     */
    private fun quad(
        c: RoomCanvas,
        dx: Float,
        x0: Float,
        x1: Float,
        y0: Float,
        y1: Float,
        colour: Int,
    ) {
        val lean0 = lean(y0)
        val lean1 = lean(y1)
        c.quad(
            dx + x0 + lean0,
            y0,
            dx + x1 + lean0,
            y0,
            dx + x1 + lean1,
            y1,
            dx + x0 + lean1,
            y1,
            colour,
        )
    }

    private fun lean(y: Float): Float =
        LightTuning.LIGHT_LEAN * (y - LightTuning.LIGHT_TOP) /
            (LightTuning.LIGHT_BOTTOM - LightTuning.LIGHT_TOP)
}

/**
 * The dark that settles where the wall meets the floor: a few stepped rows under the skirting and
 * fewer above it, each row a little lighter. Pixel art: whole rows, no gradient fill.
 */
internal object SeamShade {
    /** Paint the seam over [width] pixels from house x [x]. */
    fun paint(c: RoomCanvas, x: Int, width: Int, strength: Float = 1f) {
        val seam = LightTuning.SEAM_ROW
        for (i in LightTuning.SEAM_FLOOR.indices) {
            c.fill(x, seam + 1 + i, width, 1, shade(LightTuning.SEAM_FLOOR[i] * strength))
        }
        for (i in LightTuning.SEAM_WALL.indices) {
            c.fill(x, seam - i, width, 1, shade(LightTuning.SEAM_WALL[i] * strength))
        }
    }

    private fun shade(alpha: Float): Int =
        (Math.round(alpha.coerceAtMost(1f) * 255f) shl 24) or ShadowTuning.COLOUR
}

/** How a cat is lit from its window: which side, how much, what colour. Filled by [RimLight.of]. */
internal class RimSpec {
    /** -1 the light is on the cat's left, 1 on its right, 0 no rim. */
    var side = 0

    /** How strong the rim is, 0 to 1. */
    var strength = 0f

    /** The rim's colour, `0xRRGGBB`. */
    var colour = 0
}

/**
 * Rim light: a cat near a window gets a thin bright edge on the side that faces it, warm in the
 * day, cool and weak at night, orange at dawn and dusk. The pure part (this) says which side, how
 * strong and what colour; the stage draws it as the cat's silhouette, tinted, one art pixel to the
 * window side and under the cat.
 */
internal object RimLight {
    /**
     * World x of the centre of the glass of each window (the living room, the bedroom, the
     * kitchen).
     */
    val windowCentres: FloatArray =
        FloatArray(RoomLayout.windows.size) {
            (RoomLayout.windows[it] + LightTuning.GLASS_HALF).toFloat()
        }

    /** Fill [out] for a cat standing at world [catX] at [hour]. */
    fun of(hour: Double, catX: Float, out: RimSpec) {
        out.side = 0
        out.strength = 0f
        var best = Float.MAX_VALUE
        var centre = 0f
        for (w in windowCentres) {
            val d = abs(w - catX)
            if (d < best) {
                best = d
                centre = w
            }
        }
        val level = level(hour, out)
        if (best < LightTuning.RIM_DEAD_ZONE || best >= LightTuning.RIM_REACH || level <= 0f) return
        out.side = if (centre > catX) 1 else -1
        out.strength = level * (1f - best / LightTuning.RIM_REACH)
    }

    /** The strength of the light at [hour] before distance, and its colour into [out]. */
    private fun level(hour: Double, out: RimSpec): Float {
        val night = RoomLight.isNight(hour)
        return when {
            hour >= DAY_FROM && hour < DAY_UNTIL -> {
                out.colour = LightTuning.RIM_COLOUR_DAY
                LightTuning.RIM_DAY
            }
            night -> {
                out.colour = LightTuning.RIM_COLOUR_NIGHT
                LightTuning.RIM_NIGHT
            }
            else -> {
                out.colour = LightTuning.RIM_COLOUR_TWILIGHT
                LightTuning.RIM_TWILIGHT
            }
        }
    }

    /** Hours the window light counts as day, as the classic room's window light does. */
    private const val DAY_FROM = 7.5
    private const val DAY_UNTIL = 17.5
}
