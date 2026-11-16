package com.lifemax.rpg.feature.pet.room

import com.lifemax.rpg.feature.pet.art.ArgbImage
import com.lifemax.rpg.feature.pet.art.PetArtResources
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The owner, after the continuous furniture: "the 3D effect is still looking janky compared to the
 * habits tab". The phone's canvas placed each depth strip of a piece at the device pixel nearest
 * its own position (with the layer's nudge), so as the camera panned the strips of one piece
 * crossed pixel edges at different moments: its sides flickered a pixel wider and narrower and
 * their texture boiled. Now the nudge is rounded once for the piece ([RoomCanvas.stripX]), and the
 * camera no longer ends a pan with a jump to a whole pixel. Measured on a canvas of 7 device pixels
 * per house pixel (an S25 Ultra's card), as the phone places the strips.
 */
class SmoothMotionTest {
    private val art = PetArtResources.loadRoom()

    /** Records where each slot's strips land, in device pixels, by the canvas rule [place]. */
    private class Extents(val scale: Int, val place: (Float, Float) -> Int) :
        RoomCanvas by CountingCanvas() {
        private var nudge = 0f
        val lo = IntArray(RoomCanvas.MAX_STACKS)
        val hi = IntArray(RoomCanvas.MAX_STACKS)
        val loExact = FloatArray(RoomCanvas.MAX_STACKS)
        val hiExact = FloatArray(RoomCanvas.MAX_STACKS)
        val placed = ArrayList<Long>()

        fun begin() {
            lo.fill(Int.MAX_VALUE)
            hi.fill(Int.MIN_VALUE)
            loExact.fill(Float.MAX_VALUE)
            hiExact.fill(-Float.MAX_VALUE)
            placed.clear()
        }

        override fun nudge(dx: Float) {
            nudge = dx
        }

        override fun stack(
            image: ArgbImage,
            slot: Int,
            version: Int,
            sx: Int,
            sy: Int,
            sw: Int,
            sh: Int,
            dx: Float,
            dy: Float,
        ) {
            val x = place(nudge, dx)
            lo[slot] = minOf(lo[slot], x)
            hi[slot] = maxOf(hi[slot], x + sw * scale)
            loExact[slot] = minOf(loExact[slot], (dx + nudge) * scale)
            hiExact[slot] = maxOf(hiExact[slot], (dx + nudge + sw) * scale)
            // where the strip lands against the piece's own pixel (its nudge's)
            val local = x - Math.round(nudge * scale)
            placed.add((local.toLong() shl 32) or ((sx * 4096L + sy) and 0xFFFFFFFFL))
        }
    }

    private val shipped: (Float, Float) -> Int = { n, dx -> RoomCanvas.stripX(0, S, n, dx) }

    /** How the canvas placed strips before: the nudge and the position rounded together. */
    private val together: (Float, Float) -> Int = { n, dx -> Math.round((dx + n) * S) }

    @Test
    fun aPieceSlidesWholeWhileTheCameraPans() {
        // the same view under any nudge: every strip lands at the same place against the piece
        val faces = RoomScene(art, RoomStyle.depth25(DepthLevel.RICH)).depth!!
        for (piece in faces.pieces) {
            val f = faces.facesOf(piece.name)
            for (yaw in floatArrayOf(-0.6f, -0.25f, 0.3f, 0.55f)) {
                val vx = FaceView.vx(yaw, DepthLevel.RICH)
                val vy = FaceView.vy(yaw, DepthLevel.RICH)
                var first: List<Long>? = null
                for (k in 0..32) {
                    val c = Extents(S, shipped)
                    c.begin()
                    c.nudge(k / 32f * 3f)
                    for (b in f.bands - 1 downTo 0) {
                        f.paintBand(c, 0, b, piece.houseX.toFloat(), piece.y.toFloat(), vx, vy)
                    }
                    if (first == null) first = c.placed.toList()
                    else
                        assertEquals(
                            "${piece.name} at yaw $yaw, nudge ${k / 32f * 3f}",
                            first,
                            c.placed,
                        )
                }
            }
        }
    }

    /**
     * Frames of a slow sweep of the sway from -1 to 1 in which a piece's drawn width steps the
     * other way from its exact width (it narrows while its side grows, or the reverse), by [place].
     */
    private fun wrongWaySteps(place: (Float, Float) -> Int, names: List<String>): Int {
        val d = RoomScene(art, RoomStyle.depth25(DepthLevel.RICH).copy(idleDrift = false)).depth!!
        val slots = names.map { n -> d.pieces.indexOfFirst { it.name == n } }
        val c = Extents(S, place)
        val width = IntArray(slots.size)
        val exact = FloatArray(slots.size)
        var wrong = 0
        for (i in 0..STEPS) {
            d.sway.restAt(-1f + 2f * i / STEPS)
            c.begin()
            d.paintScene(c, NoCats)
            for ((n, p) in slots.withIndex()) {
                val w = c.hi[p] - c.lo[p]
                val e = c.hiExact[p] - c.loExact[p]
                if (i > 0 && w != width[n]) {
                    val de = e - exact[n]
                    if (abs(de) < 1e-4f || (w > width[n]) != (de > 0f)) wrong++
                }
                width[n] = w
                exact[n] = e
            }
        }
        return wrong
    }

    @Test
    fun aSideOnlyGrowsWhileTheCameraTurnsOneWay() {
        val names = listOf("sofa", "bookcase_plant", "counter", "cat_tree_slab_mid")
        val before = wrongWaySteps(together, names)
        val now = wrongWaySteps(shipped, names)
        assertTrue("the old placement flickered ($before wrong-way steps)", before > 50)
        assertEquals("wrong-way steps of the sides", 0, now)
    }

    @Test
    fun aPanEndsWithoutAJump() {
        // a cat walks and stops at a fractional x: the camera's last step is a sliver of a pixel
        for (k in 0 until 20) {
            val stop = 100.13f + k * 0.37f
            val camera = RoomCamera(0f)
            var x = 0f
            var prev = camera.cx
            var last = 0f
            repeat(120 * 12) {
                val walking = x < stop
                if (walking) x = minOf(stop, x + 30f / 120f)
                camera.update(x + if (walking) RoomCamera.LEAD else 0f, walking, false, 1f / 120f)
                val step = abs(camera.cx - prev)
                if (step > 0f) last = step
                prev = camera.cx
            }
            assertEquals("stopped at $stop", stop, camera.cx, 0f)
            assertTrue("the last step to $stop: $last px", last < 0.03f)
        }
    }

    private companion object {
        /** Device pixels per house pixel: the Cat tab on a 1440 px wide screen. */
        const val S = 7
        const val STEPS = 240
    }
}
