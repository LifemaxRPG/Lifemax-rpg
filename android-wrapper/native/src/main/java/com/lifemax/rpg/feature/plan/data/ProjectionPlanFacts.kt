package com.lifemax.rpg.feature.plan.data

import com.lifemax.rpg.core.adapters.PlanFacts
import com.lifemax.rpg.feature.plan.domain.DayStatus
import com.lifemax.rpg.feature.plan.domain.PlanRepository

/**
 * [PlanFacts] from the plan screen's projection of the page's plan. The plan's content (`planDay`)
 * is page code, so while the page is the writer the projection it pushes is the one source of
 * truth: `DONE` is the page's `dayDone(date)` (its calendar class `done`) and a `rest` kind is
 * `planDay(date).kind === 'rest'`. A day the projection does not hold (before the plan starts,
 * after its eighth week, or no plan) is neither done nor a rest day, as `planDay` answers null
 * there.
 *
 * Limit: the projection is as fresh as the page's last push, which follows every save, so a fact
 * read between a tick and its push is one save old.
 */
class ProjectionPlanFacts(private val plan: PlanRepository) : PlanFacts {
    override fun isDone(day: String): Boolean = plan.state.value?.day(day)?.status == DayStatus.DONE

    override fun isRestDay(day: String): Boolean = plan.state.value?.day(day)?.rest == true
}
