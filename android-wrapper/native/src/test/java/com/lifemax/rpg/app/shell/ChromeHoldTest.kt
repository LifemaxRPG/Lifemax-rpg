package com.lifemax.rpg.app.shell

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The shared bars held through a tab swipe's reveal (plans/TAB_SWIPE.md §5, ChromeHost.hold). */
class ChromeHoldTest {
    @Test
    fun unheldTheReleaseGoesAsBefore() {
        val hold = ChromeHold()
        assertTrue("posted after 60 ms, as always", hold.leave())
        assertFalse(hold.unhold("meals#1"))
    }

    @Test
    fun aReleaseDuringAHoldWaitsAndRunsAtTheUnhold() {
        val hold = ChromeHold()
        hold.hold("meals#1")
        assertTrue(hold.held)
        assertFalse("the leave waits", hold.leave())
        assertFalse("another reveal's unhold changes nothing", hold.unhold("meals#2"))
        assertTrue(hold.held)
        assertTrue("the release runs now", hold.unhold("meals#1"))
        assertFalse(hold.held)
        assertFalse("once", hold.unhold("meals#1"))
    }

    @Test
    fun aHoldWithNoLeaveReleasesNothing() {
        val hold = ChromeHold()
        hold.hold("character#3")
        assertFalse(hold.unhold("character#3"))
        assertTrue(hold.leave())
    }

    @Test
    fun aScreenTakingTheBarsOrAnImmediateReleaseEndsTheWait() {
        val hold = ChromeHold()
        hold.hold("meals#1")
        assertFalse(hold.leave())
        // a native screen opened and took the bars (show), or closeAll / closeNow released them
        hold.cancel()
        assertFalse("nothing waits any more", hold.unhold("meals#1"))
        assertTrue(hold.leave())
    }

    @Test
    fun aNewerRevealTakesTheHoldOver() {
        val hold = ChromeHold()
        hold.hold("meals#1")
        assertFalse(hold.leave())
        hold.hold("meals#2")
        assertFalse(hold.unhold("meals#1"))
        assertTrue(hold.unhold("meals#2"))
    }
}
