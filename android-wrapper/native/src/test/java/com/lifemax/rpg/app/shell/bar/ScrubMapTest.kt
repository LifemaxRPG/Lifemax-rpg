package com.lifemax.rpg.app.shell.bar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The finger-to-tab mapping and the strip's follow: the page's scrub (42-scrubnav.js), as numbers.
 */
class ScrubMapTest {
    private val width = 400f
    private val tabs = 10

    private fun at(x: Float, down: Float, start: Int, gain: Float = 1f) =
        ScrubMap.indexAt(x, down, width, start, tabs, gain)

    @Test
    fun aFingerThatHasNotMovedIsOnTheStartTab() {
        for (start in 0 until tabs) assertEquals(start, at(200f, 200f, start))
    }

    @Test
    fun theLeftEdgeIsTheFirstTabAndTheRightEdgeTheLast() {
        for (start in listOf(0, 3, 5, 9)) {
            assertEquals("left edge from $start", 0, at(0f, 200f, start))
            assertEquals("right edge from $start", tabs - 1, at(width, 200f, start))
        }
    }

    @Test
    fun aSwipeAcrossTheWholeBarReachesTheEndTabAtNormalSpeed() {
        // from the first tab with the finger down at the left, the far edge is the last tab
        assertEquals(tabs - 1, at(width, 40f, 0))
        assertEquals(0, at(0f, width - 40f, tabs - 1))
    }

    @Test
    fun theStretchOnEachSideOfTheStartIsSpreadOverTheTabsOnThatSide() {
        // start on tab 4 with the finger at 200: to the right 5 tabs over 200 dp, to the left 4
        // over 200 dp
        assertEquals(5, at(240f, 200f, 4)) // 40/200 * 5 = 1 tab
        assertEquals(7, at(320f, 200f, 4)) // 120/200 * 5 = 3 tabs
        assertEquals(3, at(150f, 200f, 4)) // 50/200 * 4 = 1 tab
        assertEquals(2, at(100f, 200f, 4)) // 100/200 * 4 = 2 tabs
    }

    @Test
    fun theIndexNeverLeavesTheBar() {
        for (x in listOf(-500f, -1f, 0f, 399f, 400f, 5000f)) {
            for (gain in listOf(1f, 1.8f, 2.6f)) {
                val i = at(x, 200f, 4, gain)
                assertTrue("x=$x gain=$gain gave $i", i in 0 until tabs)
            }
        }
    }

    @Test
    fun aHigherSpeedPassesMoreTabsForTheSameMovement() {
        val normal = at(260f, 200f, 4, ScrubSpeed.Normal.gain)
        val fast = at(260f, 200f, 4, ScrubSpeed.Fast.gain)
        val faster = at(260f, 200f, 4, ScrubSpeed.Faster.gain)
        assertTrue("$normal < $fast < $faster", normal < fast && fast < faster)
    }

    @Test
    fun theLastTabIsReachedBeforeTheEdgeAtFasterSpeeds() {
        // the end is within reach: a 6th of the bar's width past the start is enough at 2.6 x
        assertEquals(tabs - 1, at(200f + 400f / 2.6f, 200f, 4, ScrubSpeed.Faster.gain))
        assertTrue(at(200f + 400f / 2.6f, 200f, 4, ScrubSpeed.Normal.gain) < tabs - 1)
    }

    @Test
    fun aTouchNearAnEdgeIsHeldInsoThereIsRoomToGoBothWays() {
        // down at 5 dp from the left: treated as 40, so a slide right still has the whole bar to go
        assertEquals(5, at(40f, 5f, 5))
        assertEquals(tabs - 1, at(width, 5f, 5))
        assertEquals(0, at(0f, 5f, 5))
    }

    @Test
    fun aNarrowBarIsMeasuredAsTheMinimumWidth() {
        assertEquals(tabs - 1, ScrubMap.indexAt(200f, 40f, 100f, 0, tabs, 1f))
    }

    @Test
    fun oneTabOrNoneHasNothingToPass() {
        assertEquals(0, ScrubMap.indexAt(300f, 100f, width, 0, 1, 1f))
        assertEquals(0, ScrubMap.indexAt(300f, 100f, width, 0, 0, 1f))
    }

    @Test
    fun everyTabIsReachableFromTheFirstOne() {
        val seen = (0..width.toInt()).map { at(it.toFloat(), 40f, 0) }.toSet()
        assertEquals((0 until tabs).toSet(), seen)
    }

    @Test
    fun speedsAreThePagesAndAnUnknownNameIsFast() {
        assertEquals(1f, ScrubSpeed.of("normal").gain)
        assertEquals(1.8f, ScrubSpeed.of("fast").gain)
        assertEquals(2.6f, ScrubSpeed.of("faster").gain)
        assertEquals(ScrubSpeed.Fast, ScrubSpeed.of("nonsense"))
        assertEquals(ScrubSpeed.Fast, ScrubSpeed.of(null))
    }

    // ---------------------------------------------------------------- keeping the tab in view

    @Test
    fun aTabInViewLeavesTheStripWhereItIs() {
        assertEquals(100f, ScrubFollow.reveal(100f, 400f, 150f, 210f))
    }

    @Test
    fun aTabPastTheRightEdgeScrollsJustEnoughToShowItWithAMargin() {
        // viewport 0..400, tab 380..440: its right edge plus 10 lands on the viewport's right edge
        assertEquals(50f, ScrubFollow.reveal(0f, 400f, 380f, 440f))
    }

    @Test
    fun aTabPastTheLeftEdgeScrollsBackToItWithAMargin() {
        assertEquals(190f, ScrubFollow.reveal(300f, 400f, 200f, 260f))
    }

    @Test
    fun theScrollNeverGoesBelowZeroOrBeyondTheStrip() {
        assertEquals(0f, ScrubFollow.reveal(40f, 400f, 0f, 60f))
        assertEquals(500f, ScrubFollow.reveal(0f, 400f, 880f, 940f, maxScroll = 500f))
    }

    @Test
    fun scrubbingThroughAllTabsFollowsTheTargetToTheEnd() {
        // 14 tabs of 64 dp in a 400 dp viewport: stepping tab by tab keeps each one in view, to the
        // last
        val strip = 14 * 64f
        var scroll = 0f
        for (i in 0 until 14) {
            val left = i * 64f
            scroll = ScrubFollow.reveal(scroll, 400f, left, left + 64f, strip - 400f)
            assertTrue("tab $i visible", left >= scroll && left + 64f <= scroll + 400f)
        }
        assertEquals(strip - 400f, scroll)
    }

    // ---------------------------------------------------- the neighbours stay in view too

    @Test
    fun theTabBeforeAndTheTabAfterStayInViewWithTheTarget() {
        // tabs of 64 dp; the target is tab 3 (192..256), flush with the left edge of a strip
        // scrolled to
        // 192: the tab before it (128..192) is cut off, so the strip scrolls back to show it
        val scroll =
            ScrubFollow.revealWithNeighbours(192f, 400f, 192f, 256f, 128f, 320f, maxScroll = 600f)
        assertEquals(118f, scroll)
        assertTrue(128f >= scroll && 320f <= scroll + 400f)
    }

    @Test
    fun theTabAfterTheTargetIsNotCutOffAtTheRightEdgeEither() {
        // viewport 0..400 and the target 300..364: its next tab (364..428) would be cut off
        val scroll = ScrubFollow.revealWithNeighbours(0f, 400f, 300f, 364f, 236f, 428f)
        assertEquals(38f, scroll)
    }

    @Test
    fun theFirstAndLastTabsHaveOnlyOneNeighbourAndTheStripStopsAtItsEnds() {
        assertEquals(0f, ScrubFollow.revealWithNeighbours(40f, 400f, 0f, 64f, null, 128f, 600f))
        assertEquals(
            600f,
            ScrubFollow.revealWithNeighbours(0f, 400f, 936f, 1000f, 872f, null, maxScroll = 600f),
        )
    }

    @Test
    fun whenThreeTabsDoNotFitOnlyTheTargetIsKeptInView() {
        // a 150 dp viewport cannot hold three 64 dp tabs: it behaves as reveal
        assertEquals(
            ScrubFollow.reveal(0f, 150f, 300f, 364f),
            ScrubFollow.revealWithNeighbours(0f, 150f, 300f, 364f, 236f, 428f),
        )
    }

    @Test
    fun scrubbingThroughAllTabsAlwaysShowsBothNeighbours() {
        val strip = 14 * 64f
        var scroll = 0f
        for (i in 0 until 14) {
            val left = i * 64f
            scroll =
                ScrubFollow.revealWithNeighbours(
                    scroll,
                    400f,
                    left,
                    left + 64f,
                    if (i > 0) left - 64f else null,
                    if (i < 13) left + 128f else null,
                    strip - 400f,
                )
            val from = if (i > 0) left - 64f else left
            val to = if (i < 13) left + 128f else left + 64f
            assertTrue("tab $i and its neighbours visible", from >= scroll && to <= scroll + 400f)
        }
    }
}
