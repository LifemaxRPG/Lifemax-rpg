package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.lifedirector.PageValues.member
import com.lifemax.rpg.core.lifedirector.PageValues.number
import com.lifemax.rpg.core.lifedirector.PageValues.or
import com.lifemax.rpg.core.lifedirector.PageValues.orZero
import com.lifemax.rpg.core.lifedirector.PageValues.present
import com.lifemax.rpg.core.lifedirector.PageValues.text
import com.lifemax.rpg.core.lifedirector.PageValues.truthy
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/**
 * LDQ-2: one requirement of a quest: sessions of [tag] ([targetCount] of them, or [targetMinutes]
 * in all), of which [currentCount] and [currentMinutes] are done. The counts are numbers (NaN for a
 * stored value that is not one); [tag] and the session minutes are as stored.
 */
class QuestRequirement(
    val id: String,
    val tag: JsonValue,
    val targetCount: Double,
    val currentCount: Double,
    val targetMinutes: Double,
    val currentMinutes: Double,
    val minSessionMinutes: JsonValue,
    val sessionMinutes: JsonValue,
)

/**
 * LDQ-1: a quest as the engine reads it. Members the engine keeps as stored are [JsonValue]s (the
 * status, the deadline, the scheduling mode and recurrence, the session minutes, the intensity, and
 * the ones it only carries: applied events, bounty, source, window start, host reference).
 */
class PlayerQuest(
    val id: String,
    val title: String,
    val tier: String,
    val status: JsonValue,
    val deadlineEpoch: JsonValue,
    val prerequisiteQuestIds: List<String>,
    val exclusive: Boolean,
    val schedulingMode: JsonValue,
    val recurrence: JsonValue,
    val minSessionMinutes: JsonValue,
    val sessionMinutes: JsonValue,
    val minIntensity: JsonValue,
    val appliedEventIds: JsonValue,
    val bounty: JsonValue,
    val source: JsonValue,
    val onlyDays: List<JsonValue>?,
    val windowStartEpoch: JsonValue,
    val hostRef: JsonValue,
    val requirements: List<QuestRequirement>,
) {
    val active: Boolean
        get() = status == JsonString(ACTIVE)

    val daily: Boolean
        get() = recurrence == JsonString(DAILY)

    companion object {
        const val ACTIVE = "ACTIVE"
        const val COMPLETED = "COMPLETED"
        const val DAILY = "DAILY"
        const val OPTIONAL = "OPTIONAL"
    }
}

/** LDQ-4: how well the plan covers one active quest. */
class QuestCoverageRow(
    val quest: PlayerQuest,
    val status: JsonValue,
    val scheduledSessions: Int,
    val remainingCount: Double,
    val covered: Boolean?,
)

/** Quests: LDQ-1 to LDQ-4. */
object PlayerQuests {
    private val TIERS =
        mapOf(
            "MAIN_STORY" to "MANDATORY",
            "MANDATORY" to "MANDATORY",
            "CORE" to "CORE",
            "SIDE" to PlayerQuest.OPTIONAL,
            "BOUNTY" to PlayerQuest.OPTIONAL,
            PlayerQuest.OPTIONAL to PlayerQuest.OPTIONAL,
        )

    /** LDQ-1 `normalizeQuest`: [raw] (a stored quest; `null` throws `TypeError`). */
    fun of(raw: JsonValue?): PlayerQuest {
        if (!present(raw)) throw PageTypeError("Cannot read properties of null")
        fun m(key: String) = member(raw, key)
        val stored = m("requirements")
        val requirements =
            if (stored is JsonArray && stored.items.isNotEmpty()) stored.items
            else listOf(defaultRequirement(raw))
        val prerequisites =
            when (val p = m("prerequisiteQuestIds")) {
                is JsonArray -> p.items.map { text(it) }
                else -> if (truthy(p)) throw PageTypeError("map is not a function") else emptyList()
            }
        val minSession = orZero(m("minSessionMinutes"))
        val session = orZero(m("sessionMinutes"))
        val onlyDays = (m("onlyDays") as? JsonArray)?.items?.takeIf { it.isNotEmpty() }
        return PlayerQuest(
            id = text(or(m("id"), m("questId"))),
            title = text(or(m("title"), "Quest")),
            tier = TIERS[text(or(m("tier"), m("questTier")))] ?: "CORE",
            status = or(m("status"), PlayerQuest.ACTIVE),
            deadlineEpoch = or(m("deadlineEpoch"), null) ?: JsonNull,
            prerequisiteQuestIds = prerequisites,
            exclusive = truthy(or(m("exclusive"), m("requiresDedicatedExclusiveTime"))),
            schedulingMode = or(m("schedulingMode"), "SESSIONS"),
            recurrence = or(m("recurrence"), null) ?: JsonNull,
            minSessionMinutes = minSession,
            sessionMinutes = session,
            minIntensity = orZero(m("minIntensity")),
            appliedEventIds = or(m("appliedEventIds"), JsonArray.EMPTY)!!,
            bounty = or(m("bounty"), null) ?: JsonNull,
            source = or(m("source"), "host"),
            onlyDays = onlyDays,
            windowStartEpoch = or(m("windowStartEpoch"), null) ?: JsonNull,
            hostRef = or(m("hostRef"), null) ?: JsonNull,
            requirements =
                requirements.mapIndexed { i, r -> requirement(r, i, minSession, session) },
        )
    }

    /** The one requirement a quest without a requirement list stands for. */
    private fun defaultRequirement(raw: JsonValue?): JsonValue {
        fun m(key: String) = member(raw, key)
        val out = linkedMapOf<String, JsonValue>("requirementId" to JsonString("r0"))
        or(m("tag"), m("activityTypeTag"))?.let { out["activityTypeTag"] = it }
        out["targetCount"] = orZero(or(m("requiredCount"), m("targetCount")))
        out["currentCount"] = orZero(or(m("progressCount"), m("currentCount")))
        out["targetMinutes"] = orZero(m("requiredMinutes"))
        out["currentMinutes"] = orZero(m("progressMinutes"))
        return JsonObject(out)
    }

    /** LDQ-2: requirement [i] of a quest whose own session minutes are [minSession], [session]. */
    private fun requirement(
        r: JsonValue,
        i: Int,
        minSession: JsonValue,
        session: JsonValue,
    ): QuestRequirement {
        if (!present(r)) throw PageTypeError("Cannot read properties of null")
        fun m(key: String) = member(r, key)
        fun count(key: String) = maxOf(0.0, number(orZero(m(key))))
        return QuestRequirement(
            id = text(or(or(m("requirementId"), m("id")), "r$i")),
            tag = or(or(m("activityTypeTag"), m("tag")), "general"),
            targetCount = count("targetCount"),
            currentCount = count("currentCount"),
            targetMinutes = count("targetMinutes"),
            currentMinutes = count("currentMinutes"),
            minSessionMinutes = orZero(or(m("minSessionMinutes"), minSession)),
            sessionMinutes = orZero(or(m("sessionMinutes"), session)),
        )
    }

    /**
     * LDQ-3 `findQuestCycles`: the quests that sit on a cycle of prerequisites (a strongly
     * connected group of two or more, or one that names itself), found by Tarjan's method from each
     * quest in id order, in the order the groups close. Prerequisites that name no quest of
     * [quests] are ignored; with two quests of the same id, the later one's prerequisites count.
     */
    fun cycles(quests: List<PlayerQuest>): LinkedHashSet<String> {
        val ids = quests.map { it.id }
        val known = ids.toSet()
        val next = HashMap<String, List<String>>()
        for (q in quests) next[q.id] = q.prerequisiteQuestIds.filter { it in known }
        val order = HashMap<String, Int>()
        val low = HashMap<String, Int>()
        val onStack = HashSet<String>()
        val stack = ArrayList<String>()
        val bad = LinkedHashSet<String>()
        fun visit(v: String) {
            order[v] = order.size
            low[v] = order.getValue(v)
            stack += v
            onStack += v
            for (w in next.getValue(v)) {
                if (w !in order) {
                    visit(w)
                    low[v] = minOf(low.getValue(v), low.getValue(w))
                } else if (w in onStack) low[v] = minOf(low.getValue(v), order.getValue(w))
            }
            if (low[v] == order[v]) {
                val group = ArrayList<String>()
                do {
                    val w = stack.removeAt(stack.lastIndex)
                    onStack -= w
                    group += w
                } while (w != v)
                if (group.size > 1 || v in next.getValue(v)) bad += group
            }
        }
        for (v in ids.sorted()) if (v !in order) visit(v)
        return bad
    }

    /**
     * LDQ-4 `questCoverage`: for each active quest, the planned sessions carrying one of its
     * requirements ([plannedQuestIds]: each session's quest ids), the count still needed, and
     * whether that is covered: null for a passive quest, any session for a daily one, enough
     * sessions otherwise. [status] is the planner's status of the quest when truthy.
     */
    fun coverage(
        quests: List<PlayerQuest>,
        plannedQuestIds: List<List<JsonValue?>>,
        status: (String) -> JsonValue?,
    ): List<QuestCoverageRow> =
        quests
            .filter { it.active }
            .map { q ->
                val sessions =
                    plannedQuestIds.count { ids ->
                        ids.any { PageValues.same(it, JsonString(q.id)) }
                    }
                val need =
                    q.requirements.fold(0.0) { n, r ->
                        n + maxOf(0.0, r.targetCount - r.currentCount)
                    }
                QuestCoverageRow(
                    q,
                    or(status(q.id), q.status)!!,
                    sessions,
                    need,
                    when {
                        q.schedulingMode == JsonString("NONE") -> null
                        q.daily -> sessions > 0
                        else -> sessions.toDouble() >= need
                    },
                )
            }
}
