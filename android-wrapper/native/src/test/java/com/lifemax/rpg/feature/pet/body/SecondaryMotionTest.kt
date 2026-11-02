package com.lifemax.rpg.feature.pet.body

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ears, tail, whiskers, breathing, attention, reactions and idle life. */
class SecondaryMotionTest {
    private val dt = 1f / 120f

    private fun settle(body: CatBody, seconds: Float = 3f) =
        repeat((seconds * 120).toInt()) { body.step(dt) }

    @Test
    fun earsLagOvershootAndSettleOnAStateChange() {
        val body = CatBody(seed = 1)
        settle(body)
        body.setAffect(EarState.AIRPLANE_FLAT, TailState.NEUTRAL)
        var firstFrameFlat = -1f
        var peak = 0f
        for (tick in 0 until 120 * 3) {
            body.step(dt)
            if (tick == 0) firstFrameFlat = body.pose.earFlat
            peak = maxOf(peak, body.pose.earFlat)
        }
        assertTrue("ears must not snap: $firstFrameFlat", firstFrameFlat < 0.2f)
        assertTrue("ears should overshoot a little: $peak", peak > 1.0f && peak < 1.4f)
        assertEquals(1f, body.pose.earFlat, 0.05f)
        assertEquals(-Tuning.Ears.OUT_ANGLE, body.pose.ear[0], 0.05f)
        assertEquals(Tuning.Ears.OUT_ANGLE, body.pose.ear[1], 0.05f)
    }

    @Test
    fun radarEarsMoveIndependently() {
        val body = CatBody(seed = 1)
        body.setAffect(EarState.RADAR_SWIVEL, TailState.NEUTRAL)
        var differs = false
        repeat(120 * 4) {
            body.step(dt)
            if (abs(body.pose.ear[0] - body.pose.ear[1]) > 0.2f) differs = true
        }
        assertTrue(differs)
    }

    @Test
    fun theTailStatesAimTheChain() {
        val body = CatBody(seed = 1)
        body.setAffect(EarState.FORWARD, TailState.HIGH_FLAG)
        settle(body, 4f)
        val flag = body.pose.tail[0]
        body.setAffect(EarState.FORWARD, TailState.TUCKED_CURL)
        settle(body, 4f)
        val tucked = body.pose.tail[0]
        assertTrue("flag $flag should be above tucked $tucked", flag > tucked + 1f)
        body.setAffect(EarState.FORWARD, TailState.QUESTION_MARK)
        settle(body, 4f)
        assertTrue(
            "question mark hooks the tip over",
            body.pose.tail[Tuning.Tail.SEGMENTS - 1] < 0f,
        )
    }

    @Test
    fun theTailSwingsWhenTheCatTurns() {
        val body = CatBody(seed = 1)
        settle(body, 4f)
        val rest = body.pose.tail[0]
        body.replacePlan(1, listOf(PlanStep.Face(-1)))
        var farthest = 0f
        repeat(60) {
            body.step(dt)
            farthest = maxOf(farthest, abs(body.pose.tail[0] - rest))
        }
        assertTrue("tail ignored the turn: $farthest", farthest > 0.05f)
    }

    @Test
    fun tailTipTwitchesAreSeededPerCat() {
        fun tipTrace(seed: Int): List<Float> {
            val body = CatBody(seed = seed)
            body.setAffect(EarState.FORWARD, TailState.TIP_TWITCH)
            val out = ArrayList<Float>()
            repeat(120 * 20) {
                body.step(dt)
                if (it % 12 == 0) out.add(body.pose.tail[Tuning.Tail.SEGMENTS - 1])
            }
            return out
        }
        assertEquals(tipTrace(4), tipTrace(4))
        assertNotEquals(tipTrace(4), tipTrace(5))
        val spread = tipTrace(4).let { it.max() - it.min() }
        assertTrue("a twitching tail must twitch: $spread", spread > 0.1f)
    }

    @Test
    fun breathingIsSlowerAndDeeperAsleep() {
        fun breathe(anim: String): Pair<Float, Int> {
            val body = CatBody(seed = 1)
            body.replacePlan(1, listOf(PlanStep.Act(anim, 60f)))
            settle(body, 12f)
            var low = 2f
            var high = 0f
            var crossings = 0
            var wasUp = false
            repeat(120 * 20) {
                body.step(dt)
                low = minOf(low, body.pose.breath)
                high = maxOf(high, body.pose.breath)
                val up = body.pose.breath > 1f
                if (up && !wasUp) crossings++
                wasUp = up
            }
            return (high - low) to crossings
        }
        val (awakeDepth, awakeBreaths) = breathe("loaf")
        val (sleepDepth, sleepBreaths) = breathe("sleep")
        assertTrue(
            "asleep should be deeper: $sleepDepth vs $awakeDepth",
            sleepDepth > awakeDepth * 1.8f,
        )
        assertTrue(
            "asleep should be slower: $sleepBreaths vs $awakeBreaths",
            sleepBreaths < awakeBreaths,
        )
    }

    @Test
    fun attentionTurnsHeadAndLeanAfterAShortDelay() {
        val body = CatBody(seed = 1, startX = 100f)
        settle(body)
        body.lookAt(200f, 35f)
        body.step(dt)
        assertTrue("no instant reaction", abs(body.pose.lookX) < 0.02f)
        settle(body, 1.5f)
        assertTrue(
            "looks ahead toward a target on the right: ${body.pose.lookX}",
            body.pose.lookX > 0.5f,
        )
        assertTrue("leans toward it", body.pose.lean > 0.02f)
        body.lookAt(0f, 35f)
        settle(body, 1.5f)
        assertTrue("looks back over the shoulder: ${body.pose.lookX}", body.pose.lookX < -0.3f)
        body.lookAt(100f, 90f)
        settle(body, 1.5f)
        assertTrue("target nearer the viewer is below: ${body.pose.lookY}", body.pose.lookY > 0.3f)
        body.stopLooking()
        settle(body, 3f)
        assertTrue(abs(body.pose.lookX) < 0.3f)
    }

    @Test
    fun startleHopsFlattensEarsAndPuffsTheTail() {
        val body = CatBody(seed = 1)
        settle(body)
        body.react(ReactionKind.STARTLE)
        var peakStretch = 0f
        var earsFlat = 0f
        var tailUp = -9f
        var bobbedUp = 0f
        repeat(120) {
            body.step(dt)
            peakStretch = maxOf(peakStretch, body.pose.scaleY)
            earsFlat = maxOf(earsFlat, body.pose.earFlat)
            tailUp = maxOf(tailUp, body.pose.tail[0])
            bobbedUp = minOf(bobbedUp, body.pose.bob)
        }
        assertTrue("stretch $peakStretch", peakStretch > 1.05f)
        assertTrue("ears $earsFlat", earsFlat > 0.6f)
        assertTrue("tail $tailUp", tailUp > 0.6f)
        assertTrue("hop $bobbedUp", bobbedUp < -0.5f)
    }

    @Test
    fun slowBlinkClosesAndReopensTheEyes() {
        val body = CatBody(seed = 1)
        body.react(ReactionKind.SLOW_BLINK)
        var shut = 0f
        repeat(120 * 2) {
            body.step(dt)
            shut = maxOf(shut, body.pose.eyeClose)
        }
        assertEquals(1f, shut, 0.01f)
        assertEquals(0f, body.pose.eyeClose, 0.001f)
    }

    @Test
    fun purrVibratesWithinItsEnvelopeAndStops() {
        val body = CatBody(seed = 1)
        body.react(ReactionKind.PURR, 2f)
        var peak = 0f
        repeat(120 * 2) {
            body.step(dt)
            peak = maxOf(peak, abs(body.pose.vibration))
        }
        assertTrue(
            "purr amplitude $peak",
            peak > 0.1f && peak <= Tuning.Reaction.PURR_AMPLITUDE + 0.001f,
        )
        repeat(60) { body.step(dt) }
        assertEquals(0f, body.pose.vibration, 0f)
    }

    @Test
    fun begBobsAndPutsTheTailInAQuestion() {
        val body = CatBody(seed = 1)
        settle(body)
        body.react(ReactionKind.BEG)
        var bobSwing = 0f
        repeat(120 * 2) {
            body.step(dt)
            bobSwing = maxOf(bobSwing, abs(body.pose.bob))
        }
        assertTrue("beg bob $bobSwing", bobSwing > 0.1f)
        assertTrue(
            "tail hooked: ${body.pose.tail[Tuning.Tail.SEGMENTS - 1]}",
            body.pose.tail[0] > 0.5f,
        )
    }

    @Test
    fun reactionsLayerOverAWalkWithoutChangingTheClip() {
        val body = CatBody(seed = 1)
        body.replacePlan(1, listOf(PlanStep.Walk(300f)))
        settle(body, 1f)
        body.react(ReactionKind.PURR, 1f)
        repeat(30) { body.step(dt) }
        assertEquals("walk", body.pose.clipName)
        assertTrue(body.pose.vibration != 0f)
    }

    @Test
    fun idleLifeIsSeededSoTwoCatsDiffer() {
        fun shiftTimes(seed: Int): List<Int> {
            val body = CatBody(seed = seed)
            val ticks = ArrayList<Int>()
            for (t in 0 until 120 * 60) {
                body.step(dt)
                if (abs(body.pose.rotation) > 0.008f && (ticks.isEmpty() || t - ticks.last() > 120))
                    ticks.add(t)
            }
            return ticks
        }
        val a = shiftTimes(21)
        val b = shiftTimes(22)
        assertTrue("cat A shifted its weight ${a.size} times in a minute", a.isNotEmpty())
        assertNotEquals(a, b)
        assertEquals(a, shiftTimes(21))
    }

    @Test
    fun whiskersSweepBackWhenTheEarsGoFlat() {
        val body = CatBody(seed = 1)
        settle(body)
        val relaxed = body.pose.whisker
        body.setAffect(EarState.AIRPLANE_FLAT, TailState.NEUTRAL)
        settle(body)
        assertTrue("whiskers $relaxed -> ${body.pose.whisker}", body.pose.whisker < relaxed - 0.4f)
    }
}
