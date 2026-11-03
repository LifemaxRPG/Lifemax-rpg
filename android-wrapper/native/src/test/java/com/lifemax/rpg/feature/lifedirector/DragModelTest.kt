package com.lifemax.rpg.feature.lifedirector

import com.lifemax.rpg.feature.lifedirector.domain.BlockKind
import com.lifemax.rpg.feature.lifedirector.domain.DragEnd
import com.lifemax.rpg.feature.lifedirector.domain.DragModel
import com.lifemax.rpg.feature.lifedirector.domain.DragPoint
import com.lifemax.rpg.feature.lifedirector.domain.OrderRow
import com.lifemax.rpg.feature.lifedirector.domain.SessionState
import com.lifemax.rpg.feature.lifedirector.domain.Tap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The arithmetic of lifting a session to the trash, with no view in it. */
class DragModelTest {
    private val density = 2.5f
    private val trash = DragModel.target(1000f, 2000f, bottomInset = 200f, density = density)
    private val row =
        OrderRow(
            key = "ahead:1:i_1",
            start = 0,
            end = 1,
            time = "7:15 PM",
            length = "1h",
            kind = BlockKind.TASK,
            title = "Resistance Training",
            icon = "dumbbell",
            state = SessionState.PLAN,
            stateLabel = "",
            now = false,
            past = false,
            sub = "",
            chips = emptyList(),
            tap = Tap("inst", "i_1"),
        )

    // The lift threshold

    @Test
    fun aTouchLiftsOnlyAfterTheHoldAndOnlyIfItStayedPut() {
        assertFalse(DragModel.lifts(DragModel.LIFT_MS - 1, 0f, 8f))
        assertTrue(DragModel.lifts(DragModel.LIFT_MS, 0f, 8f))
        assertTrue(DragModel.lifts(DragModel.LIFT_MS, 8f, 8f))
        assertFalse(DragModel.lifts(DragModel.LIFT_MS * 3, 8.5f, 8f))
        assertEquals(350L, DragModel.LIFT_MS)
    }

    // Where the trash is

    @Test
    fun theTrashSitsCentredWithItsFootAboveTheBars() {
        assertEquals(500f, trash.centre.x, 0f)
        assertEquals(DragModel.TARGET_DP * density / 2, trash.radius, 0f)
        val foot = trash.centre.y + trash.radius
        assertEquals(2000f - 200f - DragModel.TARGET_LIFT_DP * density, foot, .01f)
    }

    // The hover hit-test and its hysteresis

    private fun at(dx: Float, dy: Float = 0f) = DragPoint(trash.centre.x + dx, trash.centre.y + dy)

    @Test
    fun theFingerMustComeWithinTheEnterMarginToArmTheTrash() {
        val enter = trash.radius + DragModel.ENTER_DP * density
        assertTrue(DragModel.hovers(false, at(0f), trash, density))
        assertTrue(DragModel.hovers(false, at(enter - 1f), trash, density))
        assertFalse(DragModel.hovers(false, at(enter + 1f), trash, density))
        assertFalse(DragModel.hovers(false, at(0f, -(enter + 1f)), trash, density))
    }

    @Test
    fun onceArmedItHoldsUntilTheFingerIsWellAway() {
        val enter = trash.radius + DragModel.ENTER_DP * density
        val exit = trash.radius + DragModel.EXIT_DP * density
        // between the two margins the answer is the previous one: no flicker on the edge
        val between = (enter + exit) / 2
        assertFalse(DragModel.hovers(false, at(between), trash, density))
        assertTrue(DragModel.hovers(true, at(between), trash, density))
        assertTrue(DragModel.hovers(true, at(exit - 1f), trash, density))
        assertFalse(DragModel.hovers(true, at(exit + 1f), trash, density))
    }

    @Test
    fun movingArmsAndDisarmsAndTheCardFollows() {
        val lifted = DragModel.lift(row, DragPoint(300f, 900f), DragPoint(40f, 30f))
        assertFalse(lifted.armed)
        val over = DragModel.move(lifted, at(10f), trash, density)
        assertTrue(over.armed)
        assertEquals(at(10f), over.finger)
        assertEquals(DragPoint(40f, 30f), over.grab)
        val away = DragModel.move(over, DragPoint(100f, 300f), trash, density)
        assertFalse(away.armed)
    }

    // Auto-scroll

    private val top = 150f
    private val bottom = 1500f

    @Test
    fun theListScrollsOnlyInABandAtEachEdge() {
        val edge = DragModel.EDGE_DP * density
        assertEquals(0f, DragModel.scroll(800f, top, bottom, false, density), 0f)
        assertEquals(0f, DragModel.scroll(top + edge, top, bottom, false, density), 0f)
        assertEquals(0f, DragModel.scroll(bottom - edge, top, bottom, false, density), 0f)
        assertTrue(DragModel.scroll(top + edge - 10f, top, bottom, false, density) < 0f)
        assertTrue(DragModel.scroll(bottom - edge + 10f, top, bottom, false, density) > 0f)
    }

    @Test
    fun theSpeedGrowsTowardTheEdgeAndStopsAtTheMaximum() {
        val max = DragModel.SCROLL_DP_S * density
        val edge = DragModel.EDGE_DP * density
        val shallow = DragModel.scroll(bottom - edge + 20f, top, bottom, false, density)
        val deep = DragModel.scroll(bottom - edge / 2, top, bottom, false, density)
        val atEdge = DragModel.scroll(bottom, top, bottom, false, density)
        val past = DragModel.scroll(bottom + 400f, top, bottom, false, density)
        assertTrue(0f < shallow && shallow < deep && deep < atEdge)
        assertEquals(max, atEdge, .01f)
        assertEquals(max, past, .01f)
        assertEquals(-max, DragModel.scroll(top - 400f, top, bottom, false, density), .01f)
        // halfway into the band it is a quarter of the speed: it eases in
        assertEquals(max / 4, deep, .01f)
    }

    @Test
    fun overTheTrashTheListHoldsStill() {
        assertEquals(0f, DragModel.scroll(bottom + 100f, top, bottom, true, density), 0f)
    }

    // Letting go, and cancelling

    @Test
    fun letGoOverTheTrashRemovesTheSessionAndAnywhereElsePutsItBack() {
        val lifted = DragModel.lift(row, DragPoint(300f, 900f), DragPoint(40f, 30f))
        assertEquals(DragEnd.PutBack, DragModel.release(lifted))
        val armed = DragModel.move(lifted, at(0f), trash, density)
        assertEquals(DragEnd.Remove("i_1"), DragModel.release(armed))
    }

    @Test
    fun cancellingAlwaysPutsItBackEvenOverTheTrash() {
        assertEquals(DragEnd.PutBack, DragModel.cancel())
    }
}
