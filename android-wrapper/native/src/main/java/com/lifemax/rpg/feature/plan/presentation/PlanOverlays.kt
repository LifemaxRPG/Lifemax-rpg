package com.lifemax.rpg.feature.plan.presentation

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.lifemax.rpg.feature.plan.domain.PlanProjection
import com.lifemax.rpg.feature.plan.presentation.components.PlanConfirmSheet
import com.lifemax.rpg.feature.plan.presentation.components.PlanFieldSheet
import com.lifemax.rpg.feature.plan.presentation.runner.PlanRunnerScreen

/**
 * What the screen puts over the plan when it takes the page's hand-overs: the guided workout, an
 * exercise's field in its sheet, and Leave plan's question (the page's words,
 * [PlanProjection.leaveAsk]).
 */
@Composable
internal fun BoxScope.PlanOverlays(
    p: PlanProjection,
    ui: PlanUiState,
    writes: PlanWriteActions,
    bottomInsetDp: Float,
    topInsetDp: Float,
    live: Boolean,
) {
    val inset = bottomInsetDp.dp
    p.runner?.let { r ->
        // what was typed belongs to the set it was typed for; the itinerary stays open across steps
        val token = "${r.phase.wire}|${r.date}|${r.step?.key.orEmpty()}|${r.step?.set ?: 0}"
        val u = ui.runner
        val run =
            if (u.token == token) u
            else
                u.copy(
                    amount = null,
                    weight = null,
                    confirmSkip = false,
                    swapping = null,
                    duration = null,
                    error = null,
                )
        PlanRunnerScreen(r, run, ui.busy, writes, topInsetDp, bottomInsetDp, live)
    }
    ui.edit?.let { PlanFieldSheet(it, ui.busy, p.logMax, inset, writes, live) }
    if (ui.leaving)
        PlanConfirmSheet(
            p.leaveAsk,
            "Leave plan",
            inset,
            ui.busy,
            writes::leave,
            writes::cancelLeave,
            live,
        )
}
