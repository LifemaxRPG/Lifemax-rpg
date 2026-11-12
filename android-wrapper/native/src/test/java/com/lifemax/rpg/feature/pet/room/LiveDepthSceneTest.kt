package com.lifemax.rpg.feature.pet.room

import com.lifemax.rpg.feature.pet.art.PetArtResources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The live room's camera-follow parallax on the painted room: the pan shifts the near rows against
 * the far wall, each piece takes the camera step of its own yaw (a piece left of the middle of the
 * screen shows its right side), the style without the follow is untouched, and the camera steps can
 * be made by another thread, let go of and made again without the picture changing.
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
        val rest = s.depth!!.sheets.rest
        assertTrue("sofa step ${s.depth!!.stepOf("sofa")}", s.depth!!.stepOf("sofa") < rest)
        assertTrue("fridge step ${s.depth!!.stepOf("fridge")}", s.depth!!.stepOf("fridge") > rest)
        // a piece at the middle of the screen shows its front
        val mid = scene()
        mid.pan(-96f)
        mid.paint()
        assertEquals(mid.depth!!.sheets.rest, mid.depth!!.stepOf("sofa"))
    }

    @Test
    fun theStepsShowTheSideTheYawSays() {
        // a negative yaw (a piece left of the middle) shows the right side: the piece reaches
        // farther right than at rest and not farther left; the reverse for a positive one
        val d = scene().depth!!
        val sheets = d.sheets
        val sofa = d.pieces.indexOfFirst { it.name == "sofa" }
        fun extent(step: Int): IntArray {
            val st = sheets.step(step)
            var lo = Int.MAX_VALUE
            var hi = Int.MIN_VALUE
            for (b in st.first[sofa] until st.end[sofa]) {
                lo = minOf(lo, st.dx[b])
                hi = maxOf(hi, st.dx[b] + st.w[b] - 1)
            }
            return intArrayOf(lo, hi)
        }
        val rest = extent(sheets.rest)
        val toRight = extent(sheets.rest - 2) // a yaw of -0.5
        val toLeft = extent(sheets.rest + 2) // +0.5
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
    fun releasingTheStepsKeepsTheRestPictureAndWarmingMakesThemAgain() {
        val s = scene()
        val d = s.depth!!
        d.warm()
        assertEquals(d.sheets.count, d.sheets.made)
        s.pan(200f)
        d.release()
        assertEquals(1, d.sheets.made)
        assertTrue(d.ready)
        d.warm()
        assertEquals(d.sheets.count, d.sheets.made)
        val warmed = s.paint().pixels.toList()
        // let go again; with a background warmer a frame draws the nearest made step (the rest
        // one) and never makes one itself; once the steps are back the picture is the same
        d.release()
        d.asyncWarm = true
        val resting = s.paint()
        assertEquals(1, d.sheets.made)
        assertTrue(resting.pixels.count { it == SOFA_OUTLINE } > 100)
        d.warm()
        // the steps made since fade in over a few frames (never a jump), then the picture is the
        // same
        repeat(120) {
            d.step(DT)
            s.paint()
        }
        assertEquals(warmed, s.paint().pixels.toList())
    }

    @Test
    fun aBackgroundThreadCanWarmWhileTheFramesDraw() {
        val s = scene()
        val d = s.depth!!
        d.asyncWarm = true
        d.prepare()
        val warmer = Thread { d.warm() }
        warmer.start()
        repeat(120) {
            d.follow(if (it % 2 == 0) -80f else 280f)
            d.step(DT)
            s.paint()
        }
        warmer.join()
        assertEquals(d.sheets.count, d.sheets.made)
        s.pan(200f)
        d.release()
        d.warm()
        val after = s.paint().pixels.toList()
        val sync = scene()
        sync.depth!!.warm()
        sync.pan(200f)
        sync.depth!!.release()
        sync.depth!!.warm()
        assertEquals(sync.paint().pixels.toList(), after)
    }

    @Test
    fun aRoomThatNeverSwaysKeepsOnlyItsRestPicture() {
        val s = scene(RoomStyle.DEPTH25_STILL)
        s.depth!!.prepare()
        s.depth!!.release()
        assertEquals(1, s.depth!!.sheets.made)
        assertNull(RoomScene(art, RoomStyle.CLASSIC).depth)
    }

    private companion object {
        const val DT = 1f / 60f

        /** The sofa's outline, as the painted art has it. */
        const val SOFA_OUTLINE = 0xFF1E162D.toInt()
    }
}
