package com.lifemax.rpg.feature.lifedirector.domain

import org.junit.Assert.*
import org.junit.Test

class ReturnToTodayMotionTest {
    @Test
    fun weekMonthAndYearHaveIncreasingTravelAndDuration() {
        val week = ReturnToTodayMotion.forDays(7)
        val month = ReturnToTodayMotion.forDays(30)
        val year = ReturnToTodayMotion.forDays(365)
        assertEquals(1f, week.turns, 0f)
        assertEquals(46f, year.turns, 0f)
        assertTrue(week.durationMs >= 1800)
        assertTrue(month.turns > week.turns && year.turns > month.turns)
        assertTrue(month.durationMs > week.durationMs && year.durationMs > month.durationMs)
        assertTrue(year.durationMs > week.durationMs * 3)
    }

    @Test
    fun pastAndFutureTravelSymmetricallyInOppositeDirections() {
        val past = ReturnToTodayMotion.forDays(-365)
        val future = ReturnToTodayMotion.forDays(365)
        assertEquals(past.turns, future.turns, 0f)
        assertEquals(past.durationMs, future.durationMs)
        assertEquals(-past.direction, future.direction, 0f)
        assertEquals(0, ReturnToTodayMotion.forDays(0).durationMs)
    }

    @Test
    fun extremeOffsetsRemainFiniteAndLongerThanAYear() {
        for (offset in listOf(Int.MIN_VALUE, Int.MAX_VALUE)) {
            val motion = ReturnToTodayMotion.forDays(offset)
            assertTrue(motion.turns.isFinite())
            assertTrue(motion.durationMs > ReturnToTodayMotion.forDays(365).durationMs)
            assertTrue(motion.durationMs < 60000)
        }
    }
}
