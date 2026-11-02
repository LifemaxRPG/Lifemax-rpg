package com.lifemax.rpg.feature.pet.body

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Determinism and frame-rate independence. */
class DeterminismTest {
    /** A busy script: walking, a hop, acting, a reaction, and an interrupt. */
    private fun script(body: CatBody, hz: Int, seconds: Float): List<String> {
        val prints = ArrayList<String>()
        val frames = Math.round(seconds * hz)
        val dt = 1f / hz
        body.replacePlan(
            1,
            body.routeTo(SurfaceId.SILL, 60f) +
                PlanStep.Act("groom", 1f) +
                PlanStep.Walk(150f, run = true),
        )
        val every = hz / 30
        for (frame in 1..frames) {
            if (frame == hz * 3 + 1) body.react(ReactionKind.STARTLE)
            if (frame == hz * 5 + 1) body.lookAt(10f, 70f)
            if (frame == hz * 6 + 1) body.setAffect(EarState.AIRPLANE_FLAT, TailState.QUESTION_MARK)
            body.step(dt)
            if (frame % every == 0) prints.add(TraceSupport.fingerprint(body.pose))
        }
        return prints
    }

    @Test
    fun theSameSeedReplaysTheSameTrace() {
        val a = script(CatBody(seed = 11), 60, 12f)
        val b = script(CatBody(seed = 11), 60, 12f)
        assertEquals(a, b)
    }

    @Test
    fun differentSeedsLiveDifferently() {
        fun idleTrace(seed: Int): List<String> {
            val body = CatBody(seed = seed)
            val out = ArrayList<String>()
            repeat(60 * 40) {
                body.step(1f / 60f)
                if (it % 30 == 0) out.add(TraceSupport.fingerprint(body.pose))
            }
            return out
        }
        assertNotEquals(idleTrace(1), idleTrace(2))
        assertEquals(idleTrace(3), idleTrace(3))
    }

    @Test
    fun equalPosesAtEqualTimesAcrossFrameRates() {
        // Compare at every 1/30 s mark: all of these rates land on it exactly. Scripted events are
        // issued before the step that ends at their time, so they happen at the same moment.
        val reference = script(CatBody(seed = 5), 120, 10f)
        for (hz in listOf(30, 60, 240)) {
            val other = script(CatBody(seed = 5), hz, 10f)
            val common = minOf(reference.size, other.size)
            assertTrue(common > 100)
            for (i in 0 until common) {
                assertEquals("at ${hz}Hz sample $i", reference[i], other[i])
            }
        }
    }

    @Test
    fun hostHiccupsDoNotChangeTheEndState() {
        // Frames of uneven length add up to the same simulated time and the same sub-steps.
        val steady = CatBody(seed = 8)
        val ragged = CatBody(seed = 8)
        steady.replacePlan(1, listOf(PlanStep.Walk(250f)))
        ragged.replacePlan(1, listOf(PlanStep.Walk(250f)))
        repeat(120 * 4) { steady.step(1f / 120f) }
        val pattern = floatArrayOf(1f / 60f, 1f / 120f, 1f / 40f, 1f / 120f, 1f / 30f)
        var total = 0f
        var i = 0
        while (total < 4f - 1e-4f) {
            val dt = pattern[i++ % pattern.size].coerceAtMost(4f - total)
            ragged.step(dt)
            total += dt
        }
        assertEquals(steady.pose.x, ragged.pose.x, 0.0001f)
        assertEquals(steady.pose.frame, ragged.pose.frame)
    }

    @Test
    fun aStalledFrameIsClippedNotReplayedForever() {
        val body = CatBody(seed = 1)
        body.replacePlan(1, listOf(PlanStep.Walk(300f)))
        body.step(30f)
        // At most MAX_FRAME_DT of simulated time: a cat does not teleport after the app was paused.
        assertTrue(body.pose.x < 92f + 0.25f * 40f)
    }
}
