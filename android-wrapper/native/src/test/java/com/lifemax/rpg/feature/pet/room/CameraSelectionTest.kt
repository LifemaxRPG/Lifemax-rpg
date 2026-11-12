package com.lifemax.rpg.feature.pet.room

import com.lifemax.rpg.feature.pet.body.CatBody
import com.lifemax.rpg.feature.pet.body.PlanStep
import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.body.World
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The camera follows the selected cat (the page's focus, the card's cat): the pure aim, the glide
 * to the other cat (a pan however far, never a cut), the cut with reduced motion and on the first
 * choice, the follow with its dead zone, and the director's own rule when nothing is chosen.
 */
class CameraSelectionTest {
    private val dt = 1f / 60f

    private fun cat(x: Float, seed: Int) =
        CatBody(seed, x).also { it.placeAt(x, World.ART_Z, SurfaceId.FLOOR, 1) }

    private fun run(
        d: CameraDirector,
        a: CatBody,
        b: CatBody,
        seconds: Float,
        pick: Int,
        cut: Boolean = false,
        each: (Float) -> Unit = {},
    ) {
        repeat(Math.round(seconds * 60)) {
            a.step(dt)
            b.step(dt)
            d.update(a, b, dt, pick, cut)
            each(d.camera.cx)
        }
    }

    @Test
    fun theAimIsTheCatWithALeadOnlyWhileItTravels() {
        assertEquals(40f, CameraAim.target(40f, 1, moving = false, running = false), 0f)
        assertEquals(40f - RoomCamera.LEAD, CameraAim.target(40f, -1, true, false), 0f)
        assertEquals(40f + RoomCamera.RUN_LEAD, CameraAim.target(40f, 1, true, true), 0f)
    }

    @Test
    fun twoCatsCloseTogetherAreFramedOnTheSelectedOneNotTheMidpoint() {
        val a = cat(0f, 1)
        val b = cat(80f, 2)
        val d = CameraDirector()
        run(d, a, b, 2f, pick = 0)
        assertEquals(0f, d.camera.cx, 1f)
        run(d, a, b, 3f, pick = 1)
        assertEquals("the other cat is framed", 80f, d.camera.cx, 1f)
        run(d, a, b, 3f, pick = 0)
        assertEquals("and back", 0f, d.camera.cx, 1f)
    }

    @Test
    fun choosingTheOtherCatFarAwayIsASmoothPanNotACut() {
        val a = cat(-200f, 1)
        val b = cat(200f, 2) // 400 px away: the follow would cut (CUT is 260)
        val d = CameraDirector()
        run(d, a, b, 1f, pick = 0)
        assertEquals(-200f, d.camera.cx, 1f)
        var last = d.camera.cx
        var biggest = 0f
        var frames = 0
        run(d, a, b, 4f, pick = 1) {
            biggest = maxOf(biggest, abs(it - last))
            assertTrue("never goes back: $it after $last", it >= last - 0.001f)
            if (abs(it - 200f) > 2f) frames++
            last = it
        }
        assertEquals(200f, d.camera.cx, 1f)
        val firstStepMax =
            400f * (1f - Math.exp((-RoomCamera.TRANSIT_RATE * dt).toDouble()).toFloat())
        assertTrue(
            "a frame moved $biggest, the ease allows $firstStepMax",
            biggest <= firstStepMax + 0.01f,
        )
        assertTrue("took $frames frames: not a pan", frames > 40)
        assertTrue("took $frames frames: too slow", frames < 150)
    }

    @Test
    fun reducedMotionAndTheFirstChoiceCutAtOnce() {
        val a = cat(-200f, 1)
        val b = cat(200f, 2)
        val first = CameraDirector()
        first.update(a, b, 0f, CameraDirector.NONE) // placed on the first cat
        first.update(a, b, dt, 1)
        assertEquals("nothing was chosen before: cut", 200f, first.camera.cx, 0.01f)
        val still = CameraDirector()
        still.update(a, b, 0f, 0, cutOnChange = true)
        still.update(a, b, dt, 1, cutOnChange = true)
        assertEquals("reduced motion: cut", 200f, still.camera.cx, 0.01f)
    }

    @Test
    fun theSelectedCatIsFollowedAsItWalksAndTheOtherOneIsIgnored() {
        val a = cat(-200f, 1)
        val b = cat(0f, 2)
        val d = CameraDirector()
        run(d, a, b, 1f, pick = 1)
        b.replacePlan(1, listOf(PlanStep.Walk(120f)))
        a.replacePlan(1, listOf(PlanStep.Walk(-100f)))
        var worst = 0f
        run(d, a, b, 6f, pick = 1) { if (b.isTravelling) worst = maxOf(worst, abs(b.pose.x - it)) }
        assertTrue("fell behind by $worst", worst < 60f)
        assertEquals(120f, d.camera.cx, 15f)
    }

    @Test
    fun aRestingSelectedCatNeverMakesTheRoomDrift() {
        val a = cat(0f, 1)
        val b = cat(300f, 2)
        val d = CameraDirector()
        run(d, a, b, 1f, pick = 0)
        val at = d.camera.cx
        var moved = false
        run(d, a, b, 10f, pick = 0) { if (it != at) moved = true }
        assertTrue("the camera drifted", !moved)
    }

    @Test
    fun withNoChoiceTheDirectorKeepsItsOwnRule() {
        val a = cat(0f, 1)
        val b = cat(80f, 2)
        val d = CameraDirector()
        run(d, a, b, 3f, pick = CameraDirector.NONE)
        assertEquals(40f, d.camera.cx, 1f)
    }

    @Test
    fun aGlideEndsAndTheOrdinaryFollowTakesOver() {
        val c = RoomCamera(0f)
        c.glideTo()
        repeat(60 * 4) { c.update(300f, false, false, dt) }
        assertTrue(!c.gliding)
        assertEquals(300f, c.cx, 0f)
    }
}
