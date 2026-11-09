package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.lifedirector.PageValues.member
import com.lifemax.rpg.core.lifedirector.PageValues.truthy
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/**
 * LDW-2: why the construction placed a task where it did: its index among the active tasks [k], the
 * waking day and slots, its best score with fairness ([score]), the margin over its second-best day
 * ([regret]), how many days could take it, its selection [key] and fairness multiplier, and whether
 * it was taken only because every other candidate was held back by the share cap ([capRelaxed],
 * LDW-4).
 */
class Selection(
    val k: Int,
    val cycle: Int,
    val slot: Int,
    val n: Int,
    val score: Double,
    val regret: Double,
    val count: Int,
    val key: Double,
    val fairness: Double,
    var capRelaxed: Boolean = false,
)

/** One placement of the construction, in the order they were made. */
class Placement(val session: PlanSession, val selection: Selection)

/**
 * What [PlanConstruction.construct] gives: the preparation (LDR), the demand (LDE-4), every task
 * and the active ones (not blocked), the first raw score of every active task in every waking day
 * ([firstScores], row by row, `k * days + day`), and the placements in order. The state is left as
 * the construction ends: where the repair (WP-LD5) starts.
 */
class Construction(
    val prepared: PreparedPlan,
    val demand: PlanDemandResult,
    val tasks: List<PlanTask>,
    val active: List<PlanTask>,
    val firstScores: List<DayBest>,
    val placements: List<Placement>,
) {
    val state: PlanState
        get() = prepared.state
}

/**
 * LDW-1 to LDW-5: the construction phase of `generateSchedule` (`engine.js:1204-1303`): the plan is
 * prepared (LDR), the demand compiled on it (LDE-4), and the tasks placed one at a time, the most
 * urgent tier first and within a tier the plain tasks before those a goal session could cover, each
 * time the candidate with the highest selection key (LDW-2), until none fits.
 */
object PlanConstruction {
    private const val TOP_TIER = 4
    private const val ROUNDS = 2000
    private const val REGRET_WEIGHT = 0.35
    private const val SCARCITY = 15.0
    private const val CAP_MINUTES = 180.0
    private const val CAP_SHARE = 0.60
    private const val CAP_OVER = 0.20
    private const val WEEKLY_MINIMUM = "WEEKLY_MINIMUM"

    fun construct(input: JsonValue?): Construction {
        val prep = PlanPreparation.prepare(input)
        val s = prep.state
        val keptForDemand =
            prep.kept +
                prep.history.filter {
                    member(it, "lifecycleState") == JsonString("SKIPPED_VOLUNTARY") &&
                        PageValues.lessOrEqual(member(it, "tier"), JsonNumber(2.0))
                }
        val used =
            (prep.history + prep.kept)
                .mapNotNull { (member(it, "instanceId") as? JsonString)?.value }
                .toSet()
        val demand = PlanDemand.of(input, keptForDemand, used, s.plan)
        val tasks = demand.candidates.tasks.zip(demand.prepared) { c, p -> PlanTask(c, p) }
        s.tasks = tasks
        for (t in tasks) prep.replannable[JsonString("i" + t.id.substring(1))]?.let {
            t.origStartEpoch = member(it, "startEpoch")
        }
        s.fair.share.putAll(demand.shares)
        val active = tasks.filter { it.task.blockedReason == null }
        val run = Run(s, active, demand.shares.size)
        return Construction(prep, demand, tasks, active, run.first, run.placements)
    }

    /** LDW-5 `anchorsDependents`: whether a placed task not covered by another depends on it. */
    fun anchorsDependents(s: PlanState, session: PlanSession): Boolean =
        anchors(
            s.tasks,
            { it.isPlaced },
            { truthy(it.coveredBy) },
            { it.dependsOnTask(session.taskId) },
        )

    /** LDW-5 over any task shape: the first task that is placed, not covered, and depends on it. */
    fun <T> anchors(
        tasks: List<T>,
        placed: (T) -> Boolean,
        covered: (T) -> Boolean,
        dependsOn: (T) -> Boolean,
    ): Boolean = tasks.any { placed(it) && !covered(it) && dependsOn(it) }

    /** One run of the construction over [active] tasks and the state's waking days. */
    private class Run(val s: PlanState, val active: List<PlanTask>, val goals: Int) {
        val days = s.cycles.size
        val score = DoubleArray(active.size * days) { Double.NEGATIVE_INFINITY }
        val slot = IntArray(active.size * days) { -1 }
        val length = IntArray(active.size * days)
        val dynamic = active.map { it.task.dependsOn.isNotEmpty() || it.task.requiresPrior != null }
        val fixed = active.mapIndexed { k, t -> if (dynamic[k]) null else Feasibility.window(s, t) }
        val minimumLeft = LinkedHashMap<String, Int>()
        val placements = ArrayList<Placement>()
        val first: List<DayBest>

        init {
            for (k in active.indices) evalRow(k)
            first = score.indices.map { DayBest(score[it], slot[it], length[it]) }
            for (t in active) if (
                !t.task.goalId.isNullOrEmpty() && t.task.tierReason == WEEKLY_MINIMUM
            )
                minimumLeft[t.task.goalId] = (minimumLeft[t.task.goalId] ?: 0) + 1
            for (tier in TOP_TIER downTo 1) for (coverPass in listOf(false, true)) placeTier(
                tier,
                coverPass,
            )
        }

        /** The best raw placement of active task [k] in day [b] (LDW-1, fairness off). */
        fun evalCell(k: Int, b: Int) {
            val t = active[k]
            val w = fixed[k] ?: Feasibility.window(s, t)
            val j = k * days + b
            if (w.blocked != null) {
                score[j] = Double.NEGATIVE_INFINITY
                slot[j] = -1
                return
            }
            s.rawScores = true
            val r = Placer.bestInDay(s, t, s.cycles[b], w, null)
            s.rawScores = false
            score[j] = r.score
            slot[j] = r.slot
            length[j] = r.n
        }

        fun evalRow(k: Int) {
            for (b in 0 until days) evalCell(k, b)
        }

        /**
         * LDW-4: a goal's session is held back while the plan has two goals or more, at least 180
         * goal minutes, another goal still short of its weekly minimum, and this session would take
         * the goal's share above the larger of 0.60 and its target share plus 0.20.
         */
        fun capBlocks(t: PlanTask, n: Int): Boolean {
            val goal = t.task.goalId
            if (goal.isNullOrEmpty() || goals < 2 || s.fair.total < CAP_MINUTES) return false
            if (minimumLeft.none { (id, left) -> id != goal && left > 0 }) return false
            val minutes = n * PlanState.SLOT_MINUTES
            val share =
                ((s.fair.goalMinutes[goal]?.takeIf { truthy(it) } ?: 0.0) + minutes) /
                    (s.fair.total + minutes)
            return share >
                maxOf(CAP_SHARE, (s.fair.share[goal]?.takeIf { truthy(it) } ?: 0.0) + CAP_OVER)
        }

        /** LDW-2, LDW-3: [tier]'s tasks of one kind (plain or coverable), until none fits. */
        fun placeTier(tier: Int, coverPass: Boolean) {
            var round = 0
            while (round++ < ROUNDS) {
                var win: Selection? = null
                var held: Selection? = null
                for (k in active.indices) {
                    val t = active[k]
                    if (
                        t.isPlaced || t.task.tier != tier || (t.task.coverable != null) != coverPass
                    )
                        continue
                    if (dynamic[k]) evalRow(k)
                    val pick = select(k, t) ?: continue
                    if (capBlocks(t, pick.n)) {
                        if (beats(pick, held)) held = pick
                        continue
                    }
                    if (beats(pick, win)) win = pick
                }
                if (win == null && held != null) win = held.also { it.capRelaxed = true }
                if (win == null) break
                val t = active[win.k]
                val session = Placer.commit(s, t, win.cycle, win.slot, win.n)
                placements += Placement(session, win)
                if (!t.task.goalId.isNullOrEmpty() && t.task.tierReason == WEEKLY_MINIMUM)
                    minimumLeft[t.task.goalId] = (minimumLeft[t.task.goalId] ?: 0) - 1
                for (k in active.indices) {
                    if (active[k].isPlaced) continue
                    if (active[k].task.spacingKey == t.task.spacingKey) evalRow(k)
                    else evalCell(k, win.cycle)
                }
            }
        }

        /** A higher key wins; an equal key goes to the smaller task id (UTF-16 order). */
        fun beats(a: Selection, b: Selection?): Boolean =
            if (b == null) a.key > Double.NEGATIVE_INFINITY
            else a.key > b.key || (a.key == b.key && active[a.k].id < active[b.k].id)

        /**
         * LDW-2: task [k]'s candidate: its best day (the first highest raw score) and second-best
         * score, both times its fairness multiplier and rounded (LDU-2), the regret between them,
         * the scarcity `15 / max(1, days that fit)`, and the key `q6(best + 0.35 * regret +
         * scarcity)`; none when no day fits.
         */
        fun select(k: Int, t: PlanTask): Selection? {
            var best = Double.NEGATIVE_INFINITY
            var second = Double.NEGATIVE_INFINITY
            var bestDay = -1
            var count = 0
            for (b in 0 until days) {
                val j = k * days + b
                if (slot[j] < 0) continue
                count++
                val v = score[j]
                if (v > best) {
                    second = best
                    best = v
                    bestDay = b
                } else if (v > second) second = v
            }
            if (bestDay < 0) return null
            val f = PlacementScore.fairness(s, t)
            val bestF = PlacementScore.q6(best * f)
            val secondF =
                if (second == Double.NEGATIVE_INFINITY) 0.0 else PlacementScore.q6(second * f)
            val regret = bestF - secondF
            val scarcity = SCARCITY / maxOf(1, count)
            val key = PlacementScore.q6(bestF + REGRET_WEIGHT * regret + scarcity)
            val j = k * days + bestDay
            return Selection(k, bestDay, slot[j], length[j], bestF, regret, count, key, f)
        }
    }
}
