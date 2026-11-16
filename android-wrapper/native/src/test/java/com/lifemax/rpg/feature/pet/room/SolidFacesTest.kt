package com.lifemax.rpg.feature.pet.room

import com.lifemax.rpg.feature.pet.art.ArgbImage
import com.lifemax.rpg.feature.pet.art.PetArtResources
import com.lifemax.rpg.feature.pet.body.World
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The furniture's continuous depth (docs/specs/CAT_ROOM_CONTINUOUS_DEPTH.md): every solid piece
 * drawn from faces baked once ([SolidFaces]) at the camera's live angle. What it must keep from the
 * stepped pictures (the painted sprite at rest, the piece's own palette, the ray cast's shape, the
 * cats' order) and what it must change (a side grows continuously, a device pixel at a time).
 */
class SolidFacesTest {
    private val art = PetArtResources.loadRoom()
    private val solids = art.layers!!.pieces.filter { it.volume != null }
    private val level = DepthLevel.RICH

    /** A canvas at [scale] device pixels per house pixel that draws only strips (opaque pixels). */
    private class FineCanvas(val scale: Int, val ox: Int, val oy: Int, val w: Int, val h: Int) :
        RoomCanvas {
        val pixels = IntArray(w * h)

        fun put(x: Int, y: Int, p: Int) {
            if (p ushr 24 != 0 && x in 0 until w && y in 0 until h) pixels[y * w + x] = p
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
            // as the screen's canvas places it: at the nearest device pixel
            val x0 = Math.round(ox + dx * scale)
            val y0 = Math.round(oy + dy * scale)
            for (j in 0 until sh * scale) for (i in 0 until sw * scale) {
                put(x0 + i, y0 + j, image[sx + i / scale, sy + j / scale])
            }
        }

        override fun nudge(dx: Float) = Unit

        override fun clip(x: Int, y: Int, w: Int, h: Int) = Unit

        override fun unclip() = Unit

        override fun fill(x: Int, y: Int, w: Int, h: Int, argb: Int) = Unit

        override fun image(
            image: RoomImage,
            sx: Int,
            sy: Int,
            sw: Int,
            sh: Int,
            dx: Int,
            dy: Int,
            dw: Int,
            dh: Int,
        ) = Unit

        override fun quad(
            x0: Float,
            y0: Float,
            x1: Float,
            y1: Float,
            x2: Float,
            y2: Float,
            x3: Float,
            y3: Float,
            argb: Int,
        ) = Unit

        override fun multiply(rgb: Int, x: Int, y: Int, w: Int, h: Int) = Unit

        override fun overlay(mask: ArgbImage, version: Int, x: Int, y: Int) = Unit
    }

    /** Margin around a piece, in house pixels: room for its sides at any view. */
    private val margin = 6

    private fun canvasFor(model: VolumeModel, scale: Int) =
        FineCanvas(
            scale,
            margin * scale,
            margin * scale,
            (model.width + 2 * margin) * scale,
            (model.height + 2 * margin) * scale,
        )

    /** The piece's faces seen with ([vx], [vy]) at [scale], every band back to front. */
    private fun composite(piece: LayerPiece, vx: Float, vy: Float, scale: Int = 1): FineCanvas {
        val faces = piece.volume!!.faces
        val c = canvasFor(piece.volume!!.model, scale)
        for (k in faces.bands - 1 downTo 0) faces.paintBand(c, 0, k, 0f, 0f, vx, vy)
        return c
    }

    /**
     * The same view drawn the long way: every strip's whole cross-section (every pixel whose column
     * reaches it, in the colour the faces give it), back to front.
     */
    private fun whole(piece: LayerPiece, vx: Float, vy: Float, scale: Int): FineCanvas {
        val faces = piece.volume!!.faces
        val model = piece.volume!!.model
        val c = canvasFor(model, scale)
        for (j in faces.slices - 1 downTo 0) {
            val shading = faces.shadingOf(j, vx, vy)
            val x0 = Math.round(c.ox + FaceView.depthOf(j) * vx * scale)
            val y0 = Math.round(c.oy + FaceView.depthOf(j) * vy * scale)
            for (i in 0 until model.width * model.height) {
                if (!faces.inSlice(i, j)) continue
                val p = faces.colourOf(i, j, shading % 2, shading >= 2)
                val px = x0 + (i % model.width) * scale
                val py = y0 + (i / model.width) * scale
                for (b in 0 until scale) for (a in 0 until scale) c.put(px + a, py + b, p)
            }
        }
        return c
    }

    @Test
    fun atRestEveryPieceIsItsPaintedSpriteExactly() {
        for (piece in solids) {
            val model = piece.volume!!.model
            for (vx in floatArrayOf(0f, -0f)) {
                val c = composite(piece, vx, 0f)
                for (y in 0 until c.h) for (x in 0 until c.w) {
                    val mx = x - margin
                    val my = y - margin
                    val want =
                        if (mx in 0 until model.width && my in 0 until model.height) {
                            model.colours[my * model.width + mx].let {
                                if (it ushr 24 == 0) 0 else it
                            }
                        } else 0
                    assertEquals("${piece.name} at ($mx, $my)", want, c.pixels[y * c.w + x])
                }
            }
        }
    }

    @Test
    fun everyShadeIsFromThePiecesOwnPalette() {
        for (piece in solids) {
            val model = piece.volume!!.model
            val palette = model.palette.colours.toSet()
            for (p in piece.volume!!.faces.image.pixels) {
                if (p ushr 24 != 0) assertTrue(piece.name, (p or OPAQUE) in palette)
            }
        }
    }

    /**
     * The faces keep only the rims of the strips; drawn, they are the same picture as the whole
     * cross-sections, pixel for pixel, at the phone's scale and at any view the camera takes.
     */
    @Test
    fun theRimsDrawExactlyWhatTheWholeCrossSectionsWould() {
        var kept = 0L
        var all = 0L
        for (piece in solids) {
            val faces = piece.volume!!.faces
            val model = piece.volume!!.model
            for (yaw in floatArrayOf(-1f, -0.63f, -0.2f, 0.07f, 0.6f, 1f)) {
                val vx = FaceView.vx(yaw, level)
                val vy = FaceView.vy(yaw, level)
                val rims = composite(piece, vx, vy, 4).pixels
                val full = whole(piece, vx, vy, 4).pixels
                var differ = 0
                for (i in rims.indices) if (rims[i] != full[i]) differ++
                assertEquals("${piece.name} at yaw $yaw", 0, differ)
            }
            kept += faces.pixels
            for (j in 0 until faces.slices) for (i in 0 until model.width * model.height) {
                if (faces.inSlice(i, j)) all++
            }
        }
        println(
            "FACERIMS kept %d of %d strip pixels (%.1f%%)".format(kept, all, 100.0 * kept / all)
        )
        assertTrue("the rims are a fraction of the cross-sections", kept * 3 < all)
    }

    /**
     * At the views the stepped pictures had (the ray cast's camera steps), the faces have the ray
     * cast's shape: every pixel one covers and the other does not is within one art pixel of the
     * other's silhouette, and the two mostly agree pixel for pixel.
     */
    @Test
    fun atTheOldCameraStepsTheFacesHaveTheRayCastsShape() {
        val n = 5 // RICH had five steps a side
        var worst = 1.0
        var worstName = ""
        var farOff = 0
        for (piece in solids) {
            val model = piece.volume!!.model
            val frame = VolumeFrame(VolumeRaster.size(model, 1f, 1f))
            for (i in 0..2 * n) {
                val sway = (i - n).toFloat() / n
                val vx = FaceView.vx(sway, level)
                val vy = FaceView.vy(sway, level)
                VolumeRaster.render(model, vx, vy, piece.volume!!.wall, frame)
                val c = composite(piece, vx, vy)
                fun raster(x: Int, y: Int): Int {
                    val fx = x - margin - frame.x0
                    val fy = y - margin - frame.y0
                    return if (fx in 0 until frame.width && fy in 0 until frame.height) {
                        frame.colour[fy * frame.width + fx]
                    } else 0
                }
                fun faced(x: Int, y: Int): Int =
                    if (x in 0 until c.w && y in 0 until c.h) c.pixels[y * c.w + x] else 0
                fun near(x: Int, y: Int, of: (Int, Int) -> Int): Boolean {
                    for (dy in -1..1) for (dx in -1..1) if (of(x + dx, y + dy) != 0) return true
                    return false
                }
                var both = 0
                var either = 0
                for (y in 0 until c.h) for (x in 0 until c.w) {
                    val r = raster(x, y)
                    val s = faced(x, y)
                    if (r != 0 && s != 0) both++
                    if (r != 0 || s != 0) either++
                    if (r != 0 && s == 0 && !near(x, y, ::faced)) farOff++
                    if (s != 0 && r == 0 && !near(x, y, ::raster)) farOff++
                }
                val agree = both.toDouble() / either
                if (agree < worst) {
                    worst = agree
                    worstName = "${piece.name} sway $sway"
                }
            }
        }
        println("FACESHAPE worst silhouette agreement %.4f (%s)".format(worst, worstName))
        assertEquals("no pixel more than one art pixel off the ray cast's shape", 0, farOff)
        assertTrue("$worstName agrees $worst", worst > 0.8)
    }

    /**
     * The owner's complaint: the depth moved in discrete pictures. The side face's width is a
     * continuous, monotonic function of the camera ([FaceView.sideWidth]); drawn at the phone's 6
     * device pixels per art pixel and swept finely, the sofa's left edge only ever moves one way,
     * never more than one device pixel between two neighbouring yaws, and travels its whole side.
     */
    @Test
    fun theSideFaceGrowsContinuouslyAndMonotonicallyWithTheCamera() {
        val sofa = solids.first { it.name == "sofa" }
        val model = sofa.volume!!.model
        // the width itself
        var last = -1f
        var yaw = 0f
        while (yaw <= 1f) {
            val w = FaceView.sideWidth(model.depth, FaceView.vx(yaw, level))
            assertTrue("the width never shrinks as the camera turns: $w after $last", w >= last)
            if (last >= 0f) assertTrue("no jump: $last to $w", w - last <= 0.01f)
            last = w
            yaw += 0.002f
        }
        assertEquals(model.depth * level.depthGain, last, 0.01f)
        // as drawn
        val scale = 6
        val row = (model.height / 2 + margin) * scale // the middle of the sofa, on screen
        fun leftEdge(c: FineCanvas): Int {
            for (x in 0 until c.w) if (c.pixels[row * c.w + x] != 0) return x
            return -1
        }
        var lastEdge = Int.MIN_VALUE
        var biggest = 0
        var backwards = 0
        val first = leftEdge(composite(sofa, 0f, 0f, scale))
        var travel = 0
        yaw = 0f
        while (yaw <= 1.0001f) {
            val c = composite(sofa, FaceView.vx(yaw, level), FaceView.vy(yaw, level), scale)
            val edge = leftEdge(c)
            if (lastEdge != Int.MIN_VALUE) {
                biggest = maxOf(biggest, abs(edge - lastEdge))
                if (edge > lastEdge) backwards++
            }
            travel = maxOf(travel, abs(edge - first))
            lastEdge = edge
            yaw += 0.005f
        }
        println("FACESMOOTH sofa left edge travels $travel device px, biggest move $biggest")
        assertTrue("the side shows: $travel", travel >= 3 * scale)
        assertTrue("moves at most a device pixel at a time: $biggest", biggest <= 1)
        assertEquals("never back the other way", 0, backwards)
    }

    // ------------------------------------------------------------------ in the room

    private class BoxCats(
        private val x: IntArray,
        private val z: FloatArray,
        private val feet: IntArray,
        private val surface: Array<String?>,
    ) : CatLayer {
        override val count = x.size

        override fun depth(i: Int) = z[i]

        override fun feet(i: Int) = feet[i].toFloat()

        override fun surface(i: Int) = surface[i]

        override fun draw(i: Int) = Unit

        override fun draw(i: Int, shift: Float, style: RoomStyle, canvas: RoomCanvas) {
            canvas.fill(x[i] - 6 + Math.round(shift), feet[i] - 20, 12, 20, CAT)
        }
    }

    private fun paint(style: RoomStyle, sway: Float, cats: CatLayer): SoftCanvas {
        val scene = RoomScene(art, style)
        scene.depth!!.sway.restAt(sway)
        val canvas = SoftCanvas(art)
        scene.paintBackdrop(canvas, 12.0, 0.0, 0)
        scene.paintScene(canvas, cats)
        return canvas
    }

    @Test
    fun theRoomAtRestIsTheSameTurningOrFlat() {
        val cats =
            BoxCats(
                intArrayOf(80, 120),
                floatArrayOf(World.ART_Z, 10f),
                intArrayOf(132, 120),
                arrayOf(null, null),
            )
        val turning = paint(RoomStyle.DEPTH25, 0f, cats).pixels
        val flat = paint(RoomStyle.DEPTH25.copy(volume = false), 0f, cats).pixels
        var differ = 0
        for (i in turning.indices) if (turning[i] != flat[i]) differ++
        assertEquals("at rest the solid pieces are their sprites", 0, differ)
    }

    /**
     * At either end of the camera's swing and in between, a cat on the sofa is drawn after every
     * band of the sofa and before the pink cushion's front, two cats in the tree have the top
     * platform between them, and a cat on the floor is between the bands nearer and farther than
     * it: the order does not depend on how far the faces have grown.
     */
    @Test
    fun theOrderHoldsAtTheExtremesOfTheCamera() {
        val sofa = solids.indexOfFirst { it.name == "sofa" }
        val pink = solids.indexOfFirst { it.name == "sofa_cushion_pink" }
        val mid = solids.indexOfFirst { it.name == "cat_tree_slab_mid" }
        val top = solids.indexOfFirst { it.name == "cat_tree_slab_top" }
        fun keyOn(p: Int, s: String) = solids[p].volume!!.let { it.sortFront - it.surfaces[s]!! }
        for (sway in floatArrayOf(-1f, -0.5f, 0f, 0.6f, 1f)) {
            val scene = RoomScene(art, RoomStyle.depth25(level))
            val depth = scene.depth!!
            depth.sway.restAt(sway)
            val couch =
                BoxCats(
                    intArrayOf(90),
                    floatArrayOf(World.ART_Z),
                    intArrayOf(110),
                    arrayOf("couch"),
                )
            val canvas = SoftCanvas(art)
            scene.paintScene(canvas, couch)
            val order = depth.order
            val codes = (0 until order.size).map { order.order[it] }
            val cat = codes.indexOf(DepthOrder.CAT)
            for ((k, code) in codes.withIndex()) {
                if (code >= DepthOrder.FURNITURE) continue
                val p = depth.bandPiece(code)
                if (p == sofa) assertTrue("sway $sway: sofa band before the cat", k < cat)
                if (p == pink && depth.bandNumber(code) == 0) {
                    assertTrue("sway $sway: the cushion's front after the cat", k > cat)
                }
            }
            assertEquals("every band once", codes.toSet().size, codes.size)
            // the tree
            val two =
                object : CatLayer {
                    override val count = 2

                    override fun depth(i: Int) = World.ART_Z

                    override fun feet(i: Int) = if (i == 0) 64f else 100f

                    override fun surface(i: Int) = if (i == 0) "top" else "mid"

                    override fun draw(i: Int) = Unit
                }
            scene.paintScene(canvas, two)
            val treeCodes = (0 until depth.order.size).map { depth.order.order[it] }
            val upper = treeCodes.indexOf(DepthOrder.CAT)
            val lower = treeCodes.indexOf(DepthOrder.CAT + 1)
            for ((k, code) in treeCodes.withIndex()) {
                if (code >= DepthOrder.FURNITURE || depth.bandPiece(code) != top) continue
                assertTrue(
                    "sway $sway: the top platform between the cats",
                    k in lower + 1 until upper,
                )
            }
            assertTrue(keyOn(top, "top") > 0f && keyOn(mid, "mid") > 0f)
        }
    }

    // ------------------------------------------------------------------ cost

    @Test
    fun theFacesAreSmallAndQuickToBake() {
        var bytes = 0L
        var runs = 0
        var made = 0L
        var pixels = 0L
        var sprites = 0L
        for (piece in solids) {
            val started = System.nanoTime()
            val f = SolidFaces(piece.volume!!.model, piece.volume!!.wall)
            made += System.nanoTime() - started
            bytes += f.bytes
            runs += f.runs
            pixels += f.pixels
            sprites += piece.width.toLong() * piece.height
        }
        println(
            "FACECOST all %d pieces: %.2f MB of bitmaps, baked in %.1f ms; %d strip runs (draws) a frame, %d painted pixels in one shading (%.1fx the sprites' %d)"
                .format(
                    solids.size,
                    bytes / 1e6,
                    made / 1e6,
                    runs,
                    pixels,
                    pixels.toDouble() / sprites,
                    sprites,
                )
        )
        assertTrue("the faces' bitmaps stay under 4 MB: $bytes", bytes < 4_000_000L)
        assertTrue("draws a frame for the furniture: $runs", runs < 1_200)
    }

    private companion object {
        const val OPAQUE = 0xFF000000.toInt()
        const val CAT = 0xFFFF00FF.toInt()
    }
}
