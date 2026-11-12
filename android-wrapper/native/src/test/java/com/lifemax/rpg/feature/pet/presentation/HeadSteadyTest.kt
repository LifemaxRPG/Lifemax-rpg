package com.lifemax.rpg.feature.pet.presentation

import com.lifemax.rpg.feature.pet.body.CatBody
import com.lifemax.rpg.feature.pet.body.Clips
import com.lifemax.rpg.feature.pet.body.PlanStep
import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.body.Tuning
import com.lifemax.rpg.feature.pet.body.World
import com.lifemax.rpg.feature.pet.presentation.SteadinessProbe.Cause
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A cat that sits still reads as steady (docs/specs/PET_SPRITE_STEADINESS.md, "A sitting cat's
 * head"): a black kitten on the cat tree's top slab, a second cat walking the floor below, 30 s at
 * 120 Hz on a fixed seed. Before the fix the open-eyed `idle_sit` row changed its head frame every
 * 0.29 s (98 flips, 94 of them back to a pose shown within 2.5 s, about 1 950 coat pixels
 * recoloured); now a head pose is held [Tuning.Blink.OPEN_HOLD_S] seconds.
 */
class HeadSteadyTest {
    private val s = SteadinessScenes.ROOM_SCALE

    @Test
    fun aSittingHeadDoesNotFlipBackAndForth() {
        for (look in listOf(HeadFlips.blackKitten2, HeadFlips.blackKitten3)) for (seed in
            listOf(23, 5)) {
            val r = HeadFlips.treeScene(look, s, seed)
            val said = r.summary("${look.label} seed $seed", s)
            // 30 s at one pose per 6 s is 5 flips; 8 leaves room for a restart of the row
            assertTrue("flips per 30 s: $said", r.flips.size <= MAX_FLIPS)
            assertEquals("a-b-a flips: $said", 0, r.aba.size)
            assertTrue("dwell: $said", r.dwell.all { it >= Tuning.Blink.OPEN_HOLD_S - 0.1f })
        }
    }

    @Test
    fun aSittingCoatChangesFewPixelsPerSecond() {
        for (look in listOf(HeadFlips.blackKitten2, HeadFlips.blackKitten3)) {
            val r = HeadFlips.treeScene(look, s)
            // the head flips only: a blink changes the eyes by design
            val artPx = r.coatOnFlips().sum() / (s * s)
            // before: about 65 art pixels a second; now a pose every 6 s, about 4 a second
            assertTrue(
                "${look.label}: $artPx coat pixels in ${HeadFlips.SECONDS} s",
                artPx <= MAX_COAT_ART_PX_PER_S * HeadFlips.SECONDS,
            )
        }
    }

    @Test
    fun theSittingCatStillBlinksBreathesAndFlicksItsEars() {
        var blinks = 0
        var was = false
        val r =
            HeadFlips.treeScene(HeadFlips.blackKitten2, s) { sit, _ ->
                val blinking = sit.pose.frame in Clips.IDLE_SIT.blinkFrames
                if (blinking && !was) blinks++
                was = blinking
            }
        // a blink every 4 to 11 s: 2 to 7 in 30 s
        assertTrue("blinks in 30 s: $blinks", blinks in 2..8)
        assertTrue("breath steps", r.run.causes.count { it == Cause.BREATH } > 10)
        assertTrue("rig steps (ear flicks)", r.rigSteps > 0)
    }

    @Test
    fun theSittingCatStillLooksAtAMovingCat() {
        val sitter = CatBody(seed = 3, startX = 154f)
        sitter.placeAt(154f, World.ART_Z, SurfaceId.TOP, -1)
        val dt = 1f / SteadinessProbe.HZ
        repeat(2 * SteadinessProbe.HZ) { sitter.step(dt) }
        // a cat passes in front of it (the sitter faces left, the other is on its left)
        var maxLook = 0f
        repeat(2 * SteadinessProbe.HZ) {
            sitter.lookAt(40f, World.ART_Z)
            sitter.step(dt)
            maxLook = maxOf(maxLook, sitter.pose.lookX)
        }
        assertTrue("it looks toward the other cat: $maxLook", maxLook > LOOK_MIN)
        // and, asked to face the other way, it turns round: the turn row and a new facing
        var turned = false
        sitter.replacePlan(1, listOf(PlanStep.Face(1)))
        repeat(2 * SteadinessProbe.HZ) {
            sitter.step(dt)
            if (sitter.pose.clipName == "turn") turned = true
        }
        assertTrue("it showed the turn", turned)
        assertEquals("it faces the other way", 1, sitter.pose.facing)
    }

    private companion object {
        const val MAX_FLIPS = 8
        const val MAX_COAT_ART_PX_PER_S = 8
        const val LOOK_MIN = 0.3f
    }
}
