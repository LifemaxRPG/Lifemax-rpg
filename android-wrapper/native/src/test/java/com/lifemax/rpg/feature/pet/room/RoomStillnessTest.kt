package com.lifemax.rpg.feature.pet.room

import com.lifemax.rpg.feature.pet.art.PetArtResources
import kotlin.math.sign
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The owner's "room shake" (PET_TAB_INVENTORY.md): with the camera standing still, the background
 * and the furniture of the live room moved. The live room is driven here as the screen drives it
 * (120 frames a second, the screen's middle fed to the camera-follow every frame) and painted every
 * frame on a software canvas; a still camera must draw bit-identical frames, a camera that wobbles
 * below a pixel too, and a camera that pans and stops must come to rest without anything stepping
 * back and forth on the way.
 */
class RoomStillnessTest {
    private val art = PetArtResources.loadRoom()
    private val live = RoomStyle.live(DepthLevel.MEDIUM)

    private fun RoomScene.frame(): IntArray {
        val canvas = SoftCanvas(art)
        paintBackdrop(canvas, NOON, 0.0, 0)
        paintScene(canvas, NoCats)
        return canvas.pixels
    }

    /**
     * Run [scene] for [seconds] at [HZ] with the screen's middle at [screenX] of the time, and
     * count the painted frames after [settle] seconds that differ from the frame before.
     */
    private fun changedFrames(
        scene: RoomScene,
        seconds: Float,
        settle: Float,
        screenX: (Float) -> Float,
    ): Int {
        val d = scene.depth!!
        var last: IntArray? = null
        var changed = 0
        val frames = Math.round(seconds * HZ)
        for (i in 0 until frames) {
            val t = i * DT
            d.follow(screenX(t))
            d.step(DT)
            if (t < settle) continue
            val now = scene.frame()
            if (last != null && !now.contentEquals(last)) changed++
            last = now
        }
        return changed
    }

    @Test
    fun theLiveRoomHasNoIdleDriftButTheGalleryStyleKeepsIt() {
        assertTrue(!live.idleDrift && RoomStyle.DEPTH25.idleDrift)
        val still = RoomScene(art, live).depth!!
        val gallery = RoomScene(art, RoomStyle.depth25(DepthLevel.MEDIUM)).depth!!
        var moved = 0f
        repeat(20 * HZ) {
            still.follow(PanTuning.CENTRE_X)
            still.step(DT)
            gallery.step(DT)
            assertEquals(
                "the live room's camera, standing still, never sways",
                0f,
                still.sway.shown,
            )
            moved = maxOf(moved, kotlin.math.abs(gallery.sway.shown))
        }
        assertTrue("the gallery style still drifts: $moved", moved > 0.2f)
    }

    @Test
    fun aStillCameraDrawsBitIdenticalFrames() {
        // the middle of the screen over the bedroom: the pieces on either side show their sides,
        // and the pan has moved the near rows; nothing of that may move from frame to frame
        val s = RoomScene(art, live)
        val changed = changedFrames(s, 11f, 1f) { BEDROOM_X } // 10 s held still
        assertEquals("frames that changed with the camera standing still", 0, changed)
        val sofa = s.depth!!.stepOf("sofa")
        assertTrue("the sofa shows its side ($sofa)", sofa != s.depth!!.sheets.rest)
    }

    @Test
    fun aCameraThatWobblesBelowAPixelDrawsTheSameFrames() {
        val noise = Random(7)
        val s = RoomScene(art, live)
        val changed =
            changedFrames(s, 6f, 2f) { BEDROOM_X + (noise.nextFloat() * 2f - 1f) * WOBBLE_PX }
        assertEquals("frames that changed under a +-$WOBBLE_PX px wobble", 0, changed)
    }

    @Test
    fun aPanThatStopsComesToRestWithoutSteppingBackAndForth() {
        val s = RoomScene(art, live)
        val d = s.depth!!
        val tracked = Array(2 + d.pieces.size) { ArrayList<Int>() }
        var lastFrame: IntArray? = null
        var changedAtRest = 0
        val frames = Math.round(PAN_TOTAL_S * HZ)
        for (i in 0 until frames) {
            val t = i * DT
            val u = (t / PAN_S).coerceIn(0f, 1f)
            d.follow(PAN_FROM + (PAN_TO - PAN_FROM) * u * u * (3f - 2f * u))
            d.step(DT)
            s.frame() // the painter chooses the steps
            if (t >= PAN_S) {
                tracked[0].add(d.shiftAtZ(0f))
                tracked[1].add(d.shiftAtZ(NEAR_Z))
                for (p in d.pieces.indices) tracked[2 + p].add(d.stepOf(d.pieces[p].name))
            }
            if (t >= PAN_TOTAL_S - REST_CHECK_S) {
                val now = s.frame()
                if (lastFrame != null && !now.contentEquals(lastFrame)) changedAtRest++
                lastFrame = now
            }
        }
        // the lean into the pan is let go of once (the near rows may turn back that once), and
        // nothing turns back again: a spring that settles, not one that rings or limit-cycles
        for (k in tracked.indices) {
            val turns = reversals(tracked[k])
            assertTrue("value $k turned back $turns times on its way to rest", turns <= 1)
        }
        assertEquals("frames that changed once the room was at rest", 0, changedAtRest)
    }

    @Test
    fun aTiltThatHoversDoesNotMoveTheDrawnSway() {
        val noise = Random(3)
        val sway = DepthSway(idleAmplitude = 0f)
        repeat(3 * HZ) {
            sway.tilt = HELD_TILT
            sway.step(DT)
        }
        val held = sway.shown
        repeat(6 * HZ) {
            sway.tilt = HELD_TILT + (noise.nextFloat() * 2f - 1f) * TILT_NOISE
            sway.step(DT)
            assertEquals("the drawn sway under +-$TILT_NOISE of tilt noise", held, sway.shown)
        }
    }

    @Test
    fun aSwayHoveringOnAStepEdgeKeepsItsStep() {
        val d = RoomScene(art, live).depth!!
        val sheets = d.sheets
        val edge = (sheets.swayOf(sheets.rest) + sheets.swayOf(sheets.rest + 1)) / 2f
        var step = sheets.indexOf(edge, sheets.rest)
        var switches = 0
        val noise = Random(5)
        repeat(1000) {
            val next = sheets.indexOf(edge + (noise.nextFloat() * 2f - 1f) * EDGE_NOISE, step)
            if (next != step) switches++
            step = next
        }
        assertEquals("switches of a sway hovering on a step edge", 0, switches)
    }

    /** How many times the values turn back (a step one way, then a step the other). */
    private fun reversals(values: List<Int>): Int {
        var turns = 0
        var dir = 0
        for (i in 1 until values.size) {
            val s = (values[i] - values[i - 1]).sign
            if (s == 0) continue
            if (dir != 0 && s != dir) turns++
            dir = s
        }
        return turns
    }

    private companion object {
        const val HZ = 120
        const val DT = 1f / HZ
        const val NOON = 12.0

        /** World x of the middle of the screen over the bedroom. */
        const val BEDROOM_X = 150f

        /** A camera wobble below a pixel, either way. */
        const val WOBBLE_PX = 0.3f

        /** The pan: from the living room to the kitchen over [PAN_S], then held. */
        const val PAN_FROM = -80f
        const val PAN_TO = 260f
        const val PAN_S = 2f
        const val PAN_TOTAL_S = 9f
        const val REST_CHECK_S = 1f

        /** The depth of a cat on the front rows. */
        const val NEAR_Z = 95f

        /** A held tilt and the noise on it (a sensor's, a hand's). */
        const val HELD_TILT = 0.3f
        const val TILT_NOISE = 0.01f

        /**
         * Noise on a sway resting on a step edge: within the steps' hysteresis (0.15 of a step; a
         * step is a quarter of the sway at MEDIUM), more than the drawn sway ever wanders at rest.
         */
        const val EDGE_NOISE = 0.03f
    }
}
