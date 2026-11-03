package com.lifemax.rpg.feature.pet.art

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pose grid ([RigGrid]): snapping, clamping, and keys that tell grid points apart. */
class RigGridTest {
    private val grid = RigGrid()
    private val pose = RigPose()

    @Test
    fun theMiddleOfTheGridIsTheArtAsDrawn() {
        grid.clear()
        assertTrue(grid.neutral)
        grid.toPose(pose)
        assertTrue(pose.neutral)
    }

    @Test
    fun smallWobblesSnapToNeutral() {
        grid.set(1.9, -1.9, 0.1, 1.4, 1.9, 1.04)
        assertTrue(grid.neutral)
    }

    @Test
    fun theWebsAirplaneEarsAndTailTwitchLieOnTheGrid() {
        grid.set(-36.0, 36.0, 1.0, 0.0, 0.0, 1.0)
        grid.toPose(pose)
        assertEquals(-36.0, pose.earLeft, 0.0)
        assertEquals(36.0, pose.earRight, 0.0)
        assertEquals(1.0, pose.flat, 0.0)
    }

    @Test
    fun valuesPastTheRangeStopAtItsEdge() {
        grid.set(400.0, -400.0, 5.0, 90.0, -90.0, 9.0)
        grid.toPose(pose)
        assertEquals(RigGrid.EAR_RANGE * RigGrid.EAR_STEP_DEG, pose.earLeft, 0.0)
        assertEquals(-RigGrid.EAR_RANGE * RigGrid.EAR_STEP_DEG, pose.earRight, 0.0)
        assertEquals(1.0, pose.flat, 0.0)
        assertEquals(RigGrid.TAIL_RANGE * RigGrid.TAIL_STEP_DEG, pose.tail, 0.0)
        assertEquals(-RigGrid.CURL_RANGE * RigGrid.CURL_STEP_DEG, pose.tailCurl, 0.0)
        assertEquals(1.4, pose.whisker, 1e-9)
        grid.set(Double.NaN, 0.0, Double.NaN, 0.0, 0.0, Double.NaN)
        assertTrue(grid.neutral)
    }

    @Test
    fun everyGridPointOnOneAxisHasItsOwnKey() {
        val keys = HashSet<Long>()
        for (s in -RigGrid.EAR_RANGE..RigGrid.EAR_RANGE) {
            grid.set(s * RigGrid.EAR_STEP_DEG, 0.0, 0.0, 0.0, 0.0, 1.0)
            assertTrue(keys.add(grid.key()))
        }
        for (s in -RigGrid.TAIL_RANGE..RigGrid.TAIL_RANGE) {
            if (s == 0) continue
            grid.set(0.0, 0.0, 0.0, s * RigGrid.TAIL_STEP_DEG, 0.0, 1.0)
            assertTrue(keys.add(grid.key()))
        }
        for (s in -RigGrid.CURL_RANGE..RigGrid.CURL_RANGE) {
            if (s == 0) continue
            grid.set(0.0, 0.0, 0.0, 0.0, s * RigGrid.CURL_STEP_DEG, 1.0)
            assertTrue(keys.add(grid.key()))
        }
        for (s in 1..RigGrid.FLAT_STEPS) {
            grid.set(0.0, 0.0, s.toDouble() / RigGrid.FLAT_STEPS, 0.0, 0.0, 1.0)
            assertTrue(keys.add(grid.key()))
        }
        assertTrue(grid.key() in 0 until RigGrid.POINTS)
    }

    @Test
    fun theLeftEarAndTheRightEarAreDifferentPoses() {
        grid.set(8.0, 0.0, 0.0, 0.0, 0.0, 1.0)
        val left = grid.key()
        grid.set(0.0, 8.0, 0.0, 0.0, 0.0, 1.0)
        assertTrue(left != grid.key())
    }
}
