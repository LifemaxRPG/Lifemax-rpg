package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/**
 * LDO-2: how a session got where it is (the page's `placedMeta` entry): the construction's
 * [selection] (phase 1), or a later [phase] (2 for the repair and the fill pass) with its [move]
 * (`COMPRESSION`, `SWAP`, `RELOCATED`, `EVICT`, `FILL`), the placement's [score] and the number of
 * waking days that could take it ([count]). A rescheduling placement (LDY) names its phase by its
 * radius instead ([label]: `R1` to `R4`).
 */
class PlacementRecord(
    val phase: Int,
    val move: String?,
    val score: Double,
    val count: Int,
    val selection: Selection? = null,
    val label: String? = null,
) {
    /** The margin over the second-best day (the construction's; 0 for a later move). */
    val regret: Double
        get() = selection?.regret ?: 0.0

    /** The selection key (the construction's; null for a later move). */
    val key: Double?
        get() = selection?.key

    companion object {
        const val CONSTRUCTION = 1
        const val REPAIR = 2

        fun of(s: Selection) = PlacementRecord(CONSTRUCTION, null, s.score, s.count, s)
    }
}

/** LDX-3: one of the repair's successful moves, as the page logs it. */
class RepairMove(
    val type: String,
    val taskId: String,
    val cycleId: String? = null,
    val other: JsonValue? = null,
)

/** LDX-3: what the repair did (`runLocalRepair`'s log). */
class RepairLog {
    var attempts = 0
    var successes = 0
    var rollbacks = 0
    var changed = false
    val moves = ArrayList<RepairMove>()
}

/**
 * LDX-3 to LDX-6: phase 2 of planning (`engine.js:1309-1392`): the bounded local repair for the
 * urgent tasks the construction left out, and the fill pass that follows a repair that changed
 * anything.
 */
object LocalRepair {
    /** LDX-3: the repair's budget of moves (`maxIter`). */
    const val BUDGET = 400
    private const val ROUNDS = 200
    private const val TOP_TIER = 4
    private const val LOW_TIER = 2
    private const val URGENT_TIER = 3
    private const val REGRET_WEIGHT = 0.35
    private const val SCARCITY = 15.0

    /**
     * LDX-3 `runLocalRepair`: the [targets] in order of tier (highest first), then id (UTF-16
     * order). Each one not yet placed, while the budget is not spent, with a window that is not
     * blocked, tries move A in each waking day, then move B with each candidate session; every day
     * tried and every session tried spends one of the budget (the count is checked before it grows
     * past the budget, so it ends at most one past it). [watch] sees each target as its turn
     * starts, with the log so far.
     */
    fun repair(
        s: PlanState,
        targets: List<PlanTask>,
        records: MutableMap<String, PlacementRecord>,
        budget: Int = BUDGET,
        watch: ((PlanTask, RepairLog) -> Unit)? = null,
    ): RepairLog {
        val log = RepairLog()
        val order =
            targets.sortedWith { a, b ->
                if (a.task.tier != b.task.tier) b.task.tier - a.task.tier else a.id.compareTo(b.id)
            }
        for (t in order) {
            watch?.invoke(t, log)
            if (t.isPlaced || s.iterations >= budget) continue
            val w = Feasibility.window(s, t)
            if (w.blocked != null) continue
            compress(s, t, w, records, budget, log)
            if (!t.isPlaced) swap(s, t, records, budget, log)
        }
        return log
    }

    /**
     * Whether a session may be moved by the repair: an auto-placed one still to start, whose task
     * is on the plan, that no placed task depends on.
     */
    private fun movable(s: PlanState, x: PlanSession, tier: Int): Boolean {
        val c = x.commitment ?: return false
        return x.startEpoch > s.now &&
            c.tier <= tier &&
            x.taskId is JsonString &&
            s.taskById.containsKey((x.taskId as JsonString).value) &&
            !PlanConstruction.anchorsDependents(s, x)
    }

    /**
     * LDX-4 move A, elastic compression: in a waking day that meets the target's window, the
     * movable sessions of a tier up to the target's that are longer than their shortest length
     * (lowest tier first, then earliest), when shortening all of them frees at least the target's
     * shortest length: each is taken off and put back at its shortest length where it was (if LDK-2
     * allows), else at its best place in that day (or the move fails); then the target goes to its
     * best place in that day. A move that fails, or leaves no place for the target, is rolled back
     * (LDX-2).
     */
    private fun compress(
        s: PlanState,
        t: PlanTask,
        w: TaskWindow,
        records: MutableMap<String, PlacementRecord>,
        budget: Int,
        log: RepairLog,
    ) {
        for (c in s.cycles) {
            if (t.isPlaced || ++s.iterations > budget) break
            if (c.cycle.endSlot <= w.earliestSlot || c.cycle.startSlot >= w.latestSlot) continue
            val shrinkable =
                c.sessions
                    .filter {
                        movable(s, it, t.task.tier) && it.durationSlots > it.commitment!!.minSlots
                    }
                    .sortedWith { a, b ->
                        val d = a.commitment!!.tier - b.commitment!!.tier
                        if (d != 0) d else PageValues.byDifference(a.startEpoch - b.startEpoch)
                    }
            val freed =
                shrinkable.fold(0.0) { n, x -> n + x.durationSlots - x.commitment!!.minSlots }
            if (freed < t.prepared.dMinSlots) continue
            log.attempts++
            val before = PlanSnapshot.of(s)
            if (shrinkAll(s, c, shrinkable)) {
                val r = Placer.bestAcross(s, t, setOf(c.index))
                if (r.feasible) {
                    val placed = Placer.commit(s, t, r.cycle, r.slot, r.n)
                    records[key(placed)] =
                        PlacementRecord(PlacementRecord.REPAIR, "COMPRESSION", r.score, r.count)
                    log.successes++
                    log.changed = true
                    log.moves += RepairMove("COMPRESSION", t.id, cycleId = c.cycle.id)
                    break
                }
            }
            before.restore(s)
            log.rollbacks++
        }
    }

    /** Move A's shortening: false when a session finds no place again. */
    private fun shrinkAll(s: PlanState, c: CycleState, sessions: List<PlanSession>): Boolean {
        for (x in sessions) {
            val task = x.commitment!!.task
            val at = s.slots.slotAt(x.startEpoch).toInt()
            PlanRemoval.remove(s, x)
            val w = Feasibility.window(s, task)
            if (w.blocked != null) return false
            val shortest = task.prepared.dMinSlots.toInt()
            if (Feasibility.check(s, task, c, at, shortest, w) == Refusal.OK)
                Placer.commit(s, task, c.index, at, shortest, x.instanceId)
            else {
                val e = Placer.bestInDay(s, task, c, w, null)
                if (e.slot < 0) return false
                Placer.commit(s, task, c.index, e.slot, e.n, x.instanceId)
            }
        }
        return true
    }

    /**
     * LDX-5 move B, one for one: each movable session of a tier up to the target's (lowest tier
     * first, then earliest, then id) is taken off and the target placed at its best place; if the
     * evicted session's task then fits somewhere it goes there (a swap); if not, a session of tier
     * 2 or lower makes way for a target of tier 3 or more and stays off (its task is marked
     * displaced by the target); otherwise the move is rolled back.
     */
    private fun swap(
        s: PlanState,
        t: PlanTask,
        records: MutableMap<String, PlacementRecord>,
        budget: Int,
        log: RepairLog,
    ) {
        val incumbents =
            s.sessions
                .filter { movable(s, it, t.task.tier) }
                .sortedWith { a, b ->
                    val d = a.commitment!!.tier - b.commitment!!.tier
                    when {
                        d != 0 -> d
                        a.startEpoch != b.startEpoch ->
                            PageValues.byDifference(a.startEpoch - b.startEpoch)
                        PageValues.less(a.instanceId, b.instanceId) -> -1
                        PageValues.less(b.instanceId, a.instanceId) -> 1
                        else -> 0
                    }
                }
        for (inc in incumbents) {
            if (++s.iterations > budget) break
            log.attempts++
            val before = PlanSnapshot.of(s)
            val incTask = inc.commitment!!.task
            PlanRemoval.remove(s, inc)
            val r = Placer.bestAcross(s, t)
            if (r.feasible) {
                val placed = Placer.commit(s, t, r.cycle, r.slot, r.n)
                val r2 = Placer.bestAcross(s, incTask)
                if (r2.feasible) {
                    val moved = Placer.commit(s, incTask, r2.cycle, r2.slot, r2.n, inc.instanceId)
                    records[key(placed)] =
                        PlacementRecord(PlacementRecord.REPAIR, "SWAP", r.score, r.count)
                    records[key(moved)] =
                        PlacementRecord(PlacementRecord.REPAIR, "RELOCATED", r2.score, r2.count)
                    log.successes++
                    log.changed = true
                    log.moves += RepairMove("SWAP", t.id, other = inc.instanceId)
                    return
                }
                if (inc.commitment.tier <= LOW_TIER && t.task.tier >= URGENT_TIER) {
                    incTask.displacedBy = t.id
                    records[key(placed)] =
                        PlacementRecord(PlacementRecord.REPAIR, "EVICT", r.score, r.count)
                    log.successes++
                    log.changed = true
                    log.moves += RepairMove("EVICT", t.id, other = inc.instanceId)
                    return
                }
            }
            before.restore(s)
            log.rollbacks++
        }
    }

    /**
     * LDX-6 `fillPass`: tier by tier from 4 down, up to 200 rounds each: of the [tasks] of that
     * tier not placed and not displaced, the one whose best place (LDW-1, with fairness) has the
     * highest key `q6(score + 0.35 * (score - second) + 15 / max(1, days))` (the smaller id on a
     * tie) is committed there, until none fits.
     */
    fun fill(s: PlanState, tasks: List<PlanTask>, records: MutableMap<String, PlacementRecord>) {
        for (tier in TOP_TIER downTo 1) {
            var round = 0
            while (round < ROUNDS) {
                round++
                var best: PlanFit? = null
                var bestKey = 0.0
                var bestTask: PlanTask? = null
                for (t in tasks) {
                    if (t.isPlaced || t.task.tier != tier || t.displacedBy != null) continue
                    val r = Placer.bestAcross(s, t)
                    if (!r.feasible) continue
                    val k =
                        PlacementScore.q6(
                            r.score +
                                REGRET_WEIGHT * (r.score - r.second) +
                                SCARCITY / maxOf(1, r.count)
                        )
                    if (best == null || k > bestKey || (k == bestKey && t.id < bestTask!!.id)) {
                        best = r
                        bestKey = k
                        bestTask = t
                    }
                }
                if (bestTask == null) break
                val placed = Placer.commit(s, bestTask, best!!.cycle, best.slot, best.n)
                records[key(placed)] =
                    PlacementRecord(PlacementRecord.REPAIR, "FILL", best.score, best.count)
            }
        }
    }

    /** A record's key: the session's id as text. */
    fun key(x: PlanSession): String = PageValues.text(x.instanceId)
}
