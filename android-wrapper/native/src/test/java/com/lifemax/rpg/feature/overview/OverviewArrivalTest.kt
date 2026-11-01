package com.lifemax.rpg.feature.overview

import com.lifemax.rpg.feature.overview.data.WebOverviewRepository
import com.lifemax.rpg.feature.overview.presentation.ArrivalController
import com.lifemax.rpg.feature.overview.presentation.OverviewArrival
import com.lifemax.rpg.feature.overview.presentation.OverviewViewModel
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 3's arrival (plan §4, §5): each arrival the page raises plays once, only while the screen
 * shows, never with reduced motion; and the arrival's timeline follows the page's CSS and ovReplay.
 */
class OverviewArrivalTest {
    private fun fixture() =
        JSONObject(javaClass.getResource("/fixtures/overview-cards-established.json")!!.readText())

    @Test
    fun eachArrivalPlaysOnce() {
        val a = ArrivalController()
        a.projected("s", 0, showing = true, reduced = false)
        assertEquals("no arrival yet (0) never plays", 0L, a.playing)
        a.projected("s", 5, showing = true, reduced = false)
        assertEquals(5L, a.playing)
        a.played(5)
        assertEquals(0L, a.playing)
        // the same arrival in every later projection (a refresh, the screen back after a sheet)
        a.projected("s", 5, showing = true, reduced = false)
        assertEquals(0L, a.playing)
        a.projected("s", 9, showing = true, reduced = false)
        assertEquals(9L, a.playing)
        a.played(5) // an older run ending does not stop the new one
        assertEquals(9L, a.playing)
    }

    @Test
    fun anArrivalWhileHiddenOrUnderReducedMotionIsUsedUp() {
        val a = ArrivalController()
        a.projected("s", 3, showing = false, reduced = false)
        assertEquals(0L, a.playing)
        a.projected("s", 3, showing = true, reduced = false)
        assertEquals("not kept for later", 0L, a.playing)
        a.projected("s", 4, showing = true, reduced = true)
        assertEquals(0L, a.playing)
        a.projected("s", 4, showing = true, reduced = false)
        assertEquals(0L, a.playing)
    }

    @Test
    fun hidingCutsAnArrivalShortForGood() {
        val a = ArrivalController()
        a.projected("s", 7, showing = true, reduced = false)
        a.hidden()
        assertEquals(0L, a.playing)
        a.projected("s", 7, showing = true, reduced = false)
        assertEquals(0L, a.playing)
    }

    @Test
    fun aReloadedPageIsANewSession() {
        val a = ArrivalController()
        a.projected("old", 7, showing = true, reduced = false)
        a.played(7)
        a.projected("new", 7, showing = true, reduced = false)
        assertEquals(7L, a.playing)
    }

    /** The view model feeds it: an open with a new arrival plays, a reopen with the same stays. */
    @Test
    fun theViewModelPlaysAnArrivalOnceWhileShowing() {
        val repo = WebOverviewRepository()
        val vm = OverviewViewModel(repo, OverviewRecorder())
        var sequence = 1L
        fun open(arrival: Long, reduced: Boolean = false) {
            val j = fixture().put("sequence", ++sequence).put("arrival", arrival)
            j.getJSONObject("motion").put("reduced", reduced)
            repo.accept(j, true)
            vm.opened()
        }
        fun update(arrival: Long) {
            repo.accept(fixture().put("sequence", ++sequence).put("arrival", arrival))
            vm.projected()
        }
        open(100)
        assertEquals(100L, vm.arrival.playing)
        update(100)
        assertEquals(100L, vm.arrival.playing)
        vm.arrival.played(100)
        vm.hidden()
        open(100) // back after a sheet: the page kept the counter
        assertEquals(0L, vm.arrival.playing)
        vm.hidden()
        open(200, reduced = true)
        assertEquals(0L, vm.arrival.playing)
        vm.hidden()
        open(300)
        assertEquals(300L, vm.arrival.playing)
        vm.hidden()
        assertEquals(0L, vm.arrival.playing)
    }

    @Test
    fun theTimelineFollowsThePage() {
        val e = 1e-4f
        // viewIn: .4 s, 30 ms apart, the ninth row and after at .24 s
        assertEquals(0f, OverviewArrival.card(0f, 0), e)
        assertEquals(0f, OverviewArrival.card(30f, 1), e)
        assertEquals(1f, OverviewArrival.card(400f, 0), e)
        assertEquals(0f, OverviewArrival.card(240f, 12), e)
        assertEquals(1f, OverviewArrival.card(640f, 12), e)
        assertTrue(OverviewArrival.card(200f, 0) > .5f) // ease-out: well past half at half time
        assertEquals(10f, OverviewArrival.cardDrop(0f), e)
        // ovRing 1.15 s, ovGrow .8 s, the fills 1.1 s
        assertEquals(0f, OverviewArrival.ring(0f), e)
        assertTrue(OverviewArrival.ring(1100f) < 1f)
        assertEquals(1f, OverviewArrival.ring(1150f), e)
        assertEquals(1f, OverviewArrival.bars(800f), e)
        assertTrue(OverviewArrival.fill(1000f) < 1f)
        assertEquals(1f, OverviewArrival.fill(1100f), e)
        // the count-up: 900 ms, 1 - (1 - u)^3, rounded
        assertEquals(0, OverviewArrival.count(120, 0f))
        assertEquals(105, OverviewArrival.count(120, 450f)) // 120 x .875
        assertEquals(120, OverviewArrival.count(120, 900f))
        assertEquals(120, OverviewArrival.count(120, 5000f))
        // it lasts until the ring has swept in, or the dial's last session has arrived
        assertEquals(1150f, OverviewArrival.end(3), e)
        assertEquals(250f + 70f * 14 + 450f, OverviewArrival.end(15), e)
    }
}
