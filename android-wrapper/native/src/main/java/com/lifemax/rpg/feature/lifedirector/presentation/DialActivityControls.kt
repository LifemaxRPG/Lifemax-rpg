package com.lifemax.rpg.feature.lifedirector.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.feature.lifedirector.domain.MoveSheetModel
import com.lifemax.rpg.feature.lifedirector.domain.SessionSheetModel
import com.lifemax.rpg.feature.lifedirector.domain.SheetModel
import com.lifemax.rpg.feature.lifedirector.domain.TimerSheetModel
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorPalette as P
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorText
import com.lifemax.rpg.feature.lifedirector.presentation.components.clockText
import com.lifemax.rpg.feature.lifedirector.presentation.components.rememberTickingClock

/** One continuous activity face: full title and schedule first, with no intermediate menus. */
@Composable
internal fun DialActivityControls(
    model: SheetModel,
    ui: DirectorUi,
    actions: DirectorActions,
    ticking: Boolean,
    reducedMotion: Boolean,
) {
    val ready = ui.pendingToday == null
    AnimatedContent(
        targetState = model,
        modifier = Modifier.fillMaxSize(),
        contentKey = { it.kind to it.id },
        transitionSpec = {
            fadeIn(tween(if (reducedMotion) 0 else 180)) togetherWith
                fadeOut(tween(if (reducedMotion) 0 else 120))
        },
        label = "dial controls",
    ) { current ->
        Column(
            Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val title =
                    when (current) {
                        is MoveSheetModel -> current.title
                        is SessionSheetModel -> current.title
                        is TimerSheetModel -> current.title
                        else -> ""
                    }
                Text(
                    title,
                    style = DirectorText,
                    color = P.ink,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                when (current) {
                    is SessionSheetModel -> {
                        DialCaption(current.`when`)
                        current.primary?.let {
                            DialPrimary(it.label, ready, actions.session.primary)
                        }
                        val draft =
                            ui.minutesDraft?.takeIf { it.id == current.id }?.text
                                ?: current.minutes?.value?.toString().orEmpty()
                        if (current.minutes != null && current.complete != null) {
                            DialCaption(current.minutes.label)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                DialLink("−5", ready) { actions.session.step(-5) }
                                Text(
                                    draft,
                                    color = P.ink,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                DialLink("+5", ready) { actions.session.step(5) }
                            }
                            DialPrimary(current.complete.label, ready, actions.session.complete)
                        }
                        current.row.forEach { button ->
                            DialLink(button.label, ready) { actions.session.row(button) }
                        }
                        DialCaption(current.note.joinToString("") { it.t })
                        DialCaption(current.orphan)
                        DialCaption(current.why.summary)
                    }
                    is MoveSheetModel -> {
                        DialCaption(current.note)
                        if (current.times.isEmpty()) DialCaption(current.none)
                        current.times.forEach { time ->
                            DialLink(time.label, ready) { actions.moves.moveTo(time) }
                        }
                    }
                    is TimerSheetModel -> {
                        val now by rememberTickingClock(ticking && !current.paused)
                        DialCaption(current.label)
                        Text(
                            clockText(current.timer.elapsedMs(now)),
                            style = DirectorText,
                            color = if (current.paused) P.work else P.ink,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        DialCaption(current.planned)
                        DialPrimary(current.finish, ready, actions.timerMeal.finish)
                        DialLink(current.pause, ready, actions.timerMeal.pause)
                        DialLink(current.stop, ready, actions.timerMeal.stop)
                        current.xp?.let { spans ->
                            DialCaption(spans.joinToString("") { it.t })
                        }
                    }
                    else -> {}
                }
                if (!ready) DialCaption("Saving…")
            }
            DialLink("‹ Back to dial", true, actions.session.close)
        }
    }
}

@Composable
private fun DialCaption(text: String) {
    if (text.isNotBlank())
        Text(
            text,
            style = DirectorText,
            color = P.ink3,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
        )
}

@Composable
private fun DialPrimary(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = P.task, contentColor = P.bg0),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(label, style = DirectorText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DialLink(label: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = P.ink2),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(label, style = DirectorText, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}
