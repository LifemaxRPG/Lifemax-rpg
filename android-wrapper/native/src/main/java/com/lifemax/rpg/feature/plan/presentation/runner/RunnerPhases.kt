package com.lifemax.rpg.feature.plan.presentation.runner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.form.ChoiceField
import com.lifemax.rpg.core.designsystem.form.FormOption
import com.lifemax.rpg.feature.plan.domain.PlanRunner
import com.lifemax.rpg.feature.plan.domain.RunnerPhase
import com.lifemax.rpg.feature.plan.domain.RunnerStep
import com.lifemax.rpg.feature.plan.presentation.PlanWriteActions
import com.lifemax.rpg.feature.plan.presentation.RunnerUi
import com.lifemax.rpg.feature.plan.presentation.components.LocalPlanColors
import com.lifemax.rpg.feature.plan.presentation.components.PlanForms

/** The clocks of the moment: what the page sent, run on by the time since it did. */
internal class RunnerNow(val elapsedMs: Long, val timerMs: Long, val restMs: Long)

/** A screen of the runner: its body scrolls, its buttons sit in the dock below (.gx-screen). */
@Composable
internal fun ColumnScope.PhaseScreen(
    body: @Composable ColumnScope.() -> Unit,
    dock: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalPlanColors.current
    Column(Modifier.weight(1f).fillMaxWidth()) {
        Column(
            Modifier.weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 18.dp),
            content = body,
        )
        Column(
            Modifier.fillMaxWidth()
                .drawBehind { drawRect(c.lineSoft, Offset.Zero, Size(size.width, 1.dp.toPx())) }
                .background(c.bg)
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = dock,
        )
    }
}

/** The screen for the session's phase. */
@Composable
internal fun ColumnScope.RunnerPhaseScreen(
    r: PlanRunner,
    ui: RunnerUi,
    now: RunnerNow,
    a: PlanWriteActions,
    busy: Boolean,
) {
    when (r.phase) {
        RunnerPhase.INTRO -> Intro(r, a, busy)
        RunnerPhase.COMPLETE -> Complete(r, a, busy)
        RunnerPhase.READY -> Ready(r, r.step!!, a, busy)
        RunnerPhase.ACTIVE -> Active(r, r.step!!, ui, a, busy)
        RunnerPhase.TIMING -> Timing(r, r.step!!, now, a, busy)
        RunnerPhase.TIMER_DONE -> TimerDone(r, r.step!!, a, busy)
        RunnerPhase.REST -> Rest(r, r.step!!, now, a, busy)
    }
}

@Composable
internal fun StepText(text: String, live: String? = null) {
    val c = LocalPlanColors.current
    Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text,
            color = c.muted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 17.55.sp,
        )
        if (live != null)
            Text(
                live,
                color = c.good,
                fontSize = 13.sp,
                fontWeight = FontWeight(650),
                lineHeight = 17.55.sp,
            )
    }
}

@Composable
private fun ColumnScope.Intro(r: PlanRunner, a: PlanWriteActions, busy: Boolean) {
    val c = LocalPlanColors.current
    val intro = r.intro!!
    val w = r.words
    PhaseScreen(
        body = {
            StepText(intro.step)
            BigTitle(intro.title)
            StatBand(
                listOf(
                    Triple(intro.exercises.toString(), w["exercises"], null),
                    Triple(intro.sets.toString(), w["sets"], null),
                    Triple("+${intro.xp}", w["xp"], c.gold),
                )
            )
            PlanForms {
                ChoiceField(
                    w["introDay"],
                    intro.days.map { FormOption(it.date, it.label) },
                    intro.days.first { it.selected }.date,
                    { if (!busy) a.pickDay(it) },
                    Modifier.padding(top = 20.dp),
                    menu = true,
                )
            }
            Column(Modifier.padding(top = 20.dp)) {
                intro.log.forEachIndexed { i, row ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 12.dp).drawBehind {
                            if (i < intro.log.lastIndex)
                                drawRect(
                                    c.lineSoft,
                                    Offset(0f, size.height - 1.dp.toPx()),
                                    Size(size.width, 1.dp.toPx()),
                                )
                        },
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier.size(28.dp)
                                .clip(CircleShape)
                                .background(if (row.done) c.good.copy(alpha = .14f) else c.panel2),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (row.done) CheckGlyph(c.good, 14.dp)
                            else
                                Text(
                                    row.number.toString(),
                                    color = c.textDim,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 13.sp,
                                )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (row.optional) "${row.name} ${w["optional"]}" else row.name,
                                color = c.text,
                                fontSize = 15.sp,
                                fontWeight = FontWeight(650),
                                lineHeight = 19.5.sp,
                            )
                            Text(
                                row.prescription,
                                color = c.muted,
                                fontSize = 13.sp,
                                lineHeight = 17.55.sp,
                            )
                        }
                    }
                }
            }
        },
        dock = {
            DockButton(w["start"], { if (!busy) a.begin() })
            Text(
                w["introNote"],
                Modifier.padding(top = 10.dp, bottom = 2.dp),
                color = c.muted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 18.2.sp,
                textAlign = TextAlign.Center,
            )
        },
    )
}

/** The exercise and its set (.gx-ex, .gx-sets): shared by the screens of a set in progress. */
@Composable
internal fun StepHead(r: PlanRunner, st: RunnerStep, live: String? = null) {
    val c = LocalPlanColors.current
    StepText(st.heading, live)
    Text(
        st.name,
        Modifier.semantics { heading() },
        color = c.text,
        fontSize = 30.sp,
        fontWeight = FontWeight(800),
        letterSpacing = (-0.75).sp,
        lineHeight = 31.8.sp,
    )
    Text(
        st.prescription,
        Modifier.padding(top = 8.dp),
        color = c.textDim,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    )
    SetPips(st.pips, st.setText)
}

@Composable
private fun ColumnScope.Ready(r: PlanRunner, st: RunnerStep, a: PlanWriteActions, busy: Boolean) {
    val c = LocalPlanColors.current
    val w = r.words
    PhaseScreen(
        body = {
            StepHead(r, st)
            if (st.suggest.isNotEmpty())
                Row(
                    Modifier.fillMaxWidth().padding(top = 30.dp, bottom = 4.dp),
                    horizontalArrangement =
                        Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        st.suggest,
                        color = c.text,
                        fontSize = 112.sp,
                        fontWeight = FontWeight(800),
                        letterSpacing = (-6.2).sp,
                        lineHeight = 98.sp,
                    )
                    Text(
                        st.unit,
                        Modifier.padding(bottom = 6.dp),
                        color = c.muted,
                        fontSize = 20.sp,
                        fontWeight = FontWeight(650),
                        lineHeight = 20.sp,
                    )
                }
            else
                Column(
                    Modifier.fillMaxWidth().padding(top = 30.dp, bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier.size(76.dp)
                            .clip(CircleShape)
                            .background(c.good.copy(alpha = .14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CheckGlyph(c.good, 30.dp)
                    }
                    Text(w["checkHero"], color = c.textDim, fontSize = 16.sp, lineHeight = 16.sp)
                }
            if (st.lastReady.isNotEmpty())
                Text(
                    st.lastReady,
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    color = c.textDim,
                    fontSize = 14.sp,
                    lineHeight = 19.6.sp,
                    textAlign = TextAlign.Center,
                )
            Cue(w["coach"], st.cue)
        },
        dock = {
            DockButton(if (st.timed) w["startTimer"] else w["startSet"], { if (!busy) a.go() })
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DockLink(w["swap"], a::askSwap)
                DockLink(w["skip"], a::askSkip)
            }
        },
    )
}

@Composable
internal fun Cue(title: String, text: String) {
    val c = LocalPlanColors.current
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.fillMaxWidth()
            .padding(top = 26.dp)
            .clip(shape)
            .background(c.panel)
            .border(1.dp, c.lineSoft, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            title,
            Modifier.padding(bottom = 3.dp),
            color = c.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 16.9.sp,
        )
        Text(text, color = c.textDim, fontSize = 14.sp, lineHeight = 21.sp)
    }
}

@Composable
private fun ColumnScope.Active(
    r: PlanRunner,
    st: RunnerStep,
    ui: RunnerUi,
    a: PlanWriteActions,
    busy: Boolean,
) {
    val c = LocalPlanColors.current
    val w = r.words
    val draft =
        ui.takeIf { it.token == "${r.phase.wire}|${r.date}|${st.key}|${st.set}" } ?: RunnerUi()
    PhaseScreen(
        body = {
            StepHead(r, st, w["live"])
            Column(
                Modifier.fillMaxWidth().padding(top = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (!st.isCheck)
                    NumberStepper(
                        if (st.kind == "reps") w["repsDone"] else st.label,
                        draft.amount ?: st.suggest,
                        "—",
                        st.amountStep,
                        st.amountMin,
                        a::amount,
                    )
                st.weight?.let {
                    NumberStepper(
                        w["weight"],
                        draft.weight ?: it.suggest,
                        w["weightHint"],
                        it.step,
                        it.min,
                        a::weight,
                    )
                }
                if (st.isCheck && st.weight == null)
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier.size(76.dp)
                                .clip(CircleShape)
                                .background(c.good.copy(alpha = .14f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CheckGlyph(c.good, 30.dp)
                        }
                        Text(
                            w["finishThenLog"],
                            color = c.textDim,
                            fontSize = 16.sp,
                            lineHeight = 16.sp,
                        )
                    }
            }
            if (st.lastActive.isNotEmpty())
                Text(
                    st.lastActive,
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    color = c.textDim,
                    fontSize = 14.sp,
                    lineHeight = 19.6.sp,
                    textAlign = TextAlign.Center,
                )
            ui.error?.let { Problem(it) }
        },
        dock = {
            DockButton(w["logSet"], { if (!busy) a.log() })
            DockLink(w["back"], a::back)
        },
    )
}

/** The page's refusal, where the player is looking. */
@Composable
internal fun Problem(text: String) {
    Text(
        text,
        Modifier.fillMaxWidth().padding(top = 14.dp),
        color = LocalPlanColors.current.bad,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 19.sp,
        textAlign = TextAlign.Center,
    )
}
