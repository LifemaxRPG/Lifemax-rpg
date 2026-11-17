package com.lifemax.rpg.feature.habits.presentation.scene

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The small things the seasons let fall, painted once: autumn leaves (four shapes, maple, oak, elm
 * and aspen, in the amber and the red palettes: toothed edges, veins, a fold and spots of rot),
 * blossom petals (pink and white) and a snowflake (a soft white speck), all small ARGB squares
 * drawn turned and scaled as they fall. Pure Kotlin, deterministic; [SceneSprites] turns them into
 * images.
 */
internal object SeasonSprites {
    /** A leaf's picture is [LEAF] pixels square, [LEAF_PPU] of them to a street unit. */
    const val LEAF = 32
    const val LEAF_PPU = 6f
    const val LEAF_SHAPES = 4

    /** Sub-samples a leaf's pixel is tried at, across and down, for smooth edges. */
    private const val SAMPLES = 4

    /** Where the leaf's stalk ends and its blade begins (unit square, x along the spine). */
    private const val STEM_TO = -.62f

    /** The maple's lobes: the stem's end they fan from, their angles from the spine and lengths. */
    private const val MAPLE_O = .3f
    private val MAPLE_ANGLE = floatArrayOf(0f, .95f, -.95f, 1.85f, -1.85f)
    private val MAPLE_LEN = floatArrayOf(.78f, .62f, .62f, .38f, .38f)

    const val PETAL = 10
    const val PETAL_PPU = 4f

    const val FLAKE = 12
    const val FLAKE_PPU = 5f

    /** The eight leaves: shape 0 to 3, amber then red (index shape * 2 + red). */
    fun leaves(): Array<IntArray> = Array(LEAF_SHAPES * 2) { leaf(it / 2, it % 2 == 1) }

    /** The two petals: pink, then white. */
    fun petals(): Array<IntArray> = arrayOf(petal(false), petal(true))

    fun bytes(): Int = LEAF_SHAPES * 2 * LEAF * LEAF * 4 + 2 * PETAL * PETAL * 4 + FLAKE * FLAKE * 4

    /**
     * How far inside leaf [shape] the point ([x], [y]) is, in the unit square (the spine along x,
     * the tip to the right, the stem at the left): positive inside, about the distance to the edge.
     */
    private fun margin(shape: Int, x: Float, y: Float): Float =
        when (shape) {
            // a maple's: five pointed lobes fanning from the stem's end, the edge toothed
            0 -> {
                val dx = x + MAPLE_O
                val r = sqrt(dx * dx + y * y)
                val th = atan2(y, dx)
                var reach = .44f
                for (k in MAPLE_ANGLE.indices) {
                    val d = (th - MAPLE_ANGLE[k]) / .5f
                    reach = max(reach, .44f + MAPLE_LEN[k] * exp(-d * d * 1.3f))
                }
                reach += .035f * cos(th * 30f)
                if (abs(th) > 2.55f) reach = min(reach, .22f)
                (reach - r) * .8f
            }
            // an oak's: long, with rounded lobes and the sinuses between them
            1 ->
                run {
                    if (abs(x) > .92f) return@run -1f
                    val u = x / .92f
                    val core = sqrt(max(0f, 1f - u * u))
                    val lobes = .55f + .45f * abs(cos((u + 1f) * 5.2f))
                    .44f * core * lobes * (1f - .2f * max(0f, u)) - abs(y)
                }
            // an elm's: a pointed oval, a little lopsided, its edge finely toothed
            2 ->
                run {
                    if (abs(x) > .9f) return@run -1f
                    val u = x / .9f
                    val core = sqrt(max(0f, 1f - u * u))
                    val lop = if (y > 0f) 1f else .9f
                    val teeth = 1f + .07f * abs(sin(u * 24f))
                    .42f * core * (1f - .45f * max(0f, u)) * lop * teeth - abs(y)
                }
            // a round one (aspen, birch), a touch pointed, finely toothed
            else -> {
                val dx = x + .05f
                val r = sqrt(dx * dx + y * y)
                val th = atan2(y, dx)
                val reach = .62f + .1f * cos(th) * max(0f, cos(th)) + .02f * cos(th * 26f)
                reach - r
            }
        }

    /** How much of a vein is at ([x], [y]) on leaf [shape] (0 none to 1 on one). */
    private fun vein(shape: Int, x: Float, y: Float): Float {
        if (shape == 0) {
            // from the stem's end out along each lobe
            val dx = x + MAPLE_O
            val r = sqrt(dx * dx + y * y)
            val th = atan2(y, dx)
            var v = 0f
            for (k in MAPLE_ANGLE.indices) {
                val d = abs(th - MAPLE_ANGLE[k]) * r
                v = max(v, Px.smooth(.05f, .015f, d) * (1f - .5f * r))
            }
            return v
        }
        // the midrib, and side veins sweeping out towards the tip
        val mid = Px.smooth(.05f, .015f, abs(y)) * Px.smooth(1f, .6f, x)
        val along = x - 1.1f * abs(y)
        val f = along * (if (shape == 1) 4.2f else 6f)
        val side = Px.smooth(.14f, .04f, abs(f - Math.round(f))) * Px.smooth(.0f, .06f, abs(y))
        return max(mid, .7f * side)
    }

    private fun leaf(shape: Int, red: Boolean): IntArray {
        val out = IntArray(LEAF * LEAF)
        val seed = 810 + shape * 3 + (if (red) 1 else 0)
        // each leaf its own mix: the amber ones keep a little green-gold by their veins, the red
        // ones run from crimson to orange
        val dark: FloatArray
        val light: FloatArray
        val vein: FloatArray
        if (red) {
            dark = floatArrayOf(.45f, .07f, .05f)
            light =
                if (shape % 2 == 0) floatArrayOf(.93f, .3f, .12f)
                else floatArrayOf(.96f, .46f, .14f)
            vein = floatArrayOf(.98f, .62f, .3f)
        } else {
            dark = floatArrayOf(.55f, .28f, .05f)
            light =
                when (shape) {
                    0 -> floatArrayOf(.98f, .66f, .14f)
                    1 -> floatArrayOf(.8f, .55f, .2f)
                    2 -> floatArrayOf(.93f, .78f, .24f)
                    else -> floatArrayOf(1f, .84f, .3f)
                }
            vein = floatArrayOf(.86f, .82f, .38f)
        }
        val stem = floatArrayOf(.38f, .24f, .12f)
        val n = SAMPLES
        val half = (LEAF - 1) / 2f
        for (j in 0 until LEAF) for (i in 0 until LEAF) {
            var cov = 0f
            var r = 0f
            var g = 0f
            var b = 0f
            for (sj in 0 until n) for (si in 0 until n) {
                val x = (i + (si + .5f) / n - .5f - half) / (LEAF * .46f)
                val y = (j + (sj + .5f) / n - .5f - half) / (LEAF * .46f)
                val m = margin(shape, x, y)
                // the stem: a short stalk out of the leaf's base
                val stemAt =
                    if (x < STEM_TO && x > -1.02f)
                        Px.smooth(.065f, .035f, abs(y + .08f * (x - STEM_TO)))
                    else 0f
                if (m <= 0f && stemAt <= 0f) continue
                if (m <= 0f) {
                    cov += stemAt
                    r += stem[0] * stemAt
                    g += stem[1] * stemAt
                    b += stem[2] * stemAt
                    continue
                }
                // lit from the upper left; folded along its spine, the lower half turned away
                var t = .6f - .18f * x - .22f * y
                if (y > 0f) t -= .12f
                t += (Noise.fbm(x * 5f, y * 5f, seed, 3) - .5f) * .35f
                // the rim curls and darkens
                t -= .3f * Px.smooth(.08f, 0f, m)
                t = t.coerceIn(0f, 1f)
                var cr = dark[0] + (light[0] - dark[0]) * t
                var cg = dark[1] + (light[1] - dark[1]) * t
                var cb = dark[2] + (light[2] - dark[2]) * t
                val v = vein(shape, x, y)
                cr += (vein[0] - cr) * .8f * v
                cg += (vein[1] - cg) * .8f * v
                cb += (vein[2] - cb) * .8f * v
                // spots where it has begun to rot
                val spot = Px.smooth(.77f, .85f, Noise.fbm(x * 7f, y * 7f, seed + 40, 2))
                cr *= 1f - .3f * spot
                cg *= 1f - .4f * spot
                cb *= 1f - .4f * spot
                cov += 1f
                r += cr
                g += cg
                b += cb
            }
            if (cov <= 0f) continue
            val a = cov / (n * n)
            out[j * LEAF + i] = Px.argbF(a.coerceAtMost(1f), r / cov, g / cov, b / cov)
        }
        return out
    }

    private fun petal(white: Boolean): IntArray {
        val out = IntArray(PETAL * PETAL)
        val mid = (PETAL - 1) / 2f
        for (j in 0 until PETAL) for (i in 0 until PETAL) {
            val u = (i - mid) / (PETAL * .42f)
            val v = (j - mid) / (PETAL * .42f)
            if (abs(u) > 1f) continue
            // an oval, narrower at its root
            val hw = .5f * sqrt(1f - u * u) * (1f - .25f * max(0f, -u))
            val edge = (hw - abs(v)) * PETAL * .42f
            val a = Px.smooth(-.6f, .6f, edge)
            if (a <= 0f) continue
            val t = (.6f - .25f * v - .15f * u).coerceIn(0f, 1f)
            // pink deepens at the root; white blushes yellow there
            out[j * PETAL + i] =
                if (white) Px.argbF(a, .98f, .95f - .08f * max(0f, -u), .9f - .2f * max(0f, -u))
                else Px.argbF(a, .95f + .05f * t, .6f + .3f * t, .72f + .22f * t)
        }
        return out
    }

    /** A snowflake: a soft white speck with a brighter heart (no arms: it is far too small). */
    fun flake(): IntArray {
        val out = IntArray(FLAKE * FLAKE)
        val mid = (FLAKE - 1) / 2f
        for (j in 0 until FLAKE) for (i in 0 until FLAKE) {
            val dx = (i - mid) / (FLAKE / 2f)
            val dy = (j - mid) / (FLAKE / 2f)
            val r2 = dx * dx + dy * dy
            val a = exp(-r2 * 4.5f) * .95f + exp(-r2 * 18f) * .4f
            val border = minOf(i, FLAKE - 1 - i, j, FLAKE - 1 - j)
            val k = Px.clamp01(a) * Px.smooth(0f, 1.5f, border.toFloat())
            if (k > .003f) out[j * FLAKE + i] = Px.argbF(k, 1f, 1f, 1f)
        }
        return out
    }
}
