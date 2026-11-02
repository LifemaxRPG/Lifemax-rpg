package com.lifemax.rpg.feature.plan.presentation.runner

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.components.PageButton
import com.lifemax.rpg.core.designsystem.form.FormTextField
import com.lifemax.rpg.feature.plan.domain.PlanRunner
import com.lifemax.rpg.feature.plan.domain.RunnerPhase
import com.lifemax.rpg.feature.plan.presentation.PlanWriteActions
import com.lifemax.rpg.feature.plan.presentation.RunnerUi
import com.lifemax.rpg.feature.plan.presentation.components.LocalPlanColors
import com.lifemax.rpg.feature.plan.presentation.components.PlanConfirmSheet
import com.lifemax.rpg.feature.plan.presentation.components.PlanForms
import com.lifemax.rpg.feature.plan.presentation.components.PlanSheet
import com.lifemax.rpg.feature.plan.presentation.components.SecondaryAction
import com.lifemax.rpg.feature.plan.presentation.components.SheetHeading
import kotlinx.coroutines.delay

/**
 * Time since the page's projection arrived, so a clock the page sent as "this much left" keeps
 * running on the screen between projections. Nothing runs while the screen is hidden or [live] is
 * off (the screenshots hold a moment still); the page's own tick decides when a clock runs out.
 */
@Composable
internal fun rememberSinceProjection(key: Any, live: Boolean, nowMs: () -> Long): State<Long> {
    val since = remember(key) { mutableLongStateOf(0L) }
    LaunchedEffect(key, live) {
        val start = nowMs()
        since.longValue = 0L
        while (live) {
            delay(TICK_MS)
            since.longValue = nowMs() - start
        }
    }
    return since
}

private const val TICK_MS = 250L

/**
 * The guided workout, drawn over the plan under the shared bars: the page's top bar (pause, the
 * day, the clock, the itinerary), the route of the session, and the screen for the session's phase.
 * The page keeps the session; every button sends one act of the runner command, and the page's next
 * projection says what comes next.
 */
@Composable
fun BoxScope.PlanRunnerScreen(
    r: PlanRunner,
    ui: RunnerUi,
    busy: Boolean,
    actions: PlanWriteActions,
    topInsetDp: Float,
    bottomInsetDp: Float,
    live: Boolean = false,
    nowMs: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    val c = LocalPlanColors.current
    val since by rememberSinceProjection(r, live, nowMs)
    val now =
        RunnerNow(
            r.elapsedMs + if (r.running) since else 0L,
            ((r.timer?.remainingMs ?: 0L) - since).coerceAtLeast(0L),
            ((r.rest?.remainingMs ?: 0L) - since).coerceAtLeast(0L),
        )
    val complete = r.phase == RunnerPhase.COMPLETE
    // Back puts the session away, as the page's Escape does: paused and saved, or closed once
    // complete
    BackHandler(
        enabled =
            live && !ui.itinerary && !ui.confirmSkip && ui.swapping == null && ui.duration == null
    ) {
        if (complete) actions.exit() else actions.pause()
    }
    Box(Modifier.matchParentSize().background(c.bg)) {
        Column(
            Modifier.align(Alignment.TopCenter)
                .widthIn(max = 560.dp)
                .fillMaxSize()
                .padding(top = topInsetDp.dp, bottom = bottomInsetDp.dp)
        ) {
            TopBar(r, now.elapsedMs, complete, actions)
            RunnerRoute(r.route, "${r.progressDone} of ${r.progressTotal} required sets done")
            RunnerPhaseScreen(r, ui, now, actions, busy)
        }
        val inset = bottomInsetDp.dp
        if (ui.itinerary) Itinerary(r, inset, live, actions)
        if (ui.confirmSkip)
            r.step?.let {
                PlanConfirmSheet(
                    it.skipAsk,
                    r.words["skip"],
                    inset,
                    busy,
                    actions::skip,
                    actions::cancelSkip,
                    live,
                )
            }
        ui.swapping?.let { Swap(r, it, ui.error, busy, inset, live, actions) }
        ui.duration?.let { Duration(r, it, ui.error, busy, inset, live, actions) }
    }
}

@Composable
private fun TopBar(r: PlanRunner, elapsedMs: Long, complete: Boolean, a: PlanWriteActions) {
    val c = LocalPlanColors.current
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (complete) TopPill(r.words["close"], "Close workout", a::exit) { CloseGlyph() }
        else TopPill(r.words["pause"], "Pause and save workout", a::pause) { PauseGlyph() }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                r.title,
                color = c.text,
                fontSize = 14.sp,
                fontWeight = FontWeight(650),
                lineHeight = 17.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                formatDuration(elapsedMs),
                color = c.muted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 16.25.sp,
            )
        }
        TopPill("", r.words["itinerary"], { a.itinerary(true) }) { GridGlyph() }
    }
}

/** The full list of the day's exercises (gwrQuickReview's panel). */
@Composable
private fun BoxScope.Itinerary(r: PlanRunner, inset: Dp, live: Boolean, a: PlanWriteActions) {
    val c = LocalPlanColors.current
    val w = r.words
    PlanSheet(w["itineraryTitle"], inset, { a.itinerary(false) }, live) {
        Text(
            w["itineraryKicker"],
            color = c.muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp,
            lineHeight = 14.sp,
        )
        SheetHeading(r.itinerary.title)
        r.itinerary.rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (row.current) c.accent else c.panel2)
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "%02d".format(row.number),
                        color = if (row.current) c.onAccent else c.textDim,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 12.sp,
                    )
                }
                Text(
                    row.name,
                    Modifier.weight(1f),
                    color = c.text,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                )
                Text(
                    row.status,
                    color = c.muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 12.sp,
                )
            }
        }
        PageButton(
            w["itineraryReturn"].lowercase(),
            w["itineraryReturn"],
            { a.itinerary(false) },
            Modifier.fillMaxWidth(),
        )
    }
}

/** The substitution question (gwrSubstituteExercise's prompt), in the page's words. */
@Composable
private fun BoxScope.Swap(
    r: PlanRunner,
    text: String,
    error: String?,
    busy: Boolean,
    inset: Dp,
    live: Boolean,
    a: PlanWriteActions,
) {
    val c = LocalPlanColors.current
    val step = r.step ?: return
    PlanSheet(step.swapAsk, inset, a::cancelSwap, live) {
        Text(step.swapAsk, color = c.textDim, fontSize = 14.sp, lineHeight = 20.sp)
        PlanForms {
            FormTextField(
                step.name,
                text,
                a::swapDraft,
                Modifier.fillMaxWidth(),
                maxLength = 85,
                error = error,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SecondaryAction("Cancel", a::cancelSwap)
            PageButton(
                "Use this",
                "Use this exercise",
                { if (!busy) a.swap() },
                Modifier.weight(1f),
            )
        }
    }
}

/** The duration question (gwrGo's prompt) for a timed exercise that gave none. */
@Composable
private fun BoxScope.Duration(
    r: PlanRunner,
    text: String,
    error: String?,
    busy: Boolean,
    inset: Dp,
    live: Boolean,
    a: PlanWriteActions,
) {
    val step = r.step ?: return
    val c = LocalPlanColors.current
    PlanSheet(step.durationAsk.text, inset, a::cancelDuration, live) {
        Text(
            step.durationAsk.text,
            color = c.text,
            fontSize = 15.sp,
            fontWeight = FontWeight(600),
            lineHeight = 21.sp,
        )
        PlanForms {
            FormTextField(
                step.unit.replaceFirstChar { it.uppercase() },
                text,
                a::duration,
                Modifier.fillMaxWidth(),
                decimal = true,
                error = error,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SecondaryAction("Cancel", a::cancelDuration)
            PageButton(
                r.words["startTimer"],
                r.words["startTimer"],
                { if (!busy) a.go() },
                Modifier.weight(1f),
            )
        }
    }
}
