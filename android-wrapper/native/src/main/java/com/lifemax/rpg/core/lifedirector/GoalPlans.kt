package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.lifedirector.PageValues.clamp
import com.lifemax.rpg.core.lifedirector.PageValues.member
import com.lifemax.rpg.core.lifedirector.PageValues.number
import com.lifemax.rpg.core.lifedirector.PageValues.or
import com.lifemax.rpg.core.lifedirector.PageValues.round
import com.lifemax.rpg.core.lifedirector.PageValues.text
import com.lifemax.rpg.core.lifedirector.PageValues.truthy
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import kotlin.math.ceil

/**
 * LDD-8: one template's weekly plan. [fMED] is the template's minimum frequency as stored (its
 * first `f` entry); [base] and [stretch] are the weekly session counts of tier 2 and tier 1;
 * [dMin], [dOpt], [dMax] the session lengths; [status] `ON_TRACK` or `DEADLINE_UNSUSTAINABLE` with
 * the [projection] of LDD-10; [spacingMinH] the hours between two sessions, as stored.
 */
class SessionPlan(
    val goal: PlayerGoal,
    val template: ActivityTemplate,
    val fMED: JsonValue?,
    val base: Double,
    val stretch: Double,
    val dMin: Double,
    val dOpt: Double,
    val dMax: Double,
    val status: String,
    val projection: Double?,
    val spacingMinH: JsonValue?,
)

/** LDD-9: a goal the engine cannot plan, and the archetypes it suggests from the goal's title. */
class ClarificationPrompt(
    val goalId: String,
    val title: String,
    val suggestedTemplateIds: List<String>,
) {
    val reasonCode: String
        get() = "MISSING_ACTIVITY_PARAMETERS"

    /** What the player would have to give: always these three. */
    val missingParameters: List<String>
        get() = listOf("activityTypeTag", "weeklyFrequency", "sessionDurationMinutes")
}

/** LDD-10: a deadline that cannot be met at the template's safe maximum. */
class TrajectoryWarning(
    val goalId: String,
    val projectedCompletionEpoch: Double,
    val message: String,
) {
    val code: String
        get() = GoalPlans.UNSUSTAINABLE
}

/** What [GoalPlans.expand] gives. */
class GoalExpansion(
    val plans: List<SessionPlan>,
    val clarificationPrompts: List<ClarificationPrompt>,
    val trajectoryWarnings: List<TrajectoryWarning>,
)

/** LDE-1: what placement knows of a goal: how far it lags its schedule, its priority, its title. */
data class GoalInfo(val lag: Double, val priority: Double, val title: String)

/** Goals into weekly session plans (LDD-8 to LDD-10) and their lag (LDE-1). */
object GoalPlans {
    const val ON_TRACK = "ON_TRACK"
    const val UNSUSTAINABLE = "DEADLINE_UNSUSTAINABLE"
    private const val WEEK_SECONDS = 604800.0
    private const val MINUTES_PER_HOUR = 60.0
    private const val NEUTRAL_LAG = 0.5
    private const val LAG_GAIN = 2.0

    /**
     * LDD-8 `expandGoalActivities`: every active goal of [goals] (a stored list; anything else
     * throws `TypeError`) in id order, each template's [SessionPlan]; a goal without templates
     * gives a [ClarificationPrompt]. [intensity] is the checked preference (LDP-2), [now] the
     * plan's clock.
     */
    fun expand(goals: JsonValue?, intensity: JsonValue?, now: Double): GoalExpansion {
        val items = (goals as? JsonArray)?.items ?: throw PageTypeError("goals.slice().sort")
        val sorted =
            if (items.size < 2) items
            else {
                if (items.any { !PageValues.present(it) }) throw PageTypeError("id of null")
                items.sortedWith { a, b -> idOf(a).compareTo(idOf(b)) }
            }
        val plans = ArrayList<SessionPlan>()
        val prompts = ArrayList<ClarificationPrompt>()
        val warnings = ArrayList<TrajectoryWarning>()
        for (raw in sorted) {
            val goal = PlayerGoals.of(raw)
            if (!goal.active) continue
            val templates = PlayerGoals.templates(goal)
            if (templates.isEmpty()) {
                prompts +=
                    ClarificationPrompt(
                        goal.id,
                        goal.title,
                        PlayerGoals.suggestTemplates(JsonString(goal.title)),
                    )
                continue
            }
            for (t in templates) plans += plan(goal, t, intensity, now, warnings)
        }
        return GoalExpansion(plans, prompts, warnings)
    }

    /**
     * `const [fMED, fTarget, fMRV] = t.f`: a list's items, a text's characters (whole code points),
     * and a `TypeError` for anything else.
     */
    private fun frequencies(f: JsonValue?): List<JsonValue> =
        when (f) {
            is JsonArray -> f.items
            is JsonString ->
                buildList {
                    var i = 0
                    while (i < f.value.length) {
                        val n = Character.charCount(f.value.codePointAt(i))
                        add(JsonString(f.value.substring(i, i + n)))
                        i += n
                    }
                }
            else -> throw PageTypeError("t.f is not iterable")
        }

    private fun idOf(goal: JsonValue): String = text(or(member(goal, "id"), member(goal, "goalId")))

    /** LDD-9, LDD-10: one template's plan. */
    private fun plan(
        goal: PlayerGoal,
        t: ActivityTemplate,
        intensity: JsonValue?,
        now: Double,
        warnings: MutableList<TrajectoryWarning>,
    ): SessionPlan {
        val f = frequencies(t["f"])
        val fMED = f.getOrNull(0)
        val fTarget = f.getOrNull(1)
        val fMRV = f.getOrNull(2)
        val durations = (t["d"] as? JsonArray ?: throw PageTypeError("d.map")).items
        fun duration(i: Int) = durations.getOrNull(i)?.let { QuarterHours.roundUp(number(it)) }
        val dMin = duration(0) ?: Double.NaN
        var dOpt = duration(1) ?: Double.NaN
        val dMax = duration(2) ?: Double.NaN
        val med = number(fMED)
        var base =
            when (goal.level) {
                "NOVICE" -> med
                "INTERMEDIATE" -> number(fTarget)
                else -> minOf(number(fMRV), PageValues.plusAsNumber(fTarget, 1.0))
            }
        var stretch = number(fMRV)
        when (intensity) {
            JsonString("LIGHT") -> {
                base = med
                stretch = med
            }
            JsonString("BALANCED") -> stretch = base
            else -> Unit
        }
        var status = ON_TRACK
        var projection: Double? = null
        val target = goal.target
        fun tm(key: String) = member(target, key)
        if (
            truthy(t["volumeDriver"]) &&
                truthy(target) &&
                number(tm("units")) > 0 &&
                truthy(tm("deadlineEpoch")) &&
                number(tm("unitsPerHour")) > 0
        ) {
            val perHour = number(tm("unitsPerHour"))
            val unitsLeft =
                maxOf(0.0, number(tm("units")) - number(PageValues.orZero(tm("completed"))))
            val weeksLeft = maxOf(1.0, (number(tm("deadlineEpoch")) - now) / WEEK_SECONDS)
            val needed = ceil((unitsLeft / perHour) * MINUTES_PER_HOUR / weeksLeft)
            val leastMinutes = med * dMin
            val mostMinutes = number(fMRV) * dMax
            val minutes: Double
            if (needed > mostMinutes) {
                status = UNSUSTAINABLE
                minutes = mostMinutes
                val weeks = (unitsLeft / perHour) * MINUTES_PER_HOUR / mostMinutes
                projection = round(now + weeks * WEEK_SECONDS)
                warnings +=
                    TrajectoryWarning(
                        goal.id,
                        projection,
                        "At a safe maximum of " +
                            PageValues.numberText(mostMinutes) +
                            " min/week, " +
                            goal.title +
                            " finishes later than your deadline.",
                    )
            } else minutes = maxOf(leastMinutes, needed)
            val sessions = clamp(ceil(minutes / dOpt), med, number(fMRV))
            val length = clamp(QuarterHours.roundUp(minutes / sessions), dMin, dMax)
            base = maxOf(base, sessions)
            stretch = maxOf(stretch, base)
            dOpt = length
        }
        val novice = goal.level == "NOVICE" && truthy(t["spacingMinHNovice"])
        return SessionPlan(
            goal,
            t,
            fMED,
            maxOf(med, base),
            maxOf(base, stretch),
            dMin,
            dOpt,
            dMax,
            status,
            projection,
            if (novice) t["spacingMinHNovice"] else t["spacingMinH"],
        )
    }

    /**
     * LDE-1 `goalInfoMap`: each goal of [goals] (a stored list, or nothing) by id (a later goal
     * with the same id wins): its lag, `0.5 + 2 * (elapsed - achieved)` clamped to 0..1 when it has
     * a target with units, a deadline and a creation time before that deadline, else 0.5.
     */
    fun info(goals: JsonValue?, now: Double): Map<String, GoalInfo> {
        val out = linkedMapOf<String, GoalInfo>()
        val items =
            when {
                goals is JsonArray -> goals.items
                truthy(goals) -> throw PageTypeError("goals is not iterable")
                else -> emptyList()
            }
        for (raw in items) {
            val g = PlayerGoals.of(raw)
            fun tm(key: String) = member(g.target, key)
            var lag = NEUTRAL_LAG
            if (
                truthy(g.target) &&
                    number(tm("units")) > 0 &&
                    truthy(tm("deadlineEpoch")) &&
                    truthy(g.createdEpoch) &&
                    PageValues.order(tm("deadlineEpoch"), g.createdEpoch) == 1
            ) {
                val elapsed =
                    clamp(
                        (now - number(g.createdEpoch)) /
                            (number(tm("deadlineEpoch")) - number(g.createdEpoch)),
                        0.0,
                        1.0,
                    )
                val achieved =
                    clamp(
                        number(PageValues.orZero(tm("completed"))) / number(tm("units")),
                        0.0,
                        1.0,
                    )
                lag = clamp(NEUTRAL_LAG + LAG_GAIN * (elapsed - achieved), 0.0, 1.0)
            }
            out[g.id] = GoalInfo(lag, g.priority, g.title)
        }
        return out
    }
}
