package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.lifedirector.PageValues.member
import com.lifemax.rpg.core.lifedirector.PageValues.number
import com.lifemax.rpg.core.lifedirector.PageValues.text
import com.lifemax.rpg.core.lifedirector.PageValues.truthy
import com.lifemax.rpg.core.lifedirector.ScheduleJson.obj
import com.lifemax.rpg.core.lifedirector.ScheduleJson.str
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/**
 * What [Reschedule.run] gives, as `rescheduleActivity` returns it: the new plan (the current one
 * itself when a move is refused), the engine input (with any commitment or blackout added; the one
 * passed in when refused), the change record (null when refused) and the refusal.
 */
class RescheduleResult(
    val updatedSchedule: JsonValue,
    val engineInput: JsonValue?,
    val changeRecord: JsonValue?,
    val rejectedMove: JsonValue?,
)

/**
 * LDM, LDB, LDY, LDZ: `rescheduleActivity` (`engine.js:1619-1790`), the local repair of a plan
 * after one change: the trigger's own changes ([RescheduleTriggers]), the remaining sessions held
 * where they are ([HoldPreparation]), a move refused when the held plan cannot take it, the
 * sessions the change displaced (and those depending on them) let go, every task to place again
 * placed by radius ([RadiusPlacement]) in prerequisite order, and the plan and its change record
 * written ([RescheduleOutput]). Nothing is wired to a screen; the page stays the only planner.
 */
object Reschedule {
    private const val CASCADE_ROUNDS = 20
    private const val LOW_TIER = 2
    private const val DROPPED = " It was dropped for this week to keep your other plans stable."
    private val PLANNED_STATES = setOf("PLANNED", "IN_PROGRESS")

    fun run(request: JsonValue?): RescheduleResult {
        val t0 = System.nanoTime()
        val trigger = member(request, "trigger").takeIf(::truthy) ?: JsonObject.EMPTY
        val original = member(request, "engineInput")
        val input =
            (original?.takeIf { it != JsonNull }?.let(ScheduleJson::clone) as? JsonObject)?.let {
                JsonObject(LinkedHashMap(it.members))
            }
                ?: throw PageTypeError(
                    "Cannot read properties of undefined (reading 'currentTimeEpoch')"
                )
        val current =
            member(request, "currentOutput")
                ?: throw PageTypeError("Cannot read properties of undefined")
        val prevPlanned =
            items(member(current, "scheduledInstances"), "map").map {
                StoredSession.of(ScheduleJson.clone(it))
            }
        val history =
            (member(current, "historyInstances").takeIf(::truthy)?.let { items(it, "map") }
                    ?: emptyList())
                .map(ScheduleJson::clone)
        val p = RescheduleTriggers.plan(input, trigger, prevPlanned, history)
        val hold = p.keep.map { it.with("_hold" to JsonBool.TRUE) }.toMutableList()
        p.moved?.let { hold.add(0, it) }
        val probe = HoldPreparation.prepare(p.input, hold)
        val s = probe.state
        refusal(p, probe)?.let {
            return RescheduleResult(
                current,
                original,
                null,
                obj {
                    this["reason"] = str(it.first)
                    this["message"] = str(it.second)
                },
            )
        }
        val carry =
            p.keep.filter {
                number(it["endEpoch"]) <= s.slots.startEpoch &&
                    (it["lifecycleState"] as? JsonString)?.value in PLANNED_STATES
            }
        letGo(p, probe)
        cascade(p, s)
        val block = p.block
        if (p.type == JsonString("SICK") && truthy(block))
            for (tp in p.toPlace) tp.notBefore = member(block, "endEpoch")
        val rec = RescheduleRecords()
        val placed = placeAll(p, s, rec)
        return RescheduleOutput.write(
            RescheduleOutput.Parts(p, probe, current, prevPlanned, carry, rec, placed, t0)
        )
    }

    private fun items(v: JsonValue?, method: String): List<JsonValue> =
        (v as? JsonArray)?.items
            ?: throw PageTypeError("Cannot read properties of undefined (reading '$method')")

    /**
     * LDM-3, LDB-3: why the held plan cannot take the move (blocked time, outside the plan, short
     * downtime).
     */
    private fun refusal(p: TriggerPlan, probe: HeldPlan): Pair<String, String>? {
        val moved = p.moved ?: return null
        val s = probe.state
        val id = moved["instanceId"]
        if (probe.displaced.any { PageValues.same(it["instanceId"], id) })
            return "BLOCKED_TIME" to
                "That time overlaps sleep, work, a meal or another fixed block."
        val session =
            s.sessionById[id]
                ?: return "OUTSIDE_PLAN" to "That time is outside the current planning window."
        val c = s.cycles.firstOrNull { it.cycle.id == session.cycleId }
        if (
            c != null &&
                c.recoverySlots * PlanState.SLOT_MINUTES <
                    number(s.prefs["minDailyRecoveryMinutes"]) &&
                !c.cycle.truncatedStart
        )
            return "RECOVERY_PROTECTED" to
                "Moving it there would cut that day below your minimum downtime."
        return null
    }

    /**
     * LDB-4, LDM-6: each held session the hold let go is excused; it is placed again unless a
     * disruption sheds a low tier.
     */
    private fun letGo(p: TriggerPlan, probe: HeldPlan) {
        for (inst in probe.displaced) {
            p.gone += inst["taskId"]
            p.history +=
                JsonObject(
                    LinkedHashMap(inst.members).also {
                        it["lifecycleState"] = str("EXCUSED_DISPLACED")
                        p.nowValue?.let { v -> it["resolvedAtEpoch"] = v }
                            ?: it.remove("resolvedAtEpoch")
                        p.type?.let { v -> it["excuseReason"] = v } ?: it.remove("excuseReason")
                    }
                )
            p.action(
                "action" to str("EXCUSE"),
                "instanceId" to inst["instanceId"],
                "reason" to p.type,
            )
            val until = p.shedUntil
            val shed =
                until != null &&
                    PageValues.lessOrEqual(inst["tier"], ScheduleJson.num(LOW_TIER)) &&
                    number(inst["startEpoch"]) < until
            if (!shed && truthy(inst["source"]))
                p.toPlace +=
                    Replacement(
                        inst["source"]!!,
                        "REPLACEMENT",
                        inst["instanceId"],
                        inst["startEpoch"],
                    )
            else if (shed)
                p.action(
                    "action" to str("SHED"),
                    "instanceId" to inst["instanceId"],
                    "reason" to str("LOW_TIER_AFTER_DISRUPTION"),
                )
        }
    }

    /**
     * LDB-4: a movable session whose task depends on a task gone from the plan leaves too, and is
     * placed again after it.
     */
    private fun cascade(p: TriggerPlan, s: PlanState) {
        var changed = true
        var guard = 0
        while (changed && guard < CASCADE_ROUNDS) {
            changed = false
            for (x in s.sessions.toList()) {
                val st = x.stored
                if (HeldSessions.kept(x) || HeldSessions.locked(x)) continue
                val source = if (st != null) st["source"] else x.commitment?.task?.snapshot
                if (!truthy(source)) continue
                val deps = member(source, "dependsOn")
                val list = if (truthy(deps)) PlanState.iterate(deps, "dependsOn") else emptyList()
                if (list.none { it in p.gone }) continue
                PlanRemoval.remove(s, x)
                p.gone += x.taskId
                changed = true
                val stored = st ?: throw UnreadableSource("a placed session in the cascade")
                p.history +=
                    JsonObject(
                        LinkedHashMap(stored.members).also {
                            it["lifecycleState"] = str("EXCUSED_DISPLACED")
                            p.nowValue?.let { v -> it["resolvedAtEpoch"] = v }
                                ?: it.remove("resolvedAtEpoch")
                            it["excuseReason"] = str("PREREQUISITE_MOVED")
                        }
                    )
                p.action(
                    "action" to str("EXCUSE"),
                    "instanceId" to x.instanceId,
                    "reason" to str("PREREQUISITE_MOVED"),
                )
                p.toPlace +=
                    Replacement(source!!, "REPLACEMENT", x.instanceId, stored["startEpoch"])
            }
            guard++
        }
    }

    /** One task placed again: its id, its title and the radius used, for the change record. */
    class Placed(
        val taskId: String,
        val title: JsonValue?,
        val radius: Int,
        val origInstanceId: JsonValue?,
    )

    /**
     * LDY-1, LDY-5: each task to place again gets a new id first (so a dependent can follow its
     * prerequisite's replacement), then they are placed in prerequisite depth, then tier (highest
     * first), then id order: its window starts no earlier than the clock (a sick spell's end), ends
     * at the plan's end when it has none left, its prerequisites are its replacements' ids, and it
     * is placed by radius; one that does not fit is rejected.
     */
    private fun placeAll(p: TriggerPlan, s: PlanState, rec: RescheduleRecords): List<Placed> {
        val newId = HashMap<JsonValue, String>()
        p.toPlace.forEachIndexed { k, tp ->
            val base = (tp.origInstanceId?.takeIf(::truthy) ?: member(tp.source, "id"))
            tp.newId = EngineHash.stableId("t", text(base) + ":r:" + k + ":" + text(p.nowValue))
            member(tp.source, "id")?.takeIf(::truthy)?.let { newId[it] = tp.newId }
        }
        val ids = p.toPlace.map { member(it.source, "id") }.toSet()
        fun depth(tp: Replacement): Int {
            var d = 0
            var cur = listOf(tp)
            val seen = HashSet<JsonValue?>()
            while (cur.isNotEmpty() && d < CASCADE_ROUNDS) {
                val next = ArrayList<Replacement>()
                for (c in cur) {
                    val deps = member(c.source, "dependsOn")
                    for (dp in
                        if (truthy(deps)) PlanState.iterate(deps, "dependsOn")
                        else emptyList()) if (dp in ids && seen.add(dp))
                        p.toPlace.firstOrNull { member(it.source, "id") == dp }?.let(next::add)
                }
                if (next.isEmpty()) break
                d++
                cur = next
            }
            return d
        }
        val order =
            p.toPlace.sortedWith { a, b ->
                val d = depth(a) - depth(b)
                val ta = number(PageValues.orZero(member(a.source, "tier")))
                val tb = number(PageValues.orZero(member(b.source, "tier")))
                when {
                    d != 0 -> d
                    truthy(tb - ta) -> PageValues.byDifference(tb - ta)
                    else ->
                        HeldSessions.byId(
                            PageValues.or(member(a.source, "id"), ""),
                            PageValues.or(member(b.source, "id"), ""),
                        )
                }
            }
        val goals = GoalPlans.info(member(p.input, "goals"), p.now)
        val out = ArrayList<Placed>()
        for (tp in order) {
            val made = SourceTask.make(tp.source)
            made["id"] = JsonString(tp.newId)
            made["tierReason"] =
                if (tp.reason == "RESIDUAL") str("RESIDUAL")
                else member(tp.source, "tierReason")?.takeIf(::truthy) ?: str("REPLACEMENT")
            fun start() = made["windowStart"]?.takeIf(::truthy)?.let(::number) ?: p.now
            tp.notBefore?.takeIf(::truthy)?.let {
                made["windowStart"] = ScheduleJson.num(maxOf(start(), number(it)))
            }
            made["windowStart"] = ScheduleJson.num(maxOf(start(), p.now))
            val end = made["windowEnd"]
            if (!truthy(end) || number(end) <= number(made["windowStart"]))
                made["windowEnd"] = ScheduleJson.num(s.slots.endEpoch)
            made["dependsOn"] =
                JsonArray(
                    PlanState.iterate(made["dependsOn"], "dependsOn").map { dp ->
                        newId[dp]?.let(::JsonString) ?: dp
                    }
                )
            val t = made.planTask(s, goals)
            s.tasks = s.tasks + t
            val result = RadiusPlacement.place(s, t, tp, rec)
            out += Placed(t.id, made["title"], result.radius, tp.origInstanceId)
            if (!t.isPlaced) {
                val r = ScheduleJson.rejection(Rejections.of(s, t)) as JsonObject
                val text =
                    text(r["humanExplanation"]) +
                        if (result.radius == 4 && t.task.tier <= LOW_TIER) DROPPED else ""
                p.rejected +=
                    JsonObject(
                        LinkedHashMap(r.members).also { it["humanExplanation"] = JsonString(text) }
                    )
            }
            p.actions += result.actions
        }
        return out
    }
}
