package com.lifemax.rpg.core.lifedirector

import org.junit.Assert.assertEquals
import org.junit.Test

/** LDT-0: the plan's clock is whole seconds, rounded down, as `Math.floor(Date.now() / 1000)`. */
class DirectorClockTest {
    @Test
    fun secondsAreRoundedDownOnBothSidesOf1970() {
        assertEquals(1_793_422_800L, DirectorClock { 1_793_422_800_999L }.nowEpochSeconds())
        assertEquals(0L, DirectorClock { 0L }.nowEpochSeconds())
        assertEquals(-1L, DirectorClock { -1L }.nowEpochSeconds())
        assertEquals(-2L, DirectorClock { -1_001L }.nowEpochSeconds())
    }
}
