package com.lifemax.rpg.feature.pet.presentation

import com.lifemax.rpg.feature.pet.body.CatBody
import com.lifemax.rpg.feature.pet.body.ClipPlayer
import com.lifemax.rpg.feature.pet.body.Clips
import com.lifemax.rpg.feature.pet.body.PlanStep
import com.lifemax.rpg.feature.pet.body.Rng
import com.lifemax.rpg.feature.pet.presentation.SteadinessProbe.Cause
import com.lifemax.rpg.feature.pet.presentation.SteadinessProbe.Renderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A resting cat breathes with its back only (the owner: "weird stuff happening around the cat's ear
 * slash eye area as it's breathing and sleeping, as well as the tail"). Over whole breath cycles of
 * every resting row, at the card's and the Cat tab's scale, for a plain and a genetic coat: a
 * breath step changes pixels only on the back (behind the head, above the breath's cut), never on
 * the head, the face, the ears, the paws or the tail; nothing changes with no cause; and a sleeping
 * cat's art frame never changes (the art's own hand-drawn breath moved its ears, eyes and tail tip
 * by a pixel every half second), while a loafing cat changes frame only to blink.
 */
class BreathStillnessTest {
    @Test
    fun aBreathMovesOnlyTheBack() {
        for (s in listOf(SteadinessScenes.CARD_SCALE, SteadinessScenes.ROOM_SCALE)) for (look in
            listOf(SteadinessScenes.ginger, SteadinessScenes.calico)) for (row in
            SteadinessScenes.restRows) {
            val run = SteadinessScenes.act(look, row, Renderer.STEADY, s, CYCLES_S)
            var breaths = 0
            for (i in run.changed.indices) {
                val what = "$row ${look.label} at $s, step $i"
                if (run.causes[i] == Cause.NONE)
                    assertEquals("$what changed with no cause", 0, run.changed[i])
                if (run.causes[i] != Cause.BREATH || run.changed[i] == 0) continue
                breaths++
                assertEquals("$what: a breath step moved the head or the paws", 0, run.offBack[i])
            }
            assertTrue("$row ${look.label} at $s breathes ($breaths steps)", breaths >= MIN_BREATHS)
        }
    }

    @Test
    fun aSleepingCatHoldsItsFrameAndALoafingOneOnlyBlinks() {
        for (row in listOf("sleep", "sleep_w", "loaf", "loaf_w")) {
            val clip = Clips.find(row)!!
            val body = CatBody(seed = 3)
            body.replacePlan(1, listOf(PlanStep.Act(row, LONG_S + SteadinessScenes.SETTLE_S + 1f)))
            val dt = 1f / SteadinessProbe.HZ
            repeat((SteadinessScenes.SETTLE_S * SteadinessProbe.HZ).toInt()) { body.step(dt) }
            val frames = HashSet<Int>()
            var changes = 0
            var last = -1
            repeat((LONG_S * SteadinessProbe.HZ).toInt()) {
                body.step(dt)
                if (body.pose.clip != clip.id) return@repeat
                val f = body.pose.frame
                frames.add(f)
                if (last >= 0 && f != last) changes++
                last = f
            }
            assertTrue("$row was played", last >= 0)
            if (clip.blinks) {
                assertTrue(
                    "$row shows its rest frame and blink frames: $frames",
                    frames.all { it in clip.openFrames || it in clip.blinkFrames },
                )
                assertTrue("$row blinks ($changes frame changes)", changes >= 2)
                assertTrue("$row changes frame only to blink ($changes)", changes <= MAX_CHANGES)
            } else {
                assertEquals("$row holds one frame", setOf(0), frames)
            }
        }
    }

    @Test
    fun aSleepingClipHoldsItsFirstFrame() {
        val player = ClipPlayer(Rng(1))
        player.play(Clips.SLEEP)
        repeat(SteadinessProbe.HZ * 20) {
            player.advanceAtBaseRate(1f / SteadinessProbe.HZ)
            assertEquals(0, player.frame)
        }
    }

    private companion object {
        /** Two slow sleeping breaths (0.25 a second) or more of the awake ones. */
        const val CYCLES_S = 8.5f
        const val LONG_S = 40f
        const val MIN_BREATHS = 4

        /** 40 s of blinks every 4 to 11 s, each 2 or 3 frames: in and out of each. */
        const val MAX_CHANGES = 40
    }
}
