package com.lifemax.rpg.feature.lifedirector.presentation.dial

import androidx.compose.ui.geometry.Offset
import com.lifemax.rpg.feature.lifedirector.domain.DialHandle
import com.lifemax.rpg.feature.lifedirector.domain.DialStop
import com.lifemax.rpg.feature.lifedirector.domain.DragWords
import com.lifemax.rpg.feature.lifedirector.domain.HandleEnd
import kotlin.math.abs

/**
 * What a held handle does, looked up in the page's data (attachDial, dragOn and dialAngle in
 * logic.js): which stop the finger picks, which handle a touch takes, the arc the held handle spans
 * and what the centre says. No rule is copied: the page has said where a handle can land and what
 * every place means.
 */
object DragGeometry {
    /** The web handle's hit circle, in units: 48 across, as Android asks of a touch target. */
    const val HIT_RADIUS = 24f

    /** The stop the finger picks at [angle]: the last whose [DialStop.from] is at or before it. */
    fun stopAt(stops: List<DialStop>, angle: Float): Int {
        var lo = 0
        var hi = stops.lastIndex
        var found = 0
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (stops[mid].from <= angle) {
                found = mid
                lo = mid + 1
            } else hi = mid - 1
        }
        return found
    }

    /**
     * The stop [handle] sits on: the one with its minute (the nearest by angle when a day whose
     * clocks go back has it twice), or the nearest by angle when its minute is not a stop.
     */
    fun stopOf(handle: DialHandle, stops: List<DialStop>): Int {
        val same = stops.indices.filter { stops[it].m == handle.minute }
        return (same.ifEmpty { stops.indices.toList() }).minByOrNull { abs(stops[it].a - handle.a) }
            ?: 0
    }

    /**
     * The handle a touch at [p] (units) takes: the nearest whose centre, on ring [ring], is within
     * [radius] units of it.
     */
    fun handleAt(handles: List<DialHandle>, ring: Float, p: Offset, radius: Float): DialHandle? =
        handles
            .map { it to (DialGeometry.point(ring, it.a) - p).getDistance() }
            .filter { (_, d) -> d <= radius }
            .minByOrNull { (_, d) -> d }
            ?.first

    /**
     * The arc [handle] spans held at stop [i]: from the stop to where the page says the span ends,
     * or from there to the stop for an end handle. Null when the span would be too short (the page
     * gives no length) or nothing of it is on the dial.
     */
    fun span(handle: DialHandle, stops: List<DialStop>, i: Int): Pair<Float, Float>? {
        if (handle.lengths[i].isEmpty()) return null
        val at = stops[i].a
        val a0 = if (handle.end == HandleEnd.START) at else handle.ends[i]
        val a1 = if (handle.end == HandleEnd.END) at else handle.ends[i]
        return if (a1 > a0) a0 to a1 else null
    }

    /** What the centre says with [handle] held at stop [i]. */
    fun preview(handle: DialHandle, stops: List<DialStop>, i: Int, words: DragWords) =
        DragPreview(
            handle.label,
            stops[i].time,
            handle.lengths[i].ifEmpty { words.tooShort },
            words.release,
        )
}

/**
 * The centre while a handle is held: its label, the time, the length (or too short), how to apply.
 */
data class DragPreview(val kick: String, val time: String, val length: String, val release: String)
