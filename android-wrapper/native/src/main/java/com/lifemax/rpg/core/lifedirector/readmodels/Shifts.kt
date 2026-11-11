package com.lifemax.rpg.core.lifedirector.readmodels

import com.lifemax.rpg.core.lifedirector.CalendarDate
import com.lifemax.rpg.core.lifedirector.CalendarMath
import com.lifemax.rpg.core.lifedirector.EcmaTime
import com.lifemax.rpg.core.lifedirector.PageRangeError
import com.lifemax.rpg.core.lifedirector.PageTypeError
import com.lifemax.rpg.core.lifedirector.PageValues
import com.lifemax.rpg.core.lifedirector.PageValues.number
import com.lifemax.rpg.core.lifedirector.PageValues.text
import com.lifemax.rpg.core.lifedirector.PageValues.truthy
import com.lifemax.rpg.core.lifedirector.PageZone
import com.lifemax.rpg.core.lifedirector.WallClock
import com.lifemax.rpg.core.lifedirector.readmodels.Js.get
import com.lifemax.rpg.core.lifedirector.readmodels.Js.n
import com.lifemax.rpg.core.lifedirector.readmodels.Js.obj
import com.lifemax.rpg.core.lifedirector.readmodels.Js.s
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/**
 * A work rule as `workRules()` gives it to the rest of LifeMax (RM-36); stored values as stored.
 */
data class WorkRule(
    val id: JsonValue?,
    val title: JsonValue,
    val days: JsonValue,
    val startMin: JsonValue?,
    val durationMin: JsonValue?,
) {
    fun json(): JsonValue =
        obj(
            "id" to id,
            "title" to title,
            "days" to days,
            "startMin" to startMin,
            "durationMin" to durationMin,
        )
}

/** One work shift (`shifts`, RM-37): a work rule's on a date, or a one-off commitment's. */
data class Shift(
    val key: String,
    /** The rule's id as stored (null: undefined, left out); [JsonNull] for a one-off. */
    val ruleId: JsonValue?,
    val title: JsonValue,
    val startEpoch: JsonValue,
    val endEpoch: JsonValue,
    val durationMin: JsonValue?,
) {
    fun json(): JsonValue =
        obj(
            "key" to s(key),
            "ruleId" to ruleId,
            "title" to title,
            "startEpoch" to startEpoch,
            "endEpoch" to endEpoch,
            "durationMin" to durationMin,
        )
}

/** RM-36 to RM-38: `workRules`, `shifts` and `ruleOn` (logic.js:2941-2974, 591-602). */
internal object Shifts {
    private val WORK = JsonString("WORK")
    private val CLASS = JsonString("CLASS")
    private val EMERGENCY = JsonString("EMERGENCY")
    private const val GUARD = 400
    private const val HALF = 0.5
    private const val MINUTE = 60.0
    private const val MS_PER_DAY = 86_400_000.0
    private val EVERY_DAY = (0..6).map { n(it) }

    fun workRules(p: DirectorPanels): List<WorkRule> {
        val work = p.words.phrase("workRules", "work")
        return MyTime.rules(p)
            .filter { get(it, "kind") == WORK }
            .map {
                WorkRule(
                    get(it, "id"),
                    get(it, "title").let { t -> if (truthy(t)) t!! else s(work) },
                    copyDays(get(it, "days")),
                    get(it, "startMin"),
                    get(it, "durationMin"),
                )
            }
    }

    /** `(r.days || []).slice()`: a list copied, a text as it is. */
    fun copyDays(days: JsonValue?): JsonValue =
        when {
            !truthy(days) -> JsonArray.EMPTY
            days is JsonArray -> JsonArray(days.items)
            days is JsonString -> days
            else -> throw PageTypeError("days.slice is not a function")
        }

    /**
     * RM-38 `ruleOn(r, ymd)`: within the rule's valid dates; on a rotation's active day (counted
     * from its anchor date, as the stored values combine in JavaScript); else on one of its week
     * days (every day when it stores none as a list).
     */
    fun ruleOn(r: JsonValue, date: CalendarDate): Boolean {
        val key = s(CalendarMath.key(date))
        val from = get(r, "validFrom")
        if (truthy(from) && PageValues.less(key, from)) return false
        val to = get(r, "validTo")
        if (truthy(to) && PageValues.less(to, key)) return false
        val rot = get(r, "rotation")
        if (
            truthy(rot) &&
                truthy(get(rot, "anchorDate")) &&
                PageValues.order(get(rot, "cycleLength"), n(0.0)) == 1
        ) {
            val parts = text(get(rot, "anchorDate")).split('-').map(Js::number)
            fun part(i: Int) = parts.getOrElse(i) { Double.NaN }
            val days =
                (EcmaTime.utc(date.year.toDouble(), date.month - 1.0, date.day.toDouble()) -
                    EcmaTime.utc(part(0), part(1) - 1, part(2))) / MS_PER_DAY
            val nDays = PageValues.round(days)
            val c = get(rot, "cycleLength")
            val first = PageValues.plus(nDays % number(c), c)
            val index = number(first) % number(c)
            val active = Js.itemsOrNone(get(rot, "activeDays"), "activeDays").map { n(number(it)) }
            return Js.includes(active, n(index))
        }
        val days = get(r, "days")
        val list = if (days is JsonArray) days.items else EVERY_DAY
        return Js.includes(list, n(date.weekday))
    }

    /** `zonedToEpoch(y, mo, d, minute, zone)` with the minute as the page's value gives it. */
    fun zoned(date: CalendarDate, minute: JsonValue?, zone: PageZone): Double =
        WallClock.instantOf(
            date.year.toDouble(),
            date.month.toDouble(),
            date.day.toDouble(),
            number(minute),
            zone,
        )

    /** RM-37 `shifts(fromEpoch, toEpoch)`. */
    fun of(p: DirectorPanels, fromEpoch: Double, toEpoch: Double): List<Shift> {
        val zone = p.zone()
        val p0 = parts(fromEpoch, zone)
        val p1 = parts(toEpoch, zone)
        var date = plus(p0, -1)
        val lastKey = CalendarMath.key(plus(p1, 0))
        val rules = MyTime.rules(p)
        fun ph(k: String) = p.words.phrase("shifts", k)
        val out = ArrayList<Shift>()
        var guard = 0
        while (guard < GUARD && CalendarMath.key(date) <= lastKey) {
            for (r in rules) {
                if (get(r, "kind") != WORK || !ruleOn(r, date)) continue
                val start = get(r, "startMin")
                val st = zoned(date, start, zone)
                val e = zoned(date, Js.add(start, get(r, "durationMin")), zone)
                if (e <= fromEpoch || e > toEpoch) continue
                out +=
                    Shift(
                        text(get(r, "id")) + ph("keySep") + CalendarMath.key(date),
                        get(r, "id"),
                        get(r, "title").let { if (truthy(it)) it!! else s(ph("work")) },
                        n(st),
                        n(e),
                        get(r, "durationMin"),
                    )
            }
            guard++
            date = plusDays(date, 1)
        }
        for (c in Js.itemsOrNone(get(p.state, "fixedCommitments"), "fixedCommitments")) {
            val kind =
                get(c, "kind").let { k ->
                    if (truthy(k)) k
                    else
                        when (get(c, "commitmentType")) {
                            CLASS -> CLASS
                            EMERGENCY -> EMERGENCY
                            else -> WORK
                        }
                }
            val start = get(c, "startEpoch")
            val end = get(c, "endEpoch")
            if (kind != WORK || !truthy(start) || !truthy(end)) continue
            if (PageValues.lessOrEqual(end, n(fromEpoch)) || PageValues.less(n(toEpoch), end))
                continue
            val id =
                listOf(get(c, "id"), get(c, "commitmentId")).firstOrNull { truthy(it) } ?: start
            out +=
                Shift(
                    ph("oneKey") + text(id),
                    JsonNull,
                    get(c, "title").let { if (truthy(it)) it!! else s(ph("extra")) },
                    start!!,
                    end!!,
                    n(PageValues.round((number(end) - number(start)) / MINUTE)),
                )
        }
        val off =
            Js.itemsOrNone(get(get(p.state, "availability"), "blackouts"), "blackouts").filter {
                truthy(it) && truthy(get(it, "startEpoch")) && truthy(get(it, "endEpoch"))
            }
        return out.filter { sh ->
                val e = number(sh.endEpoch)
                val st = number(sh.startEpoch)
                var hit = 0.0
                for (b in off) hit +=
                    Math.max(
                        0.0,
                        Math.min(number(get(b, "endEpoch")), e) -
                            Math.max(number(get(b, "startEpoch")), st),
                    )
                hit < (e - st) * HALF
            }
            .sortedWith { a, b -> PageValues.byDifference(number(a.endEpoch) - number(b.endEpoch)) }
    }

    private fun parts(epoch: Double, zone: PageZone) =
        try {
            WallClock.localParts(epoch, zone)
        } catch (e: IllegalArgumentException) {
            throw PageRangeError("Invalid time value")
        }

    private fun plus(p: com.lifemax.rpg.core.lifedirector.LocalParts, days: Int): CalendarDate =
        plusDays(p.date, days)

    private fun plusDays(date: CalendarDate, days: Int): CalendarDate =
        CalendarMath.plusDays(date, days) ?: throw PageRangeError("Invalid time value")
}
