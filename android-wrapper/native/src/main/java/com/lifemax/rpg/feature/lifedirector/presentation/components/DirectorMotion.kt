package com.lifemax.rpg.feature.lifedirector.presentation.components

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.platform.LocalContext
import com.lifemax.rpg.feature.lifedirector.domain.FocusTimer
import kotlinx.coroutines.delay

/*
 * What moves on Life Director's screen: the pulse of live things and a running timer. Both stop
 * while the screen is hidden (their running flag) and while the app is in the background, where no
 * frames are drawn.
 */

/**
 * A pulse's phase, from 0 (at rest) to 1 and back over [periodMs] while [running]; 0 and still
 * otherwise, or when the phone is set to remove animations. Read it in a layer so a pulse redraws
 * only that layer.
 */
@Composable
fun rememberPulse(running: Boolean, periodMs: Int): State<Float> {
    val context = LocalContext.current
    // asked each time the pulse starts, so "Remove animations" applies from the next visit on
    val still = remember(running) { animationsOff(context) }
    if (!running || still) return remember { mutableFloatStateOf(0f) }
    return rememberInfiniteTransition("pulse")
        .animateFloat(
            0f,
            1f,
            infiniteRepeatable(
                tween(periodMs / 2, easing = FastOutSlowInEasing),
                RepeatMode.Reverse,
            ),
            "pulse",
        )
}

private fun animationsOff(context: Context) =
    runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
            )
        }
        .getOrDefault(1f) == 0f

/** The wall clock in epoch milliseconds; a screenshot provides a fixed one. */
val LocalWallClock = staticCompositionLocalOf<() -> Long> { System::currentTimeMillis }

/**
 * A running [timer]'s time as the web writes it, ticking each second while [running] and the timer
 * is not paused. Only [content] recomposes as it ticks.
 */
@Composable
fun TimerClock(timer: FocusTimer, running: Boolean, content: @Composable (String) -> Unit) {
    val now by rememberTickingClock(running && timer.paused == null)
    content(clockText(timer.elapsedMs(now)))
}

/** The wall clock, ticking each second while [running]; each tick waits for a frame. */
@Composable
private fun rememberTickingClock(running: Boolean): State<Long> {
    val clock = LocalWallClock.current
    val now = remember { mutableLongStateOf(clock()) }
    LaunchedEffect(running, clock) {
        while (running) {
            withFrameMillis { now.longValue = clock() }
            delay(1000 - now.longValue % 1000)
        }
    }
    return now
}

/** A running timer as the web writes it: 4:05, 1:02:09. */
fun clockText(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = s / 60 % 60
    val sec = (s % 60).toString().padStart(2, '0')
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:$sec" else "$m:$sec"
}
