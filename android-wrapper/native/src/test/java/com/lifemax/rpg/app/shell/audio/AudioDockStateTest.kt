package com.lifemax.rpg.app.shell.audio

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioDockStateTest {
    private val tokens =
        JSONObject().apply {
            listOf(
                    "bg",
                    "bg-elev",
                    "panel",
                    "panel2",
                    "panel3",
                    "text",
                    "text-dim",
                    "muted",
                    "muted2",
                    "accent",
                    "accent-dim",
                    "accent-strong",
                    "line",
                    "line-soft",
                    "good",
                    "gold",
                    "bad",
                )
                .forEachIndexed { i, k -> put(k, 0xFF000000L + i) }
        }

    /** a push as 50-native-audio.js builds it (stateOf) */
    private fun push(visible: Boolean = true) =
        JSONObject()
            .put("v", 1)
            .put("visible", visible)
            .put("minimized", false)
            .put("loading", false)
            .put("mediaId", "m_book")
            .put("title", "Project Hail Mary")
            .put("sub", "hail-mary.m4b · 480.0 MB")
            .put("playing", true)
            .put("position", 100.0)
            .put("duration", 1000.0)
            .put("rate", 1.5)
            .put("rates", JSONArray(listOf(0.8, 1.0, 1.25, 1.5, 1.75, 2.0)))
            .put("tokens", if (visible) tokens else JSONObject())
            .put("light", false)

    private fun decode(j: JSONObject = push(), at: Long = 5_000) = AudioDockState.decode(j, at)

    @Test
    fun decodesThePagesPush() {
        val s = decode()
        assertTrue(s.visible && s.playing && !s.minimized && !s.loading)
        assertEquals("m_book", s.mediaId)
        assertEquals("Project Hail Mary", s.title)
        assertEquals(1.5, s.rate, 0.0)
        assertEquals(6, s.rates.size)
        assertEquals(17, s.tokens.size)
    }

    @Test
    fun aHiddenDockNeedsNoColours() {
        val s = decode(push(visible = false))
        assertFalse(s.visible)
        assertTrue(s.tokens.isEmpty())
        assertEquals(0f, s.heightDp, 0f)
    }

    @Test
    fun aKeyMissingOrOfTheWrongKindIsMalformed() {
        for (key in listOf("visible", "title", "position", "rates", "rate", "light", "mediaId")) {
            val j = push().apply { remove(key) }
            assertThrows(key, Exception::class.java) { decode(j) }
        }
        assertThrows(Exception::class.java) { decode(push().put("playing", "yes")) }
        assertThrows(Exception::class.java) { decode(push().put("v", 2)) }
        assertThrows(Exception::class.java) { decode(push().put("rates", JSONArray())) }
        assertThrows(Exception::class.java) { decode(push().put("position", -1.0)) }
        assertThrows(Exception::class.java) { decode(push().put("rate", 0.0)) }
    }

    @Test
    fun theClockExtrapolatesFromTheLastPush() {
        val s = decode(at = 5_000)
        assertEquals(100.0, s.positionAt(5_000), 0.0001)
        // 4 s at 1.5x
        assertEquals(106.0, s.positionAt(9_000), 0.0001)
        assertEquals(0.106f, s.fractionAt(9_000), 0.0001f)
    }

    @Test
    fun aPausedBookStandsStill() {
        val s = decode(push().put("playing", false))
        assertEquals(100.0, s.positionAt(60_000), 0.0)
    }

    @Test
    fun theClockNeverGoesPastTheEndOrBeforeTheStart() {
        val s = decode(push().put("position", 995.0))
        assertEquals(1000.0, s.positionAt(500_000), 0.0)
        assertEquals(1f, s.fractionAt(500_000), 0f)
        // a clock reading before the push (the page's push arrived "from the future") is no rewind
        assertEquals(995.0, s.positionAt(1_000), 0.0)
    }

    @Test
    fun anUnknownLengthIsNoFraction() {
        val s = decode(push().put("duration", 0.0))
        assertEquals(0f, s.fractionAt(9_000), 0f)
        assertTrue(s.positionAt(9_000) > 100.0)
    }

    @Test
    fun theTickRunsOnlyWhileTheSeekBarIsOnScreenAndMoving() {
        val s = decode()
        assertTrue(s.ticks(onScreen = true))
        assertFalse("off screen", s.ticks(onScreen = false))
        assertFalse("paused", s.copy(playing = false).ticks(true))
        assertFalse("minimised", s.copy(minimized = true).ticks(true))
        assertFalse("hidden", s.copy(visible = false).ticks(true))
        assertFalse("loading", s.copy(loading = true).ticks(true))
    }

    @Test
    fun theSpeedCyclesThroughThePagesOptionsAndWraps() {
        var s = decode(push().put("rate", 1.0))
        assertEquals(1.25, s.nextRate(), 0.0)
        s = decode(push().put("rate", 2.0))
        assertEquals(0.8, s.nextRate(), 0.0)
        // a speed the page does not list (set elsewhere) goes to the first
        s = decode(push().put("rate", 1.1))
        assertEquals(0.8, s.nextRate(), 0.0)
    }

    @Test
    fun theHeightIsKnownBeforeTheDockIsDrawn() {
        val open = decode()
        assertEquals(AudioDockMetrics.EXPANDED_DP + AudioDockMetrics.GAP_DP, open.heightDp, 0f)
        val min = decode(push().put("minimized", true))
        assertEquals(AudioDockMetrics.MINIMIZED_DP + AudioDockMetrics.GAP_DP, min.heightDp, 0f)
        assertTrue(min.heightDp < open.heightDp)
    }

    @Test
    fun theDockShowsOnlyOverNativeBarsWithoutTheKeyboard() {
        val s = decode()
        assertTrue(dockShows(true, s, barsPx = 180, keyboardUp = false))
        assertFalse("owner switched it off", dockShows(false, s, 180, false))
        assertFalse("no native bars: the page's own player", dockShows(true, s, -1, false))
        assertFalse("keyboard", dockShows(true, s, 180, true))
        assertFalse("nothing to play", dockShows(true, decode(push(false)), 180, false))
    }

    @Test
    fun theScreensClearTheDockAtTheBottomOnly() {
        assertEquals(64f to 72f, insetsWithDock(64f, 72f, 0f))
        val open = decode().heightDp
        assertEquals(64f to 72f + open, insetsWithDock(64f, 72f, open))
        // appearing and going again returns exactly to where the content was
        assertEquals(insetsWithDock(64f, 72f, 0f), 64f to 72f)
    }

    @Test
    fun timesAreWordedAsThePageWordsThem() {
        assertEquals("0:00", audioClock(0.0))
        assertEquals("0:09", audioClock(9.9))
        assertEquals("1:05", audioClock(65.0))
        assertEquals("59:59", audioClock(3599.0))
        assertEquals("1:00:00", audioClock(3600.0))
        assertEquals("16:10:00", audioClock(58200.0))
        assertEquals("0:00", audioClock(-5.0))
    }

    @Test
    fun speedsAreLabelledAsThePagesSelectDoes() {
        assertEquals("1×", rateLabel(1.0))
        assertEquals("2×", rateLabel(2.0))
        assertEquals("0.8×", rateLabel(0.8))
        assertEquals("1.25×", rateLabel(1.25))
        assertEquals("1.5×", rateLabel(1.5))
        assertEquals("1.75×", rateLabel(1.75))
    }
}
