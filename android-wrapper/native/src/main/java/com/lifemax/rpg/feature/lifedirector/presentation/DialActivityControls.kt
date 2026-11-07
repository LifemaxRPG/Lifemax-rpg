package com.lifemax.rpg.feature.lifedirector.presentation

import android.text.Html
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.feature.lifedirector.domain.MealSheetModel
import com.lifemax.rpg.feature.lifedirector.domain.MoveSheetModel
import com.lifemax.rpg.feature.lifedirector.domain.SessionSheetModel
import com.lifemax.rpg.feature.lifedirector.domain.SheetModel
import com.lifemax.rpg.feature.lifedirector.domain.TimerSheetModel
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorPalette as P
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorText
import com.lifemax.rpg.feature.lifedirector.presentation.components.clockText
import com.lifemax.rpg.feature.lifedirector.presentation.components.rememberTickingClock
import com.lifemax.rpg.feature.lifedirector.presentation.sheets.TimerResetButton

/** One continuous activity face: full title and schedule first, with no intermediate menus. */
@OptIn(ExperimentalFoundationApi::class)
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
        modifier =
            Modifier.fillMaxSize()
                .pointerInput(model.kind, model.id) {
                    awaitEachGesture {
                        val down =
                            awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Initial,
                            )
                        var claimed = false
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pointer = event.changes.firstOrNull { it.id == down.id }
                            if (
                                pointer != null &&
                                    (pointer.position - down.position).getDistance() >
                                        viewConfiguration.touchSlop
                            )
                                claimed = true
                            if (claimed) event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                    onLongClickLabel =
                        if (model is MealSheetModel) "Open meal menu" else "Open workout or quest",
                    onLongClick = {
                        if (model is MealSheetModel) actions.timerMeal.openMeal()
                        else actions.editActivity(model.id)
                    },
                )
                .semantics {
                    customActions =
                        listOf(
                            CustomAccessibilityAction("Close activity") {
                                actions.session.close()
                                true
                            }
                        )
                },
        contentKey = { it.kind to it.id },
        transitionSpec = {
            fadeIn(tween(if (reducedMotion) 0 else 180)) togetherWith
                fadeOut(tween(if (reducedMotion) 0 else 120))
        },
        label = "dial controls",
    ) { current ->
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxHeight < 180.dp
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp),
                ) {
                    val title =
                        when (current) {
                            is MealSheetModel -> current.title
                            is MoveSheetModel -> current.title
                            is SessionSheetModel -> current.title
                            is TimerSheetModel -> current.title
                            else -> ""
                        }
                    Text(
                        Html.fromHtml(title, Html.FROM_HTML_MODE_LEGACY).toString(),
                        style = DirectorText,
                        color = P.ink,
                        fontSize = if (compact) 10.sp else 12.sp,
                        lineHeight = if (compact) 12.sp else 14.sp,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(.82f),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    when (current) {
                        is MealSheetModel -> {
                            DialCaption(current.`when`, maxLines = 2)
                            DialCaption(current.chips.joinToString(" · ") { it.text }, maxLines = 1)
                            DialPrimary(current.open, ready, actions.timerMeal.openMeal)
                        }
                        is SessionSheetModel -> {
                            DialCaption(current.`when`, maxLines = 2)
                            current.primary?.let {
                                DialPrimary(it.label, ready, actions.session.primary)
                            }
                            DialCaption(current.description, maxLines = 4)
                            DialCaption(current.orphan, maxLines = 2)
                        }
                        is MoveSheetModel -> {
                            DialCaption(current.note)
                            if (current.times.isEmpty()) DialCaption(current.none)
                            var timePage by
                                remember(current.id, current.times) { mutableIntStateOf(0) }
                            current.times.getOrNull(timePage)?.let { time ->
                                DialLink(time.label, ready) { actions.moves.moveTo(time) }
                            }
                            if (current.times.size > 1)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    DialLink("‹", ready && timePage > 0) { timePage-- }
                                    Text(
                                        "${timePage + 1}/${current.times.size}",
                                        color = P.ink3,
                                        fontSize = 10.sp,
                                    )
                                    DialLink("›", ready && timePage < current.times.lastIndex) {
                                        timePage++
                                    }
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                DialLink(current.pause, ready, actions.timerMeal.pause)
                                TimerResetButton(ready, actions.timerMeal.reset)
                            }
                            current.xp?.let { spans ->
                                DialCaption(spans.joinToString("") { it.t }, maxLines = 1)
                            }
                        }
                        else -> {}
                    }
                    if (!ready) DialCaption("Saving…")
                }
            }
        }
    }
}

@Composable
private fun DialCaption(text: String, maxLines: Int = 2) {
    if (text.isNotBlank())
        Text(
            Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString(),
            style = DirectorText,
            color = P.ink3,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(.9f),
            textAlign = TextAlign.Center,
        )
}

@Composable
private fun DialPrimary(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp, max = 48.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = P.task, contentColor = P.bg0),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(label, style = DirectorText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
