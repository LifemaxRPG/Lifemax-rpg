package com.lifemax.rpg.feature.pet.body

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

    /** The cat stands still at [x] on [surface]. */
    fun standAt(surface: SurfaceId, x: Float) {
        if (surface == SurfaceId.FLOOR) {
            onFloor = true
            lo = x
            hi = x
            perch = SurfaceId.FLOOR
        } else {
            onFloor = false
            perch = surface
            perchX = x
        }
    }

    /** The cat will use the floor from [from] to [to] and then rest on [perch] at [perchX]. */
    fun reserve(from: Float, to: Float, perch: SurfaceId, perchX: Float) {
        onFloor = true
        lo = from
        hi = to
        this.perch = perch
        this.perchX = perchX
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
 * place is a spot on the web room's own surface graph ([RoutePlanner]); every plan begins on the
 * floor and ends on the floor, so a cat that goes up comes down again.
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
    }

    private fun reach(x: Float) {
        if (x < reachLo) reachLo = x
        if (x > reachHi) reachHi = x
    }

    /** Claim what the plan reaches if it keeps clear of [mate]; false (and no claim) if not. */
    private fun commit(claim: Claim, mate: Claim?, perch: SurfaceId, perchX: Float): Boolean {
        if (mate != null) {
            if (mate.onFloor && !(reachHi + GAP <= mate.lo || reachLo >= mate.hi + GAP))
                return false
            if (mate.onBed && reachHi > BED_X - GAP && reachLo < BED_X + GAP) return false
        }
        claim.reserve(reachLo, reachHi, perch, perchX)
        return true
    }

    // ---------------------------------------------------------------- the places

    /** Walk to a spot in another room, look about, and sit. */
    fun travel(x: Float, mate: Claim?, claim: Claim): List<PlanStep> {
        val roll = rng.nextFloat()
        val there =
            when (roomOf(x)) {
                LIVING -> if (roll < NEAR_ROOM_SHARE) BEDROOM else KITCHEN
                KITCHEN -> if (roll < NEAR_ROOM_SHARE) BEDROOM else LIVING
                else -> if (roll < HALF) LIVING else KITCHEN
            }
        val spot = clearOfFurniture(rng.range(ROOM_LO[there], ROOM_HI[there]))
        begin(x)
        reach(spot)
        if (!commit(claim, mate, SurfaceId.FLOOR, 0f)) return emptyList()
        return listOf(PlanStep.Walk(spot), act("idle_sit", SIT_MIN_S, SIT_MAX_S))
    }

    /** Walk to the bowls, face them, eat, and sit a moment. */
    fun eat(x: Float, mate: Claim?, claim: Claim): List<PlanStep> {
        val at = if (slot == 0) EAT_X_FIRST else EAT_X_SECOND
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
    fun bed(x: Float, mate: Claim?, claim: Claim): List<PlanStep> {
        if (mate != null && mate.onBed) return emptyList()
        val down = BED_X + BED_STEP_DOWN
        begin(x)
        reach(BED_RUN_UP_X)
        reach(BED_X)
        reach(down)
        if (!commit(claim, mate, SurfaceId.BED, BED_X)) return emptyList()
        val nap = if (weak) Tuning.Autopilot.WEAK_NAP_FACTOR else 1f
        val steps = ArrayList<PlanStep>(MAX_STEPS)
        steps.addAll(RoutePlanner.route(SurfaceId.FLOOR, x, SurfaceId.BED, BED_X))
        steps.add(act("loaf", BED_LOAF_MIN_S, BED_LOAF_MAX_S))
        steps.add(act("sleep", Tuning.Autopilot.NAP_MIN_S * nap, Tuning.Autopilot.NAP_MAX_S * nap))
        steps.add(act("stretch", STRETCH_S, STRETCH_S + STRETCH_SLACK_S))
        steps.addAll(RoutePlanner.route(SurfaceId.BED, BED_X, SurfaceId.FLOOR, down))
        return steps
    }

    /** Hop on the sofa, sit, loaf or wash for a while, and step down. */
    fun couch(x: Float, mate: Claim?, claim: Claim): List<PlanStep> {
        val at = rng.range(COUCH_LO, COUCH_HI)
        val landing =
            clearOfFurniture(
                World.clampHouse(at + if (rng.chance(HALF)) COUCH_STEP_DOWN else -COUCH_STEP_DOWN)
            )
        if (mate != null && mate.perch == SurfaceId.COUCH && abs(mate.perchX - at) < GAP) {
            return emptyList()
        }
        begin(x)
        reach(at + COUCH_RUN_UP)
        reach(at)
        reach(landing)
        if (!commit(claim, mate, SurfaceId.COUCH, at)) return emptyList()
        val steps = ArrayList<PlanStep>(MAX_STEPS)
        steps.addAll(RoutePlanner.route(SurfaceId.FLOOR, x, SurfaceId.COUCH, at))
        steps.add(dwell())
        steps.addAll(RoutePlanner.route(SurfaceId.COUCH, at, SurfaceId.FLOOR, landing))
        return steps
    }

    /** Climb the cat tree, sit up there, and come down. */
    fun tree(x: Float, mate: Claim?, claim: Claim): List<PlanStep> {
        if (mate != null && mate.onTree) return emptyList()
        val top = rng.chance(TREE_TOP_CHANCE)
        val perch = if (top) SurfaceId.TOP else SurfaceId.MID
        val at = if (top) TREE_TOP_X else TREE_MID_X
        val landing = TREE_BASE_X + TREE_STEP_DOWN
        begin(x)
        reach(TREE_BASE_X)
        reach(landing)
        if (!commit(claim, mate, perch, at)) return emptyList()
        val steps = ArrayList<PlanStep>(MAX_STEPS)
        steps.addAll(RoutePlanner.route(SurfaceId.FLOOR, x, perch, at))
        steps.add(dwell())
        if (top) steps.add(PlanStep.Hop(SurfaceId.MID, TREE_MID_X))
        steps.addAll(RoutePlanner.route(SurfaceId.MID, TREE_MID_X, SurfaceId.FLOOR, landing))
        return steps
    }

    /** Hop on the bedroom window sill, watch the garden, and come down. */
    fun sill(x: Float, mate: Claim?, claim: Claim): List<PlanStep> {
        if (mate != null && mate.perch == SurfaceId.SILL) return emptyList()
        val landing = SILL_X + SILL_STEP_DOWN
        begin(x)
        reach(SILL_RUN_UP_X)
        reach(landing)
        if (!commit(claim, mate, SurfaceId.SILL, SILL_X)) return emptyList()
        val steps = ArrayList<PlanStep>(MAX_STEPS)
        steps.addAll(RoutePlanner.route(SurfaceId.FLOOR, x, SurfaceId.SILL, SILL_X))
        steps.add(act("lookup", LOOK_MIN_S, LOOK_MAX_S))
        steps.add(act("idle_sit", DWELL_SIT_MIN_S, DWELL_SIT_MAX_S))
        steps.addAll(RoutePlanner.route(SurfaceId.SILL, SILL_X, SurfaceId.FLOOR, landing))
        return steps
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

    /** Which room the world x [x] is in: 0 living, 1 bedroom, 2 kitchen (the web's room split). */
    fun roomOf(x: Float): Int =
        if (x < LIVING_END) LIVING else if (x < KITCHEN_START) BEDROOM else KITCHEN

    companion object {
        /** Cats keep this far apart (feet to feet) on the floor. */
        const val GAP = 40f

        const val LIVING = 0
        const val BEDROOM = 1
        const val KITCHEN = 2
        private const val LIVING_END = -4f
        private const val KITCHEN_START = 196f
        private val ROOM_LO = floatArrayOf(-140f, 16f, 218f)
        private val ROOM_HI = floatArrayOf(-74f, 170f, 328f)
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
        private const val EAT_MIN_S = 4f
        private const val EAT_MAX_S = 7f

        private const val BED_X = 36f
        private const val BED_RUN_UP_X = 50f
        private const val BED_STEP_DOWN = 44f
        private const val BED_LOAF_MIN_S = 2f
        private const val BED_LOAF_MAX_S = 3.5f
        private const val COUCH_LO = -128f
        private const val COUCH_HI = -76f
        private const val COUCH_RUN_UP = 20f
        private const val COUCH_STEP_DOWN = 44f
        private const val TREE_BASE_X = 138f
        private const val TREE_MID_X = 150f
        private const val TREE_TOP_X = 154f
        private const val TREE_STEP_DOWN = -34f
        private const val TREE_TOP_CHANCE = 0.4f
        private const val SILL_X = 54f
        private const val SILL_RUN_UP_X = 54f
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
        private const val MAX_STEPS = 10
    }
}
