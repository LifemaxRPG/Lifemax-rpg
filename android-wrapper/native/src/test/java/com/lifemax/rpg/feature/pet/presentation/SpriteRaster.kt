package com.lifemax.rpg.feature.pet.presentation

import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** A picture on the device pixel grid: [w] x [h] pixels with its top left at ([x0], [y0]). */
internal class Raster(val x0: Int, val y0: Int, val w: Int, val h: Int, val px: IntArray) {
    /** The pixel at device ([x], [y]); clear outside. */
    operator fun get(x: Int, y: Int): Int {
        val i = x - x0
        val j = y - y0
        return if (i in 0 until w && j in 0 until h) px[j * w + i] else 0
    }

    companion object {
        val EMPTY = Raster(0, 0, 0, 0, IntArray(0))
    }
}

/**
 * One horizontal band of a picture drawn by one `drawImage`: picture rows [srcFrom] until [srcTo]
 * into the local rows [dstTop] until [dstTop] + [dstHeight] (device pixels before the transform).
 */
internal class Band(val srcFrom: Int, val srcTo: Int, val dstTop: Int, val dstHeight: Int)

/**
 * What one cat's drawing asks of the canvas: a picture of [srcW] x [srcH] pixels whose left edge
 * lies [left] device pixels from the paws, drawn band by band ([bands], in order), under
 * `translate(footX, footY)`, `rotate(turnDeg)`, `scale(scaleX, scaleY)` (scale x carries the
 * mirroring), as [drawSpriteAt] does.
 */
internal class SpriteDraw(
    val src: IntArray,
    val srcW: Int,
    val srcH: Int,
    val left: Int,
    val bands: List<Band>,
    val s: Int,
    val footX: Int,
    val footY: Int,
    val turnDeg: Double,
    val scaleX: Double,
    val scaleY: Double,
) {
    /** True if [other] draws the very same pixels, only moved by whole device pixels. */
    fun sameUpToTranslation(other: SpriteDraw?): Boolean =
        other != null &&
            srcW == other.srcW &&
            left == other.left &&
            s == other.s &&
            turnDeg == other.turnDeg &&
            scaleX == other.scaleX &&
            scaleY == other.scaleY &&
            bands.size == other.bands.size &&
            bands.indices.all {
                val a = bands[it]
                val b = other.bands[it]
                a.srcFrom == b.srcFrom &&
                    a.srcTo == b.srcTo &&
                    a.dstTop == b.dstTop &&
                    a.dstHeight == b.dstHeight
            } &&
            src.contentEquals(other.src)
}

/**
 * A software canvas for [SpriteDraw]: every device pixel centre is taken back through the transform
 * to the picture and sampled nearest neighbour, the way a canvas draws a bitmap with filtering off
 * (`FilterQuality.None`); later bands are drawn over earlier ones (source-over of opaque art).
 */
internal object SpriteRaster {
    fun draw(d: SpriteDraw): Raster {
        // only the opaque box of the picture is visited
        var c0 = Int.MAX_VALUE
        var c1 = -1
        var r0 = Int.MAX_VALUE
        var r1 = -1
        for (j in 0 until d.srcH) for (i in 0 until d.srcW) {
            if (d.src[j * d.srcW + i] ushr 24 == 0) continue
            c0 = min(c0, i)
            c1 = max(c1, i)
            r0 = min(r0, j)
            r1 = max(r1, j)
        }
        if (c1 < 0 || d.bands.isEmpty()) return Raster.EMPTY
        var top = Int.MAX_VALUE
        var bottom = Int.MIN_VALUE
        for (b in d.bands) {
            val from = max(b.srcFrom, r0)
            val to = min(b.srcTo, r1 + 1)
            if (from >= to) continue
            val per = b.dstHeight.toDouble() / (b.srcTo - b.srcFrom)
            top = min(top, b.dstTop + floor((from - b.srcFrom) * per).toInt())
            bottom = max(bottom, b.dstTop + ceil((to - b.srcFrom) * per).toInt())
        }
        if (top >= bottom) return Raster.EMPTY
        val rad = Math.toRadians(d.turnDeg)
        val c = cos(rad)
        val sn = sin(rad)
        var bx0 = Double.MAX_VALUE
        var by0 = Double.MAX_VALUE
        var bx1 = -Double.MAX_VALUE
        var by1 = -Double.MAX_VALUE
        for (corner in 0 until CORNERS) {
            val lx = (d.left + (if (corner and 1 == 0) c0 else c1 + 1) * d.s) * d.scaleX
            val ly = (if (corner and 2 == 0) top else bottom) * d.scaleY
            val x = d.footX + c * lx - sn * ly
            val y = d.footY + sn * lx + c * ly
            bx0 = min(bx0, x)
            bx1 = max(bx1, x)
            by0 = min(by0, y)
            by1 = max(by1, y)
        }
        val x0 = floor(bx0).toInt() - 1
        val y0 = floor(by0).toInt() - 1
        val w = ceil(bx1).toInt() + 1 - x0
        val h = ceil(by1).toInt() + 1 - y0
        val out = IntArray(w * h)
        for (j in 0 until h) {
            val qy = y0 + j + HALF - d.footY
            for (i in 0 until w) {
                val qx = x0 + i + HALF - d.footX
                val lx = (c * qx + sn * qy) / d.scaleX
                val ly = (-sn * qx + c * qy) / d.scaleY
                val col = floor((lx - d.left) / d.s).toInt()
                if (col !in 0 until d.srcW) continue
                for (b in d.bands) {
                    val v = ly - b.dstTop
                    if (v < 0 || v >= b.dstHeight) continue
                    val row = b.srcFrom + floor(v * (b.srcTo - b.srcFrom) / b.dstHeight).toInt()
                    if (row !in 0 until d.srcH) continue
                    val p = d.src[row * d.srcW + col]
                    if (p ushr 24 != 0) out[j * w + i] = p
                }
            }
        }
        return Raster(x0, y0, w, h, out)
    }

    private const val HALF = 0.5
    private const val CORNERS = 4
}
