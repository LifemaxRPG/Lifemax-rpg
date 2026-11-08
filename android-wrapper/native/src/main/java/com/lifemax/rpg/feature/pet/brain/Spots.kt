package com.lifemax.rpg.feature.pet.brain

import com.lifemax.rpg.feature.pet.body.Feeding
import com.lifemax.rpg.feature.pet.body.Occluders
import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.body.World

/**
 * A place in the house an activity can happen at: a point on a [surface] at house [x] (and floor
 * depth [z]), what it is good for ([flags], bits of [Spots]), the page's PET_SPOTS id [pageId]
 * (whose liking the page sends; null for the parkour tops the page does not know) and, for floor
 * places, which way the cat faces there ([face], 0 any). Raised places hold one cat ([single]);
 * floor places keep [BrainTuning.Places.FLOOR_GAP] between cats.
 */
class Spot(
    val index: Int,
    val id: String,
    val pageId: String?,
    val surface: SurfaceId,
    val x: Float,
    val z: Float,
    val flags: Int,
    val face: Int = 0,
) {
    /** True when this spot has [flag]. */
    fun has(flag: Int): Boolean = (flags and flag) != 0

    val onFloor: Boolean
        get() = surface == SurfaceId.FLOOR

    /** One cat at a time (every raised place; each sofa end is its own place). */
    val single: Boolean
        get() = !onFloor

    /** Low: the floor or a free-standing prop (pouf, cushion, box) or the bed. */
    val low: Boolean
        get() = onFloor || World.surface(surface).prop || surface == SurfaceId.BED

    /** The room the spot is in (0 living, 1 bedroom, 2 kitchen). */
    val room: Int = World.roomOf(x)

    /**
     * A floor place where nearer furniture would hide most of a resting cat (in either room): no
     * activity is chosen there ([Occluders]).
     */
    val hidden: Boolean = onFloor && Occluders.hidden(x, z)
}

/**
 * The house's places (plan 1.5 SpotBook, 1.8 places): the surfaces of `body/World.kt` at the points
 * the web room used (PET_SPOTS) plus the parkour tops, the floor of each room, two hidden corners
 * behind the furniture the room draws in front of the cats, the scratching places and each cat's
 * bowl. The table is fixed; per-cat liking lives in [SpotBook].
 */
object Spots {
    const val SLEEP = 1
    const val REST = 2
    const val SOFT = 4
    const val SUNNY = 8
    const val WINDOW = 16
    const val HIGH = 32
    const val HIDDEN = 64
    const val PARKOUR = 128
    const val SCRATCH = 256
    const val ROOM_FLOOR = 512
    const val BOWL = 1024

    private val list = ArrayList<Spot>()

    private fun add(
        id: String,
        page: String?,
        surface: SurfaceId,
        x: Float,
        flags: Int,
        z: Float = World.ART_Z,
        face: Int = 0,
    ): Spot = Spot(list.size, id, page, surface, x, z, flags, face).also { list += it }

    val BED = add("bed", "bed", SurfaceId.BED, 36f, SLEEP or REST or SOFT)
    val SILL = add("sill", "sill", SurfaceId.SILL, 57f, WINDOW or SUNNY or REST or SLEEP)
    val TREE_MID = add("tree mid", "tree", SurfaceId.MID, 152f, REST)
    val TREE_TOP = add("tree top", "tree", SurfaceId.TOP, 154f, SLEEP or REST or HIGH or PARKOUR)
    val COUCH_L = add("sofa", "couch", SurfaceId.COUCH, -122f, SLEEP or REST or SOFT)
    val COUCH_R = add("sofa end", "couch", SurfaceId.COUCH, -76f, SLEEP or REST or SOFT)
    val LSILL = add("living sill", "lsill", SurfaceId.LSILL, -98f, WINDOW or SUNNY or REST)
    val KSILL = add("kitchen sill", "ksill", SurfaceId.KSILL, 321f, WINDOW or REST)
    val POUF = add("pouf", "pouf", SurfaceId.POUF, -34f, REST)
    val CUSHION = add("cushion", "cushion", SurfaceId.CUSHION, -120f, SLEEP or REST or SOFT)
    val BOX = add("box", "box", SurfaceId.BOX, 290f, SLEEP or REST or HIDDEN)
    val COUNTER = add("counter", "counter", SurfaceId.COUNTER, 300f, REST or HIGH)
    val FRIDGE = add("fridge", null, SurfaceId.FRIDGE, 226f, REST or HIGH or PARKOUR)
    val BOOKCASE = add("bookcase", null, SurfaceId.BOOKCASE, 122f, REST or HIGH or PARKOUR)
    val SIDE_TABLE = add("side table", null, SurfaceId.TABLE, -17f, REST or PARKOUR)
    val RUG = add("rug", "rug", SurfaceId.FLOOR, 104f, REST or ROOM_FLOOR, z = 58f)
    val LIVING_RUG = add("living rug", "lrug", SurfaceId.FLOOR, -62f, REST or ROOM_FLOOR, z = 62f)
    val KITCHEN =
        add("kitchen floor", "kitchen", SurfaceId.FLOOR, 250f, REST or ROOM_FLOOR, z = 52f)
    val BEHIND_BED = add("behind the bed", "rug", SurfaceId.FLOOR, 40f, REST or HIDDEN, z = 10f)
    // tucked away at the sofa's far end behind the floor cushion, in front of the sofa itself:
    // behind it (once at -128, depth 22) the sofa hid all of a cat in the 2.5D room
    val BEHIND_CUSHION =
        add("behind the cushion", "lrug", SurfaceId.FLOOR, -150f, REST or HIDDEN or SLEEP, z = 30f)
    val SCRATCH_TREE = add("tree post", "tree", SurfaceId.FLOOR, 134f, SCRATCH, face = 1)
    // the sofa's front, clear of the pouf (at -50 the pouf hid over a third of the cat)
    val SCRATCH_SOFA = add("sofa arm", "couch", SurfaceId.FLOOR, -60f, SCRATCH, face = -1)
    val BOWL_0 =
        add("bowl", null, SurfaceId.FLOOR, Feeding.bowlX(0), BOWL, face = Feeding.facing(0))
    val BOWL_1 =
        add("bowl", null, SurfaceId.FLOOR, Feeding.bowlX(1), BOWL, face = Feeding.facing(1))

    /** Every spot, by [Spot.index]. */
    val all: List<Spot> = list

    val count: Int
        get() = list.size

    /** Cat [slot]'s bowl. */
    fun bowl(slot: Int): Spot = if (slot == 0) BOWL_0 else BOWL_1

    /** The spot a cat standing on [surface] at [x] is at (the nearest on that surface), or null. */
    fun at(surface: SurfaceId, x: Float): Spot? {
        var best: Spot? = null
        var bestD = Float.MAX_VALUE
        for (i in list.indices) {
            val s = list[i]
            if (s.surface != surface) continue
            val d = Math.abs(s.x - x)
            if (d < bestD) {
                bestD = d
                best = s
            }
        }
        return if (surface == SurfaceId.FLOOR && bestD > NEAR_FLOOR_PX) null else best
    }

    private const val NEAR_FLOOR_PX = 24f
}
