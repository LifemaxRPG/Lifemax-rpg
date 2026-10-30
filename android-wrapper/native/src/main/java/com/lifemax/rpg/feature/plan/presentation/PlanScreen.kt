package com.lifemax.rpg.feature.plan.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.LifeMaxTheme
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
    onPage: (PlanPageAction) -> Unit,
) {
    val projection by vm.projection.collectAsState()
    val ui by vm.ui.collectAsState()
    val p = projection ?: return
    LaunchedEffect(p.session, p.selectedRevision) { vm.adopt(p) }
    val actions = remember(vm, onPage) { PlanActions(vm, onPage) }
    PlanScreen(p, ui, actions, topInsetDp, bottomInsetDp)
}

/** Everything the screen can ask for, so the screen itself holds no state. */
class PlanActions(
    val selectDay: (String) -> Unit = {},
    val selectWeek: (Int) -> Unit = {},
    val toggle: (SetRef, Boolean) -> Unit = { _, _ -> },
    val page: (PlanPageAction) -> Unit = {},
    val feedbackShown: (Long) -> Unit = {},
) {
    constructor(
        vm: PlanViewModel,
        page: (PlanPageAction) -> Unit,
    ) : this(vm::selectDay, vm::selectWeek, vm::toggle, page, vm::feedbackShown)
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
) {
    val colors = remember(p.tokens, p.light) { PlanColors(p.tokens, p.light) }
    val day = ui.day(p)
    // where the last box was tapped: the page's celebration starts there too
    var lastTap by remember { mutableStateOf(Offset.Unspecified) }
    LifeMaxTheme(p.tokens, p.light) {
        CompositionLocalProvider(
            LocalPlanColors provides colors,
            LocalTextStyle provides
                LocalTextStyle.current.copy(letterSpacing = 0.sp, lineHeightStyle = CssLines),
        ) {
            Box(Modifier.fillMaxSize().background(colors.bg)) {
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
                            actions.page(PlanPageAction.Guided(day.date))
                        }
                    }
                    item("program") { PlanProgramCard(p) { actions.page(PlanPageAction.Leave) } }
                    item("calendar") {
                        PlanCalendarCard(p, day, actions.selectWeek, actions.selectDay)
                    }
                    item("day") {
                        PlanDayCard(
                            day,
                            ui.pending,
                            onToggle = { set, checked, at ->
                                lastTap = at
                                actions.toggle(set, checked)
                            },
                            onPage = actions.page,
                        )
                    }
                    item("rules") { PlanRulesCard(p.rules, p.progression, rulesOpen) }
                }
                ui.feedback?.let {
                    PlanFeedbackLayer(
                        it,
                        lastTap,
                        topInsetDp,
                        bottomInsetDp,
                        p.reducedMotion,
                        actions.feedbackShown,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanHead() {
    val c = LocalPlanColors.current
    Column(Modifier.padding(bottom = 4.dp)) {
        Text(
            "TRAINING",
            color = c.accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.32.sp,
            lineHeight = 16.5.sp,
        )
        Text(
            "Workout plan",
            Modifier.padding(top = 6.dp).semantics { heading() },
            color = c.text,
            fontSize = 21.sp,
            fontWeight = FontWeight(650),
            letterSpacing = (-0.63).sp,
            lineHeight = 24.15.sp,
        )
        Text(
            "Follow a structured program. Complete every box in a day to earn XP.",
            Modifier.padding(top = 6.dp),
            color = c.muted,
            fontSize = 13.5.sp,
            lineHeight = 20.25.sp,
        )
    }
}

/** Line heights spread above and below the text, as CSS does. */
private val CssLines = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
