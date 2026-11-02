package com.lifemax.rpg.feature.plan.presentation.runner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.components.PixelIcon
import com.lifemax.rpg.feature.plan.domain.PlanRunner
import com.lifemax.rpg.feature.plan.domain.RunnerStep
import com.lifemax.rpg.feature.plan.presentation.PlanWriteActions
import com.lifemax.rpg.feature.plan.presentation.components.LocalPlanColors

/* The timed screens, the rest and the report (see RunnerPhases.kt for the others). */
@Composable
internal fun ColumnScope.Timing(
    r: PlanRunner,
    st: RunnerStep,
    now: RunnerNow,
    a: PlanWriteActions,
    busy: Boolean,
) {
    val c = LocalPlanColors.current
    val w = r.words
    val timer = r.timer!!
    val ratio = now.timerMs.toFloat() / timer.totalMs.coerceAtLeast(1000)
    PhaseScreen(
        body = {
            StepHead(r, st, w["timerLive"])
            Box(
                Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                RunnerRing(
                    1f - ratio,
                    c.accent,
                    formatDuration(now.timerMs),
                    w["ringWork"],
                    final = false,
                )
            }
            Cue(w["coach"], st.cue)
        },
        dock = {
            DockButton(w["timerStop"], { if (!busy) a.stopTimer() })
            DockLink(w["timerCancel"], a::cancelTimer)
        },
    )
}

@Composable
internal fun ColumnScope.TimerDone(
    r: PlanRunner,
    st: RunnerStep,
    a: PlanWriteActions,
    busy: Boolean,
) {
    val c = LocalPlanColors.current
    val w = r.words
    val review = r.review!!
    PhaseScreen(
        body = {
            StepHead(r, st)
            Column(
                Modifier.fillMaxWidth().padding(top = 34.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(c.good.copy(alpha = .14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CheckGlyph(c.good, 24.dp)
                }
                Text(
                    review.time,
                    color = c.text,
                    fontSize = 64.sp,
                    fontWeight = FontWeight(800),
                    letterSpacing = (-2.56).sp,
                    lineHeight = 64.sp,
                )
                Text(
                    if (review.full) w["intervalDone"] else w["stoppedEarly"],
                    color = c.textDim,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    textAlign = TextAlign.Center,
                )
            }
        },
        dock = {
            DockButton("${w["log"]} ${review.time}", { if (!busy) a.log() })
            DockLink(w["restart"], a::restartInterval)
        },
    )
}

@Composable
internal fun ColumnScope.Rest(
    r: PlanRunner,
    st: RunnerStep,
    now: RunnerNow,
    a: PlanWriteActions,
    busy: Boolean,
) {
    val c = LocalPlanColors.current
    val w = r.words
    val rest = r.rest!!
    val left = (now.restMs + 999) / 1000
    val ratio = left * 1000f / rest.totalMs.coerceAtLeast(1)
    PhaseScreen(
        body = {
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PixelIcon("flame", c.gold, Modifier.size(16.dp))
                Text(
                    w["restTag"],
                    color = c.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight(650),
                    lineHeight = 20.8.sp,
                )
            }
            Box(
                Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                RunnerRing(ratio, c.good, formatDuration(left * 1000), w["ringRest"])
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            ) {
                RestPill(w["restLessShort"], w["restLess"]) { if (!busy) a.adjustRest(-15) }
                RestPill(w["restMoreShort"], w["restMore"]) { if (!busy) a.adjustRest(15) }
            }
            val shape = RoundedCornerShape(16.dp)
            Column(
                Modifier.fillMaxWidth()
                    .padding(top = 26.dp)
                    .clip(shape)
                    .background(c.panel)
                    .border(1.dp, c.lineSoft, shape)
                    .padding(12.dp)
            ) {
                Text(w["upNext"], color = c.muted, fontSize = 12.sp, lineHeight = 15.6.sp)
                Text(
                    st.name,
                    Modifier.padding(top = 1.dp),
                    color = c.text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 20.4.sp,
                )
                Text(
                    "${st.setText} · ${st.prescription}",
                    Modifier.padding(top = 3.dp),
                    color = c.textDim,
                    fontSize = 13.sp,
                    lineHeight = 17.55.sp,
                )
            }
        },
        dock = {
            DockButton(w["nextSet"], { if (!busy) a.skipRest() })
            DockLink(w["undo"], a::undo)
        },
    )
}

@Composable
private fun RestPill(text: String, description: String, onClick: () -> Unit) {
    com.lifemax.rpg.feature.plan.presentation.runner.TopPill(text, description, onClick) {}
}

@Composable
internal fun ColumnScope.Complete(r: PlanRunner, a: PlanWriteActions, busy: Boolean) {
    val c = LocalPlanColors.current
    val w = r.words
    val k = r.complete!!
    PhaseScreen(
        body = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.padding(top = 18.dp, bottom = 16.dp).size(96.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (k.full) PixelIcon("trophy", c.gold, Modifier.size(80.dp))
                    else
                        Box(
                            Modifier.fillMaxSize().clip(CircleShape).background(c.accentDim),
                            contentAlignment = Alignment.Center,
                        ) {
                            CheckGlyph(c.accent, 40.dp)
                        }
                }
                BigTitle(if (k.full) w["completeFull"] else w["completePartial"], center = true)
                Text(
                    if (k.full) w["completeNoteFull"] else w["completeNotePartial"],
                    Modifier.padding(top = 10.dp),
                    color = c.textDim,
                    fontSize = 15.sp,
                    lineHeight = 21.75.sp,
                    textAlign = TextAlign.Center,
                )
                Row(
                    Modifier.padding(top = 24.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        "+${k.xp}",
                        color = c.gold,
                        fontSize = 80.sp,
                        fontWeight = FontWeight(800),
                        letterSpacing = (-3.6).sp,
                        lineHeight = 80.sp,
                    )
                    Text(
                        w["xp"],
                        Modifier.padding(bottom = 8.dp),
                        color = c.gold,
                        fontSize = 22.sp,
                        fontWeight = FontWeight(750),
                        lineHeight = 22.sp,
                    )
                }
                StatBand(
                    listOf(
                        Triple(k.sets.toString(), w["sets"], null as Color?),
                        Triple(k.time, w["time"], null),
                        Triple(k.volume, w["volume"], null),
                    ),
                    center = true,
                )
            }
        },
        dock = {
            DockButton(w["backToPlan"], { if (!busy) a.exit() })
            if (!k.full) DockLink(w["finishRemaining"], a::reopen)
        },
    )
}
