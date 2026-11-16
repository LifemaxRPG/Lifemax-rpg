package com.lifemax.rpg.feature.habits.presentation.scene

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** A baked picture: [w] by [h] ARGB pixels. */
internal class GlassStrip(val w: Int, val h: Int, val pixels: IntArray)

/**
 * What a pane of glass mirrors, baked once and coloured by the hour when drawn. [BuildingPainter]
 * lays it over every pane, the shop windows and the door as one sheet per facade ([GlassMap]): the
 * frames and mullions only cut it into pieces.
 * - [sky]: the sky in front of the facade, [SKY_W] pixels round, from high overhead (its top row)
 *   down to the horizon (its last row), with soft cumulus drifting through it in groups, open sky
 *   between them. A pixel spans [GlassMap.K_SKY_X] by [GlassMap.K_SKY_Y] units of facade, so the
 *   clouds are baked wide to look round. Opaque; its channels are weights, not colours: red the
 *   clouds' lit tops, green their shaded undersides, blue the horizon's pale band, the rest of each
 *   pixel the sky overhead. A colour matrix turns them into the hour's colours.
 * - [land]: what the street looks out on, [LAND_W] pixels round, its horizon on row [HORIZON]:
 *   mostly open sky (clear) over a low, varied skyline with gaps in it: a ridge of far hills with
 *   two towers further off, a waterfront with a suspension bridge, a park's tree line, a church's
 *   spire, a row of low houses, a lamp or two in front; under the horizon the far shore, the water
 *   and the near bank. Its alpha is what covers the sky; its channels are weights: red the haze on
 *   far things (their lit, distant tone; moonlit at night), green the lights (windows, the bridge's
 *   lamps, a clock face: warm at night), blue the water; the rest the near, dark things. Its top
 *   row is clear, so it can be clamped above; its last is the near bank, clamped below.
 * - [interior]: a dim room seen through the glass of some panes: blinds, curtains, a net, or the
 *   blind pulled down.
 * - [glint]: the sun's specular hot spot on glass, white at an alpha, drawn additively. Pure
 *   Kotlin; deterministic.
 */
internal object GlassSprites {
    const val SKY_W = 256
    const val SKY_H = 48
    const val LAND_W = 1024
    const val LAND_H = 64

    /** The land strip's row the mirrored horizon lies along. */
    const val HORIZON = 36

    const val INTERIOR_W = 32
    const val INTERIOR_H = 72
    const val INTERIORS = 5

    /** The interior that is a roller blind pulled all the way down. */
    const val BLIND = 4
    const val GLINT = 32

    fun bytes(): Int =
        (SKY_W * SKY_H + LAND_W * LAND_H + INTERIORS * INTERIOR_W * INTERIOR_H + GLINT * GLINT) * 4

    /** [f] at [x] blended with itself a [period] on, so a strip closes on itself. */
    private inline fun seam(x: Float, period: Float, f: (Float) -> Float): Float {
        val t = x / period
        return f(x) * (1f - t) + f(x - period) * t
    }

    fun sky(): GlassStrip {
        val w = SKY_W
        val h = SKY_H
        val period = w * GlassMap.K_SKY_X
        val cloud = FloatArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val v = y / (h - 1f)
            // in facade units, so a heap is as round in the glass as it is in the sky; flatter and
            // smaller toward the horizon, in groups with long stretches of open sky between them
            val ux = x * GlassMap.K_SKY_X
            val uy = y * GlassMap.K_SKY_Y * (1f + 1.2f * v)
            val heap = seam(ux, period) { Noise.fbm(it / 26f, uy / 20f, 911, 4, .45f) }
            val group = seam(ux, period) { Noise.value(it / 64f, 2.3f, 913) }
            val band = Px.smooth(.1f, .4f, v) * (1f - .8f * Px.smooth(.84f, 1f, v))
            val d = heap * 1.1f + group * 1.1f - 1.1f
            cloud[y * w + x] = Px.smooth(0f, .22f, d) * band
        }
        val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val c = cloud[i]
            val v = y / (h - 1f)
            // lit where there is less cloud above than below (the heap's sunny top), shaded under
            val above = cloud[(y - 2).coerceAtLeast(0) * w + x]
            val below = cloud[(y + 2).coerceAtMost(h - 1) * w + x]
            val lit = Px.clamp01(.62f + (below - above) * 1.4f - .25f * v)
            val horizon = Px.smooth(.2f, 1f, v).pow(1.5f)
            px[i] = Px.argbF(1f, c * lit, c * (1f - lit), (1f - c) * horizon)
        }
        return GlassStrip(w, h, px)
    }

    fun land(): GlassStrip = Land().bake()

    /**
     * The land strip, drawn at twice its size (shapes as tests of a pixel's middle, painted back to
     * front over one another) and averaged down, so its edges are smooth.
     */
    private class Land {
        val s = 2
        val hw = LAND_W * s
        val hh = LAND_H * s
        val a = FloatArray(hw * hh)
        val r = FloatArray(hw * hh)
        val g = FloatArray(hw * hh)
        val b = FloatArray(hw * hh)
        val o = FloatArray(3)
        val hz = HORIZON.toFloat()

        /**
         * Paints every fine pixel from [x0] to [x1] and [y0] to [y1] (strip pixels; x wraps round)
         * by [f]: given the pixel's middle, it sets the weights in [o] and returns the coverage.
         */
        fun draw(
            x0: Float,
            x1: Float,
            y0: Float,
            y1: Float,
            f: (Float, Float, FloatArray) -> Float,
        ) {
            val ya = floor(y0 * s).toInt().coerceAtLeast(0)
            val yb = ceil(y1 * s).toInt().coerceAtMost(hh)
            for (yy in ya until yb) for (xx in floor(x0 * s).toInt() until ceil(x1 * s).toInt()) {
                o[0] = 0f
                o[1] = 0f
                o[2] = 0f
                val c = Px.clamp01(f((xx + .5f) / s, (yy + .5f) / s, o))
                if (c <= 0f) continue
                val i = yy * hw + ((xx % hw) + hw) % hw
                val k = 1f - c
                a[i] = a[i] * k + c
                r[i] = r[i] * k + o[0] * c
                g[i] = g[i] * k + o[1] * c
                b[i] = b[i] * k + o[2] * c
            }
        }

        fun set(wr: Float, wg: Float = 0f, wb: Float = 0f) {
            o[0] = wr
            o[1] = wg
            o[2] = wb
        }

        /** A ridge of hills from [x0] to [x1], [peak] pixels high at most, [haze] its distance. */
        fun ridge(x0: Float, x1: Float, peak: Float, haze: Float, seed: Int) =
            draw(x0, x1, hz - peak - 1f, hz + .5f) { x, y, o ->
                val t = (x - x0) / (x1 - x0)
                val env = sin(t * Math.PI.toFloat()).coerceAtLeast(0f).pow(.6f)
                val top = hz - env * peak * (.45f + .55f * Noise.fbm(x / 34f, 1.7f, seed, 3))
                // woods darker on the slopes, the crest catching the light
                val wood = Noise.fbm(x / 7f, y / 2.5f, seed + 5, 2)
                o[0] =
                    haze - .1f * Px.smooth(.45f, .7f, wood) +
                        .07f * (1f - Px.smooth(0f, 1.5f, y - top))
                y - top + .5f
            }

        /** A tower far off: [w] wide, [tall] high, windows lit at night, the sun on its left. */
        fun tower(x0: Float, w: Float, tall: Float, haze: Float, seed: Int, mast: Boolean) {
            val top = hz - tall
            draw(x0, x0 + w, top - 1f, hz) { x, y, o ->
                // a setback near the top
                val narrow = if (y < top + 4f) 1f else 0f
                if (x < x0 + narrow || x > x0 + w - narrow) return@draw 0f
                set(if (x < x0 + w * .35f) haze + .12f else haze)
                val col = floor(x - x0).toInt()
                val row = floor(y - top).toInt()
                if (row > 5 && col % 2 == 1 && row % 2 == 0 && hash(seed + col, row) < .3f)
                    set(0f, 1f)
                y - top + .5f
            }
            if (mast)
                draw(x0 + w / 2f - .4f, x0 + w / 2f + .4f, top - 6f, top) { _, _, o ->
                    set(haze - .1f)
                    .8f
                }
        }

        fun disc(cx: Float, cy: Float, rad: Float, wr: Float, wg: Float = 0f, wb: Float = 0f) =
            draw(cx - rad - 1f, cx + rad + 1f, cy - rad - 1f, cy + rad + 1f) { x, y, _ ->
                set(wr, wg, wb)
                rad - sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy)) + .5f
            }

        fun box(
            x0: Float,
            x1: Float,
            y0: Float,
            y1: Float,
            wr: Float,
            wg: Float = 0f,
            c: Float = 1f,
        ) =
            draw(x0, x1, y0, y1) { _, _, _ ->
                set(wr, wg)
                c
            }

        fun bake(): GlassStrip {
            val period = LAND_W.toFloat()
            // the land under the horizon: the far shore pale, the fields darker toward the near
            // bank
            draw(0f, period, hz, LAND_H.toFloat()) { x, y, o ->
                val d = (y - hz) / (LAND_H - hz)
                val n = seam(x, period) { Noise.value(it / 19f, y / 2f, 7) }
                o[0] = .52f * (1f - Px.smooth(0f, .55f, d)) + .1f + .06f * n
                1f
            }
            box(0f, period, hz, hz + .8f, .74f)
            // the waterfront: water widening toward the viewer, its near wall, the bridge over it
            draw(330f, 680f, hz + .5f, 52f) { x, y, o ->
                val d = y - hz
                val l = 360f - d * 1.6f
                val rr = 640f + d * 1.6f
                val ripple = .1f * sin(y * 2.7f + 3f * Noise.value(x / 9f, y, 21))
                set(.05f, 0f, .88f + ripple)
                Px.smooth(l - 1f, l + 1f, x) * (1f - Px.smooth(rr - 1f, rr + 1f, x))
            }
            draw(320f, 690f, 50f, 52.5f) { x, y, o ->
                set(if (y < 50.8f) .42f else .26f)
                Px.smooth(328f, 332f, x) * (1f - Px.smooth(668f, 672f, x))
            }
            ridge(680f, 862f, 8f, .8f, 51)
            tower(226f, 7f, 31f, .46f, 61, true)
            tower(246f, 5f, 23f, .52f, 67, false)
            ridge(40f, 330f, 15f, .44f, 41)
            bridge()
            park()
            church(866f)
            houses(940f)
            lamp(318f)
            lamp(1004f)
            return down()
        }

        /** A suspension bridge over the water: deck, two pylons, the cables, lamps along it. */
        fun bridge() {
            val deck = hz - 5f
            fun deckY(x: Float) =
                when {
                    x < 384f -> hz - 5f * Px.clamp01((x - 364f) / 20f)
                    x > 616f -> hz - 5f * Px.clamp01((636f - x) / 20f)
                    else -> deck
                }
            fun cable(x: Float): Float =
                when {
                    x < 440f -> 13.5f + 16f * ((440f - x) / 56f).pow(1.5f)
                    x > 560f -> 13.5f + 16f * ((x - 560f) / 56f).pow(1.5f)
                    else -> 13.5f + 16f * (1f - ((x - 500f) / 60f).pow(2))
                }
            // the pylons' and the lamps' reflections in the water, broken by its ripples
            for (px in floatArrayOf(440f, 560f)) draw(px - 1.3f, px + 1.3f, hz + .6f, hz + 10f) {
                _,
                y,
                o ->
                set(.12f, 0f, .45f)
                .45f * (1f - (y - hz) / 10f) * (.6f + .4f * sin(y * 3.1f))
            }
            for (i in 0 until 17) {
                val lx = 388f + i * 14f
                draw(lx - .5f, lx + .5f, hz + .8f, hz + 9f) { _, y, o ->
                    set(0f, 1f)
                    .55f * (1f - (y - hz) / 9f) * Px.smooth(-.2f, .5f, sin(y * 2.3f + i))
                }
            }
            // the cables and the hangers from them
            draw(384f, 616f, 12.5f, deck) { x, y, o ->
                set(.3f)
                val c = cable(x)
                val main = .9f * (1f - abs(y - c) / .6f)
                val hanger =
                    if (
                        y > c &&
                            (x - 386f).mod(6f) < .5f &&
                            abs(x - 440f) > 2f &&
                            abs(x - 560f) > 2f
                    )
                        .45f
                    else 0f
                maxOf(main, hanger)
            }
            // the deck and its approaches, a lit edge along its top
            draw(362f, 638f, hz - 6f, hz) { x, y, o ->
                val top = deckY(x)
                set(if (y < top + .5f) .4f else .2f)
                if (y >= top && y < top + 1.5f) 1f else 0f
            }
            // the pylons: two legs' worth in one, a cross beam, lit on the sun's side
            for (px in floatArrayOf(440f, 560f)) {
                draw(px - 1.3f, px + 1.3f, 12.5f, hz + .7f) { x, y, o ->
                    set(if (x < px) .42f else .28f)
                    if (y < 13f) .5f else 1f
                }
                box(px - 2.2f, px + 2.2f, 18.5f, 19.4f, .3f)
            }
            // the lamps along the deck
            for (i in 0 until 17) disc(388f + i * 14f, deck - .6f, .6f, 0f, 1f)
        }

        /** A park's tree line: round crowns of different sizes, a pair of poplars among them. */
        fun park() {
            var x = 708f
            var k = 0
            while (x < 830f) {
                val rad = 3.2f + 3.4f * hash(k, 71)
                val cx = x
                val cy = hz - rad * .7f - .5f
                val poplar = k == 4 || k == 11
                draw(cx - rad - 1f, cx + rad + 1f, hz - 16f, hz + .5f) { px, py, o ->
                    val dx = (px - cx) / (if (poplar) .45f else 1f)
                    val dy = (py - (if (poplar) cy - rad * .9f else cy)) / (if (poplar) 2f else 1f)
                    val dist = sqrt(dx * dx + dy * dy)
                    val leaf = Noise.value(px * .9f, py * .9f, 73 + k)
                    // lit on its top and its sunny side, darker under
                    set(.12f + .16f * Px.smooth(.2f, -.6f, (dx + dy * 1.4f) / rad) + .05f * leaf)
                    rad - dist + .5f - .6f * leaf
                }
                x += rad * .85f + 1f + 2.5f * hash(k, 72)
                k++
            }
        }

        /** A church: its nave with a pitched roof, a tower with a lit clock, the spire on it. */
        fun church(x0: Float) {
            box(x0 - 18f, x0, hz - 7f, hz, .4f)
            draw(x0 - 20f, x0 + 1f, hz - 13f, hz - 7f) { x, y, o ->
                set(.3f)
                val peak = hz - 12f
                val halfW = 10f
                val mid = x0 - 9.5f
                (y - (peak + 5f * abs(x - mid) / halfW)) + .5f
            }
            for (k in 0 until 3) box(
                x0 - 15f + k * 5f,
                x0 - 14f + k * 5f,
                hz - 5.5f,
                hz - 2f,
                .2f,
                .5f,
            )
            draw(x0, x0 + 7f, hz - 22f, hz) { x, _, o ->
                set(if (x < x0 + 2.5f) .52f else .36f)
                1f
            }
            box(x0 + 1.5f, x0 + 2.5f, hz - 20.5f, hz - 18f, .15f)
            box(x0 + 4.5f, x0 + 5.5f, hz - 20.5f, hz - 18f, .15f)
            disc(x0 + 3.5f, hz - 14.5f, 1.6f, .3f, .75f)
            draw(x0 - .5f, x0 + 7.5f, hz - 36f, hz - 22f) { x, y, o ->
                set(if (x < x0 + 3.5f) .5f else .34f)
                val half = 4f * (y - (hz - 35f)) / 13f
                half - abs(x - (x0 + 3.5f)) + .5f
            }
        }

        /** A row of low houses: pitched and flat roofs, chimneys, a few windows lit at night. */
        fun houses(x0: Float) {
            var x = x0
            for (k in 0 until 6) {
                val w = 8f + 6f * hash(k, 81)
                val tall = 5f + 4f * hash(k, 82)
                val pitched = hash(k, 83) < .65f
                val wall = .3f + .15f * hash(k, 84)
                box(x, x + w, hz - tall, hz, wall)
                if (pitched)
                    draw(x - .5f, x + w + .5f, hz - tall - w * .35f - 1f, hz - tall) { px, py, o ->
                        set(.2f)
                        py -
                            (hz -
                                tall -
                                (w * .35f) * (1f - abs(px - x - w / 2f) / (w / 2f + .5f))) + .5f
                    }
                else box(x - .3f, x + w + .3f, hz - tall - .8f, hz - tall, .28f)
                if (hash(k, 85) < .5f)
                    box(x + w * .7f, x + w * .7f + 1.2f, hz - tall - 4f, hz - tall, .22f)
                var wx = x + 1.5f
                var col = 0
                while (wx + 1f < x + w - 1f) {
                    var wy = hz - tall + 1.5f
                    var row = 0
                    while (wy + 1.2f < hz - .5f) {
                        val lit = hash(k * 13 + col, row + 86) < .4f
                        box(wx, wx + 1f, wy, wy + 1.2f, if (lit) 0f else .66f, if (lit) 1f else 0f)
                        wy += 2.8f
                        row++
                    }
                    wx += 2.6f
                    col++
                }
                x += w + .6f + 2f * hash(k, 87)
            }
        }

        /** A lamp on the near bank: its post, an arm and the lamp's head, lit at night. */
        fun lamp(x0: Float) {
            box(x0 - .45f, x0 + .45f, hz - 11f, LAND_H.toFloat(), .04f)
            box(x0, x0 + 2.4f, hz - 11.3f, hz - 10.7f, .04f)
            disc(x0 + 2.4f, hz - 10f, .8f, 0f, 1f)
        }

        /** Averaged down to the strip's own size, the weights divided by the coverage again. */
        fun down(): GlassStrip {
            val px = IntArray(LAND_W * LAND_H)
            for (y in 0 until LAND_H) for (x in 0 until LAND_W) {
                var sa = 0f
                var sr = 0f
                var sg = 0f
                var sb = 0f
                for (dy in 0 until s) for (dx in 0 until s) {
                    val i = (y * s + dy) * hw + x * s + dx
                    sa += a[i]
                    sr += r[i]
                    sg += g[i]
                    sb += b[i]
                }
                val cov = sa / (s * s)
                px[y * LAND_W + x] =
                    if (cov < .004f || y == 0) 0
                    else
                        Px.argbF(cov, Px.clamp01(sr / sa), Px.clamp01(sg / sa), Px.clamp01(sb / sa))
            }
            return GlassStrip(LAND_W, LAND_H, px)
        }
    }

    /**
     * Interior [k] of [INTERIORS], opaque, dim (it is seen through glass that mirrors the day): 0
     * venetian blinds half down, 1 curtains drawn back to the sides, 2 a roller blind a third down
     * over a dim room with a lamp's shade and a picture, 3 a net curtain over the lower part,
     * [BLIND] a roller blind pulled all the way down.
     */
    fun interior(k: Int): GlassStrip {
        val w = INTERIOR_W
        val h = INTERIOR_H
        val px = IntArray(w * h)
        val seed = 600 + k * 17
        for (y in 0 until h) for (x in 0 until w) {
            val u = (x + .5f) / w
            val v = (y + .5f) / h
            // the room: a dark warm grey, darker toward its back and its floor, a little grain
            val grain = (Noise.value(x * .4f, y * .4f, seed) - .5f) * .03f
            var cr = .2f - .06f * v + grain
            var cg = .19f - .06f * v + grain
            var cb = .2f - .05f * v + grain
            // the back wall's far corner and the floor's line
            if (v > .78f) {
                cr -= .04f
                cg -= .045f
                cb -= .05f
            }
            when (k) {
                0 -> {
                    val drop = .48f
                    if (v < drop) {
                        val slat = (y % 4).toFloat()
                        val l = if (slat < 1f) .38f else if (slat < 3f) .56f - .04f * slat else .3f
                        cr = l * .96f
                        cg = l * .93f
                        cb = l * .86f
                    } else if (v < drop + .025f) {
                        cr = .22f
                        cg = .2f
                        cb = .18f
                    }
                }
                1 -> {
                    val reach = .3f + .05f * Noise.value(v * 3f, 1f, seed)
                    val left = u < reach
                    val right = u > 1f - reach
                    if (left || right) {
                        val e = if (left) u / reach else (1f - u) / reach
                        val fold = .5f + .5f * kotlin.math.cos((u * 7f + .3f) * 6.283f)
                        val l = .55f + .3f * fold - .15f * e
                        cr = .46f * l
                        cg = .2f * l
                        cb = .19f * l
                    }
                    // the rail across the top
                    if (y < 3) {
                        cr = .18f
                        cg = .16f
                        cb = .15f
                    }
                }
                2 -> {
                    if (v < .32f) {
                        val l = .62f - .1f * v + (if (y % 9 == 0) -.05f else 0f)
                        cr = l * .97f
                        cg = l * .93f
                        cb = l * .84f
                    } else if (v < .345f) {
                        cr = .3f
                        cg = .28f
                        cb = .25f
                    } else {
                        // a picture on the back wall and a lamp's shade on its stand
                        val pic = u in .14f..0.42f && v in .42f..0.58f
                        if (pic) {
                            cr = .32f
                            cg = .27f
                            cb = .22f
                        }
                        val shade =
                            v in .52f..0.6f && kotlin.math.abs(u - .72f) < .07f + (v - .52f) * .9f
                        if (shade) {
                            cr = .5f
                            cg = .42f
                            cb = .3f
                        }
                        if (v > .6f && v < .9f && kotlin.math.abs(u - .72f) < .02f) {
                            cr = .1f
                            cg = .1f
                            cb = .1f
                        }
                    }
                }
                3 -> {
                    if (v > .36f) {
                        val thread =
                            .04f * kotlin.math.sin(x * 2.1f) +
                                .02f * Noise.value(x * .3f, v * 5f, seed)
                        val sag = Px.smooth(.36f, .4f, v)
                        val l = (.58f + thread) * sag + cr * (1f - sag)
                        cr = l
                        cg = l * .98f
                        cb = l * .94f
                    }
                }
                else -> {
                    // the blind's cloth, a little lighter in the middle where the light comes
                    // through it, its weighted hem, and the dark of the room under that
                    val weave = .02f * Noise.value(x * .5f, y * 2f, seed)
                    val l =
                        (.6f - .05f * v + weave) * (1f - .2f * Px.smooth(.3f, .5f, abs(u - .5f)))
                    cr = l * .97f
                    cg = l * .94f
                    cb = l * .86f
                    if (v in .9f..0.93f) {
                        cr = .36f
                        cg = .33f
                        cb = .3f
                    } else if (v > .93f) {
                        cr = .14f
                        cg = .13f
                        cb = .14f
                    }
                }
            }
            px[y * w + x] = Px.argbF(1f, Px.clamp01(cr), Px.clamp01(cg), Px.clamp01(cb))
        }
        return GlassStrip(w, h, px)
    }

    /**
     * The sun's glint on glass: a small hot core, a soft halo round it and a faint four-pointed
     * star of the lens, white at an alpha, clear at its rim (so a clamped edge adds nothing).
     */
    fun glint(): GlassStrip {
        val s = GLINT
        val px = IntArray(s * s)
        val mid = s / 2f
        for (y in 0 until s) for (x in 0 until s) {
            val dx = (x + .5f - mid) / mid
            val dy = (y + .5f - mid) / mid
            val r = sqrt(dx * dx + dy * dy)
            val core = exp(-(r / .1f) * (r / .1f))
            val halo = .5f / (1f + (r / .16f) * (r / .16f))
            val star =
                .28f *
                    (exp(-(dy / .035f) * (dy / .035f)) + .6f * exp(-(dx / .035f) * (dx / .035f))) *
                    (1f - Px.smooth(0f, .9f, r))
            val a = (core + halo + star) * (1f - Px.smooth(.7f, .98f, r))
            px[y * s + x] = if (a < .003f) 0 else Px.argbF(Px.clamp01(a), 1f, 1f, 1f)
        }
        return GlassStrip(s, s, px)
    }

    private fun hash(a: Int, b: Int): Float {
        var n = a * 374761393 + b * 668265263 + 977 * 1274126177
        n = (n xor (n ushr 13)) * 1274126177
        n = n xor (n ushr 16)
        return (n and 0xFFFFFF) / 16777216f
    }
}

/**
 * Where a pane's reflection comes from in the [GlassSprites] strips, shared by the painter and the
 * JVM harness that looks at it. A facade is one mirror: every pane on it shows the same mirrored
 * world, placed by the point's own position on the facade plus a shift for the whole facade (its
 * own slice of the view and the camera's pan), so the reflection slides as one sheet behind all the
 * panes and the frames only cut it. Nothing is turned or raised per pane: the mirrored horizon lies
 * at [HORIZON_Z] over the pavement across every facade, so a higher pane sees more sky only because
 * it is higher.
 */
internal object GlassMap {
    /** Units of facade one land pixel spans, across and up. */
    const val K_LAND = .65f

    /** Units of facade one sky pixel spans across, and up. */
    const val K_SKY_X = 1.25f
    const val K_SKY_Y = 3.5f

    /** The height over the pavement the mirrored horizon lies at, on every facade. */
    const val HORIZON_Z = 84f

    /**
     * How fast the mirrored land and the sky slide against a facade as the view pans, of the pan:
     * the land is far off, so it keeps more still on screen than the facade; the sky still more.
     */
    const val LAND_SLIDE = .5f
    const val SKY_SLIDE = .8f

    /** The clouds' drift, in sky pixels a second. */
    const val DRIFT = .9f

    /**
     * Where the sun's mirror image falls across the view: this far from the view's middle toward
     * the side the sun stands on, of the way to it.
     */
    const val GLINT_ACROSS = .45f

    /** The land's shift for a facade at [phase] (its own slice of the view) at [buildingX]. */
    fun landShift(phase: Float, buildingX: Float, viewX: Float): Float =
        phase - (viewX - buildingX) * LAND_SLIDE

    /**
     * The sky's shift for a facade at [buildingX]: one sky down the whole street, drifting with
     * [seconds]; [turn] puts a side face on another part of it.
     */
    fun skyShift(buildingX: Float, viewX: Float, seconds: Float, turn: Float = 0f): Float =
        buildingX - viewX * SKY_SLIDE + seconds * DRIFT * K_SKY_X + turn

    /**
     * Where in the mirrored world (units across) the point [fx] across a facade with [shift] is.
     */
    fun worldX(fx: Float, shift: Float): Float = fx + shift

    /**
     * The pane whose left edge is [fx] across its facade and whose top is [top] units over the
     * pavement, on a facade with the shifts [land] and [sky]: into [out] the land strip's column at
     * its left edge and its row at its top, then the sky strip's.
     */
    fun place(fx: Float, top: Float, land: Float, sky: Float, out: FloatArray) {
        out[0] = round(worldX(fx, land) / K_LAND, GlassSprites.LAND_W)
        out[1] = GlassSprites.HORIZON + (HORIZON_Z - top) / K_LAND
        out[2] = round(worldX(fx, sky) / K_SKY_X, GlassSprites.SKY_W)
        out[3] = GlassSprites.SKY_H + (HORIZON_Z - top) / K_SKY_Y
    }

    /** [u] brought into one turn of a strip [w] round, so no offset grows without end. */
    private fun round(u: Float, w: Int): Float = u - kotlin.math.floor(u / w) * w

    /**
     * Where along the street the sun's glint lies, for the light at [lightX] and the view's middle
     * at [viewX]; and how high, for a light whose [down] (its elevation's sine) is this.
     */
    fun glintX(lightX: Float, viewX: Float): Float = viewX + (lightX - viewX) * GLINT_ACROSS

    fun glintZ(down: Float): Float = HORIZON_Z + 16f + 70f * down

    /** A pane's own brightness: within a few per cent of its neighbours'. */
    fun sheen(seed: Int): Float = 1f + .06f * (unit(seed, 1) - .5f)

    /**
     * What is behind the pane [seed]: -1 nothing to see, else a room's blinds or curtains
     * ([GlassSprites.interior]), now and then the blind pulled down ([GlassSprites.BLIND]).
     */
    fun interior(seed: Int): Int {
        val u = unit(seed, 3)
        return when {
            u < .07f -> GlassSprites.BLIND
            u < .55f -> -1
            else -> (unit(seed, 4) * GlassSprites.BLIND).toInt()
        }
    }

    private fun unit(seed: Int, salt: Int): Float {
        var n = seed * 668265263 + salt * 374761393 + 1013
        n = (n xor (n ushr 13)) * 1274126177
        n = n xor (n ushr 16)
        return (n and 0xFFFFFF) / 16777216f
    }

    /**
     * The colours the strips' weights stand for at [sky]: into [out], twelve for the sky strip (its
     * red, green and blue weights' colours, then the rest's: the clouds' lit tops and shade, the
     * horizon, the sky overhead) and twelve for the land's (the haze on far things, the lights, the
     * water, the near dark). As mirrored by dark glass: a little darker and deeper than the things
     * themselves; at night the far hills moonlit, the lights warm.
     */
    fun colours(sky: SkyColors, out: FloatArray) {
        val inside = SceneInk.GlassNight
        val dark = sky.dark
        val zenith = lerp(lerp(sky.sky[0], sky.sky[1], .55f), inside, .3f)
        val horizon = lerp(lerp(sky.sky[2], sky.sky[1], .2f), inside, .14f)
        val cloudLit = lerp(lerp(sky.cloudLit, sky.cloudBody, .45f), inside, .16f + .1f * dark)
        val cloudShade = lerp(lerp(sky.cloudShade, sky.cloudBody, .25f), inside, .22f)
        val near = lerp(lerp(sky.far[2], sky.ground[1], .6f), inside, .45f + .2f * dark)
        val haze = lerp(lerp(lerp(sky.far[0], horizon, .5f), MoonHaze, .25f * dark), inside, .12f)
        val lights = lerp(lerp(haze, horizon, .5f), SceneInk.WindowLit, Px.smooth(.15f, .8f, dark))
        val water = lerp(lerp(horizon, zenith, .35f), inside, .15f + .2f * dark)
        var i = 0
        for (c in arrayOf(cloudLit, cloudShade, horizon, zenith, haze, lights, water, near)) {
            out[i++] = c.red
            out[i++] = c.green
            out[i++] = c.blue
        }
    }

    /** The moon's light on far hills at night. */
    private val MoonHaze = Color(0xFF7D8BB0)
}
