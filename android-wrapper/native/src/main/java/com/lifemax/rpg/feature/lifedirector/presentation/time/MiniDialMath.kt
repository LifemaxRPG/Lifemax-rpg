package com.lifemax.rpg.feature.lifedirector.presentation.time

import kotlin.math.floor

/**
 * My time's small sleep dial, as the page has it (miniDialHtml and attachMini in logic.js), from
 * the projection's time.sleep: the saved [bed] and [wake] (minutes after midnight) with the page's
 * texts for them and for the [length] between; the page's snap [step]; the work arcs drawn faintly
 * behind ([context], minutes); and the page's words for every stop: [clock] holds fHm of each stop
 * of the day (stop k is k × step minutes) and [lengths] the page's mins of each length (entry k is
 * k × step minutes, entry 0 the whole day, as the page counts a bedtime equal to the wake time).
 * [words] are the dial's own labels.
 */
data class MiniDial(
    val bed: Int,
    val wake: Int,
    val bedText: String,
    val wakeText: String,
    val length: String,
    val step: Int,
    val context: List<MinuteSpan>,
    val clock: List<String>,
    val lengths: List<String>,
    val words: MiniDialWords,
)

/** A span of the day in minutes after midnight; [to] may pass midnight (up to 2879). */
data class MinuteSpan(val from: Int, val to: Int)

/**
 * The page's words on the dial: "Sleep" over the length, the handles' "Bedtime" and "Wake up",
 * "Sleep window".
 */
data class MiniDialWords(val kick: String, val bed: String, val wake: String, val group: String)

/** What the centre and the handles say for a bedtime and a wake time. */
data class MiniDialTexts(val bed: String, val wake: String, val length: String)

/**
 * The mini dial's arithmetic, the page's: a minute is a quarter of a degree, midnight at the top; a
 * held handle lands on the stop nearest the finger (Math.round(a * 4 / 15) * 15 % 1440); the sleep
 * arc runs clockwise from the bedtime to the wake time, past midnight when the wake time is not
 * later.
 */
object MiniDialMath {
    const val DAY = 1440

    fun angle(minutes: Int): Float = minutes / 4f

    /**
     * The sleep arc's angles: from the bedtime round to the wake time, the end past 360 when it
     * crosses midnight.
     */
    fun arc(bed: Int, wake: Int): Pair<Float, Float> =
        angle(bed) to angle(if (wake <= bed) wake + DAY else wake)

    /** The stop a finger at [angle] picks, rounding half up as JavaScript's Math.round does. */
    fun snap(angle: Float, step: Int): Int =
        (floor(angle * 4.0 / step + .5).toInt() * step).mod(DAY)

    /** Minutes asleep: the page's ((wake - bed) + 1440) % 1440 || 1440. */
    fun length(bed: Int, wake: Int): Int = (wake - bed).mod(DAY).let { if (it == 0) DAY else it }

    /**
     * One stop later ([by] 1) or earlier (-1) round the clock, as the web handle's arrow keys move
     * it; a minute between stops moves to the next stop that way.
     */
    fun stepped(minutes: Int, by: Int, step: Int): Int {
        val k = if (by > 0) minutes.floorDiv(step) + 1 else -(-minutes).floorDiv(step) - 1
        return (k * step).mod(DAY)
    }

    /**
     * The words for [bed] and [wake]: the page's own for each stop, or the page's texts for the
     * saved times. A minute between stops that is not the saved one (only a time typed in a field
     * can be) reads as its nearest stop, the page sending no words for it.
     */
    fun texts(dial: MiniDial, bed: Int, wake: Int): MiniDialTexts {
        fun clock(m: Int, saved: Int, text: String) =
            if (m % dial.step != 0 && m == saved) text
            else dial.clock[nearest(m, dial.step, dial.clock.size)]
        val len = length(bed, wake)
        val savedLen = bed == dial.bed && wake == dial.wake
        return MiniDialTexts(
            clock(bed, dial.bed, dial.bedText),
            clock(wake, dial.wake, dial.wakeText),
            if (savedLen && (bed % dial.step != 0 || wake % dial.step != 0)) dial.length
            else dial.lengths[nearest(len, dial.step, dial.lengths.size)],
        )
    }

    private fun nearest(minutes: Int, step: Int, size: Int): Int =
        floor(minutes.toDouble() / step + .5).toInt().mod(size)
}
