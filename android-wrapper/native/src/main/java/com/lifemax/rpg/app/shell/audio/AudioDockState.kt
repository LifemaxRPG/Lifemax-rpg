package com.lifemax.rpg.app.shell.audio

import com.lifemax.rpg.core.data.pageTokens
import org.json.JSONObject

/**
 * The page's player as the dock draws it (`NativeAudio.state`, v1). The page pushes it on every
 * play, pause, seek, speed or file change and every ten seconds while the book plays; between
 * pushes the dock extrapolates [position] from [atMs] ([positionAt]), so nothing is pushed per
 * second. The page stays the player: this holds nothing the page does not say.
 */
data class AudioDockState(
    val visible: Boolean = false,
    val minimized: Boolean = false,
    val loading: Boolean = false,
    val mediaId: String = "",
    val title: String = "",
    val sub: String = "",
    val playing: Boolean = false,
    /** seconds into the book when the state was taken, and the book's length (0 while unknown) */
    val position: Double = 0.0,
    val duration: Double = 0.0,
    val rate: Double = 1.0,
    /** the speeds the page offers, in its order */
    val rates: List<Double> = listOf(1.0),
    val tokens: Map<String, Long> = emptyMap(),
    val light: Boolean = false,
    /** the clock reading ([positionAt]'s) when [position] was true */
    val atMs: Long = 0,
) {
    /** Where the book is at [nowMs]: the page's position plus the time played since, never past the end. */
    fun positionAt(nowMs: Long): Double {
        val played = if (playing) (nowMs - atMs).coerceAtLeast(0) / 1000.0 * rate else 0.0
        val at = (position + played).coerceAtLeast(0.0)
        return if (duration > 0) at.coerceAtMost(duration) else at
    }

    /** How far through the book, 0..1 (0 while the length is unknown). */
    fun fractionAt(nowMs: Long): Float =
        if (duration > 0) (positionAt(nowMs) / duration).toFloat().coerceIn(0f, 1f) else 0f

    /**
     * Whether the once-a-second tick runs: only while the seek bar is on screen and moving. A
     * minimised dock, a paused book and a hidden dock draw nothing that changes.
     */
    fun ticks(onScreen: Boolean) = onScreen && visible && playing && !minimized && !loading

    /** The speed after the current one, in the page's order, round to the first. */
    fun nextRate(): Double {
        val i = rates.indexOfFirst { it == rate }
        return rates[(i + 1) % rates.size]
    }

    /** What the dock takes from the screen's height: nothing when it is not showing. */
    val heightDp: Float
        get() = if (!visible) 0f else AudioDockMetrics.heightDp(minimized)

    companion object {
        /**
         * Decodes a push. A key missing or of the wrong kind throws, and so does a version this
         * build does not know; the colours matter only while the dock shows.
         */
        fun decode(j: JSONObject, nowMs: Long): AudioDockState {
            require(j.getInt("v") == 1) { "Unsupported audio state version" }
            val visible = j.getBoolean("visible")
            val rates = j.getJSONArray("rates")
            require(rates.length() > 0) { "No speeds" }
            val state =
                AudioDockState(
                    visible,
                    j.getBoolean("minimized"),
                    j.getBoolean("loading"),
                    j.getString("mediaId"),
                    j.getString("title"),
                    j.getString("sub"),
                    j.getBoolean("playing"),
                    j.getDouble("position"),
                    j.getDouble("duration"),
                    j.getDouble("rate"),
                    (0 until rates.length()).map { rates.getDouble(it) },
                    if (visible) j.getJSONObject("tokens").pageTokens() else emptyMap(),
                    j.getBoolean("light"),
                    nowMs,
                )
            require(state.position >= 0 && state.duration >= 0 && state.rate > 0) {
                "Audio position out of range"
            }
            return state
        }
    }
}

/**
 * The dock's size, fixed so the insets it pushes to the screens are known before it is drawn: the
 * screens' content must clear it the moment it shows.
 */
object AudioDockMetrics {
    const val EXPANDED_DP = 144f
    const val MINIMIZED_DP = 60f

    /** the gap above the bars, which the dock's height includes */
    const val GAP_DP = 8f

    fun heightDp(minimized: Boolean) = (if (minimized) MINIMIZED_DP else EXPANDED_DP) + GAP_DP
}

/** A time as the page words it (fmtAudioTime): m:ss, or h:mm:ss from an hour. */
fun audioClock(seconds: Double): String {
    val s = seconds.coerceAtLeast(0.0).toLong()
    val h = s / 3600
    val m = s % 3600 / 60
    val r = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, r) else "%d:%02d".format(m, r)
}
