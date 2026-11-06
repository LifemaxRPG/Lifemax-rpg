package com.lifemax.rpg.feature.lifedirector

import com.lifemax.rpg.feature.lifedirector.domain.DayWheel
import org.junit.Assert.assertEquals
import org.junit.Test

class DayWheelTest {
    @Test
    fun crossesMidnightInBothDirectionsWithoutJumping() {
        val wheel = DayWheel(3, 7, 350f)
        wheel.move(10f)
        assertEquals(3f + 20f / (360f / 7), wheel.position, .001f)
        wheel.move(350f)
        assertEquals(3f, wheel.position, .001f)
    }

    @Test
    fun reachesLastDayAndReversesWithoutOverscrollDebt() {
        val wheel = DayWheel(0, 7, 0f)
        for (angle in listOf(60f, 120f, 180f, 240f, 300f, 350f, 50f)) wheel.move(angle)
        assertEquals(6, wheel.selected)
        wheel.move(350f)
        assertEquals(5, wheel.selected)
    }

    @Test
    fun clampsAtFirstDayAndWorksWithShortHorizons() {
        val wheel = DayWheel(0, 2, 90f)
        wheel.move(0f)
        assertEquals(0, wheel.selected)
        wheel.move(100f)
        assertEquals(1, wheel.selected)
    }
}
