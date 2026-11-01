package com.lifemax.rpg.core.designsystem.feedback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The held celebrations' queue: each id plays once, never while hidden, in the page's order. */
class PageCelebrationTest {
    private val gain = XpGain("strength", "STR", 28, 0xFFFF8A50)

    private fun held(id: Long, gains: List<XpGain> = listOf(gain)) =
        HeldCelebration(id, 40, PagePoint(205.5f, 820f), gains)

    @Test
    fun playsEachIdOnce() {
        val q = CelebrationQueue()
        val list = listOf(held(1))
        assertEquals(1L, q.next(list, true)?.id)
        q.start(1)
        // the page sends the same award with every update for 2 s
        assertNull(q.next(list, true))
        assertNull(q.next(list, true))
    }

    @Test
    fun nothingStartsWhileHidden() {
        val q = CelebrationQueue()
        assertNull(q.next(listOf(held(1)), false))
        // shown again while the page still holds it: it plays then
        assertEquals(1L, q.next(listOf(held(1)), true)?.id)
    }

    @Test
    fun oneCutShortIsNotReplayed() {
        val q = CelebrationQueue()
        q.start(q.next(listOf(held(1)), true)!!.id)
        assertNull(q.next(listOf(held(1)), false))
        assertNull(q.next(listOf(held(1)), true))
    }

    @Test
    fun takesThemInTheOrderThePageHandedThem() {
        val q = CelebrationQueue()
        val list = listOf(held(4), held(5))
        assertEquals(4L, q.next(list, true)?.id)
        q.start(4)
        assertEquals(5L, q.next(list, true)?.id)
    }

    @Test
    fun skipsAnAwardWithoutGains() {
        val q = CelebrationQueue()
        assertEquals(2L, q.next(listOf(held(1, emptyList()), held(2)), true)?.id)
    }

    @Test
    fun forgetsIdsThePageDropped() {
        val q = CelebrationQueue()
        q.start(1)
        assertNull(q.next(listOf(held(1)), true))
        q.next(emptyList(), true)
        // a new page session may count from 1 again
        assertEquals(1L, q.next(listOf(held(1)), true)?.id)
    }

    @Test
    fun theAwardIsTheTotalAndItsGains() {
        assertEquals(XpAward(40, listOf(gain)), held(9).award)
    }
}
