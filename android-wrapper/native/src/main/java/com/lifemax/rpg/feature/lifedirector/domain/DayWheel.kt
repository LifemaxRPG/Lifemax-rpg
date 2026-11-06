package com.lifemax.rpg.feature.lifedirector.domain

import kotlin.math.roundToInt

/** Continuous bezel travel. A 45-degree detent advances one date, independent of strip length. */
class DayWheel(private val startIndex: Int, count: Int, initialAngle: Float) {
    init {
        require(count > 0 && initialAngle.isFinite())
    }

    private var previous = initialAngle
    var rotation = 0f
        private set

    val position: Float
        get() = startIndex + rotation / 45f

    val selected: Int
        get() = startIndex + (rotation / 45f).roundToInt()

    fun move(angle: Float): Int {
        require(angle.isFinite())
        rotation += ((angle - previous + 540f) % 360f) - 180f
        previous = angle
        return selected
    }
}
