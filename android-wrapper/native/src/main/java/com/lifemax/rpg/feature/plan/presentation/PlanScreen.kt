package com.lifemax.rpg.feature.plan.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.lifemax.rpg.core.designsystem.PageTheme
import com.lifemax.rpg.core.designsystem.components.PageHead
import com.lifemax.rpg.feature.plan.domain.PlanPageAction
import com.lifemax.rpg.feature.plan.domain.PlanProjection
import com.lifemax.rpg.feature.plan.domain.SetRef
import com.lifemax.rpg.feature.plan.presentation.components.LocalPlanColors
import com.lifemax.rpg.feature.plan.presentation.components.PlanCalendarCard
import com.lifemax.rpg.feature.plan.presentation.components.PlanColors
import com.lifemax.rpg.feature.plan.presentation.components.PlanDayCard
import com.lifemax.rpg.feature.plan.presentation.components.PlanFeedbackLayer
import com.lifemax.rpg.feature.plan.presentation.components.PlanLauncherCard
import com.lifemax.rpg.feature.plan.presentation.components.PlanProgramCard
import com.lifemax.rpg.feature.plan.presentation.components.PlanRulesCard

@Composable
fun PlanRoute(
    vm: PlanViewModel,
    topInsetDp: Float,
    bottomInsetDp: Float,
    visible: Boolean = true,
    onPage: (PlanPageAction) -> Unit,
) {
    val projection by vm.projection.collectAsState()
    val offered by vm.offers.collectAsState()
    val ui by vm.ui.collectAsState()
    val actions = remember(vm, onPage) { PlanActions(vm, onPage) }
    val p = projection
    val o = offered
    if (p != null) {
        LaunchedEffect(p.session, p.selectedRevision) { vm.adopt(p) }
        PlanScreen(p, ui, actions, topInsetDp, bottomInsetDp, live = visible)
    } else if (o != null) PlanOfferScreen(o, ui, actions, topInsetDp, bottomInsetDp)
}

/** Everything the screen can ask for, so the screen itself holds no state. */
class PlanActions(
    val selectDay: (String) -> Unit = {},
    val selectWeek: (Int) -> Unit = {},
    val toggle: (SetRef, Boolean) -> Unit = { _, _ -> },
    val page: (PlanPageAction) -> Unit = {},
    val feedbackShown: (Long) -> Unit = {},
    /**
     * The writes the screen can make itself (fields, Leave plan, sign-up, runner); none, and each
     * is the page's.
     */
    val writes: PlanWriteActions? = null,
) {
    constructor(
        vm: PlanViewModel,
        page: (PlanPageAction) -> Unit,
    ) : this(vm::selectDay, vm::selectWeek, vm::toggle, page, vm::feedbackShown, vm.actions)
}

/**
 * The Workout plan view as the page lays it out: the guided-workout launcher, the active plan, the
 * calendar of weeks and days, the selected day's exercises and the General rules. [rulesOpen] is
 * where the rules start (the page starts them closed).
 */
@Composable
fun PlanScreen(
    p: PlanProjection,
    ui: PlanUiState,
    actions: PlanActions,
    topInsetDp: Float = 64f,
    bottomInsetDp: Float = 72f,
    list: LazyListState = rememberLazyListState(),
    rulesOpen: Boolean = false,
    live: Boolean = false,
) {
    val day = ui.day(p)
    val writes = actions.writes
    // where the last box was tapped: the page's celebration starts there too
    var lastTap by remember { mutableStateOf(Offset.Unspecified) }
    PlanTheme(p.tokens, p.light) {
        Box(Modifier.fillMaxSize().background(LocalPlanColors.current.bg)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = list,
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
                item("launcher") {
                    PlanLauncherCard(
                        p.guidedSession ?: day.guided,
                        resume = p.guidedSession != null,
                    ) {
                        if (writes != null && p.features.runner)
                            writes.launch(day.date, p.guidedSession != null)
                        else actions.page(PlanPageAction.Guided(day.date))
                    }
                }
                item("program") {
                    PlanProgramCard(p) {
                        if (writes != null && p.features.leave) writes.askLeave()
                        else actions.page(PlanPageAction.Leave)
                    }
                }
                item("calendar") { PlanCalendarCard(p, day, actions.selectWeek, actions.selectDay) }
                item("day") {
                    PlanDayCard(
                        day,
                        ui.pending,
                        onToggle = { set, checked, at ->
                            lastTap = at
                            actions.toggle(set, checked)
                        },
                        onPage = actions.page,
                        onField =
                            if (writes != null && p.features.fields)
                                { item, kind -> writes.editField(day, item, kind) }
                            else null,
                    )
                }
                item("rules") { PlanRulesCard(p.rules, p.progression, rulesOpen) }
            }
            if (writes != null) PlanOverlays(p, ui, writes, bottomInsetDp, topInsetDp, live)
            ui.feedback?.let {
                PlanFeedbackLayer(
                    it,
                    lastTap,
                    p.celebrationTarget,
                    bottomInsetDp,
                    p.reducedMotion,
                    actions.feedbackShown,
                )
            }
        }
    }
}

/** The page's palette and type for the plan view, with the plan's own colours. */
@Composable
internal fun PlanTheme(tokens: Map<String, Long>, light: Boolean, content: @Composable () -> Unit) {
    val colors = remember(tokens, light) { PlanColors(tokens, light) }
    PageTheme(colors) {
        CompositionLocalProvider(LocalPlanColors provides colors, content = content)
    }
}

@Composable
internal fun PlanHead() =
    PageHead(
        "TRAINING",
        "Workout plan",
        "Follow a structured program. Complete every box in a day to earn XP.",
        Modifier.padding(bottom = 4.dp),
    )
