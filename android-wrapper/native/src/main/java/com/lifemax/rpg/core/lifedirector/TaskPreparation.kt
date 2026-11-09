package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.lifedirector.PageValues.number
import com.lifemax.rpg.core.lifedirector.PageValues.or
import com.lifemax.rpg.core.lifedirector.PageValues.orZero
import com.lifemax.rpg.core.lifedirector.PageValues.text
import com.lifemax.rpg.core.lifedirector.PageValues.truthy
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import kotlin.math.ceil

/**
 * LDE-2: what placement reads of a task again and again, worked out once: its lengths in slots, its
 * priority and goal lag on 0..1, the base of its urgency, and whether it is high-intensity. [lag]
 * is null where the page's value is undefined (a goal info without a lag).
 */
data class PreparedTask(
    val dOptSlots: Double,
    val dMinSlots: Double,
    val priority: Double,
    val lag: Double?,
    val urgencyBase: Double,
    val high: Boolean,
)

/** The members of a task [TaskPreparation.prepare] reads, as stored. */
class TaskDemand(
    val dOpt: JsonValue?,
    val dMin: JsonValue?,
    val priority: JsonValue?,
    val questWeight: JsonValue?,
    val goalId: JsonValue?,
    val deadlineEpoch: JsonValue?,
    val physical: JsonValue?,
    val cognitive: JsonValue?,
)

/** LDE-2 `prepTask` and the fairness shares (LDE-4). */
object TaskPreparation {
    private const val SLOT_MINUTES = 15.0
    private const val DEFAULT_PRIORITY = 3.0
    private const val PRIORITY_SHARE = 0.60
    private const val QUEST_SHARE = 0.40
    private const val PRIORITY_STEPS = 4.0
    private const val QUEST_WEIGHT_TOP = 5.0
    private const val NEUTRAL_LAG = 0.5
    private const val URGENCY_RATE = -3.0
    private const val NO_DEADLINE_URGENCY = 0.25
    private const val HIGH_INTENSITY = 4.0
    private const val MINUTE = 60.0

    /**
     * LDE-2: [t] on a plan of [h0]..[hn] at [now]. The urgency base of a task with a deadline is
     * `exp(-3 * max(0, (deadline - now - dOpt minutes) / plan length))`, the exponential taken as
     * the page's ([PageMath.exp], LDF-1); without one 0.25.
     */
    fun prepare(
        t: TaskDemand,
        goals: Map<String, GoalInfo>,
        h0: Double,
        hn: Double,
        now: Double,
    ): PreparedTask {
        val optSlots = ceil(number(t.dOpt) / SLOT_MINUTES)
        val priority = number(or(t.priority, JsonNumber(DEFAULT_PRIORITY)))
        val info = if (truthy(t.goalId)) goals[text(t.goalId)] else null
        val span = hn - h0
        val urgency =
            if (truthy(t.deadlineEpoch))
                PageMath.exp(
                    URGENCY_RATE *
                        maxOf(0.0, (number(t.deadlineEpoch) - now - number(t.dOpt) * MINUTE) / span)
                )
            else NO_DEADLINE_URGENCY
        return PreparedTask(
            optSlots,
            minOf(optSlots, ceil(number(t.dMin) / SLOT_MINUTES)),
            PRIORITY_SHARE * ((priority - 1) / PRIORITY_STEPS) +
                QUEST_SHARE * (number(orZero(t.questWeight)) / QUEST_WEIGHT_TOP),
            if (info != null) info.lag else NEUTRAL_LAG,
            urgency,
            PageValues.atLeast(t.physical, JsonNumber(HIGH_INTENSITY)) ||
                PageValues.atLeast(t.cognitive, JsonNumber(HIGH_INTENSITY)),
        )
    }

    /** [task]'s members as [prepare] reads them. */
    fun demandOf(task: CandidateTask): TaskDemand =
        TaskDemand(
            JsonNumber(task.dOpt),
            JsonNumber(task.dMin),
            JsonNumber(task.priority),
            JsonNumber(task.questWeight),
            task.goalId?.let(::JsonString),
            task.deadlineEpoch,
            task.physical,
            task.cognitive,
        )

    /**
     * LDE-4: each goal's share of the plan's goal time: its priority (3 for a goal without info)
     * over the sum for every goal that has a task, in id order; a sum of 0 counts as 1.
     */
    fun shares(tasks: List<CandidateTask>, goals: Map<String, GoalInfo>): Map<String, Double> {
        val ids =
            tasks.mapNotNull { it.goalId?.takeIf { id -> id.isNotEmpty() } }.distinct().sorted()
        fun weight(id: String) = goals[id]?.priority ?: DEFAULT_PRIORITY
        val sum = ids.fold(0.0) { s, id -> s + weight(id) }.let { if (truthy(it)) it else 1.0 }
        return ids.associateWith { weight(it) / sum }
    }
}
