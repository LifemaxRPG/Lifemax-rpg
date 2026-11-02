package com.lifemax.rpg.feature.pet.room

import com.lifemax.rpg.feature.pet.body.CatBody
import com.lifemax.rpg.feature.pet.body.PlanStep
import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.body.World
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The card's camera: the web's easing and lead, the clamp to the house, the cut, and which cat the
 * camera looks at when there are two.
 */
class RoomCameraTest {
    private fun run(
        camera: RoomCamera,
        target: Float,
        moving: Boolean,
        seconds: Float,
        hz: Int = 60,
    ) {
        repeat(Math.round(seconds * hz)) { camera.update(target, moving, false, 1f / hz) }
    }

    @Test
    fun theCameraEasesTowardAWalkingCatAndNeverOvershoots() {
        val camera = RoomCamera(0f)
        var last = 0f
        repeat(120) {
            camera.update(100f, true, false, 1f / 60f)
            assertTrue(
                "moved backwards or overshot: ${camera.cx}",
                camera.cx >= last && camera.cx <= 100f,
            )
            last = camera.cx
        }
        assertTrue("walking pace: ${camera.cx}", camera.cx > 80f)
    }

    @Test
    fun theSamePanTakesTheSameTimeAtAnyFrameRate() {
        val a = RoomCamera(0f)
        val b = RoomCamera(0f)
        run(a, 120f, true, 1f, 60)
        run(b, 120f, true, 1f, 120)
        assertEquals(a.cx, b.cx, 0.2f)
    }

    @Test
    fun aGallopIsFollowedFasterThanAWalkAndARestingCatSlowest() {
        fun after(moving: Boolean, running: Boolean): Float {
            val c = RoomCamera(0f)
            repeat(30) { c.update(100f, moving, running, 1f / 60f) }
            return c.cx
        }
        val resting = RoomCamera(0f).also { it.update(40f, false, false, 0.5f) }.cx
        assertTrue(after(true, true) > after(true, false))
        assertTrue(after(true, false) > resting)
    }

    @Test
    fun aRestingCatNeverMakesTheRoomDrift() {
        val camera = RoomCamera(50f)
        run(camera, 60f, false, 5f) // 10 px off: inside the dead zone
        assertEquals(50f, camera.cx, 0f)
        run(camera, 70f, false, 5f) // 20 px off: the camera goes, then settles on a whole pixel
        assertEquals(70f, camera.cx, 0f)
    }

    @Test
    fun aJumpOfMoreThanTheCutDistanceIsACutNotAPan() {
        val camera = RoomCamera(-100f)
        camera.update(200f, false, false, 1f / 60f)
        assertEquals(200f, camera.cx, 0f)
        camera.update(200f + RoomCamera.CUT - 1f, false, false, 1f / 60f)
        assertTrue("a shorter jump pans: ${camera.cx}", camera.cx < 460f)
    }

    @Test
    fun theViewStaysInsideTheHouse() {
        val camera = RoomCamera(0f)
        val lo = RoomLayout.HOUSE_X0.toFloat()
        val hi = lo + RoomLayout.HOUSE_WIDTH
        for (cx in listOf(-300f, -154f, 0f, 190f, 346f, 500f)) {
            camera.cutTo(cx)
            for (view in listOf(192f, 216f, 400f)) {
                val left = camera.left(view)
                assertTrue("view $view at $cx: left $left", left >= lo && left + view <= hi)
            }
        }
        camera.cutTo(96f)
        assertEquals(0f, camera.left(192f), 0f)
    }

    // ---------------------------------------------------------------- which cat

    private fun cat(x: Float, seed: Int = 1) =
        CatBody(seed, x).also { it.placeAt(x, World.ART_Z, SurfaceId.FLOOR, 1) }

    private fun follow(director: CameraDirector, a: CatBody, b: CatBody?, seconds: Float) {
        repeat(Math.round(seconds * 60)) {
            a.step(1f / 60f)
            b?.step(1f / 60f)
            director.update(a, b, 1f / 60f)
        }
    }

    @Test
    fun oneCatIsFollowedWithALeadInTheDirectionItWalks() {
        val director = CameraDirector()
        val a = cat(0f)
        director.update(a, null, 0f)
        a.replacePlan(1, listOf(PlanStep.Walk(120f)))
        follow(director, a, null, 1.5f)
        assertTrue(
            "camera ${director.camera.cx} is behind the cat ${a.pose.x}",
            director.camera.cx > a.pose.x - 30f,
        )
        assertTrue(
            "camera ${director.camera.cx} is not leading ${a.pose.x}",
            director.camera.cx > 0f,
        )
    }

    @Test
    fun twoCatsCloseTogetherAreFramedByTheirMidpoint() {
        val director = CameraDirector()
        val a = cat(0f)
        val b = cat(80f, 2)
        follow(director, a, b, 3f)
        assertEquals(40f, director.camera.cx, 1f)
    }

    @Test
    fun twoCatsFarApartAreFollowedByTheOneThatMoves() {
        val director = CameraDirector()
        val a = cat(-100f)
        val b = cat(250f, 2)
        follow(director, a, b, 1f)
        assertEquals(-100f, director.camera.cx, 1f) // the first cat is where it starts
        b.replacePlan(1, listOf(PlanStep.Walk(320f)))
        follow(director, a, b, 4f)
        assertTrue(
            "the camera should have gone to the walker: ${director.camera.cx}",
            director.camera.cx > 200f,
        )
    }

    @Test
    fun theCameraDoesNotFlickBetweenTwoCatsThatBothMove() {
        val director = CameraDirector()
        val a = cat(-100f)
        val b = cat(250f, 2)
        follow(director, a, b, 0.5f)
        a.replacePlan(
            1,
            listOf(PlanStep.Walk(-60f), PlanStep.Act("idle_sit", 1f), PlanStep.Walk(-120f)),
        )
        b.replacePlan(
            1,
            listOf(PlanStep.Walk(300f), PlanStep.Act("idle_sit", 1f), PlanStep.Walk(220f)),
        )
        var swings = 0
        var side = 0
        repeat(60 * 6) {
            a.step(1f / 60f)
            b.step(1f / 60f)
            director.update(a, b, 1f / 60f)
            val now = if (director.camera.cx > 75f) 1 else -1
            if (side != 0 && now != side) swings++
            side = now
        }
        assertTrue("the view crossed the house $swings times", swings <= 1)
    }

    @Test
    fun aCatThatWalksAwayAcrossTheHouseIsACutWhenFarAndAPanWhenNear() {
        val director = CameraDirector()
        val a = cat(-140f)
        director.update(a, null, 0f)
        a.placeAt(330f, World.ART_Z, SurfaceId.FLOOR, 1) // put somewhere new, 470 px away
        director.update(a, null, 1f / 60f)
        assertEquals(330f, director.camera.cx, 0f)
    }
}
