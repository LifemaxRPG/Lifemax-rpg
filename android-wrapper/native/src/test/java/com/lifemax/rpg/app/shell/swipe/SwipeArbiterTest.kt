package com.lifemax.rpg.app.shell.swipe

import com.lifemax.rpg.app.shell.swipe.SwipeArbiter.Phase
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeArbiterTest {
    /** A 412 dp wide phone at density 3, with an 8 dp touch slop. */
    private val facts = TouchFacts(widthPx = 1236, density = 3f, touchSlopPx = 24f)
    private val tune = SwipeTune.DEFAULT
    private val slop = 24f

    private fun down(
        x: Float = 600f,
        y: Float = 1000f,
        facts: TouchFacts = this.facts,
        gate: SwipeGate? = null,
        tune: SwipeTune = this.tune,
    ) = SwipeArbiter("character").down(x, y, facts, gate, tune)

    /** DOWN at (600, 1000), then the candidate event 24 px to the left. */
    private fun candidate(): SwipeArbiter {
        val step = down().arbiter.move(600f - slop, 1000f)
        assertFalse("never on the candidate event itself", step.intercept)
        assertNull(step.input)
        assertTrue(step.arbiter.phase is Phase.Candidate)
        return step.arbiter
    }

    private fun claimed(): SwipeArbiter = candidate().move(560f, 1002f).arbiter

    @Test
    fun theEdgeZoneIsTheGestureInsetOr24Dp() {
        for (insetDp in listOf(0, 24, 48)) {
            val zone = maxOf(insetDp, 24) * 3f
            val f = facts.copy(gestureLeftPx = insetDp * 3, gestureRightPx = insetDp * 3)
            val right = facts.widthPx - zone
            assertEquals("$insetDp left", SwipeGate.Edge, gateAt(zone - 1f, f))
            assertNull("$insetDp left", gateAt(zone, f))
            assertEquals("$insetDp right", SwipeGate.Edge, gateAt(right + 1f, f))
            assertNull("$insetDp right", gateAt(right, f))
        }
    }

    private fun gateAt(x: Float, f: TouchFacts): SwipeGate? {
        val step = down(x = x, facts = f)
        return (step.arbiter.phase as? Phase.Passive)?.gate.also {
            assertEquals(it == null, step.input == SwipeInput.Arm("character"))
        }
    }

    @Test
    fun theTunedEdgeFloorApplies() {
        val wide = tune.copy(edgeDp = 40f)
        assertEquals(
            SwipeGate.Edge,
            (down(x = 119f, tune = wide).arbiter.phase as Phase.Passive).gate,
        )
        assertEquals(SwipeInput.Arm("character"), down(x = 120f, tune = wide).input)
    }

    @Test
    fun theLockComesExactlyOneEventAfterTheCandidate() {
        val step = candidate().move(570f, 1003f)
        assertTrue(step.intercept)
        assertEquals(SwipeInput.DragStart(anchorX = 576f, dir = 1, x = 570f), step.input)
        assertEquals(Phase.Claimed(576f, 1), step.arbiter.phase)
        assertTrue(step.arbiter.consumes)
    }

    @Test
    fun aDragToTheRightGoesToThePreviousTab() {
        val c = down().arbiter.move(630f, 1000f).arbiter
        val step = c.move(640f, 1000f)
        assertEquals(SwipeInput.DragStart(anchorX = 624f, dir = -1, x = 640f), step.input)
    }

    @Test
    fun theSlopIsSubtractedSoTheScreenStartsAtZero() {
        val step = candidate().move(600f - slop, 1000f)
        val start = step.input as SwipeInput.DragStart
        assertEquals(start.x, start.anchorX, 0f)
        // the model sees no travel at the claim
        val model = SwipeStage.armedOn("character")
        val env = SwipeEnv(0L, facts.widthPx, facts.density)
        val dragging = model.on(start, env).model.phase as SwipePhase.Dragging
        assertEquals(0f, dragging.drag.u(env), 0f)
        assertEquals(0f, dragging.drag.offset(env, dragging.gesture), 0f)
    }

    @Test
    fun aChildThatClaimsOnTheCandidateEventKeepsTheGesture() {
        val step = candidate().childClaimed()
        assertEquals(SwipeInput.Disarm, step.input)
        val next = step.arbiter.move(500f, 1000f)
        assertFalse(next.intercept)
        assertNull(next.input)
        assertTrue(next.arbiter.phase is Phase.Passive)
        assertFalse(next.arbiter.consumes)
    }

    @Test
    fun aNoSwipeRegionTakesTheDown() {
        val step = down().arbiter.childClaimed()
        assertEquals(SwipeInput.Disarm, step.input)
        assertFalse(step.arbiter.move(400f, 1000f).arbiter.move(300f, 1000f).intercept)
    }

    @Test
    fun aClaimedDragIgnoresALateDisallow() {
        val step = claimed().childClaimed()
        assertNull(step.input)
        assertTrue(step.arbiter.phase is Phase.Claimed)
    }

    @Test
    fun aVerticalStartIsDeadForTheWholeGesture() {
        val dead = down().arbiter.move(605f, 1000f + slop)
        assertEquals(SwipeInput.Disarm, dead.input)
        var a = dead.arbiter
        for (x in listOf(500f, 400f, 300f, 200f)) {
            val step = a.move(x, 1024f)
            assertFalse(step.intercept)
            assertNull(step.input)
            a = step.arbiter
        }
    }

    @Test
    fun aDiagonalUnderTheRatioIsDead() {
        // 30 < 1.3 · 24
        assertEquals(SwipeInput.Disarm, down().arbiter.move(570f, 1024f).input)
        // 32 ≥ 1.3 · 24
        assertTrue(down().arbiter.move(568f, 1024f).arbiter.phase is Phase.Candidate)
    }

    @Test
    fun theTunedSlopAndRatioApply() {
        val firm = tune.copy(slopFactor = 2f, lockRatio = 2f)
        val a = down(tune = firm).arbiter
        assertTrue(a.move(600f - slop, 1000f).arbiter.phase is Phase.Pending)
        assertTrue(a.move(552f, 1000f).arbiter.phase is Phase.Candidate)
        // 48 < 2 · 25, but under the doubled slop of 48 it is not dead yet
        assertTrue(a.move(552f, 1025f).arbiter.phase is Phase.Pending)
        assertEquals(SwipeInput.Disarm, a.move(560f, 1049f).input)
    }

    @Test
    fun aSlopFactorUnderOneStillLetsAChildScrollerClaimFirst() {
        // the page may send any factor; a LazyRow claims at the platform's slop, so the lock never
        // takes less, and the child still has the candidate event to itself
        var a = down(tune = SwipeTune.parse(JSONObject().put("slopFactor", 0.75))).arbiter
        for (dx in 1 until slop.toInt()) {
            val step = a.move(600f - dx, 1000f)
            assertFalse("$dx px", step.intercept)
            assertTrue("$dx px", step.arbiter.phase is Phase.Pending)
            a = step.arbiter
        }
        val candidate = a.move(600f - slop, 1000f)
        assertFalse(candidate.intercept)
        assertTrue(candidate.arbiter.phase is Phase.Candidate)
    }

    @Test
    fun aCandidateThatFallsBackUnderTheSlopWaits() {
        val step = candidate().move(590f, 1000f)
        assertFalse(step.intercept)
        assertTrue(step.arbiter.phase is Phase.Pending)
        // and needs a fresh candidate event before it claims
        val again = step.arbiter.move(570f, 1000f)
        assertFalse(again.intercept)
        assertTrue(again.arbiter.move(560f, 1000f).intercept)
    }

    @Test
    fun aCandidateThatTurnsAroundStartsOver() {
        val step = candidate().move(630f, 1000f)
        assertFalse(step.intercept)
        assertEquals(1f, (step.arbiter.phase as Phase.Candidate).sign, 0f)
    }

    @Test
    fun aSecondPointerBeforeTheClaimEndsTheGesture() {
        val step = candidate().pointerDown()
        assertEquals(SwipeInput.Disarm, step.input)
        assertFalse(step.arbiter.move(500f, 1000f).intercept)
    }

    @Test
    fun aSecondPointerAfterTheClaimSpringsBackAndConsumesUntilAllAreUp() {
        val step = claimed().pointerDown()
        assertEquals(SwipeInput.DragCancel, step.input)
        assertEquals(Phase.Draining, step.arbiter.phase)
        val moved = step.arbiter.move(300f, 1000f)
        assertNull(moved.input)
        assertTrue(moved.arbiter.consumes)
        val up = moved.arbiter.up(0f)
        assertNull(up.input)
        assertEquals(Phase.Idle, up.arbiter.phase)
        assertFalse(up.arbiter.consumes)
    }

    @Test
    fun theReleaseAndTheSystemCancelEndTheDrag() {
        assertEquals(SwipeInput.DragMove(500f), claimed().move(500f, 1000f).input)
        assertEquals(SwipeInput.DragEnd(-3600f), claimed().up(-3600f).input)
        assertEquals(SwipeInput.DragCancel, claimed().cancel().input)
        assertEquals(SwipeInput.Disarm, candidate().up(-3600f).input)
        assertEquals(SwipeInput.Disarm, down().arbiter.cancel().input)
    }

    @Test
    fun theGatesAtTheDown() {
        val failing =
            mapOf(
                SwipeGate.Ime to facts.copy(imeVisible = true),
                SwipeGate.NoFocus to facts.copy(windowFocused = false),
                SwipeGate.TouchExploration to facts.copy(touchExploration = true),
                SwipeGate.Pointers to facts.copy(pointers = 2),
                SwipeGate.Tool to facts.copy(tool = ToolKind.Mouse),
                SwipeGate.Tool to facts.copy(tool = ToolKind.Other),
            )
        for ((gate, f) in failing) {
            val step = down(facts = f)
            assertEquals(gate, (step.arbiter.phase as Phase.Passive).gate)
            assertNull(step.input)
            assertFalse(step.arbiter.move(500f, 1000f).arbiter.move(400f, 1000f).intercept)
        }
        assertEquals(
            SwipeInput.Arm("character"),
            down(facts = facts.copy(tool = ToolKind.Stylus)).input,
        )
    }

    @Test
    fun theModelsGatesComeFirst() {
        for (gate in
            listOf(SwipeGate.NotSource, SwipeGate.StaleView, SwipeGate.Busy, SwipeGate.Off)) {
            val step = down(gate = gate, facts = facts.copy(imeVisible = true))
            assertEquals(gate, (step.arbiter.phase as Phase.Passive).gate)
            assertNull(step.input)
        }
    }

    @Test
    fun aModelThatStopsTrackingLetsTheGestureGo() {
        assertTrue(candidate().yieldGesture().phase is Phase.Passive)
        val drained = claimed().yieldGesture()
        assertEquals(Phase.Draining, drained.phase)
        assertNull(drained.move(100f, 1000f).input)
    }

    @Test
    fun aDownThatFindsAGestureStillRunningEndsItAndSkipsItself() {
        val step = claimed().down(600f, 1000f, facts, null, tune)
        assertEquals(SwipeInput.DragCancel, step.input)
        assertTrue(step.arbiter.phase is Phase.Passive)
        assertEquals(SwipeInput.Disarm, candidate().down(600f, 1000f, facts, null, tune).input)
    }

    @Test
    fun theArbitersInputsCarryTheModelThroughAWholeSwipe() {
        val env = SwipeEnv(0L, facts.widthPx, facts.density)
        var model = SwipeStage.homeOn("character")
        var arbiter = SwipeArbiter("character")
        val effects = mutableListOf<SwipeEffect>()
        fun feed(step: ArbiterStep) {
            arbiter = step.arbiter
            step.input?.let { input ->
                val next = model.on(input, env)
                model = next.model
                effects += next.effects
            }
        }
        feed(arbiter.down(600f, 1000f, facts, model.startGate("character"), model.tune))
        assertTrue(model.tracking("character"))
        feed(arbiter.move(576f, 1000f))
        feed(arbiter.move(560f, 1001f))
        assertTrue(model.phase is SwipePhase.Dragging)
        // 576 px of travel on a 1236 px screen is past the catch
        feed(arbiter.move(0f, 1004f))
        feed(arbiter.up(-4000f))
        assertEquals(Phase.Idle, arbiter.phase)
        assertEquals("meals", (model.phase as SwipePhase.Settling).commit?.dest)
        assertEquals(
            SwipeRequest("character#1", "character", "food", 1, 1, Via.Drag),
            effects.filterIsInstance<SwipeEffect.Send>().single().request,
        )
    }
}
