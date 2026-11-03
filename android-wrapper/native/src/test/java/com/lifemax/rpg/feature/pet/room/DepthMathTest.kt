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
    fun nearerPlanesMoveMoreAndTheSkyNotAtAll() {
        val gains =
            listOf(Plane.SKY, Plane.WALL, Plane.BACK, Plane.MID, Plane.FRONT).map(Parallax::gain)
        assertEquals(0f, gains[0], 0f)
        for (i in 1 until gains.size) assertTrue("plane $i", gains[i] > gains[i - 1])
        // four to six planes, one to six art pixels
        assertTrue(gains.last() <= 6f)
        assertEquals(1f, gains[1], 0f)
    }

    @Test
    fun theWholeRangeIsOneToSixPixels() {
        for (plane in listOf(Plane.WALL, Plane.BACK, Plane.MID, Plane.FRONT)) {
            val reach = Parallax.shift(1f, plane)
            assertTrue("$plane reaches $reach", reach in 1..6)
            assertEquals(-reach, Parallax.shift(-1f, plane))
        }
        assertEquals(6, Parallax.shiftAtZ(1f, World.MAX_Z))
    }

    @Test
    fun aCatAtTheArtDepthMovesWithTheMiddlePlane() {
        for (sway in floatArrayOf(-1f, -0.6f, -0.2f, 0f, 0.3f, 0.7f, 1f)) {
            assertEquals(Parallax.shift(sway, Plane.MID), Parallax.shiftAtZ(sway, World.ART_Z))
        }
    }

    @Test
    fun theShiftGrowsWithDepthAndIsContinuousAtTheArtDepth() {
        var last = 0f
        var z = 0f
        while (z <= World.MAX_Z) {
            val g = Parallax.gainAtZ(z)
            assertTrue("gain at $z", g >= last)
            last = g
            z += 5f
        }
        assertEquals(ParallaxTuning.WALL, Parallax.gainAtZ(0f), 1e-6f)
        assertEquals(ParallaxTuning.MID, Parallax.gainAtZ(World.ART_Z), 1e-6f)
        assertEquals(ParallaxTuning.NEAREST, Parallax.gainAtZ(World.MAX_Z), 1e-6f)
        val before = Parallax.gainAtZ(World.ART_Z - 0.01f)
        val after = Parallax.gainAtZ(World.ART_Z + 0.01f)
        assertTrue(abs(after - before) < 0.01f)
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
