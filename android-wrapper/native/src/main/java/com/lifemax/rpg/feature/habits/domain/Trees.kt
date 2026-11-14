package com.lifemax.rpg.feature.habits.domain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * The street's trees, with no drawing in it: a handful of shapes (round, tall, pine), chosen by a seed and the
 * same every time, as foliage lobes (or a pine's outline) in dp at full size, standing on the pavement line at
 * x 0 with up as a negative y. The painter turns each shape into paths once.
 */

enum class TreeKind {
    ROUND,
    TALL,
    PINE,
}

/**
 * One tree. [lobes] are the foliage's circles, three numbers each (across, up as a negative,
 * radius); the edge bumps that make the leaves ragged follow the same layout in [bumps]. A pine has
 * an [outline] (across, up pairs, round the whole silhouette) instead. [halfWidth] and [height]
 * bound the whole of it, [crown] is where the trunk ends and the foliage begins, [warmth] (0 to
 * .35) how far its green leans to olive.
 */
class TreeShape(
    val kind: TreeKind,
    val lobes: FloatArray,
    val bumps: FloatArray,
    val outline: FloatArray,
    val halfWidth: Float,
    val height: Float,
    val crown: Float,
    val warmth: Float,
)

object Trees {
    const val VARIANTS = 8

    /** The variant a tree with [seed] is, 0 until [VARIANTS]: four round, two tall, two pines. */
    fun variant(seed: Int): Int =
        (Ambient.hash(seed, 0, 90) * VARIANTS).toInt().coerceIn(0, VARIANTS - 1)

    val shapes: List<TreeShape> by lazy { List(VARIANTS) { build(it) } }

    fun shape(seed: Int): TreeShape = shapes[variant(seed)]

    private fun kindOf(v: Int) =
        when (v) {
            in 0..3 -> TreeKind.ROUND
            4,
            5 -> TreeKind.TALL
            else -> TreeKind.PINE
        }

    private fun build(v: Int): TreeShape {
        fun j(k: Int) = Ambient.hash(v, k, 91) - .5f
        val kind = kindOf(v)
        val warmth = .35f * Ambient.hash(v, 7, 92)
        if (kind == TreeKind.PINE) return pine(v, warmth)
        val lobes =
            if (kind == TreeKind.ROUND)
                floatArrayOf(
                    j(0) * 3f,
                    -50f + j(1) * 2f,
                    15f + j(2) * 2f,
                    -11f + j(3) * 3f,
                    -41f + j(4) * 2f,
                    11f + j(5) * 2f,
                    11f + j(6) * 3f,
                    -42f + j(7) * 2f,
                    12f + j(8) * 2f,
                    -6f + j(9) * 3f,
                    -58f + j(10) * 2f,
                    10f + j(11) * 2f,
                    7f + j(12) * 3f,
                    -57f + j(13) * 2f,
                    10.5f + j(14) * 2f,
                )
            else
                floatArrayOf(
                    j(0) * 2f,
                    -57f + j(1) * 2f,
                    10f,
                    -5f + j(2) * 2f,
                    -47f + j(3) * 2f,
                    10.5f,
                    5f + j(4) * 2f,
                    -46f + j(5) * 2f,
                    11f,
                    j(6) * 2f,
                    -36f + j(7) * 2f,
                    12f,
                    -3f + j(8) * 2f,
                    -55f + j(9) * 2f,
                    8f,
                )
        // ragged edges: three small bumps round each lobe, at angles of the tree's own
        val n = lobes.size / 3
        val bumps = FloatArray(n * 9)
        for (k in 0 until n) {
            for (b in 0 until 3) {
                val a = Ambient.hash(v, k * 3 + b, 93) * 2f * PI.toFloat()
                val r = lobes[k * 3 + 2]
                val at = (k * 3 + b) * 3
                bumps[at] = lobes[k * 3] + cos(a) * r * .92f
                bumps[at + 1] = lobes[k * 3 + 1] + sin(a) * r * .92f
                bumps[at + 2] = 3.2f + 2f * Ambient.hash(v, k * 3 + b, 94)
            }
        }
        var half = 0f
        var top = 0f
        for (set in arrayOf(lobes, bumps)) {
            var m = 0
            while (m < set.size) {
                half = max(half, abs(set[m]) + set[m + 2])
                top = min(top, set[m + 1] - set[m + 2])
                m += 3
            }
        }
        // fitted to the room a tree is given: at most 21.5 wide each way and 64 tall
        val fit = min(1f, min(21.5f / half, 64f / -top))
        if (fit < 1f) {
            for (set in arrayOf(lobes, bumps)) for (m in set.indices) set[m] *= fit
            half *= fit
            top *= fit
        }
        return TreeShape(
            kind,
            lobes,
            bumps,
            FloatArray(0),
            half,
            -top,
            if (kind == TreeKind.ROUND) -32f else -28f,
            warmth,
        )
    }

    /** Four ragged tiers, each narrower and higher than the one under it. */
    private fun pine(v: Int, warmth: Float): TreeShape {
        fun j(k: Int) = (Ambient.hash(v, k, 95) - .5f) * 1.6f
        val tiers = 4
        val right = ArrayList<Float>()
        val left = ArrayList<Float>()
        var widest = 0f
        for (t in 0 until tiers) {
            val apex = -64f + t * 9f
            val base = apex + 20f
            val w = 7f + t * 4.4f
            widest = w
            right += w * .5f + j(t)
            right += apex + 10f
            right += w
            right += base
            right += w * .42f
            right += base - 1.5f
            left += -w * .42f
            left += base - 1.5f
            left += -w
            left += base
            left += -w * .5f + j(t + 4)
            left += apex + 10f
        }
        val outline = ArrayList<Float>()
        outline += 0f
        outline += -64f
        outline.addAll(right)
        // across the foot of the lowest tier, then back up the left side, tier by tier
        var i = left.size
        val groups = tiers
        for (g in groups - 1 downTo 0) {
            i = g * 6
            outline += left[i]
            outline += left[i + 1]
            outline += left[i + 2]
            outline += left[i + 3]
            outline += left[i + 4]
            outline += left[i + 5]
        }
        return TreeShape(
            TreeKind.PINE,
            FloatArray(0),
            FloatArray(0),
            outline.toFloatArray(),
            widest + 1f,
            65f,
            -24f,
            warmth * .5f,
        )
    }
}
