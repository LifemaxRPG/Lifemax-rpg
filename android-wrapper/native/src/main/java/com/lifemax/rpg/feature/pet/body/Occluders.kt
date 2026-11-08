package com.lifemax.rpg.feature.pet.body

/**
 * What of a cat on the floor the room's furniture hides, so the brain never lets a cat rest where
 * it can barely be seen (the owner's "the cats clip behind the sofa and are barely visible").
 *
 * Both rooms draw back to front by floor row: a piece whose front row is nearer than the row of a
 * cat's feet is drawn over the cat. The classic room has only the atlas furniture (the bed's two
 * halves, the box's front, the pouf, the floor cushion); the 2.5D room also has the solid pieces
 * cut out of the backdrops (the sofa, the lamp, the side table, the cat tree, the counter, the
 * fridge). Each is kept here as the box of its sprite (world x, screen rows) and its front row, the
 * numbers of `room/layers.json` and the room's own furniture table (OccludersTest checks them
 * against the shipped art, and measures the real pixels). The box is a little more than the pixels,
 * so a share from here errs toward "hidden".
 *
 * A cat is a box too: [CAT_W] by [CAT_H] art pixels at full scale (a cat lying or loafing), scaled
 * by its depth, centred on its x, feet on its floor row. [hiddenShare] is the part of that box
 * under nearer furniture; above [MAX_HIDDEN] the cat is hidden. Nothing here allocates.
 */
object Occluders {
    /**
     * One piece: world x [x0]..[x1], screen rows [top]..[bottom], drawn over cats behind [front].
     */
    class Piece(
        val name: String,
        val x0: Float,
        val x1: Float,
        val top: Float,
        val bottom: Float,
        val front: Float,
        val layered: Boolean,
    )

    /** The most of a resting cat furniture may hide. */
    const val MAX_HIDDEN = 0.35f

    /** A resting cat's box at full scale, art pixels. */
    const val CAT_W = 36f
    const val CAT_H = 22f

    private const val SAMPLES = 8
    private const val Z_STEP = 1f

    private fun prop(name: String, x: Float, z: Float, w: Float, h: Float): Piece {
        val k = World.depthScale(z)
        val row = World.floorY(z)
        return Piece(name, x - w * k / 2, x + w * k / 2, row - h * k, row, row, false)
    }

    private fun item(name: String, x: Float, y: Float, w: Float, h: Float, z: Float) =
        Piece(name, x, x + w, y, y + h, World.floorY(z), false)

    /** A solid piece: its sprite's box, the row of its base and how far its front stands out. */
    private fun solid(
        name: String,
        x0: Float,
        x1: Float,
        top: Float,
        bottom: Float,
        base: Float,
        front: Float,
    ) = Piece(name, x0, x1, top, bottom, base - front, true)

    /** The atlas furniture of both rooms (RoomScene's table) and the 2.5D room's solid pieces. */
    val pieces: List<Piece> =
        listOf(
            item("bed_back", BED_X, BED_BACK_Y, BED_W, BED_BACK_H, Z_BED_BACK),
            item("bed_front", BED_X, BED_FRONT_Y, BED_W, BED_FRONT_H, Z_BED_FRONT),
            prop("box_front", BOX_X, BOX_Z + BOX_FRONT_LIFT, BOX_W, BOX_H),
            prop("pouf", POUF_X, POUF_Z, POUF_W, POUF_H),
            prop("cushion", CUSHION_X, CUSHION_Z, CUSHION_W, CUSHION_H),
            solid("sofa", -162f, -30f, 86f, 128f, 128f, 0f),
            solid("sofa_cushion_yellow", -140f, -126f, 98f, 108f, 128f, 6f),
            solid("sofa_cushion_pink", -68f, -52f, 100f, 124f, 128f, -1f),
            solid("floor_lamp", -183f, -162f, 26f, 129f, 129f, 0f),
            solid("side_table_plant", -26f, -7f, 70f, 126f, 126f, 0f),
            solid("bookcase_plant", 106f, 138f, 0f, 112f, 112f, 0f),
            solid("cat_tree_post", 147f, 180f, 76f, 129f, 137f, 0f),
            solid("cat_tree_cubby", 164f, 184f, 80f, 100f, 137f, 0f),
            solid("cat_tree_slab_base", 136f, 182f, 128f, 137f, 137f, 0f),
            solid("cat_tree_slab_mid", 122f, 182f, 100f, 109f, 137f, 0f),
            solid("cat_tree_slab_top", 124f, 188f, 64f, 76f, 137f, 0f),
            solid("counter", 252f, 376f, 76f, 128f, 128f, 0f),
            solid("fridge", 206f, 246f, 30f, 128f, 128f, 0f),
        )

    /**
     * The share (0..1) of a resting cat on the floor at [x], depth [z] that nearer furniture hides,
     * in the 2.5D room ([layered]) or the classic room.
     */
    fun hiddenShare(x: Float, z: Float, layered: Boolean = true): Float {
        val k = World.depthScale(z)
        val feet = World.floorY(z)
        val w = CAT_W * k
        val h = CAT_H * k
        var hidden = 0
        for (i in 0 until SAMPLES) {
            val px = x - w / 2 + w * (i + HALF) / SAMPLES
            for (j in 0 until SAMPLES) {
                val py = feet - h + h * (j + HALF) / SAMPLES
                if (covered(px, py, feet, layered)) hidden++
            }
        }
        return hidden.toFloat() / (SAMPLES * SAMPLES)
    }

    /** True when the cat at [x], [z] would be hidden in either room. */
    fun hidden(x: Float, z: Float): Boolean =
        hiddenShare(x, z, true) > MAX_HIDDEN || hiddenShare(x, z, false) > MAX_HIDDEN

    /**
     * The nearest depth at or in front of [z] where a cat at [x] is not hidden (toward the glass),
     * or [z] itself when it is not hidden there.
     */
    fun visibleZ(x: Float, z: Float): Float {
        var d = z
        while (d < World.MAX_Z && hidden(x, d)) d += Z_STEP
        return d.coerceAtMost(World.MAX_Z)
    }

    private fun covered(px: Float, py: Float, feet: Float, layered: Boolean): Boolean {
        for (i in pieces.indices) {
            val p = pieces[i]
            if (p.layered && !layered) continue
            if (p.front <= feet + CAT_AFTER) continue
            if (px >= p.x0 && px < p.x1 && py >= p.top && py < p.bottom) return true
        }
        return false
    }

    private const val HALF = 0.5f

    /** A cat sorts just after what stands on its own row (DepthOrder's rule). */
    private const val CAT_AFTER = 0.001f

    // the room's own furniture (RoomLayout, RoomScene), world x and screen rows
    private const val BED_X = 10f
    private const val BED_W = 56f
    private const val BED_BACK_Y = 118f
    private const val BED_BACK_H = 16f
    private const val BED_FRONT_Y = 126f
    private const val BED_FRONT_H = 10f
    private const val Z_BED_BACK = 30f
    private const val Z_BED_FRONT = 40f
    private const val BOX_X = 290f
    private const val BOX_Z = 42f
    private const val BOX_FRONT_LIFT = 0.2f
    private const val BOX_W = 44f
    private const val BOX_H = 24f
    private const val POUF_X = -34f
    private const val POUF_Z = 45f
    private const val POUF_W = 28f
    private const val POUF_H = 19f
    private const val CUSHION_X = -120f
    private const val CUSHION_Z = 66f
    private const val CUSHION_W = 40f
    private const val CUSHION_H = 19f
}
