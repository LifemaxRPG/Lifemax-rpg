package com.lifemax.rpg.core.lifedirector

import kotlin.math.ceil

/** Session lengths in whole slots (LDT-20). */
object QuarterHours {
    /** The shortest length a session can be given, in minutes. */
    const val MINIMUM_MINUTES: Double = 15.0

    private const val SLOT_MINUTES = 15.0

    /**
     * LDT-20 `ceilTo15`: [minutes] rounded up to a whole number of 15-minute slots, and never less
     * than 15. Fractions round up (14.999 is 15, 15.0000001 is 30); zero and negative lengths give
     * 15; NaN stays NaN and infinity stays infinity.
     */
    fun roundUp(minutes: Double): Double =
        maxOf(MINIMUM_MINUTES, ceil(minutes / SLOT_MINUTES) * SLOT_MINUTES)
}
