package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.lifedirector.PageValues.member
import com.lifemax.rpg.core.lifedirector.PageValues.number
import com.lifemax.rpg.core.lifedirector.PageValues.text
import com.lifemax.rpg.core.lifedirector.PageValues.truthy
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/**
 * A stored task snapshot the native port cannot read as the engine writes one (a member missing or
 * of another type): the page would read it with JavaScript's coercions, which only snapshots the
 * engine itself wrote are known to meet (LDB-2).
 */
class UnreadableSource(message: String) : IllegalStateException(message)

/**
 * LDB-2 `makeTask` (`engine.js:777-785`) on a stored snapshot (a session's `source`, a residual
 * task, a re-placed task): the task's members are its defaults, then the snapshot's, its lists
 * copied. The merged members are kept as stored ([members]) so the task's own snapshot (LDO-8
 * `taskSnapshot`) is taken from them; [task] reads them as a candidate task.
 */
class SourceTask(val members: LinkedHashMap<String, JsonValue>) {
    /** The member [key] as stored (absent: null). */
    operator fun get(key: String): JsonValue? = members[key]

    /** Sets [key] (null removes it, as an undefined member). */
    operator fun set(key: String, value: JsonValue?) {
        if (value == null) members.remove(key) else members[key] = value
    }

    /** LDO-8 `taskSnapshot`: the snapshot's members the task has, in the snapshot's order. */
    fun snapshot(): JsonValue =
        ScheduleJson.clone(
            JsonObject(
                LinkedHashMap<String, JsonValue>().also { o ->
                    for (k in SNAPSHOT_KEYS) members[k]?.let { o[k] = it }
                }
            )
        )

    /** The task as placement reads it (LDC); the stored members must be of the engine's types. */
    fun task(): CandidateTask {
        fun need(k: String) = members[k] ?: throw UnreadableSource("no $k")
        fun str(k: String) =
            (need(k) as? JsonString)?.value ?: throw UnreadableSource("$k not text")
        fun optStr(k: String) =
            when (val v = members[k]) {
                null,
                JsonNull -> null
                is JsonString -> v.value
                else -> throw UnreadableSource("$k not text")
            }
        fun strings(k: String) =
            (need(k) as? JsonArray)?.items?.mapTo(ArrayList()) { text(it) }
                ?: throw UnreadableSource("$k not a list")
        fun num(k: String) = number(need(k))
        val coverable = members["coverable"]
        return CandidateTask(
            id = str("id"),
            kind =
                TaskKind.entries.firstOrNull { it.name == str("kind") }
                    ?: throw UnreadableSource("kind"),
            title = need("title"),
            templateId = str("templateId"),
            tag = need("tag"),
            goalId = optStr("goalId"),
            goalTitle = optStr("goalTitle"),
            linkedGoalIds = strings("linkedGoalIds"),
            linkedQuestRequirementIds =
                (need("linkedQuestRequirementIds") as? JsonArray)?.items?.mapTo(ArrayList(), ::link)
                    ?: throw UnreadableSource("links"),
            dependsOn = strings("dependsOn"),
            minLagMin = num("minLagMin"),
            exclusive = truthy(members["exclusive"]),
            requiresPrior = prior(members["requiresPrior"]),
            blockedReason = optStr("blockedReason"),
            priority = num("priority"),
            questWeight = num("questWeight"),
            tier = num("tier").toInt(),
            tierReason = str("tierReason"),
            dOpt = num("dOpt"),
            dMin = num("dMin"),
            dMax = number(members["dMax"]),
            physical = need("IP"),
            cognitive = need("IC"),
            spacingKey = str("spacingKey"),
            spacingMinH = members["spacingMinH"],
            spacingIdealH = members["spacingIdealH"],
            maxPerBwc = members["maxPerBwc"] ?: JsonNull,
            avoidBeforeSleepMin = members["avoidBeforeSleepMin"] ?: JsonNull,
            phases = need("phases"),
            preferRestDay = (members["preferRestDay"] as? JsonBool)?.value,
            windowStart = number(members["windowStart"]),
            windowEnd = number(members["windowEnd"]),
            deadlineEpoch = members["deadlineEpoch"] ?: JsonNull,
            week = number(members["week"]),
            sessionIndex = number(members["sessionIndex"]),
            goalStatus = null,
            questId = null,
            hasCoverable = coverable != null,
            coverable = cover(coverable),
        )
    }

    /** LDE-2 `prepTask` on the stored members. */
    fun prepare(s: PlanState, goals: Map<String, GoalInfo>): PreparedTask =
        TaskPreparation.prepare(
            TaskDemand(
                members["dOpt"],
                members["dMin"],
                members["priority"],
                members["questWeight"],
                members["goalId"],
                members["deadlineEpoch"],
                members["IP"],
                members["IC"],
            ),
            goals,
            s.slots.startEpoch,
            s.slots.endEpoch,
            s.now,
        )

    /** The task on the plan, prepared on [s] and carrying its stored snapshot. */
    fun planTask(s: PlanState, goals: Map<String, GoalInfo>): PlanTask =
        PlanTask(task(), prepare(s, goals)).also { it.snapshot = snapshot() }

    companion object {
        /** `taskSnapshot`'s members, in its order (`engine.js:1111-1113`). */
        val SNAPSHOT_KEYS =
            listOf(
                "coverable",
                "id",
                "kind",
                "title",
                "templateId",
                "tag",
                "goalId",
                "goalTitle",
                "linkedGoalIds",
                "linkedQuestRequirementIds",
                "priority",
                "questWeight",
                "tier",
                "tierReason",
                "dOpt",
                "dMin",
                "dMax",
                "IP",
                "IC",
                "spacingKey",
                "spacingMinH",
                "spacingIdealH",
                "maxPerBwc",
                "avoidBeforeSleepMin",
                "phases",
                "preferRestDay",
                "windowStart",
                "windowEnd",
                "deadlineEpoch",
                "week",
                "sessionIndex",
                "dependsOn",
                "minLagMin",
                "exclusive",
                "requiresPrior",
            )

        /**
         * `makeTask(o)`: the defaults (a goal session with no links, prerequisites or prior volume,
         * no lag, not exclusive, priority 3, quest weight 0), then [o]'s members, its lists copied
         * (none: an empty list) and not placed.
         */
        fun make(o: JsonValue?): SourceTask {
            val m = LinkedHashMap<String, JsonValue>()
            m["kind"] = JsonString("GOAL")
            m["linkedGoalIds"] = JsonArray.EMPTY
            m["linkedQuestRequirementIds"] = JsonArray.EMPTY
            m["dependsOn"] = JsonArray.EMPTY
            m["minLagMin"] = JsonNumber(0.0)
            m["exclusive"] = JsonBool.FALSE
            m["requiresPrior"] = JsonNull
            m["blockedReason"] = JsonNull
            m["origStartEpoch"] = JsonNull
            m["isPlaced"] = JsonBool.FALSE
            m["priority"] = JsonNumber(DEFAULT_PRIORITY)
            m["questWeight"] = JsonNumber(0.0)
            (o as? JsonObject)?.members?.let(m::putAll)
                ?: throw UnreadableSource("the snapshot is not an object")
            for (k in listOf("linkedGoalIds", "linkedQuestRequirementIds", "dependsOn")) {
                val v = member(o, k)
                m[k] = if (truthy(v)) v!! else JsonArray.EMPTY
            }
            m["isPlaced"] = JsonBool.FALSE
            return SourceTask(m)
        }

        private const val DEFAULT_PRIORITY = 3.0

        private fun link(v: JsonValue): QuestLink =
            QuestLink(
                text(member(v, "questId")),
                text(member(v, "reqId")),
                text(member(v, "questTitle")),
                member(v, "deadlineEpoch") ?: JsonNull,
            )

        private fun prior(v: JsonValue?): PriorVolume? =
            if (v == null || v == JsonNull) null
            else
                PriorVolume(
                    (member(v, "templateIds") as? JsonArray)?.items?.map { text(it) }
                        ?: throw UnreadableSource("requiresPrior"),
                    member(v, "count"),
                )

        private fun cover(v: JsonValue?): DailyCover? =
            if (v == null || v == JsonNull) null
            else
                DailyCover(
                    member(v, "reqTag") ?: JsonNull,
                    number(member(v, "start")),
                    number(member(v, "end")),
                    link(member(v, "link") ?: throw UnreadableSource("coverable link")),
                    truthy(member(v, "exactOnly")),
                )
    }
}
