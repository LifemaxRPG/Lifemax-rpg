package com.lifemax.rpg.feature.habits.presentation.scene

import com.lifemax.rpg.feature.habits.domain.TreeKind
import com.lifemax.rpg.feature.habits.domain.TreeShape
import com.lifemax.rpg.feature.habits.domain.Trees
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** The three looks of a tree: by day, at dusk and at night. */
internal enum class TreeLook {
    DAY,
    DUSK,
    NIGHT,
}

/**
 * One tree variant, painted once in each [TreeLook]: [pixels] hold an ARGB image of [w] by [h]
 * pixels, [ppu] pixels to a street unit, with the foot of the trunk at ([footX], [footY]) pixels.
 * The sun (or the moon) is on the left; the sprite is drawn mirrored for a light from the right.
 * The soft contact shadow is in it.
 */
internal class TreeSprite(
    val w: Int,
    val h: Int,
    val ppu: Float,
    val footX: Float,
    val footY: Float,
    val pixels: Array<IntArray>,
)

/**
 * The tree sprite generator. A tree is a few masses of leaves (a pine's tiers) and a tapering
 * trunk. Each mass is lit as a lump: its surface normal, roughened by noise into smaller clusters
 * of leaves, is lit from the upper left, and each mass casts a soft shadow on the ones behind it,
 * so the crown has depth in three or four values instead of a flat blob. Leaf texture is fine
 * noise, the edge is ragged and feathered, the lit rim catches a highlight, the foot has a soft
 * shadow. At night the moonlight is dim and blue and the street lamps rim the side facing the
 * street warm. Everything is a function of the variant, so the same tree is painted every time.
 */
internal object TreeSprites {
    const val PPU = 2.5f

    /** Space round the tree, in units. */
    private const val PAD = 4f

    private const val LX = -.5f
    private const val LY = -.62f
    private const val LZ = .6f

    /** The planes a tree is painted into before it is coloured. */
    private class Planes(val w: Int, val h: Int) {
        val alpha = FloatArray(w * h)

        /** Tone, 0 (deep shade) to 1 (full light). */
        val tone = FloatArray(w * h)

        /** The warm street lamps' light on the side away from the sun, 0 to 1. */
        val lamp = FloatArray(w * h)

        /** 1 for bark, 0 for leaves. */
        val bark = FloatArray(w * h)
        val shadow = FloatArray(w * h)
    }

    fun generate(variant: Int): TreeSprite {
        val shape = Trees.shapes[variant]
        val w = ((shape.halfWidth + PAD) * 2f * PPU).toInt() + 1
        val h = ((shape.height + PAD + 4f) * PPU).toInt() + 1
        val footX = w / 2f
        val footY = h - 4f * PPU
        val p = Planes(w, h)
        shadow(p, shape, footX, footY)
        trunk(p, shape, variant, footX, footY)
        if (shape.kind == TreeKind.PINE) tiers(p, shape, variant, footX, footY)
        else masses(p, shape, variant, footX, footY)
        val pixels = Array(TreeLook.entries.size) { colour(p, shape, TreeLook.entries[it]) }
        return TreeSprite(w, h, PPU, footX, footY, pixels)
    }

    fun bytes(): Int =
        (0 until Trees.VARIANTS).sumOf {
            val s = Trees.shapes[it]
            val w = ((s.halfWidth + PAD) * 2f * PPU).toInt() + 1
            val h = ((s.height + PAD + 4f) * PPU).toInt() + 1
            w * h * 4 * TreeLook.entries.size
        }

    // ---- painting the planes

    private fun shadow(p: Planes, shape: TreeShape, footX: Float, footY: Float) {
        val rx = (shape.halfWidth * .78f + 2f) * PPU
        val ry = (if (shape.kind == TreeKind.SHRUB) 2.4f else 3.1f) * PPU
        val cx = footX + 3.2f * PPU
        val cy = footY + .5f * PPU
        for (y in 0 until p.h) for (x in 0 until p.w) {
            val dx = (x - cx) / rx
            val dy = (y - cy) / ry
            val r = sqrt(dx * dx + dy * dy)
            if (r < 1f) {
                val s = 1f - r
                p.shadow[y * p.w + x] = s * s * (3f - 2f * s) * .46f
            }
        }
    }

    private fun trunk(p: Planes, shape: TreeShape, variant: Int, footX: Float, footY: Float) {
        val top = if (shape.kind == TreeKind.PINE) -26f else shape.crown - 4f
        val baseHalf = if (shape.kind == TreeKind.SHRUB) 1.1f else 2.5f
        val topHalf = if (shape.kind == TreeKind.PINE) 1.3f else 1.45f
        val lean = (com.lifemax.rpg.feature.habits.domain.Ambient.hash(variant, 3, 130) - .5f) * 3f
        for (y in 0 until p.h) {
            val u = (footY - y) / PPU // units above the ground
            if (u < -.5f || u > -top) continue
            val t = (u / -top).coerceIn(0f, 1f)
            val flare = 1f + .5f * Px.smooth(.14f, 0f, t)
            val half = (baseHalf + (topHalf - baseHalf) * Px.smooth(0f, 1f, t)) * flare * PPU
            val cx = footX + lean * t * t * PPU
            val lo = (cx - half - 1f).toInt()
            val hi = (cx + half + 1f).toInt() + 1
            for (x in max(0, lo)..min(p.w - 1, hi)) {
                val nx = (x - cx) / half
                val edge = 1f - abs(nx)
                val cover = Px.smooth(0f, .18f, edge)
                if (cover <= 0f) continue
                val nz = sqrt(max(0f, 1f - nx * nx))
                val lam = nx * LX + nz * LZ + (-.3f) * LY * .2f
                val streak = Noise.fbm(x * .55f, y * .09f, 140 + variant, 3)
                val v = Px.smooth(-.25f, .85f, lam) * .8f + (streak - .5f) * .35f + .05f
                val i = y * p.w + x
                p.alpha[i] = max(p.alpha[i], cover)
                p.tone[i] = v.coerceIn(0f, 1f)
                p.bark[i] = 1f
                p.lamp[i] = Px.smooth(.3f, .95f, nx) * .8f
            }
        }
        // a limb into each side of the crown (not for a pine, whose tiers hide the trunk)
        if (shape.kind != TreeKind.PINE && shape.kind != TreeKind.SHRUB) {
            val y0 = footY + (shape.crown + 2f) * PPU
            for (s in intArrayOf(-1, 1)) {
                val len =
                    min(shape.halfWidth * .6f, 6.5f) +
                        2f * com.lifemax.rpg.feature.habits.domain.Ambient.hash(variant, s, 131)
                limb(
                    p,
                    footX + lean * PPU,
                    y0,
                    footX + s * len * PPU,
                    y0 - 9f * PPU,
                    1.3f * PPU,
                    .7f * PPU,
                )
            }
        }
    }

    private fun limb(p: Planes, x0: Float, y0: Float, x1: Float, y1: Float, r0: Float, r1: Float) {
        val steps = 28
        for (k in 0..steps) {
            val t = k / steps.toFloat()
            val cx = x0 + (x1 - x0) * t
            val cy = y0 + (y1 - y0) * t
            val r = r0 + (r1 - r0) * t
            for (y in (cy - r - 1).toInt()..(cy + r + 1).toInt()) for (x in
                (cx - r - 1).toInt()..(cx + r + 1).toInt()) {
                if (x < 0 || y < 0 || x >= p.w || y >= p.h) continue
                val d = sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy)) / r
                val cover = Px.smooth(1f, .6f, d)
                if (cover <= 0f) continue
                val i = y * p.w + x
                if (cover > p.alpha[i]) {
                    p.alpha[i] = cover
                    p.tone[i] = (.35f + .25f * (-(x - cx) / r)).coerceIn(0f, 1f)
                    p.bark[i] = 1f
                }
            }
        }
    }

    /** Broadleaf, tall and shrub trees: masses of leaves, lowest first, each lit as a lump. */
    private fun masses(p: Planes, shape: TreeShape, variant: Int, footX: Float, footY: Float) {
        val c = shape.clumps
        // paint the lowest first; the higher masses sit in front, nearest the light
        val order = (0 until c.size / 4).sortedByDescending { c[it * 4 + 1] }
        for (m in order) {
            val cx = footX + c[m * 4] * PPU
            val cy = footY + c[m * 4 + 1] * PPU
            val rx = c[m * 4 + 2] * PPU
            val ry = c[m * 4 + 3] * PPU
            val seed = 150 + variant * 17 + m * 3
            cast(p, cx, cy, rx, ry, seed)
            mass(p, cx, cy, rx, ry, seed)
        }
    }

    private fun maskAt(
        x: Float,
        y: Float,
        cx: Float,
        cy: Float,
        rx: Float,
        ry: Float,
        seed: Int,
    ): Float {
        val dx = (x - cx) / rx
        val dy = (y - cy) / ry
        val r = sqrt(dx * dx + dy * dy)
        // the edge of a mass of leaves is clustered and ragged
        val rag =
            (Noise.fbm(x * .16f, y * .16f, seed, 3) - .5f) * .5f +
                (Noise.fbm(x * .45f, y * .45f, seed + 1, 2) - .5f) * .16f
        return 1f - r + rag
    }

    /** The mass's shadow on what is already painted behind it: soft, thrown away from the light. */
    private fun cast(p: Planes, cx: Float, cy: Float, rx: Float, ry: Float, seed: Int) {
        val ox = 3.2f * PPU
        val oy = 4.2f * PPU
        for (y in max(0, (cy - ry - 3f * PPU).toInt())..min(p.h - 1, (cy + ry + oy + 6f).toInt())) {
            for (x in
                max(0, (cx - rx - 3f * PPU).toInt())..min(p.w - 1, (cx + rx + ox + 6f).toInt())) {
                val i = y * p.w + x
                if (p.alpha[i] <= 0f) continue
                val m = maskAt(x - ox, y - oy, cx, cy, rx, ry, seed)
                val s = Px.smooth(.05f, .42f, m) * .5f
                if (s > 0f) {
                    p.tone[i] *= 1f - s
                    p.lamp[i] *= 1f - s * .8f
                }
            }
        }
    }

    private fun mass(p: Planes, cx: Float, cy: Float, rx: Float, ry: Float, seed: Int) {
        for (y in
            max(0, (cy - ry - 4f * PPU).toInt())..min(p.h - 1, (cy + ry + 4f * PPU).toInt())) {
            for (x in
                max(0, (cx - rx - 4f * PPU).toInt())..min(p.w - 1, (cx + rx + 4f * PPU).toInt())) {
                val m = maskAt(x.toFloat(), y.toFloat(), cx, cy, rx, ry, seed)
                val cover = Px.smooth(0f, .16f, m)
                if (cover <= 0f) continue
                val dx = (x - cx) / rx
                val dy = (y - cy) / ry
                val r2 = min(.98f, dx * dx + dy * dy)
                // the surface of the lump, roughened by noise into smaller clusters of leaves
                val bx = (Noise.fbm(x * .17f, y * .17f, seed + 2, 3) - .5f) * .9f
                val by = (Noise.fbm(x * .17f + 9f, y * .17f + 4f, seed + 3, 3) - .5f) * .9f
                var nx = dx * .85f + bx
                var ny = dy * .85f + by
                val nl = sqrt(nx * nx + ny * ny)
                if (nl > .98f) {
                    nx *= .98f / nl
                    ny *= .98f / nl
                }
                val nz = sqrt(max(0f, 1f - nx * nx - ny * ny))
                val lam = nx * LX + ny * LY + nz * LZ
                var v = Px.smooth(-.45f, .8f, lam)
                // the crowns of the clusters catch a little more light, the creases between fall
                // dark
                v += (Noise.fbm(x * .5f, y * .5f, seed + 4, 3) - .5f) * .26f
                // a shade under the mass, lit rim on the sun's side
                v *= .84f + .16f * Px.smooth(.9f, -.4f, dy)
                if (dx < -.45f && dy < .1f)
                    v += .16f * Px.smooth(.55f, .98f, sqrt(r2)) * Px.smooth(-.45f, -.8f, dx)
                val lampSide =
                    Px.smooth(.25f, .95f, dx * .8f - dy * .1f + (1f - nz) * .3f) *
                        Px.smooth(.35f, .9f, sqrt(r2) + bx * .2f)
                val i = y * p.w + x
                p.tone[i] = Px.mix(p.tone[i], v.coerceIn(0f, 1f), cover)
                p.lamp[i] = Px.mix(p.lamp[i], lampSide, cover)
                p.bark[i] = Px.mix(p.bark[i], 0f, cover)
                p.alpha[i] = p.alpha[i] + (1f - p.alpha[i]) * cover
            }
        }
    }

    /** A pine: tiers from the lowest up, each a ragged triangle, each shading the one below it. */
    private fun tiers(p: Planes, shape: TreeShape, variant: Int, footX: Float, footY: Float) {
        val c = shape.clumps
        var m = 0
        while (m < c.size / 4) {
            val seed = 170 + variant * 13 + m
            val cx = footX + c[m * 4] * PPU
            val baseY = footY + c[m * 4 + 1] * PPU
            val hw = c[m * 4 + 2] * PPU
            val ht = c[m * 4 + 3] * PPU
            tierShadow(p, cx, baseY, hw, ht, seed)
            tier(p, cx, baseY, hw, ht, seed)
            m++
        }
    }

    private fun tierMask(
        x: Float,
        y: Float,
        cx: Float,
        baseY: Float,
        hw: Float,
        ht: Float,
        seed: Int,
    ): Float {
        val t = (baseY - y) / ht // 0 at the foot, 1 at the apex
        val half = hw * (1f - t)
        val dx = abs(x - cx)
        val rag = (Noise.fbm(x * .2f, y * .2f, seed, 3) - .5f) * 3.4f * PPU * .5f
        // the skirt hangs in drooping scallops
        val skirt = (Noise.fbm(x * .1f, 4f, seed + 1, 2) - .5f) * 2.2f * PPU
        val side = (half - dx + rag) / (hw * .5f)
        val foot = (baseY + skirt - y) / (ht * .22f) + 1f
        val head = (1f - t) * 4f
        return min(side, min(foot, head))
    }

    private fun tierShadow(p: Planes, cx: Float, baseY: Float, hw: Float, ht: Float, seed: Int) {
        val ox = 1.5f * PPU
        val oy = 3.4f * PPU
        for (y in max(0, (baseY - ht - 2f).toInt())..min(p.h - 1, (baseY + oy + 4f).toInt())) {
            for (x in max(0, (cx - hw - 4f).toInt())..min(p.w - 1, (cx + hw + ox + 4f).toInt())) {
                val i = y * p.w + x
                if (p.alpha[i] <= 0f) continue
                val m = tierMask(x - ox, y - oy, cx, baseY, hw, ht, seed)
                val s = Px.smooth(0f, 1.2f, m) * .5f
                if (s > 0f) {
                    p.tone[i] *= 1f - s
                    p.lamp[i] *= 1f - s * .8f
                }
            }
        }
    }

    private fun tier(p: Planes, cx: Float, baseY: Float, hw: Float, ht: Float, seed: Int) {
        for (y in max(0, (baseY - ht - 3f).toInt())..min(p.h - 1, (baseY + 4f * PPU).toInt())) {
            for (x in max(0, (cx - hw - 4f).toInt())..min(p.w - 1, (cx + hw + 4f).toInt())) {
                val m = tierMask(x.toFloat(), y.toFloat(), cx, baseY, hw, ht, seed)
                val cover = Px.smooth(0f, .55f, m)
                if (cover <= 0f) continue
                val t = ((baseY - y) / ht).coerceIn(0f, 1f)
                val half = max(1f, hw * (1f - t))
                val dx = (x - cx) / half
                // a cone: lit on the sun's side, with boughs breaking it into clusters
                val bx = (Noise.fbm(x * .2f, y * .24f, seed + 2, 3) - .5f) * .8f
                val nx = (dx * .9f + bx).coerceIn(-.98f, .98f)
                val nz = sqrt(max(0f, 1f - nx * nx))
                val lam = nx * LX + nz * (LZ + .1f) - .3f * LY * (1f - t) * .3f
                var v = Px.smooth(-.4f, .8f, lam) * .82f
                // boughs: horizontal ridges, light on top, dark underneath
                val ridge = Noise.fbm(x * .06f, y * .3f, seed + 3, 3)
                v += (ridge - .5f) * .34f
                v += (Noise.fbm(x * .55f, y * .55f, seed + 4, 3) - .5f) * .24f
                // under each tier's skirt it is darker
                v *= .72f + .28f * Px.smooth(0f, .5f, t + .12f)
                val lampSide = Px.smooth(.2f, .95f, dx) * Px.smooth(.25f, .8f, 1f - t * .5f)
                val i = y * p.w + x
                p.tone[i] = Px.mix(p.tone[i], v.coerceIn(0f, 1f), cover)
                p.lamp[i] = Px.mix(p.lamp[i], lampSide, cover)
                p.bark[i] = Px.mix(p.bark[i], 0f, cover)
                p.alpha[i] = p.alpha[i] + (1f - p.alpha[i]) * cover
            }
        }
    }

    // ---- colouring

    private class Ramp(
        val deep: Int,
        val shade: Int,
        val mid: Int,
        val light: Int,
        val glint: Int,
        val barkDark: Int,
        val barkLit: Int,
        val lamp: Int,
        val lampAmount: Float,
        val shadow: Int,
    )

    private val DAY =
        Ramp(
            0xFF1B4638.toInt(),
            0xFF2A6548.toInt(),
            0xFF418959.toInt(),
            0xFF86B96C.toInt(),
            0xFFD2E69A.toInt(),
            0xFF4A3524.toInt(),
            0xFF86644A.toInt(),
            0xFFFFE0A0.toInt(),
            0f,
            0xFF15202B.toInt(),
        )
    private val DUSK =
        Ramp(
            0xFF2A2D4A.toInt(),
            0xFF3F4A5A.toInt(),
            0xFF5C7A58.toInt(),
            0xFFDDA672.toInt(),
            0xFFFFD09A.toInt(),
            0xFF3A2B3A.toInt(),
            0xFF9A6C58.toInt(),
            0xFFFFC27A.toInt(),
            .22f,
            0xFF1E1830.toInt(),
        )
    private val NIGHT =
        Ramp(
            0xFF050E16.toInt(),
            0xFF0B1D26.toInt(),
            0xFF15343A.toInt(),
            0xFF3E6A78.toInt(),
            0xFF8EB0C4.toInt(),
            0xFF140F1A.toInt(),
            0xFF3A3448.toInt(),
            0xFFFFC070.toInt(),
            .62f,
            0xFF050810.toInt(),
        )

    private fun ch(c: Int, s: Int) = (c shr s) and 255

    private fun mixc(a: Int, b: Int, t: Float): Int =
        Px.argb(
            255,
            (ch(a, 16) + (ch(b, 16) - ch(a, 16)) * t).toInt(),
            (ch(a, 8) + (ch(b, 8) - ch(a, 8)) * t).toInt(),
            (ch(a, 0) + (ch(b, 0) - ch(a, 0)) * t).toInt(),
        )

    private fun colour(p: Planes, shape: TreeShape, look: TreeLook): IntArray {
        val r =
            when (look) {
                TreeLook.DAY -> DAY
                TreeLook.DUSK -> DUSK
                TreeLook.NIGHT -> NIGHT
            }
        val olive = 0xFF7C9C3E.toInt()
        val warm = shape.warmth * (if (look == TreeLook.NIGHT) .25f else 1f)
        val deep = mixc(r.deep, olive, warm * .5f)
        val shade = mixc(r.shade, olive, warm * .6f)
        val mid = mixc(r.mid, olive, warm)
        val light = mixc(r.light, olive, warm * .8f)
        val out = IntArray(p.w * p.h)
        for (i in out.indices) {
            val a = p.alpha[i]
            val s = p.shadow[i]
            if (a <= 0.004f && s <= 0.004f) continue
            val v = p.tone[i].coerceIn(0f, 1f)
            var col: Int
            if (p.bark[i] > .5f) {
                col = mixc(r.barkDark, r.barkLit, v)
            } else {
                // four values: deep, shade, mid, light, with a glint on the brightest leaves
                col =
                    if (v < .34f) mixc(deep, shade, v / .34f)
                    else if (v < .62f) mixc(shade, mid, (v - .34f) / .28f)
                    else mixc(mid, light, (v - .62f) / .38f)
                if (v > .86f) col = mixc(col, r.glint, (v - .86f) / .14f * .55f)
            }
            if (r.lampAmount > 0f)
                col = mixc(col, r.lamp, (p.lamp[i] * r.lampAmount).coerceIn(0f, .75f))
            // the soft shadow under it, behind the tree
            val total = a + s * (1f - a)
            val tr = (ch(col, 16) * a + ch(r.shadow, 16) * s * (1f - a)) / total
            val tg = (ch(col, 8) * a + ch(r.shadow, 8) * s * (1f - a)) / total
            val tb = (ch(col, 0) * a + ch(r.shadow, 0) * s * (1f - a)) / total
            out[i] = Px.argb((total * 255f + .5f).toInt(), tr.toInt(), tg.toInt(), tb.toInt())
        }
        return out
    }
}
