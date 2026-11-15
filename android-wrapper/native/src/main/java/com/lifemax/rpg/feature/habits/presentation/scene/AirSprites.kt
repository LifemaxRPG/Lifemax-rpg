package com.lifemax.rpg.feature.habits.presentation.scene

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * A thing of the air, painted once: white ARGB layers whose alpha is the thing's own light, each
 * drawn with a tint of the hour (the way the clouds are). [w] and [h] are pixels; [ox] and [oy]
 * where the thing's own origin (the plane's fuselage centre, the blimp's envelope centre, the
 * contrail's near end) lies in the picture, in pixels; [ppd] how many pixels make a dp.
 */
internal class AirSprite(
    val w: Int,
    val h: Int,
    val ox: Float,
    val oy: Float,
    val ppd: Float,
    val layers: Array<IntArray>,
) {
    val bytes: Int
        get() = w * h * 4 * layers.size
}

/**
 * The sprite painter for the plane, its contrail and the blimp. Every picture is a signed-distance
 * field of a few simple parts (a fuselage with a radius profile, swept plates, capsules) sampled a
 * few times a pixel, lit from the upper left as the clouds are, and split into layers by what the
 * light does: the whole body (shade), what the sun reaches (lit), the sharp gleam of the sun on
 * metal or fabric (glint), the sky's light on the belly (bounce), and what glows of its own at
 * night (glow). Pure Kotlin, deterministic; nothing here runs per frame.
 */
internal object AirSprites {
    /** Pixels in a dp for the plane: it is small and far, and must hold at a 3x screen. */
    const val PLANE_PPD = 5f
    const val BLIMP_PPD = 4f
    const val TRAIL_PPD = 2.5f

    /** The layers of the plane and the blimp, in order. */
    const val SHADE = 0
    const val LIT = 1
    const val GLINT = 2
    const val BOUNCE = 3
    const val GLOW = 4
    const val LAYERS = 5

    /** The contrail's two layers: its whole soft body, and its bright core. */
    const val TRAIL_BODY = 0
    const val TRAIL_CORE = 1
    const val TRAIL_LAYERS = 2

    /** How long the contrail is, in dp, from the engines back to nothing. */
    const val TRAIL_DP = 216f
    const val TRAIL_HALF_DP = 15f

    /**
     * Seen from the street the plane's two engines hang under its near wing nearly in line, one a
     * little lower than the other: the contrail's two cores start this far either side of its
     * centre line, in dp.
     */
    const val ENGINE_Y = .7f

    /**
     * Where the contrail's origin lies from the plane's origin, in dp: behind and below the
     * engines.
     */
    const val TRAIL_X = -1.8f
    const val TRAIL_Y = 2.4f

    /**
     * The engines' and the wingtips' places, for the lights: near wingtip, far wingtip, belly,
     * tail.
     */
    const val TIP_NEAR_X = -6.9f
    const val TIP_NEAR_Y = 4.1f
    const val TIP_FAR_X = -5.6f
    const val TIP_FAR_Y = -2.0f
    const val BEACON_X = -.4f
    const val BEACON_Y = 1.15f
    const val TAIL_X = -11.0f
    const val TAIL_Y = -.45f

    /** Bytes the three sprites hold. */
    fun bytes(): Int = planeBytes() + blimpBytes() + trailBytes()

    fun planeBytes(): Int = PLANE_W * PLANE_H * 4 * LAYERS

    fun blimpBytes(): Int = BLIMP_W * BLIMP_H * 4 * LAYERS

    fun trailBytes(): Int = TRAIL_W * TRAIL_H * 4 * TRAIL_LAYERS

    const val PLANE_W = 130
    const val PLANE_H = 66
    const val BLIMP_W = 248
    const val BLIMP_H = 136
    const val TRAIL_W = 544
    const val TRAIL_H = 76

    // the light: up and to the left and towards the viewer; the half vector for the gleam
    private const val LX = -.42f
    private const val LY = -.66f
    private const val LZ = .62f
    private val HX: Float
    private val HY: Float
    private val HZ: Float

    init {
        val n = sqrt(LX * LX + LY * LY + (LZ + 1f) * (LZ + 1f))
        HX = LX / n
        HY = LY / n
        HZ = (LZ + 1f) / n
    }

    // ---- a tiny toolbox of fields

    /**
     * A sample of the picture: the coverage of a part and the light on it. Parts are laid down back
     * to front, each over what is there.
     */
    private class Sample {
        var cover = 0f
        var lit = 0f
        var glint = 0f
        var bounce = 0f
        var glow = 0f

        fun clear() {
            cover = 0f
            lit = 0f
            glint = 0f
            bounce = 0f
            glow = 0f
        }

        /**
         * Lays a part over what is here: [c] of it, lit [l], gleaming [g], bounced [b], glowing
         * [w].
         */
        fun put(c: Float, l: Float, g: Float, b: Float, w: Float = 0f) {
            if (c <= 0f) return
            lit = lit * (1f - c) + l * c
            glint = glint * (1f - c) + g * c
            bounce = bounce * (1f - c) + b * c
            glow = glow * (1f - c) + w * c
            cover += c * (1f - cover)
        }
    }

    private fun cov(sdf: Float, soft: Float = .35f): Float = Px.smooth(soft, -soft, sdf)

    private fun lambert(nx: Float, ny: Float, nz: Float): Float =
        max(0f, nx * LX + ny * LY + nz * LZ)

    private fun gleam(nx: Float, ny: Float, nz: Float, power: Float): Float =
        max(0f, nx * HX + ny * HY + nz * HZ).pow(power)

    /** Distance to a convex polygon ([xs], [ys]), wound either way, negative inside. */
    private fun polygon(xs: FloatArray, ys: FloatArray, x: Float, y: Float): Float {
        var d = Float.MAX_VALUE
        var inside = true
        val n = xs.size
        // which way round it goes: the sign of its area
        var area = 0f
        for (i in 0 until n) {
            val j = (i + 1) % n
            area += xs[i] * ys[j] - xs[j] * ys[i]
        }
        for (i in 0 until n) {
            val j = (i + 1) % n
            val ex = xs[j] - xs[i]
            val ey = ys[j] - ys[i]
            val px = x - xs[i]
            val py = y - ys[i]
            val t = ((px * ex + py * ey) / (ex * ex + ey * ey)).coerceIn(0f, 1f)
            val qx = px - ex * t
            val qy = py - ey * t
            d = min(d, sqrt(qx * qx + qy * qy))
            if ((ex * py - ey * px) * area < 0f) inside = false
        }
        return if (inside) -d else d
    }

    /** Distance to a capsule from ([ax],[ay]) to ([bx],[by]) of radius [r]; negative inside. */
    private fun capsule(
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float,
        r: Float,
        x: Float,
        y: Float,
    ): Float {
        val ex = bx - ax
        val ey = by - ay
        val t = (((x - ax) * ex + (y - ay) * ey) / (ex * ex + ey * ey)).coerceIn(0f, 1f)
        val qx = x - ax - ex * t
        val qy = y - ay - ey * t
        return sqrt(qx * qx + qy * qy) - r
    }

    /** Distance to a rounded box centred on ([cx],[cy]), [hw] by [hh] half-sizes, corners [r]. */
    private fun roundBox(
        cx: Float,
        cy: Float,
        hw: Float,
        hh: Float,
        r: Float,
        x: Float,
        y: Float,
    ): Float {
        val qx = abs(x - cx) - hw + r
        val qy = abs(y - cy) - hh + r
        return min(max(qx, qy), 0f) + sqrt(max(qx, 0f).pow(2f) + max(qy, 0f).pow(2f)) - r
    }

    private fun pack(w: Int, h: Int, field: Array<FloatArray>): Array<IntArray> =
        Array(field.size) { l ->
            val f = field[l]
            IntArray(w * h) { i ->
                val a = f[i]
                if (a <= .002f) 0 else Px.argbF(min(1f, a), 1f, 1f, 1f)
            }
        }

    /** 1 inside the picture, falling to 0 over the last few pixels, so nothing touches its edge. */
    private fun border(x: Int, y: Int, w: Int, h: Int): Float =
        Px.smooth(0f, 3f, min(min(x, w - 1 - x), min(y, h - 1 - y)).toFloat())

    // ---- the plane

    /** The fuselage's half height at [x] (dp, nose to the right), 0 where there is none. */
    private fun hullR(x: Float): Float {
        if (x > 11.2f || x < -11.1f) return 0f
        return when {
            x > 7.6f -> 1.02f * sqrt(max(0f, 1f - ((x - 7.6f) / 3.6f).pow(2f)))
            x > -4.2f -> 1.02f
            else -> {
                val t = (-4.2f - x) / 6.9f
                1.02f * (1f - .62f * t * t) * (1f - Px.smooth(.88f, 1f, t) * .5f)
            }
        }
    }

    /**
     * The fuselage's centre line: the nose droops a little, and the tail cone sweeps up (the belly
     * rises to the tail while the back stays nearly straight), as an airliner's does.
     */
    private fun hullC(x: Float): Float {
        if (x > 7.6f) return .16f * ((x - 7.6f) / 3.6f).pow(2f)
        if (x > -4.2f) return 0f
        val t = (-4.2f - x) / 6.9f
        return -.68f * t * t
    }

    // the near wing, swept back and coming towards the viewer (so it drops below the hull and is
    // short), the far wing's sliver above the back, the fin, the tailplanes
    private val WING_X = floatArrayOf(2.7f, -5.9f, -7.4f, -2.9f)
    private val WING_Y = floatArrayOf(.45f, 4.0f, 4.05f, .6f)
    private val FAR_WING_X = floatArrayOf(1.6f, -5.0f, -6.3f, -2.9f)
    private val FAR_WING_Y = floatArrayOf(-.9f, -2.05f, -2.05f, -.9f)
    private val FIN_X = floatArrayOf(-5.7f, -10.4f, -11.7f, -10.3f)
    private val FIN_Y = floatArrayOf(-.85f, -6.3f, -6.3f, -.7f)
    private val TAIL_NEAR_X = floatArrayOf(-7.4f, -9.7f, -10.4f, -11.0f)
    private val TAIL_NEAR_Y = floatArrayOf(.25f, -.1f, 1.85f, 1.85f)
    private val TAIL_FAR_X = floatArrayOf(-7.6f, -10.2f, -10.6f, -9.9f)
    private val TAIL_FAR_Y = floatArrayOf(-.85f, -.75f, -2.0f, -2.0f)

    /**
     * The airliner as the street sees it: in profile from a little below, nose to the right, 22 dp
     * long. A long thin fuselage with its cockpit glazing and a row of windows, lit from the upper
     * left along its back with a gleam, its belly taking the sky's light; the fin rising over the
     * tail with the tailplanes; the near wing swept back towards the viewer, foreshortened and
     * drooping under the hull with its two engines hanging below it; a sliver of the far wing over
     * the back. The windows are a layer of their own (dark by day, lit at night).
     */
    fun plane(): AirSprite {
        val w = PLANE_W
        val h = PLANE_H
        val ppd = PLANE_PPD
        val ox = 64f
        val oy = 37f
        val out = Array(LAYERS) { FloatArray(w * h) }
        val s = Sample()
        val sub = 3
        val acc = FloatArray(LAYERS)
        for (py in 0 until h) for (px in 0 until w) {
            acc.fill(0f)
            for (sy in 0 until sub) for (sx in 0 until sub) {
                val x = (px + (sx + .5f) / sub - ox) / ppd
                val y = (py + (sy + .5f) / sub - oy) / ppd
                s.clear()
                planeSample(x, y, s)
                acc[SHADE] += s.cover
                acc[LIT] += s.cover * s.lit
                acc[GLINT] += s.cover * s.glint
                acc[BOUNCE] += s.cover * s.bounce
                acc[GLOW] += s.cover * s.glow
            }
            val n = (sub * sub).toFloat()
            val edge = border(px, py, w, h)
            val i = py * w + px
            for (l in 0 until LAYERS) out[l][i] = acc[l] / n * edge
        }
        return AirSprite(w, h, ox, oy, ppd, pack(w, h, out))
    }

    /** A flat plate of the plane: [c] of it here, facing the viewer, lit [l], its edge gleaming. */
    private fun plate(s: Sample, d: Float, l: Float, edgeGlint: Float, bounce: Float) {
        val c = cov(d, .3f)
        if (c <= 0f) return
        val rim = Px.smooth(.4f, 0f, abs(d)) * edgeGlint
        s.put(c, l, rim, bounce, 0f)
    }

    private fun planeSample(x: Float, y: Float, s: Sample) {
        // behind everything: the far wing's sliver and the far tailplane (their undersides, dim)
        plate(s, polygon(FAR_WING_X, FAR_WING_Y, x, y), .3f, .2f, .3f)
        plate(s, polygon(TAIL_FAR_X, TAIL_FAR_Y, x, y), .32f, .2f, .25f)
        // the fin: a blade facing us, the light coming round its leading edge
        run {
            val d = polygon(FIN_X, FIN_Y, x, y)
            val c = cov(d, .3f)
            if (c > 0f) {
                val up = Px.smooth(-.8f, -6.3f, y)
                val l = .5f + .12f * up
                val rim = Px.smooth(.45f, 0f, abs(d)) * .45f
                s.put(c, l, rim, .08f, 0f)
            }
        }
        // the near wing: its underside towards us, in the hull's shade, lit by the sky below
        run {
            val d = polygon(WING_X, WING_Y, x, y)
            val c = cov(d, .3f)
            if (c > 0f) {
                val out = Px.smooth(.5f, 4f, y)
                val l = .34f - .08f * out
                val leading = x > 2.7f - (y - .45f) * 2.46f - .55f
                val rim = Px.smooth(.45f, 0f, abs(d)) * (if (leading) .5f else .15f)
                s.put(c, l, rim, .6f - .1f * out, 0f)
            }
        }
        // the near tailplane, the same
        plate(s, polygon(TAIL_NEAR_X, TAIL_NEAR_Y, x, y), .34f, .3f, .5f)
        // the fuselage: a cylinder side on, its back lit, a gleam below the back, the belly in
        // shade with the sky's light under it
        run {
            val r = hullR(x)
            if (r > 0f) {
                val cy = hullC(x)
                val d = abs(y - cy) - r
                val c = cov(d, .3f)
                if (c > 0f) {
                    val t = ((y - cy) / r).coerceIn(-1f, 1f)
                    val nz = sqrt(max(0f, 1f - t * t))
                    val slope = (hullR(x + .1f) - hullR(x - .1f)) / .2f
                    val nx = (slope * .9f).coerceIn(-.95f, .95f)
                    val k = sqrt(max(0f, 1f - nx * nx))
                    val ny = t * k
                    val nzz = nz * k
                    val l = .22f + .78f * lambert(nx, ny, nzz)
                    val g = gleam(nx, ny, nzz, 36f) * 1.1f + gleam(nx, ny, nzz, 6f) * .18f
                    val b = .62f * Px.smooth(-.2f, .95f, t)
                    // the windows: a row along the upper half; the cockpit's glazing at the nose
                    var win = 0f
                    if (x > -5.4f && x < 6.6f) {
                        val k2 = (x + 5.4f) / .6f
                        val fx = k2 - kotlin.math.floor(k2) - .5f
                        val wy = (y - cy) / r + .28f
                        val dd = sqrt((fx * .6f).pow(2f) + (wy * r).pow(2f))
                        win = Px.smooth(.19f, .09f, dd)
                    }
                    if (x > 8.2f && x < 9.5f) {
                        // a slanted pane: higher at its front
                        val wy = (y - cy) / r + .42f + (x - 8.2f) * .25f
                        val wd = max(abs(wy * r) - .2f, max(8.2f - x, x - 9.5f))
                        win = max(win, Px.smooth(.12f, 0f, wd) * .95f)
                    }
                    s.put(c, l * (1f - .55f * win), g * (1f - win), b * (1f - win), win)
                }
            }
        }
        // the engines under the near wing: short fat cylinders, the outer one lower and further
        // back, each with a dark intake at its front and a bright lip
        for (e in 0 until 2) {
            val ex0 = if (e == 0) 2.9f else 1.1f
            val ey = if (e == 0) 1.7f else 3.1f
            val er = if (e == 0) .86f else .76f
            // the pylon up to the wing
            val pd = capsule(ex0 - 1.6f, ey - er, ex0 - 2.1f, ey - er - 1f, .28f, x, y)
            plate(s, pd, .3f, 0f, .35f)
            val d = capsule(ex0, ey, ex0 - 3.4f, ey, er, x, y)
            val c = cov(d, .3f)
            if (c > 0f) {
                val t = ((y - ey) / er).coerceIn(-1f, 1f)
                val nz = sqrt(max(0f, 1f - t * t))
                val nx = Px.smooth(ex0 - .6f, ex0 + .4f, x) * .9f
                val k = sqrt(max(0f, 1f - nx * nx))
                val l = .22f + .78f * lambert(nx, t * k, nz * k)
                val g = gleam(nx, t * k, nz * k, 28f) * .9f
                val lip = Px.smooth(ex0 - .55f, ex0 - .2f, x)
                val b = .45f * Px.smooth(-.2f, 1f, t) * (1f - lip)
                s.put(c, l * (1f - .72f * lip), g * (1f - lip) + lip * .3f, b, 0f)
            }
        }
    }

    // ---- the contrail

    /**
     * The contrail: two cores of exhaust right behind the engines that the wake pulls together into
     * one, widening and thinning with distance, broken into lumps towards its far end and gone by
     * [TRAIL_DP]. Its origin is the engines' x, the fuselage's centre line; it runs to the left
     * (behind a plane flying right). Two layers: the soft body and the bright core.
     */
    fun contrail(): AirSprite {
        val w = TRAIL_W
        val h = TRAIL_H
        val ppd = TRAIL_PPD
        val ox = w - 2f
        val oy = h / 2f
        val body = FloatArray(w * h)
        val core = FloatArray(w * h)
        val sub = 2
        for (py in 0 until h) for (px in 0 until w) {
            var bSum = 0f
            var cSum = 0f
            for (sy in 0 until sub) for (sx in 0 until sub) {
                // s: dp behind the engines; y: dp off the centre line
                val sdp = (ox - (px + (sx + .5f) / sub)) / ppd
                val y = (py + (sy + .5f) / sub - oy) / ppd
                if (sdp < 0f) continue
                // the trail wanders a little as the air moves it
                val wander =
                    (Noise.fbm(sdp * .022f, 3.1f, 401, 3) - .5f) * 7f * Px.smooth(0f, 90f, sdp)
                // further back the air has torn at it: its edge wavers
                val tear =
                    (Noise.fbm(sdp * .09f, y * .07f, 402, 3) - .5f) * 6f * Px.smooth(40f, 160f, sdp)
                val yy = y - wander - tear
                // the two cores converge (the wake vortices roll them together), and widen
                val pull = 1f - .8f * Px.smooth(8f, 90f, sdp)
                val sigma = .32f + .024f * sdp + .00005f * sdp * sdp
                var dens = 0f
                for (side in 0 until 2) {
                    val sign = if (side == 0) 1f else -1f
                    val yc = sign * ENGINE_Y * pull
                    val dy = (yy - yc) / sigma
                    dens += exp(-.5f * dy * dy)
                }
                // the exhaust condenses a little way behind the engine
                val onset = Px.smooth(.3f, 2.2f, sdp)
                // thins out with distance; and the far part breaks into lumps
                val fade = exp(-sdp / 85f) * Px.smooth(TRAIL_DP, TRAIL_DP - 90f, sdp)
                val lumpN = Noise.fbm(sdp * .1f, yy * .15f, 403, 4)
                val fineN = Noise.fbm(sdp * .3f, yy * .45f, 404, 3)
                val broken = Px.smooth(25f, 140f, sdp)
                val lumps = Px.mix(1f, Px.smooth(.4f, .7f, lumpN), broken)
                val fine = 1f + (fineN - .5f) * (.2f + .5f * broken)
                val amount = dens * onset * fade * lumps * fine
                // the body is soft and wide, the core is what is dense
                val b = Px.clamp01(amount).pow(.9f)
                bSum += b
                cSum += min(b, Px.clamp01((amount - .15f) * 1.6f).pow(1.5f) * exp(-sdp / 120f))
            }
            val n = (sub * sub).toFloat()
            val edge = border(px, py, w, h)
            val i = py * w + px
            body[i] = bSum / n * edge
            core[i] = cSum / n * edge
        }
        return AirSprite(w, h, ox, oy, ppd, pack(w, h, arrayOf(body, core)))
    }

    // ---- the blimp

    /** The envelope's half height at [x] (dp, nose to the right, 54.5 dp long). */
    private fun envelopeH(x: Float): Float {
        val u = (x + 27f) / 54.5f
        if (u <= 0f || u >= 1f) return 0f
        val base = kotlin.math.sin(Math.PI * u.pow(.74f).toDouble()).toFloat().pow(.8f)
        return 10f * base
    }

    private val FIN_TOP_X = floatArrayOf(-17f, -24.5f, -30f, -27.5f)
    private val FIN_TOP_Y = floatArrayOf(-8f, -4f, -14.2f, -16f)
    private val FIN_BOT_X = floatArrayOf(-17f, -27f, -29.5f, -24.5f)
    private val FIN_BOT_Y = floatArrayOf(8f, 15f, 13.4f, 4f)
    private val FIN_SIDE_X = floatArrayOf(-18f, -29f, -29f)
    private val FIN_SIDE_Y = floatArrayOf(0f, 3.4f, -3.4f)

    /** The lit panel on the envelope's side, in dp: its left, top, right and bottom. */
    const val BAND_L = -15.5f
    const val BAND_T = .4f
    const val BAND_R = 17.5f
    const val BAND_B = 4.2f

    /**
     * The airship, nose to the right: a smooth envelope shaded as the fabric cylinder it is (lit
     * from the upper left, a broad gleam along its top, the sky's light under its belly, faint gore
     * seams and ribs), tail fins (the upper one lit, the lower in the envelope's shade), a gondola
     * with a window strip. The glow layer is the advertising panel on its side and the gondola's
     * windows, for the night.
     */
    fun blimp(): AirSprite {
        val w = BLIMP_W
        val h = BLIMP_H
        val ppd = BLIMP_PPD
        val ox = 128f
        val oy = 68f
        val out = Array(LAYERS) { FloatArray(w * h) }
        val s = Sample()
        val sub = 3
        val acc = FloatArray(LAYERS)
        for (py in 0 until h) for (px in 0 until w) {
            acc.fill(0f)
            for (sy in 0 until sub) for (sx in 0 until sub) {
                val x = (px + (sx + .5f) / sub - ox) / ppd
                val y = (py + (sy + .5f) / sub - oy) / ppd
                s.clear()
                blimpSample(x, y, s)
                acc[SHADE] += s.cover
                acc[LIT] += s.cover * s.lit
                acc[GLINT] += s.cover * s.glint
                acc[BOUNCE] += s.cover * s.bounce
                acc[GLOW] += s.cover * s.glow
            }
            val n = (sub * sub).toFloat()
            val edge = border(px, py, w, h)
            val i = py * w + px
            for (l in 0 until LAYERS) out[l][i] = acc[l] / n * edge
        }
        return AirSprite(w, h, ox, oy, ppd, pack(w, h, out))
    }

    private fun blimpSample(x: Float, y: Float, s: Sample) {
        // the fins first, the envelope lies over their roots
        run {
            val dTop = polygon(FIN_TOP_X, FIN_TOP_Y, x, y)
            val c = cov(dTop, .3f)
            if (c > 0f) {
                val l = .5f + .35f * Px.smooth(-14f, -27f, x)
                val rim = Px.smooth(.4f, 0f, abs(dTop)) * .3f
                s.put(c, l, rim, .1f, 0f)
            }
            val dBot = polygon(FIN_BOT_X, FIN_BOT_Y, x, y)
            val cb = cov(dBot, .3f)
            if (cb > 0f) s.put(cb, .18f, 0f, .3f, 0f)
            val dSide = polygon(FIN_SIDE_X, FIN_SIDE_Y, x, y)
            val cs = cov(dSide, .3f)
            if (cs > 0f) {
                val l = .3f + .25f * Px.smooth(-18f, -29f, x)
                s.put(cs, l, Px.smooth(.35f, 0f, abs(dSide)) * .25f, .2f, 0f)
            }
        }
        // the gondola: two struts, a cabin with a rounded roof and a lit window strip
        run {
            val strutA = capsule(-3f, 8.6f, -3.5f, 10.4f, .3f, x, y)
            val strutB = capsule(5f, 8.8f, 5.5f, 10.4f, .3f, x, y)
            val cst = cov(min(strutA, strutB), .25f)
            if (cst > 0f) s.put(cst, .25f, 0f, .1f, 0f)
            // the cabin: a rounded box
            val cx = (x - 1f) / 7.8f
            val cy = (y - 12.4f) / 2.6f
            val d = roundBox(1f, 12.4f, 7.8f, 2.6f, 2.2f, x, y)
            val cc = cov(d, .3f)
            if (cc > 0f) {
                // lit along its roof, darker under; a window strip a little below the roof
                val l = .32f + .4f * Px.smooth(.9f, -.9f, cy) + .12f * Px.smooth(.5f, -1f, cx)
                val g = Px.smooth(-.55f, -.95f, cy) * .5f * Px.smooth(.9f, .3f, abs(cx))
                val winY = abs(y - 12.1f) - .8f
                val winX = abs(x - 1f) - 6.2f
                val strip = Px.smooth(.1f, -.1f, max(winY, winX))
                // the windows are panes with mullions between
                val k = (x - 1f + 6.2f) / 1.55f
                val fk = k - kotlin.math.floor(k)
                val pane = Px.smooth(.06f, .14f, min(fk, 1f - fk))
                val win = strip * pane
                s.put(cc, l * (1f - .6f * win), g, .15f, win)
            }
        }
        // the envelope
        run {
            val hh = envelopeH(x)
            if (hh > 0f) {
                val d = abs(y) - hh
                val c = cov(d, .3f)
                if (c > 0f) {
                    val t = (y / hh).coerceIn(-1f, 1f)
                    val nz = sqrt(max(0f, 1f - t * t))
                    val slope = (envelopeH(x + .15f) - envelopeH(x - .15f)) / .3f
                    val nx = (-slope * .7f).coerceIn(-.9f, .9f)
                    val k = sqrt(max(0f, 1f - nx * nx))
                    val ny = t * k
                    val nzz = nz * k
                    var l = .24f + .76f * lambert(nx, ny, nzz)
                    // the fabric's broad soft gleam, and a sharper one
                    var g = gleam(nx, ny, nzz, 5f) * .28f + gleam(nx, ny, nzz, 30f) * .5f
                    // the rim on the sun's side, where the hull curves away
                    g += Px.smooth(.3f, -.1f, d) * Px.smooth(-.5f, -.95f, ny) * .25f
                    // the sky's light under the belly
                    val b = .6f * Px.smooth(-.1f, .95f, t) * Px.smooth(-.9f, -.3f, nx)
                    // the ribs: faint shaded creases across the envelope every 7 dp
                    var seam = 0f
                    for (r in 0 until 7) {
                        val rx = -21f + r * 7f
                        seam = max(seam, Px.smooth(.38f, .08f, abs(x - rx)))
                    }
                    // the gores, running nose to tail, curving with the hull
                    for (gt in floatArrayOf(-.62f, -.25f, .14f, .52f, .86f)) {
                        seam = max(seam, Px.smooth(.05f, .012f, abs(t - gt)) * .55f)
                    }
                    val crease = seam * (.08f + .06f * nz)
                    l -= crease
                    g *= (1f - seam * .4f)
                    // the panel on its side: a rounded strip, softly edged, with blocks of
                    // lettering (a little less lit) along it
                    val bx = (x - (BAND_L + BAND_R) / 2f) / ((BAND_R - BAND_L) / 2f)
                    val bandY = y - (BAND_T + BAND_B) / 2f - .9f * (1f - bx * bx)
                    val by = bandY / ((BAND_B - BAND_T) / 2f)
                    val pd = roundBox(0f, 0f, 1f, 1f, .5f, bx, by)
                    val panel = Px.smooth(.06f, -.06f, pd)
                    var glow = panel
                    if (panel > 0f) {
                        // the lettering: a run of narrow blocks of two heights, a shade less lit
                        val lx = (x - BAND_L - 2.4f) / 1.35f
                        val fx = lx - kotlin.math.floor(lx)
                        val k = kotlin.math.floor(lx).toInt()
                        val tall =
                            if (Px.smooth(0f, 1f, Noise.value(k * .7f, .5f, 17)) > .5f) .72f
                            else .5f
                        val letter =
                            Px.smooth(.2f, .32f, fx) *
                                Px.smooth(.8f, .68f, fx) *
                                Px.smooth(tall + .15f, tall - .1f, abs(by + .05f)) *
                                Px.smooth(-.2f, .1f, lx) *
                                Px.smooth(20.4f, 20f, lx)
                        glow = panel * (1f - .3f * letter)
                    }
                    s.put(c, l, g, b, glow)
                }
            }
        }
    }
}
