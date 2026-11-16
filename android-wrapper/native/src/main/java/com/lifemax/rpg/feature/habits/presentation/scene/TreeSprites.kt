package com.lifemax.rpg.feature.habits.presentation.scene

import com.lifemax.rpg.feature.habits.domain.Ambient
import com.lifemax.rpg.feature.habits.domain.SeasonState
import com.lifemax.rpg.feature.habits.domain.TreeKind
import com.lifemax.rpg.feature.habits.domain.TreeShape
import com.lifemax.rpg.feature.habits.domain.Trees
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** The three looks of a tree: by day, at dusk and at night. */
internal enum class TreeLook {
    DAY,
    DUSK,
    NIGHT,
}

/**
 * The dress of a deciduous tree through the year: summer's green, the yellow-green of the leaves
 * turning, autumn's amber or red (by variant), spring's thin light green, the blossom the flowering
 * variants put on over it, and the bare winter tree (its trunk and branches, which the leafy looks
 * sit on). A pine is [SUMMER] all year. The snow that lies on the branches is an overlay of its own
 * ([TreeSprites.snow]).
 */
internal enum class TreeSeason {
    SUMMER,
    TURNING,
    AUTUMN,
    SPRING,
    BLOSSOM,
    BARE,
}

/**
 * One tree variant as a whole picture, in each [TreeLook]: [pixels] hold an ARGB image of [w] by
 * [h] pixels, [ppu] pixels to a street unit, with the foot of the trunk at ([footX], [footY])
 * pixels. The sun (or the moon) is on the left; the picture is drawn mirrored for a light from the
 * right. The soft contact shadow is in it. The street draws a tree from its parts ([TreePart]); the
 * whole picture is what its shadows, its reflection and its depth are made from.
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
 * A piece of a tree cut out of its picture, so it can move on its own in the wind: [w] by [h]
 * pixels from ([x], [y]) in the tree's picture, one image per look in [pixels] (one look for the
 * pieces the hour tints when drawn).
 */
internal class TreePart(
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
    val pixels: Array<IntArray>,
)

/**
 * How a tree variant hangs together, the same for every season: its picture's size and foot, the
 * top of its trunk ([jointX], [jointY], where its boughs leave it), and for each mass of leaves
 * (cluster [k]) its middle ([cx], [cy]), how high up the tree it is ([rise], 0 at the foot to 1 at
 * the top) and the point it swings about ([px], [py]): the trunk's top, or for a mass sitting close
 * over it a point on the trunk further down, so it swings rather than turns on itself. The clusters
 * are drawn in [order] (the lowest first). All in the picture's pixels.
 */
internal class TreeRig(
    val w: Int,
    val h: Int,
    val ppu: Float,
    val footX: Float,
    val footY: Float,
    val jointX: Float,
    val jointY: Float,
    val cx: FloatArray,
    val cy: FloatArray,
    val rise: FloatArray,
    val px: FloatArray,
    val py: FloatArray,
    val order: IntArray,
) {
    val clusters: Int
        get() = cx.size
}

/**
 * The street tree generator: city trees after the London plane, the Norway maple, the flowering
 * cherry, the honey locust and the pear, two pines and a shrub.
 *
 * The trunk tapers up from a root flare that spreads into its pit, round in the light (its core
 * shadow just past the middle, the street's light thrown back on the far edge), in its species'
 * bark: the plane's mottle of cream, olive and grey where the old bark flakes away, the maple's and
 * the locust's furrows, the cherry's dark rings, the pine's red plates. At its top it forks into a
 * bough for each mass of the crown, each bough fanning into limbs and forking into twigs; the
 * sprays of finest twigs are a layer of their own, the fine mesh a winter crown is from across a
 * street.
 *
 * Each mass of leaves is built from many small rounded heaps of leaves, painted back to front: each
 * heap lit as a ball from the upper left, the ones deep in the crown darker, the underside in the
 * crown's own shade, the edge broken into single leaves, gaps left between them where the sky and
 * the branches show through; each mass throws a soft shadow on those behind it, and the whole crown
 * is lit as one round volume over that. The honey locust's fine leaves make a lacy crown.
 *
 * The picture is cut into parts that sway ([TreePart]): the trunk; for each mass its boughs and
 * twigs, its fine twigs, its leaves; each part keeps a margin of what lies beside it, so when the
 * wind moves one a little against another no seam opens. Everything is a function of the variant
 * and season, so the same tree is painted every time.
 */
internal object TreeSprites {
    const val PPU = 3f

    /** Space round the tree, in units. */
    private const val PAD = 4f

    /** The nearest a mass swings about, in units: closer to the trunk's top it pivots lower. */
    private const val SWING_REACH = 14f

    private const val LX = -.5f
    private const val LY = -.62f
    private const val LZ = .6f

    /** Who a pixel belongs to: the trunk, or none (else the index of a mass of leaves). */
    private const val TRUNK = -2
    private const val NONE = -1

    /** How far a part reaches under the parts drawn after it, in pixels. */
    private const val MARGIN = 2

    /** The planes a tree is painted into before it is coloured. */
    private class Planes(val w: Int, val h: Int) {
        val alpha = FloatArray(w * h)

        /** Tone, 0 (deep shade) to 1 (full light). */
        val tone = FloatArray(w * h)

        /** The warm street lamps' light on the side away from the sun, 0 to 1. */
        val lamp = FloatArray(w * h)

        /** The bark's own colour, 0 (its darkest) to 1 (its palest: the plane's cream). */
        val mott = FloatArray(w * h)

        /** Which mass a pixel belongs to ([TRUNK], [NONE] or a mass). */
        val owner = IntArray(w * h) { NONE }
        val shadow = FloatArray(w * h)
    }

    /** What sets one species apart. */
    private class Species(
        /** A heap of leaves' radius, in units, and how many heaps fill a mass. */
        val heap: Float,
        val density: Float,
        /** How lacy the leaves are (the honey locust's fine leaflets), 0 to 1. */
        val lace: Float,
        val bark: Bark,
        /** How far the trunk's top half is narrower than its foot. */
        val girth: Float,
    )

    private enum class Bark {
        MOTTLE,
        FURROW,
        RINGED,
        PLATES,
    }

    private val SPECIES =
        arrayOf(
            // the London plane: broad heaps, dense, its mottled bark
            Species(2.5f, 1.2f, 0f, Bark.MOTTLE, 1f),
            // the Norway maple: dense and dark, furrowed
            Species(2.2f, 1.25f, 0f, Bark.FURROW, 1f),
            // the flowering cherry: small, ringed bark
            Species(1.9f, 1.15f, .05f, Bark.RINGED, .9f),
            // the honey locust: fine and lacy, the sky through it
            Species(1.45f, .95f, .55f, Bark.FURROW, .95f),
            // the pear: a tight column
            Species(1.7f, 1.3f, .1f, Bark.FURROW, .9f),
            // the pines
            Species(2f, 1f, 0f, Bark.PLATES, 1f),
            Species(2f, 1f, 0f, Bark.PLATES, 1f),
            // the shrub
            Species(1.5f, 1.3f, .1f, Bark.FURROW, .7f),
        )

    // ---- the rig

    private fun width(shape: TreeShape) = ((shape.halfWidth + PAD) * 2f * PPU).toInt() + 1

    private fun height(shape: TreeShape) = ((shape.height + PAD + 4f) * PPU).toInt() + 1

    /** The top of the trunk, in units up from the foot (negative). */
    private fun trunkTop(shape: TreeShape) =
        if (shape.kind == TreeKind.PINE) -shape.height * .92f else shape.crown - 4f

    /** How far the trunk's top leans from over its foot, in units. */
    private fun lean(variant: Int) = (Ambient.hash(variant, 3, 130) - .5f) * 3f

    private val rigs: Array<TreeRig> by lazy { Array(Trees.VARIANTS) { buildRig(it) } }

    fun rig(variant: Int): TreeRig = rigs[variant]

    private fun buildRig(variant: Int): TreeRig {
        val shape = Trees.shapes[variant]
        val w = width(shape)
        val h = height(shape)
        val footX = w / 2f
        val footY = h - 4f * PPU
        val c = shape.clumps
        val n = c.size / 4
        val pine = shape.kind == TreeKind.PINE
        val cx = FloatArray(n) { footX + c[it * 4] * PPU }
        // a pine's tier sways about its middle, a little up from its foot
        val cy =
            FloatArray(n) {
                footY + (c[it * 4 + 1] - (if (pine) c[it * 4 + 3] * .35f else 0f)) * PPU
            }
        val rise = FloatArray(n) { ((footY - cy[it]) / (shape.height * PPU)).coerceIn(0f, 1f) }
        val order =
            if (pine) IntArray(n) { it }
            else (0 until n).sortedByDescending { c[it * 4 + 1] }.toIntArray()
        val jx = footX + lean(variant) * PPU
        val jy = footY + trunkTop(shape) * PPU
        val reach = SWING_REACH * PPU
        val px = FloatArray(n)
        val py = FloatArray(n)
        for (m in 0 until n) {
            val dx = cx[m] - jx
            val dy = cy[m] - jy
            if (dx * dx + dy * dy >= reach * reach) {
                px[m] = jx
                py[m] = jy
            } else {
                px[m] = cx[m]
                py[m] = min(footY, cy[m] + reach)
            }
        }
        return TreeRig(w, h, PPU, footX, footY, jx, jy, cx, cy, rise, px, py, order)
    }

    // ---- what the street draws

    /** The trunk, its root flare and its bark, in the three looks. */
    fun trunk(variant: Int): TreePart {
        val p = wood(variant, fine = false)
        val looks = Array(TreeLook.entries.size) { barkColour(p, TreeLook.entries[it], variant) }
        return cut(p, looks, TRUNK, rankOf(rig(variant)))
            ?: TreePart(0, 0, 1, 1, Array(looks.size) { IntArray(1) })
    }

    /**
     * Each mass's boughs, limbs and twigs (by mass; none for a pine), lit by day: the hour tints
     * them when they are drawn.
     */
    fun twigs(variant: Int): Array<TreePart?> {
        val r = rig(variant)
        if (Trees.evergreen(variant)) return arrayOfNulls(r.clusters)
        val p = wood(variant, fine = false)
        val looks = arrayOf(barkColour(p, TreeLook.DAY, variant, lamps = false))
        val rank = rankOf(r)
        return Array(r.clusters) { cut(p, looks, it, rank) }
    }

    /**
     * Each mass's sprays of finest twigs (by mass; none for a pine): the winter crown's fine mesh,
     * drawn as the leaves go. Lit by day, tinted when drawn.
     */
    fun fine(variant: Int): Array<TreePart?> {
        val r = rig(variant)
        if (Trees.evergreen(variant)) return arrayOfNulls(r.clusters)
        val p = wood(variant, fine = true)
        val looks = arrayOf(barkColour(p, TreeLook.DAY, variant, lamps = false))
        val rank = rankOf(r)
        return Array(r.clusters) { cut(p, looks, it, rank) }
    }

    /**
     * Each mass's leaves in [season] (a pine's tiers all year; none for the bare tree), in the
     * three looks.
     */
    fun leaves(variant: Int, season: TreeSeason): Array<TreePart?> {
        val r = rig(variant)
        val dress = if (Trees.evergreen(variant)) TreeSeason.SUMMER else season
        if (dress == TreeSeason.BARE) return arrayOfNulls(r.clusters)
        val p = crown(variant, dress)
        val looks =
            Array(TreeLook.entries.size) { leafColour(p, TreeLook.entries[it], dress, variant) }
        val rank = rankOf(r)
        return Array(r.clusters) { cut(p, looks, it, rank) }
    }

    /**
     * The snow that lies on the tree of [variant] in winter, white at every alpha so the hour tints
     * it, cut as the parts it lies on are (by mass): on the upper sides of the bare tree's branches
     * and twigs, or, for a pine, along its boughs and the lit ridges of its tiers.
     */
    fun snowParts(variant: Int): Array<TreePart?> {
        val r = rig(variant)
        val (p, cap) = snowPlanes(variant)
        val px = IntArray(p.w * p.h)
        for (i in px.indices) if (cap[i] > .01f)
            px[i] = Px.argbF(cap[i].coerceIn(0f, 1f), 1f, 1f, 1f)
        // the snow lies proud of the branch: let a part reach a little over its own pixels
        val rank = rankOf(r)
        return Array(r.clusters) { cut(p, arrayOf(px), it, rank, own = cap) }
    }

    /** The snow of [snowParts] as one picture the tree's size (what the tests look at). */
    fun snow(variant: Int): IntArray {
        val (p, cap) = snowPlanes(variant)
        val out = IntArray(p.w * p.h)
        for (i in out.indices) if (cap[i] > .01f)
            out[i] = Px.argbF(cap[i].coerceIn(0f, 1f), 1f, 1f, 1f)
        return out
    }

    /**
     * The whole tree of [variant] in [season] in its three looks, the contact shadow at its foot:
     * the trunk and its boughs, the winter's fine twigs on the bare tree, the season's leaves.
     */
    fun generate(variant: Int, season: TreeSeason = TreeSeason.SUMMER): TreeSprite {
        val r = rig(variant)
        val shape = Trees.shapes[variant]
        val dress = if (Trees.evergreen(variant)) TreeSeason.SUMMER else season
        val wood = wood(variant, fine = false)
        val shadow = FloatArray(r.w * r.h)
        contact(shadow, r, shape, if (dress == TreeSeason.BARE) .55f else 1f)
        val fine = if (dress == TreeSeason.BARE) wood(variant, fine = true) else null
        val leaves = if (dress == TreeSeason.BARE) null else crown(variant, dress)
        val pixels =
            Array(TreeLook.entries.size) {
                val look = TreeLook.entries[it]
                val out = IntArray(r.w * r.h)
                val sh = SHADOW[it]
                for (i in out.indices) if (shadow[i] > .004f)
                    out[i] =
                        Px.argb((shadow[i] * 255f + .5f).toInt(), ch(sh, 16), ch(sh, 8), ch(sh, 0))
                over(out, barkColour(wood, look, variant))
                if (fine != null) over(out, barkColour(fine, look, variant))
                if (leaves != null) over(out, leafColour(leaves, look, dress, variant))
                out
            }
        return TreeSprite(r.w, r.h, PPU, r.footX, r.footY, pixels)
    }

    // ---- the memory the street's trees take

    private val partBytes = HashMap<Long, Int>()

    private fun bytesOf(parts: Array<TreePart?>): Int =
        parts.sumOf { if (it == null) 0 else it.w * it.h * 4 * it.pixels.size }

    @Synchronized
    private fun measured(kind: Int, variant: Int, season: Int, make: () -> Int): Int =
        partBytes.getOrPut(kind.toLong() * 10_000 + variant * 100L + season) { make() }

    /** The bytes the wood of [variant] takes once made: the trunk, the twigs, the fine twigs. */
    fun woodBytes(variant: Int): Int =
        measured(0, variant, 0) {
            val t = trunk(variant)
            t.w * t.h * 4 * t.pixels.size + bytesOf(twigs(variant)) + bytesOf(fine(variant))
        }

    /** The bytes [variant]'s leaves in [season] take, and the half-size shape drawn with them. */
    fun leafBytes(variant: Int, season: TreeSeason): Int =
        measured(1, variant, season.ordinal) {
            bytesOf(leaves(variant, season)) + shapeBytes(variant)
        }

    /** The half-size silhouette of a tree (its shadows, its reflection, its back leaves). */
    fun shapeBytes(variant: Int): Int {
        val r = rig(variant)
        return ((r.w + 1) / 2) * ((r.h + 1) / 2) * 4
    }

    /**
     * What the pool of summer trees takes, in bytes: every variant's wood, its summer leaves and
     * its shape.
     */
    fun bytes(): Int =
        (0 until Trees.VARIANTS).sumOf { woodBytes(it) + leafBytes(it, TreeSeason.SUMMER) }

    /**
     * What one more [season] of trees takes once baked, in bytes: the deciduous variants only (a
     * pine is the same all year).
     */
    fun bytes(season: TreeSeason): Int =
        if (season == TreeSeason.SUMMER) bytes()
        else
            (0 until Trees.VARIANTS).sumOf { if (Trees.evergreen(it)) 0 else leafBytes(it, season) }

    /** What the snow on every variant takes, in bytes. */
    fun snowBytes(): Int =
        (0 until Trees.VARIANTS).sumOf { measured(2, it, 0) { bytesOf(snowParts(it)) } }

    /** What the tree pit's grate takes. */
    fun grateBytes(): Int = GRATE_W * GRATE_H * 4

    // ---- the tree pit

    /**
     * The grate's picture, seen from straight above: [GRATE_PPU] pixels a unit, its middle the
     * foot.
     */
    const val GRATE_PPU = 4f
    const val GRATE_UNITS = 17f
    val GRATE_W: Int = (GRATE_UNITS * GRATE_PPU).toInt()
    val GRATE_H: Int = GRATE_W

    /**
     * The pit the street tree stands in, seen from straight above (the street lays it flat): a
     * square of granite setts round a cast-iron grate of rings and spokes, the soil dark through
     * its slots, the trunk's shade soft round the middle, rust and wear on the iron. Drawn under
     * the tree, squashed into the pavement's depth.
     */
    fun grate(): IntArray {
        val n = GRATE_W
        val out = IntArray(n * n)
        val half = GRATE_UNITS / 2f
        for (j in 0 until n) for (i in 0 until n) {
            val x = (i + .5f) / GRATE_PPU - half
            val z = (j + .5f) / GRATE_PPU - half
            val ax = abs(x)
            val az = abs(z)
            val edge = max(ax, az)
            // the granite kerb of the pit: a band of setts with joints, worn paler on its top
            if (edge > half - .2f) continue
            val inner = 6.6f
            val r = sqrt(x * x + z * z)
            val col: Int
            var a = 1f
            if (edge > inner) {
                val sett = ((if (ax > az) z else x) + 40f) % 1.9f
                val joint = Px.smooth(.12f, 0f, sett) + Px.smooth(1.78f, 1.9f, sett)
                val ring =
                    Px.smooth(inner + .12f, inner, edge) + Px.smooth(half - .55f, half - .2f, edge)
                val grain = (Noise.fbm(x * 2.2f, z * 2.2f, 1201, 2) - .5f) * .16f
                val v = (.78f + grain - .22f * min(1f, joint + ring)).coerceIn(0f, 1f)
                col = Px.argbF(1f, .62f * v, .61f * v, .6f * v)
                a = Px.smooth(half - .2f, half - .5f, edge)
            } else {
                // the iron: rings and spokes round an opening for the trunk, the soil between
                val ang = kotlin.math.atan2(z, x)
                val spokes = abs(sin(ang * 12f))
                val rings = abs(sin(r * 2.6f))
                val iron =
                    max(
                        Px.smooth(.32f, .12f, spokes) * Px.smooth(2.4f, 2.7f, r),
                        Px.smooth(.38f, .16f, rings) * Px.smooth(2.3f, 2.6f, r),
                    )
                val frame = Px.smooth(inner - .55f, inner - .25f, edge) + Px.smooth(2.6f, 2.3f, r)
                val metal = min(1f, iron + frame) * Px.smooth(1.7f, 2f, r)
                val rust = Px.smooth(.55f, .8f, Noise.fbm(x * .9f, z * .9f, 1203, 3))
                val wear = Px.smooth(.6f, .85f, Noise.fbm(x * 1.6f, z * 1.6f, 1205, 2))
                // the soil through the slots, a leaf or two of litter, the trunk's shade round it
                val soil = .2f + .08f * Noise.fbm(x * 3f, z * 3f, 1207, 2)
                val shade = 1f - .55f * Px.smooth(5.5f, 1.2f, r)
                val ir = (.2f + .12f * wear) + .1f * rust
                val ig = .21f + .1f * wear
                val ib = (.23f + .09f * wear) - .06f * rust
                val sr = soil * .75f
                val sg = soil * .6f
                val sb = soil * .5f
                col =
                    Px.argbF(
                        1f,
                        Px.mix(sr, ir, metal) * shade,
                        Px.mix(sg, ig, metal) * shade,
                        Px.mix(sb, ib, metal) * shade,
                    )
                // the opening round the trunk: the soil there is under the root flare
                if (r < 1.7f) a = Px.smooth(.9f, 1.7f, r)
            }
            out[j * n + i] =
                (col and 0xFFFFFF) or ((a * 255f + .5f).toInt().coerceIn(0, 255) shl 24)
        }
        return out
    }

    // ---- cutting the picture into parts

    /** Each mass's place in the draw order. */
    private fun rankOf(r: TreeRig): IntArray {
        val rank = IntArray(r.clusters)
        for (k in r.order.indices) rank[r.order[k]] = k
        return rank
    }

    /**
     * The part of the picture [looks] (painted into [p]) that belongs to [k] ([TRUNK] or a mass):
     * its own pixels, and within [MARGIN] of them the pixels of the parts drawn after it (they lie
     * over it, so when one moves a little against it nothing shows through). [own], when given,
     * widens what counts as its own to every pixel of [k] with something in it. Null if empty.
     */
    private fun cut(
        p: Planes,
        looks: Array<IntArray>,
        k: Int,
        rank: IntArray,
        own: FloatArray? = null,
    ): TreePart? {
        val w = p.w
        val h = p.h
        val mine = BooleanArray(w * h)
        var x0 = w
        var y0 = h
        var x1 = -1
        var y1 = -1
        val base = looks[0]
        for (i in mine.indices) {
            if (p.owner[i] != k) continue
            if ((base[i] ushr 24) == 0 && (own == null || own[i] <= .01f)) continue
            mine[i] = true
        }
        val keep = BooleanArray(w * h)
        val later = if (k == TRUNK) -1 else rank[k]
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if ((base[i] ushr 24) == 0) continue
            var take = mine[i]
            if (!take) {
                val o = p.owner[i]
                // under what is drawn after it: a mass after this one, or (for the trunk) any mass
                val after = if (o == TRUNK || o == NONE) false else rank[o] > later
                if (after) {
                    loop@ for (dy in -MARGIN..MARGIN) for (dx in -MARGIN..MARGIN) {
                        val xx = x + dx
                        val yy = y + dy
                        if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue
                        if (mine[yy * w + xx]) {
                            take = true
                            break@loop
                        }
                    }
                }
            }
            if (!take) continue
            keep[i] = true
            if (x < x0) x0 = x
            if (y < y0) y0 = y
            if (x > x1) x1 = x
            if (y > y1) y1 = y
        }
        if (x1 < 0) return null
        val cw = x1 - x0 + 1
        val chh = y1 - y0 + 1
        val out =
            Array(looks.size) { l ->
                val src = looks[l]
                val dst = IntArray(cw * chh)
                for (y in 0 until chh) for (x in 0 until cw) {
                    val i = (y + y0) * w + x + x0
                    if (keep[i]) dst[y * cw + x] = src[i]
                }
                dst
            }
        return TreePart(x0, y0, cw, chh, out)
    }

    /** [src] laid over [dst] (straight alpha), in place. */
    private fun over(dst: IntArray, src: IntArray) {
        for (i in dst.indices) {
            val s = src[i]
            val sa = (s ushr 24) / 255f
            if (sa <= 0f) continue
            val d = dst[i]
            val da = (d ushr 24) / 255f
            val oa = sa + da * (1f - sa)
            if (oa <= 0f) continue
            fun c(sh: Int) = (ch(s, sh) * sa + ch(d, sh) * da * (1f - sa)) / oa
            dst[i] = Px.argb((oa * 255f + .5f).toInt(), c(16).toInt(), c(8).toInt(), c(0).toInt())
        }
    }

    // ---- painting the wood

    private fun contact(out: FloatArray, r: TreeRig, shape: TreeShape, amount: Float) {
        val rx = (shape.halfWidth * .7f + 2f) * PPU * (.6f + .4f * amount)
        val ry = (if (shape.kind == TreeKind.SHRUB) 2.4f else 3f) * PPU
        val cx = r.footX + 2.6f * PPU
        val cy = r.footY + .5f * PPU
        for (y in 0 until r.h) for (x in 0 until r.w) {
            val dx = (x - cx) / rx
            val dy = (y - cy) / ry
            val d = sqrt(dx * dx + dy * dy)
            if (d < 1f) {
                val s = 1f - d
                out[y * r.w + x] = s * s * (3f - 2f * s) * .46f * amount
            }
        }
    }

    /**
     * The wood of [variant]: the trunk and every mass's bough, limbs and twigs, or (with [fine])
     * only the sprays of finest twigs off them.
     */
    private fun wood(variant: Int, fine: Boolean): Planes {
        val r = rig(variant)
        val shape = Trees.shapes[variant]
        val p = Planes(r.w, r.h)
        val sp = SPECIES[variant]
        if (!fine) trunk(p, r, shape, sp, variant)
        if (shape.kind != TreeKind.PINE) branches(p, r, shape, sp, variant, fine)
        return p
    }

    /** The trunk's half width at [t] (0 at the foot, 1 at its top), in units. */
    private fun girth(shape: TreeShape, sp: Species, u: Float, t: Float): Float {
        val base = (if (shape.kind == TreeKind.SHRUB) 1.1f else 2.1f) * sp.girth
        val top = (if (shape.kind == TreeKind.PINE) .9f else 1.3f) * sp.girth
        val taper = top + (base - top) * (1f - t).pow(1.4f)
        // the root flare: the trunk spreads into the ground over its last unit or two
        return taper + base * .8f * exp(-u / 1.1f) + base * .25f * exp(-u / 3.5f)
    }

    private fun trunk(p: Planes, r: TreeRig, shape: TreeShape, sp: Species, variant: Int) {
        val top = -trunkTop(shape)
        val lean = lean(variant)
        // the roots reach out a little further on one side than the other
        val rootL = .8f + .5f * Ambient.hash(variant, 9, 131)
        val rootR = .8f + .5f * Ambient.hash(variant, 10, 131)
        for (y in 0 until p.h) {
            val u = (r.footY - y) / PPU // units above the ground
            if (u < -.6f || u > top) continue
            val t = (u / top).coerceIn(0f, 1f)
            val g = girth(shape, sp, max(0f, u), t)
            val cx = r.footX + (lean * t * t + .35f * sin(t * 3.1f + variant) * t) * PPU
            val left = g * (1f + (rootL - 1f) * exp(-max(0f, u) / .9f)) * PPU
            val right = g * (1f + (rootR - 1f) * exp(-max(0f, u) / .9f)) * PPU
            // it sinks into the soil at its foot
            val sink = Px.smooth(-.6f, .3f, u)
            for (x in max(0, (cx - left - 2f).toInt())..min(p.w - 1, (cx + right + 2f).toInt())) {
                val half = if (x < cx) left else right
                val nx = ((x + .5f - cx) / half).coerceIn(-1.2f, 1.2f)
                val cover = Px.clamp01((1f - abs(nx)) * half + .5f) * sink
                if (cover <= 0f) continue
                val n = nx.coerceIn(-1f, 1f)
                val nz = sqrt(max(0f, 1f - n * n))
                // the root flare leans back into the ground: its top faces the sky
                val up = .5f * exp(-max(0f, u) / 1.2f)
                val lam = n * LX + nz * LZ - up * LY
                var v = Px.smooth(-.35f, .95f, lam) * .8f + .06f
                // the core shadow just past the middle, the street's light thrown back on the edge
                v *= 1f - .42f * Px.smooth(.0f, .4f, n) * Px.smooth(.97f, .62f, n)
                v += .09f * Px.smooth(.62f, .94f, n)
                // the lit edge rolls off as the round turns from the light
                v *= 1f - .25f * Px.smooth(-.75f, -1f, n)
                // the crown's shade over its top
                v *= 1f - .32f * Px.smooth(.55f, 1f, t)
                // round the trunk: the arc across it, so the bark's pattern wraps
                val s = asin(n) * g
                val (tone, mott) = barkAt(sp.bark, s, u, variant, t)
                v *= tone
                val i = y * p.w + x
                p.alpha[i] = max(p.alpha[i], cover)
                p.tone[i] = v.coerceIn(0f, 1f)
                p.mott[i] = mott
                p.owner[i] = TRUNK
                p.lamp[i] = Px.smooth(.3f, .95f, n) * .8f
            }
        }
    }

    /**
     * The bark at [s] round the trunk and [u] up it (units): the tone it multiplies the light by
     * and its own colour (0 to 1 along its species' palette).
     */
    private fun barkAt(bark: Bark, s: Float, u: Float, variant: Int, t: Float): Pair<Float, Float> {
        val seed = 1100 + variant * 7
        return when (bark) {
            Bark.MOTTLE -> {
                // the plane: plates of old bark flake off, leaving cream, olive and grey patches
                // with dark rims; the foot of the trunk keeps its rough old bark
                val wx = s * .6f + (Noise.fbm(s * .4f, u * .4f, seed, 2) - .5f) * 1.6f
                val m = Noise.fbm(wx, u * .36f, seed + 1, 3)
                val m2 = Noise.fbm(wx * 1.9f + 5f, u * .7f, seed + 2, 2)
                val old = Px.smooth(5f, 1.5f, u)
                var c = if (m > .56f) 1f else if (m > .45f) .55f else .12f
                if (m2 > .62f && c < 1f) c = max(c, .55f)
                c *= 1f - .8f * old
                val rim =
                    Px.smooth(.025f, 0f, abs(m - .56f)) + Px.smooth(.02f, 0f, abs(m - .45f)) * .6f
                val grain = (Noise.value(s * 3f, u * 3f, seed + 3) - .5f) * .14f
                val rough = old * (Noise.fbm(s * 1.4f, u * .5f, seed + 4, 2) - .5f) * .5f
                Pair(1f - .35f * min(1f, rim) + grain + rough, c)
            }
            Bark.FURROW -> {
                // ridges up the trunk with dark furrows between, broken now and then across
                val ridge = Noise.fbm(s * 1.1f, u * .22f, seed, 3)
                val furrow = Px.smooth(.5f, .36f, ridge)
                val crack = Px.smooth(.7f, .8f, Noise.value(s * .8f, u * 1.1f, seed + 1))
                val grain = (Noise.value(s * 3.5f, u * 2.5f, seed + 2) - .5f) * .14f
                Pair(
                    1f - .62f * furrow - .2f * crack + .18f * Px.smooth(.55f, .7f, ridge) + grain,
                    .15f + .7f * ridge,
                )
            }
            Bark.RINGED -> {
                // the cherry: smooth and dark, ringed round with pale lenticels
                val band = Noise.value(s * .5f, u * 3.1f, seed)
                val lent =
                    Px.smooth(.78f, .88f, band) *
                        Px.smooth(.3f, .6f, Noise.value(s * 2.2f, u * .8f, seed + 1))
                val sheen = (Noise.fbm(s * .6f, u * .3f, seed + 2, 2) - .5f) * .2f
                Pair(1f + sheen, (.3f + .7f * lent).coerceIn(0f, 1f))
            }
            Bark.PLATES -> {
                // a pine's red plates with dark fissures round them
                val cell = Noise.fbm(s * 1.1f, u * .55f, seed, 3)
                val fis = Px.smooth(.06f, 0f, abs(cell - .5f))
                Pair(1f - .45f * fis + (cell - .5f) * .3f, (.3f + .8f * cell).coerceIn(0f, 1f))
            }
        }
    }

    /**
     * Each mass's bough from the top of the trunk, the limbs it fans into towards the mass's edge
     * and the twigs they fork into; or (with [fine]) the sprays of finest twigs off those.
     * Everything kept inside the mass, so the bare tree stands inside the leafy one's outline.
     */
    private fun branches(
        p: Planes,
        r: TreeRig,
        shape: TreeShape,
        sp: Species,
        variant: Int,
        fine: Boolean,
    ) {
        val c = shape.clumps
        val top = trunkTop(shape)
        val topHalf = girth(shape, sp, -top, 1f)
        for (m in r.order) {
            val cx = r.footX + c[m * 4] * PPU
            val cy = r.footY + c[m * 4 + 1] * PPU
            val rx = c[m * 4 + 2] * PPU
            val ry = c[m * 4 + 3] * PPU
            val seed = 300 + variant * 29 + m * 7
            fun h(a: Int, b: Int) = Ambient.hash(seed, a, b)
            // the bough leaves the trunk on the side its mass is, a little down from the top
            val side = ((cx - r.jointX) / max(1f, rx)).coerceIn(-1f, 1f)
            val bx = r.jointX + side * topHalf * .55f * PPU
            val by = r.jointY + (1.2f + 1.5f * (1f - abs(side))) * PPU
            val r0 = (.8f + .35f * (rx / (9f * PPU))).coerceIn(.75f, 1.35f) * PPU * sp.girth
            val ex = cx - (cx - bx) * .12f
            val ey = cy + ry * .1f
            if (!fine)
                bough(p, bx, by, ex, ey, r0, .5f * PPU, (h(0, 1) - .5f) * .45f, seed, m, variant)
            val limbs = 3 + (h(0, 2) * 2.5f).toInt()
            for (k in 0 until limbs) {
                // the limbs fan upward and outward from the middle of the mass
                val ang =
                    (-PI.toFloat() / 2f +
                        (k - (limbs - 1) / 2f) * (PI.toFloat() / (limbs + .2f)) +
                        (h(k, 3) - .5f) * .5f)
                val len = .62f + .22f * h(k, 4)
                var lx = ex + cos(ang) * rx * len
                var ly = ey + sin(ang) * ry * len
                lx = insideX(cx, rx, lx, ly, cy, ry, .68f)
                ly = insideY(cx, rx, lx, ly, cy, ry, .68f)
                if (!fine)
                    bough(
                        p,
                        ex,
                        ey,
                        lx,
                        ly,
                        .5f * PPU,
                        .28f * PPU,
                        (h(k, 5) - .5f) * .4f,
                        seed + k + 1,
                        m,
                        variant,
                    )
                for (j in 0 until 2) {
                    val a2 =
                        upward(ang + (if (j == 0) -.6f else .6f) + (h(k * 2 + j, 6) - .5f) * .45f)
                    var fx = lx + cos(a2) * rx * .38f
                    var fy = ly + sin(a2) * ry * .38f
                    fx = insideX(cx, rx, fx, fy, cy, ry, .74f)
                    fy = insideY(cx, rx, fx, fy, cy, ry, .74f)
                    if (!fine)
                        bough(
                            p,
                            lx,
                            ly,
                            fx,
                            fy,
                            .28f * PPU,
                            .15f * PPU,
                            (h(k, 7 + j) - .5f) * .5f,
                            seed + 20 + k * 3 + j,
                            m,
                            variant,
                        )
                    else {
                        // the finest twigs off each twig's end, reaching out and up
                        val n = 4 + (h(k * 2 + j, 9) * 3f).toInt()
                        for (q in 0 until n) {
                            val hq = k * 64 + j * 32 + q
                            val a3 = upward(a2 + (h(hq, 10) - .5f) * 1.9f - .15f)
                            val l3 = (.2f + .22f * h(hq, 30)) * (rx + ry) * .5f
                            // from somewhere along the twig, not all from its tip
                            val along = .45f + .55f * h(hq, 31)
                            val sx = lx + (fx - lx) * along
                            val sy = ly + (fy - ly) * along
                            var tx = sx + cos(a3) * l3
                            var ty = sy + sin(a3) * l3
                            tx = insideX(cx, rx, tx, ty, cy, ry, .96f)
                            ty = insideY(cx, rx, tx, ty, cy, ry, .96f)
                            bough(
                                p,
                                sx,
                                sy,
                                tx,
                                ty,
                                .17f * PPU,
                                .08f * PPU,
                                (h(hq, 50) - .5f) * .6f,
                                seed + 60 + q,
                                m,
                                variant,
                            )
                            // and their own sprays
                            for (z in 0 until 2) {
                                val a4 =
                                    upward(
                                        a3 +
                                            (if (z == 0) -.55f else .5f) +
                                            (h(hq, 60 + z) - .5f) * .5f
                                    )
                                val l4 = l3 * (.45f + .3f * h(hq, 62 + z))
                                val ux = sx + (tx - sx) * (.5f + .3f * z)
                                val uy = sy + (ty - sy) * (.5f + .3f * z)
                                var vx = ux + cos(a4) * l4
                                var vy = uy + sin(a4) * l4
                                vx = insideX(cx, rx, vx, vy, cy, ry, .99f)
                                vy = insideY(cx, rx, vx, vy, cy, ry, .99f)
                                bough(
                                    p,
                                    ux,
                                    uy,
                                    vx,
                                    vy,
                                    .11f * PPU,
                                    .06f * PPU,
                                    (h(hq, 64 + z) - .5f) * .5f,
                                    seed + 90 + q,
                                    m,
                                    variant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * A twig's heading [a] (radians, y down) kept from hanging down: one that would point more than
     * a little below level is turned up as far, as twigs grow towards the light.
     */
    private fun upward(a: Float): Float =
        if (sin(a) > .2f) -a + .4f * (if (cos(a) < 0f) -1f else 1f) else a

    /** ([x], [y]) pulled back towards the ellipse's middle until it is within [r] of its radius. */
    private fun insideX(
        cx: Float,
        rx: Float,
        x: Float,
        y: Float,
        cy: Float,
        ry: Float,
        r: Float,
    ): Float {
        val d = sqrt(((x - cx) / rx).let { it * it } + ((y - cy) / ry).let { it * it })
        return if (d <= r) x else cx + (x - cx) * r / d
    }

    private fun insideY(
        cx: Float,
        rx: Float,
        x: Float,
        y: Float,
        cy: Float,
        ry: Float,
        r: Float,
    ): Float {
        val d = sqrt(((x - cx) / rx).let { it * it } + ((y - cy) / ry).let { it * it })
        return if (d <= r) y else cy + (y - cy) * r / d
    }

    /**
     * A branch from ([x0], [y0]) to ([x1], [y1]), [r0] thick at its root and [r1] at its tip, bowed
     * sideways by [bend] of its length: a run of discs along a curve, round in the light from the
     * upper left, its bark streaked along it; belonging to mass [owner]. One thinner than a pixel
     * is drawn faint rather than thick.
     */
    private fun bough(
        p: Planes,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        r0: Float,
        r1: Float,
        bend: Float,
        seed: Int,
        owner: Int,
        variant: Int,
    ) {
        val dx = x1 - x0
        val dy = y1 - y0
        val len = sqrt(dx * dx + dy * dy)
        if (len < .5f) return
        // the control point stands off the chord, so the branch bows (and rises a little)
        val mx = (x0 + x1) / 2f - dy / len * bend * len
        val my = (y0 + y1) / 2f + dx / len * bend * len - len * .06f
        val steps = max(4, (len / .7f).toInt())
        val pale = SPECIES[variant].bark == Bark.MOTTLE
        for (k in 0..steps) {
            val t = k / steps.toFloat()
            val u = 1f - t
            val cx = u * u * x0 + 2f * u * t * mx + t * t * x1
            val cy = u * u * y0 + 2f * u * t * my + t * t * y1
            // the direction along the branch here, for its round light
            val tx = 2f * u * (mx - x0) + 2f * t * (x1 - mx)
            val ty = 2f * u * (my - y0) + 2f * t * (y1 - my)
            val tl = max(1e-3f, sqrt(tx * tx + ty * ty))
            val px = -ty / tl
            val py = tx / tl
            val r = r0 + (r1 - r0) * t
            val rr = max(r, .62f)
            val thin = min(1f, r / .62f)
            for (y in (cy - rr - 1).toInt()..(cy + rr + 1).toInt()) for (x in
                (cx - rr - 1).toInt()..(cx + rr + 1).toInt()) {
                if (x < 0 || y < 0 || x >= p.w || y >= p.h) continue
                val ddx = x + .5f - cx
                val ddy = y + .5f - cy
                val d = sqrt(ddx * ddx + ddy * ddy) / rr
                val cover = Px.smooth(1.12f, .62f, d) * thin
                if (cover <= 0f) continue
                val i = y * p.w + x
                if (cover > p.alpha[i] || p.owner[i] == NONE) {
                    // across the branch, -1 to 1: its round surface's light
                    val n = ((ddx * px + ddy * py) / rr).coerceIn(-1f, 1f)
                    val nz = sqrt(max(0f, 1f - n * n))
                    val lam = n * (px * LX + py * LY) + nz * LZ
                    val streak = Noise.fbm(x * .5f, y * .5f, seed, 2)
                    val v = Px.smooth(-.4f, .9f, lam) * .7f + .1f + (streak - .5f) * .22f
                    p.tone[i] = v.coerceIn(0f, 1f)
                    p.lamp[i] = Px.smooth(.2f, .95f, n * px) * .7f
                    // a plane's upper limbs have shed their dark bark: cream and olive
                    p.mott[i] =
                        if (pale)
                            Px.smooth(.4f, .62f, Noise.fbm(x * .2f, y * .2f, seed + 1, 2)) * .9f +
                                .1f
                        else .3f + .4f * streak
                    p.alpha[i] = max(p.alpha[i], cover)
                    p.owner[i] = owner
                }
            }
        }
    }

    // ---- painting the leaves

    /** The crown of [variant] in [season]: its masses of leaves (a pine's tiers), owned by mass. */
    private fun crown(variant: Int, season: TreeSeason): Planes {
        val r = rig(variant)
        val shape = Trees.shapes[variant]
        val p = Planes(r.w, r.h)
        if (shape.kind == TreeKind.PINE) tiers(p, r, shape, variant)
        else {
            masses(p, r, shape, variant, season)
            volume(p, shape, r)
        }
        return p
    }

    /**
     * The crown as one rounded mass: over the heaps' own light, the whole crown is lit as a ball
     * (or a pine as a cone) from the light's side, falling darker on the far side and underneath,
     * so the tree reads as a volume and not a flat cut-out.
     */
    private fun volume(p: Planes, shape: TreeShape, r: TreeRig) {
        val c = shape.clumps
        var x0 = Float.MAX_VALUE
        var x1 = -Float.MAX_VALUE
        var y0 = Float.MAX_VALUE
        var y1 = -Float.MAX_VALUE
        for (m in 0 until c.size / 4) {
            x0 = min(x0, c[m * 4] - c[m * 4 + 2])
            x1 = max(x1, c[m * 4] + c[m * 4 + 2])
            y0 = min(y0, c[m * 4 + 1] - c[m * 4 + 3])
            y1 = max(y1, c[m * 4 + 1] + c[m * 4 + 3])
        }
        val ccx = r.footX + (x0 + x1) / 2f * PPU
        val ccy = r.footY + (y0 + y1) / 2f * PPU
        val crx = (x1 - x0) / 2f * PPU
        val cry = (y1 - y0) / 2f * PPU
        for (i in 0 until p.w * p.h) {
            if (p.alpha[i] <= 0f) continue
            val gx = ((i % p.w) - ccx) / crx
            val gy = ((i / p.w) - ccy) / cry
            val r2 = min(.96f, gx * gx + gy * gy)
            val nz = sqrt(1f - r2)
            val lam = gx * LX + gy * LY + nz * LZ
            var k = .64f + .48f * Px.smooth(-.55f, .85f, lam)
            // the underside, out of the sky's light, and the heart of the crown, out of all light
            k *= 1f - .26f * Px.smooth(.3f, 1f, gy)
            k *= 1f - .12f * Px.smooth(.5f, 0f, sqrt(r2))
            p.tone[i] = (p.tone[i] * k).coerceIn(0f, 1f)
        }
    }

    /**
     * One heap of leaves: where it is, how big, how deep in the crown (0 the back, 1 the front).
     */
    private class Heap(val x: Float, val y: Float, val r: Float, val depth: Float)

    /** Broadleaf, tall and shrub trees: masses of leaves, lowest first, each built of heaps. */
    private fun masses(p: Planes, r: TreeRig, shape: TreeShape, variant: Int, season: TreeSeason) {
        val c = shape.clumps
        val sp = SPECIES[variant]
        // spring's leaves are new and few; autumn's half fallen
        val thin =
            when (season) {
                TreeSeason.SPRING -> .72f
                TreeSeason.AUTUMN -> .62f
                TreeSeason.TURNING -> .92f
                else -> 1f
            }
        val small = if (season == TreeSeason.SPRING) .85f else 1f
        for ((rank, m) in r.order.withIndex()) {
            val cx = r.footX + c[m * 4] * PPU
            val cy = r.footY + c[m * 4 + 1] * PPU
            val rx = c[m * 4 + 2] * PPU
            val ry = c[m * 4 + 3] * PPU
            val seed = 150 + variant * 17 + m * 3
            cast(p, cx, cy, rx, ry, seed)
            // the masses behind (painted first) a little darker than those in front of them
            val depth = .84f + .16f * rank / max(1, r.order.size - 1)
            val heapR = sp.heap * PPU * small
            val count =
                (sp.density * thin * (rx * ry) / (heapR * heapR) * 1.25f).toInt().coerceIn(8, 140)
            val heaps = ArrayList<Heap>(count)
            for (j in 0 until count) {
                fun hh(k: Int) = Ambient.hash(seed, j, 400 + k)
                // more of them towards the edge: a crown is a shell of leaves round its branches
                val ang = hh(0) * 2f * PI.toFloat()
                val rr = sqrt(hh(1)).pow(.75f)
                val x = cx + cos(ang) * rr * max(1f, rx - heapR * .55f)
                val y = cy + sin(ang) * rr * max(1f, ry - heapR * .55f)
                val front = sqrt(max(0f, 1f - rr * rr))
                val d = (front * (.35f + .65f * hh(2)) + (1f - front) * .55f * hh(3))
                heaps += Heap(x, y, heapR * (.72f + .55f * hh(4)), d)
            }
            heaps.sortBy { it.depth }
            for (hp in heaps) heap(p, hp, cx, cy, rx, ry, seed, sp, depth, m, season)
        }
    }

    /** One heap of leaves of mass [owner], in the mass at ([cx], [cy]), into the planes. */
    private fun heap(
        p: Planes,
        hp: Heap,
        cx: Float,
        cy: Float,
        rx: Float,
        ry: Float,
        seed: Int,
        sp: Species,
        depth: Float,
        owner: Int,
        season: TreeSeason,
    ) {
        val reach = hp.r * 1.3f
        val autumn = season == TreeSeason.AUTUMN
        for (y in max(0, (hp.y - reach).toInt())..min(p.h - 1, (hp.y + reach).toInt() + 1)) {
            for (x in max(0, (hp.x - reach).toInt())..min(p.w - 1, (hp.x + reach).toInt() + 1)) {
                val ddx = x + .5f - hp.x
                val ddy = y + .5f - hp.y
                val d = sqrt(ddx * ddx + ddy * ddy) / hp.r
                // the edge broken into single leaves: noise about a leaf across
                val leaf =
                    (Noise.value(x * .62f, y * .62f, seed + 9) - .5f) * .5f +
                        (Noise.value(x * 1.3f, y * 1.3f, seed + 10) - .5f) * .22f
                val edge = 1f + leaf
                var cover = Px.clamp01((edge - d) * hp.r * .9f + .5f)
                if (cover <= 0f) continue
                // the locust's leaflets let the sky through; an autumn crown is thinning
                val holes = sp.lace + (if (autumn) .25f else 0f)
                if (holes > 0f)
                    cover *=
                        Px.smooth(
                            holes * .55f,
                            holes * .55f + .18f,
                            Noise.fbm(x * .45f, y * .45f, seed + 11, 2),
                        )
                if (cover <= .01f) continue
                // the heap's round surface, and the mass's, lit together
                val hx = (ddx / hp.r).coerceIn(-1f, 1f)
                val hy = (ddy / hp.r).coerceIn(-1f, 1f)
                val hz = sqrt(max(0f, 1f - hx * hx - hy * hy))
                val mx = ((x - cx) / rx).coerceIn(-1f, 1f)
                val my = ((y - cy) / ry).coerceIn(-1f, 1f)
                val mz = sqrt(max(0f, 1f - min(.98f, mx * mx + my * my)))
                var nx = hx * .6f + mx * .4f
                var ny = hy * .6f + my * .4f
                var nz = hz * .6f + mz * .4f
                val nl = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-3f)
                nx /= nl
                ny /= nl
                nz /= nl
                val lam = nx * LX + ny * LY + nz * LZ
                var v = Px.smooth(-.45f, .85f, lam)
                // single leaves catch the light or turn from it
                v += (Noise.value(x * .85f, y * .85f, seed + 12) - .5f) * .3f
                // deep in the crown it is dark; under the mass it is in the crown's own shade
                v *= .52f + .48f * hp.depth
                v *= .82f + .18f * Px.smooth(.9f, -.4f, my)
                // the lit rim on the sun's side of the mass
                if (mx < -.4f && my < .2f)
                    v += .14f * Px.smooth(.55f, .98f, sqrt(mx * mx + my * my))
                val lampSide =
                    Px.smooth(.25f, .95f, mx * .8f - my * .1f + (1f - mz) * .3f) *
                        Px.smooth(.3f, .9f, sqrt(mx * mx + my * my)) *
                        (.6f + .4f * hp.depth)
                val i = y * p.w + x
                p.tone[i] = Px.mix(p.tone[i], (v * depth).coerceIn(0f, 1f), cover)
                p.lamp[i] = Px.mix(p.lamp[i], lampSide, cover)
                if (cover > .4f || p.owner[i] == NONE) p.owner[i] = owner
                p.alpha[i] = p.alpha[i] + (1f - p.alpha[i]) * cover
            }
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
        val rag =
            (Noise.fbm(x * .16f, y * .16f, seed, 3) - .5f) * .5f +
                (Noise.fbm(x * .45f, y * .45f, seed + 1, 2) - .5f) * .16f
        return 1f - r + rag
    }

    /** The mass's shadow on what is already painted behind it: soft, thrown away from the light. */
    private fun cast(p: Planes, cx: Float, cy: Float, rx: Float, ry: Float, seed: Int) {
        val ox = 3f * PPU
        val oy = 4f * PPU
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

    /** A pine: tiers from the lowest up, each a ragged triangle, each shading the one below it. */
    private fun tiers(p: Planes, r: TreeRig, shape: TreeShape, variant: Int) {
        val c = shape.clumps
        for (m in 0 until c.size / 4) {
            val seed = 170 + variant * 13 + m
            val cx = r.footX + c[m * 4] * PPU
            val baseY = r.footY + c[m * 4 + 1] * PPU
            val hw = c[m * 4 + 2] * PPU
            val ht = c[m * 4 + 3] * PPU
            tierShadow(p, cx, baseY, hw, ht, seed)
            tier(p, cx, baseY, hw, ht, seed, m)
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
        val rag =
            (Noise.fbm(x * .2f, y * .2f, seed, 3) - .5f) * 3.4f * PPU * .5f +
                (Noise.value(x * .7f, y * .7f, seed + 5) - .5f) * 1.2f
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

    private fun tier(
        p: Planes,
        cx: Float,
        baseY: Float,
        hw: Float,
        ht: Float,
        seed: Int,
        owner: Int,
    ) {
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
                // boughs: horizontal ridges, light on top, dark underneath, needles on them
                val ridge = Noise.fbm(x * .06f, y * .3f, seed + 3, 3)
                v += (ridge - .5f) * .34f
                v += (Noise.fbm(x * .55f, y * .55f, seed + 4, 3) - .5f) * .24f
                v += (Noise.value(x * 1.1f, y * .6f, seed + 6) - .5f) * .14f
                // under each tier's skirt it is darker
                v *= .72f + .28f * Px.smooth(0f, .5f, t + .12f)
                val lampSide = Px.smooth(.2f, .95f, dx) * Px.smooth(.25f, .8f, 1f - t * .5f)
                val i = y * p.w + x
                p.tone[i] = Px.mix(p.tone[i], v.coerceIn(0f, 1f), cover)
                p.lamp[i] = Px.mix(p.lamp[i], lampSide, cover)
                if (cover > .4f || p.owner[i] == NONE) p.owner[i] = owner
                p.alpha[i] = p.alpha[i] + (1f - p.alpha[i]) * cover
            }
        }
    }

    // ---- snow

    /** The planes the snow lies on (the bare tree, or the pine) and how much lies on each pixel. */
    private fun snowPlanes(variant: Int): Pair<Planes, FloatArray> {
        val r = rig(variant)
        val shape = Trees.shapes[variant]
        val pine = shape.kind == TreeKind.PINE
        val p: Planes
        if (pine) p = crown(variant, TreeSeason.SUMMER)
        else {
            p = wood(variant, fine = false)
            val f = wood(variant, fine = true)
            for (i in p.alpha.indices) if (f.alpha[i] > p.alpha[i]) {
                p.alpha[i] = f.alpha[i]
                p.tone[i] = f.tone[i]
                if (p.owner[i] != TRUNK) p.owner[i] = f.owner[i]
            }
        }
        val w = p.w
        val h = p.h
        val cap = FloatArray(w * h)
        for (y in 3 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val a = p.alpha[i]
            if (a < .3f || p.owner[i] < 0) continue
            // how open the sky is over this pixel: snow settles where nothing is above
            var open = 0f
            for (k in 1..3) open += max(0f, 1f - p.alpha[i - k * w]) * (if (k == 1) 1f else .5f)
            var c = Px.smooth(.5f, 1.7f, open)
            // a pine's boughs hold snow along their lit ridges too
            if (pine) c = max(c, Px.smooth(.7f, .92f, p.tone[i]) * .6f)
            c *= Px.smooth(.35f, .95f, a) * .85f
            // not every branch top is covered alike
            c *= .7f + .3f * Noise.fbm(x * .3f, y * .3f, 700 + variant, 2)
            if (c <= 0f) continue
            cap[i] = max(cap[i], c)
            // it lies a little proud of the branch
            if (cap[i - w] < c * .85f) {
                cap[i - w] = c * .85f
                if (p.owner[i - w] < 0) p.owner[i - w] = p.owner[i]
            }
            if (c > .5f && cap[i - 2 * w] < (c - .5f) * .8f) {
                cap[i - 2 * w] = (c - .5f) * .8f
                if (p.owner[i - 2 * w] < 0) p.owner[i - 2 * w] = p.owner[i]
            }
        }
        return Pair(p, cap)
    }

    // ---- colouring

    private class Ramp(
        val deep: Int,
        val shade: Int,
        val mid: Int,
        val light: Int,
        val glint: Int,
        val lamp: Int,
        val lampAmount: Float,
    )

    private val DAY =
        Ramp(
            0xFF173E32.toInt(),
            0xFF275F45.toInt(),
            0xFF3F8656.toInt(),
            0xFF86B96C.toInt(),
            0xFFD6EA9E.toInt(),
            0xFFFFE0A0.toInt(),
            0f,
        )
    private val DUSK =
        Ramp(
            0xFF262A46.toInt(),
            0xFF3D4858.toInt(),
            0xFF5C7A58.toInt(),
            0xFFDDA672.toInt(),
            0xFFFFD09A.toInt(),
            0xFFFFC27A.toInt(),
            .22f,
        )
    private val NIGHT =
        Ramp(
            0xFF040C14.toInt(),
            0xFF0A1B24.toInt(),
            0xFF143238.toInt(),
            0xFF3E6A78.toInt(),
            0xFF8EB0C4.toInt(),
            0xFFFFC070.toInt(),
            .62f,
        )

    /** The contact shadow's colour in each look. */
    private val SHADOW = intArrayOf(0xFF15202B.toInt(), 0xFF1E1830.toInt(), 0xFF050810.toInt())

    /**
     * The light on bark in each look: the sky's (the shade) and the sun's or moon's (the lit side),
     * as multipliers of the bark's own colour; and the lamps' at night.
     */
    private val BARK_AMBIENT =
        arrayOf(
            floatArrayOf(.32f, .35f, .44f),
            floatArrayOf(.3f, .27f, .38f),
            floatArrayOf(.1f, .12f, .19f),
        )
    private val BARK_SUN =
        arrayOf(
            floatArrayOf(1.12f, 1.04f, .9f),
            floatArrayOf(1.25f, .86f, .62f),
            floatArrayOf(.3f, .38f, .52f),
        )
    private val BARK_LAMP = floatArrayOf(0f, .25f, .62f)

    /** Each bark's palette, darkest to palest. */
    private val BARK_INK =
        mapOf(
            Bark.MOTTLE to intArrayOf(0xFF57534A.toInt(), 0xFF7E8058.toInt(), 0xFFC6C0A4.toInt()),
            Bark.FURROW to intArrayOf(0xFF2E2620.toInt(), 0xFF54483C.toInt(), 0xFF7C6E5E.toInt()),
            Bark.RINGED to intArrayOf(0xFF331E1A.toInt(), 0xFF57352B.toInt(), 0xFFA08C7A.toInt()),
            Bark.PLATES to intArrayOf(0xFF3E2418.toInt(), 0xFF74442C.toInt(), 0xFFA6744E.toInt()),
        )

    private fun ch(c: Int, s: Int) = (c shr s) and 255

    private fun mixc(a: Int, b: Int, t: Float): Int =
        Px.argb(
            255,
            (ch(a, 16) + (ch(b, 16) - ch(a, 16)) * t).toInt(),
            (ch(a, 8) + (ch(b, 8) - ch(a, 8)) * t).toInt(),
            (ch(a, 0) + (ch(b, 0) - ch(a, 0)) * t).toInt(),
        )

    /** The wood's pixels in [look]: the bark's own colour under the hour's light. */
    private fun barkColour(
        p: Planes,
        look: TreeLook,
        variant: Int,
        lamps: Boolean = true,
    ): IntArray {
        val ink = BARK_INK.getValue(SPECIES[variant].bark)
        val amb = BARK_AMBIENT[look.ordinal]
        val sun = BARK_SUN[look.ordinal]
        val lampK = if (lamps) BARK_LAMP[look.ordinal] else 0f
        val out = IntArray(p.w * p.h)
        for (i in out.indices) {
            val a = p.alpha[i]
            if (a <= .004f) continue
            val m = p.mott[i].coerceIn(0f, 1f)
            val al =
                if (m < .5f) mixc(ink[0], ink[1], m * 2f) else mixc(ink[1], ink[2], m * 2f - 1f)
            val v = p.tone[i].coerceIn(0f, 1f)
            val l = p.lamp[i] * lampK
            fun c(k: Int, sh: Int, lamp: Float) =
                (ch(al, sh) * (amb[k] + (sun[k] - amb[k]) * v + lamp * l)).toInt()
            out[i] = Px.argb((a * 255f + .5f).toInt(), c(0, 16, 1f), c(1, 8, .72f), c(2, 0, .4f))
        }
        return out
    }

    /**
     * What a season does to the leaf colours: the hue the ramp is turned to, how much more (or
     * less) saturated and lighter it is, and how much the flowers speckle the canopy.
     */
    private class Dye(
        val hue: Float,
        val sat: Float,
        val light: Float,
        val speckle: Float,
        /**
         * How the ramp's own spread of hue is laid about the new hue: negative turns the light
         * leaves towards yellow and the shaded ones towards red, as turning leaves go.
         */
        val spread: Float,
    )

    private val AMBER = Dye(36f, 1.2f, .04f, 0f, -.7f)
    private val RED = Dye(8f, 1.1f, -.01f, 0f, -.6f)
    private val GOLD = Dye(78f, 1.1f, .05f, 0f, -.6f)
    private val BRASS = Dye(52f, 1.1f, .03f, 0f, -.6f)

    /** The honey locust's clear butter yellow. */
    private val BUTTER = Dye(52f, 1.3f, .1f, 0f, -.45f)
    private val SPRING_DYE = Dye(86f, 1.05f, .1f, 0f, -.5f)
    private val PINK = Dye(338f, .62f, .3f, .5f, .3f)
    private val WHITE = Dye(48f, .2f, .42f, .5f, .2f)

    private fun dyeOf(season: TreeSeason, variant: Int): Dye? =
        when (season) {
            TreeSeason.SUMMER,
            TreeSeason.BARE -> null
            TreeSeason.TURNING -> if (Trees.turnsRed(variant)) BRASS else GOLD
            TreeSeason.AUTUMN ->
                if (Trees.turnsRed(variant)) RED else if (variant == 3) BUTTER else AMBER
            TreeSeason.SPRING -> SPRING_DYE
            TreeSeason.BLOSSOM -> if (variant == 7) WHITE else PINK
        }

    private fun hsl(c: Int): FloatArray {
        val r = ch(c, 16) / 255f
        val g = ch(c, 8) / 255f
        val b = ch(c, 0) / 255f
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val l = (mx + mn) / 2f
        val d = mx - mn
        if (d < 1e-4f) return floatArrayOf(0f, 0f, l)
        val s = d / (1f - abs(2f * l - 1f))
        var h =
            when (mx) {
                r -> ((g - b) / d) % 6f
                g -> (b - r) / d + 2f
                else -> (r - g) / d + 4f
            } * 60f
        if (h < 0f) h += 360f
        return floatArrayOf(h, s, l)
    }

    private fun fromHsl(h: Float, s: Float, l: Float): Int {
        val c = (1f - abs(2f * l - 1f)) * s
        val hh = ((h % 360f) + 360f) % 360f / 60f
        val x = c * (1f - abs(hh % 2f - 1f))
        val (r1, g1, b1) =
            when (hh.toInt()) {
                0 -> Triple(c, x, 0f)
                1 -> Triple(x, c, 0f)
                2 -> Triple(0f, c, x)
                3 -> Triple(0f, x, c)
                4 -> Triple(x, 0f, c)
                else -> Triple(c, 0f, x)
            }
        val m = l - c / 2f
        return Px.argbF(1f, r1 + m, g1 + m, b1 + m)
    }

    /**
     * A leaf colour of a ramp (whose middle hue is [midHue]) turned by [dye]: the ramp's own spread
     * of hue is kept about the new hue, so the deep shade and the glint still differ; at dusk and
     * at night the colours change less (the light, not the leaves, is what colours them then).
     */
    private fun dyed(c: Int, midHue: Float, dye: Dye, look: TreeLook): Int {
        val x = hsl(c)
        val k =
            when (look) {
                TreeLook.DAY -> 1f
                TreeLook.DUSK -> .7f
                TreeLook.NIGHT -> .45f
            }
        val spread = ((x[0] - midHue + 540f) % 360f) - 180f
        val h = dye.hue + spread * dye.spread
        val s = (x[1] * (1f + (dye.sat - 1f) * k)).coerceIn(0f, 1f)
        val l = (x[2] + dye.light * k).coerceIn(0f, 1f) * (if (look == TreeLook.NIGHT) .8f else 1f)
        return mixc(fromHsl(h, s, l), c, 1f - k)
    }

    /** The leaves' pixels in [look] and [season]. */
    private fun leafColour(p: Planes, look: TreeLook, season: TreeSeason, variant: Int): IntArray {
        val shape = Trees.shapes[variant]
        val r =
            when (look) {
                TreeLook.DAY -> DAY
                TreeLook.DUSK -> DUSK
                TreeLook.NIGHT -> NIGHT
            }
        val olive = 0xFF7C9C3E.toInt()
        val dye = dyeOf(season, variant)
        val warm =
            if (dye != null) 0f else shape.warmth * (if (look == TreeLook.NIGHT) .25f else 1f)
        var deep = mixc(r.deep, olive, warm * .5f)
        var shade = mixc(r.shade, olive, warm * .6f)
        var mid = mixc(r.mid, olive, warm)
        var light = mixc(r.light, olive, warm * .8f)
        var glint = r.glint
        if (dye != null) {
            val midHue = hsl(r.mid)[0]
            deep = dyed(deep, midHue, dye, look)
            shade = dyed(shade, midHue, dye, look)
            mid = dyed(mid, midHue, dye, look)
            light = dyed(light, midHue, dye, look)
            glint = dyed(glint, midHue, dye, look)
        }
        val speckle = dye?.speckle ?: 0f
        val out = IntArray(p.w * p.h)
        for (i in out.indices) {
            val a = p.alpha[i]
            if (a <= 0.004f) continue
            var v = p.tone[i].coerceIn(0f, 1f)
            // blossom: the flowers cluster, so the canopy is speckled light and dark
            if (speckle > 0f)
                v =
                    (v +
                            (Noise.value((i % p.w) * .8f, (i / p.w) * .8f, 900 + variant) - .5f) *
                                speckle)
                        .coerceIn(0f, 1f)
            // four values: deep, shade, mid, light, with a glint on the brightest leaves
            var col =
                if (v < .34f) mixc(deep, shade, v / .34f)
                else if (v < .62f) mixc(shade, mid, (v - .34f) / .28f)
                else mixc(mid, light, (v - .62f) / .38f)
            if (v > .86f) col = mixc(col, glint, (v - .86f) / .14f * .55f)
            if (r.lampAmount > 0f)
                col = mixc(col, r.lamp, (p.lamp[i] * r.lampAmount).coerceIn(0f, .75f))
            out[i] = (col and 0xFFFFFF) or ((a * 255f + .5f).toInt().coerceIn(0, 255) shl 24)
        }
        return out
    }
}

/**
 * How a tree of the moment is put together from its season looks: which looks are drawn, in what
 * order, and how strongly, for a [SeasonState]. The bare tree goes under, the canopy looks over it
 * at the leaf's fullness, two of them blended where the year is between looks (summer into the
 * turning yellow-green into autumn's colour; spring's thin green into summer's; blossom over
 * spring), so the seasons cross over a couple of weeks and nothing pops. Pure, so it is tested.
 */
internal object TreeDress {
    /** The order the looks are drawn in, from the one underneath. */
    val ORDER =
        arrayOf(
            TreeSeason.BARE,
            TreeSeason.SPRING,
            TreeSeason.SUMMER,
            TreeSeason.TURNING,
            TreeSeason.AUTUMN,
            TreeSeason.BLOSSOM,
        )

    /**
     * Writes into [out] (one slot per [TreeSeason], by ordinal) the alpha each look is drawn with
     * for a tree of [variant] in [s]; 0 for a look not drawn.
     */
    fun plan(s: SeasonState, variant: Int, out: FloatArray) {
        java.util.Arrays.fill(out, 0f)
        if (Trees.evergreen(variant)) {
            out[TreeSeason.SUMMER.ordinal] = 1f
            return
        }
        // in autumn the canopy holds its colour and then drops quickly (a thin canopy over the sky
        // goes grey, so it is not held thin for long)
        val canopy = if (s.autumn > .01f) Px.smooth(0f, .7f, s.leaf) else s.leaf
        // the bare tree under the canopy, fading out as the canopy fills (it is covered by then)
        out[TreeSeason.BARE.ordinal] = 1f - Px.smooth(.6f, 1f, canopy)
        if (canopy <= .01f) return
        if (s.autumn > .01f) {
            if (s.autumn < .5f) {
                out[TreeSeason.SUMMER.ordinal] = canopy
                out[TreeSeason.TURNING.ordinal] = canopy * s.autumn * 2f
            } else {
                if (s.autumn < .99f) out[TreeSeason.TURNING.ordinal] = canopy
                out[TreeSeason.AUTUMN.ordinal] = canopy * (s.autumn - .5f) * 2f
            }
        } else {
            if (s.mature < .99f) out[TreeSeason.SPRING.ordinal] = canopy
            if (s.mature > .01f) out[TreeSeason.SUMMER.ordinal] = canopy * s.mature
            if (s.blossom > .01f && Trees.blossoms(variant))
                out[TreeSeason.BLOSSOM.ordinal] = canopy * s.blossom
        }
    }

    /** The canopy's alpha in [plan]'s [out]: what the tree's shadow is cast with. */
    fun canopy(out: FloatArray): Float =
        max(
            max(out[TreeSeason.SPRING.ordinal], out[TreeSeason.SUMMER.ordinal]),
            max(out[TreeSeason.TURNING.ordinal], out[TreeSeason.AUTUMN.ordinal]),
        )
}
