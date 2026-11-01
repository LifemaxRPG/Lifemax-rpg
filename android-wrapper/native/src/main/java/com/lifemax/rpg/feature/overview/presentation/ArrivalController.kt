package com.lifemax.rpg.feature.overview.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue

/**
 * Which of the page's arrivals the cards play (plan §4 phase 3). The page raises the projection's
 * `arrival` only when the owner really arrives at the Overview, never when the screen comes back
 * after a sheet or a celebration; each new value is played once:
 * - only while the screen shows: an arrival first seen while hidden is used up, not kept for later;
 * - not with reduced motion: it is used up too, and the cards are drawn as they end;
 * - 0 (a page before phase 3, or no arrival yet) never plays.
 *
 * [playing] is the arrival being played (0 when none); the screen calls [played] when it has ended.
 */
class ArrivalController {
    var playing by mutableLongStateOf(0L)
        private set

    private var seen: Pair<String, Long>? = null

    /** A projection from page session [session] with its [arrival], [reduced] motion or not. */
    fun projected(session: String, arrival: Long, showing: Boolean, reduced: Boolean) {
        if (arrival == 0L) return
        val key = session to arrival
        if (key == seen) return
        seen = key
        playing = if (showing && !reduced) arrival else 0L
    }

    /** The arrival [key] has played to its end (or could not play: animations are off). */
    fun played(key: Long) {
        if (playing == key) playing = 0L
    }

    /** The screen went behind the page: an arrival cut short is not played again. */
    fun hidden() {
        playing = 0L
    }
}
