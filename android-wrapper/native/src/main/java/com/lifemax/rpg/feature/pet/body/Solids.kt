package com.lifemax.rpg.feature.pet.body

import com.lifemax.rpg.feature.pet.body.SurfaceId.BENCH
import com.lifemax.rpg.feature.pet.body.SurfaceId.BOOKCASE
import com.lifemax.rpg.feature.pet.body.SurfaceId.COUCH
import com.lifemax.rpg.feature.pet.body.SurfaceId.COUNTER
import com.lifemax.rpg.feature.pet.body.SurfaceId.DESK
import com.lifemax.rpg.feature.pet.body.SurfaceId.FRIDGE
import com.lifemax.rpg.feature.pet.body.SurfaceId.MID
import com.lifemax.rpg.feature.pet.body.SurfaceId.SHELF
import com.lifemax.rpg.feature.pet.body.SurfaceId.SINK
import com.lifemax.rpg.feature.pet.body.SurfaceId.TABLE
import com.lifemax.rpg.feature.pet.body.SurfaceId.TOP
import com.lifemax.rpg.feature.pet.body.SurfaceId.TUB

/**
 * Furniture a flying cat cannot pass through, as rectangles in room pixels (world x, rows from
 * [top] down to [bottom]). The extents are the room layers' own: the manifest
 * (`pet/room/layers.json`) gives each piece's box, shifted by its room's house x (living -192,
 * bedroom 0, kitchen 192). A leap to or from a surface the piece carries ignores it (a cat does not
 * collide with the counter it jumps onto); every other leap must keep its feet clear of it. That is
 * what makes the window sill above the sofa reachable only over the sofa, and the kitchen sill only
 * over the counter.
 *
 * @property owners the surfaces that stand on this piece, as a bit mask of [SurfaceId.ordinal].
 */
internal class Solid(
    val name: String,
    val x0: Float,
    val x1: Float,
    val top: Float,
    val bottom: Float,
    val owners: Int,
) {
    /** True when a leap between [a] and [b] must keep clear of this solid. */
    fun blocks(a: SurfaceId, b: SurfaceId): Boolean = owners and (bit(a) or bit(b)) == 0

    /** True when a cat's feet at ([x], [y]) are inside the solid grown by [margin]. */
    fun contains(x: Float, y: Float, margin: Float): Boolean =
        x >= x0 - margin && x <= x1 + margin && y >= top - margin && y <= bottom

    companion object {
        fun bit(id: SurfaceId): Int = 1 shl id.ordinal
    }
}

/** The room's solids and the arc test. */
internal object Solids {
    private fun owned(vararg ids: SurfaceId): Int = ids.fold(0) { m, id -> m or Solid.bit(id) }

    /** Sofa 30..162 in the living room (house x -192): its back rest is at row 86. */
    private val SOFA = Solid("sofa", -162f, -30f, 86f, 128f, owned(COUCH))

    /** Side table with its plant, living room x 166..185. */
    private val SIDE_TABLE = Solid("side_table", -26f, -7f, 104f, 126f, owned(TABLE))

    /** Bookcase 106..137, bedroom (house x 0): top row 32 (its plant stands above it). */
    private val BOOKCASE_BODY = Solid("bookcase", 106f, 138f, 32f, 112f, owned(BOOKCASE))

    /**
     * The cat tree's top slab, over the mid slab: a drop from above to the mid slab must go round
     * it.
     */
    private val TOP_SLAB = Solid("cat_tree_top", 124f, 188f, 64f, 76f, owned(TOP))

    /** The mid slab. */
    private val MID_SLAB = Solid("cat_tree_mid", 122f, 182f, 100f, 109f, owned(MID))

    /** The post and cubby between and beside the slabs. */
    private val TREE = Solid("cat_tree_post", 155f, 188f, 76f, 127f, owned(MID, TOP))

    /** The fridge, kitchen x 14..53 (house x 192): top row 30. */
    private val FRIDGE_BODY = Solid("fridge", 206f, 246f, 30f, 128f, owned(FRIDGE))

    /** Counter 60..183 with the kettle on it, kitchen (house x 192): worktop row 90. */
    private val COUNTER_BODY = Solid("counter", 252f, 376f, 90f, 128f, owned(COUNTER))

    // The pieces of the rooms that open with the cats' bond (layers_ext.json; house x = the room's
    // x0: sun 384, study -384, bath -576). A solid is there whether or not its room is open: no
    // leap of a cat in another room comes near it.

    /** The Sunroom's window bench, sun x 40..144: its cushions are at row 100. */
    private val SUN_BENCH = Solid("sun_bench", 424f, 528f, 100f, 128f, owned(BENCH))

    /** The wicker chair beside it, sun x 142..184. */
    private val WICKER_CHAIR = Solid("wicker_chair", 526f, 568f, 82f, 128f, 0)

    /** The Study's bookshelf, study x 8..44: top row 34, reached from the desk. */
    private val STUDY_SHELF = Solid("study_shelf", -376f, -340f, 34f, 128f, owned(SHELF))

    /** The desk with its lamp, study x 52..148: the top is row 96, the lamp reaches row 84. */
    private val DESK_BODY = Solid("desk", -332f, -236f, 84f, 128f, owned(DESK))

    /** The desk chair, study x 148..176. */
    private val DESK_CHAIR = Solid("desk_chair", -236f, -208f, 84f, 128f, 0)

    /** The claw-foot tub with its tap, bath x 4..104: the rim is row 92. */
    private val TUB_BODY = Solid("bathtub", -572f, -472f, 88f, 128f, owned(TUB))

    /** The sink cabinet, bath x 122..178: the basin is row 80, the tap reaches row 72. */
    private val SINK_BODY = Solid("sink_cabinet", -454f, -398f, 72f, 122f, owned(SINK))

    /** Every solid. */
    val all: Array<Solid> =
        arrayOf(
            SOFA,
            SIDE_TABLE,
            BOOKCASE_BODY,
            TOP_SLAB,
            MID_SLAB,
            TREE,
            FRIDGE_BODY,
            COUNTER_BODY,
            SUN_BENCH,
            WICKER_CHAIR,
            STUDY_SHELF,
            DESK_BODY,
            DESK_CHAIR,
            TUB_BODY,
            SINK_BODY,
        )

    /**
     * True when a flight from ([x0], [y0]) to ([x1], [y1]) with [apex] and [flight] seconds,
     * between surfaces [from] and [to], keeps the cat's feet out of every solid the leap is not
     * part of.
     */
    fun clear(
        from: SurfaceId,
        to: SurfaceId,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        apex: Float,
        flight: Float,
    ): Boolean {
        val g = Tuning.Parkour.GRAVITY
        val v0 = kotlin.math.sqrt(2f * g * apex)
        val n = Tuning.Parkour.ARC_SAMPLES
        for (i in 1 until n) {
            val t = flight * i / n
            val x = Mathx.lerp(x0, x1, t / flight)
            val y = y0 - Ballistics.lift(v0, t, g)
            for (s in all) {
                if (s.blocks(from, to) && s.contains(x, y, Tuning.Parkour.SOLID_MARGIN)) {
                    return false
                }
            }
        }
        return true
    }
}
