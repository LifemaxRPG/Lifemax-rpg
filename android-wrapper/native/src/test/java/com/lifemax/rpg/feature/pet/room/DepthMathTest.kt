package com.lifemax.rpg.feature.pet.room

import com.lifemax.rpg.feature.pet.body.World
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The parallax maths and the camera sway of the 2.5D room: pure numbers, no pictures. */
class DepthMathTest {
    // ------------------------------------------------------------------ parallax

    @Test
    fun theWallMovesByItsGainAndEveryRowInFrontOfItMoreTheNearerItIs() {
        for (level in DepthLevel.entries) {
            assertEquals(level.wallGain, Parallax.gainAtRow(Parallax.WALL_ROW, level), 0f)
            // behind the wall (a picture on it, the bookcase's sort row) is the wall
            assertEquals(level.wallGain, Parallax.gainAtRow(100f, level), 0f)
            var last = 0f
            var row = Parallax.WALL_ROW
            while (row <= 160f) {
                val g = Parallax.gainAtRow(row, level)
                assertTrue("$level row $row", g >= last)
                last = g
                row += 1f
            }
            // eleven rows (the sofa's depth) are eleven times the depth gain
            val sofa = Parallax.gainAtRow(128f, level) - Parallax.gainAtRow(117f, level)
            assertEquals(11 * level.depthGain, sofa, 1e-5f)
        }
    }

    @Test
    fun aCatMovesWithTheFloorRowItStandsOn() {
        for (level in DepthLevel.entries) for (z in floatArrayOf(0f, 20f, 35f, 60f, 100f)) {
            for (sway in floatArrayOf(-1f, -0.4f, 0f, 0.3f, 1f)) {
                assertEquals(
                    Parallax.shiftAtRow(sway, World.floorY(z), level),
                    Parallax.shiftAtZ(sway, z, level),
                )
            }
        }
        // at the back wall a cat moves with the wall
        assertEquals(
            Parallax.wallShift(1f, DepthLevel.RICH),
            Parallax.shiftAtZ(1f, 0f, DepthLevel.RICH),
        )
    }

    @Test
    fun theSkyMarginCoversTheWallsReach() {
        for (level in DepthLevel.entries) {
            assertTrue(Parallax.wallShift(1f, level) <= Parallax.maxWallShift)
            assertTrue(-Parallax.wallShift(-1f, level) <= Parallax.maxWallShift)
        }
    }

    @Test
    fun shiftsRoundHalfUpToWholePixels() {
        assertEquals(2, Parallax.shift(0.5f, 3f)) // 1.5 -> 2
        assertEquals(-1, Parallax.shift(-0.5f, 3f)) // -1.5 -> -1
        assertEquals(1, Parallax.shift(0.4f, 3f)) // 1.2 -> 1
        assertEquals(0, Parallax.shift(0.1f, 3f))
    }

    @Test
    fun theDepthOfARowIsThePagesPetZFromY() {
        // the page: s = (y - 98) / 34 * 100; z = (s - 55) * 100 / 130, clamped to 0..100
        assertEquals(0f, DepthMath.zFromRow(98f), 0f)
        assertEquals(World.ART_Z, DepthMath.zFromRow(132f), 0.5f) // the feet line of the art
        assertEquals(25.57f, DepthMath.zFromRow(128f), 0.01f)
        assertEquals(World.MAX_Z, DepthMath.zFromRow(400f), 0f)
        // and it inverts World.floorY at whole depths
        for (z in intArrayOf(10, 35, 60, 90)) {
            val y = World.floorY(z.toFloat())
            assertEquals(z.toFloat(), DepthMath.zFromRow(y), 1.0f)
        }
    }

    // ------------------------------------------------------------------ sway

    private fun settle(sway: DepthSway, seconds: Float, hz: Int): Float {
        repeat((seconds * hz).toInt()) { sway.step(1f / hz) }
        return sway.position
    }

    @Test
    fun theSwayStaysInsideMinusOneToOneAndMovesSlowly() {
        val sway = DepthSway()
        var lo = 0f
        var hi = 0f
        var fastest = 0f
        repeat(60 * 120) {
            sway.step(1f / 60f)
            lo = minOf(lo, sway.position)
            hi = maxOf(hi, sway.position)
            fastest = maxOf(fastest, abs(sway.velocity))
        }
        assertTrue("reaches $lo..$hi", lo >= -1f && hi <= 1f)
        assertTrue("the idle drift swings", hi - lo > 0.3f)
        assertTrue(
            "and never more than ${SwayTuning.IDLE_AMPLITUDE}",
            hi <= SwayTuning.IDLE_AMPLITUDE + 0.05f,
        )
        assertTrue("at $fastest units a second", fastest < 0.5f)
    }

    @Test
    fun aTiltPullsTheCameraThereAndItSettlesWithoutRingingForever() {
        val sway = DepthSway()
        sway.tilt = 1f
        var peak = 0f
        repeat(60 * 6) {
            sway.step(1f / 60f)
            peak = maxOf(peak, sway.position)
        }
        val goal = sway.target(6f)
        assertEquals(goal, sway.position, 0.2f)
        assertTrue("barely overshoots: $peak", peak <= 1f)
        // let go: it comes back to the idle drift
        sway.tilt = 0f
        repeat(60 * 8) { sway.step(1f / 60f) }
        assertTrue(abs(sway.position) <= SwayTuning.IDLE_AMPLITUDE + 0.1f)
    }

    @Test
    fun theSpringIsCriticallyCloseToDamped() {
        // a step response with a damping ratio near 1 does not overshoot by more than a few percent
        val sway = DepthSway()
        sway.tilt = 0.5f
        var last = 0f
        var overshoot = 0f
        repeat(60 * 10) {
            sway.step(1f / 60f)
            last = sway.position
            overshoot =
                maxOf(overshoot, last - (SwayTuning.TILT_RANGE * 0.5f + SwayTuning.IDLE_AMPLITUDE))
        }
        assertTrue("overshoot $overshoot", overshoot <= 0.02f)
        assertTrue(last.isFinite())
    }

    @Test
    fun theResultDoesNotDependOnTheFrameRate() {
        val a = DepthSway()
        val b = DepthSway()
        val c = DepthSway()
        a.tilt = 0.6f
        b.tilt = 0.6f
        c.tilt = 0.6f
        val seconds = 9f
        val pa = settle(a, seconds, 30)
        val pb = settle(b, seconds, 60)
        val pc = settle(c, seconds, 144)
        assertEquals(pa, pb, 4e-3f)
        assertEquals(pb, pc, 4e-3f)
    }

    @Test
    fun aStalledFrameCountsAsAShortOne() {
        val a = DepthSway()
        val b = DepthSway()
        a.step(30f) // the app was in the background for half a minute
        b.step(SwayTuning.MAX_FRAME_S)
        assertEquals(b.position, a.position, 1e-6f)
    }

    @Test
    fun reducedMotionNeverMoves() {
        val sway = DepthSway(reducedMotion = true)
        sway.tilt = 1f
        repeat(600) { sway.step(1f / 60f) }
        assertEquals(0f, sway.position, 0f)
        assertEquals(0f, sway.velocity, 0f)
        // turning it on mid-sway stops the camera at once
        val moving = DepthSway()
        repeat(900) { moving.step(1f / 60f) }
        moving.reducedMotion = true
        assertEquals(0f, moving.position, 0f)
        moving.restAt(0.8f)
        assertEquals(0f, moving.position, 0f)
    }

    @Test
    fun theTiltIsClamped() {
        val sway = DepthSway()
        sway.tilt = 7f
        assertEquals(1f, sway.tilt, 0f)
        sway.tilt = -7f
        assertEquals(-1f, sway.tilt, 0f)
    }
}
