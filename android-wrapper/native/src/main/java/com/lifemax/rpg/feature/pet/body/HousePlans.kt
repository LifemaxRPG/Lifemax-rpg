package com.lifemax.rpg.feature.pet.body

import com.lifemax.rpg.feature.pet.room.HouseMap
import kotlin.math.abs

/**
 * The room a cat has set aside for itself while it carries out a plan, so that two cats in the
 * house keep a polite distance: a stretch of the floor (everywhere the plan takes the cat, from
 * where it stands) and the piece of furniture it will end up on. A cat reads the other's claim when
 * it plans, and plans only what keeps clear of it. A cat that is doing nothing claims the spot it
 * stands on.
 */
internal class Claim {
    /** True when the cat is, or will be, on the floor; [lo]..[hi] is the stretch it uses. */
    var onFloor = true
        private set

    var lo = 0f
        private set

    var hi = 0f
        private set

    /** The raised surface the cat is on or is going to, or [SurfaceId.FLOOR] for none. */
    var perch = SurfaceId.FLOOR
        private set

    /** Where on [perch] the cat will be. */
    var perchX = 0f
        private set

    /**
     * Every raised surface the plan lands on, on the way up and on the way down, as a bit mask of
     * [SurfaceId.ordinal]: the counter and the fridge for a cat going to the fridge top. Two cats
     * never plan to land on the same single-occupancy surface (see [Claim.clashes]).
     */
    var passes = 0
        private set

    /** The cat stands still at [x] on [surface]. */
    fun standAt(surface: SurfaceId, x: Float) {
        if (surface == SurfaceId.FLOOR) {
            onFloor = true
            lo = x
            hi = x
            perch = SurfaceId.FLOOR
            passes = 0
        } else {
            onFloor = false
            perch = surface
            perchX = x
            passes = Solid.bit(surface)
        }
    }

    /**
     * The cat will use the floor from [from] to [to] and then rest on [perch] at [perchX], having
     * landed on every surface in [passes] on the way.
     */
    fun reserve(from: Float, to: Float, perch: SurfaceId, perchX: Float, passes: Int = 0) {
        onFloor = true
        lo = from
        hi = to
        this.perch = perch
        this.perchX = perchX
        this.passes = if (perch == SurfaceId.FLOOR) passes else passes or Solid.bit(perch)
    }

    /**
     * True when a plan landing on [mine] (a mask like [passes]) would land where this cat is or
     * will be: any surface both use except the sofa, which two cats may share a metre apart (a
     * position rule the sofa's planner applies on its own).
     */
    fun clashes(mine: Int): Boolean = (passes and mine and SHARED.inv()) != 0

    private companion object {
        /** Surfaces wide enough for two cats. */
        val SHARED = Solid.bit(SurfaceId.COUCH)
    }

    /** True when the cat lies on the bed, or is going to. */
    val onBed: Boolean
        get() = perch == SurfaceId.BED

    /** True when the cat is on, or heading for, the cat tree. */
    val onTree: Boolean
        get() = perch == SurfaceId.MID || perch == SurfaceId.TOP
}

/**
 * What a cat in the house may do in it, as plans for the body: walk to another room, eat at the
 * bowls, sleep on the bed, sit on the sofa, climb the cat tree, watch from the window sill. Every
 * place is a spot on the room's surfaces, reached by the parkour planner ([ParkourPlanner]); every
 * plan begins on the floor and ends on the floor, so a cat that goes up comes down again.
 *
 * Each planner returns an empty list when the spot is not free (the other cat is there, or in the
 * way) and the cat simply rests instead; otherwise it writes the plan's [Claim].
 */
internal class HousePlans(private val rng: Rng, private val slot: Int, private val weak: Boolean) {
    /** The free stretch of the floor around the cat after [freeSpan]. */
    var spanLo = World.HOUSE_MIN_X
        private set

    var spanHi = World.HOUSE_MAX_X
        private set

    private var reachLo = 0f
    private var reachHi = 0f

    /** The raised surfaces the plan being made lands on (a [Claim.passes] mask). */
    private var landed = 0

    /**
     * Work out how far a cat standing at [x] may walk either way without coming within [GAP] of
     * [mate] (the other cat's claim) or of the bed while the mate is on it.
     */
    fun freeSpan(x: Float, mate: Claim?) {
        spanLo = World.HOUSE_MIN_X
        spanHi = World.HOUSE_MAX_X
        if (mate == null) return
        if (mate.onFloor) shrink(x, mate.lo - GAP, mate.hi + GAP)
        if (mate.onBed) shrink(x, BED_X - GAP, BED_X + GAP)
    }

    private fun shrink(x: Float, a: Float, b: Float) {
        if (x >= b) {
            spanLo = maxOf(spanLo, b)
        } else if (x <= a) {
            spanHi = minOf(spanHi, a)
        } else {
            spanLo = x // inside the keep-out zone: stay put
            spanHi = x
        }
    }

    /**
     * Move a place to stop on the floor out of the way of what stands in front of the cat in the
     * room: the cushion, the pouf, the box and the bed. A cat that stops there stands half behind
     * them (the room draws them in front of the cats), which looks like a cat hiding, not resting.
     * A place already clear stays.
     */
    fun clearOfFurniture(x: Float): Float {
        for (i in ZONE_LO.indices) {
            val lo = ZONE_LO[i] - STOP_MARGIN
            val hi = ZONE_HI[i] + STOP_MARGIN
            if (x <= lo || x >= hi) continue
            val left = lo >= World.HOUSE_MIN_X && !inZone(lo)
            val right = hi <= World.HOUSE_MAX_X && !inZone(hi)
            return when {
                left && (!right || x - lo < hi - x) -> lo
                right -> hi
                else -> x
            }
        }
        return x
    }

    private fun inZone(x: Float): Boolean {
        for (i in ZONE_LO.indices) if (x > ZONE_LO[i] - STOP_MARGIN && x < ZONE_HI[i] + STOP_MARGIN)
            return true
        return false
    }

    // ---------------------------------------------------------------- reserving the floor

    private fun begin(x: Float) {
        reachLo = x
        reachHi = x
        landed = 0
    }

    private fun reach(x: Float) {
        if (x < reachLo) reachLo = x
        if (x > reachHi) reachHi = x
    }

    /**
     * Take what [steps] use into the plan's reach: the floor it walks, takes off from and lands on,
     * and every raised surface it lands on. [start] is where the steps begin.
     */
    private fun trace(steps: List<PlanStep>, start: SurfaceId) {
        var on = start
        for (s in steps) {
            when (s) {
                is PlanStep.Walk -> if (on == SurfaceId.FLOOR) reach(s.x)
                is PlanStep.Leap -> {
                    if (on == SurfaceId.FLOOR) reach(s.takeOffX)
                    on = s.surface
                    if (on == SurfaceId.FLOOR) reach(s.x) else landed = landed or Solid.bit(on)
                }
                else -> Unit
            }
        }
    }

    /**
     * Claim what the plan reaches if it keeps clear of [mate]; false (and no claim) if not. Two
     * cats never land on the same single-occupancy surface, on the way or at the top.
     */
    private fun commit(claim: Claim, mate: Claim?, perch: SurfaceId, perchX: Float): Boolean {
        if (mate != null) {
            if (mate.onFloor && !(reachHi + GAP <= mate.lo || reachLo >= mate.hi + GAP))
                return false
            if (mate.onBed && reachHi > BED_X - GAP && reachLo < BED_X + GAP) return false
            if (mate.clashes(landed)) return false
        }
        claim.reserve(reachLo, reachHi, perch, perchX, landed)
        return true
    }

    /**
     * Up to [perch] at [at], [dwell] there, and down to the floor at [landing], every jump of it
     * the planner's for this [cap]. Empty (the cat rests instead) when the cat cannot get up or
     * cannot get down again, or when the other cat has a hand on the route.
     */
    private fun excursion(
        x: Float,
        mate: Claim?,
        claim: Claim,
        cap: JumpCapability,
        perch: SurfaceId,
        at: Float,
        landing: Float,
        dwell: List<PlanStep>,
    ): List<PlanStep> {
        val up = ParkourPlanner.plan(cap, SurfaceId.FLOOR, x, perch, at, slot) ?: return emptyList()
        val standX = World.clampTo(perch, at)
        val down =
            ParkourPlanner.plan(cap, perch, standX, SurfaceId.FLOOR, landing, slot)
                ?: return emptyList()
        begin(x)
        trace(up.steps, SurfaceId.FLOOR)
        trace(down.steps, perch)
        if (!commit(claim, mate, perch, standX)) return emptyList()
        val steps = ArrayList<PlanStep>(up.steps.size + dwell.size + down.steps.size)
        steps.addAll(up.steps)
        steps.addAll(dwell)
        steps.addAll(down.steps)
        return steps
    }

    // ---------------------------------------------------------------- the places

    /** Walk to a spot in another room, look about, and sit. */
    fun travel(x: Float, mate: Claim?, claim: Claim): List<PlanStep> {
        val roll = rng.nextFloat()
        val rooms = HouseMap.current.rooms
        val there = rooms[otherRoom(roomOf(x), rooms.size, roll)]
        val spot = clearOfFurniture(rng.range(there.strollLo, there.strollHi))
        begin(x)
        reach(spot)
        if (!commit(claim, mate, SurfaceId.FLOOR, 0f)) return emptyList()
        return listOf(PlanStep.Walk(spot), act("idle_sit", SIT_MIN_S, SIT_MAX_S))
    }

    /** Walk to the bowls, face them, eat, and sit a moment. */
    fun eat(x: Float, mate: Claim?, claim: Claim): List<PlanStep> {
        val at = eatX(slot)
        begin(x)
        reach(at)
        if (!commit(claim, mate, SurfaceId.FLOOR, 0f)) return emptyList()
        return listOf(
            PlanStep.Walk(at),
            PlanStep.Face(if (slot == 0) 1 else -1),
            act("eat", EAT_MIN_S, EAT_MAX_S),
            act("idle_sit", SIT_MIN_S, SIT_MAX_S),
        )
    }

    /** Hop on the bed, curl up and sleep, stretch on waking and step down. */
    fun bed(x: Float, mate: Claim?, claim: Claim, cap: JumpCapability): List<PlanStep> {
        if (mate != null && mate.onBed) return emptyList()
        val nap = if (weak) Tuning.Autopilot.WEAK_NAP_FACTOR else 1f
        val sleep =
            listOf(
                act("loaf", BED_LOAF_MIN_S, BED_LOAF_MAX_S),
                act("sleep", Tuning.Autopilot.NAP_MIN_S * nap, Tuning.Autopilot.NAP_MAX_S * nap),
                act("stretch", STRETCH_S, STRETCH_S + STRETCH_SLACK_S),
            )
        return excursion(x, mate, claim, cap, SurfaceId.BED, BED_X, BED_X + BED_STEP_DOWN, sleep)
    }

    /** Hop on the sofa, sit, loaf or wash for a while, and step down. */
    fun couch(x: Float, mate: Claim?, claim: Claim, cap: JumpCapability): List<PlanStep> {
        val at = rng.range(COUCH_LO, COUCH_HI)
        val landing =
            clearOfFurniture(
                World.clampHouse(at + if (rng.chance(HALF)) COUCH_STEP_DOWN else -COUCH_STEP_DOWN)
            )
        if (mate != null && mate.perch == SurfaceId.COUCH && abs(mate.perchX - at) < GAP) {
            return emptyList()
        }
        return excursion(x, mate, claim, cap, SurfaceId.COUCH, at, landing, listOf(dwell()))
    }

    /** Climb the cat tree (the top 40 percent of the time), sit up there, and come down. */
    fun tree(x: Float, mate: Claim?, claim: Claim, cap: JumpCapability): List<PlanStep> {
        if (mate != null && mate.onTree) return emptyList()
        val top = rng.chance(TREE_TOP_CHANCE)
        val perch = if (top) SurfaceId.TOP else SurfaceId.MID
        val at = if (top) TREE_TOP_X else TREE_MID_X
        val landing = TREE_BASE_X + TREE_STEP_DOWN
        return excursion(x, mate, claim, cap, perch, at, landing, listOf(dwell()))
    }

    /** Hop on the bedroom window sill, watch the garden, and come down. */
    fun sill(x: Float, mate: Claim?, claim: Claim, cap: JumpCapability): List<PlanStep> {
        if (mate != null && mate.perch == SurfaceId.SILL) return emptyList()
        val watch =
            listOf(
                act("lookup", LOOK_MIN_S, LOOK_MAX_S),
                act("idle_sit", DWELL_SIT_MIN_S, DWELL_SIT_MAX_S),
            )
        return excursion(
            x,
            mate,
            claim,
            cap,
            SurfaceId.SILL,
            SILL_X,
            SILL_X + SILL_STEP_DOWN,
            watch,
        )
    }

    /**
     * Parkour: up on the counter and from there onto the fridge, sit up there in the corner, and
     * back the way it came (counter, floor). A cat that cannot make the counter, or the fridge from
     * it, rests instead.
     */
    fun fridge(x: Float, mate: Claim?, claim: Claim, cap: JumpCapability): List<PlanStep> {
        val at = rng.range(FRIDGE_LO, FRIDGE_HI)
        val landing = clearOfFurniture(rng.range(FRIDGE_FLOOR_LO, FRIDGE_FLOOR_HI))
        return excursion(x, mate, claim, cap, SurfaceId.FRIDGE, at, landing, perchDwell())
    }

    /** Parkour: up to the top of the bookcase (by the tree, or the window sill) and back down. */
    fun bookcase(x: Float, mate: Claim?, claim: Claim, cap: JumpCapability): List<PlanStep> {
        val at = rng.range(BOOKCASE_LO, BOOKCASE_HI)
        val landing = clearOfFurniture(rng.range(BOOKCASE_FLOOR_LO, BOOKCASE_FLOOR_HI))
        return excursion(x, mate, claim, cap, SurfaceId.BOOKCASE, at, landing, perchDwell())
    }

    /**
     * Parkour: a window sill that needs a stepping stone, the living room's (over the sofa) or the
     * kitchen's (over the counter), to watch from and come down.
     */
    fun window(x: Float, mate: Claim?, claim: Claim, cap: JumpCapability): List<PlanStep> {
        val living = rng.chance(HALF)
        val perch = if (living) SurfaceId.LSILL else SurfaceId.KSILL
        val s = World.surface(perch)
        val at = rng.range(s.x0 + WINDOW_INSET, s.x1 - WINDOW_INSET)
        val landing =
            if (living) clearOfFurniture(rng.range(WINDOW_LIVING_LO, WINDOW_LIVING_HI))
            else clearOfFurniture(rng.range(WINDOW_KITCHEN_LO, WINDOW_KITCHEN_HI))
        return excursion(x, mate, claim, cap, perch, at, landing, perchDwell())
    }

    /** What a cat does on a high perch: sit and watch, loaf, wash, or look about. */
    private fun perchDwell(): List<PlanStep> {
        val roll = rng.nextFloat()
        return when {
            roll < PERCH_LOAF -> listOf(act("loaf", LOAF_MIN_S, LOAF_MAX_S))
            roll < PERCH_GROOM -> listOf(act("groom", Tuning.Autopilot.GROOM_MIN_S, GROOM_MAX_S))
            roll < PERCH_WATCH ->
                listOf(
                    act("lookup", LOOK_MIN_S, LOOK_MAX_S),
                    act("idle_sit", DWELL_SIT_MIN_S, DWELL_SIT_MAX_S),
                )
            else -> listOf(act("idle_sit", DWELL_SIT_MIN_S, DWELL_SIT_MAX_S))
        }
    }

    /** What a cat does while it is up on something. */
    private fun dwell(): PlanStep {
        val roll = rng.nextFloat()
        return when {
            roll < DWELL_LOAF -> act("loaf", LOAF_MIN_S, LOAF_MAX_S)
            roll < DWELL_GROOM -> act("groom", Tuning.Autopilot.GROOM_MIN_S, GROOM_MAX_S)
            else -> act("idle_sit", DWELL_SIT_MIN_S, DWELL_SIT_MAX_S)
        }
    }

    private fun act(anim: String, lo: Float, hi: Float) = PlanStep.Act(anim, rng.range(lo, hi))

    /** Which open room the world x [x] is in, from the left (the web's room split). */
    fun roomOf(x: Float): Int = World.roomOf(x)

    /**
     * The room a trip from room [here] of [count] goes to, chosen by [roll] (0 up to 1): a
     * neighbour [NEAR_ROOM_SHARE] of the time, any other room the rest; with no other room than the
     * neighbours (the middle of three) they share it equally. Rooms are counted from the left.
     */
    internal fun otherRoom(here: Int, count: Int, roll: Float): Int {
        val nears = (if (here > 0) 1 else 0) + (if (here < count - 1) 1 else 0)
        val fars = count - 1 - nears
        if (fars == 0) return nthNear(here, count, minOf(nears - 1, (roll * nears).toInt()))
        if (roll < NEAR_ROOM_SHARE) {
            return nthNear(here, count, minOf(nears - 1, (roll / NEAR_ROOM_SHARE * nears).toInt()))
        }
        var k = minOf(fars - 1, ((roll - NEAR_ROOM_SHARE) / (1f - NEAR_ROOM_SHARE) * fars).toInt())
        for (i in 0 until count) {
            if (i == here || i == here - 1 || i == here + 1) continue
            if (k-- == 0) return i
        }
        return here
    }

    private fun nthNear(here: Int, count: Int, n: Int): Int =
        if (here > 0 && n == 0) here - 1 else here + 1

    companion object {
        /** Cats keep this far apart (feet to feet) on the floor. */
        const val GAP = 40f

        private const val NEAR_ROOM_SHARE = 0.7f

        /**
         * What stands in front of the cats (world x ranges): the cushion, the pouf, the bed, the
         * box.
         */
        private val ZONE_LO = floatArrayOf(-148f, -50f, 10f, 266f)
        private val ZONE_HI = floatArrayOf(-92f, -18f, 66f, 314f)
        private const val STOP_MARGIN = 14f
        private const val HALF = 0.5f

        /** The web room's eating spots: the first cat at x 87 facing right, the second at 121. */
        private const val EAT_X_FIRST = 87f
        private const val EAT_X_SECOND = 121f

        /** Where cat [slot] eats: the first cat at the left bowl, the second at the right. */
        internal fun eatX(slot: Int): Float = if (slot == 0) EAT_X_FIRST else EAT_X_SECOND

        private const val EAT_MIN_S = 4f
        private const val EAT_MAX_S = 7f

        private const val BED_X = 36f
        private const val BED_STEP_DOWN = 44f
        private const val BED_LOAF_MIN_S = 2f
        private const val BED_LOAF_MAX_S = 3.5f
        private const val COUCH_LO = -128f
        private const val COUCH_HI = -76f
        private const val COUCH_STEP_DOWN = 44f
        private const val TREE_BASE_X = 138f
        private const val TREE_MID_X = 150f
        private const val TREE_TOP_X = 154f
        private const val TREE_STEP_DOWN = -34f
        private const val TREE_TOP_CHANCE = 0.4f
        private const val SILL_X = 54f
        private const val FRIDGE_LO = 218f
        private const val FRIDGE_HI = 234f
        private const val FRIDGE_FLOOR_LO = 226f
        private const val FRIDGE_FLOOR_HI = 330f
        private const val BOOKCASE_LO = 112f
        private const val BOOKCASE_HI = 132f
        private const val BOOKCASE_FLOOR_LO = 80f
        private const val BOOKCASE_FLOOR_HI = 120f
        private const val WINDOW_INSET = 6f
        private const val WINDOW_LIVING_LO = -140f
        private const val WINDOW_LIVING_HI = -70f
        private const val WINDOW_KITCHEN_LO = 250f
        private const val WINDOW_KITCHEN_HI = 330f
        private const val PERCH_LOAF = 0.3f
        private const val PERCH_GROOM = 0.55f
        private const val PERCH_WATCH = 0.8f
        private const val SILL_STEP_DOWN = 30f

        private const val SIT_MIN_S = 1.2f
        private const val SIT_MAX_S = 2.6f
        private const val DWELL_SIT_MIN_S = 4f
        private const val DWELL_SIT_MAX_S = 9f
        private const val LOAF_MIN_S = 6f
        private const val LOAF_MAX_S = 12f
        private const val GROOM_MAX_S = 6.5f
        private const val LOOK_MIN_S = 3f
        private const val LOOK_MAX_S = 6f
        private const val STRETCH_S = 1.75f
        private const val STRETCH_SLACK_S = 0.4f
        private const val DWELL_LOAF = 0.4f
        private const val DWELL_GROOM = 0.7f
    }
}
