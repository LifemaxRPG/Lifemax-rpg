package com.lifemax.rpg.feature.habits.presentation.scene

import com.lifemax.rpg.feature.habits.domain.BuildingSpec as B
import com.lifemax.rpg.feature.habits.domain.Wind
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min

/**
 * How a shop's awning ([AwningArt]) moves in the wind ([Wind]): the canopy breathes, lifting a
 * little at its front as the air gets under it and settling as it passes; each scallop of the
 * valance swings out on its own (shorter seen from the front, as cloth swung towards the eye or
 * away is), its hem pushed along the street downwind, in a ripple that runs along the awning as the
 * gust does. Pure arithmetic into a kept array, shared by the painter and the previews.
 */
internal object AwningSway {
    /** The valance is drawn in strips a scallop wide, cut at the scallops' points. */
    val STRIPS: Int = ceil((B.W - 2f) / AwningArt.SCALLOP).toInt()

    /** Where strip [k] starts across the awning, in units from its middle (its points). */
    fun stripX(k: Int): Float = -B.W / 2f + 1f + k * AwningArt.SCALLOP

    /** The pose array: the canopy's lift, then each strip's lift and its hem's push. */
    fun buffer(): FloatArray = FloatArray(1 + 2 * STRIPS)

    /**
     * Writes into [out] the awning's pose for one standing at [x] along the street (its middle),
     * [seed] its own: at 0 the canopy's lift (a share of its depth, 0 to 0.1); for strip k at 1 +
     * 2k how much shorter its scallop hangs (a share, 0 to 0.4) and at 2 + 2k how far its hem is
     * pushed along the street (units, downwind positive). All 0 when the wind is still.
     */
    fun pose(x: Float, seed: Int, wind: Wind, out: FloatArray) {
        java.util.Arrays.fill(out, 0f)
        if (!wind.moving) return
        val s = seed and 0x3FF
        val f = wind.force(x)
        // the cloth on its frame barely lifts: the air under it in a gust, a slow breath
        out[0] = (.022f * f + .012f * wind.flutter(x, s + 900, .9f)).coerceIn(-.02f, .1f)
        for (k in 0 until STRIPS) {
            val at = x + stripX(k) + AwningArt.SCALLOP / 2f
            val here = wind.force(at)
            // each scallop flaps on its own, a little after the one upwind of it
            val flap = wind.flutter(at, s + k * 13, 2.6f)
            val slow = wind.flutter(at, s + k * 13 + 7, 1.1f)
            out[1 + 2 * k] = min(.4f, abs(flap) * .16f + abs(slow) * .1f + here * .05f)
            out[2 + 2 * k] = (here * .55f + slow * .5f + flap * .25f).coerceIn(-1.5f, 2.5f)
        }
    }
}
