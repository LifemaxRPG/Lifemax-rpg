package com.lifemax.rpg.feature.pet.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import com.lifemax.rpg.feature.pet.room.DepthGuard
import com.lifemax.rpg.feature.pet.room.DepthLevel
import com.lifemax.rpg.feature.pet.room.GuardTuning
import com.lifemax.rpg.feature.pet.room.RoomStyle

/**
 * The depth the live rooms (the Overview card, the Cat tab, the full-screen room) are drawn at, for
 * the whole app: the 2.5D room at [DepthLevel.MEDIUM] wherever native cats are shown (no switch of
 * its own: it rides "Native Cats (beta)" and "Native Cat (beta)"), stepped down by the performance
 * guard ([DepthGuard]) when a phone cannot keep up, MEDIUM then SUBTLE then the classic flat room,
 * and back up after a long calm. The level is in memory only: it is never saved or logged, and it
 * starts at MEDIUM on every launch.
 *
 * Read [level] in composition (it is snapshot state, so the rooms redraw in the new style); feed it
 * with [record] once a frame from the room that is running.
 */
internal object LiveDepth {
    private val guard = DepthGuard(DepthLevel.MEDIUM) { System.nanoTime() / NANOS_PER_MS }

    /** The level to draw at; null is the classic flat room. */
    var level: DepthLevel? by mutableStateOf(guard.level)
        private set

    /** The style of a live room at the current level ([reducedMotion]: the rest picture only). */
    fun style(reducedMotion: Boolean): RoomStyle = RoomStyle.live(level, reducedMotion)

    /**
     * One frame of a running live room: [workMs] of main-thread time (its step and drawing),
     * [intervalMs] since the last frame, on a display whose frame takes [budgetMs].
     */
    fun record(workMs: Float, intervalMs: Float, budgetMs: Float) {
        if (guard.record(workMs, intervalMs, budgetMs)) level = guard.level
    }

    /** A live room started or came back to the screen: its first frames are not counted. */
    fun resume() = guard.resume()

    private const val NANOS_PER_MS = 1_000_000L
}

/** The style of a live room now: the guard's level, or the rest picture with [reducedMotion]. */
@Composable
internal fun rememberLiveStyle(reducedMotion: Boolean): RoomStyle =
    remember(LiveDepth.level, reducedMotion) { LiveDepth.style(reducedMotion) }

/** The time one frame has on this display, in ms (16.7 at 60 Hz); 60 Hz if it is not known. */
@Composable
internal fun rememberFrameBudgetMs(): Float {
    val view = LocalView.current
    return remember(view) {
        val hz = view.display?.refreshRate ?: 0f
        if (hz >= MIN_REFRESH_HZ) 1000f / hz else GuardTuning.DEFAULT_BUDGET_MS
    }
}

private const val MIN_REFRESH_HZ = 24f
