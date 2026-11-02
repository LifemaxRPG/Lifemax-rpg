package com.lifemax.rpg.feature.pet.body

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The clip table (petAssets.anims) and how clips start and hand over to each other. */
class ClipsTest {
    private val dt = 1f / 120f

    @Test
    fun theTableMatchesThePetAssetsRows() {
        // name, row, frames, fps, bottom: copied from petAssets.html `anims`.
        val web =
            listOf(
                listOf("idle_sit", 0, 8, 7f, 47),
                listOf("walk", 1, 8, 12f, 46),
                listOf("run", 2, 6, 16f, 46),
                listOf("sleep", 3, 4, 2f, 47),
                listOf("loaf", 4, 4, 2f, 47),
                listOf("groom", 5, 8, 5f, 47),
                listOf("eat", 6, 6, 5f, 46),
                listOf("play", 7, 6, 9f, 47),
                listOf("stretch", 8, 7, 4f, 47),
                listOf("lookup", 9, 4, 3f, 47),
                listOf("slowblink", 10, 6, 4f, 47),
                listOf("purr", 11, 4, 4f, 47),
                listOf("rub", 12, 4, 5f, 46),
                listOf("knead", 13, 4, 4f, 47),
                listOf("meow", 14, 4, 6f, 47),
                listOf("leap", 15, 2, 4f, 46),
                listOf("idle_sit_w", 16, 8, 5f, 47),
                listOf("walk_w", 17, 8, 8f, 46),
                listOf("loaf_w", 18, 4, 1.5f, 47),
                listOf("sleep_w", 19, 4, 1.5f, 47),
                listOf("crouch", 20, 3, 7f, 47),
                listOf("scratch", 21, 8, 7f, 46),
                listOf("scratch_in", 22, 3, 7f, 47),
                listOf("scratch_out", 23, 3, 7f, 47),
                listOf("roll", 24, 8, 8f, 47),
                listOf("kick", 25, 6, 12f, 47),
                listOf("bat", 26, 8, 9f, 46),
                listOf("swat", 27, 6, 9f, 47),
            )
        for (r in web) {
            val c = Clips.find(r[0] as String)
            assertNotNull("missing clip ${r[0]}", c)
            assertEquals(r[0] as String, r[1], c!!.row)
            assertEquals(r[0] as String, r[2], c.frames)
            assertEquals(r[0] as String, r[3] as Float, c.fps, 0f)
            assertEquals(r[0] as String, r[4], c.bottom)
            assertEquals("ids follow the sheet rows", c.row, c.id)
        }
        val depth = listOf("walk_front", "walk_back", "sniff", "bonk").map { Clips.find(it)!! }
        assertEquals(listOf(6, 6, 4, 2), depth.map { it.frames })
        assertEquals(listOf(10f, 10f, 10f, 6f), depth.map { it.fps })
        assertTrue(depth.all { it.depthSheet })
    }

    @Test
    fun everyLocomotionClipHasFootContactsInsideItsFrames() {
        for (c in Clips.all.filter { it.isLocomotion }) {
            assertEquals("${c.name} has two footfalls per cycle", 2, c.contacts.size)
            assertTrue(c.contacts.all { it in 0 until c.frames })
            assertTrue("${c.name} has a stride length", c.stridePx > 0f)
        }
    }

    @Test
    fun weakVariantsFollowTheWebMap() {
        assertEquals(Clips.WALK_W, Clips.weakOf(Clips.RUN))
        assertEquals(Clips.IDLE_SIT_W, Clips.weakOf(Clips.IDLE_SIT))
        assertEquals(Clips.LOAF_W, Clips.weakOf(Clips.LOAF))
        assertEquals(Clips.GROOM, Clips.weakOf(Clips.GROOM))
    }

    @Test
    fun aClipStartsAtItsFirstFrameNotAtTheGlobalClock() {
        // Whatever the cat did before, groom begins on frame 0.
        for (delay in listOf(0f, 0.37f, 1.9f)) {
            val body = CatBody(seed = 1)
            body.replacePlan(
                1,
                listOf(PlanStep.Act("idle_sit", 0.2f + delay), PlanStep.Act("groom", 1f)),
            )
            var firstGroomFrame = -1
            var firstGroomPhase = 1f
            repeat(120 * 6) {
                body.step(dt)
                if (firstGroomFrame < 0 && body.pose.clipName == "groom") {
                    firstGroomFrame = body.pose.frame
                    firstGroomPhase = body.pose.framePhase
                }
            }
            assertEquals("delay $delay", 0, firstGroomFrame)
            assertTrue(firstGroomPhase < 0.2f)
        }
    }

    @Test
    fun aOneShotClipHoldsItsLastFrame() {
        val body = CatBody(seed = 1)
        body.replacePlan(1, listOf(PlanStep.Act("stretch", 4f)))
        repeat(120 * 3) { body.step(dt) }
        assertEquals("stretch", body.pose.clipName)
        assertEquals(Clips.STRETCH.frames - 1, body.pose.frame)
    }

    /** Worst stride-phase jump when the legs change gait while the cat keeps moving. */
    private fun worstSwitchJump(first: PlanStep, then: PlanStep): Float {
        val body = CatBody(seed = 1)
        body.replacePlan(1, listOf(first))
        repeat(120 * 2) { body.step(dt) }
        body.replacePlan(2, listOf(then))
        var prevClip = body.pose.clip
        var prevPhase = (body.pose.frame + body.pose.framePhase) / Clips.all[prevClip].frames
        var worst = -1f
        repeat(120 * 4) {
            body.step(dt)
            val p = body.pose
            val phase = (p.frame + p.framePhase) / Clips.all[p.clip].frames
            val bothStride = Clips.all[p.clip].isLocomotion && Clips.all[prevClip].isLocomotion
            if (bothStride && p.clip != prevClip) {
                val d = abs(phase - prevPhase)
                worst = maxOf(worst, minOf(d, 1f - d))
            }
            prevClip = p.clip
            prevPhase = phase
        }
        return worst
    }

    @Test
    fun walkToRunKeepsTheStridePhase() {
        val worst = worstSwitchJump(PlanStep.Walk(330f), PlanStep.Walk(330f, run = true))
        assertTrue("never switched gait", worst >= 0f)
        // One tick of stride at the switch is under 3 percent of a cycle; allow a few ticks.
        assertTrue("stride phase jumped by $worst", worst < 0.08f)
    }

    @Test
    fun runToWalkAlsoKeepsThePhase() {
        val worst = worstSwitchJump(PlanStep.Walk(330f, run = true), PlanStep.Walk(331f))
        assertTrue("never switched gait", worst >= 0f)
        assertTrue("stride phase jumped by $worst", worst < 0.08f)
    }

    @Test
    fun droppingIntoALoafSquashesTheBodyAndSettles() {
        val body = CatBody(seed = 1)
        body.replacePlan(1, listOf(PlanStep.Act("loaf", 2f)))
        var minY = 2f
        var maxY = 0f
        repeat(120 * 2) {
            body.step(dt)
            minY = minOf(minY, body.pose.scaleY)
            maxY = maxOf(maxY, body.pose.scaleY)
        }
        assertTrue("no squash on lowering: $minY", minY < 0.95f)
        assertTrue("no overshoot back up: $maxY", maxY > 1.0f)
        assertEquals(1f, body.pose.scaleY, 0.01f)
    }
}
