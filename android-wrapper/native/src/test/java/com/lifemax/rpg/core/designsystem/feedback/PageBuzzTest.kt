package com.lifemax.rpg.core.designsystem.feedback

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The page's haptic scale (50-haptics.js hapScale with HAP_LEVELS and HAP_MAX) and the waveform
 * NavHaptics plays, so a native buzz is the page's at every strength. The expected values are the
 * page's own: hapScale run in the built page for each level (6 and 10 ms, a 200 ms buzz that hits
 * the cap, and the editor's 8-30-8 reset).
 */
class PageBuzzTest {
    private val levels =
        mapOf(
            "light" to HapticScale(dm = .7, add = 0.0, amp = 90, min = 4),
            "normal" to HapticScale(dm = 1.0, add = 0.0, amp = 150, min = 5),
            "strong" to HapticScale(dm = 1.7, add = 8.0, amp = 230, min = 16),
            "max" to HapticScale(dm = 2.5, add = 16.0, amp = 255, min = 28),
        )

    // hapScale(6), hapScale(10), hapScale(200), hapScale([8,30,8]) as the page answers them
    private val page =
        mapOf(
            "light" to listOf(4L, 7L, 140L, 6L),
            "normal" to listOf(6L, 10L, 200L, 8L),
            "strong" to listOf(18L, 25L, 260L, 22L),
            "max" to listOf(31L, 41L, 260L, 36L),
        )

    @Test
    fun everyLevelScalesAsThePageDoes() {
        levels.forEach { (key, scale) ->
            val (six, ten, long, reset) = page.getValue(key)
            assertEquals("$key 6 ms", six, scale.on(6))
            assertEquals("$key 10 ms", ten, scale.on(10))
            assertEquals("$key 200 ms is capped", long, scale.on(200))
            assertArrayEquals("$key 8-30-8", longArrayOf(reset, 30, reset), scale.pattern(8, 30, 8))
        }
    }

    @Test
    fun theWeekPickAtNormalIsTheFlatSixMilliseconds() {
        val played = mutableListOf<Pair<LongArray, Int?>>()
        val buzz = PageBuzz { t, a -> played += t to a }
        buzz.play(levels.getValue("normal"), 6)
        assertArrayEquals(longArrayOf(6), played.single().first)
        assertEquals(150, played.single().second)
    }

    @Test
    fun nothingPlaysWithHapticsOff() {
        var played = false
        val buzz = PageBuzz { _, _ -> played = true }
        buzz.play(null, 6)
        buzz.play(levels.getValue("max"))
        assertTrue(!played)
    }

    @Test
    fun theMinimumHoldsAShortBuzzUpAndTheCapHoldsItDown() {
        val tight = HapticScale(dm = 1.0, add = 0.0, amp = 150, min = 5, max = 20)
        assertEquals(5, tight.on(1))
        assertEquals(20, tight.on(400))
        // JS Math.round rounds a half up: 4 + 0.5 = 4.5 → 5, 2 + 0.5 = 2.5 → 3
        assertEquals(5, HapticScale(1.0, 0.5, 150, 1).on(4))
        assertEquals(3, HapticScale(1.0, 0.5, 150, 1).on(2))
    }

    @Test
    fun theWaveformStartsWithAZeroWaitAndCarriesTheAmplitudeOnEveryOnPart() {
        val (wave, amps) = pageWaveform(longArrayOf(22, 30, 22), 230)
        assertArrayEquals(longArrayOf(0, 22, 30, 22), wave)
        assertArrayEquals(intArrayOf(0, 230, 0, 230), amps)
    }

    @Test
    fun theWaveformHoldsPartsAndStrengthToWhatNavHapticsAccepts() {
        val (wave, amps) = pageWaveform(LongArray(30) { 900 }, 999)
        assertEquals(25, wave.size)
        assertTrue(wave.drop(1).all { it == 600L })
        assertEquals(255, amps!![1])
        assertEquals(1, pageWaveform(longArrayOf(6), 0).second!![1])
    }

    @Test
    fun withoutAStrengthTheVibratorKeepsItsDefault() {
        val (wave, amps) = pageWaveform(longArrayOf(6), null)
        assertArrayEquals(longArrayOf(0, 6), wave)
        assertNull(amps)
    }
}
