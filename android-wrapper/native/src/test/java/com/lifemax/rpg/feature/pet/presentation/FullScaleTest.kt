package com.lifemax.rpg.feature.pet.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The full-screen room's size: a whole number of device pixels to a room pixel that shows at least
 * 120 room pixels across and the whole 144 up and down, whatever the window's shape.
 */
class FullScaleTest {
    @Test
    fun anUprightPhoneIsLimitedByItsWidth() {
        assertEquals(9, fullScale(1080f, 2340f)) // 120 room pixels across at 9
        assertEquals(10, fullScale(1200f, 2600f))
    }

    @Test
    fun aWidePhoneIsLimitedByItsHeightAndShowsMoreOfTheHouse() {
        assertEquals(7, fullScale(2340f, 1080f)) // 144 * 7 = 1008 tall, 334 room pixels wide
        assertTrue(2340f / fullScale(2340f, 1080f) > 300f)
    }

    @Test
    fun theWholeHeightAlwaysFits() {
        for (w in listOf(320f, 720f, 1080f, 1440f, 2340f, 3200f)) {
            for (h in listOf(480f, 720f, 1080f, 2340f, 3200f)) {
                val s = fullScale(w, h)
                assertTrue("$w x $h at $s", s >= 1)
                assertTrue("$w x $h at $s", s == 1 || 144 * s <= h)
            }
        }
    }

    @Test
    fun aTinyWindowStillGetsOnePixelToAPixel() {
        assertEquals(1, fullScale(100f, 100f))
    }
}
