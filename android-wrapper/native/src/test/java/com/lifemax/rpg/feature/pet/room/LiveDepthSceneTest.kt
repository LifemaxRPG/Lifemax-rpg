package com.lifemax.rpg.feature.pet.room

import com.lifemax.rpg.feature.pet.art.PetArtResources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The live room's camera-follow parallax on the painted room: the pan shifts the near rows against
 * the far wall, each piece turns by its own yaw (a piece left of the middle of the screen shows its
 * right side), the style without the follow is untouched, and the faces can be baked by another
 * thread while the frames draw, once for every room of the art.
 */
class LiveDepthSceneTest {
    private val art = PetArtResources.loadRoom()
    private val live = RoomStyle.live(DepthLevel.MEDIUM)

    private fun scene(style: RoomStyle = live) = RoomScene(art, style)

    /** Follow the screen's middle [x] for [seconds], then hold the idle drift at 0. */
    private fun RoomScene.pan(x: Float, seconds: Float = 6f) {
        val d = depth!!
        repeat(Math.round(seconds * 60)) {
            d.follow(x)
            d.step(DT)
        }
        d.sway.restAt(0f)
    }

    private fun RoomScene.paint(): SoftCanvas {
        val canvas = SoftCanvas(art)
        paintBackdrop(canvas, 12.0, 0.0, 0)
        paintScene(canvas, NoCats)
        return canvas
    }

    @Test
    fun theLiveStyleIsMediumWithTheFollowAndTheStillStyleHasNeither() {
        assertEquals(DepthLevel.MEDIUM, live.level)
        assertTrue(live.cameraFollow && live.parallax && live.volume && live.layers)
        val still = RoomStyle.live(DepthLevel.MEDIUM, reducedMotion = true)
        assertTrue(still.reducedMotion && !still.cameraFollow)
        assertEquals(RoomStyle.CLASSIC, RoomStyle.live(null))
        assertEquals(RoomStyle.CLASSIC, RoomStyle.live(null, reducedMotion = true))
        assertTrue(!RoomStyle.DEPTH25.cameraFollow)
    }

    @Test
    fun nearbyFurnitureShiftsAgainstTheFarWallWhenTheCameraPans() {
        val left = scene().also { it.pan(-80f) }.depth!!
        val right = scene().also { it.pan(280f) }.depth!!
        val farWall = left.shiftAtZ(0f) - right.shiftAtZ(0f)
        val near = left.shiftAtZ(60f) - right.shiftAtZ(60f)
        assertTrue("near $near px against the wall's $farWall", near >= farWall + 3)
        assertTrue("slight: the wall moves at most 2 px between the ends", farWall in 0..2)
        assertTrue("and the near rows under 12", near in 4..12)
    }

    @Test
    fun aPieceLeftOfTheMiddleShowsItsRightSideAndTheReverse() {
        // the middle of the screen over the bedroom: the sofa is left of it, the fridge right of it
        val s = scene()
        s.pan(150f)
        s.paint()
        assertTrue("sofa yaw ${s.depth!!.yawOf("sofa")}", s.depth!!.yawOf("sofa") < -0.1f)
        assertTrue("fridge yaw ${s.depth!!.yawOf("fridge")}", s.depth!!.yawOf("fridge") > 0.1f)
        // a piece at the middle of the screen shows its front
        val mid = scene()
        mid.pan(-96f)
        mid.paint()
        assertEquals(0f, mid.depth!!.yawOf("sofa"), 0.1f)
    }

    /** A canvas that only records how far right and left the strips of one slot reach. */
    private class Reach(val slot: Int) : RoomCanvas by CountingCanvas() {
        var lo = Float.MAX_VALUE
        var hi = -Float.MAX_VALUE

        override fun stack(
            image: com.lifemax.rpg.feature.pet.art.ArgbImage,
            slot: Int,
            version: Int,
            sx: Int,
            sy: Int,
            sw: Int,
            sh: Int,
            dx: Float,
            dy: Float,
        ) {
            if (slot != this.slot) return
            lo = minOf(lo, dx)
            hi = maxOf(hi, dx + sw)
        }
    }

    @Test
    fun theFacesShowTheSideTheYawSays() {
        // a negative yaw (a piece left of the middle) shows the right side: the piece reaches
        // farther right than at rest and not farther left; the reverse for a positive one
        val d = scene().depth!!
        val sofa = d.pieces.indexOfFirst { it.name == "sofa" }
        val faces = d.facesOf("sofa")
        fun extent(yaw: Float): FloatArray {
            val c = Reach(sofa)
            val vx = FaceView.vx(yaw, DepthLevel.MEDIUM)
            val vy = FaceView.vy(yaw, DepthLevel.MEDIUM)
            for (k in faces.bands - 1 downTo 0) faces.paintBand(c, sofa, k, 0f, 0f, vx, vy)
            return floatArrayOf(c.lo, c.hi)
        }
        val rest = extent(0f)
        val toRight = extent(-0.5f)
        val toLeft = extent(0.5f)
        assertTrue("right side shows", toRight[1] > rest[1] && toRight[0] >= rest[0])
        assertTrue("left side shows", toLeft[0] < rest[0] && toLeft[1] <= rest[1])
    }

    @Test
    fun theStyleWithoutTheFollowIgnoresTheCamera() {
        val plain = scene(RoomStyle.depth25(DepthLevel.MEDIUM))
        val followed = scene(RoomStyle.depth25(DepthLevel.MEDIUM))
        repeat(600) {
            followed.depth!!.follow(300f)
            followed.depth!!.step(DT)
            plain.depth!!.step(DT)
        }
        assertEquals(plain.depth!!.sway.position, followed.depth!!.sway.position, 0f)
        assertEquals(plain.paint().pixels.toList(), followed.paint().pixels.toList())
    }

    @Test
    fun reducedMotionIsTheRestPictureWhateverTheCamera() {
        val s = scene(RoomStyle.live(DepthLevel.MEDIUM, reducedMotion = true))
        repeat(600) {
            s.depth!!.follow(300f)
            s.depth!!.step(DT)
        }
        assertEquals(0f, s.depth!!.sway.position, 0f)
        val rest = scene(RoomStyle.depth25(DepthLevel.MEDIUM).copy(reducedMotion = true))
        assertEquals(rest.paint().pixels.toList(), s.paint().pixels.toList())
    }

    @Test
    fun theCameraDoesNotChangeTheRestPicturesColoursOnlyWhereTheyMove() {
        // a pan moves the room; the picture is never blank and keeps the sofa's outline colour
        val s = scene()
        s.pan(300f)
        val c = s.paint()
        assertTrue(c.pixels.count { it == SOFA_OUTLINE } > 100)
    }

    // ------------------------------------------------------------------ memory and threads

    @Test
    fun theFacesAreBakedOnceAndSharedByEveryRoomOfTheArt() {
        val s = scene()
        val d = s.depth!!
        d.warm()
        assertTrue(d.ready)
        val sofa = d.facesOf("sofa")
        // another room of the same art, at another level, draws from the same faces
        val other = RoomScene(art, RoomStyle.live(DepthLevel.SUBTLE)).depth!!
        assertTrue(other.ready)
        assertSame(sofa, other.facesOf("sofa"))
    }

    @Test
    fun aBackgroundThreadCanBakeWhileTheFramesDraw() {
        // a fresh art, so nothing is baked yet: the frames draw the flat sprites until it is
        val fresh = PetArtResources.loadRoom()
        val s = RoomScene(fresh, live)
        val d = s.depth!!
        d.asyncWarm = true
        assertTrue(!d.ready)
        val flat = SoftCanvas(fresh)
        s.paintBackdrop(flat, 12.0, 0.0, 0)
        s.paintScene(flat, NoCats)
        assertTrue("never blank", flat.pixels.count { it == SOFA_OUTLINE } > 100)
        val warmer = Thread { d.warm() }
        warmer.start()
        repeat(120) {
            d.follow(if (it % 2 == 0) -80f else 280f)
            d.step(DT)
            val c = SoftCanvas(fresh)
            s.paintBackdrop(c, 12.0, 0.0, 0)
            s.paintScene(c, NoCats)
        }
        warmer.join()
        assertTrue(d.ready)
        s.pan(200f)
        val after =
            SoftCanvas(fresh).also {
                s.paintBackdrop(it, 12.0, 0.0, 0)
                s.paintScene(it, NoCats)
            }
        val sync = scene()
        sync.depth!!.warm()
        sync.pan(200f)
        assertEquals(sync.paint().pixels.toList(), after.pixels.toList())
    }

    @Test
    fun theClassicRoomHasNoDepth() {
        assertNull(RoomScene(art, RoomStyle.CLASSIC).depth)
    }

    private companion object {
        const val DT = 1f / 60f

        /** The sofa's outline, as the painted art has it. */
        const val SOFA_OUTLINE = 0xFF1E162D.toInt()
    }
}
