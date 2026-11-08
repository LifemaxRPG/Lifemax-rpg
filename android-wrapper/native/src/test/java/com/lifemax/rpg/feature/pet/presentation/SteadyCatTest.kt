package com.lifemax.rpg.feature.pet.presentation

import com.lifemax.rpg.feature.pet.art.RigCompose
import com.lifemax.rpg.feature.pet.art.RigFrames
import com.lifemax.rpg.feature.pet.body.CatPose
import com.lifemax.rpg.feature.pet.body.Clips
import com.lifemax.rpg.feature.pet.body.Tuning
import com.lifemax.rpg.feature.pet.presentation.SteadinessProbe.Cause
import com.lifemax.rpg.feature.pet.presentation.SteadinessProbe.Renderer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The steady sprite's rules (docs/specs/PET_SPRITE_STEADINESS.md), drawn on the software canvas at
 * the phone's card scale and 120 frames a second: a resting cat draws byte-identical frames between
 * the moments something is meant to move (the art's next frame, a rig step, a breath step), the
 * breath is a whole device pixel at a time and never flickers back, and a spring that hovers on a
 * step's edge does not flick the drawing between two steps.
 */
class SteadyCatTest {
    private val s = SteadinessScenes.CARD_SCALE

    @Test
    fun aRestingCatsFramesAreIdenticalBetweenMotionEvents() {
        for (look in listOf(SteadinessScenes.ginger, SteadinessScenes.calico)) for (row in
            listOf("idle_sit", "loaf", "sleep")) {
            val run = SteadinessScenes.act(look, row, Renderer.STEADY, s, REST_S)
            for (i in run.changed.indices) {
                if (run.causes[i] == Cause.NONE)
                    assertEquals(
                        "$row ${look.label} step $i changed with no cause",
                        0,
                        run.changed[i],
                    )
            }
            assertTrue("$row ${look.label} was drawn", run.changed.size > REST_S * 100)
            val idle = run.changed.count { it == 0 }
            assertTrue(
                "$row ${look.label}: only $idle of ${run.changed.size} frames held still",
                idle > run.changed.size * MIN_STILL_SHARE,
            )
        }
    }

    @Test
    fun theOldDrawingShimmeredSoTheProbeCanSeeIt() {
        val run = SteadinessScenes.act(SteadinessScenes.ginger, "idle_sit", Renderer.BEFORE, s, 4f)
        val off = run.changed.indices.count { run.causes[it] == Cause.NONE && run.changed[it] > 0 }
        assertTrue("the probe must see the old breath's re-sampling ($off)", off > 0)
    }

    @Test
    fun theBreathIsAWholePixelStepThatNeverFlicksBack() {
        val plan = SpritePlan()
        val steady = SpriteSteady()
        val frames = RigFrames()
        val rig = SteadinessProbe.rigOf(SteadinessScenes.ginger, 1)
        val pose = CatPose().apply { clip = Clips.IDLE_SIT.id }
        val rises = ArrayList<Int>()
        val dt = 1f / SteadinessProbe.HZ
        var phase = 0f
        repeat((REST_S * SteadinessProbe.HZ).toInt()) {
            phase += Tuning.Breath.AWAKE_HZ * dt
            // the breath as the body makes it, with a little noise on top to tempt a flicker
            pose.breath =
                1f +
                    Tuning.Breath.AWAKE_DEPTH * kotlin.math.sin(2f * Math.PI.toFloat() * phase) +
                    if (it % 2 == 0) NOISE else -NOISE
            plan.plan(pose, rig, Clips.IDLE_SIT.bottom, FRAME_W, FRAME_H, steady, frames, s, true)
            rises.add(plan.rise)
        }
        assertTrue("the breath shows: ${rises.toSet()}", rises.toSet().size >= 2)
        for (i in 1 until rises.size) {
            assertTrue("a breath step is one pixel", kotlin.math.abs(rises[i] - rises[i - 1]) <= 1)
        }
        for (i in 2 until rises.size) {
            val flick = rises[i] == rises[i - 2] && rises[i] != rises[i - 1]
            assertTrue("the breath flicked back at frame $i", !flick)
        }
    }

    @Test
    fun aRigSpringHoveringOnAnEdgeHoldsItsStep() {
        val steady = SpriteSteady()
        val pose = CatPose().apply { clip = Clips.IDLE_SIT.id }
        for (i in pose.tail.indices) pose.tail[i] = 0f
        val keys = HashSet<Long>()
        repeat(HOVER_FRAMES) {
            // half a grid step of ear, wobbling a hair either way of the edge
            val wobble = if (it % 2 == 0) 0.02 else -0.02
            pose.ear[0] = Math.toRadians(EAR_EDGE_STEPS * 4.0 + wobble * 4.0).toFloat()
            steady.read(pose, kitten = false, motion = true, s = s)
            keys.add(steady.grid.key())
        }
        assertEquals("the ear stayed on one step", 1, keys.size)
    }

    @Test
    fun aSteadyStepStartsAtTheNearestStepAndHolds() {
        val step = SteadyStep()
        assertEquals(1, step.follow(0.6))
        assertEquals("inside the hold", 1, step.follow(0.3))
        assertEquals("past the hold", 0, step.follow(0.2))
        assertEquals(0, step.follow(0.74))
        assertEquals(1, step.follow(0.76))
        assertEquals(-3, step.follow(-3.2))
        step.reset()
        assertEquals(0, step.follow(0.4))
    }

    @Test
    fun aCatThatHasNotBreathedIsDrawnInOnePieceAsBefore() {
        val plan = SpritePlan()
        val rig = SteadinessProbe.rigOf(SteadinessScenes.calico, 1)
        val pose = CatPose().apply { clip = Clips.IDLE_SIT.id }
        val feet = Clips.IDLE_SIT.bottom
        plan.plan(pose, rig, feet, FRAME_W, FRAME_H, SpriteSteady(), RigFrames(), s, true)
        val bands = IntArray(SpritePlan.MAX_BANDS * SpritePlan.BAND)
        assertEquals(1, plan.bands(s, bands))
        val h = FRAME_H + RigCompose.MARGIN_TOP + RigCompose.MARGIN_BOTTOM
        assertArrayEquals(
            intArrayOf(0, h, (1 - feet - RigCompose.MARGIN_TOP) * s, h * s),
            bands.copyOf(SpritePlan.BAND),
        )
        assertEquals(-(FRAME_W / 2 + RigCompose.MARGIN_LEFT), plan.left)
        assertEquals(0f, plan.turnDeg)
        assertEquals(1f, plan.scaleX)
        assertEquals(1f, plan.scaleY)
    }

    private companion object {
        const val REST_S = 6f
        const val FRAME_W = RigFrames.FRAME_WIDTH
        const val FRAME_H = RigFrames.FRAME_HEIGHT
        const val NOISE = 0.0004f
        const val HOVER_FRAMES = 240
        const val EAR_EDGE_STEPS = 1.5

        /** A resting cat is still for most frames: the art's own frames and breath steps aside. */
        const val MIN_STILL_SHARE = 0.8
    }
}
