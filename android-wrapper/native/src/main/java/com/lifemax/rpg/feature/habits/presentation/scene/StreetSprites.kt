package com.lifemax.rpg.feature.habits.presentation.scene

import com.lifemax.rpg.feature.habits.domain.CycleRig
import com.lifemax.rpg.feature.habits.domain.DogRig
import com.lifemax.rpg.feature.habits.domain.FigBuilder
import com.lifemax.rpg.feature.habits.domain.Shape
import com.lifemax.rpg.feature.habits.domain.StreetGait
import com.lifemax.rpg.feature.habits.domain.StreetLife
import com.lifemax.rpg.feature.habits.domain.StreetLooks
import com.lifemax.rpg.feature.habits.domain.StreetRig
import com.lifemax.rpg.feature.habits.domain.WalkerJoints
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The street's people and the near car as pre-rendered pictures: the walkers ([StreetRig]) at a close-up's
 * resolution, the dog and the cyclist ([DogRig], [CycleRig]) through the same rasteriser, and the near car painted
 * from signed distances as the far road's vehicles are, at street scale. Pure Kotlin: pixels only; [SceneSprites]
 * turns them into images the first time each is drawn.
 */

/**
 * One baked walker frame: the picture (cut to its pixels, the feet's middle at [Sprite.ox],
 * [Sprite.oy]) and its joints.
 */
internal class WalkerSprite(val sprite: Sprite, val joints: WalkerJoints)

/**
 * The near car, nose to the right: its painted body by [day] and by [night] (dark, its lenses lit),
 * the [lights] to add over it at night (the lamps' bloom), the wheels' centres and radius, in the
 * picture's pixels, the ground at [groundY].
 */
internal class CarSprite(
    val w: Int,
    val h: Int,
    val ppu: Float,
    val day: IntArray,
    val night: IntArray,
    val lights: IntArray,
    val wheelX0: Float,
    val wheelX1: Float,
    val wheelY: Float,
    val groundY: Float,
    val frontX: Float,
    val backX: Float,
    val lampY: Float,
    /** The car's length, bumper to bumper, in street units. */
    val length: Float,
)

internal object StreetSprites {
    /** Pixels to a street unit for the walkers: what the phone shows at the focus camera, 3x. */
    const val PPU = 3.6f

    /** Pixels to a unit for the near car (it is big). */
    const val CAR_PPU = 3f

    /** Pixels left round a figure for its glow, and round the car for its lamps' bloom. */
    private const val PAD = 3
    private const val CAR_PAD = 20

    /** The bicycle's rider is this look. */
    const val RIDER = 3

    private fun bake(shapes: List<Shape>, size: Float, ppu: Float): Sprite {
        val b = FigBuilder.bounds(shapes)
        val x0 = floor(b[0] * size * ppu).toInt() - PAD
        val y0 = floor(b[1] * size * ppu).toInt() - PAD
        val x1 = ceil(b[2] * size * ppu).toInt() + PAD
        val y1 = ceil(b[3] * size * ppu).toInt() + PAD
        val full =
            PeopleRaster.figure(
                shapes,
                x1 - x0,
                y1 - y0,
                size * ppu,
                -x0.toFloat(),
                -y0.toFloat(),
                emptyList(),
                false,
                PeopleRaster.STREET,
            )
        return PeopleSprites.crop(full)
    }

    /**
     * Walker [look]'s frame [frame] ([StreetRig.TOTAL_FRAMES] of them), at [StreetLife.SIZE] times
     * the rig.
     */
    fun walker(look: Int, frame: Int): WalkerSprite {
        val fig = StreetRig.frame(look, frame)
        return WalkerSprite(bake(fig.shapes, StreetLife.SIZE, PPU), fig.joints)
    }

    /** The dog's frame [frame] ([DogRig.TOTAL_FRAMES]). */
    fun dog(frame: Int): Sprite = bake(DogRig.frame(frame), StreetLife.DOG_SIZE, PPU)

    /**
     * The bicycle and its rider at crank frame [frame] ([StreetGait.CRANK_FRAMES]), wheels apart.
     */
    fun cycle(frame: Int): Sprite = bake(CycleRig.frame(RIDER, frame), StreetLife.SIZE, PPU)

    /** A spoked wheel of radius [r] units: tyre, rim, spokes and hub, centred. */
    fun wheel(r: Float, ppu: Float, spokes: Int, tyre: Float, hubR: Float, solid: Boolean): Sprite {
        val n = ceil(r * ppu * 2f).toInt() + 4
        val c = Canvas(n, n)
        val cx = n / 2f
        val cy = n / 2f
        val R = r * ppu
        c.fill(0x0B0D12) { x, y -> ring(x, y, cx, cy, R, R - tyre * ppu) }
        c.fill(0x9AA0AC) { x, y -> ring(x, y, cx, cy, R - tyre * ppu, R - tyre * ppu - .45f * ppu) }
        if (solid) {
            c.fill(0x2A2E38, .9f) { x, y -> circle(x, y, cx, cy, R - tyre * ppu - .45f * ppu) }
            c.fill(0x8A8F9A) { x, y -> circle(x, y, cx, cy, hubR * ppu) }
            // five bolt holes, so the turning shows
            for (i in 0 until 5) {
                val a = i / 5f * 2f * Math.PI.toFloat()
                c.fill(0x14171E) { x, y ->
                    circle(
                        x,
                        y,
                        cx + cos(a) * hubR * ppu * .62f,
                        cy + sin(a) * hubR * ppu * .62f,
                        .36f * ppu,
                    )
                }
            }
            c.fill(0x14171E) { x, y -> circle(x, y, cx, cy, .35f * ppu) }
        } else {
            for (i in 0 until spokes) {
                val a = i / spokes.toFloat() * Math.PI.toFloat()
                c.fill(0xB8BEC8, .85f) { x, y ->
                    segment(
                        x,
                        y,
                        cx - cos(a) * (R - tyre * ppu),
                        cy - sin(a) * (R - tyre * ppu),
                        cx + cos(a) * (R - tyre * ppu),
                        cy + sin(a) * (R - tyre * ppu),
                        .12f * ppu,
                    )
                }
            }
            c.fill(0x3A3F4A) { x, y -> circle(x, y, cx, cy, hubR * ppu) }
        }
        return Sprite(n, n, ppu, cx, cy, c.argb())
    }

    // ---- the near car

    /** The bodies' paints, RGB. */
    val PAINTS = intArrayOf(0xC23A2E, 0xE6E8EC, 0x2B5EA6, 0x23262D, 0xB3B7BE)

    /**
     * A body: its length, the bottom of the body and the beltline over the ground, the cabin's foot
     * (from [cab0] to [cab1]) and roof (from [roof0] to [roof1] at [roofY]), the wheels' centres
     * from either end, and whether the back is a hatch (a steep rear window).
     */
    private class Body(
        val length: Float,
        val bottom: Float,
        val belt: Float,
        val cab0: Float,
        val cab1: Float,
        val roof0: Float,
        val roof1: Float,
        val roofY: Float,
        val wheelIn: Float,
        val hatch: Boolean,
    )

    private val BODIES =
        arrayOf(
            Body(66f, 2.6f, 10.4f, 18f, 54f, 25f, 46.5f, 19.6f, 13f, false),
            Body(59f, 2.6f, 10.6f, 14f, 49f, 16.5f, 41f, 20.8f, 12.5f, true),
            Body(68f, 3.8f, 12.2f, 12f, 58f, 14f, 53f, 23.6f, 13.5f, true),
        )

    /** The near car [body] in [paint]: its day and night pictures and its lights. */
    fun car(body: Int, paint: Int): CarSprite {
        val b = BODIES[body]
        val k = CAR_PPU
        val w = ceil(b.length * k).toInt() + 2 * CAR_PAD
        val h = ceil((b.roofY + 2f) * k).toInt() + 14
        val groundY = h - 8f
        val day = paint(b, PAINTS[paint], false, w, h, groundY)
        val night = paint(b, PAINTS[paint], true, w, h, groundY)
        val lights = lights(b, w, h, groundY)
        val x0 = CAR_PAD.toFloat()
        return CarSprite(
            w,
            h,
            k,
            day.argb(),
            night.argb(),
            lights.argb(),
            x0 + b.wheelIn * k,
            x0 + (b.length - b.wheelIn) * k,
            groundY - StreetLife.CAR_WHEEL * k,
            groundY,
            x0 + b.length * k,
            x0,
            groundY - 8f * k,
            b.length,
        )
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(s: Int) =
            (((a shr s and 255) + ((b shr s and 255) - (a shr s and 255)) * t).toInt()).coerceIn(
                0,
                255,
            )
        return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun paint(b: Body, rgb: Int, night: Boolean, w: Int, h: Int, groundY: Float): Canvas {
        val c = Canvas(w, h)
        val k = CAR_PPU
        val x0 = CAR_PAD.toFloat()
        fun px(u: Float) = x0 + u * k
        fun py(up: Float) = groundY - up * k
        val paint = if (night) mix(rgb, 0x0B0E18, .72f) else rgb
        val glass = if (night) 0x090C16 else mix(0x243650, rgb, .1f)
        val trim = if (night) 0x05060A else 0x15171C
        val L = b.length
        val wheelR = StreetLife.CAR_WHEEL
        // the contact shadow on the road: soft, deeper under the middle
        c.soft(0x05070C) { x, y ->
            val ex = (x - px(L / 2f)) / (px(L / 2f) - px(1.5f))
            val ey = (y - groundY) / (1.6f * k)
            val r = sqrt(ex * ex + ey * ey)
            (1f - r).coerceIn(0f, 1f) * .75f
        }
        // the body: a rounded box from bottom to belt, the shading darker low down and a highlight
        // along the belt
        val shade: (Float, Float) -> Float = { _, y ->
            val u = ((py(b.belt) - y) / (py(b.belt) - py(b.bottom))).coerceIn(0f, 1f)
            (if (night) .55f else .72f) + .45f * (1f - u) * (1f - u) * (1f - u)
        }
        c.fill(paint, 1f, shade) { x, y ->
            roundRect(x, y, px(.6f), py(b.belt), px(L - .6f), py(b.bottom), 1.4f * k)
        }
        // the cabin: a trapezoid with rounded top corners
        val cabTone: (Float, Float) -> Float = { _, _ -> if (night) .45f else .95f }
        c.fill(paint, 1f, cabTone) { x, y ->
            min(
                poly(
                    x,
                    y,
                    floatArrayOf(px(b.roof0), px(b.roof1), px(b.cab1), px(b.cab0)),
                    floatArrayOf(py(b.roofY), py(b.roofY), py(b.belt - .5f), py(b.belt - .5f)),
                ),
                roundRect(
                    x,
                    y,
                    px(b.roof0 + 1f),
                    py(b.roofY),
                    px(b.roof1 - 1f),
                    py(b.belt),
                    2.2f * k,
                ),
            )
        }
        // the roof's rounded edge
        c.fill(paint, 1f, { _, _ -> if (night) .5f else 1.08f }) { x, y ->
            roundRect(
                x,
                y,
                px(b.roof0 + .4f),
                py(b.roofY),
                px(b.roof1 - .4f),
                py(b.roofY - 1.2f),
                1.2f * k,
            )
        }
        // the windows: the glass inset from the pillars, split by the B pillar
        val inset = 1.2f
        val gy0 = b.belt + .3f
        val gy1 = b.roofY - 1.1f
        val split = (b.cab0 + b.cab1) / 2f + (if (b.hatch) 1.5f else 2.5f)
        // the rear window's slope: steep on a hatch
        val rearTop = b.roof0 + inset
        val rearBottom = b.cab0 + inset * (if (b.hatch) 1.6f else 2.2f)
        val frontTop = b.roof1 - inset
        val frontBottom = b.cab1 - inset * 2.4f
        val glassShade: (Float, Float) -> Float = { x, y ->
            // a diagonal reflection band across the glass
            val d = (x - px(0f)) * .55f + (py(gy1) - y)
            val band = abs(((d / (3.5f * k)) % 1f + 1f) % 1f - .5f)
            if (night) .6f else .85f + .5f * Px.smooth(.32f, .5f, band)
        }
        c.fill(glass, 1f, glassShade) { x, y ->
            poly(
                x,
                y,
                floatArrayOf(px(rearTop), px(split - .7f), px(split - .7f), px(rearBottom)),
                floatArrayOf(py(gy1), py(gy1), py(gy0), py(gy0)),
            )
        }
        c.fill(glass, 1f, glassShade) { x, y ->
            poly(
                x,
                y,
                floatArrayOf(px(split + .7f), px(frontTop), px(frontBottom), px(split + .7f)),
                floatArrayOf(py(gy1), py(gy1), py(gy0), py(gy0)),
            )
        }
        if (night) {
            // the dashboard's faint glow and the people in the car
            c.soft(0x4A5B7A) { x, y ->
                val cx = px(frontBottom - 3f)
                val cy = py(gy0 + 1.5f)
                val d = sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy)) / (3f * k)
                (1f - d).coerceIn(0f, 1f) * .35f
            }
        }
        // the sills and the bumpers, dark
        c.fill(trim) { x, y ->
            roundRect(x, y, px(.6f), py(b.bottom + 1.3f), px(L - .6f), py(b.bottom), .6f * k)
        }
        c.fill(trim) { x, y ->
            roundRect(x, y, px(0f), py(7f), px(1.6f), py(b.bottom + .5f), .5f * k)
        }
        c.fill(trim) { x, y ->
            roundRect(x, y, px(L - 1.6f), py(7f), px(L), py(b.bottom + .5f), .5f * k)
        }
        // the wheel arches: cut out, with a dark lip
        for (wx in floatArrayOf(b.wheelIn, L - b.wheelIn)) {
            c.fill(trim) { x, y ->
                ring(x, y, px(wx), py(wheelR), (wheelR + 1.1f) * k, (wheelR + .5f) * k)
            }
            c.cut { x, y -> circle(x, y, px(wx), py(wheelR), (wheelR + .5f) * k) }
        }
        // the door seams and handles
        val seam = (b.cab0 + b.cab1) / 2f + 1f
        c.fill(trim, .55f) { x, y ->
            roundRect(
                x,
                y,
                px(seam - .15f),
                py(b.belt + .2f),
                px(seam + .15f),
                py(b.bottom + 1.3f),
                .1f,
            )
        }
        c.fill(trim, .7f) { x, y ->
            roundRect(
                x,
                y,
                px(seam + 1.5f),
                py(b.belt - 2.2f),
                px(seam + 4f),
                py(b.belt - 2.9f),
                .3f * k,
            )
        }
        c.fill(trim, .7f) { x, y ->
            roundRect(
                x,
                y,
                px(seam - 4.2f),
                py(b.belt - 2.2f),
                px(seam - 1.7f),
                py(b.belt - 2.9f),
                .3f * k,
            )
        }
        // the mirror
        c.fill(paint, 1f, { _, _ -> if (night) .5f else .9f }) { x, y ->
            roundRect(
                x,
                y,
                px(frontBottom - .4f),
                py(b.belt + 2.4f),
                px(frontBottom + 1.6f),
                py(b.belt + 1.1f),
                .4f * k,
            )
        }
        // the lamps: the head lamp's lens and the tail lamp
        val headLens = if (night) 0xFFF3C8 else 0xD9DEE6
        val tailLens = if (night) 0xFF3A2A else 0x9A1E18
        c.fill(headLens) { x, y ->
            roundRect(x, y, px(L - 3.4f), py(9.2f), px(L - .3f), py(7.1f), .6f * k)
        }
        c.fill(tailLens) { x, y -> roundRect(x, y, px(.3f), py(9.4f), px(3.2f), py(7.2f), .5f * k) }
        if (!night) {
            // the lens's glint by day
            c.fill(0xFFFFFF, .7f) { x, y ->
                roundRect(x, y, px(L - 2.8f), py(8.9f), px(L - 1.2f), py(8.4f), .3f * k)
            }
        }
        // the belt's highlight
        c.fill(0xFFFFFF, if (night) .18f else .35f) { x, y ->
            roundRect(x, y, px(1.5f), py(b.belt + .1f), px(L - 1.5f), py(b.belt - .45f), .2f * k)
        }
        return c
    }

    private fun lights(b: Body, w: Int, h: Int, groundY: Float): Canvas {
        val c = Canvas(w, h)
        val k = CAR_PPU
        val x0 = CAR_PAD.toFloat()
        fun px(u: Float) = x0 + u * k
        fun py(up: Float) = groundY - up * k
        val L = b.length
        // the head lamp's bloom and the tail lamp's
        c.soft(0xFFE9B8) { x, y ->
            val d =
                sqrt(
                    (x - px(L - 1.6f)) * (x - px(L - 1.6f)) + (y - py(8.1f)) * (y - py(8.1f)) * 1.6f
                ) / (6f * k)
            val f = (1f - d).coerceIn(0f, 1f)
            f * f * .9f
        }
        c.soft(0xFF4A30) { x, y ->
            val d =
                sqrt((x - px(1.6f)) * (x - px(1.6f)) + (y - py(8.3f)) * (y - py(8.3f)) * 1.4f) /
                    (4.2f * k)
            val f = (1f - d).coerceIn(0f, 1f)
            f * f * .8f
        }
        return c
    }

    /** What the whole street set would take, every frame baked, in bytes. */
    fun bytes(): Int {
        var total = 0
        for (l in 0 until StreetLooks.COUNT) for (f in 0 until StreetRig.TOTAL_FRAMES) {
            val s = walker(l, f).sprite
            total += s.w * s.h * 4
        }
        for (f in 0 until DogRig.TOTAL_FRAMES) dog(f).let { total += it.w * it.h * 4 }
        for (f in 0 until StreetGait.CRANK_FRAMES) cycle(f).let { total += it.w * it.h * 4 }
        for (bd in 0 until StreetLife.CAR_BODIES) {
            val s = car(bd, 0)
            total += s.w * s.h * 4 * (2 * StreetLife.CAR_PAINTS + 1)
        }
        return total
    }

    /** What the walkers alone take, every frame of every look baked, in bytes. */
    fun walkerBytes(): Int {
        var total = 0
        for (l in 0 until StreetLooks.COUNT) for (f in 0 until StreetRig.TOTAL_FRAMES) {
            val s = walker(l, f).sprite
            total += s.w * s.h * 4
        }
        return total
    }

    // ---- a small painter: float RGBA composited over, anti-aliased shapes from signed distances

    internal class Canvas(val w: Int, val h: Int) {
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

        /** Takes away what is inside [sdf]. */
        fun cut(sdf: (Float, Float) -> Float) {
            for (y in 0 until h) for (x in 0 until w) {
                val cov = Px.clamp01(.5f - sdf(x + .5f, y + .5f))
                if (cov <= 0f) continue
                px[(y * w + x) * 4 + 3] *= 1f - cov
            }
        }

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
        val hw = abs(x1 - x0) / 2f
        val hh = abs(y1 - y0) / 2f
        val rr = min(r, min(hw, hh))
        val qx = abs(x - cx) - (hw - rr)
        val qy = abs(y - cy) - (hh - rr)
        val ox = max(qx, 0f)
        val oy = max(qy, 0f)
        return sqrt(ox * ox + oy * oy) + min(max(qx, qy), 0f) - rr
    }

    private fun circle(x: Float, y: Float, cx: Float, cy: Float, r: Float): Float {
        val dx = x - cx
        val dy = y - cy
        return sqrt(dx * dx + dy * dy) - r
    }

    private fun ring(x: Float, y: Float, cx: Float, cy: Float, outer: Float, inner: Float): Float =
        max(circle(x, y, cx, cy, outer), -circle(x, y, cx, cy, inner))

    private fun segment(
        x: Float,
        y: Float,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        r: Float,
    ): Float {
        val dx = x1 - x0
        val dy = y1 - y0
        val l2 = max(1e-6f, dx * dx + dy * dy)
        val t = (((x - x0) * dx + (y - y0) * dy) / l2).coerceIn(0f, 1f)
        val qx = x - (x0 + dx * t)
        val qy = y - (y0 + dy * t)
        return sqrt(qx * qx + qy * qy) - r
    }

    /** A convex polygon, clockwise on a y-down canvas. */
    private fun poly(x: Float, y: Float, xs: FloatArray, ys: FloatArray): Float {
        var d = -1e9f
        val n = xs.size
        for (i in 0 until n) {
            val j = (i + 1) % n
            val ex = xs[j] - xs[i]
            val ey = ys[j] - ys[i]
            val len = max(1e-6f, sqrt(ex * ex + ey * ey))
            val nx = ey / len
            val ny = -ex / len
            d = max(d, (x - xs[i]) * nx + (y - ys[i]) * ny)
        }
        return d
    }
}
