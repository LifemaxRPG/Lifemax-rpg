package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.lifedirector.PageValues.member
import com.lifemax.rpg.core.lifedirector.PageValues.number
import com.lifemax.rpg.core.lifedirector.PageValues.or
import com.lifemax.rpg.core.lifedirector.PageValues.truthy
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonValue

/**
 * What [PlanDemand.of] gives: the goal plans, the candidate tasks with each one's [prepared]
 * constants (in the tasks' order), the goals' info and their fairness [shares].
 */
class PlanDemandResult(
    val expansion: GoalExpansion,
    val candidates: CandidateSet,
    val goalInfo: Map<String, GoalInfo>,
    val prepared: List<PreparedTask>,
    val shares: Map<String, Double>,
)

/**
 * LDE-4: the demand half of planning (`generateSchedule`, `engine.js:1205-1228`), on an engine
 * input as the page builds it: its availability (LDA-10) for the plan's days and bounds, the goal
 * plans (LDD-8), the completed sessions from a week before the plan, the candidate tasks (LDC-2 to
 * LDC-10) with [kept] sessions and [used] instance ids as placement's preparation finds them
 * (WP-LD4), the goals' info (LDE-1), each task's constants (LDE-2) and the shares.
 */
object PlanDemand {
    private const val HISTORY_REACH = 7 * 86400.0

    fun of(input: JsonValue?, kept: List<JsonValue>, used: Set<String>): PlanDemandResult {
        val plan = Availability.derive(input)
        val now = number(member(input, "currentTimeEpoch"))
        val h0 = plan.grid.slots.startEpoch
        val hn = plan.grid.slots.endEpoch
        val expansion =
            GoalPlans.expand(
                or(member(input, "goals"), JsonArray.EMPTY),
                plan.prefs["intensityLevel"],
                now,
            )
        val history =
            listOf(member(input, "completionHistory"))
                .flatMap {
                    (it as? JsonArray)?.items
                        ?: if (truthy(it)) throw PageTypeError("filter is not a function")
                        else emptyList()
                }
                .filter {
                    if (!PageValues.present(it))
                        throw PageTypeError("Cannot read properties of null")
                    number(member(it, "startEpoch")) >= h0 - HISTORY_REACH
                }
        val candidates =
            Candidates.compile(
                expansion.plans,
                or(member(input, "activeQuests"), JsonArray.EMPTY),
                CandidateIds.weeks(plan.days),
                now,
                plan.days,
                history,
                kept,
                used,
            )
        val info = GoalPlans.info(member(input, "goals"), now)
        val prepared =
            candidates.tasks.map {
                TaskPreparation.prepare(TaskPreparation.demandOf(it), info, h0, hn, now)
            }
        return PlanDemandResult(
            expansion,
            candidates,
            info,
            prepared,
            TaskPreparation.shares(candidates.tasks, info),
        )
    }
}
