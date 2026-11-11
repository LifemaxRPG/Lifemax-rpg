package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.lifedirector.LedgerValues.add
import com.lifemax.rpg.core.lifedirector.LedgerValues.concatKeepLast
import com.lifemax.rpg.core.lifedirector.LedgerValues.field
import com.lifemax.rpg.core.lifedirector.LedgerValues.includes
import com.lifemax.rpg.core.lifedirector.LedgerValues.isText
import com.lifemax.rpg.core.lifedirector.LedgerValues.items
import com.lifemax.rpg.core.lifedirector.LedgerValues.lengthOf
import com.lifemax.rpg.core.lifedirector.LedgerValues.num
import com.lifemax.rpg.core.lifedirector.LedgerValues.put
import com.lifemax.rpg.core.lifedirector.LedgerValues.str
import com.lifemax.rpg.core.lifedirector.PageValues.atLeast
import com.lifemax.rpg.core.lifedirector.PageValues.number
import com.lifemax.rpg.core.lifedirector.PageValues.or
import com.lifemax.rpg.core.lifedirector.PageValues.same
import com.lifemax.rpg.core.lifedirector.PageValues.text
import com.lifemax.rpg.core.lifedirector.PageValues.truthy
import com.lifemax.rpg.core.lifedirector.ScheduleJson.clone
import com.lifemax.rpg.core.lifedirector.ScheduleJson.obj
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/**
 * What [QuestProgress.reconcile] gave: the quests and goals after the event, the bounties earned,
 * the planned sessions with completed quests' links removed ([pruned]: an unchanged session is the
 * very value given), the ids of sessions to drop, and each touched quest's progress before and
 * after.
 */
class Reconciled(
    val quests: List<JsonValue>,
    val goals: List<JsonValue>,
    val bounties: List<JsonValue>,
    val pruned: List<JsonValue>,
    val removedIds: List<JsonValue>,
    val deltas: List<JsonValue>,
) {
    fun json(): JsonObject = obj {
        this["updatedQuests"] = JsonArray(quests)
        this["updatedGoals"] = JsonArray(goals)
        this["questBountyRewards"] = JsonArray(bounties)
        this["prunedScheduleInstances"] = JsonArray(pruned)
        this["removedInstanceIds"] = JsonArray(removedIds)
        this["questDeltas"] = JsonArray(deltas)
    }
}

/**
 * LED-14 to LED-16 `reconcileQuestProgress` (`engine.js:1987-2045`): one completion event counted
 * once into each active quest and each goal it serves (the event id is remembered in
 * `appliedEventIds`, the last 500), and the planned sessions of quests it completed released.
 */
object QuestProgress {
    /** The ids a quest or goal remembers. */
    const val REMEMBERED = 500
    private const val MINUTES_PER_HOUR = 60.0

    fun reconcile(
        event: JsonValue?,
        quests: JsonValue?,
        goals: JsonValue?,
        future: JsonValue?,
        optsIn: JsonValue?,
    ): Reconciled {
        val opts = or(optsIn, JsonObject.EMPTY)!!
        val satisfied =
            atLeast(
                field(event, "completionRatio"),
                or(field(opts, "satisfiedRatio"), JsonNumber(CompletionLedger.SATISFIED_RATIO)),
            )
        val eventId = field(event, "eventId")
        val updatedQuests = ArrayList<JsonValue>()
        val bounties = ArrayList<JsonValue>()
        val deltas = ArrayList<JsonValue>()
        val list = if (truthy(quests)) quests else JsonArray.EMPTY
        if (list is JsonString) throw LedgerRefusal("quests as text")
        for (raw in LedgerValues.iterate(list)) {
            if (raw != JsonNull && raw !is JsonObject)
                throw LedgerRefusal("a quest that is not an object")
            val q = PlayerQuests.of(raw)
            val out = clone(raw)
            if (!q.active || includes(q.appliedEventIds, eventId)) {
                updatedQuests += out
                continue
            }
            val progress =
                questOutcome(q, raw, out as JsonObject, event, satisfied, opts, bounties, deltas)
            updatedQuests += progress
        }
        val updatedGoals =
            (if (truthy(goals)) items(goals) else emptyList()).map { goal(it, event, satisfied) }
        return prune(event, updatedQuests, updatedGoals, bounties, deltas, future)
    }

    /** LED-14: one active quest the event has not counted yet. */
    private fun questOutcome(
        q: PlayerQuest,
        raw: JsonValue,
        cloned: JsonObject,
        event: JsonValue?,
        satisfied: Boolean,
        opts: JsonValue,
        bounties: MutableList<JsonValue>,
        deltas: MutableList<JsonValue>,
    ): JsonValue {
        var touched = false
        val duration = field(event, "actualDurationMinutes")
        val reqs =
            q.requirements.map { r ->
                val linked =
                    items(field(event, "linkedQuestRequirementIds")).any { x ->
                        same(field(x, "questId"), JsonString(q.id)) &&
                            same(field(x, "reqId"), JsonString(r.id))
                    }
                val matches =
                    linked ||
                        (!q.exclusive &&
                            ActivityTags.matches(r.tag, field(event, "activityTypeTag")))
                if (!matches) return@map r
                var count = r.currentCount
                var minutes = r.currentMinutes
                if (PageValues.truthy(r.targetMinutes)) {
                    minutes = minOf(r.targetMinutes, number(PageValues.plus(minutes, duration)))
                    touched = true
                }
                if (
                    PageValues.truthy(r.targetCount) &&
                        satisfied &&
                        atLeast(duration, or(r.minSessionMinutes, JsonNumber(0.0)))
                ) {
                    count = minOf(r.targetCount, count + 1)
                    touched = true
                }
                QuestRequirement(
                    r.id,
                    r.tag,
                    r.targetCount,
                    count,
                    r.targetMinutes,
                    minutes,
                    r.minSessionMinutes,
                    r.sessionMinutes,
                )
            }
        if (!touched) return cloned
        var out: JsonObject =
            put(
                cloned,
                "appliedEventIds",
                concatKeepLast(q.appliedEventIds, field(event, "eventId"), REMEMBERED),
            )
        val stored = field(raw, "requirements")
        if (stored is JsonArray && stored.items.isNotEmpty()) {
            val rewritten =
                stored.items.mapIndexed { i, r ->
                    val m = LedgerValues.ownMembers(r)
                    m["currentCount"] = num(reqs[i].currentCount)
                    m["currentMinutes"] = num(reqs[i].currentMinutes)
                    JsonObject(m)
                }
            out = put(out, "requirements", JsonArray(rewritten))
        } else {
            out = put(out, "progressCount", num(reqs[0].currentCount))
            out = put(out, "progressMinutes", num(reqs[0].currentMinutes))
        }
        val done =
            reqs.all {
                (!PageValues.truthy(it.targetCount) || it.currentCount >= it.targetCount) &&
                    (!PageValues.truthy(it.targetMinutes) || it.currentMinutes >= it.targetMinutes)
            }
        deltas += obj {
            this["questId"] = str(q.id)
            this["title"] = str(q.title)
            this["eventId"] = field(event, "eventId")
            this["before"] = pairs(q.requirements)
            this["after"] = pairs(reqs)
        }
        if (done && !q.daily) {
            out = put(out, "status", str(PlayerQuest.COMPLETED))
            out = put(out, "completedEventId", field(event, "eventId"))
            bounties += obj {
                this["bountyId"] = str(EngineHash.stableId("bounty", "QUEST_BOUNTY:" + q.id))
                this["questId"] = str(q.id)
                this["title"] = str(q.title)
                this["bounty"] = or(q.bounty, null) ?: JsonNull
            }
        } else if (done)
            out = put(out, "completedForDayKey", or(field(opts, "dayKey"), null) ?: JsonNull)
        return out
    }

    private fun pairs(reqs: List<QuestRequirement>): JsonValue =
        JsonArray(reqs.map { JsonArray(listOf(num(it.currentCount), num(it.currentMinutes))) })

    /** LED-15: one goal. */
    private fun goal(raw: JsonValue, event: JsonValue?, satisfied: Boolean): JsonValue {
        if (raw != JsonNull && raw !is JsonObject)
            throw LedgerRefusal("a goal that is not an object")
        var g: JsonValue = clone(raw)
        val id = text(or(field(g, "id"), field(g, "goalId")))
        val linked = includes(field(event, "linkedGoalIds"), JsonString(id))
        val tag = field(event, "activityTypeTag")
        val tagHit =
            !linked &&
                isText(field(event, "source"), "ADHOC") &&
                PlayerGoals.templates(PlayerGoals.of(raw)).any {
                    ActivityTags.matches(it.tag, tag) || ActivityTags.matches(tag, it.tag)
                }
        if (!(linked || tagHit)) return g
        val applied = or(field(g, "appliedEventIds"), JsonArray.EMPTY)!!
        g = put(g, "appliedEventIds", applied)
        val eventId = field(event, "eventId")
        if (includes(applied, eventId)) return g
        g = put(g, "appliedEventIds", concatKeepLast(applied, eventId, REMEMBERED))
        val duration = field(event, "actualDurationMinutes")
        g =
            put(
                g,
                "sessionsCompleted",
                add(or(field(g, "sessionsCompleted"), ZERO), if (satisfied) 1.0 else 0.0),
            )
        g = put(g, "minutesCompleted", add(or(field(g, "minutesCompleted"), ZERO), duration))
        val target = field(g, "target")
        if (truthy(target) && truthy(field(target, "unitsPerHour"))) {
            val gained = number(duration) * number(field(target, "unitsPerHour")) / MINUTES_PER_HOUR
            val sum = add(or(field(target, "completed"), ZERO), gained)
            val m = LedgerValues.ownMembers(target)
            m["completed"] = num(minOf(number(field(target, "units")), number(sum)))
            g = put(g, "target", JsonObject(m))
        }
        return g
    }

    private val ZERO = JsonNumber(0.0)

    /** LED-16: planned sessions of quests now completed lose those links, or leave the plan. */
    private fun prune(
        event: JsonValue?,
        quests: List<JsonValue>,
        goals: List<JsonValue>,
        bounties: List<JsonValue>,
        deltas: List<JsonValue>,
        future: JsonValue?,
    ): Reconciled {
        val completed =
            quests
                .filter { isText(field(it, "status"), PlayerQuest.COMPLETED) }
                .map { text(or(field(it, "id"), field(it, "questId"))) }
                .toSet()
        val pruned = ArrayList<JsonValue>()
        val removed = ArrayList<JsonValue>()
        val sessions = if (truthy(future)) LedgerValues.iterate(future) else emptyList()
        for (inst in sessions) {
            if (
                !isText(field(inst, "lifecycleState"), "PLANNED") ||
                    same(field(inst, "instanceId"), field(event, "instanceId"))
            ) {
                pruned += inst
                continue
            }
            val all = items(field(inst, "linkedQuestRequirementIds"))
            val links =
                all.filter { l ->
                    !(field(l, "questId").let { it is JsonString && it.value in completed })
                }
            if (links.size == all.size) {
                pruned += inst
                continue
            }
            val noGoals by lazy { !truthy(lengthOf(field(inst, "linkedGoalIds"))) }
            if (links.isEmpty() && noGoals && !truthy(field(inst, "isUserLocked"))) {
                removed += field(inst, "instanceId") ?: JsonNull
                continue
            }
            var c = put(clone(inst), "linkedQuestRequirementIds", JsonArray(links))
            if (links.isEmpty() && noGoals) c = put(c, "orphanWarning", JsonBool.TRUE)
            val source = field(c, "source")
            if (truthy(source) && source !is JsonArray)
                c = put(c, "source", put(source, "linkedQuestRequirementIds", JsonArray(links)))
            pruned += c
        }
        return Reconciled(quests, goals, bounties, pruned, removed, deltas)
    }
}
