package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.lifedirector.PageValues.truthy
import com.lifemax.rpg.core.lifedirector.ScheduleJson.num
import com.lifemax.rpg.core.lifedirector.ScheduleJson.obj
import com.lifemax.rpg.core.lifedirector.ScheduleJson.orNull
import com.lifemax.rpg.core.lifedirector.ScheduleJson.str
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import java.util.Collections
import java.util.IdentityHashMap

/**
 * LDM: one task a reschedule must place again: its stored snapshot ([source]), why (`REPLACEMENT`,
 * `RESIDUAL`, `TIME_FREED`), the session it replaces ([origInstanceId]), a waking day it must not
 * go back to ([excludeCycleId], a skip's), the instant whose day it should try first
 * ([preferEpoch]), and for a sick spell the end it must wait for ([notBefore]). [newId] is its new
 * task id (LDY-1).
 */
class Replacement(
    val source: JsonValue,
    val reason: String,
    val origInstanceId: JsonValue?,
    val preferEpoch: JsonValue?,
    val excludeCycleId: JsonValue? = null,
) {
    var notBefore: JsonValue? = null
    var newId: String = ""
}

/** What [RadiusPlacement.place] did: the radius it ended at and its change-record actions. */
class RadiusResult(val radius: Int, val actions: List<JsonValue>)

/**
 * The rescheduling state's extras: the placement records (`placedMeta`) and the sessions a
 * compression put back with the page's `kept: false` member (LDY-3).
 */
class RescheduleRecords {
    val records = LinkedHashMap<String, PlacementRecord>()
    val keptFalse: MutableSet<PlanSession> = Collections.newSetFromMap(IdentityHashMap())
}

/**
 * LDY-2 to LDY-4 `placeByRadius` (`engine.js:1843-1903`): a re-placed task goes to the closest
 * radius that takes it. Radius 1 its home day (the day of its preferred instant, never before the
 * clock), radius 2 any allowed day, radius 3 an allowed day after shortening up to two of its
 * flexible sessions, radius 4 (tiers 3 and 4 only) after evicting one or two sessions of a lower
 * tier; a low tier that reaches radius 4 is shed. Outside a critical task, nothing starting in the
 * next 24 hours (the freeze zone) is shortened or evicted.
 */
object RadiusPlacement {
    private const val FREEZE = 24 * 3600.0
    private const val CRITICAL = 4
    private const val LOW_TIER = 2
    private const val EVICTABLE_TIER = 3
    private const val SHRINK_LIMIT = 2
    private const val RADIUS3_BUDGET = 600
    private const val RADIUS4_BUDGET = 900

    fun place(s: PlanState, t: PlanTask, tp: Replacement, rec: RescheduleRecords): RadiusResult {
        val actions = ArrayList<JsonValue>()
        val tier = t.task.tier
        val freezeEnd = if (tier >= CRITICAL) s.now else s.now + FREEZE
        val allowed =
            s.cycles
                .filter { !PageValues.same(JsonString(it.cycle.id), tp.excludeCycleId) }
                .map { it.index }
                .toSet()
        fun done(r: PlanFit, radius: Int, move: String? = null): RadiusResult {
            val x = Placer.commit(s, t, r.cycle, r.slot, r.n)
            rec.records[LocalRepair.key(x)] =
                PlacementRecord(0, move, r.score, r.count, label = "R$radius")
            actions += obj {
                this["action"] = str("INSERT")
                this["instanceId"] = orNull(x.instanceId)
                this["radius"] = num(radius)
                this["replaces"] =
                    tp.origInstanceId?.takeIf(::truthy) ?: com.lifemax.rpg.core.save.JsonNull
                this["to"] = num(x.startEpoch)
            }
            return RadiusResult(radius, actions)
        }
        val preferred = tp.preferEpoch?.takeIf(::truthy)?.let(PageValues::number) ?: s.now
        val home = PlanPreparation.cycleAt(s, maxOf(preferred, s.now))
        if (home >= 0 && home in allowed) {
            val r = Placer.bestAcross(s, t, setOf(home))
            if (r.feasible) return done(r, 1)
        }
        Placer.bestAcross(s, t, allowed).let { if (it.feasible) return done(it, 2) }
        for (c in s.cycles) {
            if (c.index !in allowed || s.iterations++ > RADIUS3_BUDGET) continue
            val shrink = shrinkable(s, c, freezeEnd)
            if (
                shrink.isEmpty() ||
                    shrink.fold(0.0) { n, x -> n + x.durationSlots - HeldSessions.minSlots(x) } < 1
            )
                continue
            val before = PlanSnapshot.of(s)
            val lengths = shrink.map { it.instanceId to HeldSessions.slotsValue(it) }
            if (shrinkAll(s, c, shrink, rec)) {
                val r = Placer.bestAcross(s, t, setOf(c.index))
                if (r.feasible) {
                    for ((id, slots) in lengths) actions += obj {
                        this["action"] = str("COMPRESS")
                        this["instanceId"] = id
                        this["fromSlots"] = slots
                    }
                    return done(r, 3, "COMPRESSION")
                }
            }
            before.restore(s)
        }
        if (tier <= LOW_TIER) return RadiusResult(4, actions)
        return evict(s, t, allowed, freezeEnd, rec, actions, ::done)
    }

    /** LDY-3: up to two flexible sessions of the day, lowest tier first, then earliest. */
    private fun shrinkable(s: PlanState, c: CycleState, freezeEnd: Double): List<PlanSession> =
        c.sessions
            .filter {
                !HeldSessions.kept(it) &&
                    !HeldSessions.locked(it) &&
                    it.startEpoch > freezeEnd &&
                    it.durationSlots > HeldSessions.minSlots(it) &&
                    HeldSessions.hasTask(s, it) &&
                    !PlanConstruction.anchorsDependents(s, it)
            }
            .sortedWith { a, b ->
                val d =
                    PageValues.number(HeldSessions.tier(a)) -
                        PageValues.number(HeldSessions.tier(b))
                if (truthy(d)) PageValues.byDifference(d)
                else PageValues.byDifference(a.startEpoch - b.startEpoch)
            }
            .take(SHRINK_LIMIT)

    /**
     * LDY-3: each session is taken off and put back where it started at its task's shortest length
     * (keeping its id and explanation, `kept: false`); false when that is not allowed (LDK-2).
     */
    private fun shrinkAll(
        s: PlanState,
        c: CycleState,
        shrink: List<PlanSession>,
        rec: RescheduleRecords,
    ): Boolean {
        for (x in shrink) {
            val task = HeldSessions.taskOf(s, x)!!
            val at = s.slots.slotAt(x.startEpoch).toInt()
            PlanRemoval.remove(s, x)
            val w = Feasibility.window(s, task)
            val n = task.prepared.dMinSlots.toInt()
            if (w.blocked != null || Feasibility.check(s, task, c, at, n, w) != Refusal.OK)
                return false
            val back = Placer.commit(s, task, c.index, at, n, x.instanceId)
            back.explanation = x.explanation
            rec.keptFalse += back
        }
        return true
    }

    /**
     * LDY-4: sessions of a lower tier than the task (and at most tier 3), movable, after the freeze
     * zone, lowest tier then latest then id; tried one at a time, then in pairs of the same day.
     * The first set whose removal lets the task fit (an allowed day) is evicted: the task is placed
     * and each evicted session goes to its best place anywhere (moved) or is shed.
     */
    private fun evict(
        s: PlanState,
        t: PlanTask,
        allowed: Set<Int>,
        freezeEnd: Double,
        rec: RescheduleRecords,
        actions: MutableList<JsonValue>,
        done: (PlanFit, Int, String?) -> RadiusResult,
    ): RadiusResult {
        val tier = JsonNumber(t.task.tier.toDouble())
        val victims =
            s.sessions
                .filter {
                    val vt = HeldSessions.tier(it)
                    !HeldSessions.kept(it) &&
                        !HeldSessions.locked(it) &&
                        PageValues.order(vt, tier) == -1 &&
                        PageValues.lessOrEqual(vt, JsonNumber(EVICTABLE_TIER.toDouble())) &&
                        it.startEpoch > freezeEnd &&
                        HeldSessions.hasTask(s, it) &&
                        !PlanConstruction.anchorsDependents(s, it)
                }
                .sortedWith { a, b ->
                    val d =
                        PageValues.number(HeldSessions.tier(a)) -
                            PageValues.number(HeldSessions.tier(b))
                    when {
                        truthy(d) -> PageValues.byDifference(d)
                        truthy(b.startEpoch - a.startEpoch) ->
                            PageValues.byDifference(b.startEpoch - a.startEpoch)
                        else -> HeldSessions.byId(a.instanceId, b.instanceId)
                    }
                }
        val sets = victims.map { listOf(it) }.toMutableList()
        for (a in victims.indices) for (b in a + 1 until victims.size) if (
            victims[a].cycleId == victims[b].cycleId
        )
            sets += listOf(victims[a], victims[b])
        for (set in sets) {
            if (s.iterations++ > RADIUS4_BUDGET) break
            val before = PlanSnapshot.of(s)
            val tasks = set.map { HeldSessions.taskOf(s, it)!! }
            for (v in set) PlanRemoval.remove(s, v)
            val r = Placer.bestAcross(s, t, allowed)
            if (r.feasible) {
                val res = done(r, 4, "EVICT")
                set.forEachIndexed { k, v ->
                    val r2 = Placer.bestAcross(s, tasks[k])
                    if (r2.feasible) {
                        val m = Placer.commit(s, tasks[k], r2.cycle, r2.slot, r2.n, v.instanceId)
                        rec.records[LocalRepair.key(m)] =
                            PlacementRecord(0, "RELOCATED", r2.score, r2.count, label = "R4")
                        actions += obj {
                            this["action"] = str("MOVE")
                            this["instanceId"] = orNull(v.instanceId)
                            this["from"] = v.stored?.get("startEpoch") ?: num(v.startEpoch)
                            this["to"] = num(m.startEpoch)
                        }
                    } else
                        actions += obj {
                            this["action"] = str("SHED")
                            this["instanceId"] = orNull(v.instanceId)
                            this["reason"] = str("DISPLACED_BY_HIGHER_PRIORITY")
                        }
                }
                return res
            }
            before.restore(s)
        }
        return RadiusResult(4, actions)
    }
}
