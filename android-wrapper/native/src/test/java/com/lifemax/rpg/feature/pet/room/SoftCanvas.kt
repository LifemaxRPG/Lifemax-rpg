package com.lifemax.rpg.feature.pet.room

import com.lifemax.rpg.feature.pet.art.ArgbImage

/**
 * A software [RoomCanvas]: the house as 576 x 144 straight-alpha pixels, drawn nearest-neighbour
 * with 8-bit blending the way a canvas does. The room's painter draws onto it in the test, and the
 * result is compared with what the web room painted.
 *
 * Quads are anti-aliased by sampling 16 x 16 points per pixel, which is as exact as an 8-bit alpha.
 */
internal class SoftCanvas(private val art: RoomArt, private val topExtra: Int = 0) : RoomCanvas {
    val width = RoomLayout.HOUSE_WIDTH
    val height = RoomLayout.HEIGHT + topExtra
    val pixels = IntArray(width * height)

    private var clipX0 = 0
    private var clipY0 = 0
    private var clipX1 = width
    private var clipY1 = height

    /** The picture drawn so far. */
    fun picture() = ArgbImage(width, height, pixels.copyOf())

    override fun clip(x: Int, y: Int, w: Int, h: Int) {
        clipX0 = maxOf(0, x)
        clipY0 = maxOf(0, y + topExtra)
        clipX1 = minOf(width, x + w)
        clipY1 = minOf(height, y + h + topExtra)
    }

    override fun unclip() {
        clipX0 = 0
        clipY0 = 0
        clipX1 = width
        clipY1 = height
    }

    private fun put(x: Int, y: Int, argb: Int) {
        val py = y + topExtra
        if (x < clipX0 || x >= clipX1 || py < clipY0 || py >= clipY1) return
        pixels[py * width + x] = over(argb, pixels[py * width + x])
    }

    override fun fill(x: Int, y: Int, w: Int, h: Int, argb: Int) {
        for (j in y until y + h) for (i in x until x + w) put(i, j, argb)
    }

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
    ) {
        if (dw <= 0 || dh <= 0) return
        val src = art.image(image)
        for (j in 0 until dh) {
            val v = sy + sample((2 * j + 1) * sh, 2 * dh)
            for (i in 0 until dw) {
                val u = sx + sample((2 * i + 1) * sw, 2 * dw)
                put(dx + i, dy + j, src[u, v])
            }
        }
    }

    /**
     * The source index for a destination pixel's centre, `centre / scale`, nearest-neighbour. A
     * centre that falls exactly between two source pixels goes to the lower one: the page's canvas
     * (Skia) computes it in float and lands just under the boundary.
     */
    private fun sample(numerator: Int, denominator: Int) = Math.floorDiv(numerator - 1, denominator)

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
    ) {
        val xs = floatArrayOf(x0, x1, x2, x3)
        val ys = floatArrayOf(y0, y1, y2, y3)
        val alpha = argb ushr 24
        for (py in ys.min().toInt() until Math.ceil(ys.max().toDouble()).toInt()) {
            for (px in xs.min().toInt() until Math.ceil(xs.max().toDouble()).toInt()) {
                var inside = 0
                for (j in 0 until SAMPLES) for (i in 0 until SAMPLES) {
                    if (contains(xs, ys, px + (i + 0.5f) / SAMPLES, py + (j + 0.5f) / SAMPLES)) {
                        inside++
                    }
                }
                if (inside == 0) continue
                val a = Math.round(alpha * inside / (SAMPLES * SAMPLES).toFloat())
                put(px, py, (a shl 24) or (argb and 0xFFFFFF))
            }
        }
    }

    private fun contains(xs: FloatArray, ys: FloatArray, x: Float, y: Float): Boolean {
        var sign = 0
        for (k in 0 until 4) {
            val n = (k + 1) % 4
            val cross = (xs[n] - xs[k]) * (y - ys[k]) - (ys[n] - ys[k]) * (x - xs[k])
            val s = if (cross > 0) 1 else if (cross < 0) -1 else 0
            if (s != 0) {
                if (sign == 0) sign = s else if (sign != s) return false
            }
        }
        return true
    }

    override fun multiply(rgb: Int, x: Int, y: Int, w: Int, h: Int) {
        val r = (rgb shr 16) and 255
        val g = (rgb shr 8) and 255
        val b = rgb and 255
        for (j in y until y + h) for (i in x until x + w) {
            val py = j + topExtra
            if (i !in 0 until width || py !in 0 until height) continue
            val d = pixels[py * width + i]
            pixels[py * width + i] =
                (d and 0xFF000000.toInt()) or
                    (mul((d shr 16) and 255, r) shl 16) or
                    (mul((d shr 8) and 255, g) shl 8) or
                    mul(d and 255, b)
        }
    }

    override fun overlay(mask: ArgbImage, version: Int, x: Int, y: Int) {
        for (j in 0 until mask.height) for (i in 0 until mask.width) put(x + i, y + j, mask[i, j])
    }

    override fun sheet(
        image: ArgbImage,
        slot: Int,
        version: Int,
        sx: Int,
        sy: Int,
        sw: Int,
        sh: Int,
        dx: Int,
        dy: Int,
        alpha: Float,
    ) {
        if (alpha >= 1f) {
            for (j in 0 until sh) for (i in 0 until sw) put(dx + i, dy + j, image[sx + i, sy + j])
            return
        }
        for (j in 0 until sh) for (i in 0 until sw) {
            val p = image[sx + i, sy + j]
            val a = Math.round((p ushr 24) * alpha)
            if (a > 0) put(dx + i, dy + j, (a shl 24) or (p and 0xFFFFFF))
        }
    }

    /** The product of two 8-bit values as the page's canvas rounds it: `(a * b + 255) / 256`. */
    private fun mul(a: Int, b: Int) = (a * b + 255) shr 8

    /**
     * Source over an opaque destination, straight alpha [s]: the source colour is made
     * premultiplied (rounded), the destination is scaled by what the source lets through with the
     * same `(v + 255) / 256` the multiply uses, and the two are added.
     */
    private fun over(s: Int, d: Int): Int {
        val a = s ushr 24
        if (a == 255) return s
        if (a == 0) return d
        fun ch(shift: Int): Int {
            val sc = (s shr shift) and 255
            val dc = (d shr shift) and 255
            return minOf(255, (sc * a + 127) / 255 + mul(dc, 255 - a))
        }
        return (d and 0xFF000000.toInt()) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private companion object {
        const val SAMPLES = 16
    }
}
