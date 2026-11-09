package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/** LDW-1: the best placement found in one waking day: [slot] -1 (and [score] -infinity) none. */
class DayBest(val score: Double, val slot: Int, val n: Int)

/**
 * LDW-1 `evalAcross`: where a task fits best over the plan: [feasible] with its waking day, slot,
 * length and score, the best score of any other day ([second], 0 when none) and how many days could
 * take it ([count]); or not, and [reason] why (a blocked window's reason, else BUSY).
 */
class PlanFit(
    val feasible: Boolean,
    val reason: Refusal? = null,
    val cycle: Int = -1,
    val slot: Int = -1,
    val n: Int = 0,
    val score: Double = 0.0,
    val second: Double = 0.0,
    val count: Int = 0,
)

/** The members a committed session takes from its task (`commit`, `engine.js:1053-1060`). */
class Commitment(
    val goalId: String?,
    val minSlots: Double,
    val optSlots: Double,
    val tier: Int,
    val compressed: Boolean,
    val origStartEpoch: JsonValue?,
)

/** LDW-1 the argmin search within a day and across days, and LDW-6 `commit`. */
object Placer {
    /** Reason tallies are indexed by [Refusal.code]. */
    const val TALLY_SIZE = 16

    /**
     * LDW-1 `evalInBwc`: the best placement of [t] in waking day [c] within window [w]: for each
     * length from its best down to its shortest, each start from the later of the day's start and
     * the window's up to the last that ends inside both, the utility (LDU-1) of every start that
     * passes LDK-2; the first highest wins (so the longer length, then the earlier start). A day
     * outside the window counts once as WINDOW in [tally]; at the shortest length a start without a
     * long enough free run counts as BUSY, and every refusal of LDK-2 by its reason.
     */
    fun bestInDay(
        s: PlanState,
        t: PlanTask,
        c: CycleState,
        w: TaskWindow,
        tally: IntArray?,
    ): DayBest {
        var best = Double.NEGATIVE_INFINITY
        var bestSlot = -1
        var bestN = 0
        if (c.cycle.endSlot <= w.earliestSlot || c.cycle.startSlot >= w.latestSlot) {
            if (tally != null) tally[Refusal.WINDOW.code]++
            return DayBest(best, bestSlot, bestN)
        }
        val from = maxOf(c.cycle.startSlot, w.earliestSlot)
        val longest = t.prepared.dOptSlots
        val shortest = t.prepared.dMinSlots
        var n = longest
        while (n >= shortest) {
            val last = minOf(c.cycle.endSlot, w.latestSlot) - n
            val len = n.toInt()
            var at = from
            while (at <= last) {
                val slot = at.toInt()
                at += 1
                if (s.energy.runAhead[slot] < n) {
                    if (tally != null && n == shortest) tally[Refusal.BUSY.code]++
                    continue
                }
                val r = Feasibility.check(s, t, c, slot, len, w)
                if (r != Refusal.OK) {
                    if (tally != null) tally[r.code]++
                    continue
                }
                val u = PlacementScore.utility(s, t, c, slot, len)
                if (u > best) {
                    best = u
                    bestSlot = slot
                    bestN = len
                }
            }
            n -= 1
        }
        return DayBest(best, bestSlot, bestN)
    }

    /**
     * LDW-1 `evalAcross`: [bestInDay] in each waking day (only [only]'s, when given) in order; the
     * first highest day wins.
     */
    fun bestAcross(s: PlanState, t: PlanTask, only: Set<Int>? = null): PlanFit {
        val w = Feasibility.window(s, t)
        if (w.blocked != null) return PlanFit(false, w.blocked)
        var best: DayBest? = null
        var bestCycle = -1
        var second = Double.NEGATIVE_INFINITY
        var count = 0
        for (c in s.cycles) {
            if (only != null && c.index !in only) continue
            val r = bestInDay(s, t, c, w, null)
            if (r.slot < 0) continue
            count++
            if (best == null || r.score > best.score) {
                if (best != null) second = maxOf(second, best.score)
                best = r
                bestCycle = c.index
            } else second = maxOf(second, r.score)
        }
        if (best == null) return PlanFit(false, Refusal.BUSY)
        return PlanFit(
            true,
            null,
            bestCycle,
            best.slot,
            best.n,
            best.score,
            if (second == Double.NEGATIVE_INFINITY) 0.0 else second,
            count,
        )
    }

    /**
     * LDW-6 `commit`: [t]'s session at slots [slot] to `slot + n` of waking day [cycle] goes on the
     * plan (LDR-3 `occupy`, as a task) and the task is placed. A session linked to a goal then
     * covers, in the tasks' order, each other unplaced task with a daily cover (LDC-8) whose day
     * holds its start, when its tag fits the cover (exactly, for an exclusive quest; else by LDD-1,
     * a check-in tag only exactly) and it does not already count toward that requirement: that task
     * is placed, covered by this session, and the session takes the requirement.
     */
    fun commit(s: PlanState, t: PlanTask, cycle: Int, slot: Int, n: Int): PlanSession {
        val task = t.task
        val c = s.cycles[cycle]
        val session =
            PlanSession(
                JsonString("i" + task.id.substring(1)),
                JsonString(task.id),
                JsonString(task.templateId),
                task.tag,
                s.epochOf(slot.toDouble()),
                s.epochOf((slot + n).toDouble()),
                task.physical,
                task.cognitive,
                JsonString(task.spacingKey),
                task.linkedGoalIds.map(::JsonString),
                task.linkedQuestRequirementIds.mapTo(ArrayList(), ::linkJson),
                n.toDouble(),
                c.cycle.id,
                kept = false,
                commitment =
                    Commitment(
                        task.goalId,
                        t.prepared.dMinSlots,
                        t.prepared.dOptSlots,
                        task.tier,
                        n < t.prepared.dOptSlots,
                        if (PageValues.truthy(t.origStartEpoch)) t.origStartEpoch else null,
                    ),
            )
        s.occupy(session, cycle, SlotCode.TASK)
        t.isPlaced = true
        if (session.goalLinks.isNotEmpty())
            for (u in s.tasks) {
                val cover = u.task.coverable ?: continue
                if (
                    u.isPlaced ||
                        u === t ||
                        session.startEpoch < cover.start ||
                        session.startEpoch >= cover.end
                )
                    continue
                val fits =
                    if (cover.exactOnly) PageValues.same(session.tag, cover.reqTag)
                    else
                        ActivityTags.matches(cover.reqTag, session.tag) &&
                            (!Candidates.checkIn(session.tag ?: JsonString("undefined")) ||
                                PageValues.same(session.tag, cover.reqTag))
                if (!fits || session.counts(cover.link.questId, cover.link.reqId)) continue
                u.isPlaced = true
                u.coveredBy = session.instanceId
                session.questLinks += linkJson(cover.link)
            }
        return session
    }

    /** A quest link as the page's object holds it. */
    fun linkJson(l: QuestLink): JsonValue =
        JsonObject(
            linkedMapOf(
                "questId" to JsonString(l.questId),
                "reqId" to JsonString(l.reqId),
                "questTitle" to JsonString(l.questTitle),
                "deadlineEpoch" to l.deadlineEpoch,
            )
        )
}
