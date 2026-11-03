package com.lifemax.rpg.feature.pet.room

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The depth levels: each is stronger than the last, and none breaks the limits of its own. */
class DepthLevelTest {
    private val levels = DepthLevel.entries

    @Test
    fun subtleIsWhatWasFirstBuilt() {
        val s = DepthLevel.SUBTLE
        assertEquals(ParallaxTuning.WALL, s.wall, 0f)
        assertEquals(ParallaxTuning.BACK, s.back, 0f)
        assertEquals(ParallaxTuning.MID, s.mid, 0f)
        assertEquals(ParallaxTuning.FRONT, s.front, 0f)
        assertEquals(ParallaxTuning.NEAREST, s.nearest, 0f)
        assertEquals(SwayTuning.IDLE_AMPLITUDE, s.idleAmplitude, 0f)
        assertEquals(FaceTuning.MAX_PX, s.maxFacePx)
        assertEquals(FaceTuning.TOP_PX, s.faceTopPx)
        assertEquals(FaceTuning.SIDE_REST_PX, s.faceSideRestPx)
        assertEquals(FaceTuning.SIDE_SWAY_PX, s.faceSwayPx, 0f)
    }

    @Test
    fun everyNumberGrowsWithTheLevel() {
        for ((a, b) in levels.zipWithNext()) {
            assertTrue(b.wall > a.wall && b.back > a.back && b.mid > a.mid)
            assertTrue(b.front > a.front && b.nearest > a.nearest)
            assertTrue(b.idleAmplitude > a.idleAmplitude && b.maxFacePx > a.maxFacePx)
            assertTrue(b.shadowSize > a.shadowSize && b.shadowStrength > a.shadowStrength)
            assertTrue(b.lightStrength > a.lightStrength && b.rimStrength > a.rimStrength)
            assertTrue(b.seamStrength > a.seamStrength)
        }
    }

    @Test
    fun parallaxReachIsTheLevelsRange() {
        assertEquals(1 to 6, Parallax.shift(1f, Plane.WALL) to Parallax.shiftAtZ(1f, 100f))
        val m = DepthLevel.MEDIUM
        assertEquals(2 to 9, Parallax.shift(1f, Plane.WALL, m) to Parallax.shiftAtZ(1f, 100f, m))
        val r = DepthLevel.RICH
        assertEquals(3 to 12, Parallax.shift(1f, Plane.WALL, r) to Parallax.shiftAtZ(1f, 100f, r))
        for (l in levels) {
            assertEquals(l.mid, Parallax.gainAtZ(35f, l), 1e-5f)
            assertTrue(Parallax.maxShift >= l.maxShift)
        }
    }

    @Test
    fun theFaceStaysWithinTheLevelsLimitAtEverySway() {
        val v = IntArray(2)
        for (l in levels) {
            var sway = -1.5f
            while (sway <= 1.5f) {
                Thickness.vector(sway, v, l)
                assertTrue("$l $sway", abs(v[0]) <= l.maxFacePx && abs(v[1]) <= l.maxFacePx)
                sway += 0.05f
            }
            // the face slides the other way with the sway, as at the subtle level
            val right = IntArray(2)
            val left = IntArray(2)
            Thickness.vector(1f, right, l)
            Thickness.vector(-1f, left, l)
            assertTrue(right[0] < left[0])
            // and it is the silhouette moved, never warped: one-pixel steps along a straight line
            val steps = IntArray(2 * l.maxFacePx)
            assertTrue(Thickness.offsets(left[0], left[1], steps) <= l.maxFacePx)
        }
    }

    @Test
    fun theSwayIdleDriftGrowsWithTheLevel() {
        val peaks =
            levels.map { l ->
                val sway = DepthSway(idleAmplitude = l.idleAmplitude)
                var hi = 0f
                repeat(60 * 120) {
                    sway.step(1f / 60f)
                    hi = maxOf(hi, abs(sway.position))
                }
                hi
            }
        assertTrue(peaks[0] < peaks[1] && peaks[1] < peaks[2])
        assertTrue(peaks.all { it <= 1f })
    }

    @Test
    fun theDefaultStyleIsSubtleAndClassicIsStillTheDefault() {
        assertEquals(DepthLevel.SUBTLE, RoomStyle.DEPTH25.level)
        assertEquals(RoomStyle(), RoomStyle.CLASSIC)
        assertEquals(DepthLevel.RICH, RoomStyle.depth25(DepthLevel.RICH).level)
    }
}
