package com.lifemax.rpg.feature.pet.body

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The springs behind squash, ears, tail and whiskers: settle time and bounded overshoot. */
class SpringTest {
    private val dt = Tuning.Time.SUBSTEP

    /** Step a spring from 0 toward 1; return peak position and the time it stays within 2%. */
    private fun stepResponse(params: SpringParams): Pair<Float, Float> {
        val spring = Spring(params)
        var peak = 0f
        var lastOutside = 0f
        for (i in 1..(10f / dt).toInt()) {
            spring.step(1f, dt)
            if (spring.pos > peak) peak = spring.pos
            if (abs(spring.pos - 1f) > SETTLE_BAND) lastOutside = i * dt
        }
        return peak to lastOutside
    }

    @Test
    fun criticalDampingNeverOvershootsAndSettlesFast() {
        val (peak, settle) = stepResponse(SpringParams(frequencyHz = 3f, dampingRatio = 1f))
        assertTrue("critical spring overshot: $peak", peak <= 1.001f)
        // Critical settling to 2% takes about 5.8 / omega.
        val omega = 2 * PI.toFloat() * 3f
        assertTrue("settled too slowly: $settle", settle < 6.5f / omega)
    }

    @Test
    fun underdampedOvershootMatchesTheory() {
        val zeta = 0.4f
        val (peak, settle) = stepResponse(SpringParams(frequencyHz = 5f, dampingRatio = zeta))
        val theory = exp(-PI * zeta / sqrt(1 - zeta * zeta)).toFloat()
        // Semi-implicit Euler damps slightly more than the exact solution; stay within 0.03.
        assertEquals("overshoot", theory, peak - 1f, 0.03f)
        // 2% settling is about 4 / (zeta * omega).
        val omega = 2 * PI.toFloat() * 5f
        assertTrue("ring-down too slow: $settle", settle < 4.6f / (zeta * omega))
    }

    @Test
    fun theBodyAndLimbSpringsStayWithinTheirOvershootBudget() {
        val budget =
            mapOf(
                "squash" to (Tuning.Springs.SQUASH to 0.2f),
                "ear" to (Tuning.Springs.EAR to 0.3f),
                "tailTip" to (Tuning.Springs.TAIL_TIP to 0.5f),
                "look" to (Tuning.Springs.LOOK to 0.02f),
            )
        for ((name, pair) in budget) {
            val (peak, settle) = stepResponse(pair.first)
            assertTrue("$name overshoots ${peak - 1f}", peak - 1f <= pair.second)
            assertTrue("$name settles in $settle s", settle < 2f)
        }
    }

    @Test
    fun aKickDiesAwayAndTheSpringReturnsToRest() {
        val spring = Spring(Tuning.Springs.EAR)
        spring.kick(9f)
        var peak = 0f
        repeat(120) {
            spring.step(0f, dt)
            peak = maxOf(peak, abs(spring.pos))
        }
        assertTrue("kick had no effect", peak > 0.05f)
        repeat(360) { spring.step(0f, dt) }
        assertTrue("ear still ringing", abs(spring.pos) < 0.001f && abs(spring.vel) < 0.01f)
    }

    @Test
    fun theTailTipLagsTheRootAndTheWaveSettles() {
        val tail = Tail(Rng(5))
        repeat(600) { tail.update(dt, TailState.NEUTRAL, 0f) }
        tail.update(dt, TailState.HIGH_FLAG, 0f)
        var tipBehindRoot = false
        repeat(60) {
            tail.update(dt, TailState.HIGH_FLAG, 0f)
            if (
                tail.bend(0) - Tuning.Tail.BASE_NEUTRAL > 0.1f && tail.bend(3) < tail.bend(0) * 0.3f
            ) {
                tipBehindRoot = true
            }
        }
        assertTrue("root moved but the chain did not lag", tipBehindRoot || tail.bend(0) > 0.3f)
        repeat(600) { tail.update(dt, TailState.HIGH_FLAG, 0f) }
        // Twitches may ring, but the root must have found its aim.
        assertEquals(Tuning.Tail.BASE_FLAG, tail.bend(0), 0.25f)
    }

    private companion object {
        const val SETTLE_BAND = 0.02f
    }
}
