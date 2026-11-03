package com.lifemax.rpg.feature.pet.brain

import com.lifemax.rpg.feature.pet.world.FakeTimeSource
import com.lifemax.rpg.feature.pet.world.WorldClock
import java.util.Locale

/**
 * The day viewer (plan 4.1): runs a [Brain] through a simulated day on a fake clock, at the world's
 * own sub-steps, and prints what each cat did: one line per activity change (`07:12 Ted
 * sleep→waking routine bed (energy .91, why: wake hour)`), a strip of 24 columns per cat (each
 * hour's main activity as a letter, [LETTERS]), and an SVG timeline. Humans judge believability
 * from it before phone time. Test scope only; CatDayViewerTest runs it.
 */
object CatDayViewer {
    /** A simulated day: the change [lines], one [strips] row per cat, and the [svg] timeline. */
    data class Day(val lines: List<String>, val strips: List<String>, val svg: String) {
        /** Everything as text, as the viewer prints it. */
        val text: String
            get() = (lines + "" + strips).joinToString("\n") + "\n"
    }

    /** Hour-strip letters per activity; anything else is '.', a cat that changed a lot '*'. */
    val LETTERS =
        mapOf(
            "sleep" to 'S',
            "loaf" to 'L',
            "solo toy play" to 'P',
            "owner play" to 'P',
            "window watch" to 'W',
            "parkour visit" to 'K',
            "eat" to 'E',
            "groom" to 'G',
            "zoomies" to 'Z',
            "glass visit" to '♥',
            "comfort sit" to '♥',
            "pair: curl up" to '=',
            "pair: groom" to '=',
            "pair: chase" to '=',
        )

    private const val MINUTES = 60
    private const val MS_PER_MINUTE = 60_000L
    private const val NANOS_PER_MS = 1_000_000L
    private const val HOURS = 24
    private const val SVG_HOUR = 40
    private const val SVG_ROW = 28
    private const val SVG_LEFT = 60

    /**
     * Runs [brainOf] (given the day's clock) for [hours] from [startEpochMs] (a local midnight: the
     * lines are stamped from it), with frames [frameMs] long (the result must not depend on it),
     * and the owner's [acts] at their simulated times.
     */
    fun simulate(
        startEpochMs: Long,
        hours: Int = HOURS,
        frameMs: Long = 16,
        acts: List<OwnerAct> = emptyList(),
        brainOf: (WorldClock) -> Brain,
    ): Day {
        val src = FakeTimeSource(mono = 0, epoch = startEpochMs)
        val clock = WorldClock(src)
        val brain = brainOf(clock)
        val n = brain.names.size
        val minutes = hours * MINUTES
        val samples = Array(n) { arrayOfNulls<Doing>(minutes) }
        val lines = ArrayList<String>()
        val last = arrayOfNulls<Doing>(n)
        var pending = acts.sortedBy { it.atMs }
        var minute = 0
        val endNanos = hours * MINUTES * MS_PER_MINUTE * NANOS_PER_MS
        var mono = 0L
        fun sample(m: Int) {
            for (slot in 0 until n) {
                val d = brain.now(slot)
                samples[slot][m] = d
                if (d.activity != last[slot]?.activity) {
                    val from = last[slot]?.activity?.let { "$it→" } ?: ""
                    lines +=
                        "${hhmm(m * MS_PER_MINUTE)} ${brain.names[slot].padEnd(5)} $from${d.activity}  ${d.place}  " +
                            "(${level(d.level)}, why: ${d.why})"
                }
                last[slot] = d
            }
        }
        // everything happens on sub-steps, never on frames: the owner's acts as their time comes,
        // then
        // the brain's step, then a sample at each minute's start, so the frame rate cannot show
        val listener =
            WorldClock.SubStepListener { step, think ->
                val now = clock.simMs
                while (pending.isNotEmpty() && pending[0].atMs <= now) {
                    brain.owner(pending[0])
                    lines += "${hhmm(pending[0].atMs)} owner ${pending[0].kind}"
                    pending = pending.drop(1)
                }
                brain.step(step, think)
                if (minute < minutes && minute.toLong() * MS_PER_MINUTE <= now) sample(minute++)
            }
        while (minute < minutes && mono < endNanos) {
            mono = minOf(endNanos, mono + frameMs * NANOS_PER_MS)
            clock.advanceTo(mono, Int.MAX_VALUE, listener)
        }
        val strips =
            (0 until n).map { slot ->
                brain.names[slot].padEnd(5) + " |" + strip(samples[slot], hours) + "|"
            }
        return Day(lines, strips, svg(brain.names, samples, hours))
    }

    private fun hhmm(ms: Long): String {
        val m = ms / MS_PER_MINUTE
        return String.format(Locale.ROOT, "%02d:%02d", m / MINUTES, m % MINUTES)
    }

    private fun level(v: Double) =
        "level " + String.format(Locale.ROOT, "%.2f", v).removePrefix("0")

    /** Each hour's most common activity as its letter. */
    private fun strip(s: Array<Doing?>, hours: Int): String =
        (0 until hours).joinToString("") { h ->
            val hour = s.copyOfRange(h * MINUTES, (h + 1) * MINUTES).filterNotNull()
            val top = hour.groupingBy { it.activity }.eachCount().maxByOrNull { it.value }?.key
            (top?.let { LETTERS[it] } ?: '.').toString()
        }

    private fun svg(names: List<String>, s: Array<Array<Doing?>>, hours: Int): String {
        val w = SVG_LEFT + hours * SVG_HOUR
        val b = StringBuilder()
        b.append(
            "<svg xmlns='http://www.w3.org/2000/svg' width='$w' height='${(names.size + 1) * SVG_ROW}' font-family='monospace' font-size='11'>\n"
        )
        for (h in 0..hours) b.append(
            "<text x='${SVG_LEFT + h * SVG_HOUR}' y='${names.size * SVG_ROW + 16}'>${h % HOURS}</text>\n"
        )
        for ((slot, name) in names.withIndex()) {
            val y = slot * SVG_ROW
            b.append("<text x='4' y='${y + 18}'>$name</text>\n")
            var start = 0
            for (m in 1..s[slot].size) {
                val a = s[slot][start]?.activity
                if (m < s[slot].size && s[slot][m]?.activity == a) continue
                val x = SVG_LEFT + start * SVG_HOUR / MINUTES
                val width = maxOf(1, (m - start) * SVG_HOUR / MINUTES)
                b.append(
                    "<rect x='$x' y='${y + 4}' width='$width' height='${SVG_ROW - 8}' fill='${colour(a)}'><title>${a ?: "-"}</title></rect>\n"
                )
                start = m
            }
        }
        return b.append("</svg>\n").toString()
    }

    /** A stable colour per activity name (hue from its hash). */
    private fun colour(activity: String?): String =
        if (activity == null) "#eeeeee"
        else "hsl(${Math.floorMod(activity.hashCode(), 360)},55%,62%)"
}
