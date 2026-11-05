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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

/** A focused control face; secondary actions never crowd the timer or primary action. */
@Composable
internal fun DialActivityControls(
    model: SheetModel,
    ui: DirectorUi,
    actions: DirectorActions,
    ticking: Boolean,
    reducedMotion: Boolean,
) {
    var page by rememberSaveable(model.kind, model.id) { mutableStateOf("main") }
    val ready = ui.pendingToday == null
    AnimatedContent(
        targetState = model to page,
        modifier = Modifier.fillMaxSize(),
        contentKey = { (m, p) -> Triple(m.kind, m.id, p) },
        transitionSpec = {
            fadeIn(tween(if (reducedMotion) 0 else 180)) togetherWith
                fadeOut(tween(if (reducedMotion) 0 else 120))
        },
        label = "dial controls",
    ) { (current, face) ->
        Column(
            Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            val title =
                when (current) {
                    is MoveSheetModel -> current.title
                    is SessionSheetModel -> current.title
                    is TimerSheetModel -> current.title
                    else -> ""
                }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 40.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { if (face == "main") actions.session.close() else page = "main" },
                    modifier = Modifier.size(40.dp).semantics { contentDescription = "Back" },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text("‹", color = P.ink2, fontSize = 22.sp)
                }
                Text(
                    title,
                    Modifier.weight(1f),
                    style = DirectorText,
                    color = P.ink,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                when (current) {
                    is SessionSheetModel -> {
                        val draft =
                            ui.minutesDraft?.takeIf { it.id == current.id }?.text
                                ?: current.minutes?.value?.toString().orEmpty()
                        when (face) {
                            "log" -> {
                                DialCaption(current.minutes?.label.orEmpty())
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    DialLink("−5", ready) { actions.session.step(-5) }
                                    Text(
                                        draft,
                                        color = P.ink,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    DialLink("+5", ready) { actions.session.step(5) }
                                }
                                current.complete?.let {
                                    DialPrimary(it.label, ready, actions.session.complete)
                                }
                            }
                            "more" -> {
                                DialCaption(current.`when`)
                                current.row.forEach { button ->
                                    DialLink(button.label, ready) { actions.session.row(button) }
                                }
                                DialCaption(current.note.joinToString("") { it.t })
                                DialLink("Why this time?", true) { page = "why" }
                                DialCaption(current.orphan)
                            }
                            "why" -> {
                                DialCaption(current.why.summary)
                            }
                            else -> {
                                current.primary?.let {
                                    DialPrimary(it.label, ready, actions.session.primary)
                                }
                                Row {
                                    if (current.complete != null)
                                        DialLink("Log time", ready) { page = "log" }
                                    DialLink("More", ready) { page = "more" }
                                }
                            }
                        }
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
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        DialCaption(current.planned)
                        if (face == "more") {
                            DialLink(current.stop, ready, actions.timerMeal.stop)
                            current.xp?.let { spans ->
                                DialCaption(spans.joinToString("") { it.t })
                            }
                        } else {
                            DialPrimary(current.finish, ready, actions.timerMeal.finish)
                            Row {
                                DialLink(current.pause, ready, actions.timerMeal.pause)
                                DialLink("More", ready) { page = "more" }
                            }
                        }
                    }
                    else -> {}
                }
                if (!ready) DialCaption("Saving…")
            }
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
