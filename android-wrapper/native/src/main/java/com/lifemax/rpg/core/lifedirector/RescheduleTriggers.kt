package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.lifedirector.PageValues.member
import com.lifemax.rpg.core.lifedirector.PageValues.number
import com.lifemax.rpg.core.lifedirector.PageValues.text
import com.lifemax.rpg.core.lifedirector.PageValues.truthy
import com.lifemax.rpg.core.lifedirector.ScheduleJson.num
import com.lifemax.rpg.core.lifedirector.ScheduleJson.obj
import com.lifemax.rpg.core.lifedirector.ScheduleJson.str
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/** A stored session the reschedule may change in place (the page mutates its copies). */
class StoredSession(val m: LinkedHashMap<String, JsonValue>) {
    operator fun get(key: String): JsonValue? = m[key]

    fun json(): JsonObject = JsonObject(LinkedHashMap(m))

    /** `Object.assign({}, this, extra)`: a copy with [extra] set (a null value is undefined). */
    fun with(vararg extra: Pair<String, JsonValue?>): JsonObject {
        val c = LinkedHashMap(m)
        for ((k, v) in extra) if (v == null) c.remove(k) else c[k] = v
        return JsonObject(c)
    }

    companion object {
        fun of(v: JsonValue): StoredSession =
            StoredSession(
                LinkedHashMap((v as? JsonObject)?.members ?: throw PageTypeError("not an object"))
            )
    }
}

/**
 * LDM-1 to LDM-7: what a trigger asks of the reschedule (`engine.js:1621-1696`), worked out on the
 * current plan's sessions ([planned], copies) and history: the sessions kept for the hold, the
 * moved one, the tasks to place again, the change record's first actions, the tasks gone from the
 * plan, the input as changed (a commitment or blackout added) and what a disruption sheds.
 */
class TriggerPlan(val input: JsonObject, val nowValue: JsonValue?, val trigger: JsonValue?) {
    val now: Double = number(nowValue)
    val type: JsonValue? = member(trigger, "type")
    var keep = ArrayList<StoredSession>()
    val history = ArrayList<JsonValue>()
    val toPlace = ArrayList<Replacement>()
    val actions = ArrayList<JsonValue>()
    val rejected = ArrayList<JsonValue>()
    val gone = LinkedHashSet<JsonValue?>()
    var moved: JsonObject? = null
    var block: JsonValue? = null
    var shedUntil: Double? = null

    /** `retire`: off the plan into history, in [state] at the clock, its task gone. */
    fun retire(inst: StoredSession, state: String, vararg extra: Pair<String, JsonValue?>) {
        gone += inst["taskId"]
        keep = ArrayList(keep.filter { it !== inst })
        history += inst.with("lifecycleState" to str(state), "resolvedAtEpoch" to nowValue, *extra)
    }

    fun action(vararg members: Pair<String, JsonValue?>) {
        actions += obj { for ((k, v) in members) this[k] = v }
    }
}

/** LDM-1 to LDM-7: the triggers; an unknown one, or a session not on the plan, throws (LDM-1). */
object RescheduleTriggers {
    private const val CRITICAL_REACH = 86400.0 + 3600.0
    private const val CRITICAL = 4
    private const val URGENT = 3
    private val MOVED_SUMMARY =
        "You moved this here and it is pinned; Life Director will not move it automatically."

    fun plan(
        input: JsonObject,
        trigger: JsonValue?,
        planned: List<StoredSession>,
        history: List<JsonValue>,
    ): TriggerPlan {
        val p = TriggerPlan(input, input["currentTimeEpoch"], trigger)
        p.keep = ArrayList(planned)
        p.history += history
        val type = (p.type as? JsonString)?.value
        when (type) {
            "MISSED",
            "SKIPPED" -> missed(p, type == "MISSED")
            "RESIDUAL" ->
                p.toPlace +=
                    Replacement(
                        member(trigger, "task") ?: JsonNull,
                        "RESIDUAL",
                        member(trigger, "parentInstanceId"),
                        p.nowValue,
                    )
            "MOVE" -> move(p)
            "NEW_COMMITMENT",
            "BLOCK_HOURS",
            "BLACKOUT_DAY",
            "SICK" -> disrupt(p, type)
            "QUEST_CHANGED" -> quests(p)
            "TIME_CHANGED" -> timeChanged(p, planned)
            else -> throw EngineRefusal("Unknown trigger " + text(p.type))
        }
        return p
    }

    private fun find(p: TriggerPlan): StoredSession {
        val id = member(p.trigger, "instanceId")
        return p.keep.firstOrNull { PageValues.same(it["instanceId"], id) }
            ?: throw EngineRefusal("Instance not found: " + text(id))
    }

    /**
     * LDM-2: a missed or skipped session leaves; an urgent one is placed again (a skip: not that
     * day).
     */
    private fun missed(p: TriggerPlan, miss: Boolean) {
        val inst = find(p)
        p.retire(inst, if (miss) "MISSED_UNEXPLAINED" else "SKIPPED_VOLUNTARY")
        p.action("action" to str(if (miss) "MISS" else "SKIP"), "instanceId" to inst["instanceId"])
        if (
            PageValues.atLeast(inst["tier"], JsonNumber(URGENT.toDouble())) &&
                truthy(inst["source"])
        )
            p.toPlace +=
                Replacement(
                    inst["source"]!!,
                    "REPLACEMENT",
                    inst["instanceId"],
                    p.nowValue,
                    if (miss) null else inst["bwcId"] ?: JsonNull,
                )
    }

    /** LDM-3: the moved session, pinned at its new start with its length and a new explanation. */
    private fun move(p: TriggerPlan) {
        val inst = find(p)
        val dur = number(inst["endEpoch"]) - number(inst["startEpoch"])
        val to = member(p.trigger, "newStartEpoch")
        val moves = number(PageValues.orZero(inst["moves"])) + 1
        p.moved =
            inst.with(
                "startEpoch" to to,
                "endEpoch" to num(number(to) + dur),
                "isUserLocked" to JsonBool.TRUE,
                "rescheduledAdvance" to JsonBool.TRUE,
                "moves" to num(moves),
                "explanation" to
                    obj {
                        this["tier"] = inst["tier"]
                        this["summary"] = str(MOVED_SUMMARY)
                        this["factors"] = JsonArray.EMPTY
                        this["locked"] = JsonBool.TRUE
                    },
            )
        p.keep = ArrayList(p.keep.filter { it !== inst })
        p.action(
            "action" to str("MOVE"),
            "instanceId" to inst["instanceId"],
            "from" to inst["startEpoch"],
            "to" to to,
        )
    }

    /**
     * LDM-5, LDM-6: the commitment or blackout joins the input; what it displaces is handled after
     * the hold.
     */
    private fun disrupt(p: TriggerPlan, type: String) {
        val c = member(p.trigger, "commitment")
        val m = p.input.members as LinkedHashMap<String, JsonValue>
        if (type == "NEW_COMMITMENT") {
            val list = m["fixedCommitments"].takeIf(::truthy) ?: JsonArray.EMPTY
            m["fixedCommitments"] = JsonArray(items(list) + (c ?: JsonNull))
        } else {
            val av =
                LinkedHashMap(
                    (m["playerAvailability"].takeIf(::truthy) as? JsonObject)?.members ?: emptyMap()
                )
            val blackouts = av["blackouts"].takeIf(::truthy) ?: JsonArray.EMPTY
            val b = LinkedHashMap<String, JsonValue>()
            b["kind"] = str(if (type == "SICK") "SICK" else "BLACKOUT")
            (c as? JsonObject)?.members?.let(b::putAll)
            av["blackouts"] = JsonArray(items(blackouts) + JsonObject(b))
            m["playerAvailability"] = JsonObject(av)
        }
        p.block = c
        if (type == "BLOCK_HOURS")
            p.shedUntil = dayEndAfter(p.input, number(member(c, "startEpoch")))
        if (type == "BLACKOUT_DAY" || type == "SICK") p.shedUntil = Double.POSITIVE_INFINITY
    }

    private fun items(v: JsonValue): List<JsonValue> =
        (v as? JsonArray)?.items ?: throw PageTypeError("push is not a function")

    /** LDT-18 `dayEndAfter` in the input's zone (its own, the availability's, else UTC). */
    private fun dayEndAfter(input: JsonObject, epoch: Double): Double {
        val tz =
            PageValues.or(
                PageValues.or(input["timezone"], member(input["playerAvailability"], "timezone")),
                "UTC",
            )
        return PlanHorizon.nextDayStart(
            epoch,
            ZoneNames.resolve(text(tz)) ?: throw PageRangeError("Invalid time zone"),
        )
    }

    /**
     * LDM-7 `QUEST_CHANGED`: each future, unpinned session with quest links keeps the links whose
     * quest is still active, takes each one's new deadline (not a daily quest's); one left with no
     * link and no goal is cancelled; one now ending after its earliest deadline is excused and
     * placed again (tier 4 when that deadline is at most 25 hours away); otherwise it is relinked.
     */
    private fun quests(p: TriggerPlan) {
        val qs = LinkedHashMap<String, PlayerQuest>()
        for (raw in
            PlanState.iterate(
                PageValues.or(p.input["activeQuests"], JsonArray.EMPTY),
                "activeQuests",
            )) {
            val q = PlayerQuests.of(raw)
            qs[q.id] = q
        }
        for (inst in p.keep.toList()) {
            val links = inst["linkedQuestRequirementIds"] as? JsonArray ?: continue
            if (
                truthy(inst["isUserLocked"]) ||
                    number(inst["startEpoch"]) <= p.now ||
                    links.items.isEmpty()
            )
                continue
            var changed = false
            val kept = ArrayList<JsonObject>()
            for (l in links.items) {
                val q = qs[text(member(l, "questId"))]
                if (q != null && q.active) kept += l as JsonObject else changed = true
            }
            val relinked =
                kept.map { l ->
                    val q = qs.getValue(text(l["questId"]))
                    if (!PageValues.same(q.deadlineEpoch, l["deadlineEpoch"]) && !q.daily) {
                        changed = true
                        JsonObject(
                            LinkedHashMap(l.members).also { it["deadlineEpoch"] = q.deadlineEpoch }
                        )
                    } else l
                }
            if (!changed) continue
            if (
                relinked.isEmpty() &&
                    PlanState.iterate(inst["linkedGoalIds"], "linkedGoalIds").isEmpty()
            ) {
                p.retire(inst, "CANCELLED_BY_QUEST")
                p.action(
                    "action" to str("DELETE"),
                    "instanceId" to inst["instanceId"],
                    "reason" to str("QUEST_CLOSED"),
                )
                continue
            }
            val linkList = JsonArray(relinked)
            inst.m["linkedQuestRequirementIds"] = linkList
            val dl =
                relinked.fold(Double.POSITIVE_INFINITY) { m, l ->
                    if (truthy(l["deadlineEpoch"])) minOf(m, number(l["deadlineEpoch"])) else m
                }
            val src = LinkedHashMap((inst["source"] as? JsonObject)?.members ?: emptyMap())
            src["linkedQuestRequirementIds"] = linkList
            src["deadlineEpoch"] = if (dl == Double.POSITIVE_INFINITY) JsonNull else num(dl)
            inst.m["source"] = JsonObject(src)
            if (dl != Double.POSITIVE_INFINITY && number(inst["endEpoch"]) > dl) {
                p.retire(inst, "EXCUSED_DISPLACED", "excuseReason" to str("QUEST_DEADLINE_CHANGED"))
                val tier =
                    if (dl - p.now <= CRITICAL_REACH) CRITICAL.toDouble()
                    else maxOf(URGENT.toDouble(), number(inst["tier"]))
                val replaced = LinkedHashMap(src)
                replaced["tier"] = num(tier)
                replaced["tierReason"] =
                    if (tier == CRITICAL.toDouble()) str("CRITICAL_DEADLINE")
                    else src["tierReason"] ?: JsonNull
                if (src["tierReason"] == null && tier != CRITICAL.toDouble())
                    replaced.remove("tierReason")
                val windowEnd = src["windowEnd"]?.takeIf(::truthy)?.let(::number) ?: dl
                replaced["windowEnd"] = num(minOf(windowEnd, dl))
                p.toPlace +=
                    Replacement(JsonObject(replaced), "REPLACEMENT", inst["instanceId"], p.nowValue)
                p.action(
                    "action" to str("EXCUSE"),
                    "instanceId" to inst["instanceId"],
                    "reason" to str("QUEST_DEADLINE_CHANGED"),
                )
            } else p.action("action" to str("RELINK"), "instanceId" to inst["instanceId"])
        }
    }

    /**
     * LDM-7 `TIME_CHANGED`: the tasks a fresh plan of the changed input places that no session has.
     */
    private fun timeChanged(p: TriggerPlan, planned: List<StoredSession>) {
        val existing = planned.map { it["taskId"] }.toSet()
        val proposed = PlanSchedule.generate(p.input) as JsonObject
        if (proposed["status"] == JsonString("INVARIANT_VIOLATION")) return
        for (i in (proposed["scheduledInstances"] as JsonArray).items) {
            val src = member(i, "source")
            if (truthy(src) && member(i, "taskId") !in existing)
                p.toPlace += Replacement(src!!, "TIME_FREED", null, p.nowValue)
        }
    }
}
