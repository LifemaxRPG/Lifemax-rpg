package com.lifemax.rpg.feature.habits.presentation.scene

import com.lifemax.rpg.feature.habits.domain.FigBuilder
import com.lifemax.rpg.feature.habits.domain.Mat
import com.lifemax.rpg.feature.habits.domain.Shape
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The street's people in their own colours: each [Shape] filled anti-aliased in its [Shape.rgb] and
 * given form by the daylight from above (a soft pillow shading from its own outline: its top edges
 * catch the light, its under side and far edge fall away), a sheen by what it is made of ([Mat]),
 * and a soft shade where it lies over something else (an arm over the coat, a strap over the
 * shoulder). No glow round the figure: outdoors the scene's light does the rest
 * ([StreetLifePainter] grades them for the hour). Pure Kotlin, any JVM.
 */
internal object StreetRaster {
    private const val SS = PeopleRaster.SS
    private const val PAD = 2

    /** The light, from above and towards us, and the half vector for the sheen. */
    private const val LY = -.6f
    private const val LZ = .8f
    private val HY: Float
    private val HZ: Float

    init {
        val l = sqrt(LY * LY + (LZ + 1f) * (LZ + 1f))
        HY = LY / l
        HZ = (LZ + 1f) / l
    }

    /** By material: ambient, diffuse, sheen strength, sheen tightness. */
    private val AMB = floatArrayOf(.6f, .68f, .56f, .55f, .5f, .7f, 1f)
    private val DIF = floatArrayOf(.5f, .42f, .5f, .48f, .55f, .32f, 0f)
    private val KS = floatArrayOf(0f, .07f, .2f, .3f, .75f, .12f, 0f)
    private val SP = floatArrayOf(1f, 10f, 18f, 26f, 40f, 16f, 1f)

    /** A shape with no colour of its own: a warm grey by its tone. */
    private fun fallback(tone: Float): Int {
        val v = (40f + 150f * tone.coerceIn(0f, 1f)).toInt()
        return (v shl 16) or ((v * .94f).toInt() shl 8) or (v * .88f).toInt()
    }

    /** Bakes [shapes] (units) at [scale] pixels a unit, the origin at the units' 0, 0. */
    fun bake(shapes: List<Shape>, scale: Float): Sprite {
        val b = FigBuilder.bounds(shapes)
        val x0 = floor(b[0] * scale).toInt() - PAD
        val y0 = floor(b[1] * scale).toInt() - PAD
        val x1 = ceil(b[2] * scale).toInt() + PAD
        val y1 = ceil(b[3] * scale).toInt() + PAD
        val w = x1 - x0
        val h = y1 - y0
        val ox = -x0.toFloat()
        val oy = -y0.toFloat()
        val r = FloatArray(w * h)
        val g = FloatArray(w * h)
        val bl = FloatArray(w * h)
        val a = FloatArray(w * h)
        val base = IntArray(w * h) { -1 }
        // runs of shapes of one colour and stuff (a leg's thigh and shin, a coat's panels) are
        // shaded as one piece, so no seam shows where they join
        var from = 0
        while (from < shapes.size) {
            val s = shapes[from]
            var to = from + 1
            while (
                to < shapes.size &&
                    s.mat != Mat.FLAT &&
                    shapes[to].rgb == s.rgb &&
                    shapes[to].mat == s.mat &&
                    shapes[to].alpha == s.alpha
            ) to++
            shape(shapes.subList(from, to), scale, ox, oy, w, h, r, g, bl, a, base)
            from = to
        }
        // a little more light at the head than at the feet: the sky above, the pavement below
        var top = h
        var bottom = 0
        for (y in 0 until h) for (x in 0 until w) if (a[y * w + x] > .05f) {
            top = min(top, y)
            bottom = max(bottom, y)
        }
        val span = max(1, bottom - top).toFloat()
        val out = IntArray(w * h)
        for (y in 0 until h) {
            val up = 1f - ((y - top) / span).coerceIn(0f, 1f)
            val k = .93f + .1f * up
            for (x in 0 until w) {
                val i = y * w + x
                if (a[i] <= .004f) continue
                out[i] = PeopleRaster.argb(a[i], r[i] * k, g[i] * k, bl[i] * k)
            }
        }
        return PeopleSprites.crop(Sprite(w, h, scale, ox, oy, out))
    }

    private fun shape(
        group: List<Shape>,
        scale: Float,
        ox: Float,
        oy: Float,
        w: Int,
        h: Int,
        r: FloatArray,
        g: FloatArray,
        bl: FloatArray,
        a: FloatArray,
        base: IntArray,
    ) {
        // the shape's box in the sprite's pixels, with room for the blur
        var minX = 1e9f
        var minY = 1e9f
        var maxX = -1e9f
        var maxY = -1e9f
        for (q in group) {
            var i = 0
            while (i < q.pts.size) {
                minX = min(minX, q.pts[i] * scale + ox)
                maxX = max(maxX, q.pts[i] * scale + ox)
                minY = min(minY, q.pts[i + 1] * scale + oy)
                maxY = max(maxY, q.pts[i + 1] * scale + oy)
                i += 2
            }
        }
        val s = group[0]
        val bx0 = max(0, floor(minX).toInt() - 3)
        val by0 = max(0, floor(minY).toInt() - 3)
        val bx1 = min(w, ceil(maxX).toInt() + 3)
        val by1 = min(h, ceil(maxY).toInt() + 3)
        val bw = bx1 - bx0
        val bh = by1 - by0
        if (bw <= 0 || bh <= 0) return
        val plane = PeopleRaster.Planes(bw * SS, bh * SS)
        for (q in group) fill(plane, q.pts, ox - bx0, oy - by0, scale)
        val cov = FloatArray(bw * bh)
        val inv = 1f / (SS * SS)
        for (y in 0 until bh) for (x in 0 until bw) {
            var sa = 0f
            for (j in 0 until SS) for (k in 0 until SS) sa +=
                plane.alpha[(y * SS + j) * plane.w + x * SS + k]
            cov[y * bw + x] = sa * inv
        }
        val rgb = if (s.rgb >= 0) s.rgb else fallback(s.tone)
        val cr = (rgb shr 16 and 255).toFloat()
        val cg = (rgb shr 8 and 255).toFloat()
        val cb = (rgb and 255).toFloat()
        val mat = s.mat.coerceIn(0, AMB.size - 1)
        val flat = mat == Mat.FLAT
        val m = if (flat) cov else PeopleRaster.blur(PeopleRaster.blur(cov, bw, bh, 2), bw, bh, 1)
        for (y in 0 until bh) for (x in 0 until bw) {
            val li = y * bw + x
            val gi = (y + by0) * w + x + bx0
            val c = cov[li] * s.alpha
            // the soft shade this shape lays on what is under it, a pixel below its edge
            if (!flat && a[gi] > 0f && base[gi] != rgb) {
                val above = if (y > 0) m[li - bw] else 0f
                val shade = (above - cov[li]).coerceIn(0f, 1f) * .45f * s.alpha
                if (shade > 0f) {
                    r[gi] *= 1f - shade
                    g[gi] *= 1f - shade
                    bl[gi] *= 1f - shade
                }
            }
            if (c <= 0f) continue
            var fr = cr
            var fg = cg
            var fb = cb
            if (!flat) {
                val gx = (if (x + 1 < bw) m[li + 1] else 0f) - (if (x > 0) m[li - 1] else 0f)
                val gy = (if (y + 1 < bh) m[li + bw] else 0f) - (if (y > 0) m[li - bw] else 0f)
                // the outline's slope as the surface's: flat facing us inside, turning away at
                // the edges
                var nx = -gx * 2.6f
                var ny = -gy * 2.6f
                var nz = 1f
                val nl = sqrt(nx * nx + ny * ny + nz * nz)
                nx /= nl
                ny /= nl
                nz /= nl
                val diff = max(0f, ny * LY + nz * LZ)
                val lit = AMB[mat] + DIF[mat] * diff
                val spec =
                    if (KS[mat] > 0f) KS[mat] * max(0f, ny * HY + nz * HZ).pow(SP[mat]) * 255f
                    else 0f
                fr = cr * lit + spec
                fg = cg * lit + spec
                fb = cb * lit + spec * 1.04f
            }
            // over what is there
            val da = a[gi]
            val oa = c + da * (1f - c)
            r[gi] = (fr * c + r[gi] * da * (1f - c)) / oa
            g[gi] = (fg * c + g[gi] * da * (1f - c)) / oa
            bl[gi] = (fb * c + bl[gi] * da * (1f - c)) / oa
            a[gi] = oa
            if (c > .5f && !flat) base[gi] = rgb
        }
    }

    /**
     * Fills [pts] (units) into [p] at [SS] times the pixels, by the non-zero rule: a limb whose
     * round ends overlap its sides is solid, and a ring wound back on itself has its hole.
     */
    private fun fill(p: PeopleRaster.Planes, pts: FloatArray, ox: Float, oy: Float, scale: Float) {
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
        val dir = IntArray(n)
        for (y in y0..y1) {
            val yc = y + .5f
            var c = 0
            for (i in 0 until n) {
                val j = if (i + 1 == n) 0 else i + 1
                val ya = ys[i]
                val yb = ys[j]
                if ((ya <= yc && yb > yc) || (yb <= yc && ya > yc)) {
                    cut[c] = xs[i] + (yc - ya) / (yb - ya) * (xs[j] - xs[i])
                    dir[c] = if (yb > ya) 1 else -1
                    c++
                }
            }
            for (k in 1 until c) {
                val v = cut[k]
                val d = dir[k]
                var m = k - 1
                while (m >= 0 && cut[m] > v) {
                    cut[m + 1] = cut[m]
                    dir[m + 1] = dir[m]
                    m--
                }
                cut[m + 1] = v
                dir[m + 1] = d
            }
            var wind = 0
            for (k in 0 until c - 1) {
                wind += dir[k]
                if (wind == 0) continue
                val xa = max(0, ceil(cut[k] - .5f).toInt())
                val xb = min(p.w - 1, floor(cut[k + 1] - .5f).toInt())
                for (x in xa..xb) p.alpha[y * p.w + x] = 1f
            }
        }
    }
}
