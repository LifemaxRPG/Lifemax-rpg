package com.lifemax.rpg.feature.pet.room

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The blend between two camera steps, as numbers: how much of each step a piece is drawn with, for
 * a camera that moves, rests, and waits for a step a background warmer is still making.
 */
class StepBlendTest {
    private val all = StepReady { true }

    /** How much of step [i] the blend draws (1 for the base, but for the pixels that fade out). */
    private fun StepBlend.share(i: Int): Float =
        when (i) {
            over -> weight
            base -> 1f - weight
            else -> 0f
        }

    @Test
    fun theWeightIsContinuousInTheSwayAndExactlyOneStepAtEachStep() {
        val b = StepBlend(N)
        b.update(-1f, DT, all)
        var last = FloatArray(2 * N + 1) { b.share(it) }
        val frames = 4000
        for (k in 1..frames) {
            val sway = -1f + 2f * k / frames
            b.update(sway, DT, all)
            assertEquals("follows the camera at $sway", sway * N + N, b.shown, 1e-4f)
            val now = FloatArray(2 * N + 1) { b.share(it) }
            for (i in now.indices) {
                assertTrue(
                    "step $i jumps ${abs(now[i] - last[i])} at sway $sway",
                    abs(now[i] - last[i]) <= 2f * N / frames + 1e-4f,
                )
            }
            last = now
        }
        for (i in 0..2 * N) {
            val c = StepBlend(N)
            c.update(0f, DT, all)
            c.update((i - N).toFloat() / N + 1e-3f, DT, all) // moving
            c.update((i - N).toFloat() / N, DT, all) // onto the step
            assertEquals("at step $i alone", i, c.base)
            assertEquals(-1, c.over)
            assertEquals(0f, c.weight, 0f)
        }
    }

    @Test
    fun halfWayTheTwoStepsAreDrawnHalfEachAndTheBaseIsTheOneNearerRest() {
        val b = StepBlend(N)
        b.update(0f, DT, all)
        b.update(0.625f, DT, all) // between steps 6 and 7 of 0..8
        assertEquals(6, b.base)
        assertEquals(7, b.over)
        assertEquals(0.5f, b.weight, 1e-5f)
        b.update(-0.625f, DT, all) // between 1 and 2: 2 is nearer rest
        assertEquals(2, b.base)
        assertEquals(1, b.over)
        assertEquals(0.5f, b.weight, 1e-5f)
        b.update(0.1f, DT, all) // between rest and 5
        assertEquals(N, b.base)
        assertEquals(N + 1, b.over)
    }

    @Test
    fun theFirstFrameIsAWholeStepAsBefore() {
        val b = StepBlend(N)
        b.update(0.6f, DT, all) // 6.4: step 6, the nearest
        assertEquals(6, b.base)
        assertEquals(-1, b.over)
        val sheetsLike = Math.round(0.6f * N + N)
        assertEquals(sheetsLike, b.nearest)
    }

    @Test
    fun aCameraThatStopsBetweenStepsSettlesOnTheStepTheRoomDrewBeforeAndStaysThere() {
        val b = StepBlend(N)
        b.update(0f, DT, all)
        // a pan that ends at 0.6 (6.4 steps), coming from below
        for (k in 1..60) b.update(0.6f * k / 60, DT, all)
        assertEquals(6, b.base)
        assertTrue("still blending as it stops", b.over == 7 && b.weight > 0f)
        var frames = 0
        while (b.over != -1) {
            b.update(0.6f, DT, all)
            frames++
            assertTrue("settles within half a second ($frames frames)", frames <= 30)
        }
        assertEquals(6, b.base)
        assertEquals(6f, b.shown, 0f)
        // and nothing changes after
        repeat(600) {
            b.update(0.6f, DT, all)
            assertEquals(6, b.base)
            assertEquals(-1, b.over)
        }
        // hysteresis as before: a camera resting a little past the middle keeps its step
        val h = StepBlend(N)
        h.update(0.6f, DT, all) // on 6
        for (k in 1..30) h.update(0.6f + 0.05f * k / 30, DT, all) // to 6.6
        repeat(60) { h.update(0.65f, DT, all) } // 6.6: nearer 7, within the hysteresis of 6
        assertEquals(6, h.base)
        assertEquals(-1, h.over)
    }

    @Test
    fun theSettleNeverJumps() {
        val b = StepBlend(N)
        b.update(0f, DT, all)
        for (k in 1..40) b.update(0.55f * k / 40, DT, all)
        var last = b.shown
        repeat(120) {
            b.update(0.55f, DT, all)
            assertTrue(abs(b.shown - last) <= BlendTuning.SETTLE_STEPS_PER_S * DT + 1e-5f)
            last = b.shown
        }
    }

    @Test
    fun aStepThatIsNotMadeIsNeverEnteredAndFadesInWhenItIs() {
        var made = N + 1 // steps 0 until made are ready
        val ready = StepReady { it < made }
        val b = StepBlend(N)
        b.update(0f, DT, ready)
        for (k in 1..60) {
            b.update(0.9f * k / 60, DT, ready)
            assertTrue("never past the last made step: ${b.shown}", b.shown <= N.toFloat())
            assertTrue(b.over == -1 || b.over < made)
        }
        made = 2 * N + 1 // the warmer is done
        var last = b.shown
        var frames = 0
        // to rest on 7 (7.6 steps, reached from below: the hysteresis keeps 7, as before)
        while (b.over != -1 || b.shown < 7f) {
            b.update(0.9f, DT, ready)
            assertTrue(
                "moves on at the settle speed, no jump",
                b.shown - last <= BlendTuning.SETTLE_STEPS_PER_S * DT + 1e-5f,
            )
            last = b.shown
            frames++
            assertTrue(frames < 600)
        }
        assertEquals(7, b.base)
        // with the warmer not even done with rest, the first frame takes the nearest made step
        val c = StepBlend(N)
        c.update(0.9f, DT, StepReady { it == N })
        assertEquals(N, c.base)
    }

    @Test
    fun aRoomThatNeverSwaysIsAlwaysItsOnlyStep() {
        val b = StepBlend(0)
        b.update(0.7f, DT, all)
        b.update(-0.3f, DT, all)
        assertEquals(0, b.base)
        assertEquals(-1, b.over)
        assertEquals(0f, b.sway, 0f)
    }

    @Test
    fun resetStartsAgainOnAWholeStep() {
        val b = StepBlend(N)
        b.update(0f, DT, all)
        b.update(0.33f, DT, all)
        assertTrue(b.over != -1)
        b.reset()
        b.update(0.33f, DT, all)
        assertEquals(-1, b.over)
        assertEquals(Math.round(0.33f * N + N), b.base)
    }

    private companion object {
        const val N = 4
        const val DT = 1f / 60f
    }
}
