package com.lifemax.rpg.core.lifedirector.readmodels

import com.lifemax.rpg.core.lifedirector.CalendarDate
import com.lifemax.rpg.core.lifedirector.CalendarMath
import com.lifemax.rpg.core.lifedirector.PageRangeError
import com.lifemax.rpg.core.lifedirector.PageValues
import com.lifemax.rpg.core.lifedirector.PageValues.number
import com.lifemax.rpg.core.lifedirector.PageValues.text
import com.lifemax.rpg.core.lifedirector.PageValues.truthy
import com.lifemax.rpg.core.lifedirector.PageZone
import com.lifemax.rpg.core.lifedirector.PlanDay
import com.lifemax.rpg.core.lifedirector.WallClock
import com.lifemax.rpg.core.lifedirector.readmodels.Js.get
import com.lifemax.rpg.core.lifedirector.readmodels.Js.n
import com.lifemax.rpg.core.lifedirector.readmodels.Js.obj
import com.lifemax.rpg.core.lifedirector.readmodels.Js.s
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import kotlin.math.ceil

/** A place a handle can land on the day (RM-39): minute [m], where it sits, where it is chosen. */
data class DialStop(
    val m: Double,
    val ep: Double,
    val a: Double,
    val from: Double,
    val time: String,
)

/**
 * A handle on the day's dial (RM-38, RM-40): which end of which rule, where it is, its readout
 * label and TalkBack label, and for every stop the length the readout shows (`''`: too short) and
 * the angle where the lit arc ends.
 */
data class DialHandle(
    val key: String,
    val rule: String,
    val end: String,
    val kind: String,
    val a: Double,
    val minute: Double,
    val label: String,
    val aria: String,
    val lengths: List<String>,
    val ends: List<Double>,
)

/** A day's handles and stops as Today's dial carries them (`nativeHandles`, RM-40). */
data class DayHandles(
    val startEpoch: Double,
    val endEpoch: Double,
    val handles: List<DialHandle>,
    val stops: List<DialStop>,
    val dragWords: JsonValue,
) {
    fun json(): JsonValue =
        obj(
            "startEpoch" to n(startEpoch),
            "endEpoch" to n(endEpoch),
            "handles" to
                Js.list(
                    handles.map {
                        obj(
                            "key" to s(it.key),
                            "rule" to s(it.rule),
                            "end" to s(it.end),
                            "kind" to s(it.kind),
                            "a" to n(it.a),
                            "minute" to n(it.minute),
                            "label" to s(it.label),
                            "aria" to s(it.aria),
                            "lengths" to Js.list(it.lengths.map(::s)),
                            "ends" to Js.list(it.ends.map(::n)),
                        )
                    }
                ),
            "stops" to
                Js.list(
                    stops.map {
                        obj(
                            "m" to n(it.m),
                            "a" to n(it.a),
                            "from" to n(it.from),
                            "time" to s(it.time),
                        )
                    }
                ),
            "dragWords" to dragWords,
        )
}

/**
 * RM-38 to RM-40: `ruleInstances`, `dialHandles`, `snapEp`, `dialStops`, `dragPreview`,
 * `dialChange`.
 */
internal object Dial {
    private val SLEEP = JsonString("SLEEP")
    private const val START = "start"
    private const val END = "end"
    private const val DAY = 1440.0
    private const val DAY_SECONDS = 86400.0
    private const val QUARTER = 15.0
    private const val LAST_STOP = 1425.0
    private const val QUARTERS = 95
    private const val SNAP_AT = 7
    private const val MINUTE = 60.0
    private const val FULL = 360.0

    /** A rule's occurrence that touches the day: its start and end, and the date it starts on. */
    private class Occurrence(
        val rule: JsonValue,
        val start: Double,
        val end: Double,
        val date: CalendarDate,
    )

    private class Handle(val occ: Occurrence, val end: String, val ep: Double, val key: String)

    fun handles(p: DirectorPanels, day: PlanDay): DayHandles {
        val d0 = day.startEpoch
        val d1 = day.endEpoch
        val list = dialHandles(p, day)
        if (list.isEmpty()) return DayHandles(d0, d1, emptyList(), emptyList(), p.words.dragWords)
        val zone = p.zone()
        val stops = stops(p, d0, d1, zone)
        val space = p.words.phrase("nativeHandles", "space")
        val out =
            list.map { h ->
                val r = h.occ.rule
                val label = label(p, r, h.end)
                val om = minuteOf(h.ep, zone)
                val held = stops.map { preview(p, r, h.end, om, d0, it, zone) }
                DialHandle(
                    key = h.key,
                    rule = text(get(r, "id")),
                    end = h.end,
                    kind = if (get(r, "kind") == SLEEP) "sleep" else "work",
                    a = angle(h.ep, d0, d1),
                    minute = om,
                    label = label,
                    aria = label + space + p.clock.time(h.ep, zone),
                    lengths = held.map { if (it.bad) "" else it.text },
                    ends = held.map { angle(if (h.end == START) it.ne else it.ns, d0, d1) },
                )
            }
        return DayHandles(d0, d1, out, stops, p.words.dragWords)
    }

    /** `angOf(ep, d0, d1)`: the angle of [ep], kept on the day. */
    fun angle(ep: Double, d0: Double, d1: Double): Double =
        (Math.max(d0, Math.min(d1, ep)) - d0) / Math.max(1.0, d1 - d0) * FULL

    private fun minuteOf(ep: Double, zone: PageZone): Double {
        val q = parts(ep, zone)
        return q.hour * MINUTE + q.minute
    }

    private fun parts(ep: Double, zone: PageZone) =
        try {
            WallClock.localParts(ep, zone)
        } catch (e: IllegalArgumentException) {
            throw PageRangeError("Invalid time value")
        }

    /** `handleLabel(r, end)`: Bedtime and Wake up for sleep, else `<title> starts`/`ends`. */
    private fun label(p: DirectorPanels, r: JsonValue, end: String): String {
        fun ph(k: String) = p.words.phrase("handleLabel", k)
        if (get(r, "kind") == SLEEP) return if (end == START) ph("bedtime") else ph("wakeUp")
        val title = get(r, "title")
        val name =
            if (truthy(title)) text(title)
            else p.words.kindLabel[text(get(r, "kind"))] ?: ph("commitment")
        return name + if (end == START) ph("starts") else ph("ends")
    }

    /** RM-38 `ruleInstances(day)`: every drag rule's occurrence from the day before or the day. */
    private fun occurrences(p: DirectorPanels, day: PlanDay): List<Occurrence> {
        val zone = p.zone()
        val out = ArrayList<Occurrence>()
        val before =
            CalendarMath.plusDays(day.date, -1) ?: throw PageRangeError("Invalid time value")
        for (r in MyTime.rules(p)) {
            if (text(get(r, "kind")) !in p.words.dragKinds) continue
            for (date in listOf(before, day.date)) {
                if (!Shifts.ruleOn(r, date)) continue
                val start = get(r, "startMin")
                val s = Shifts.zoned(date, start, zone)
                val e = Shifts.zoned(date, Js.add(start, get(r, "durationMin")), zone)
                if (e <= day.startEpoch || s >= day.endEpoch) continue
                out += Occurrence(r, s, e, date)
            }
        }
        return out
    }

    /** RM-38 `dialHandles(day)`: each end of an occurrence that falls on the day. */
    private fun dialHandles(p: DirectorPanels, day: PlanDay): List<Handle> {
        val sep = p.words.phrase("dialHandles", "keySep")
        val out = ArrayList<Handle>()
        for (o in occurrences(p, day)) {
            for (end in listOf(START, END)) {
                val ep = if (end == START) o.start else o.end
                if (
                    ep < day.startEpoch ||
                        ep >= day.endEpoch ||
                        (end == END && ep == day.startEpoch)
                )
                    continue
                val key =
                    text(get(o.rule, "id")) +
                        sep +
                        end +
                        sep +
                        CalendarMath.key(o.date) +
                        sep +
                        PageValues.numberText(ep)
                out += Handle(o, end, ep, key)
            }
        }
        return out
    }

    /** RM-39 `snapEp(d0, ep)`: the nearest quarter hour of the wall clock, on the day of [d0]. */
    private fun snap(d0: Double, ep: Double, zone: PageZone): Pair<Double, Double> {
        val q = parts(ep, zone)
        var m = PageValues.round((q.hour * MINUTE + q.minute) / QUARTER) * QUARTER
        val day = parts(d0, zone)
        if (m >= DAY) m = LAST_STOP
        return m to
            WallClock.instantOf(
                day.year.toDouble(),
                day.month.toDouble(),
                day.day.toDouble(),
                m,
                zone,
            )
    }

    /**
     * RM-39 `dialStops(d0, d1)`: on a plain day (24 hours from a local midnight with one offset)
     * the snap at the start and at minute 8 of every quarter hour; else at every minute; a stop
     * wherever the snapped minute changes.
     */
    private fun stops(p: DirectorPanels, d0: Double, d1: Double, zone: PageZone): List<DialStop> {
        val first = parts(d0, zone)
        val even =
            d1 - d0 == DAY_SECONDS &&
                first.hour == 0 &&
                first.minute == 0 &&
                WallClock.offsetSeconds(d0, zone) == WallClock.offsetSeconds(d1 - MINUTE, zone)
        val at =
            if (even)
                listOf(d0) + (0 until QUARTERS).map { d0 + (QUARTER * (it + 1) - SNAP_AT) * MINUTE }
            else (0 until ceil((d1 - d0) / MINUTE).toInt()).map { d0 + it * MINUTE }
        val out = ArrayList<DialStop>()
        for (t in at) {
            val (m, ep) = snap(d0, t, zone)
            if (out.isEmpty() || out.last().m != m)
                out +=
                    DialStop(m, ep, angle(ep, d0, d1), (t - d0) / (d1 - d0) * FULL, p.clock.hm(m))
        }
        return out
    }

    private class Preview(val ns: Double, val ne: Double, val bad: Boolean, val text: String)

    /** RM-40 `dragPreview(r, end, om, d0, cur)` with `dialChange`'s length. */
    private fun preview(
        p: DirectorPanels,
        r: JsonValue,
        end: String,
        om: Double,
        d0: Double,
        cur: DialStop,
        zone: PageZone,
    ): Preview {
        val dur = change(r, end, cur.m)
        val bad = dur < QUARTER
        val day = parts(d0, zone)
        val minute = if (end == START) cur.m + dur else cur.m - dur
        val other =
            WallClock.instantOf(
                day.year.toDouble(),
                day.month.toDouble(),
                day.day.toDouble(),
                minute,
                zone,
            )
        val text =
            if (bad) p.words.tooShort
            else
                p.mins(dur) +
                    if (get(r, "kind") == SLEEP) p.words.phrase("dragPreview", "ofSleep") else ""
        return if (end == START) Preview(cur.ep, other, bad, text)
        else Preview(other, cur.ep, bad, text)
    }

    /** RM-40 `dialChange(r, end, om, m).durationMin`: the rule's length with that end at [m]. */
    private fun change(r: JsonValue, end: String, m: Double): Double {
        val start = get(r, "startMin")
        return if (end == START) {
            val endRel = number(Js.add(start, get(r, "durationMin")))
            (((endRel - m) % DAY) + DAY) % DAY
        } else (((m - number(start)) % DAY) + DAY) % DAY
    }
}
