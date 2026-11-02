package com.lifemax.rpg.feature.pet.body

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The autopilot that lives a cat's day on the Overview card: stable, varied, and inside its strip.
 */
class AutopilotTest {
    private val minX = 44f
    private val maxX = 148f

    /** Runs a cat for [seconds] at [hz] and returns what it did, one entry per second. */
    private fun day(seed: Int, seconds: Int, hz: Int = 60, weak: Boolean = false): Day {
        val pilot = Autopilot(seed, minX, maxX, weak)
        val body = CatBody(seed, pilot.startX)
        body.weak = weak
        body.placeAt(pilot.startX, World.ART_Z, SurfaceId.FLOOR, pilot.startFacing)
        val clips = LinkedHashSet<String>()
        val moves = ArrayList<Autopilot.Move>()
        val trace = ArrayList<String>()
        var left = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        val dt = 1f / hz
        for (frame in 1..seconds * hz) {
            body.step(dt)
            val before = pilot.last
            pilot.update(body, dt)
            if (pilot.last != before) moves.add(pilot.last)
            clips.add(body.pose.clipName)
            left = minOf(left, body.pose.x)
            right = maxOf(right, body.pose.x)
            if (frame % hz == 0) trace.add(TraceSupport.fingerprint(body.pose))
        }
        return Day(trace, clips, moves, left, right)
    }

    private class Day(
        val trace: List<String>,
        val clips: Set<String>,
        val moves: List<Autopilot.Move>,
        val left: Float,
        val right: Float,
    )

    @Test
    fun theSameCatLivesTheSameDay() {
        val seed = Autopilot.seedFor("Miso", "ginger")
        assertEquals(day(seed, 300).trace, day(seed, 300).trace)
        assertEquals(day(seed, 300).moves, day(seed, 300).moves)
    }

    @Test
    fun theSameDayAtAnyFrameRate() {
        // plans are decided from accumulated time, so 60 and 120 Hz hosts make the same choices
        val seed = Autopilot.seedFor("Miso", "ginger")
        assertEquals(day(seed, 200, 60).moves.take(8), day(seed, 200, 120).moves.take(8))
    }

    @Test
    fun twoCatsLiveDifferently() {
        val a = day(Autopilot.seedFor("Miso", "ginger"), 300)
        val b = day(Autopilot.seedFor("Pip", "gray"), 300)
        assertNotEquals(a.trace, b.trace)
        assertNotEquals(a.moves, b.moves)
        assertNotEquals(Autopilot.seedFor("Miso", "ginger"), Autopilot.seedFor("Miso", "black"))
    }

    @Test
    fun theCatStaysInsideItsStrip() {
        for (seed in 1..12) {
            val d = day(seed, 400)
            assertTrue("seed $seed left $minX..$maxX: ${d.left}", d.left >= minX - 0.5f)
            assertTrue("seed $seed right: ${d.right}", d.right <= maxX + 0.5f)
        }
    }

    @Test
    fun aCatDoesEverythingACatDoes() {
        val d = day(Autopilot.seedFor("Miso", "ginger"), 900)
        for (clip in listOf("idle_sit", "walk", "groom", "stretch", "loaf", "sleep")) {
            assertTrue("never showed $clip: ${d.clips}", clip in d.clips)
        }
        assertTrue(Autopilot.Move.WANDER in d.moves)
        assertTrue(Autopilot.Move.NAP in d.moves)
        assertTrue("walks only a few steps, never runs", "run" !in d.clips)
    }

    @Test
    fun aCatDoesNotRepeatItselfAndStretchesAfterANap() {
        val d = day(Autopilot.seedFor("Miso", "ginger"), 900)
        for (i in 1 until d.moves.size) assertNotEquals(d.moves[i - 1], d.moves[i])
        for (i in 0 until d.moves.size - 1) {
            if (d.moves[i] == Autopilot.Move.NAP)
                assertEquals(Autopilot.Move.STRETCH, d.moves[i + 1])
        }
    }

    @Test
    fun aHungryCatWalksLessAndRestsMore() {
        var walks = 0
        var weakWalks = 0
        for (seed in 1..6) {
            walks += day(seed, 600).moves.count { it == Autopilot.Move.WANDER }
            weakWalks += day(seed, 600, weak = true).moves.count { it == Autopilot.Move.WANDER }
        }
        assertTrue("$weakWalks weak walks against $walks", weakWalks < walks)
    }

    @Test
    fun theStepsAreAlwaysPlansTheBodyAccepts() {
        val pilot = Autopilot(7, minX, maxX)
        val body = CatBody(7, pilot.startX)
        repeat(60 * 120) {
            body.step(1f / 60f)
            pilot.update(body, 1f / 60f)
        }
        assertTrue(pilot.plans > 5)
    }
}
