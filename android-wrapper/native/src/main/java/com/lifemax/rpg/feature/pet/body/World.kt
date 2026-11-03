package com.lifemax.rpg.feature.pet.body

import kotlin.math.floor

/** Every place the cat can stand. The names are the web room's own surface keys. */
enum class SurfaceId(val key: String) {
    FLOOR("floor"),
    BED("bed"),
    SILL("sill"),
    MID("mid"),
    TOP("top"),
    COUCH("couch"),
    COUNTER("counter"),
    LSILL("lsill"),
    KSILL("ksill"),
    POUF("pouf"),
    CUSHION("cushion"),
    BOX("box"),

    /** Parkour surfaces (not in the web room): the fridge top, the bookcase top, the side table. */
    FRIDGE("fridge"),
    BOOKCASE("bookcase"),
    TABLE("sidetable"),
}

/**
 * A standable surface, in room pixels.
 *
 * @property y screen row of the feet. For [SurfaceId.FLOOR] this is the row at the art depth; the
 *   real row depends on z, see [World.floorY].
 * @property x0 left end of the walkable range.
 * @property x1 right end of the walkable range.
 * @property z depth the cat is drawn at while on it.
 * @property prop true for the free-standing depth props (pouf, cushion, box).
 * @property half half the prop's drawn width (zero for painted surfaces).
 */
class Surface(
    val id: SurfaceId,
    val y: Float,
    val x0: Float,
    val x1: Float,
    val z: Float,
    val prop: Boolean = false,
    val half: Float = 0f,
)

/**
 * The room as the cat's body sees it: the surface table and the depth projection, both copied from
 * the web room (PET_SURF, PET_PROPS and the petDepth* functions) so a position means the same thing
 * on both sides of the bridge.
 */
object World {
    const val HOUSE_MIN_X = -154f
    const val HOUSE_MAX_X = 346f
    const val ART_Z = 35f
    const val MAX_Z = 100f
    const val HORIZON_Y = 98f
    const val FLOOR_Y_ART = 132f

    /** The box is entered and left this much nearer the viewer than its own depth. */
    const val BOX_FRONT_Z = 9f

    private const val LIVING_END = -4f
    private const val KITCHEN_START = 196f
    private const val SCALE_AT_BACK_PCT = 55
    private const val SCALE_RANGE_PCT = 130f
    private const val WALK_SPEED_BASE_PCT = 70
    private const val WALK_SPEED_Z_GAIN_PCT = 60f
    private const val PROP_TOP_LIFT = 1.6f
    private const val PROP_FOOT_SPAN = 0.55f

    /** Built from the same formulas the web uses for its props, so the numbers match exactly. */
    private fun prop(id: SurfaceId, x: Float, z: Float, top: Float, half: Float): Surface {
        val k = depthScalePct(z) / 100f
        val w = half * k
        return Surface(
            id = id,
            y = Mathx.jsRound(floorY(z) - top * k * PROP_TOP_LIFT),
            x0 = Mathx.jsRound(x - w * PROP_FOOT_SPAN),
            x1 = Mathx.jsRound(x + w * PROP_FOOT_SPAN),
            z = z,
            prop = true,
            half = w,
        )
    }

    private fun flat(id: SurfaceId, y: Float, x0: Float, x1: Float) = Surface(id, y, x0, x1, ART_Z)

    private val table: Array<Surface> =
        arrayOf(
            Surface(SurfaceId.FLOOR, FLOOR_Y_ART, HOUSE_MIN_X, HOUSE_MAX_X, ART_Z),
            flat(SurfaceId.BED, 128f, 36f, 36f),
            flat(SurfaceId.SILL, 74f, 38f, 76f),
            flat(SurfaceId.MID, 100f, 146f, 158f),
            flat(SurfaceId.TOP, 64f, 150f, 158f),
            flat(SurfaceId.COUCH, 109f, -136f, -60f),
            flat(SurfaceId.COUNTER, 91f, 286f, 340f),
            flat(SurfaceId.LSILL, 73f, -124f, -72f),
            flat(SurfaceId.KSILL, 74f, 304f, 338f),
            prop(SurfaceId.POUF, -34f, 45f, 5f, 12f),
            prop(SurfaceId.CUSHION, -120f, 66f, 7f, 18f),
            prop(SurfaceId.BOX, 290f, 42f, 3.6f, 20f),
            flat(SurfaceId.FRIDGE, 31f, 214f, 238f),
            flat(SurfaceId.BOOKCASE, 33f, 108f, 136f),
            flat(SurfaceId.TABLE, 105f, -22f, -12f),
        )

    /** The surface record for [id]. */
    fun surface(id: SurfaceId): Surface = table[id.ordinal]

    /** Depth scale in percent, 55 at the back wall to 185 at the glass (petDepthScalePct). */
    fun depthScalePct(z: Float): Int =
        SCALE_AT_BACK_PCT + floor(Mathx.clamp(z, 0f, MAX_Z) * SCALE_RANGE_PCT / 100f).toInt()

    /** Depth scale as a multiplier (petK). */
    fun depthScale(z: Float): Float = depthScalePct(z) / 100f

    /** Screen row of the floor under a cat at depth [z] (petFloorY). */
    fun floorY(z: Float): Float = HORIZON_Y + (FLOOR_Y_ART - HORIZON_Y) * depthScalePct(z) / 100f

    /** Walk speed multiplier at depth [z], in percent (petWalkSpeedPct). */
    fun walkSpeedPct(z: Float): Int =
        WALK_SPEED_BASE_PCT +
            floor(Mathx.clamp(z, 0f, MAX_Z) * WALK_SPEED_Z_GAIN_PCT / 100f).toInt()

    /**
     * The room an x is in: 0 living (x < -4), 1 bedroom (to 196), 2 kitchen (the web room split).
     */
    fun roomOf(x: Float): Int = if (x < LIVING_END) 0 else if (x < KITCHEN_START) 1 else 2

    /** Keep an x inside the walkable house (petClampHouse). */
    fun clampHouse(x: Float): Float = Mathx.clamp(x, HOUSE_MIN_X, HOUSE_MAX_X)

    /** Keep an x on the given surface's walkable range. */
    fun clampTo(id: SurfaceId, x: Float): Float {
        val s = surface(id)
        return Mathx.clamp(x, s.x0, s.x1)
    }

    /** Feet row for a cat standing on [id] at depth [z]. */
    fun groundY(id: SurfaceId, z: Float): Float =
        if (id == SurfaceId.FLOOR) floorY(z) else surface(id).y
}
