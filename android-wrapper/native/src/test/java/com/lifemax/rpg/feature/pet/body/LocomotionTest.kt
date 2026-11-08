package com.lifemax.rpg.feature.pet.body

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Walking: eased speed, strides tied to speed, skids (turning round: [TurnTest]). */
class LocomotionTest {
    private val dt = 1f / 120f

    private fun walker(step: PlanStep, x: Float = 92f): CatBody =
        CatBody(seed = 3, startX = x).also { it.replacePlan(1, listOf(step)) }

    @Test
    fun speedEasesInAndOutInsteadOfJumping() {
        val body = walker(PlanStep.Walk(200f))
        var prevX = body.pose.x
        var prevV = 0f
        var maxAccel = 0f
        var topSpeed = 0f
        var at = ""
        repeat(120 * 5) {
            body.step(dt)
            // The final snap onto the goal is at most 0.05 px; it is not a change of speed.
            if (abs(body.pose.x - 200f) < 1e-4f) return@repeat
            val v = (body.pose.x - prevX) / dt
            if (abs(v - prevV) / dt > maxAccel)
                at = "x=${body.pose.x} v=$v prev=$prevV t=${it * dt}"
            maxAccel = maxOf(maxAccel, abs(v - prevV) / dt)
            topSpeed = maxOf(topSpeed, v)
            prevX = body.pose.x
            prevV = v
        }
        val limit = maxOf(Tuning.Walk.ACCEL, Tuning.Walk.DECEL) * 1.35f
        assertTrue("jerked: peak acceleration $maxAccel at $at", maxAccel < limit)
        assertEquals(Tuning.Walk.WALK_SPEED, topSpeed, 1.5f)
        assertEquals(200f, body.pose.x, 0.001f)
    }

    @Test
    fun theCatArrivesExactlyAndReportsIt() {
        val body = walker(PlanStep.Walk(140f))
        var arrived = false
        repeat(120 * 4) {
            body.step(dt)
            for (i in 0 until body.pose.events.count) {
                if (body.pose.events.type(i) == CatEventType.Arrived) arrived = true
            }
        }
        assertTrue(arrived)
        assertEquals(140f, body.pose.x, 0.0001f)
        assertEquals("idle_sit", body.pose.clipName)
    }

    @Test
    fun theStrideCoversTheSameGroundPerCycleAtEverySpeed() {
        // No foot slip: distance per walk cycle is the clip's stride length, walking or trotting.
        for (speedStep in listOf(PlanStep.Walk(300f), PlanStep.Walk(300f, run = true))) {
            val body = walker(speedStep, x = -100f)
            var cycles = 0
            var startX = Float.NaN
            var prevFrame = body.pose.frame
            var prevClip = body.pose.clip
            var endX = 0f
            repeat(120 * 4) {
                body.step(dt)
                val p = body.pose
                val steady = abs(p.x - -100f) > 40f && abs(300f - p.x) > 40f
                if (steady && p.clip == prevClip && p.frame < prevFrame) {
                    // Measure from one wrap of the cycle to a later wrap: whole cycles only.
                    if (startX.isNaN()) startX = p.x else cycles++
                    endX = p.x
                }
                prevFrame = p.frame
                prevClip = p.clip
            }
            val stride = Clips.all[body.pose.clip].stridePx
            assertTrue("no steady section: $cycles cycles", cycles >= 2)
            val perCycle = (endX - startX) / cycles
            assertEquals("stride at ${speedStep}", stride, perCycle, stride * 0.12f)
        }
    }

    @Test
    fun strideRateFollowsSpeedSoSlowWalkingStepsSlowly() {
        val slow =
            CatBody(seed = 3).also {
                it.replacePlan(1, listOf(PlanStep.Walk(300f)))
                it.weak = true
            }
        val fast = walker(PlanStep.Walk(300f))
        fun cyclesIn(body: CatBody): Int {
            var cycles = 0
            var prev = body.pose.frame
            repeat(120 * 3) {
                body.step(dt)
                if (body.pose.frame < prev && Clips.all[body.pose.clip].isLocomotion) cycles++
                prev = body.pose.frame
            }
            return cycles
        }
        assertTrue("weak cat should step less often", cyclesIn(slow) < cyclesIn(fast))
    }

    @Test
    fun walkThroughTrotToRunIsContinuous() {
        val body = walker(PlanStep.Walk(330f, run = true))
        var prevX = body.pose.x
        var prevV = 0f
        var maxJump = 0f
        val clips = HashSet<String>()
        repeat(120 * 4) {
            body.step(dt)
            if (abs(body.pose.x - 330f) < 1e-4f) return@repeat
            val v = (body.pose.x - prevX) / dt
            maxJump = maxOf(maxJump, abs(v - prevV))
            clips.add(body.pose.clipName)
            prevX = body.pose.x
            prevV = v
        }
        assertTrue(
            "speed jumped by $maxJump in one tick",
            maxJump < Tuning.Walk.RUN_DECEL * dt * 1.5f,
        )
        assertTrue("passed through walk and run: $clips", clips.containsAll(setOf("walk", "run")))
    }

    @Test
    fun aGallopStoppedHardSkids() {
        val body = walker(PlanStep.Walk(300f, run = true))
        var skidded = false
        var leaned = 0f
        repeat(120 * 5) {
            body.step(dt)
            if (body.pose.skid > 0.5f) skidded = true
            leaned = minOf(leaned, body.pose.rotation)
        }
        assertTrue("no skid on a hard stop", skidded)
        assertTrue("no backward lean in the skid: $leaned", leaned < -0.03f)
    }

    @Test
    fun aGentleWalkStopDoesNotSkid() {
        val body = walker(PlanStep.Walk(160f))
        repeat(120 * 4) {
            body.step(dt)
            assertEquals(0f, body.pose.skid, 0f)
        }
    }

    @Test
    fun aFootfallGivesTheBodyATinyBob() {
        val body = walker(PlanStep.Walk(300f))
        var peak = 0f
        repeat(120 * 3) {
            body.step(dt)
            peak = maxOf(peak, abs(body.pose.bob))
        }
        assertTrue("no bob", peak > 0.05f)
        assertTrue("bob too large: $peak", peak < 1.5f)
    }

    @Test
    fun walkingBackwardsInDepthShowsTheBackView() {
        val body = CatBody(seed = 3, startX = 100f, startZ = 60f)
        body.replacePlan(1, listOf(PlanStep.Walk(100f, z = 20f)))
        val clips = HashSet<String>()
        repeat(120 * 4) {
            body.step(dt)
            clips.add(body.pose.clipName)
        }
        assertTrue("expected walk_back in $clips", "walk_back" in clips)
        assertEquals(20f, body.pose.z, 0.001f)
    }
}
