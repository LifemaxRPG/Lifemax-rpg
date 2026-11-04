package com.lifemax.rpg.feature.lifedirector.presentation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.feature.lifedirector.domain.SessionSheetModel
import com.lifemax.rpg.feature.lifedirector.domain.SheetModel
import com.lifemax.rpg.feature.lifedirector.domain.TimerSheetModel
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorPalette
import com.lifemax.rpg.feature.lifedirector.presentation.components.clockText
import com.lifemax.rpg.feature.lifedirector.presentation.components.rememberTickingClock

/** Activity controls share the dial's centre and the existing authoritative commands. */
@Composable
internal fun DialActivityControls(
    model: SheetModel,
    ui: DirectorUi,
    actions: DirectorActions,
    ticking: Boolean,
    reducedMotion: Boolean,
) {
    Crossfade(
        targetState = model,
        animationSpec = tween(if (reducedMotion) 0 else 220),
        contentKey = { it.kind + it.id },
        label = "dial activity",
    ) { current ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val close = actions.session.close
            DialControl("Back to dial", true, close)
            when (current) {
                is SessionSheetModel -> {
                    Text(
                        current.title,
                        color = DirectorPalette.ink,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    val draft =
                        ui.minutesDraft?.takeIf { it.id == current.id }?.text
                            ?: current.minutes?.value?.toString().orEmpty()
                    val ready = ui.pendingToday == null
                    current.primary?.let { DialControl(it.label, ready, actions.session.primary) }
                    if (current.minutes != null && current.complete != null) {
                        Text(
                            current.minutes.label,
                            color = DirectorPalette.ink3,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DialControl("−", ready) { actions.session.step(-1) }
                            Text(draft, color = DirectorPalette.ink, fontSize = 18.sp)
                            DialControl("+", ready) { actions.session.step(1) }
                        }
                        DialControl(current.complete.label, ready, actions.session.complete)
                    }
                    current.row.forEach { button ->
                        DialControl(button.label, ready) { actions.session.row(button) }
                    }
                }
                is TimerSheetModel -> {
                    val now by rememberTickingClock(ticking && !current.paused)
                    Text(
                        current.title,
                        color = DirectorPalette.ink,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        clockText(current.timer.elapsedMs(now)),
                        color = DirectorPalette.ink,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(current.planned, color = DirectorPalette.ink3, fontSize = 11.sp)
                    val ready = ui.pendingToday == null
                    DialControl(current.pause, ready, actions.timerMeal.pause)
                    DialControl(current.finish, ready, actions.timerMeal.finish)
                    DialControl(current.stop, ready, actions.timerMeal.stop)
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun DialControl(label: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled) {
        Text(label, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}
