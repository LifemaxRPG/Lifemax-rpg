package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.lifedirector.PageValues.member
import com.lifemax.rpg.core.lifedirector.PageValues.number
import com.lifemax.rpg.core.lifedirector.PageValues.or
import com.lifemax.rpg.core.lifedirector.PageValues.orZero
import com.lifemax.rpg.core.lifedirector.PageValues.round
import com.lifemax.rpg.core.lifedirector.PageValues.same
import com.lifemax.rpg.core.lifedirector.PageValues.text
import com.lifemax.rpg.core.lifedirector.PageValues.truthy
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/**
 * LDC-2 to LDC-10 `compileCandidates`: the sessions the planner will try to place. Goal sessions
 * first, week by week ([GoalSessions]); then each active quest's requirements, window by window,
 * attached to compatible goal sessions where it can and given sessions of its own where it cannot
 * ([QuestDemand]); then the prerequisite links between quests' sessions. Inputs are what the
 * engine's caller passes: the plans of LDD-8, the stored quests, the plan's weeks and days, the
 * plan's clock, the completed sessions of the last weeks ([history]), the sessions the plan keeps
 * ([kept]) and the instance ids already used ([used]).
 */
object Candidates {
    /** A quest's sessions are due inside one window: a day of a daily quest, or the whole plan. */
    internal class Window(
        val start: Double,
        val end: Double,
        val key: String,
        val isToday: Boolean,
    )

    private const val DAY = 86400.0
    private const val MIN_LAG_MINUTES = 15.0

    fun compile(
        plans: List<SessionPlan>,
        rawQuests: JsonValue?,
        weeks: List<PlanWeek>,
        now: Double,
        horizon: List<PlanDay>,
        history: List<JsonValue>,
        kept: List<JsonValue>,
        used: Set<String>?,
    ): CandidateSet {
        val weekOf = { epoch: JsonValue? ->
            val e = number(epoch)
            weeks.indexOfFirst { e >= it.startEpoch && e < it.endEpoch }
        }
        val tasks = ArrayList<CandidateTask>()
        GoalSessions(weeks, now, used, history + kept, weekOf).addTo(tasks, plans)
        val questItems =
            (rawQuests as? JsonArray)?.items
                ?: if (truthy(rawQuests)) throw PageTypeError("map is not a function")
                else emptyList()
        val quests = questItems.map(PlayerQuests::of).sortedWith { a, b -> a.id.compareTo(b.id) }
        val cycles = PlayerQuests.cycles(quests)
        val byId = quests.associateBy { it.id }
        val status = linkedMapOf<String, JsonValue>()
        val passive = ArrayList<PassiveReminder>()
        val rejected = ArrayList<QuestRejection>()
        val demand = QuestDemand(tasks, weeks, now, horizon, kept, used, weekOf)
        for (q in quests) {
            val verdict =
                when {
                    !q.active -> q.status
                    q.id in cycles -> {
                        rejected += QuestRejection(q.id, CYCLE, q.title + CYCLE_TEXT)
                        JsonString(CYCLE)
                    }
                    q.prerequisiteQuestIds.any { blocks(byId[it]) } -> {
                        rejected += QuestRejection(q.id, BLOCKED, q.title + BLOCKED_TEXT)
                        JsonString(BLOCKED)
                    }
                    q.schedulingMode == JsonString("NONE") -> {
                        passive += PassiveReminder(q.id, q.title, PASSIVE_ANCHOR)
                        JsonString("PASSIVE")
                    }
                    truthy(q.deadlineEpoch) &&
                        PageValues.lessOrEqual(q.deadlineEpoch, JsonNumber(now)) ->
                        JsonString("DEADLINE_PASSED")
                    else -> null
                }
            if (verdict != null) {
                status[q.id] = verdict
                continue
            }
            status[q.id] = JsonString(SCHEDULING)
            demand.add(q)
        }
        linkPrerequisites(quests, byId, status, tasks)
        return CandidateSet(
            tasks.sortedWith { a, b -> a.id.compareTo(b.id) },
            passive,
            rejected,
            status,
            quests,
        )
    }

    /**
     * LDC-4: a prerequisite that keeps a quest waiting: known, and neither completed nor active.
     */
    private fun blocks(p: PlayerQuest?): Boolean =
        p != null && p.status != JsonString(PlayerQuest.COMPLETED) && !p.active

    /**
     * LDC-10: every session of a scheduling quest depends on every session of each prerequisite
     * that is known and not completed, at least 15 minutes after it; with none while the
     * prerequisite still needs sessions, it is blocked.
     */
    private fun linkPrerequisites(
        quests: List<PlayerQuest>,
        byId: Map<String, PlayerQuest>,
        status: Map<String, JsonValue>,
        tasks: List<CandidateTask>,
    ) {
        for (q in quests) {
            if (status[q.id] != JsonString(SCHEDULING)) continue
            for (pid in q.prerequisiteQuestIds) {
                val p = byId[pid] ?: continue
                if (p.status == JsonString(PlayerQuest.COMPLETED)) continue
                val deps = tasks.filter { it.counts(pid) }.map { it.id }
                val needs =
                    p.requirements.any {
                        it.targetCount > it.currentCount || it.targetMinutes > it.currentMinutes
                    }
                for (t in tasks) {
                    if (!t.counts(q.id)) continue
                    if (needs && deps.isEmpty()) t.blockedReason = BLOCKED
                    t.dependsOn += deps
                    t.minLagMin = maxOf(t.minLagMin, MIN_LAG_MINUTES)
                }
            }
        }
    }

    internal const val SCHEDULING = "SCHEDULING"
    private const val CYCLE = "DEPENDENCY_CYCLE"
    private const val CYCLE_TEXT =
        " is part of a circular prerequisite chain, so it cannot be scheduled."
    internal const val BLOCKED = "BLOCKED_BY_PREREQUISITE"
    private const val BLOCKED_TEXT = " waits for a prerequisite quest that is not active."
    private const val PASSIVE_ANCHOR = "WAKE"

    /** LDC-2, LDC-3: each plan's sessions for each week. */
    private class GoalSessions(
        private val weeks: List<PlanWeek>,
        private val now: Double,
        private val used: Set<String>?,
        counted: List<JsonValue>,
        weekOf: (JsonValue?) -> Int,
    ) {
        private val done = HashMap<String, Int>()

        init {
            for (s in counted) {
                if (!PageValues.present(s)) throw PageTypeError("Cannot read properties of null")
                val w = weekOf(member(s, "startEpoch"))
                val template = member(s, "templateId")
                if (w >= 0 && truthy(template)) done.merge(text(template) + "|" + w, 1, Int::plus)
            }
        }

        fun addTo(tasks: MutableList<CandidateTask>, plans: List<SessionPlan>) {
            for (p in plans) {
                val t = p.template
                for ((w, wk) in weeks.withIndex()) {
                    val scale = wk.days / DAYS_IN_WEEK
                    val total = maxOf(0.0, round(p.stretch * scale))
                    val already = done[t.templateId + "|" + w] ?: 0
                    val ids =
                        CandidateIds.fresh(
                            used,
                            t.templateId + "|" + wk.key + "|",
                            maxOf(0.0, total - already),
                        )
                    ids.forEachIndexed { j, id ->
                        val k = (already + j).toDouble()
                        val tier =
                            if (k < round(number(p.fMED) * scale)) 3
                            else if (k < round(p.base * scale)) 2 else 1
                        tasks += session(p, t, id, tier, k, w, wk)
                    }
                }
            }
        }

        private fun session(
            p: SessionPlan,
            t: ActivityTemplate,
            id: String,
            tier: Int,
            k: Double,
            w: Int,
            wk: PlanWeek,
        ) =
            CandidateTask(
                id = id,
                kind = TaskKind.GOAL,
                title = t["title"] ?: JsonNull,
                templateId = t.templateId,
                tag = t.tag ?: JsonNull,
                goalId = p.goal.id,
                goalTitle = p.goal.title,
                linkedGoalIds = mutableListOf(p.goal.id),
                linkedQuestRequirementIds = mutableListOf(),
                dependsOn = mutableListOf(),
                minLagMin = 0.0,
                exclusive = false,
                requiresPrior = prior(p.goal.id, t["requiresPrior"]),
                blockedReason = null,
                priority = p.goal.priority,
                questWeight = 0.0,
                tier = tier,
                tierReason = GOAL_TIER_REASONS.getValue(tier),
                dOpt = p.dOpt,
                dMin = p.dMin,
                dMax = p.dMax,
                physical = t["IP"] ?: JsonNull,
                cognitive = t["IC"] ?: JsonNull,
                spacingKey = text(t["spacingKey"]),
                spacingMinH = p.spacingMinH,
                spacingIdealH = t["spacingIdealH"],
                maxPerBwc = or(t["maxPerBwc"], JsonNumber(1.0))!!,
                avoidBeforeSleepMin = orZero(t["avoidBeforeSleepMin"]),
                phases = or(t["phases"], ANY_PHASE)!!,
                preferRestDay = truthy(t["preferRestDay"]),
                windowStart = maxOf(wk.startEpoch, now),
                windowEnd = wk.endEpoch,
                deadlineEpoch = JsonNull,
                week = w.toDouble(),
                sessionIndex = k,
                goalStatus = p.status,
                questId = null,
            )

        private fun prior(goalId: String, value: JsonValue?): PriorVolume? {
            if (!truthy(value)) return null
            val keys = (member(value, "keys") as JsonArray).items
            return PriorVolume(keys.map { "$goalId:" + text(it) }, member(value, "count"))
        }
    }

    private const val DAYS_IN_WEEK = 7.0
    internal val ANY_PHASE = JsonArray(listOf(JsonString("ANY")))
    private val GOAL_TIER_REASONS =
        mapOf(3 to "WEEKLY_MINIMUM", 2 to "WEEKLY_TARGET", 1 to "STRETCH_VOLUME")

    /** LDC-5: the quest tier of a requirement's sessions in [win]. */
    internal fun questTier(q: PlayerQuest, win: Window, now: Double, sessionMinutes: Double): Int {
        val optional = q.tier == PlayerQuest.OPTIONAL
        val dueSoon =
            truthy(q.deadlineEpoch) &&
                !optional &&
                number(q.deadlineEpoch) - now <= DAY + sessionMinutes * MINUTE
        val dailyToday = q.daily && win.end - now <= DAY && !optional
        return if (dueSoon || dailyToday) 4 else if (optional) 2 else 3
    }

    internal const val MINUTE = 60.0

    /** Whether [tag] names a check-in session that only an exact quest tag may claim (LDC-6). */
    internal fun checkIn(tag: JsonValue): Boolean =
        text(tag).let { it.endsWith(".assessment") || it.endsWith(".checkpoint") }

    /** `x.questId === id && x.reqId === req` for a stored link. */
    internal fun storedLink(x: JsonValue?, questId: String, reqId: String): Boolean {
        if (!PageValues.present(x)) throw PageTypeError("Cannot read properties of null")
        return same(member(x, "questId"), JsonString(questId)) &&
            same(member(x, "reqId"), JsonString(reqId))
    }
}
