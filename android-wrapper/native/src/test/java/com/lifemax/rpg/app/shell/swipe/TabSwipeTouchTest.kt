package com.lifemax.rpg.app.shell.swipe

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** The fingers on the shared bars, which a screen's own events never count (touches are split). */
class TabSwipeTouchTest {
    private val chrome = Any()
    private val overview = Any()

    @After fun lift() = listOf(chrome, overview).forEach { TabSwipe.touched(it, false) }

    @Test
    fun aFingerOnTheBarsCountsAtAScreensDownUntilItLifts() {
        assertEquals(1, TabSwipe.fingers(1))
        TabSwipe.touched(chrome, true)
        assertEquals(2, TabSwipe.fingers(1))
        TabSwipe.touched(chrome, true) // a further finger on the same frame is one of its pointers
        assertEquals(2, TabSwipe.fingers(1))
        TabSwipe.touched(overview, true)
        assertEquals(3, TabSwipe.fingers(1))
        TabSwipe.touched(chrome, false)
        TabSwipe.touched(overview, false)
        assertEquals(1, TabSwipe.fingers(1))
    }

    @Test
    fun noSwipeStartsBesideAFingerRestingOnABar() {
        val facts = TouchFacts(widthPx = 1080, density = 2.625f, touchSlopPx = 22f)
        TabSwipe.touched(chrome, true)
        val down = facts.copy(pointers = TabSwipe.fingers(1)) // as TabScreenView counts them
        assertEquals(SwipeGate.Pointers, SwipeArbiter.platformGate(540f, down, SwipeTune.DEFAULT))
        TabSwipe.touched(chrome, false)
        val alone = facts.copy(pointers = TabSwipe.fingers(1))
        assertEquals(null, SwipeArbiter.platformGate(540f, alone, SwipeTune.DEFAULT))
    }
}
