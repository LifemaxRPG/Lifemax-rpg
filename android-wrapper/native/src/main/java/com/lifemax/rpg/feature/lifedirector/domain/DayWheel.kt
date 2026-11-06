package com.lifemax.rpg.feature.lifedirector.domain

import kotlin.math.roundToInt

/** Relative scroll-wheel travel; crossing midnight never jumps to the other end of the week. */
class DayWheel(startIndex: Int, private val count: Int, initialAngle: Float) {
    init {
        require(count > 1 && startIndex in 0 until count)
        require(initialAngle.isFinite())
    }

    private var previous = initialAngle
    var position = startIndex.toFloat()
        private set

    val selected: Int
        get() = position.roundToInt().coerceIn(0, count - 1)

    fun move(angle: Float): Int {
        require(angle.isFinite())
        val delta = ((angle - previous + 540f) % 360f) - 180f
        previous = angle
        position = (position + delta / (360f / count)).coerceIn(0f, (count - 1).toFloat())
        return selected
    }
}
