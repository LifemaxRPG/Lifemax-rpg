package com.lifemax.rpg.feature.pet.body

import org.junit.Test

/**
 * Golden traces: a recorded animation sheet for each core motion. A change to Tuning or to the body
 * shows up here as a diff of text a person can read. Regenerate with `RECORD=1`.
 */
class GoldenTraceTest {
    private fun cat(seed: Int = 7) = CatBody(seed = seed, startX = 92f)

    @Test
    fun walkStartAndStop() {
        val body = cat()
        body.replacePlan(1, listOf(PlanStep.Walk(140f)))
        TraceSupport.checkGolden("walk-start-stop", TraceSupport.record(body, 3.2f))
    }

    @Test
    fun walkToRun() {
        val body = cat()
        body.replacePlan(1, listOf(PlanStep.Walk(200f)))
        val sheet =
            TraceSupport.record(body, 4f) { frame ->
                if (frame == 66) body.replacePlan(2, listOf(PlanStep.Walk(330f, run = true)))
            }
        TraceSupport.checkGolden("walk-to-run", sheet)
    }

    @Test
    fun turn() {
        val body = cat()
        body.replacePlan(1, listOf(PlanStep.Face(-1), PlanStep.Act("idle_sit", 0.4f)))
        TraceSupport.checkGolden("turn", TraceSupport.record(body, 1f, sampleEveryFrames = 1))
    }

    @Test
    fun hopToTheTree() {
        val body = cat()
        body.replacePlan(1, body.routeTo(SurfaceId.TOP, 154f))
        TraceSupport.checkGolden("hop-to-tree", TraceSupport.record(body, 7f))
    }

    @Test
    fun sitThenGroom() {
        val body = cat()
        body.replacePlan(1, listOf(PlanStep.Act("idle_sit", 1f), PlanStep.Act("groom", 1.6f)))
        TraceSupport.checkGolden("sit-then-groom", TraceSupport.record(body, 3.4f))
    }

    @Test
    fun interruptMidWalk() {
        val body = cat()
        body.replacePlan(1, listOf(PlanStep.Walk(250f)))
        val sheet =
            TraceSupport.record(body, 3.5f, sampleEveryFrames = 3) { frame ->
                if (frame == 54) body.replacePlan(2, listOf(PlanStep.Walk(60f)))
            }
        TraceSupport.checkGolden("interrupt-mid-walk", sheet)
    }

    @Test
    fun emptyQueueIdles() {
        val body = cat()
        TraceSupport.checkGolden(
            "empty-queue-idles",
            TraceSupport.record(body, 24f, sampleEveryFrames = 30, format = TraceSupport::lifeRow),
        )
    }
}
