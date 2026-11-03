package com.lifemax.rpg.feature.pet.body

import kotlin.math.abs

/** How a hop between two surfaces is made. */
enum class LeapKind {
    /** A rise or fall of a few pixels: a hop with no crouch. */
    STEP,

    /** Up, or across a gap: crouch, push, fly, land. */
    LEAP,

    /** Down: a controlled fall with a landing squash. */
    DROP;

    companion object {
        /** The kind of a flight with this [rise] (positive up), sideways distance and [apex]. */
        fun of(rise: Float, dx: Float, apex: Float): LeapKind =
            when {
                rise < -Tuning.Parkour.STEP_MAX -> DROP
                rise > Tuning.Parkour.STEP_MAX ||
                    dx > Tuning.Parkour.STEP_DX_MAX ||
                    apex > Tuning.Parkour.STEP_APEX_MAX -> LEAP
                else -> STEP
            }
    }
}

/**
 * One hop of a route, as the planner chose it.
 *
 * @property takeOffX where the feet leave [from].
 * @property landX where they touch [to].
 * @property runUp px of run in front of [takeOffX] (0: standing take-off).
 * @property rise feet to feet, px, positive up.
 * @property arc the flight.
 */
class RouteLeap(
    val from: SurfaceId,
    val to: SurfaceId,
    val kind: LeapKind,
    val takeOffX: Float,
    val landX: Float,
    val runUp: Float,
    val rise: Float,
    val arc: Arc,
)

/** A planned route: the steps to hand a body, the hops inside them and the planner's cost. */
class Route(val steps: List<PlanStep>, val leaps: List<RouteLeap>, val cost: Float) {
    /** How many hops the route climbs or falls through (a chain is more than one). */
    val chain: Int
        get() = leaps.size
}

/**
 * Plans how a cat gets from one surface to another by jumping, from geometry and the cat's
 * [JumpCapability] alone: no hand-made edges. Every pair of surfaces is a possible hop; it is an
 * edge when a flight within the cat's limits links a take-off and a landing point on them and the
 * arc clears every solid it is not part of ([Solids]). A route is the cheapest chain of edges
 * (Dijkstra over the surfaces): seconds of walking, run-up and flight, a fixed cost per hop (so a
 * cat takes the shortest chain it can do) and a cost on effort that grows with the square of the
 * share of its best the cat spends (so a weak cat shuns high jumps, and anything near the limit is
 * the last choice). Ties go to a tiny deterministic jitter from the cat's seed.
 *
 * The edge kinds are WALK (same surface), [LeapKind.STEP], [LeapKind.LEAP], [LeapKind.DROP] and the
 * chains of them. The planner picks take-off and landing points: it keeps the landing a little in
 * from a surface's ends, lands near the goal on the last hop, backs up for a run-up only when the
 * hop needs one, and rejects arcs that would pass through furniture.
 */
object ParkourPlanner {
    private val ids = SurfaceId.entries.toTypedArray()
    private const val NO_X = Float.NaN
    private const val EDGE_INSET = 2f
    private const val MIN_WALK = 0.25f
    private const val SAME_SPOT = 0.5f
    private const val FREE_WALK_X = 0f
    private const val BASE_SPEED = 32f

    /**
     * The route for a cat with [cap] standing at [fromX] on [from] to [dest] at [x], or null when
     * the cat cannot get there at all. [seed] breaks ties between equally good routes.
     */
    fun plan(
        cap: JumpCapability,
        from: SurfaceId,
        fromX: Float,
        dest: SurfaceId,
        x: Float,
        seed: Int = 0,
    ): Route? {
        if (from == dest) return Route(stayOn(dest, x), emptyList(), 0f)
        val n = ids.size
        val cost = FloatArray(n) { Float.MAX_VALUE }
        val at = FloatArray(n)
        val prev = IntArray(n) { -1 }
        val via = arrayOfNulls<RouteLeap>(n)
        val done = BooleanArray(n)
        cost[from.ordinal] = 0f
        at[from.ordinal] = fromX
        while (true) {
            var u = -1
            for (i in 0 until n) if (!done[i] && cost[i] < Float.MAX_VALUE) {
                if (u < 0 || cost[i] < cost[u]) u = i
            }
            if (u < 0 || u == dest.ordinal) break
            done[u] = true
            for (v in 0 until n) {
                if (done[v] || v == u) continue
                val goal = if (v == dest.ordinal) x else NO_X
                val hop = bestHop(cap, ids[u], ids[v], at[u], goal, seed) ?: continue
                val total = cost[u] + hop.second
                if (total < cost[v]) {
                    cost[v] = total
                    at[v] = hop.first.landX
                    prev[v] = u
                    via[v] = hop.first
                }
            }
        }
        if (cost[dest.ordinal] == Float.MAX_VALUE) return null
        val chain = ArrayList<RouteLeap>(MAX_CHAIN)
        var v = dest.ordinal
        while (v != from.ordinal) {
            chain.add(via[v]!!)
            v = prev[v]
        }
        chain.reverse()
        refine(cap, chain, fromX, x, seed)
        return Route(stepsFor(chain, fromX, dest, x), chain, cost[dest.ordinal])
    }

    /**
     * Choose each landing knowing where the next hop starts, so that a chain flows: the cat lands
     * where the next run-up begins (no walk between two leaps) and stands where it was asked to.
     * Two passes settle it: a landing moves the next take-off, which moves the landing before it.
     */
    private fun refine(
        cap: JumpCapability,
        chain: MutableList<RouteLeap>,
        fromX: Float,
        goal: Float,
        seed: Int,
    ) {
        repeat(REFINE_PASSES) {
            var at = fromX
            for (i in chain.indices) {
                val old = chain[i]
                val next = if (i == chain.lastIndex) goal else runStart(chain[i + 1])
                val between = i != chain.lastIndex
                val hop = bestHop(cap, old.from, old.to, at, next, seed, between)?.first ?: old
                chain[i] = hop
                at = hop.landX
            }
        }
    }

    private fun runStart(hop: RouteLeap): Float =
        hop.takeOffX - (if (hop.landX >= hop.takeOffX) 1f else -1f) * hop.runUp

    private const val REFINE_PASSES = 2

    /** The steps of [plan], or an empty list when there is no way (or nothing to do). */
    fun route(
        cap: JumpCapability,
        from: SurfaceId,
        fromX: Float,
        dest: SurfaceId,
        x: Float,
        seed: Int = 0,
    ): List<PlanStep> = plan(cap, from, fromX, dest, x, seed)?.steps ?: emptyList()

    /**
     * The best single hop from [a] to [b] with no start position or goal (the way a table of what a
     * cat can reach is drawn), or null if there is none.
     */
    fun edge(cap: JumpCapability, a: SurfaceId, b: SurfaceId): RouteLeap? =
        bestHop(cap, a, b, NO_X, NO_X, 0)?.first

    private const val MAX_CHAIN = 6

    private fun stayOn(dest: SurfaceId, x: Float): List<PlanStep> =
        when (dest) {
            SurfaceId.FLOOR -> listOf(PlanStep.Walk(World.clampHouse(x)))
            SurfaceId.BED -> emptyList()
            else -> listOf(PlanStep.Walk(World.clampTo(dest, x)))
        }

    // ---------------------------------------------------------------- steps

    private fun stepsFor(
        chain: List<RouteLeap>,
        fromX: Float,
        dest: SurfaceId,
        x: Float,
    ): List<PlanStep> {
        val steps = ArrayList<PlanStep>(chain.size * 2 + 2)
        var cur = fromX
        for (hop in chain) {
            val dir = if (hop.landX >= hop.takeOffX) 1f else -1f
            val start = hop.takeOffX - dir * hop.runUp
            val b = World.surface(hop.to)
            val a = World.surface(hop.from)
            if (hop.from == SurfaceId.FLOOR) {
                steps.add(PlanStep.Walk(start, floorZ(a, b)))
            } else if (abs(cur - start) > MIN_WALK) {
                steps.add(PlanStep.Walk(start))
            }
            steps.add(PlanStep.Leap(hop.to, hop.landX, hop.takeOffX, hop.arc.apex, hop.arc.effort))
            cur = hop.landX
        }
        val end = if (dest == SurfaceId.FLOOR) World.clampHouse(x) else World.clampTo(dest, x)
        if (dest != SurfaceId.BED && abs(cur - end) > SAME_SPOT) steps.add(PlanStep.Walk(end))
        return steps
    }

    /** The depth the cat stands at on the floor to jump to or from [other]: a prop's own depth. */
    private fun floorZ(floor: Surface, other: Surface): Float =
        when {
            other.id == SurfaceId.BOX -> other.z + World.BOX_FRONT_Z
            other.prop -> other.z
            else -> World.ART_Z
        }

    // ---------------------------------------------------------------- one hop

    /** The best hop from [a] to [b] and its cost, or null. [startX] and [goal] may be [NO_X]. */
    private fun bestHop(
        cap: JumpCapability,
        a: SurfaceId,
        b: SurfaceId,
        startX: Float,
        goal: Float,
        seed: Int,
        nextStart: Boolean = false,
    ): Pair<RouteLeap, Float>? {
        val sa = World.surface(a)
        val sb = World.surface(b)
        val z = if (sa.prop) sa.z else if (sb.prop) sb.z else World.ART_Z
        val ya = level(sa, z)
        val yb = level(sb, z)
        val rise = ya - yb
        if (rise > cap.maxRise(Tuning.Parkour.RUN_UP_MAX_PX) || rise < -cap.maxDrop) return null
        if (maxOf(low(sb) - high(sa), low(sa) - high(sb)) > cap.reachMax) return null
        var best: RouteLeap? = null
        var bestCost = Float.MAX_VALUE
        val takeOffs = takeOffPoints(sa, sb, startX)
        for (xt in takeOffs) {
            for (xl in landings(sb, xt, goal)) {
                val hop = hopBetween(cap, sa, sb, ya, yb, xt, xl) ?: continue
                val c = hopCost(cap, hop, startX, goal, nextStart) + jitter(seed, a, b)
                if (c < bestCost) {
                    bestCost = c
                    best = hop
                }
            }
        }
        return best?.let { it to bestCost }
    }

    private fun level(s: Surface, z: Float): Float =
        if (s.id == SurfaceId.FLOOR) World.floorY(z) else s.y

    private fun hopBetween(
        cap: JumpCapability,
        sa: Surface,
        sb: Surface,
        ya: Float,
        yb: Float,
        xt: Float,
        xl: Float,
    ): RouteLeap? {
        // A wall stands between the rooms: no flight crosses it.
        if (World.roomOf(xt) != World.roomOf(xl)) return null
        val dx = abs(xl - xt)
        val dir = if (xl >= xt) 1f else -1f
        val rise = ya - yb
        val runway = if (dir > 0f) xt - low(sa) else high(sa) - xt
        var runUp = cap.runUpFor(rise, dx, runway)
        if (runUp < 0f) return null
        var arc = cap.arc(rise, dx, runUp) ?: return null
        // A hard jump from rest is worth a short run-up when that makes it clearly easier.
        val p = Tuning.Parkour
        if (runUp == 0f && arc.effort > p.RUN_WHEN_EFFORT && runway >= p.RUN_UP_MIN_PX) {
            val run = cap.arc(rise, dx, p.RUN_UP_MIN_PX)
            if (run != null && run.effort < arc.effort - p.RUN_GAIN) {
                runUp = p.RUN_UP_MIN_PX
                arc = run
            }
        }
        // Floor, props and the like stand in front of the furniture: a hop between two of them is
        // clear of every solid. Anything else must keep its feet out of them.
        val lowPair = (sa.prop || sa.id == SurfaceId.FLOOR) && (sb.prop || sb.id == SurfaceId.FLOOR)
        if (!lowPair && !Solids.clear(sa.id, sb.id, xt, ya, xl, yb, arc.apex, arc.flight))
            return null
        val kind = LeapKind.of(rise, dx, arc.apex)
        return RouteLeap(sa.id, sb.id, kind, xt, xl, runUp, rise, arc)
    }

    private fun low(s: Surface) = if (s.id == SurfaceId.FLOOR) World.HOUSE_MIN_X else s.x0

    private fun high(s: Surface) = if (s.id == SurfaceId.FLOOR) World.HOUSE_MAX_X else s.x1

    /**
     * Seconds-equivalent cost: walking, run-up, flight and settle, a fixed cost per hop, effort.
     */
    private fun hopCost(
        cap: JumpCapability,
        hop: RouteLeap,
        startX: Float,
        goal: Float,
        nextStart: Boolean,
    ): Float {
        val p = Tuning.Parkour
        val dir = if (hop.landX >= hop.takeOffX) 1f else -1f
        val runStart = hop.takeOffX - dir * hop.runUp
        val approach = if (startX.isNaN()) FREE_WALK_X else abs(startX - runStart) / BASE_SPEED
        val run = hop.runUp / (p.RUN_SPEED / 2f)
        // A walk between two leaps breaks a chain: it counts for more than a walk at the end.
        val walkMul = if (nextStart) p.CHAIN_WALK_MUL else 1f
        val finish = if (goal.isNaN()) 0f else abs(goal - hop.landX) / BASE_SPEED * walkMul
        val risk =
            p.EFFORT_COST_S *
                hop.arc.effort *
                hop.arc.effort *
                (if (cap.weak) p.WEAK_EFFORT_MUL else 1f)
        val fixed = if (hop.kind == LeapKind.STEP) p.STEP_LAND_S else p.FIXED_LEAP_S
        return approach + run + hop.arc.flight + fixed + p.HOP_COST_S + risk + finish
    }

    private fun jitter(seed: Int, a: SurfaceId, b: SurfaceId): Float {
        val h = Rng.hash("$seed|${a.ordinal}|${b.ordinal}")
        return ((h ushr 8) and JITTER_MASK) / JITTER_RANGE * Tuning.Parkour.TIE_BREAK
    }

    private const val JITTER_MASK = 0xFFFF
    private const val JITTER_RANGE = 65536f

    // ---------------------------------------------------------------- take-off and landing points

    /** Where on [a] the cat might leave for [b]: where it stands, the ends, and spots in reach. */
    private fun takeOffPoints(a: Surface, b: Surface, startX: Float): FloatArray {
        val inset = if (a.id != SurfaceId.FLOOR && a.x1 - a.x0 > 2f * EDGE_INSET) EDGE_INSET else 0f
        val lo = low(a) + inset
        val hi = high(a) - inset
        val out = ArrayList<Float>(TRY_POINTS)
        if (!startX.isNaN()) {
            out.add(Mathx.clamp(startX, lo, hi))
            // A take-off a short run-up on from where the cat stands: the run starts right here
            // (a chain: the cat lands and goes on, with no walk to a mark in between).
            val toward = if ((low(b) + high(b)) / 2f >= startX) 1f else -1f
            out.add(Mathx.clamp(startX + toward * Tuning.Parkour.RUN_UP_MIN_PX, lo, hi))
        }
        out.add(Mathx.clamp(nearest(b, (low(a) + high(a)) / 2f), lo, hi))
        out.add(Mathx.clamp((low(b) + high(b)) / 2f, lo, hi))
        out.add(lo)
        out.add(hi)
        val anchor = Mathx.clamp(nearest(b, (low(a) + high(a)) / 2f), low(b), high(b))
        for (d in Tuning.Parkour.TRY_DX) {
            out.add(Mathx.clamp(anchor - d, lo, hi))
            out.add(Mathx.clamp(anchor + d, lo, hi))
        }
        return out.distinct().toFloatArray()
    }

    /**
     * Where on [b] the cat might land after leaving from [xt]: the near end, the goal, spots round.
     */
    private fun landings(b: Surface, xt: Float, goal: Float): FloatArray {
        val lo = low(b)
        val hi = high(b)
        val inset =
            if (hi - lo > 2f * Tuning.Parkour.LANDING_INSET) Tuning.Parkour.LANDING_INSET else 0f
        val out = ArrayList<Float>(TRY_POINTS)
        out.add(Mathx.clamp(xt, lo + inset, hi - inset))
        if (!goal.isNaN()) out.add(Mathx.clamp(goal, lo + inset, hi - inset))
        out.add((lo + hi) / 2f)
        for (d in Tuning.Parkour.TRY_DX) {
            out.add(Mathx.clamp(xt - d, lo + inset, hi - inset))
            out.add(Mathx.clamp(xt + d, lo + inset, hi - inset))
        }
        return out.distinct().toFloatArray()
    }

    private fun nearest(s: Surface, x: Float): Float = Mathx.clamp(x, low(s), high(s))

    private const val TRY_POINTS = 16
}
