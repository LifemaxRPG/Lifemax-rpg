package com.lifemax.rpg.app.shell.swipe

import com.lifemax.rpg.app.shell.swipe.PaneVisibility.Gone
import com.lifemax.rpg.app.shell.swipe.PaneVisibility.Parked
import com.lifemax.rpg.app.shell.swipe.PaneVisibility.Shown
import com.lifemax.rpg.app.shell.swipe.SwipeFixtures.nativeRow
import com.lifemax.rpg.app.shell.swipe.SwipeFixtures.state
import com.lifemax.rpg.app.shell.swipe.SwipeInput.Arm
import com.lifemax.rpg.app.shell.swipe.SwipeInput.Closed
import com.lifemax.rpg.app.shell.swipe.SwipeInput.Drawn
import com.lifemax.rpg.app.shell.swipe.SwipeInput.Opened
import com.lifemax.rpg.app.shell.swipe.SwipeInput.Register
import com.lifemax.rpg.app.shell.swipe.SwipeInput.Scene
import com.lifemax.rpg.app.shell.swipe.SwipeInput.Settle
import com.lifemax.rpg.app.shell.swipe.SwipeStage.Companion.W
import kotlin.math.abs
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeModelTest {
    @Test
    fun aCommitIsAdoptedAndTheSourceLeavesOnlyOnceItIsOffScreen() {
        val s = SwipeStage().home()
        s.grab().dragTo(0.55f)
        assertEquals(listOf(HapticMoment.Latch), s.haptics)
        // dragging: the source follows the finger, Food's screen sits one width further
        val dragging = s.scene()
        assertEquals(Shown, dragging.panes.getValue("meals").visibility)
        assertEquals(
            dragging.panes.getValue("character").tx + W,
            dragging.panes.getValue("meals").tx,
        )
        assertFalse("the source takes the touches", dragging.panes.getValue("character").shielded)
        assertTrue("the peek is not open", dragging.panes.getValue("meals").a11yHidden)

        s.release(dp = 100f)
        assertEquals(
            SwipeRequest("character#1", "character", "food", 1, 1, Via.Drag),
            s.sent.single(),
        )
        assertNull("Food's screen is composed already", s.model.awaitingDraw)
        assertEquals(listOf(HapticMoment.Latch), s.haptics)
        assertTrue(s.model.phase is SwipePhase.Settling)

        val before = s.model
        s.send(Settle("character#1", SettleOutcome.Native))
        assertEquals("a native answer only waits", before, s.model)
        s.wait(40)
        val moving = s.scene()
        val sourceTx = moving.panes.getValue("character").tx
        assertTrue("sliding out: $sourceTx", sourceTx < 0 && sourceTx > -W)
        assertTrue(
            "nothing can touch a moving screen",
            moving.panes.values.filter { it.visibility == Shown }.all { it.shielded },
        )

        // the page navigates: the destination opens and adopts the running spring
        s.send(Opened("meals"), Scene(state("meals")))
        val adopted = s.scene()
        assertEquals(sourceTx, adopted.panes.getValue("character").tx)
        assertEquals(sourceTx + W, adopted.panes.getValue("meals").tx)
        assertFalse("open now", adopted.panes.getValue("meals").a11yHidden)

        // the source's close waits until it is off screen
        s.send(Closed("character"))
        val closing = s.pane("character")
        assertEquals(Shown, closing.visibility)
        assertEquals(sourceTx, closing.tx)
        assertTrue(closing.a11yHidden)

        s.wait(500)
        assertEquals(SwipePhase.Idle, s.model.phase)
        assertEquals(PaneStage(0, Shown), s.pane("meals"))
        // parked after landing: the old source waits at −W, where it ended
        assertEquals(PaneStage(-W, Parked, a11yHidden = true), s.pane("character"))
        assertTrue(s.traces.isEmpty())
    }

    @Test
    fun aRefusalSpringsBackFromWhereTheScreenIs() {
        val s = SwipeStage().home()
        val request = s.commitNext()
        s.wait(30)
        val before = s.scene()
        s.send(Settle(request.token, SettleOutcome.Refused))
        assertEquals(before, s.scene())
        assertEquals(listOf(HapticMoment.Latch, HapticMoment.Refused), s.haptics)
        assertNull((s.model.phase as SwipePhase.Settling).commit)
        s.wait(500)
        assertEquals(SwipePhase.Idle, s.model.phase)
        assertEquals(PaneStage(0, Shown), s.pane("character"))
        assertEquals(PaneStage(W, Parked, a11yHidden = true), s.pane("meals"))
    }

    @Test
    fun aRefusalAfterLandingSpringsBackToo() {
        val s = SwipeStage().home()
        val request = s.commitNext()
        s.wait(600)
        assertTrue(s.model.phase is SwipePhase.AwaitPage)
        assertEquals(-W, s.pane("character").tx)
        s.send(Settle(request.token, SettleOutcome.Refused)).wait(30)
        assertTrue(s.pane("character").tx in -W + 1..-1)
        s.wait(500)
        assertEquals(PaneStage(0, Shown), s.pane("character"))
    }

    @Test
    fun anAnswerWithAStaleTokenIsIgnored() {
        val s = SwipeStage().home()
        s.commitNext()
        val before = s.model
        s.send(
            Settle("character#7", SettleOutcome.Refused),
            Settle("meals#1", SettleOutcome.Native),
        )
        assertEquals(before, s.model)
        assertEquals(listOf(HapticMoment.Latch), s.haptics)
    }

    @Test
    fun theSourceClosingBeforeTheDestinationOpensSwapsToTheVeil() {
        val s = SwipeStage().home()
        val request = s.commitNext()
        s.send(Settle(request.token, SettleOutcome.Native)).wait(40)
        // the destination fell back to web, so the page closed the source at once (phase 2: the
        // veil takes Food's place and reveals the page; SwipeEdgesTest has the whole path)
        s.send(Closed("character"))
        assertTrue(s.traces.single().startsWith("fallback"))
        assertEquals(request.token, s.model.holdsChrome)
        val swapped = s.scene()
        assertEquals("Food", swapped.veil?.tab?.label)
        assertTrue(swapped.panes.getValue("meals").visibility != Shown)
        s.wait(1000)
        assertEquals(SwipePhase.Idle, s.model.phase)
        assertNull(s.model.holdsChrome)
        assertTrue(s.scene().panes.values.all { it.visibility == Gone })
    }

    @Test
    fun theWatchdogSpringsBackWhenThePageNeverAnswers() {
        val s = SwipeStage().home()
        s.commitNext()
        assertEquals(s.t * SwipeStage.MS + SwipeModel.WATCHDOG_NANOS, s.model.deadline)
        s.wait(2400)
        assertTrue(s.model.phase is SwipePhase.AwaitPage)
        s.wait(150)
        assertTrue(s.traces.single().startsWith("watchdog"))
        assertNull(s.model.deadline)
        s.wait(500)
        assertEquals(SwipePhase.Idle, s.model.phase)
        assertEquals(PaneStage(0, Shown), s.pane("character"))
    }

    @Test
    fun theWatchdogLetsGoWhenTheSourceNeverCloses() {
        val s = SwipeStage().home()
        val request = s.commitNext()
        s.send(Settle(request.token, SettleOutcome.Native), Opened("meals"), Scene(state("meals")))
        s.wait(2600)
        assertEquals(SwipePhase.Idle, s.model.phase)
        assertTrue(s.traces.single().startsWith("watchdog"))
        // both are open as far as the page said; the newest is on top
        assertEquals(listOf("character", "meals"), s.model.open)
    }

    @Test
    fun theWatchdogLeavesASlowSettleAloneOnceThePageHasDoneItsPart() {
        // animator duration scale 10: the settle outlasts the watchdog's 2.5 s
        val s = SwipeStage(scale = 10f).home()
        val request = s.commitNext()
        s.send(Settle(request.token, SettleOutcome.Native), Opened("meals"))
        assertEquals(s.t * SwipeStage.MS + SwipeModel.WATCHDOG_NANOS, s.model.deadline)
        s.send(Scene(state("meals")), Closed("character"))
        assertNull("nothing left to wait for", s.model.deadline)
        s.wait(2600)
        assertTrue("still sliding", s.model.phase is SwipePhase.Settling)
        s.wait(2000)
        assertEquals(SwipePhase.Idle, s.model.phase)
        assertTrue(s.traces.isEmpty())
        assertEquals(1, s.handovers)
        assertEquals(PaneStage(0, Shown), s.pane("meals"))
    }

    @Test
    fun aForeignOpenMidDragAborts() {
        val s = SwipeStage().home(composed = listOf("character", "meals", "log"))
        s.grab().dragTo(0.3f)
        s.send(Opened("log"))
        assertEquals(SwipePhase.Idle, s.model.phase)
        assertFalse(s.model.tracking("character"))
        assertEquals("abort: open log", s.traces.single())
        assertEquals(PaneStage(0, Shown), s.pane("log"))
        assertEquals(PaneStage(0, Shown), s.pane("character"))
        // the rest of the gesture is ignored
        s.send(SwipeInput.DragMove(100f), SwipeInput.DragEnd(-9000f))
        assertTrue(s.sent.isEmpty())
    }

    @Test
    fun aForeignOpenOrCloseDuringTheSettleAborts() {
        val open = SwipeStage().home(composed = listOf("character", "meals", "log"))
        open.commitNext()
        open.send(Opened("log"))
        assertEquals(SwipePhase.Idle, open.model.phase)
        val close = SwipeStage().home()
        close.send(
            Settle(close.commitNext().token, SettleOutcome.Native),
            Opened("meals"),
            Closed("meals"),
        )
        assertEquals(SwipePhase.Idle, close.model.phase)
        assertEquals(PaneStage(0, Shown), close.pane("character"))
    }

    @Test
    fun aSceneForAnotherViewAbortsTheSettle() {
        val s = SwipeStage().home()
        s.commitNext()
        s.send(Scene(state("meals")))
        assertTrue(s.model.phase is SwipePhase.Settling)
        s.send(Scene(state("log")))
        assertEquals(SwipePhase.Idle, s.model.phase)
    }

    @Test
    fun offMidDragSpringsBack() {
        val s = SwipeStage().home()
        s.grab().dragTo(0.3f)
        s.send(Scene(state("character", on = false)))
        assertNull((s.model.phase as SwipePhase.Settling).commit)
        assertFalse(s.model.tracking("character"))
        s.wait(500)
        assertEquals(SwipePhase.Idle, s.model.phase)
        assertEquals(PaneStage(0, Shown), s.pane("character"))
        assertEquals("parking stops while the swipe is off", Gone, s.pane("meals").visibility)
    }

    @Test
    fun aPageLoadMidSettleForgetsThePage() {
        val s = SwipeStage().home()
        val request = s.commitNext()
        s.wait(40).send(SwipeInput.Drop)
        assertEquals(SwipeModel(panes = s.model.panes, serial = 1), s.model)
        assertTrue(s.scene().panes.values.all { it.visibility == Gone })
        s.send(Settle(request.token, SettleOutcome.Refused))
        assertEquals(listOf(HapticMoment.Latch), s.haptics)
    }

    @Test
    fun aColdDestinationSwapsInUnderTheVeil() {
        val s = SwipeStage().home(composed = listOf("character"))
        s.grab().dragTo(0.55f)
        val food = state("character").tabs[2]
        val dragging = s.scene()
        assertEquals(VeilStage(dragging.panes.getValue("character").tx + W, food), dragging.veil)

        s.release(dp = 100f)
        assertEquals("meals", s.model.awaitingDraw)
        s.send(Settle("character#1", SettleOutcome.Native)).wait(40)
        // the page opens Food cold: it composes INVISIBLE under the veil
        s.send(Register("meals"), Opened("meals"), Scene(state("meals")))
        val composing = s.scene()
        val slot = composing.panes.getValue("character").tx + W
        assertEquals(VeilStage(slot, food), composing.veil)
        assertEquals(Parked, composing.panes.getValue("meals").visibility)
        assertEquals(slot, composing.panes.getValue("meals").tx)

        // its first OnPreDraw swaps it in at the veil's place, in one frame
        s.send(Drawn("meals"))
        assertNull(s.model.awaitingDraw)
        val swapped = s.scene()
        assertNull(swapped.veil)
        assertEquals(Shown, swapped.panes.getValue("meals").visibility)
        assertEquals(slot, swapped.panes.getValue("meals").tx)

        s.send(Closed("character")).wait(500)
        assertEquals(SwipePhase.Idle, s.model.phase)
        assertEquals(PaneStage(0, Shown), s.pane("meals"))
    }

    @Test
    fun aColdDestinationThatDrawsAfterTheLandingWaitsUnderTheVeil() {
        val s = SwipeStage().home(composed = listOf("character"))
        s.commitNext()
        s.send(Settle("character#1", SettleOutcome.Native), Register("meals"), Opened("meals"))
        s.send(Scene(state("meals")), Closed("character")).wait(600)
        assertTrue(s.model.phase is SwipePhase.AwaitPage)
        assertEquals(0, s.scene().veil!!.tx)
        assertEquals(PaneStage(0, Parked, shielded = true, a11yHidden = false), s.pane("meals"))
        s.send(Drawn("meals"))
        assertEquals(SwipePhase.Idle, s.model.phase)
        assertEquals(PaneStage(0, Shown), s.pane("meals"))
    }

    @Test
    fun aWallGivesALittleOverTheBlankVeilAndAlwaysSpringsBack() {
        val s = SwipeStage().home()
        // Character's previous tab is the Overview, a web tab: a wall in phase 1
        s.grab(dir = -1).dragTo(0.5f, dir = -1)
        val wall = s.scene()
        val tx = wall.panes.getValue("character").tx
        assertEquals(TensionCurve.wall(0.5f, SwipeTune.DEFAULT) * W, tx.toFloat(), 1f)
        assertTrue(tx < SwipeTune.DEFAULT.wallMax * W)
        assertEquals(VeilStage(tx - W, null), wall.veil)
        s.release(dp = 5000f, dir = -1)
        assertTrue(s.sent.isEmpty())
        assertTrue("nothing plays at a wall", s.haptics.isEmpty())
        s.wait(500)
        assertEquals(PaneStage(0, Shown), s.pane("character"))
    }

    @Test
    fun aFlingCommitClicksOnceAtTheRelease() {
        val s = SwipeStage().home()
        s.grab().dragTo(0.2f).release(dp = 1500f)
        assertEquals(Via.Fling, s.sent.single().via)
        assertEquals(listOf(HapticMoment.Fling), s.haptics)
    }

    @Test
    fun aCasualFlickSpringsBack() {
        val s = SwipeStage().home()
        s.grab().dragTo(0.3f).release(dp = 900f)
        assertTrue(s.sent.isEmpty())
        s.wait(500)
        assertEquals(PaneStage(0, Shown), s.pane("character"))
    }

    @Test
    fun theCatchClicksEachWayAndTheReleaseRuleSeesTheLatch() {
        val s = SwipeStage().home()
        s.grab().dragTo(0.5f).dragTo(0.3f).dragTo(0.5f).dragTo(0.42f)
        assertEquals(
            listOf(HapticMoment.Latch, HapticMoment.Unlatch, HapticMoment.Latch),
            s.haptics,
        )
        s.release()
        assertEquals(Via.Drag, s.sent.single().via)
    }

    @Test
    fun hapticsFollowThePage() {
        val off = SwipeStage().home(haptics = "off")
        off.send(Settle(off.commitNext().token, SettleOutcome.Refused))
        assertTrue(off.haptics.isEmpty())
        val strong = SwipeStage().home(haptics = "strong")
        strong.commitNext()
        assertEquals(
            SwipeEffect.Haptic(HapticMoment.Latch, HapticLevel.Strong, HapticStyle.Threshold),
            strong.effects.first { it is SwipeEffect.Haptic },
        )
        // the owner's tune key turns the detent off, but a refusal still says no
        val quiet = SwipeStage()
        quiet.send(Register("character"), Register("meals"), Opened("character"))
        val tune = JSONObject().put("haptic", "off")
        quiet.send(Scene(state("character", tune = tune)))
        quiet.send(Settle(quiet.commitNext().token, SettleOutcome.Refused))
        assertEquals(listOf(HapticMoment.Refused), quiet.haptics)
    }

    @Test
    fun theDragTurnsToTheOtherSideWithoutAJump() {
        val s =
            SwipeStage()
                .home(
                    view = "meals",
                    composed = listOf("character", "meals", "log"),
                    bar = nativeRow,
                )
        // let the pop settle: it moves faster than the finger on purpose
        s.grab().dragTo(0.55f).wait(400)
        var last = s.pane("meals").tx
        // back over the catch, past the anchor and on toward Character, 4 px per frame
        while (s.x < SwipeStage.ANCHOR + 0.3f * W) {
            s.wait(8).send(SwipeInput.DragMove(s.x + 4f))
            val tx = s.pane("meals").tx
            assertTrue("jumped from $last to $tx at x=${s.x}", abs(tx - last) <= 16)
            last = tx
        }
        val drag = (s.model.phase as SwipePhase.Dragging).drag
        assertEquals(-1, drag.dir)
        assertEquals(
            PaneStage(last - W, Shown, shielded = true, a11yHidden = true),
            s.pane("character"),
        )
        assertEquals(PaneStage(W, Parked, a11yHidden = true), s.pane("log"))
    }

    @Test
    fun withMotionOffNothingFollowsAndTheSwitchIsInstant() {
        val s = SwipeStage().home(motion = false)
        s.grab().dragTo(0.55f)
        assertEquals(PaneStage(0, Shown), s.pane("character"))
        assertNull(s.scene().veil)
        assertEquals("the click is the only feedback", listOf(HapticMoment.Latch), s.haptics)
        s.release()
        assertTrue(s.model.phase is SwipePhase.AwaitPage)
        assertEquals(-W, s.pane("character").tx)
        assertEquals(0, s.pane("meals").tx)
        s.send(Opened("meals"), Scene(state("meals", motion = false)), Closed("character"))
        assertEquals(SwipePhase.Idle, s.model.phase)
    }

    @Test
    fun animatorScaleZeroSettlesAtOnce() {
        val s = SwipeStage(scale = 0f).home()
        s.grab().dragTo(0.55f)
        assertEquals("the pop is instant too", -0.55f * W, s.pane("character").tx.toFloat(), 1f)
        s.release()
        assertTrue(s.model.phase is SwipePhase.AwaitPage)
        val back = SwipeStage(scale = 0f).home()
        back.grab().dragTo(0.3f).release()
        assertEquals(SwipePhase.Idle, back.model.phase)
    }

    @Test
    fun theModelsGates() {
        val s = SwipeStage()
        assertEquals(SwipeGate.Off, s.model.startGate("character"))
        s.home(on = false)
        assertEquals(SwipeGate.Off, s.model.startGate("character"))
        s.send(Scene(state("character")))
        assertNull(s.model.startGate("character"))
        assertEquals(SwipeGate.NotOwner, s.model.startGate("meals"))
        s.send(Scene(state("meals")))
        assertEquals(SwipeGate.StaleView, s.model.startGate("character"))
        s.send(Scene(state("character")), Opened("meals"))
        assertEquals(
            "the make-before-break overlap",
            SwipeGate.NotOwner,
            s.model.startGate("meals"),
        )
        s.send(Closed("meals"), Closed("character"))
        assertEquals(SwipeGate.NotOwner, s.model.startGate("character"))

        val ld = SwipeStage().home(view = "life-director", composed = listOf("life-director"))
        assertEquals(SwipeGate.NotSource, ld.model.startGate("life-director"))

        val busy = SwipeStage().home()
        busy.commitNext()
        busy.send(Opened("meals"), Scene(state("meals")), Closed("character"))
        assertEquals(SwipeGate.Busy, busy.model.startGate("meals"))
        val ignored = busy.model
        busy.send(Arm("meals"))
        assertEquals(ignored, busy.model)
    }

    @Test
    fun theArbiterReadsThePagesTune() {
        assertEquals(SwipeTune.DEFAULT, SwipeModel().tune)
        val s = SwipeStage().home()
        assertEquals(SwipeTune.DEFAULT, s.model.tune)
        val firm = JSONObject().put("lockRatio", 2).put("edgeDp", 40)
        s.send(Scene(state("character", tune = firm)))
        assertEquals(SwipeTune(lockRatio = 2f, edgeDp = 40f), s.model.tune)
    }

    @Test
    fun aSceneThatNoLongerAllowsTheSwipeDisarms() {
        val s = SwipeStage().home()
        s.send(Arm("character"))
        assertTrue(s.model.tracking("character"))
        s.send(Scene(state("character", on = false)))
        assertEquals(SwipePhase.Idle, s.model.phase)
        assertFalse(s.model.tracking("character"))
    }

    @Test
    fun framesAreNeededOnlyWhileSomethingMovesOnItsOwn() {
        val s = SwipeStage().home()
        assertFalse(s.model.needsFrame(s.env()))
        s.grab().dragTo(0.3f)
        assertFalse("following the finger needs no frames of its own", s.model.needsFrame(s.env()))
        s.dragTo(0.5f)
        assertTrue("the pop", s.model.needsFrame(s.env()))
        s.wait(600)
        assertFalse(s.model.needsFrame(s.env()))
        s.release()
        assertTrue(s.model.needsFrame(s.env()))
    }

    @Test
    fun removingAScreenInTheGestureAborts() {
        val s = SwipeStage().home(composed = listOf("character", "meals", "log"))
        s.grab().dragTo(0.3f)
        s.send(SwipeInput.Removed("log"))
        assertTrue("log is not beside Character", s.model.phase is SwipePhase.Dragging)
        s.send(SwipeInput.Removed("meals"))
        assertEquals(SwipePhase.Idle, s.model.phase)
    }
}
