package com.lifemax.rpg.feature.lifedirector

import com.lifemax.rpg.feature.lifedirector.domain.BlockKind
import com.lifemax.rpg.feature.lifedirector.domain.ChipSize
import com.lifemax.rpg.feature.lifedirector.domain.DragChip
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
        val lifted = DragModel.lift(row, DragPoint(300f, 900f))
        assertFalse(lifted.armed)
        val over = DragModel.move(lifted, at(10f), trash, density)
        assertTrue(over.armed)
        assertEquals(at(10f), over.finger)
        val away = DragModel.move(over, DragPoint(100f, 300f), trash, density)
        assertFalse(away.armed)
    }

    // The chip that stands for the lifted session

    private val chip = ChipSize(300f, 120f)
    private val list = 1000f

    private fun chipAt(finger: DragPoint, size: ChipSize = chip) =
        DragChip.origin(finger, size, list, density)

    @Test
    fun theChipIsCentredOnTheFingerAndAboveItSoTheFingerDoesNotCoverIt() {
        val at = chipAt(DragPoint(500f, 900f))
        assertEquals(350f, at.x, 0f)
        // its bottom edge is the gap above the finger
        assertEquals(900f - DragChip.GAP_DP * density, at.y + chip.height, 0f)
    }

    @Test
    fun theChipFollowsTheFingerAndIgnoresWhereItTookHold() {
        val a = chipAt(DragPoint(500f, 900f))
        val b = chipAt(DragPoint(560f, 1000f))
        assertEquals(60f, b.x - a.x, 0f)
        assertEquals(100f, b.y - a.y, 0f)
    }

    @Test
    fun theChipStaysWithinTheListsSides() {
        val side = DragChip.SIDE_DP * density
        assertEquals(side, chipAt(DragPoint(0f, 900f)).x, 0f)
        assertEquals(list - side - chip.width, chipAt(DragPoint(list, 900f)).x, 0f)
    }

    @Test
    fun aChipWiderThanTheListIsHeldAtTheLeftSideRatherThanThrownOffIt() {
        val wide = ChipSize(list, 120f)
        assertEquals(DragChip.SIDE_DP * density, chipAt(DragPoint(500f, 900f), wide).x, 0f)
    }

    @Test
    fun withNoRoomAboveTheFingerTheChipGoesBelowIt() {
        val at = chipAt(DragPoint(500f, 150f))
        assertEquals(150f + DragChip.GAP_DP * density, at.y, 0f)
        // just enough room above keeps it above
        val fits = DragChip.GAP_DP * density + chip.height
        assertEquals(0f, chipAt(DragPoint(500f, fits)).y, 0f)
    }

    @Test
    fun theChipIsAtMostSixtyPercentOfTheListWide() {
        assertEquals(600f, DragChip.maxWidth(list), 0f)
    }

    @Test
    fun aChipDroppedOnTheTrashIsCentredOnIt() {
        val at = DragChip.centredOn(trash.centre, chip)
        assertEquals(trash.centre.x, at.x + chip.width / 2, 0f)
        assertEquals(trash.centre.y, at.y + chip.height / 2, 0f)
    }

    @Test
    fun theTrashIsArmedByTheFingerEvenWhenTheChipIsAwayFromIt() {
        // the chip sits above the finger: the finger over the trash arms it, the chip's place does
        // not
        val finger = at(0f)
        assertTrue(DragModel.hovers(false, finger, trash, density))
        val chipAbove = chipAt(finger)
        assertTrue(chipAbove.y + chip.height < finger.y)
        val onlyTheChip = DragPoint(chipAbove.x + chip.width / 2, chipAbove.y + chip.height / 2)
        assertFalse(DragModel.hovers(false, onlyTheChip, trash, density))
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
        val lifted = DragModel.lift(row, DragPoint(300f, 900f))
        assertEquals(DragEnd.PutBack, DragModel.release(lifted))
        val armed = DragModel.move(lifted, at(0f), trash, density)
        assertEquals(DragEnd.Remove("i_1"), DragModel.release(armed))
    }

    @Test
    fun cancellingAlwaysPutsItBackEvenOverTheTrash() {
        assertEquals(DragEnd.PutBack, DragModel.cancel())
    }
}
