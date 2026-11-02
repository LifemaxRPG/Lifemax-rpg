package com.lifemax.rpg.feature.plan.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.lifemax.rpg.feature.plan.domain.PlanOfferProjection
import com.lifemax.rpg.feature.plan.presentation.components.LocalPlanColors
import com.lifemax.rpg.feature.plan.presentation.components.PlanFeedbackLayer
import com.lifemax.rpg.feature.plan.presentation.components.PlanOfferCard

/**
 * The Workout plan with no plan yet: the heading and the plan the page offers, with its start date
 * and the sign-up button. Signing up is the page's startPlan; the plan's own screen replaces this
 * one when the page sends it.
 */
@Composable
fun PlanOfferScreen(
    p: PlanOfferProjection,
    ui: PlanUiState,
    actions: PlanActions,
    topInsetDp: Float = 64f,
    bottomInsetDp: Float = 72f,
) {
    val writes = actions.writes
    PlanTheme(p.tokens, p.light) {
        Box(Modifier.fillMaxSize().background(LocalPlanColors.current.bg)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = topInsetDp.dp + 16.dp,
                        bottom = bottomInsetDp.dp + 24.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item("head") { PlanHead() }
                item("offer") {
                    PlanOfferCard(
                        p.offer,
                        ui.startDate ?: p.offer.start,
                        ui.busy,
                        onStart = { writes?.startDate(it) },
                        onSignUp = { writes?.signUp(p.offer.start) },
                    )
                }
            }
            ui.feedback?.let {
                PlanFeedbackLayer(
                    it,
                    Offset.Unspecified,
                    p.celebrationTarget,
                    bottomInsetDp,
                    p.reducedMotion,
                    actions.feedbackShown,
                )
            }
        }
    }
}
