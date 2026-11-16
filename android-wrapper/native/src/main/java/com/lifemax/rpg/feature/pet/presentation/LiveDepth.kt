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
import com.lifemax.rpg.feature.pet.room.LayerOffsets
import com.lifemax.rpg.feature.pet.room.LiveDepthTuning
import com.lifemax.rpg.feature.pet.room.RoomStyle

/**
 * The depth the live rooms (the Overview card, the Cat tab, the full-screen room) are drawn at, for
 * the whole app: the 2.5D room at [DepthLevel.RICH] wherever native cats are shown (no switch of
 * its own: it rides "Native Cats (beta)" and "Native Cat (beta)"), stepped down by the performance
 * guard ([DepthGuard]) when a phone cannot keep up, RICH then MEDIUM then SUBTLE then the classic
 * flat room, and back up after a long calm. The level is in memory only: it is never saved or
 * logged, and it starts at RICH on every launch.
 *
 * Read [level] in composition (it is snapshot state, so the rooms redraw in the new style); feed it
 * with [record] once a frame from the room that is running.
 */
internal object LiveDepth {
    private val guard = DepthGuard(LiveDepthTuning.BEST_LEVEL) { System.nanoTime() / NANOS_PER_MS }

    /** The level to draw at; null is the classic flat room. */
    var level: DepthLevel? by mutableStateOf(guard.level)
        private set

    /**
     * Whether the guard is at its best level: cat lighting is shed below it (CAT_LIGHTING_PLAN).
     */
    val atBest: Boolean
        get() = level == LiveDepthTuning.BEST_LEVEL

    /** The style of a live room at the current level ([reducedMotion]: the rest picture only). */
    fun style(reducedMotion: Boolean): RoomStyle =
        RoomStyle.live(level, reducedMotion, LiveDepthTuning.VIEW_ANGLE)

    /** The pixel offsets of the layers as the room is drawn now (the stage reports them). */
    @Volatile private var wallPx = 0
    @Volatile private var nearPx = 0

    /** A live stage's layers as drawn this frame: the wall's shift and the nearest row's. */
    fun reportOffsets(wall: Int, near: Int) {
        wallPx = wall
        nearPx = near
    }

    /**
     * What the phone is doing, for the Developer-mode readout of the Cat tab: the level in use, why
     * the guard chose it, its load, and the skew in pixels (never saved or logged).
     */
    fun readout(): String {
        val lv = level?.name ?: "flat (classic room)"
        val load = Math.round(guard.load * PERCENT)
        return "Depth $lv (best ${LiveDepthTuning.BEST_LEVEL.name}), ${guard.reason}, load $load%, " +
            "changes ${guard.changes}, skew ${LayerOffsets(wallPx, nearPx)}"
    }

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
    private const val PERCENT = 100
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
