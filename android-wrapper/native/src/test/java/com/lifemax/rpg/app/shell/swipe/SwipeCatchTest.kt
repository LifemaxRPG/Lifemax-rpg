package com.lifemax.rpg.app.shell.swipe

import com.lifemax.rpg.app.shell.swipe.SwipeFixtures.state
import com.lifemax.rpg.app.shell.swipe.SwipeInput.Catch
import com.lifemax.rpg.app.shell.swipe.SwipeInput.Closed
import com.lifemax.rpg.app.shell.swipe.SwipeInput.Drawn
import com.lifemax.rpg.app.shell.swipe.SwipeInput.Opened
import com.lifemax.rpg.app.shell.swipe.SwipeInput.Scene
import com.lifemax.rpg.app.shell.swipe.SwipeInput.Settle
import com.lifemax.rpg.app.shell.swipe.SwipeStage.Companion.ANCHOR
import com.lifemax.rpg.app.shell.swipe.SwipeStage.Companion.W
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Chained swipes (CONTRACT A34 item 8): a finger takes hold of a screen still sliding. */
class SwipeCatchTest {
    /** A cancel that has begun to spring back, 30 ms in. */
    private fun cancelling(): SwipeStage {
        val s = SwipeStage().home()
        s.grab().dragTo(0.3f).release(dp = 0f)
        assertNull("a cancel", (s.model.phase as SwipePhase.Settling).commit)
        return s.wait(30)
    }

    /**
     * A commit whose hand-over is done, the destination still sliding in, 40 ms after the release.
     */
    private fun handedOver(): SwipeStage {
        val s = SwipeStage().home()
        val request = s.commitNext()
        s.send(Settle(request.token, SettleOutcome.Native)).wait(40)
        s.send(Opened("meals"), Scene(state("meals")), Drawn("meals"), Closed("character"))
        assertTrue("still sliding", s.model.phase is SwipePhase.Settling)
        return s
    }

    @Test
    fun aCancelSpringingBackCanBeCaughtWhereItStands() {
        val s = cancelling()
        val before = s.pane("character")
        assertTrue("moving: ${before.tx}", before.tx < 0 && before.tx > -W / 2)
        assertEquals(Catching("character", before.tx), s.model.catching(s.env()))
        assertFalse("the screen a finger may catch takes touches", before.shielded)
        assertTrue("the peek beside it does not", s.pane("meals").shielded)

        s.send(Catch("character", ANCHOR))
        assertTrue(s.model.phase is SwipePhase.Dragging)
        assertEquals(
            "no jump at the catch",
            before.tx.toFloat(),
            s.pane("character").tx.toFloat(),
            1f,
        )
        assertEquals(before.tx + W, s.pane("meals").tx)
        assertTrue("nothing was sent yet", s.sent.isEmpty())
        assertTrue("and no haptic for a catch", s.haptics.isEmpty())
    }

    @Test
    fun theCaughtDragFollowsTheFingerFromWhereTheScreenStands() {
        val s = cancelling()
        val tx = s.pane("character").tx
        s.send(Catch("character", ANCHOR))
        // a little further left: the screen goes further along the same side, on the curve
        s.dragTo(0.05f)
        val further = s.pane("character").tx
        assertTrue("$further beyond $tx", further < tx)
        // back to the right past where it was caught: it comes back with the finger
        s.dragTo(-0.2f, steps = 8)
        assertTrue("back toward rest", s.pane("character").tx > tx)
    }

    @Test
    fun aCaughtDragCarriedPastTheCatchCommitsAsAFreshSwipe() {
        val s = cancelling()
        s.send(Catch("character", ANCHOR))
        s.dragTo(0.45f).release(dp = 100f)
        val request = s.sent.single()
        assertEquals("character", request.from)
        assertEquals("food", request.to)
        assertEquals(1, request.dir)
        assertEquals(Via.Drag, request.via)
        assertEquals("character#1", request.token)
        s.send(Settle(request.token, SettleOutcome.Native))
        s.send(Opened("meals"), Scene(state("meals")), Drawn("meals"), Closed("character"))
        s.wait(600)
        assertEquals(SwipePhase.Idle, s.model.phase)
        assertEquals(PaneStage(0, PaneVisibility.Shown), s.pane("meals"))
    }

    @Test
    fun aCaughtScreenReleasedAtRestSpringsBack() {
        val s = cancelling()
        s.send(Catch("character", ANCHOR))
        s.release(dp = 0f)
        assertNull((s.model.phase as SwipePhase.Settling).commit)
        s.wait(600)
        assertEquals(SwipePhase.Idle, s.model.phase)
        assertEquals(PaneStage(0, PaneVisibility.Shown), s.pane("character"))
        assertTrue(s.sent.isEmpty())
    }

    @Test
    fun aCommitHandedOverCanBeCaughtOnTheDestination() {
        val s = handedOver()
        val caught = s.model.catching(s.env())
        assertNotNull(caught)
        assertEquals("meals", caught!!.pane)
        val meals = s.pane("meals")
        assertTrue("sliding in from the right: ${meals.tx}", meals.tx > 0 && meals.tx < W)
        assertFalse(meals.shielded)
        assertTrue("the old source beside it is shielded", s.pane("character").shielded)

        s.send(Catch("meals", ANCHOR))
        assertEquals(meals.tx.toFloat(), s.pane("meals").tx.toFloat(), 1f)
        // the side it shows is the tab it came from, one width back
        assertEquals(s.pane("meals").tx - W, s.pane("character").tx)
        assertEquals(PaneVisibility.Shown, s.pane("character").visibility)
    }

    @Test
    fun pulledBackOverTheCatchTheDestinationSwipesBackToTheSource() {
        val s = handedOver()
        s.send(Catch("meals", ANCHOR))
        // toward the previous tab, the finger moving right
        s.dragTo(0.6f, dir = -1).release(dp = 100f, dir = -1)
        val request = s.sent.last()
        assertEquals("meals", request.from)
        assertEquals("character", request.to)
        assertEquals(-1, request.dir)
        s.send(Settle(request.token, SettleOutcome.Native))
        s.send(Opened("character"), Scene(state("character")), Drawn("character"), Closed("meals"))
        s.wait(600)
        assertEquals(SwipePhase.Idle, s.model.phase)
        assertEquals(PaneStage(0, PaneVisibility.Shown), s.pane("character"))
        // the first commit was taken over by the catch; the second one is the hand-over that ends
        assertEquals(1, s.handovers)
    }

    @Test
    fun aCaughtDestinationPulledTheOtherWayGoesOnToTheNextTab() {
        val s = SwipeStage().home(composed = listOf("character", "meals", "log"))
        val request = s.commitNext()
        s.send(Settle(request.token, SettleOutcome.Native)).wait(40)
        s.send(Opened("meals"), Scene(state("meals")), Drawn("meals"), Closed("character"))
        s.send(Catch("meals", ANCHOR))
        // the finger moves left, back through the screen's rest and on: the other side's tab
        s.dragTo(0.5f, dir = 1, steps = 20)
        val drag = s.model.phase as SwipePhase.Dragging
        assertEquals("flipped to the next side", 1, drag.drag.dir)
    }

    @Test
    fun aCommitStillWaitingForThePageIsNotCaught() {
        val s = SwipeStage().home()
        val request = s.commitNext()
        s.wait(40)
        assertNull("no answer yet", s.model.catching(s.env()))
        s.send(Settle(request.token, SettleOutcome.Native)).wait(10)
        assertNull("the destination has not opened", s.model.catching(s.env()))
        val before = s.model
        s.send(Catch("character", ANCHOR))
        assertEquals("a catch that is not allowed does nothing", before, s.model)
        s.send(Opened("meals"), Scene(state("meals")), Drawn("meals"))
        assertNull("the source has not closed", s.model.catching(s.env()))
    }

    @Test
    fun aSettleThatHasLandedIsNotCaught() {
        val s = SwipeStage().home()
        s.commitNext()
        s.wait(600)
        assertTrue(s.model.phase is SwipePhase.AwaitPage)
        assertNull(s.model.catching(s.env()))
        val rest = SwipeStage().home()
        rest.grab().dragTo(0.2f).release()
        rest.wait(600)
        assertEquals(SwipePhase.Idle, rest.model.phase)
        assertNull(rest.model.catching(rest.env()))
    }

    @Test
    fun noCatchWhenThePageNoLongerAllowsASwipeOrMotionIsOff() {
        val s = cancelling()
        s.send(Scene(state("character", on = false)))
        assertNull(s.model.catching(s.env()))
        // motion off: the settle is instant, nothing is left to catch
        val still = SwipeStage(scale = 0f).home()
        still.grab().dragTo(0.3f).release()
        assertNull(still.model.catching(still.env()))
    }

    @Test
    fun noCatchWhenAnotherScreenIsOpenToo() {
        val s = cancelling()
        s.send(Opened("meals"))
        assertTrue("a foreign open aborts the swipe", s.model.phase == SwipePhase.Idle)
        assertNull(s.model.catching(s.env()))
    }

    @Test
    fun aCatchNamingAnotherScreenDoesNothing() {
        val s = cancelling()
        val before = s.model
        s.send(Catch("meals", ANCHOR))
        assertEquals(before, s.model)
    }

    @Test
    fun aScreenCaughtOnAWallBandStartsOnTheBandWithoutAJump() {
        // Character is the first native tab; its previous neighbour is the web Overview: a drag
        // toward it is a rubber band, or a veil slide; a catch on it must not jump either way
        val s = SwipeStage().home()
        s.grab(dir = -1).dragTo(0.3f, dir = -1).release(dp = 0f, dir = -1)
        s.wait(20)
        val tx = s.pane("character").tx
        if (s.model.catching(s.env()) != null) {
            s.send(Catch("character", ANCHOR))
            assertEquals(tx.toFloat(), s.pane("character").tx.toFloat(), 1f)
            s.release(dp = 0f, dir = -1)
        }
        s.wait(800)
        assertEquals(SwipePhase.Idle, s.model.phase)
    }

    @Test
    fun manyCatchesInARowAlwaysEndAtRest() {
        val s = SwipeStage().home()
        s.grab().dragTo(0.3f).release(dp = 0f)
        repeat(8) {
            s.wait(25)
            if (s.model.catching(s.env()) != null) {
                s.send(Catch("character", ANCHOR))
                s.dragTo(0.02f, steps = 2).release(dp = 0f)
            }
        }
        s.wait(800)
        assertEquals(SwipePhase.Idle, s.model.phase)
        assertEquals(PaneStage(0, PaneVisibility.Shown), s.pane("character"))
        assertTrue(s.sent.isEmpty())
        assertTrue(s.traces.isEmpty())
    }

    @Test
    fun theCaughtOffsetIsContinuousAcrossEveryStageOfASettle() {
        for (ms in listOf(5L, 15L, 40L, 80L, 120L)) {
            val s = SwipeStage().home()
            s.grab().dragTo(0.4f).release(dp = 0f)
            s.wait(ms)
            val tx = s.pane("character").tx
            if (s.model.catching(s.env()) == null) continue
            s.send(Catch("character", ANCHOR))
            assertTrue(
                "at $ms ms: $tx vs ${s.pane("character").tx}",
                abs(s.pane("character").tx - tx) <= 1,
            )
        }
    }
}
