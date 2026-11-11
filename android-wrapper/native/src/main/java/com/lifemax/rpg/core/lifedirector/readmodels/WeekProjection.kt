package com.lifemax.rpg.core.lifedirector.readmodels

import com.lifemax.rpg.core.lifedirector.PageTypeError
import com.lifemax.rpg.core.lifedirector.PageValues
import com.lifemax.rpg.core.lifedirector.PageValues.member
import com.lifemax.rpg.core.lifedirector.PageValues.number
import com.lifemax.rpg.core.lifedirector.PageValues.numberText
import com.lifemax.rpg.core.lifedirector.PageValues.text
import com.lifemax.rpg.core.lifedirector.PageValues.truthy
import com.lifemax.rpg.core.lifedirector.PlanDay
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonValue

/** RM-18 `weekSegs`: a block of a day's bar, its class, session state and place in percent. */
data class WeekSegment(val kind: String, val state: String, val left: Double, val width: Double)

/** A day row of the projected week (RM-19). */
data class WeekDayRow(
    val index: Int,
    val label: String,
    val date: Int,
    val today: Boolean,
    val aria: String,
    val count: String,
    val done: String,
    val minutes: String,
    val segments: List<WeekSegment>,
)

/** A row of "Where your XP goes" (RM-19): shares of the largest attribute, rounded to 0.01. */
data class YieldRow(
    val label: String,
    val colour: Double,
    val done: Double,
    val plan: Double,
    val total: String,
)

data class WeekYield(val title: String, val note: String, val rows: List<YieldRow>)

/** A group of sessions that could not fit (RM-19 `unscheduledGroups`). */
data class UnfitGroup(val title: String, val tier: String, val text: JsonValue?)

data class Unfit(val summary: String, val groups: List<UnfitGroup>)

data class WeekFit(
    val title: String,
    val text: String,
    val allFit: String,
    val unscheduled: Unfit?,
)

/** RM-19 `nativeWeek()`: the week as the native projection carries it (logic.js:3272-3288). */
data class WeekProjection(
    val stats: List<WeekStat>,
    val ticks: List<String>,
    val days: List<WeekDayRow>,
    val legend: List<LegendEntry>,
    val yield: WeekYield?,
    val fit: WeekFit,
)

/** RM-18, RM-19 (logic.js:1302-1311, 1363-1371, 3272-3288). */
internal object WeekProjections {
    private const val PERCENT = 100.0

    /** RM-18 `weekSegs(day, blocks)`. */
    fun segments(day: PlanDay, blocks: List<DayBlock>, words: ReadModelWords): List<WeekSegment> {
        val d0 = day.startEpoch
        val d1 = day.endEpoch
        val span = maxOf(1.0, d1 - d0)
        val out = ArrayList<WeekSegment>()
        for (b in blocks) {
            val k = if (b.session != null) "task" else b.blockClass(words)
            if (k == "free") continue
            val l = (maxOf(d0, b.startEpoch) - d0) / span * PERCENT
            val w = (minOf(d1, b.endEpoch) - maxOf(d0, b.startEpoch)) / span * PERCENT
            if (w > 0) out += WeekSegment(k, b.session?.sessionState ?: "", l, w)
        }
        return out
    }

    /** `Math.round(v * 100) / 100`. */
    private fun round2(v: Double): Double = PageValues.round(v * PERCENT) / PERCENT

    /** RM-19 `attrName`: the first UTF-16 unit upper-cased, as `toUpperCase` does. */
    private fun attrName(a: String): String =
        if (a.isEmpty()) "" else a.substring(0, 1).uppercase() + a.substring(1)

    fun of(m: DirectorReadModels, w: WeekModel): WeekProjection {
        val words = m.words
        val unfit = unscheduled(m.requireSchedule(), words)
        return WeekProjection(
            stats = w.stats,
            ticks = words.weekTicks,
            days =
                w.rows.map { r ->
                    WeekDayRow(
                        r.index,
                        r.name,
                        r.day.date.day,
                        r.index == 0,
                        r.aria,
                        r.sessions.toString(),
                        if (r.done != 0) words.phrase("doneMark") + r.done else "",
                        if (truthy(r.minutes)) Week.mins(r.minutes, words)
                        else words.phrase("noMinutes"),
                        segments(r.day, r.blocks, words).map {
                            it.copy(left = round2(it.left), width = round2(it.width))
                        },
                    )
                },
            legend = words.weekLegend,
            yield =
                if (w.attributes.isEmpty()) null
                else
                    WeekYield(
                        words.phrase("yieldTitle"),
                        words.yieldNote,
                        w.attributes.map {
                            YieldRow(
                                attrName(it.attr),
                                words.attributeColours[it.attr] ?: words.fallbackColour,
                                round2(it.done / w.maxAttribute),
                                round2(it.plan / w.maxAttribute),
                                numberText(PageValues.round(it.done + it.plan)),
                            )
                        },
                    ),
            fit = WeekFit(words.phrase("fitTitle"), w.fitText, words.allFit, unfit),
        )
    }

    /** RM-19 `unscheduledGroups(s)` and `unfitSummary(n)`. */
    private fun unscheduled(s: JsonValue, words: ReadModelWords): Unfit? {
        val v = member(s, "unscheduledDiagnostics")
        val items =
            if (!truthy(v)) emptyList()
            else (v as? JsonArray)?.items ?: throw PageTypeError("filter is not a function")
        val list =
            items.filter {
                if (it == JsonNull)
                    throw PageTypeError("Cannot read properties of null (reading 'tier')")
                truthy(member(it, "tier"))
            }
        if (list.isEmpty()) return null
        val groups = LinkedHashMap<String, Pair<JsonValue, IntArray>>()
        for (d in list) {
            val k = text(member(d, "title")) + "|" + text(member(d, "reasonCode"))
            groups.getOrPut(k) { d to IntArray(1) }.second[0]++
        }
        val ordered =
            groups.values.sortedWith { a, b ->
                val d = tierOf(b.first) - tierOf(a.first)
                if (d > 0) 1 else if (d < 0) -1 else 0
            }
        val n = list.size
        val summary =
            n.toString() +
                words.phrase("unfitSession") +
                (if (n == 1) "" else words.phrase("unfitPlural")) +
                words.phrase("unfitTail")
        return Unfit(
            summary,
            ordered.map { (d, count) ->
                UnfitGroup(
                    text(member(d, "title")) +
                        (if (count[0] > 1) words.phrase("times") + count[0] else ""),
                    words.phrase("tier") + text(member(d, "tier")),
                    member(d, "humanExplanation"),
                )
            },
        )
    }

    /** `d.tier || 0` as the comparator's number. */
    private fun tierOf(d: JsonValue): Double =
        member(d, "tier").let { if (truthy(it)) number(it) else 0.0 }
}
