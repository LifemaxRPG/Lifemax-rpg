package com.lifemax.rpg.feature.lifedirector

import com.lifemax.rpg.feature.lifedirector.domain.DayWheel
import org.junit.Assert.assertEquals
import org.junit.Test

class DayWheelTest {
    @Test
    fun crossesMidnightWithoutJumping() {
        val wheel = DayWheel(3, 7, 350f)
        wheel.move(10f)
        assertEquals(20f, wheel.rotation, .001f)
        wheel.move(350f)
        assertEquals(0f, wheel.rotation, .001f)
        assertEquals(3, wheel.selected)
    }

    @Test
    fun keepsTurningPastTheWeekAndReversesImmediately() {
        val wheel = DayWheel(0, 7, 0f)
        repeat(10) { for (a in listOf(90f, 180f, 270f, 0f)) wheel.move(a) }
        assertEquals(80, wheel.selected)
        wheel.move(315f)
        assertEquals(79, wheel.selected)
        assertEquals(3555f, wheel.rotation, .001f)
    }

    @Test
    fun canBrowseEarlierDatesEvenWithOneDayOnTheStrip() {
        val wheel = DayWheel(0, 1, 90f)
        wheel.move(0f)
        assertEquals(-2, wheel.selected)
        wheel.move(90f)
        assertEquals(0, wheel.selected)
    }
}
