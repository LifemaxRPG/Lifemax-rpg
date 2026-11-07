package com.lifemax.rpg.feature.lifedirector.domain

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.ln1p
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Eight day detents per revolution, matching the calendar bezel. */
object ReturnToTodayMotion {
    data class Travel(val turns: Float, val durationMs: Int, val direction: Float)

    fun forDays(offset: Int): Travel {
        val days = abs(offset.toLong()).toDouble()
        if (days == 0.0) return Travel(0f, 0, 0f)
        // A full year keeps the real wheel distance. Beyond that, compress very large
        // journeys so angles retain precision and the rewind remains usable.
        val turns = ceil(if (days <= 366) days / 8.0 else 366 / 8.0 + sqrt((days - 366) / 8.0))
        val duration = (900 + 1500 * ln1p(days / 7.0)).roundToInt()
        return Travel(turns.toFloat(), duration, if (offset > 0) -1f else 1f)
    }
}
