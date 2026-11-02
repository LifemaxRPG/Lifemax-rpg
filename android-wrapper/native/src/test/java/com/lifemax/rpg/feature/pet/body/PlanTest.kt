package com.lifemax.rpg.feature.pet.body

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The plan queue: events, stale ids, interrupts that finish a beat, and idle life. */
class PlanTest {
    private val dt = 1f / 60f

    private class Seen(
        val type: CatEventType,
        val plan: Int,
        val step: Int,
        val last: Boolean,
        val tick: Int,
    )

    private fun run(
        body: CatBody,
        seconds: Float,
        tickBase: Int = 0,
        before: (Int) -> Unit = {},
    ): List<Seen> {
        val seen = ArrayList<Seen>()
        for (tick in 1..Math.round(seconds * 60)) {
            before(tick)
            body.step(dt)
            val e = body.pose.events
            for (i in 0 until e.count) {
                seen.add(
                    Seen(e.type(i), e.planId(i), e.stepIndex(i), e.isLastStep(i), tickBase + tick)
                )
            }
        }
        return seen
    }

    @Test
    fun stepsReportDoneInOrderWithPlanIdAndIndex() {
        val body = CatBody(seed = 1)
        body.replacePlan(
            9,
            listOf(PlanStep.Act("idle_sit", 0.5f), PlanStep.Walk(110f), PlanStep.Act("groom", 0.5f)),
        )
        val done = run(body, 4f).filter { it.type == CatEventType.StepDone }
        assertEquals(listOf(0, 1, 2), done.map { it.step })
        assertTrue(done.all { it.plan == 9 })
        assertEquals(listOf(false, false, true), done.map { it.last })
    }

    @Test
    fun aWalkReportsArrivedJustBeforeItsStepDone() {
        val body = CatBody(seed = 1)
        body.replacePlan(1, listOf(PlanStep.Walk(110f)))
        val events = run(body, 3f)
        val kinds = events.map { it.type }
        assertEquals(listOf(CatEventType.Arrived, CatEventType.StepDone), kinds)
    }

    @Test
    fun anEmptyQueueIdlesInsteadOfFreezing() {
        val body = CatBody(seed = 4)
        assertTrue(body.isIdle)
        var moved = false
        val first = body.pose.rotation
        var frames = HashSet<Int>()
        repeat(60 * 30) {
            body.step(dt)
            if (body.pose.rotation != first || body.pose.scaleY != 1f) moved = true
            frames.add(body.pose.frame)
        }
        assertTrue("an idle cat must keep moving", moved)
        assertTrue("the idle clip must animate: $frames", frames.size > 1)
        assertEquals("idle_sit", body.pose.clipName)
    }

    @Test
    fun theCatKeepsRestingPostureWhenAPlanEndsOnALoaf() {
        val body = CatBody(seed = 4)
        body.replacePlan(1, listOf(PlanStep.Act("loaf", 0.5f)))
        run(body, 3f)
        assertEquals("loaf", body.pose.clipName)
    }

    @Test
    fun aStalePlanIdIsIgnored() {
        val body = CatBody(seed = 1)
        assertTrue(body.replacePlan(5, listOf(PlanStep.Walk(200f))))
        assertFalse("older id", body.replacePlan(4, listOf(PlanStep.Walk(0f))))
        assertFalse("duplicate id", body.replacePlan(5, listOf(PlanStep.Walk(0f))))
        assertFalse("extending the wrong plan", body.extendPlan(4, listOf(PlanStep.Walk(0f))))
        assertEquals(5, body.planId)
        run(body, 1f)
        assertTrue("still heading right", body.pose.x > 92f)
    }

    @Test
    fun extendingTheCurrentPlanAppendsAndKeepsStepNumbers() {
        val body = CatBody(seed = 1)
        body.replacePlan(3, listOf(PlanStep.Act("idle_sit", 0.3f)))
        body.extendPlan(3, listOf(PlanStep.Act("groom", 0.3f)))
        val done = run(body, 1.5f).filter { it.type == CatEventType.StepDone }
        assertEquals(listOf(0, 1), done.map { it.step })
        // A page that was late sends more after the queue ran dry: the plan resumes.
        assertTrue(body.extendPlan(3, listOf(PlanStep.Act("loaf", 0.3f))))
        val late = run(body, 1f).filter { it.type == CatEventType.StepDone }
        assertEquals(listOf(2), late.map { it.step })
    }

    @Test
    fun interruptingReportsInterruptedAndFinishesTheFootfallFirst() {
        val body = CatBody(seed = 1)
        body.replacePlan(1, listOf(PlanStep.Walk(300f)))
        run(body, 0.9f)
        val sinceFootfall = HashSet<Int>()
        body.replacePlan(2, listOf(PlanStep.Act("loaf", 1f)))
        var cutAtTick = -1
        val events = ArrayList<Seen>()
        for (tick in 1..60) {
            body.step(dt)
            val e = body.pose.events
            for (i in 0 until e.count) events.add(
                Seen(e.type(i), e.planId(i), e.stepIndex(i), e.isLastStep(i), tick)
            )
            sinceFootfall.add(body.pose.frame)
            if (cutAtTick < 0 && body.planId == 2 && body.pose.clipName != "walk") cutAtTick = tick
        }
        val interrupted = events.first { it.type == CatEventType.Interrupted }
        assertEquals(1, interrupted.plan)
        assertEquals(1, interrupted.tick)
        assertTrue("the walk should not stop dead on the spot", sinceFootfall.size > 1)
        assertEquals(2, body.planId)
    }

    @Test
    fun theWalkStopsReportingForAPlanThatWasInterrupted() {
        val body = CatBody(seed = 1)
        body.replacePlan(1, listOf(PlanStep.Walk(120f), PlanStep.Walk(10f)))
        run(body, 0.5f)
        body.replacePlan(2, listOf(PlanStep.Act("loaf", 0.5f)))
        val events = run(body, 3f)
        assertTrue(
            "old plan must stay silent",
            events.none { it.plan == 1 && it.type != CatEventType.Interrupted },
        )
    }

    @Test
    fun anInterruptWaitsForABlinkToFinish() {
        val body = CatBody(seed = 1)
        // Run the idle clock until the cat is mid-blink.
        var guard = 0
        while (body.pose.frame !in Clips.IDLE_SIT.blinkFrames && guard++ < 60 * 40) body.step(dt)
        assertTrue("never blinked", guard < 60 * 40)
        val blinkFrame = body.pose.frame
        body.replacePlan(2, listOf(PlanStep.Walk(200f)))
        body.step(dt)
        assertEquals("still finishing the blink, not walking yet", "idle_sit", body.pose.clipName)
        assertTrue(body.pose.frame in Clips.IDLE_SIT.blinkFrames || body.pose.frame != blinkFrame)
        run(body, 1f)
        assertTrue(body.pose.x > 92f)
    }

    @Test
    fun anInterruptedHopStillLands() {
        val body = CatBody(seed = 1)
        body.replacePlan(1, listOf(PlanStep.Hop(SurfaceId.SILL, 60f)))
        run(body, 0.4f)
        body.replacePlan(2, listOf(PlanStep.Act("loaf", 0.5f)))
        run(body, 3f)
        assertTrue(body.pose.surface == SurfaceId.SILL || body.pose.surface == SurfaceId.FLOOR)
        assertEquals(World.groundY(body.pose.surface, body.pose.z), body.pose.y, 0.01f)
    }
}
