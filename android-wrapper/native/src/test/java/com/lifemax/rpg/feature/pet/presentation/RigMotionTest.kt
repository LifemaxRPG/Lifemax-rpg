package com.lifemax.rpg.feature.pet.presentation

import com.lifemax.rpg.feature.pet.art.RigGrid
import com.lifemax.rpg.feature.pet.body.CatBody
import com.lifemax.rpg.feature.pet.body.EarState
import com.lifemax.rpg.feature.pet.body.TailState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How the body's springs reach the rig: a cat settled in its neutral states draws the art as drawn;
 * a brain state is a target the ears and tail travel to over several frames, with overshoot;
 * reduced motion and kittens' ears hold the art's pose.
 */
class RigMotionTest {
    private val dt = 1f / 120f
    private val grid = RigGrid()

    private fun settle(body: CatBody, seconds: Float = 4f) =
        repeat((seconds * 120).toInt()) { body.step(dt) }

    @Test
    fun aCatAtRestIsTheArtAsDrawn() {
        val body = CatBody(seed = 1)
        body.setAffect(EarState.FORWARD, TailState.NEUTRAL)
        settle(body)
        RigMotion.read(body.pose, kitten = false, motion = true, grid = grid)
        assertTrue(
            "ears ${grid.earLeft}/${grid.earRight} tail ${grid.tail}/${grid.curl}",
            grid.neutral,
        )
    }

    @Test
    fun airplaneEarsArriveByTheSpringsWithAnOvershoot() {
        val body = CatBody(seed = 1)
        settle(body)
        body.setAffect(EarState.AIRPLANE_FLAT, TailState.NEUTRAL)
        body.step(dt)
        RigMotion.read(body.pose, kitten = false, motion = true, grid = grid)
        assertTrue("the first frame must not snap: ${grid.earLeft}", grid.earLeft > -3)
        var deepest = 0
        var flatPeak = 0
        repeat(120 * 3) {
            body.step(dt)
            RigMotion.read(body.pose, kitten = false, motion = true, grid = grid)
            deepest = minOf(deepest, grid.earLeft)
            flatPeak = maxOf(flatPeak, grid.flat)
        }
        assertTrue("the left ear reaches the web's 36 degrees: $deepest", deepest <= -9)
        assertEquals("flat settles at all the way", RigGrid.FLAT_STEPS, grid.flat)
        assertEquals(-9, grid.earLeft)
        assertEquals(9, grid.earRight)
    }

    @Test
    fun theTailFollowsItsStateAndTheTipLagsTheRoot() {
        val body = CatBody(seed = 1)
        body.setAffect(EarState.FORWARD, TailState.HIGH_FLAG)
        var firstRoot = Int.MIN_VALUE
        var curlLater = false
        repeat(120 * 4) { tick ->
            body.step(dt)
            RigMotion.read(body.pose, kitten = false, motion = true, grid = grid)
            if (tick == 6) firstRoot = grid.tail
            if (tick > 60 && grid.curl != 0) curlLater = true
        }
        assertTrue("the flag raises the tail: ${grid.tail}", grid.tail > 3)
        assertTrue("not at once: $firstRoot", firstRoot < grid.tail)
        assertTrue("the tail bent while it travelled", curlLater)
        body.setAffect(EarState.FORWARD, TailState.TUCKED_CURL)
        settle(body)
        RigMotion.read(body.pose, kitten = false, motion = true, grid = grid)
        assertTrue("tucked lowers it: ${grid.tail}", grid.tail < 0)
    }

    @Test
    fun reducedMotionAndKittenEarsHoldTheArtsPose() {
        val body = CatBody(seed = 1)
        body.setAffect(EarState.AIRPLANE_FLAT, TailState.HIGH_FLAG)
        settle(body)
        RigMotion.read(body.pose, kitten = false, motion = false, grid = grid)
        assertTrue(grid.neutral)
        RigMotion.read(body.pose, kitten = true, motion = true, grid = grid)
        assertEquals(0, grid.earLeft)
        assertEquals(0, grid.earRight)
        assertEquals(0, grid.flat)
        assertFalse("a kitten's tail still moves", grid.tail == 0)
    }

    @Test
    fun theWhiskersSwayWithTheirSpring() {
        val body = CatBody(seed = 1)
        settle(body)
        body.setAffect(EarState.AIRPLANE_FLAT, TailState.NEUTRAL)
        settle(body)
        RigMotion.read(body.pose, kitten = false, motion = true, grid = grid)
        assertTrue("flat ears sweep the whiskers back: ${grid.whisker}", grid.whisker < 0)
    }
}
