package com.lifemax.rpg.feature.pet.room

import com.lifemax.rpg.feature.pet.art.ArgbImage
import com.lifemax.rpg.feature.pet.art.PetArtResources
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The slight thickness of a piece: the face vector, the sweep, the silhouette it is cut from. */
class ThicknessTest {
    private val art = PetArtResources.loadRoom()
    private val layers = art.layers!!

    private fun image(rows: List<String>): ArgbImage {
        val h = rows.size
        val w = rows[0].length
        return ArgbImage(w, h, IntArray(w * h) { if (rows[it / w][it % w] == '#') OPAQUE else 0 })
    }

    @Test
    fun theFaceIsNeverThickerThanTheLimitWhateverTheSway() {
        val v = IntArray(2)
        var sway = -3f
        while (sway <= 3f) {
            Thickness.vector(sway, v)
            assertTrue(
                "sway $sway: $v",
                abs(v[0]) <= FaceTuning.MAX_PX && abs(v[1]) <= FaceTuning.MAX_PX,
            )
            sway += 0.05f
        }
        // at rest: a little to the right and up
        Thickness.vector(0f, v)
        assertEquals(FaceTuning.SIDE_REST_PX, v[0])
        assertEquals(-FaceTuning.TOP_PX, v[1])
    }

    @Test
    fun theFaceSlidesTheOtherWayWhenTheCameraSways() {
        val rest = IntArray(2)
        val right = IntArray(2)
        val left = IntArray(2)
        Thickness.vector(0f, rest)
        Thickness.vector(1f, right)
        Thickness.vector(-1f, left)
        assertTrue(right[0] < rest[0] && rest[0] < left[0])
        // the whole range of the face stays within the 6 to 10 art pixels the plan allows
        assertTrue(abs(left[0]) + abs(left[1]) <= 10)
        assertTrue(abs(right[0]) + abs(right[1]) <= 10)
    }

    @Test
    fun theSweepGoesInOnePixelStepsFromNearToFar() {
        val out = IntArray(2 * FaceTuning.MAX_PX)
        assertEquals(0, Thickness.offsets(0, 0, out))
        val n = Thickness.offsets(4, -2, out)
        assertEquals(4, n)
        // farthest first, ending one step from the piece, each step moving at most a pixel each way
        assertEquals(4, out[0])
        assertEquals(-2, out[1])
        for (i in 1 until n) {
            assertTrue(abs(out[2 * i] - out[2 * i - 2]) <= 1)
            assertTrue(abs(out[2 * i + 1] - out[2 * i - 1]) <= 1)
        }
        assertEquals(1, abs(out[2 * (n - 1)]))
    }

    @Test
    fun theFaceOfABoxIsAnLShapeOutsideTheBox() {
        val box = image(listOf("####", "####", "####"))
        val mask = Thickness.face(box, 2, -1)
        val w = 4 + 2
        val h = 3 + 1
        assertEquals(w * h, mask.size)
        // the box starts one row down in the grown frame (the face goes up): box cells are never
        // face
        for (y in 0 until 3) for (x in 0 until 4) assertFalse("($x,$y)", mask[(y + 1) * w + x])
        // the top face: the row above the box, from the two-pixel shift to the end of the swept
        // copy
        for (x in 2..5) assertTrue("top $x", mask[x])
        assertFalse(mask[0] || mask[1])
        // the right side: two columns beside the box on its first two rows, one on the last
        assertTrue(mask[1 * w + 4] && mask[1 * w + 5])
        assertTrue(mask[2 * w + 4] && mask[2 * w + 5])
        assertTrue(mask[3 * w + 4])
        assertFalse(mask[3 * w + 5])
    }

    @Test
    fun theFaceIsTheSweepOfTheSilhouetteMinusTheSilhouette() {
        // brute force on every thick piece, at several sways
        for (piece in layers.pieces.filter { it.thick }) {
            val sprite = sprite(piece)
            for (sway in floatArrayOf(-1f, 0f, 1f)) {
                val v = IntArray(2)
                Thickness.vector(sway, v)
                val mask = Thickness.face(sprite, v[0], v[1])
                val w = sprite.width + abs(v[0])
                val x0 = minOf(0, v[0])
                val y0 = minOf(0, v[1])
                val steps = IntArray(2 * FaceTuning.MAX_PX)
                val n = Thickness.offsets(v[0], v[1], steps)
                var faceCells = 0
                for (y in 0 until sprite.height + abs(v[1])) for (x in 0 until w) {
                    val sx = x + x0
                    val sy = y + y0
                    val inside =
                        sx in 0 until sprite.width &&
                            sy in 0 until sprite.height &&
                            sprite[sx, sy] ushr 24 != 0
                    var swept = false
                    for (i in 0 until n) {
                        val ox = sx - steps[2 * i]
                        val oy = sy - steps[2 * i + 1]
                        if (
                            ox in 0 until sprite.width &&
                                oy in 0 until sprite.height &&
                                sprite[ox, oy] ushr 24 != 0
                        )
                            swept = true
                    }
                    assertEquals(
                        "${piece.name} ($x,$y) at sway $sway",
                        swept && !inside,
                        mask[y * w + x],
                    )
                    if (mask[y * w + x]) faceCells++
                }
                assertTrue("${piece.name} has a face at sway $sway", faceCells > 0)
            }
        }
    }

    @Test
    fun theDrawnFaceIsTheMaskInTheFaceColour() {
        // the room draws the face as dark copies of the piece under the piece: the pixels it adds
        // around the piece are exactly the mask, and they are the shaded colours
        val piece = layers.piece("cat_tree_slab_mid")
        val sprite = sprite(piece)
        val v = IntArray(2)
        Thickness.vector(0f, v)
        val steps = IntArray(2 * FaceTuning.MAX_PX)
        val n = Thickness.offsets(v[0], v[1], steps)
        val margin = FaceTuning.MAX_PX
        val w = sprite.width + 2 * margin
        val h = sprite.height + 2 * margin
        val canvas = IntArray(w * h)
        fun put(sx: Int, sy: Int, src: IntArray, ox: Int, oy: Int) {
            for (y in 0 until sprite.height) for (x in 0 until sprite.width) {
                val p = src[y * sprite.width + x]
                if (p ushr 24 != 0) canvas[(margin + y + oy) * w + margin + x + ox] = p
            }
        }
        val dark = IntArray(sprite.pixels.size) { Thickness.shade(sprite.pixels[it]) }
        for (i in 0 until n) put(0, 0, dark, steps[2 * i], steps[2 * i + 1])
        put(0, 0, sprite.pixels, 0, 0)
        val mask = Thickness.face(sprite, v[0], v[1])
        val mw = sprite.width + abs(v[0])
        val x0 = minOf(0, v[0])
        val y0 = minOf(0, v[1])
        var painted = 0
        for (y in 0 until sprite.height + 2 * margin) for (x in 0 until w) {
            val sx = x - margin
            val sy = y - margin
            val inSprite =
                sx in 0 until sprite.width &&
                    sy in 0 until sprite.height &&
                    sprite[sx, sy] ushr 24 != 0
            if (inSprite) continue
            val mx = sx - x0
            val my = sy - y0
            val inMask =
                mx in 0 until mw && my in 0 until sprite.height + abs(v[1]) && mask[my * mw + mx]
            assertEquals("($sx,$sy)", inMask, canvas[y * w + x] ushr 24 != 0)
            if (inMask) painted++
        }
        assertTrue(painted > 0)
    }

    @Test
    fun theFaceColourIsTheOwnColourDarkerWithTheSameAlpha() {
        assertEquals(0, Thickness.shade(0))
        val c = Thickness.shade(0xFF8A6CC0.toInt())
        assertEquals(0xFF, c ushr 24)
        for (shift in intArrayOf(16, 8, 0)) {
            val before = (0x8A6CC0 shr shift) and 255
            val after = (c shr shift) and 255
            assertEquals(before * FaceTuning.SHADE_PERCENT / 100, after)
        }
    }

    private fun sprite(piece: LayerPiece): ArgbImage {
        val out = IntArray(piece.width * piece.height)
        for (y in 0 until piece.height) for (x in 0 until piece.width) {
            out[y * piece.width + x] = layers.atlas[piece.atlasX + x, piece.atlasY + y]
        }
        return ArgbImage(piece.width, piece.height, out)
    }

    private companion object {
        const val OPAQUE = 0xFF808080.toInt()
    }
}
