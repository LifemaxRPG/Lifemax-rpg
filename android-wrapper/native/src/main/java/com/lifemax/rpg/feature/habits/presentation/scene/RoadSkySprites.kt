package com.lifemax.rpg.feature.habits.presentation.scene

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/*
 * The small painted things of the far road and the night sky: the vehicles that pass on the horizon, the light
 * they throw (a soft glow, a headlight beam) and the meteor's streak. Like the clouds, each is baked once by pure
 * Kotlin from its spec, as ARGB pixels, and drawn as a tinted image under a transform; nothing here runs per
 * frame. The vehicles are painted at four pixels a dp (they are a few dp long on screen, so they must hold up at
 * a 3x density), the lights and the streak at two.
 */

/** The body shapes of the far road's vehicles. */
internal enum class VehicleKind {
    HATCH,
    SEDAN,
    SUV,
    VAN,
}

/**
 * One vehicle, nose to the right: its picture by [day] (its own colour, lit from above) and by
 * [night] (a dark body, its lenses lit), the same shape, and where its lights are in the picture's
 * pixels: the headlight at ([frontX], [lightY]), the tail-light at ([backX], [lightY]), its wheels
 * on the road at [roadY]. Draw the sprite mirrored for a vehicle going left.
 */
internal class VehicleSprite(
    val w: Int,
    val h: Int,
    val day: IntArray,
    val night: IntArray,
    val frontX: Float,
    val backX: Float,
    val lightY: Float,
    val roadY: Float,
) {
    val dpW: Float
        get() = w / RoadSkySprites.CAR_PX_PER_DP

    val dpH: Float
        get() = h / RoadSkySprites.CAR_PX_PER_DP
}

/** What one vehicle of the pool is: its shape, its pixel size and its paint (RGB). */
internal class VehicleSpec(val kind: VehicleKind, val w: Int, val h: Int, val paint: Int)

/**
 * The meteor's streak, head to the right: its hot [core] and its wider, cooler [halo], both white.
 */
internal class StreakSprite(
    val w: Int,
    val h: Int,
    /** Where the head is, in pixels from the left. */
    val headX: Float,
    val core: IntArray,
    val halo: IntArray,
)

internal object RoadSkySprites {
    /** Pixels in a dp of a vehicle sprite. */
    const val CAR_PX_PER_DP = 4f

    /** Pixels in a dp of the light sprites and the streak. */
    const val PX_PER_DP = 2f

    /** The vehicles of the far road: three cars of different shapes and colours, and a van. */
    val VEHICLES: List<VehicleSpec> =
        listOf(
            VehicleSpec(VehicleKind.HATCH, 44, 20, 0xD23B30),
            VehicleSpec(VehicleKind.SEDAN, 48, 20, 0xDCE1E8),
            VehicleSpec(VehicleKind.SUV, 44, 22, 0x2E63A6),
            VehicleSpec(VehicleKind.VAN, 56, 26, 0xE8DEC4),
        )

    /** The soft round glow: a light's bloom, a pool on the road, a sparkle. Square. */
    const val GLOW = 64

    /** The headlight's beam: from its origin on the left, opening to the right. */
    const val BEAM_W = 128
    const val BEAM_H = 40

    /** The meteor's streak. */
    const val STREAK_W = 320
    const val STREAK_H = 56

    /** Bytes the whole pool holds. */
    fun bytes(): Int =
        VEHICLES.sumOf { it.w * it.h * 4 * 2 } +
            GLOW * GLOW * 4 +
            BEAM_W * BEAM_H * 4 +
            STREAK_W * STREAK_H * 4 * 2

    // ---- a small painter: float RGBA, composited over, anti-aliased shapes from signed distances

    private class Canvas(val w: Int, val h: Int) {
        val px = FloatArray(w * h * 4)

        fun over(i: Int, r: Float, g: Float, b: Float, a: Float) {
            if (a <= 0f) return
            val k = i * 4
            val da = px[k + 3]
            val oa = a + da * (1f - a)
            if (oa <= 0f) return
            px[k] = (r * a + px[k] * da * (1f - a)) / oa
            px[k + 1] = (g * a + px[k + 1] * da * (1f - a)) / oa
            px[k + 2] = (b * a + px[k + 2] * da * (1f - a)) / oa
            px[k + 3] = oa
        }

        /** Paints [rgb] wherever [sdf] (in pixels, negative inside) says so, [alpha] strong. */
        fun fill(
            rgb: Int,
            alpha: Float = 1f,
            shade: ((Float, Float) -> Float)? = null,
            sdf: (Float, Float) -> Float,
        ) {
            val r = (rgb shr 16 and 255) / 255f
            val g = (rgb shr 8 and 255) / 255f
            val b = (rgb and 255) / 255f
            for (y in 0 until h) for (x in 0 until w) {
                val cx = x + .5f
                val cy = y + .5f
                val cov = Px.clamp01(.5f - sdf(cx, cy))
                if (cov <= 0f) continue
                val s = shade?.invoke(cx, cy) ?: 1f
                over(
                    y * w + x,
                    Px.clamp01(r * s),
                    Px.clamp01(g * s),
                    Px.clamp01(b * s),
                    cov * alpha,
                )
            }
        }

        /** A soft (unfeathered) alpha field of one colour: [field] is the alpha at a pixel. */
        fun soft(rgb: Int, field: (Float, Float) -> Float) {
            val r = (rgb shr 16 and 255) / 255f
            val g = (rgb shr 8 and 255) / 255f
            val b = (rgb and 255) / 255f
            for (y in 0 until h) for (x in 0 until w) {
                val a = Px.clamp01(field(x + .5f, y + .5f))
                if (a > 0f) over(y * w + x, r, g, b, a)
            }
        }

        fun argb(): IntArray =
            IntArray(w * h) {
                val k = it * 4
                val a = px[k + 3]
                if (a <= 0f) 0 else Px.argbF(a, px[k], px[k + 1], px[k + 2])
            }
    }

    private fun roundRect(
        x: Float,
        y: Float,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        r: Float,
    ): Float {
        val cx = (x0 + x1) / 2f
        val cy = (y0 + y1) / 2f
        val qx = abs(x - cx) - ((x1 - x0) / 2f - r)
        val qy = abs(y - cy) - ((y1 - y0) / 2f - r)
        val ox = max(qx, 0f)
        val oy = max(qy, 0f)
        return sqrt(ox * ox + oy * oy) + min(max(qx, qy), 0f) - r
    }

    private fun circle(x: Float, y: Float, cx: Float, cy: Float, r: Float): Float {
        val dx = x - cx
        val dy = y - cy
        return sqrt(dx * dx + dy * dy) - r
    }

    /**
     * A convex polygon ([xs], [ys] clockwise on a y-down canvas): the greatest of the distances to
     * its edges' lines, exact along the edges and near enough at the corners for anti-aliasing.
     */
    private fun poly(x: Float, y: Float, xs: FloatArray, ys: FloatArray): Float {
        var d = -1e9f
        val n = xs.size
        for (i in 0 until n) {
            val j = (i + 1) % n
            val ex = xs[j] - xs[i]
            val ey = ys[j] - ys[i]
            val len = sqrt(ex * ex + ey * ey)
            // the outward normal of a clockwise edge on a y-down canvas
            val nx = ey / len
            val ny = -ex / len
            d = max(d, (x - xs[i]) * nx + (y - ys[i]) * ny)
        }
        return d
    }

    /**
     * A trapezoid standing on [yb] with its top at [yt]: bottom from [bx0] to [bx1], top from [tx0]
     * to [tx1].
     */
    private fun trapezoid(
        x: Float,
        y: Float,
        bx0: Float,
        bx1: Float,
        yb: Float,
        tx0: Float,
        tx1: Float,
        yt: Float,
    ): Float = poly(x, y, floatArrayOf(tx0, tx1, bx1, bx0), floatArrayOf(yt, yt, yb, yb))

    private fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(s: Int) = Px.mix((a shr s and 255).toFloat(), (b shr s and 255).toFloat(), t).toInt()
        return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    // ---- the vehicles

    /**
     * The shape of a vehicle, in dp from its left edge, the road at y = 0 and up positive: the body
     * (a rounded box from [x0] to [x1], [bodyBottom] to [bodyTop]), the cabin (a trapezoid standing
     * on the body, from [cab0] to [cab1] at its foot and [roof0] to [roof1] at [roofY]), the wheels
     * (centres [wheel0] and [wheel1], radius [wheelR]) and whether the cabin's back is a panel (a
     * van) or glass.
     */
    private class Shape(
        val x0: Float,
        val x1: Float,
        val bodyBottom: Float,
        val bodyTop: Float,
        val cab0: Float,
        val cab1: Float,
        val roof0: Float,
        val roof1: Float,
        val roofY: Float,
        val wheel0: Float,
        val wheel1: Float,
        val wheelR: Float,
        val panelBack: Boolean,
    )

    private fun shape(kind: VehicleKind): Shape =
        when (kind) {
            VehicleKind.HATCH ->
                Shape(
                    .7f,
                    9.6f,
                    .55f,
                    1.95f,
                    2.6f,
                    7.9f,
                    3.2f,
                    6.5f,
                    3.35f,
                    2.5f,
                    7.8f,
                    .58f,
                    false,
                )
            VehicleKind.SEDAN ->
                Shape(.7f, 10.6f, .55f, 1.8f, 3.1f, 8.6f, 4.3f, 7.1f, 3.1f, 2.6f, 8.6f, .58f, false)
            VehicleKind.SUV ->
                Shape(.7f, 9.8f, .7f, 2.3f, 2.3f, 8.6f, 2.8f, 7.4f, 3.75f, 2.5f, 8.0f, .66f, false)
            VehicleKind.VAN ->
                Shape(
                    .7f,
                    12.6f,
                    .6f,
                    2.3f,
                    1.2f,
                    12.1f,
                    1.5f,
                    11.0f,
                    4.5f,
                    2.8f,
                    10.4f,
                    .62f,
                    true,
                )
        }

    fun generate(index: Int): VehicleSprite {
        val spec = VEHICLES[index]
        val s = shape(spec.kind)
        val day = paint(spec, s, false)
        val night = paint(spec, s, true)
        val k = CAR_PX_PER_DP
        val roadY = spec.h - 4f
        val lightY = roadY - (s.bodyBottom + (s.bodyTop - s.bodyBottom) * .55f) * k
        return VehicleSprite(
            spec.w,
            spec.h,
            day.argb(),
            night.argb(),
            frontX = s.x1 * k - 1.5f,
            backX = s.x0 * k + 1.5f,
            lightY = lightY,
            roadY = roadY,
        )
    }

    private fun paint(spec: VehicleSpec, s: Shape, night: Boolean): Canvas {
        val w = spec.w
        val h = spec.h
        val c = Canvas(w, h)
        val k = CAR_PX_PER_DP
        val roadY = h - 4f
        // from dp above the road to pixels down the picture
        fun py(up: Float) = roadY - up * k
        fun pxl(dp: Float) = dp * k
        val body = if (night) 0x121830 else spec.paint
        val glass = if (night) 0x0A0E1C else mix(0x2A3A58, spec.paint, .12f)
        val tyre = if (night) 0x07090F else 0x1A1D24
        val hub = if (night) 0x262B3A else 0x7A808C
        val light = if (night) 0.38f else 1f

        // the contact shadow: a soft dark ellipse under the car, deeper under its middle
        val mid = pxl((s.x0 + s.x1) / 2f)
        val half = pxl((s.x1 - s.x0) / 2f) - 1f
        c.soft(0x060812) { x, y ->
            val ex = (x - mid) / half
            val ey = (y - roadY) / pxl(.55f)
            val r = sqrt(ex * ex + ey * ey)
            .5f * Px.smooth(1f, .25f, r) * Px.smooth(roadY + pxl(.7f), roadY - pxl(.3f), y)
        }
        // the body: a rounded box, lighter along its top where the sky lights it, darker at its
        // sill
        val bTop = py(s.bodyTop)
        val bBottom = py(s.bodyBottom)
        c.fill(
            body,
            shade = { _, y ->
                val t = Px.clamp01((y - bTop) / (bBottom - bTop))
                (if (night) 1.25f - .5f * t else 1.22f - .42f * t) * light.coerceAtLeast(.85f)
            },
        ) { x, y ->
            roundRect(x, y, pxl(s.x0), bTop, pxl(s.x1), bBottom, pxl(.42f))
        }
        // the cabin, a little set in from the body's sides, with its roof lit
        c.fill(
            body,
            shade = { _, y ->
                val t = Px.clamp01((y - py(s.roofY)) / (bTop - py(s.roofY)))
                (if (night) 1.35f - .45f * t else 1.3f - .32f * t)
            },
        ) { x, y ->
            trapezoid(
                x,
                y,
                pxl(s.cab0),
                pxl(s.cab1),
                bTop + 1f,
                pxl(s.roof0),
                pxl(s.roof1),
                py(s.roofY),
            )
        }
        // the glass: the cabin's inside, less its pillars; split by the B pillar; a van's back is
        // panel
        val pillar = pxl(.22f)
        val gBottom = bTop - pxl(.12f)
        val gTop = py(s.roofY) + pxl(.26f)
        val span = s.cab1 - s.cab0
        val bPillar = pxl(s.cab0 + span * (if (s.panelBack) .62f else .5f))
        val glassFrom = if (s.panelBack) bPillar + pillar * .5f else pxl(s.cab0) + pillar
        c.fill(
            glass,
            shade = { x, y ->
                // the sky in the glass: brighter towards the top left, a diagonal glint across it
                val u = (x - glassFrom) / (pxl(s.cab1) - glassFrom)
                val v = Px.clamp01((y - gTop) / (gBottom - gTop))
                val glint = Px.smooth(.18f, 0f, abs(u * .7f + v * .5f - .42f))
                (if (night) .9f + .5f * (1f - v) + glint * .7f
                else 1f + .55f * (1f - v) - .25f * u + glint * .9f)
            },
        ) { x, y ->
            val cab =
                trapezoid(
                    x,
                    y,
                    glassFrom,
                    pxl(s.cab1) - pillar,
                    gBottom,
                    max(glassFrom, pxl(s.roof0) + pillar),
                    pxl(s.roof1) - pillar,
                    gTop,
                )
            // the B pillar is a slot of body between the windows
            max(cab, -(abs(x - bPillar) - pillar * .55f))
        }
        // the crease of light along the body's top edge
        c.soft(0xFFFFFF) { x, y ->
            val inside =
                roundRect(x, y, pxl(s.x0) + 1f, bTop, pxl(s.x1) - 1f, bBottom, pxl(.4f)) < 0f
            if (!inside) 0f
            else (if (night) .22f else .5f) * Px.smooth(1.4f, 0f, abs(y - bTop - .9f))
        }
        // the wheels: tyres with a hub, set under the body
        for (wx in floatArrayOf(s.wheel0, s.wheel1)) {
            val cx = pxl(wx)
            val cy = roadY - pxl(s.wheelR)
            c.fill(tyre) { x, y -> circle(x, y, cx, cy, pxl(s.wheelR)) }
            c.fill(hub) { x, y -> circle(x, y, cx, cy, pxl(s.wheelR) * .42f) }
        }
        // the lenses: a pale headlight at the nose, a red tail-light at the back
        val lensY = py(s.bodyBottom + (s.bodyTop - s.bodyBottom) * .55f)
        c.fill(if (night) 0xFFF8E6 else 0xFFF1C8, alpha = if (night) 1f else .9f) { x, y ->
            roundRect(
                x,
                y,
                pxl(s.x1) - pxl(.55f),
                lensY - pxl(.2f),
                pxl(s.x1) - pxl(.12f),
                lensY + pxl(.2f),
                pxl(.12f),
            )
        }
        c.fill(if (night) 0xFF5A48 else 0xB8302A, alpha = if (night) 1f else .9f) { x, y ->
            roundRect(
                x,
                y,
                pxl(s.x0) + pxl(.12f),
                lensY - pxl(.18f),
                pxl(s.x0) + pxl(.5f),
                lensY + pxl(.18f),
                pxl(.1f),
            )
        }
        // nothing touches the border (the sprite is drawn scaled and must stay clean at its edge)
        for (y in 0 until h) for (x in 0 until w) if (
            x == 0 || y == 0 || x == w - 1 || y == h - 1
        ) {
            val i = (y * w + x) * 4
            c.px[i + 3] = 0f
        }
        return c
    }

    // ---- the lights

    /** The glow: a soft round bloom, white, with a tighter bright core; clear at the border. */
    fun glow(): IntArray {
        val n = GLOW
        val r0 = n / 2f
        return IntArray(n * n) {
            val x = it % n + .5f - r0
            val y = it / n + .5f - r0
            val r = sqrt(x * x + y * y) / r0
            val a =
                (.55f * exp(-(r / .24f) * (r / .24f)) + .45f * exp(-(r / .52f) * (r / .52f))) *
                    Px.smooth(1f, .8f, r) *
                    border(it % n, it / n, n, n)
            if (a <= .002f) 0 else Px.argbF(a, 1f, 1f, 1f)
        }
    }

    /** 0 on the outermost row and column of a [w] by [h] picture, 1 inside it. */
    private fun border(x: Int, y: Int, w: Int, h: Int): Float =
        if (x == 0 || y == 0 || x == w - 1 || y == h - 1) 0f else 1f

    /**
     * The beam: light leaving its origin (at the left, half way down) in a cone to the right,
     * bright and narrow where it leaves, wider and fainter the further it goes, soft at its sides.
     */
    fun beam(): IntArray {
        val w = BEAM_W
        val h = BEAM_H
        val oy = h / 2f
        return IntArray(w * h) {
            val x = it % w + .5f - 6f
            val y = it / w + .5f - oy
            if (x <= 0f) 0
            else {
                val spread = 1.3f + x * .105f
                val across = exp(-(y * y) / (2f * spread * spread))
                val along = exp(-x / 52f) * Px.smooth(0f, 4f, x)
                val a =
                    across *
                        along *
                        Px.smooth(w - 6f, w - 30f, x + 6f) *
                        Px.smooth(0f, 3f, oy - abs(y)) *
                        border(it % w, it / w, w, h)
                if (a <= .002f) 0 else Px.argbF(a, 1f, 1f, 1f)
            }
        }
    }

    // ---- the meteor

    /**
     * The streak: a line that is hottest and thinnest just behind the head and tapers back to
     * nothing along a slight curve, with a fine ripple in its brightness. The core is the burning
     * trail itself, the halo the glow of the air round it: wider, softer, drawn in a cooler tint.
     */
    fun streak(): StreakSprite {
        val w = STREAK_W
        val h = STREAK_H
        val headX = w - 14f
        val tailX = 10f
        val core = IntArray(w * h)
        val halo = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val cx = x + .5f
            val cy = y + .5f
            // how far back along the streak, 0 at the head to 1 at the tail's end
            val s = (headX - cx) / (headX - tailX)
            if (s > 1f) continue
            val sc = s.coerceAtLeast(0f)
            // the streak sags a little towards its tail: a curve, not a ruler
            val mid = h * .42f + sc * sc * h * .22f
            // how far off the streak's line; ahead of the head, how far from the head itself
            val dy = cy - mid
            val ax = (cx - headX).coerceAtLeast(0f)
            val d2 = dy * dy + ax * ax
            val ripple =
                1f +
                    .3f * (Noise.fbm(cx * .09f, 3f, 17, 3) - .5f) +
                    .15f * (Noise.value(cx * .35f, 1f, 5) - .5f)
            val coreW =
                (.45f + 2.3f * (1f - sc).pow(.9f)) *
                    (1f + .25f * (Noise.fbm(cx * .05f, 7f, 23, 2) - .5f))
            val coreI = (1f - sc).pow(1.5f) * ripple * (1f + .6f * Px.smooth(.14f, 0f, sc))
            val haloW = 1f + 5.5f * (1f - sc).pow(.65f)
            val haloI = .5f * (1f - sc).pow(1.25f) * (1f + .12f * (ripple - 1f))
            val edge = Px.smooth(0f, 4f, min(min(cx, w - cx), min(cy, h - cy))) * border(x, y, w, h)
            val c = coreI * exp(-d2 / (2f * coreW * coreW)) * edge
            val g = haloI * exp(-d2 / (2f * haloW * haloW)) * edge
            val i = y * w + x
            if (c > .002f) core[i] = Px.argbF(Px.clamp01(c), 1f, 1f, 1f)
            if (g > .002f) halo[i] = Px.argbF(Px.clamp01(g), 1f, 1f, 1f)
        }
        return StreakSprite(w, h, headX, core, halo)
    }
}
