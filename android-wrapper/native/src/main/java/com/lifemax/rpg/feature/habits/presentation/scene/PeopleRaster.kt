package com.lifemax.rpg.feature.habits.presentation.scene

import com.lifemax.rpg.feature.habits.domain.Glow
import com.lifemax.rpg.feature.habits.domain.Shape
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A pre-rendered picture: [pixels] ARGB (not premultiplied), [ppu] pixels to a unit, the origin at
 * ([ox], [oy]) pixels.
 */
internal class Sprite(
    val w: Int,
    val h: Int,
    val ppu: Float,
    val ox: Float,
    val oy: Float,
    val pixels: IntArray,
)

/**
 * The little software rasteriser the people and their rooms are painted with: polygons filled at
 * three times the resolution (so edges are properly anti-aliased), a tone per layer, and a lighting
 * pass that turns a flat silhouette into a warm back-lit one: a deep warm brown lit a little more
 * from above, a soft rim of room light on the edges facing the light, a glow that bleeds out past
 * the edge. Pure Kotlin, no Android: it runs on any JVM, so what it draws can be looked at as a
 * picture.
 */
internal object PeopleRaster {
    const val SS = 3

    /** The figure's colours, deep to lit, and the light that rims it. */
    private val DEEP = intArrayOf(0x1B, 0x10, 0x0B)
    private val MID = intArrayOf(0x4A, 0x2D, 0x1B)
    private val RIM = intArrayOf(0xFF, 0xB8, 0x6C)
    private val TOP = intArrayOf(0x8A, 0x4E, 0x25)
    private val HALO = intArrayOf(0xFF, 0xD6, 0x94)

    /** The planes shapes are painted into at [SS] times the sprite's size. */
    class Planes(val w: Int, val h: Int) {
        val alpha = FloatArray(w * h)
        val tone = FloatArray(w * h)

        fun clear() {
            alpha.fill(0f)
            tone.fill(0f)
        }
    }

    /** Fills [pts] (x, y pairs in pixels of the plane) with [tone], opaque, even-odd. */
    fun fill(p: Planes, pts: FloatArray, tone: Float, ox: Float, oy: Float, scale: Float) {
        val n = pts.size / 2
        if (n < 3) return
        val xs = FloatArray(n)
        val ys = FloatArray(n)
        var minY = 1e9f
        var maxY = -1e9f
        for (i in 0 until n) {
            xs[i] = (pts[i * 2] * scale + ox) * SS
            ys[i] = (pts[i * 2 + 1] * scale + oy) * SS
            minY = min(minY, ys[i])
            maxY = max(maxY, ys[i])
        }
        val y0 = max(0, ceil(minY - .5f).toInt())
        val y1 = min(p.h - 1, floor(maxY - .5f).toInt())
        val cut = FloatArray(n)
        for (y in y0..y1) {
            val yc = y + .5f
            var c = 0
            for (i in 0 until n) {
                val j = if (i + 1 == n) 0 else i + 1
                val ya = ys[i]
                val yb = ys[j]
                if ((ya <= yc && yb > yc) || (yb <= yc && ya > yc)) {
                    cut[c++] = xs[i] + (yc - ya) / (yb - ya) * (xs[j] - xs[i])
                }
            }
            // insertion sort: a handful of crossings
            for (a in 1 until c) {
                val v = cut[a]
                var b = a - 1
                while (b >= 0 && cut[b] > v) {
                    cut[b + 1] = cut[b]
                    b--
                }
                cut[b + 1] = v
            }
            var k = 0
            while (k + 1 < c) {
                val xa = max(0, ceil(cut[k] - .5f).toInt())
                val xb = min(p.w - 1, floor(cut[k + 1] - .5f).toInt())
                for (x in xa..xb) {
                    p.alpha[y * p.w + x] = 1f
                    p.tone[y * p.w + x] = tone
                }
                k += 2
            }
        }
    }

    /**
     * Turns the shapes of one figure into a lit sprite [w] by [h] pixels, [ppu] pixels to a unit,
     * the origin (the feet's middle) at ([ox], [oy]) pixels. [emit] are extra lights.
     */
    fun figure(
        shapes: List<Shape>,
        w: Int,
        h: Int,
        ppu: Float,
        ox: Float,
        oy: Float,
        emit: List<Glow>,
        mirrorRim: Boolean = false,
    ): Sprite {
        val big = Planes(w * SS, h * SS)
        for (s in shapes) fill(big, s.pts, s.tone, ox, oy, ppu)
        return light(big, w, h, ppu, ox, oy, emit, mirrorRim)
    }

    private fun light(
        big: Planes,
        w: Int,
        h: Int,
        ppu: Float,
        ox: Float,
        oy: Float,
        emit: List<Glow>,
        mirrorRim: Boolean,
    ): Sprite {
        // down to the sprite's own pixels: coverage and the tone of what covers
        val a = FloatArray(w * h)
        val t = FloatArray(w * h)
        val inv = 1f / (SS * SS)
        for (y in 0 until h) for (x in 0 until w) {
            var sa = 0f
            var st = 0f
            for (j in 0 until SS) for (i in 0 until SS) {
                val k = (y * SS + j) * big.w + x * SS + i
                sa += big.alpha[k]
                st += big.alpha[k] * big.tone[k]
            }
            a[y * w + x] = sa * inv
            t[y * w + x] = if (sa > 0f) st / sa else 0f
        }
        val near = blur(a, w, h, 1)
        val wide = blur(blur(a, w, h, 2), w, h, 2)
        var top = h
        var bottom = 0
        for (y in 0 until h) for (x in 0 until w) if (a[y * w + x] > .05f) {
            top = min(top, y)
            bottom = max(bottom, y)
        }
        val span = max(1, bottom - top).toFloat()
        val out = IntArray(w * h)
        val lx = if (mirrorRim) .35f else -.35f
        val ly = -.94f
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val cov = a[i]
            val halo = min(1f, wide[i] * 1.5f) * .42f
            if (cov <= 0.004f && halo <= .01f) continue
            // body colour from its tone, then lit a little more the nearer the top
            val tn = t[i].coerceIn(0f, 1f)
            val up = 1f - ((y - top) / span).coerceIn(0f, 1f)
            var r = DEEP[0] + (MID[0] - DEEP[0]) * tn
            var g = DEEP[1] + (MID[1] - DEEP[1]) * tn
            var bl = DEEP[2] + (MID[2] - DEEP[2]) * tn
            val topLit = up * up * .5f * (.4f + .6f * tn)
            r += (TOP[0] - r) * topLit
            g += (TOP[1] - g) * topLit
            bl += (TOP[2] - bl) * topLit
            // the rim: the edge pixels whose outward face looks towards the light
            val gx = near[clampX(x + 1, w) + y * w] - near[clampX(x - 1, w) + y * w]
            val gy = near[x + clampY(y + 1, h) * w] - near[x + clampY(y - 1, h) * w]
            val gl = sqrt(gx * gx + gy * gy)
            val edge = ((1f - near[i]) * 2.4f).coerceIn(0f, 1f)
            if (gl > .001f && cov > 0f) {
                val facing = max(0f, (-gx / gl) * lx + (-gy / gl) * ly)
                val rim = edge * (.22f + .78f * facing) * .75f
                r += (RIM[0] - r) * rim
                g += (RIM[1] - g) * rim
                bl += (RIM[2] - bl) * rim
            }
            // other lights: a screen's glow on the face and hands
            val px = (x + .5f - ox) / ppu
            val py = (y + .5f - oy) / ppu
            for (e in emit) {
                val dx = px - e.x
                val dy = py - e.y
                val d = sqrt(dx * dx + dy * dy) / e.radius
                if (d >= 1f) continue
                val k = (1f - d) * (1f - d) * e.strength
                val er = (e.color shr 16) and 255
                val eg = (e.color shr 8) and 255
                val eb = e.color and 255
                r += (er - r) * k
                g += (eg - g) * k
                bl += (eb - bl) * k
            }
            // the glow that bleeds out past the edge, under the body
            val total = cov + halo * (1f - cov)
            val hr = HALO[0]
            val hg = HALO[1]
            val hb = HALO[2]
            val wb = cov / total
            val wh = 1f - wb
            val fr = r * wb + hr * wh
            val fg = g * wb + hg * wh
            val fb = bl * wb + hb * wh
            out[i] = argb(total, fr, fg, fb)
        }
        return Sprite(w, h, ppu, ox, oy, out)
    }

    private fun clampX(x: Int, w: Int) = if (x < 0) 0 else if (x >= w) w - 1 else x

    private fun clampY(y: Int, h: Int) = if (y < 0) 0 else if (y >= h) h - 1 else y

    /** A box blur of [r] pixels, in two passes. */
    fun blur(src: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        val tmp = FloatArray(w * h)
        val out = FloatArray(w * h)
        val d = (2 * r + 1).toFloat()
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0f
            for (k in -r..r) if (x + k in 0 until w) s += src[y * w + x + k]
            tmp[y * w + x] = s / d
        }
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0f
            for (k in -r..r) if (y + k in 0 until h) s += tmp[(y + k) * w + x]
            out[y * w + x] = s / d
        }
        return out
    }

    fun argb(a: Float, r: Float, g: Float, b: Float): Int =
        (((a * 255f + .5f).toInt().coerceIn(0, 255)) shl 24) or
            ((r + .5f).toInt().coerceIn(0, 255) shl 16) or
            ((g + .5f).toInt().coerceIn(0, 255) shl 8) or
            (b + .5f).toInt().coerceIn(0, 255)

    fun bytes(w: Int, h: Int): Int = w * h * 4
}
