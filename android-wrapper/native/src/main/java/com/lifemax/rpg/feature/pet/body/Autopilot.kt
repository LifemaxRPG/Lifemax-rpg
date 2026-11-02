package com.lifemax.rpg.feature.pet.body

/**
 * A stand-in for the page's brain: it keeps a [CatBody] busy with the small life of a cat that has
 * nothing to do. It sits and breathes, wanders a few steps, turns, grooms, stretches, looks up,
 * loafs and naps (and stretches on waking), then rests a while and picks something else.
 *
 * Everything is a plan handed to the body, so the body's own idle life (blinks, ear flicks, weight
 * shifts, glances) keeps running between and during the plans. The autopilot never reads the clock:
 * it counts the `dt` it is given, so the same seed and the same frame times replay the same day,
 * and two cats (different seeds) never do the same thing in step.
 *
 * It keeps the cat inside [minX]..[maxX] on the floor, which is how the Overview card keeps the cat
 * in its little window. A [weak] (hungry) cat walks less, rests more and naps sooner.
 *
 * Planning allocates (a plan is a small list); stepping through idle time does not.
 *
 * @param seed the cat's own: use [seedFor] so a cat keeps its character from one visit to the next.
 */
class Autopilot
internal constructor(
    seed: Int,
    private val minX: Float,
    private val maxX: Float,
    private val weak: Boolean,
    private val house: HouseLink?,
) {
    /** A cat in a strip of floor, the Overview card's first autopilot. */
    constructor(
        seed: Int,
        minX: Float,
        maxX: Float,
        weak: Boolean = false,
    ) : this(seed, minX, maxX, weak, null)

    /** The things the autopilot does, in the order of [Move.ordinal]. */
    enum class Move {
        REST,
        WANDER,
        TURN,
        GROOM,
        STRETCH,
        LOOK_UP,
        SLOW_BLINK,
        LOAF,
        NAP,

        /** House only: walk to another room. */
        TRAVEL,

        /** House only: eat at the bowls. */
        EAT,

        /** House only: sleep on the bed. */
        BED,

        /** House only: sit on the sofa. */
        COUCH,

        /** House only: climb the cat tree. */
        TREE,

        /** House only: watch from the window sill. */
        SILL,
    }

    private val rng = Rng.derive(seed, SALT)
    private val places = house?.let { HousePlans(rng, it.slot, weak) }

    /** What this cat has set aside in the house (see [Claim]); only the house reads it. */
    internal val claim = Claim()
    private val weights = FloatArray(Move.entries.size)
    private var planId = 0
    private var pause = rng.range(Tuning.Autopilot.FIRST_PAUSE_MIN_S, FIRST_PAUSE_MAX_S)
    private var awake = 0f
    private var woke = false

    /** The move most recently started (for tests and for variety); starts as [Move.REST]. */
    var last = Move.REST
        private set

    /** How many plans have been handed to the body so far. */
    val plans: Int
        get() = planId

    /**
     * Where this cat starts: near the middle of its range, a little off to one side; in the house
     * the first cat starts in the living room or the bedroom, the second at the other end, so they
     * start well apart.
     */
    val startX: Float =
        if (house == null) (minX + maxX) / 2f + rng.range(-START_SPREAD, START_SPREAD)
        else if (house.slot == 0) {
            places!!.clearOfFurniture(rng.range(HOUSE_START_FIRST_LO, HOUSE_START_FIRST_HI))
        } else {
            places!!.clearOfFurniture(rng.range(HOUSE_START_SECOND_LO, HOUSE_START_SECOND_HI))
        }

    /** Which way this cat starts out facing. */
    val startFacing: Int = if (rng.chance(HALF)) 1 else -1

    /**
     * Account for [dt] seconds of host time. When the body has finished what it was doing and has
     * rested long enough, hand it the next plan. Call after every [CatBody.step].
     */
    fun update(body: CatBody, dt: Float) {
        awake += dt
        if (!body.isIdle) return
        if (house != null) claim.standAt(body.pose.surface, body.pose.x)
        pause -= dt
        if (pause > 0f) return
        begin(body, pick())
    }

    // ---------------------------------------------------------------- choosing

    private fun pick(): Move {
        if (woke) {
            woke = false
            return Move.STRETCH
        }
        val sleepy = minOf(awake / Tuning.Autopilot.SLEEPY_AFTER_S, 1f) * Tuning.Autopilot.NAP_GAIN
        val lazy = if (weak) Tuning.Autopilot.WEAK_LAZINESS else 1f
        weights[Move.REST.ordinal] = Tuning.Autopilot.W_REST
        weights[Move.WANDER.ordinal] = Tuning.Autopilot.W_WANDER / lazy
        weights[Move.TURN.ordinal] = Tuning.Autopilot.W_TURN
        weights[Move.GROOM.ordinal] = Tuning.Autopilot.W_GROOM
        weights[Move.STRETCH.ordinal] = Tuning.Autopilot.W_STRETCH
        weights[Move.LOOK_UP.ordinal] = Tuning.Autopilot.W_LOOK_UP
        weights[Move.SLOW_BLINK.ordinal] = Tuning.Autopilot.W_SLOW_BLINK
        weights[Move.LOAF.ordinal] = Tuning.Autopilot.W_LOAF * lazy
        weights[Move.NAP.ordinal] = (Tuning.Autopilot.W_NAP + sleepy) * lazy
        if (house != null) houseWeights(sleepy, lazy)
        weights[last.ordinal] = 0f
        var total = 0f
        for (w in weights) total += w
        var roll = rng.nextFloat() * total
        for (i in weights.indices) {
            roll -= weights[i]
            if (roll < 0f) return Move.entries[i]
        }
        return Move.REST
    }

    /**
     * In the house the cat also travels, eats, and goes up on things; it naps on the bed instead.
     */
    private fun houseWeights(sleepy: Float, lazy: Float) {
        weights[Move.WANDER.ordinal] *= HOUSE_WANDER_SHARE
        weights[Move.NAP.ordinal] *= HOUSE_FLOOR_NAP_SHARE
        weights[Move.TRAVEL.ordinal] = Tuning.Autopilot.W_TRAVEL / lazy
        weights[Move.EAT.ordinal] =
            if (weak) Tuning.Autopilot.W_EAT_HUNGRY else Tuning.Autopilot.W_EAT
        weights[Move.BED.ordinal] = (Tuning.Autopilot.W_BED + sleepy) * lazy
        weights[Move.COUCH.ordinal] = Tuning.Autopilot.W_COUCH
        weights[Move.TREE.ordinal] = Tuning.Autopilot.W_TREE / lazy
        weights[Move.SILL.ordinal] = Tuning.Autopilot.W_SILL / lazy
    }

    // ---------------------------------------------------------------- doing

    private fun begin(body: CatBody, move: Move) {
        last = move
        val steps = steps(body, move)
        pause =
            rng.range(Tuning.Autopilot.REST_MIN_S, Tuning.Autopilot.REST_MAX_S) *
                (if (weak) Tuning.Autopilot.WEAK_LAZINESS else 1f)
        if (steps.isEmpty()) return
        planId++
        body.replacePlan(planId, steps)
    }

    private fun steps(body: CatBody, move: Move): List<PlanStep> =
        when (move) {
            Move.REST -> emptyList()
            Move.WANDER -> wander(body.pose.x)
            Move.TURN -> listOf(PlanStep.Face(-body.pose.facing), settle())
            Move.GROOM -> listOf(act("groom", Tuning.Autopilot.GROOM_MIN_S, GROOM_MAX_S))
            Move.STRETCH -> listOf(act("stretch", STRETCH_S, STRETCH_S + STRETCH_SLACK_S))
            Move.LOOK_UP -> listOf(act("lookup", LOOK_MIN_S, LOOK_MAX_S))
            Move.SLOW_BLINK -> listOf(act("slowblink", SLOW_BLINK_S, SLOW_BLINK_S + 0.5f))
            Move.LOAF -> listOf(act("loaf", LOAF_MIN_S, LOAF_MAX_S))
            Move.NAP -> nap()
            else -> excursion(body, move)
        }

    /** One of the house's places (see [HousePlans]); nothing if there is no house or it is busy. */
    private fun excursion(body: CatBody, move: Move): List<PlanStep> {
        val p = places ?: return emptyList()
        val x = body.pose.x
        val mate = house?.mate?.claim
        return when (move) {
            Move.TRAVEL -> p.travel(x, mate, claim)
            Move.EAT -> p.eat(x, mate, claim)
            Move.BED -> p.bed(x, mate, claim).also { if (it.isNotEmpty()) awake = 0f }
            Move.COUCH -> p.couch(x, mate, claim)
            Move.TREE -> p.tree(x, mate, claim)
            Move.SILL -> p.sill(x, mate, claim)
            else -> emptyList()
        }
    }

    private fun act(anim: String, lo: Float, hi: Float) = PlanStep.Act(anim, rng.range(lo, hi))

    private fun settle() = act("idle_sit", SETTLE_MIN_S, SETTLE_MAX_S)

    /** A few steps one way, sometimes a few back, then sit for a moment. */
    private fun wander(x: Float): List<PlanStep> {
        var minX = this.minX
        var maxX = this.maxX
        if (places != null) {
            places.freeSpan(x, house?.mate?.claim)
            minX = places.spanLo
            maxX = places.spanHi
        }
        val reach = if (weak) Tuning.Autopilot.WEAK_WALK_MAX_PX else Tuning.Autopilot.WALK_MAX_PX
        val leftRoom = x - minX
        val rightRoom = maxX - x
        var dir = if (rightRoom >= leftRoom) 1f else -1f
        if (rng.chance(Tuning.Autopilot.WALK_AGAINST_ROOM_CHANCE)) dir = -dir
        var distance = rng.range(Tuning.Autopilot.WALK_MIN_PX, reach)
        if (dir * distance + x !in minX..maxX) {
            dir = -dir
            distance = minOf(distance, if (dir > 0f) rightRoom else leftRoom)
        }
        var first = Mathx.clamp(x + dir * distance, minX, maxX)
        if (places != null) first = Mathx.clamp(places.clearOfFurniture(first), minX, maxX)
        val steps = ArrayList<PlanStep>(3)
        steps.add(PlanStep.Walk(first))
        var lastStop = first
        if (rng.chance(Tuning.Autopilot.WALK_TWICE_CHANCE)) {
            lastStop = Mathx.clamp(first - dir * distance * HALF, minX, maxX)
            if (places != null)
                lastStop = Mathx.clamp(places.clearOfFurniture(lastStop), minX, maxX)
            steps.add(PlanStep.Walk(lastStop))
        }
        steps.add(settle())
        if (house != null)
            claim.reserve(minOf(x, first, lastStop), maxOf(x, first, lastStop), SurfaceId.FLOOR, 0f)
        return steps
    }

    /** Curl up into a loaf, then sleep for a good while; the cat stretches when it wakes. */
    private fun nap(): List<PlanStep> {
        awake = 0f
        woke = true
        val sleep = if (weak) Tuning.Autopilot.WEAK_NAP_FACTOR else 1f
        return listOf(
            act("loaf", NAP_LOAF_MIN_S, NAP_LOAF_MAX_S),
            act("sleep", Tuning.Autopilot.NAP_MIN_S * sleep, Tuning.Autopilot.NAP_MAX_S * sleep),
        )
    }

    companion object {
        private const val SALT = 0xA170
        private const val HALF = 0.5f
        private const val START_SPREAD = 14f
        private const val FIRST_PAUSE_MAX_S = 3.5f
        private const val GROOM_MAX_S = 6.5f
        private const val STRETCH_S = 1.75f
        private const val STRETCH_SLACK_S = 0.4f
        private const val LOOK_MIN_S = 2f
        private const val LOOK_MAX_S = 3.5f
        private const val SLOW_BLINK_S = 1.5f
        private const val LOAF_MIN_S = 6f
        private const val LOAF_MAX_S = 12f
        private const val NAP_LOAF_MIN_S = 2f
        private const val NAP_LOAF_MAX_S = 3.5f
        private const val SETTLE_MIN_S = 1.2f
        private const val SETTLE_MAX_S = 2.6f
        private const val HOUSE_START_FIRST_LO = -20f
        private const val HOUSE_START_FIRST_HI = 60f
        private const val HOUSE_START_SECOND_LO = 130f
        private const val HOUSE_START_SECOND_HI = 230f
        private const val HOUSE_WANDER_SHARE = 0.75f
        private const val HOUSE_FLOOR_NAP_SHARE = 0.4f

        /** A seed for a cat: stable for the same name and coat, different between cats. */
        fun seedFor(name: String, coat: String): Int = Rng.hash("$name|$coat")
    }
}

/** The link between the two cats of a house: which one this is, and who the other is. */
internal class HouseLink(val slot: Int) {
    var mate: Autopilot? = null
}
