package com.lifemax.rpg.feature.pet.brain

import com.lifemax.rpg.feature.pet.body.Feeding
import com.lifemax.rpg.feature.pet.body.Occluders
import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.body.World
import com.lifemax.rpg.feature.pet.room.HouseMap

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

    /** The room the spot is in, as a place among the open rooms from the left. */
    val room: Int
        get() = World.roomOf(x)

    /**
     * Whether the spot is in a room that is open: a place of a room the cats have not been given
     * yet ([HouseMap]) is never chosen, so the table can hold every room's places.
     */
    val open: Boolean
        get() = x >= HouseMap.current.x0 && x < HouseMap.current.x1

    /**
     * A floor place where nearer furniture would hide most of a resting cat (in either room), or
     * where a cat sitting up would have its head inside the cat tree's platform: no activity is
     * chosen there ([Occluders]). A bowl stays where the page put it; the room draws a cat at the
     * second bowl, half under the tree, in front of the tree.
     */
    val hidden: Boolean =
        onFloor && (Occluders.hidden(x, z) || (!has(Spots.BOWL) && Occluders.clipped(x, z, face)))
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

    /**
     * A place worth a visit (roaming's Explore goes to any place with this flag): set on every
     * place by [add] unless it carries [NO_VISIT], so a place added to the table (a new room's
     * sofa, shelf, bowl) is explored without any change to the planner.
     */
    const val VISIT = 2048
    const val NO_VISIT = 4096

    private val list = ArrayList<Spot>()

    private fun add(
        id: String,
        page: String?,
        surface: SurfaceId,
        x: Float,
        flags: Int,
        z: Float = World.ART_Z,
        face: Int = 0,
    ): Spot =
        Spot(
                list.size,
                id,
                page,
                surface,
                x,
                z,
                if (flags and NO_VISIT != 0) flags else flags or VISIT,
                face,
            )
            .also { list += it }

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
    // in front of the tree: at the cats' own floor depth the cat stood under the middle platform,
    // and its ears came out above the platform's front ("poking through the cat tree")
    val SCRATCH_TREE =
        add(
            "tree post",
            "tree",
            SurfaceId.FLOOR,
            134f,
            SCRATCH or NO_VISIT,
            z = TREE_FRONT_Z,
            face = 1,
        )
    // the sofa's front, clear of the pouf (at -50 the pouf hid over a third of the cat)
    val SCRATCH_SOFA =
        add("sofa arm", "couch", SurfaceId.FLOOR, -60f, SCRATCH or NO_VISIT, face = -1)
    val BOWL_0 =
        add("bowl", null, SurfaceId.FLOOR, Feeding.bowlX(0), BOWL, face = Feeding.facing(0))
    val BOWL_1 =
        add("bowl", null, SurfaceId.FLOOR, Feeding.bowlX(1), BOWL, face = Feeding.facing(1))

    // The floor places of the rooms that open as the cats grow close (house.json). They are in the
    // table always and chosen only while their room is open ([Spot.open]); the page has no names
    // for
    // them (no pageId), and the classic room never draws them. Raised places (the bench, the desk,
    // the tub) come with their surfaces.
    val SUN_PATCH =
        add("sun patch", null, SurfaceId.FLOOR, 470f, REST or SUNNY or ROOM_FLOOR, z = 48f)
    val STUDY_RUG = add("study rug", null, SurfaceId.FLOOR, -294f, REST or ROOM_FLOOR, z = 62f)
    val BATH_MAT = add("bath mat", null, SurfaceId.FLOOR, -496f, REST or ROOM_FLOOR, z = 62f)

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

    /** The nearest floor depth in front of the cat tree's footprint (row 137.4; it ends at 137). */
    private const val TREE_FRONT_Z = 47f
}
