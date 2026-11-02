package com.lifemax.rpg.feature.pet.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.lifemax.rpg.feature.lifedirector.presentation.components.animatorScale
import com.lifemax.rpg.feature.pet.body.Autopilot
import com.lifemax.rpg.feature.pet.body.CatLife
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A live cat in a strip of room: a [CatLife] stepped once per display frame and drawn by
 * [drawCatStage].
 *
 * Time. One coroutine waits for each frame ([withFrameNanos]) and steps the cat by the real time
 * since the last one, so the motion is the same at 60, 90 or 120 Hz. It runs only while [running]
 * and the window's lifecycle is at least resumed; otherwise no frame is requested at all, so a
 * hidden or stopped screen costs nothing, and the cat picks up where it was.
 *
 * Stillness. With [still] (the page's reduced motion, or the phone's "remove animations") the cat
 * is drawn once, sitting, and the clock never runs.
 *
 * Only the canvas redraws on a frame (the counter is read in the draw phase), so nothing recomposes
 * while the cat moves.
 */
@Composable
internal fun CatLiveView(
    life: CatLife,
    sprites: CatSprites,
    colors: CatStageColors,
    running: Boolean,
    still: Boolean,
    modifier: Modifier = Modifier,
) {
    val resumed = rememberResumed()
    val frame = remember { mutableLongStateOf(0L) }
    val animate = running && resumed && !still
    LaunchedEffect(life, animate) {
        if (!animate) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                life.step((now - last) / NANOS_PER_SECOND)
                last = now
                frame.longValue = now
            }
        }
    }
    Canvas(modifier) {
        frame.longValue // redraw on every step of the clock
        drawCatStage(life.pose, sprites, colors)
    }
}

/** True while the window's lifecycle is at least resumed (the app is in front). */
@Composable
private fun rememberResumed(): Boolean {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycle.currentState.isAtLeast(RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return resumed
}

/**
 * The cat for a card, built from what the page said about it: its sprites (loaded off the main
 * thread; null while loading, and for good if the art cannot be loaded) and a [CatLife] seeded by
 * the cat's [name] and coat, so it keeps its own character from visit to visit.
 */
@Composable
internal fun rememberCatSprites(coat: String, stage: Int): CatSprites? {
    // A page coat's sheets arrive one by one ([CatArt.revision] changes with each): the sprites are
    // asked again, and the cat already on screen stays until better ones (with the depth rows)
    // come.
    val sprites = remember(coat, stage) { mutableStateOf<CatSprites?>(null) }
    val revision = CatArt.revision
    LaunchedEffect(coat, stage, revision) {
        val found = withContext(Dispatchers.Default) { CatArt.sprites(coat, stage) }
        if (found != null) sprites.value = found
    }
    return sprites.value
}

/** A cat's life for a card: the same cat for the same [name], [coat] and hunger. */
@Composable
internal fun rememberCatLife(name: String, coat: String, weak: Boolean): CatLife =
    remember(name, coat, weak) { CatLife(Autopilot.seedFor(name, coat), weak) }

/** Whether the phone is set to remove animations. */
@Composable
internal fun rememberAnimationsOff(): Boolean {
    val context = LocalContext.current
    return remember { animatorScale(context) == 0f }
}

private val RESUMED = Lifecycle.State.RESUMED
private const val NANOS_PER_SECOND = 1_000_000_000f
