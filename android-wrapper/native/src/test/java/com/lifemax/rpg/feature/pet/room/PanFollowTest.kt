package com.lifemax.rpg.feature.pet.room

import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The camera-follow parallax: the room camera's pan feeds the depth sway ([PanTuning] has the
 * mapping). A camera right of the house's middle pushes the near rows left; a camera moving leans
 * with the motion; the result is damped, limited, the same at any frame rate, and nothing at all
 * with reduced motion. A piece left of the middle of the screen shows its right side.
 */
class PanFollowTest {
    private fun sway(): DepthSway = DepthSway(false, DepthLevel.MEDIUM.idleAmplitude)

    private fun run(sway: DepthSway, seconds: Float, hz: Int = 60, x: (Float) -> Float) {
        val dt = 1f / hz
        var t = 0f
        repeat(Math.round(seconds * hz)) {
            sway.follow(x(t))
            sway.step(dt)
            t += dt
        }
    }

    @Test
    fun aCameraRightOfTheMiddlePushesTheNearRowsLeftAndLeftOfItRight() {
        val right = sway()
        run(right, 8f) { PanTuning.CENTRE_X + PanTuning.RANGE_PX }
        assertEquals(-PanTuning.POSITION_GAIN, right.pan, 0.01f)
        val left = sway()
        run(left, 8f) { PanTuning.CENTRE_X - PanTuning.RANGE_PX }
        assertEquals(PanTuning.POSITION_GAIN, left.pan, 0.01f)
        val middle = sway()
        run(middle, 8f) { PanTuning.CENTRE_X }
        assertEquals(0f, middle.pan, 0.01f)
    }

    @Test
    fun theNearRowsMoveMoreThanTheWallAgainstTheCamera() {
        // at MEDIUM the wall moves 1.5 px a unit of sway, a cat on row 150 about 10.4 px
        val right = sway()
        run(right, 8f) { PanTuning.CENTRE_X + PanTuning.RANGE_PX }
        val wall = Parallax.shift(right.pan, DepthLevel.MEDIUM.wallGain)
        val near = Parallax.shiftAtRow(right.pan, 150f, DepthLevel.MEDIUM)
        assertTrue("wall $wall near $near", near < wall && near <= -3)
        assertTrue("slight: the wall moves at most a pixel", abs(wall) <= 1)
    }

    @Test
    fun aMovingCameraLeansAgainstItsMotionAndSettlesWhenItStops() {
        // the screen's middle is 30 px ahead of the view: the view moves at 100 px/s
        val p = PanFollow()
        p.cutTo(PanTuning.CENTRE_X)
        p.advance(PanTuning.CENTRE_X + 30f, 1f / 240f)
        assertEquals(100f, p.speed, 1f)
        assertEquals(-PanTuning.VELOCITY_GAIN, p.target(), 0.01f)
        // and the other way
        val q = PanFollow()
        q.cutTo(PanTuning.CENTRE_X)
        q.advance(PanTuning.CENTRE_X - 30f, 1f / 240f)
        assertEquals(PanTuning.VELOCITY_GAIN, q.target(), 0.01f)
        // when the camera stops, the lean fades and the pan returns to the position term
        val s = sway()
        run(s, 3f) { PanTuning.CENTRE_X - 150f + 100f * minOf(it, 1.5f) }
        run(s, 6f) { PanTuning.CENTRE_X }
        assertEquals(0f, s.pan, 0.01f)
    }

    @Test
    fun thePanIsLimitedAndTheSwayStaysInRange() {
        val s = sway()
        run(s, 5f) { 1_000_000f * sin(it * 9f) }
        assertTrue(abs(s.pan) <= PanTuning.LIMIT + 1e-4f)
        assertTrue(abs(s.position) <= 1f)
    }

    @Test
    fun theSpringDoesNotOvershootTheTarget() {
        val s = sway()
        run(s, 0.1f) { PanTuning.CENTRE_X } // begin at the middle
        var lowest = 0f
        val dt = 1f / 60f
        repeat(600) {
            s.follow(PanTuning.CENTRE_X + PanTuning.RANGE_PX)
            s.step(dt)
            lowest = minOf(lowest, s.pan)
        }
        assertTrue("overshoot $lowest", lowest >= -PanTuning.LIMIT - 0.01f)
    }

    @Test
    fun theResultDoesNotDependOnTheFrameRate() {
        fun at(hz: Int): Pair<Float, Float> {
            val s = sway()
            run(s, 4f, hz) { PanTuning.CENTRE_X + 150f * sin(it * 0.8f) }
            return s.pan to s.viewX
        }
        val (pan60, view60) = at(60)
        for (hz in intArrayOf(30, 90, 144)) {
            val (pan, view) = at(hz)
            assertEquals("pan at $hz Hz", pan60, pan, 0.02f)
            assertEquals("view at $hz Hz", view60, view, 3f)
        }
    }

    @Test
    fun aCutIsAGlideNotAJump() {
        val s = sway()
        run(s, 4f) { PanTuning.CENTRE_X - 100f }
        val before = s.viewX
        run(s, 0.05f) { PanTuning.CENTRE_X + 100f }
        assertTrue("jumped ${s.viewX - before}", s.viewX - before < 60f)
        run(s, 5f) { PanTuning.CENTRE_X + 100f }
        assertEquals(PanTuning.CENTRE_X + 100f, s.viewX, 1f)
    }

    @Test
    fun aPieceLeftOfTheMiddleShowsItsRightSideAndTheReverse() {
        val s = sway()
        run(s, 6f) { 60f }
        assertTrue("left of the middle: right side", s.perspective(-100f) < 0f)
        assertTrue("right of the middle: left side", s.perspective(220f) > 0f)
        assertEquals(0f, s.perspective(60f), 0.01f)
        assertEquals(-PanTuning.PERSPECTIVE_GAIN, s.perspective(-400f), 1e-4f)
        assertEquals(PanTuning.PERSPECTIVE_GAIN, s.perspective(500f), 1e-4f)
    }

    @Test
    fun reducedMotionHasNoPanAndNoPerspective() {
        val s = DepthSway(true, DepthLevel.MEDIUM.idleAmplitude)
        run(s, 5f) { PanTuning.CENTRE_X + 190f }
        assertEquals(0f, s.pan, 0f)
        assertEquals(0f, s.position, 0f)
        assertEquals(0f, s.perspective(-100f), 0f)
    }

    @Test
    fun withoutFollowingThereIsNoPanOrPerspective() {
        val s = sway()
        repeat(600) { s.step(1f / 60f) }
        assertEquals(0f, s.pan, 0f)
        assertEquals(0f, s.perspective(-100f), 0f)
        assertEquals(s.idle, s.position, 0f)
    }
}
