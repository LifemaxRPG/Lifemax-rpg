package com.lifemax.rpg.core.lifedirector.readmodels

import com.lifemax.rpg.core.lifedirector.Availability
import com.lifemax.rpg.core.lifedirector.AvailabilityGrid
import com.lifemax.rpg.core.lifedirector.CalendarMath
import com.lifemax.rpg.core.lifedirector.GridBlock
import com.lifemax.rpg.core.lifedirector.PageTypeError
import com.lifemax.rpg.core.lifedirector.PageValues.member
import com.lifemax.rpg.core.lifedirector.PageValues.number
import com.lifemax.rpg.core.lifedirector.PageValues.truthy
import com.lifemax.rpg.core.lifedirector.PlanDay
import com.lifemax.rpg.core.lifedirector.WallClock
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/** RM-5 to RM-9: a day's availability preview and its blocks (logic.js:404-457). */
internal object DayBlocks {
    private const val SLOT = 900.0
    private const val PREVIEW_DAYS = 3.0
    private const val MIN_FREE = 900.0

    /** RM-5: the block kinds the preview shows (logic.js:413). */
    private val VISIBLE =
        setOf(
            "SLEEP",
            "WORK",
            "CLASS",
            "APPOINTMENT",
            "COMMUTE",
            "DECOMPRESS",
            "BLACKOUT",
            "SICK",
            "EMERGENCY",
            "UNAVAILABLE",
        )

    /** RM-6: segment codes that are not drawn from the segments (logic.js:432). */
    private val SKIPPED = setOf("FREE", "SCHEDULED_TASK", "USER_LOCKED_ACTIVITY")

    /** RM-7: lifecycle states a day does not show (logic.js:435). */
    private val HIDDEN = setOf("EXCUSED_DISPLACED", "CANCELLED_BY_QUEST")

    /** RM-5 `unplannedSegments(day, z)`. */
    fun unplanned(m: DirectorReadModels, day: PlanDay): List<GridBlock> {
        val zoneValue = m.zoneValue()
        val zone = DirectorReadModels.resolve(zoneValue)
        val previous =
            CalendarMath.plusDays(day.date, -1)
                ?: throw com.lifemax.rpg.core.lifedirector.PageRangeError("Invalid time value")
        val start = WallClock.instantOf(previous, 0, zone)
        val av = member(m.state, "availability")
        val availability = LinkedHashMap((av as? JsonObject)?.members ?: emptyMap())
        availability["timezone"] = zoneValue
        val commitments = member(m.state, "fixedCommitments")
        val params = LinkedHashMap<String, JsonValue>()
        params["timezone"] = zoneValue
        params["horizonStartEpoch"] = JsonNumber(start)
        params["horizonDays"] = JsonNumber(PREVIEW_DAYS)
        params["playerAvailability"] = JsonObject(availability)
        params["fixedCommitments"] = if (truthy(commitments)) commitments!! else JsonArray.EMPTY
        member(m.state, "prefs")?.let { params["playerPreferences"] = it }
        val g: AvailabilityGrid = Availability.derive(JsonObject(params)).grid
        val h0 = g.slots.startEpoch
        val n = g.tag.size
        val out = ArrayList<GridBlock>()
        var i = 0
        while (i < n) {
            val tag = g.tag[i]
            var end = i + 1
            while (end < n && g.tag[end] == tag) end++
            val block = if (tag >= 0) g.blocks[tag] else null
            val kind = block?.meta?.kind
            if (block != null && kind is JsonString && kind.value in VISIBLE) {
                val a = maxOf(day.startEpoch, h0 + i * SLOT)
                val b = minOf(day.endEpoch, h0 + end * SLOT)
                if (b > a) out += block.copy(startEpoch = a, endEpoch = b)
            }
            i = end
        }
        return out
    }

    /** A segment as `dayBlocks` reads it: a stored segment of the plan, or a preview block. */
    private class Segment(
        val startEpoch: Double,
        val endEpoch: Double,
        val code: JsonValue?,
        val kind: JsonValue?,
        val title: JsonValue?,
        val refId: JsonValue?,
    )

    private fun stored(s: JsonValue?): List<Segment> {
        val v = member(s, "segments")
        if (!truthy(v)) return emptyList()
        val items = (v as? JsonArray)?.items ?: throw PageTypeError("segments is not iterable")
        return items.map {
            Segment(
                number(member(it, "startEpoch")),
                number(member(it, "endEpoch")),
                member(it, "code"),
                member(it, "kind"),
                member(it, "title"),
                member(it, "refId"),
            )
        }
    }

    private fun preview(blocks: List<GridBlock>): List<Segment> =
        blocks.map {
            Segment(
                it.startEpoch,
                it.endEpoch,
                JsonNumber(it.code.toDouble()),
                it.meta?.kind,
                it.meta?.title,
                it.meta?.refId?.let(::JsonString),
            )
        }

    /** RM-6 to RM-9 `dayBlocks(sched, day, z)`. */
    fun of(m: DirectorReadModels, sched: JsonValue, day: PlanDay): List<DayBlock> {
        val s = day.startEpoch
        val e = day.endEpoch
        val words = m.words
        val previewDay =
            sched === m.schedule &&
                (e <= number(member(sched, "horizonStartEpoch")) ||
                    s >= number(member(sched, "horizonEndEpoch")))
        val segments = if (previewDay) preview(unplanned(m, day)) else stored(sched)
        val out = ArrayList<DayBlock>()
        for (g in segments) {
            if (g.endEpoch <= s || g.startEpoch >= e) continue
            val code = g.code
            if (code is JsonString && code.value in SKIPPED) continue
            val meal = g.kind == MEAL
            out +=
                DayBlock(
                    maxOf(s, g.startEpoch),
                    minOf(e, g.endEpoch),
                    code,
                    g.kind,
                    g.title,
                    g.refId,
                    mealStartEpoch = if (meal) g.startEpoch else null,
                    clipped = g.startEpoch < s || g.endEpoch > e,
                )
        }
        for (i in m.sessionsOf(sched)) {
            if (!(i.endEpoch > s && i.startEpoch < e)) continue
            val st = i.lifecycleState
            if (st is JsonString && st.value in HIDDEN) continue
            out += DayBlock(i.startEpoch, i.endEpoch, TASK, TASK, i.title, session = i)
        }
        val sorted =
            out.sortedWith { a, b ->
                val d = a.startEpoch - b.startEpoch
                if (d != 0.0 && !d.isNaN()) sign(d) else flag(a) - flag(b)
            }
        val cleaned = ArrayList<DayBlock>()
        for (b in sorted) {
            if (b.session == null && sorted.any { it.isOverPlanned(b) }) {
                val ov =
                    sorted
                        .filter { it.session != null && it.overlaps(b) }
                        .sortedWith(byStart)
                var cur = b.startEpoch
                for (o in ov) {
                    if (o.startEpoch > cur) cleaned += b.span(cur, o.startEpoch)
                    cur = maxOf(cur, o.endEpoch)
                }
                if (cur < b.endEpoch) cleaned += b.span(cur, b.endEpoch)
            } else cleaned += b
        }
        val ordered = cleaned.sortedWith(byStart)
        val final = ArrayList<DayBlock>()
        var cur = s
        for (b in ordered) {
            if (b.startEpoch > cur && b.startEpoch - cur >= MIN_FREE) final += free(cur, b.startEpoch, words)
            if (b.endEpoch > cur || b.session != null) final += b
            cur = maxOf(cur, b.endEpoch)
        }
        if (e - cur >= MIN_FREE) final += free(cur, e, words)
        return final
    }

    private val TASK = JsonString("TASK")
    private val FREE = JsonString("FREE")
    private val MEAL = JsonString("MEAL")

    /** `(a, b) => a.startEpoch - b.startEpoch` as a comparator (NaN reads as equal). */
    private val byStart = Comparator<DayBlock> { a, b -> sign(a.startEpoch - b.startEpoch) }

    private fun sign(d: Double): Int = if (d > 0) 1 else if (d < 0) -1 else 0

    private fun flag(b: DayBlock): Int = if (b.session != null) 1 else 0

    private fun DayBlock.overlaps(b: DayBlock): Boolean =
        startEpoch < b.endEpoch && endEpoch > b.startEpoch

    /** RM-8: a planned session over [b]. */
    private fun DayBlock.isOverPlanned(b: DayBlock): Boolean =
        session != null && overlaps(b) && session.isPlanned

    /** RM-9: free time, which has no reference and is not clipped. */
    private fun free(from: Double, until: Double, words: ReadModelWords): DayBlock =
        DayBlock(from, until, FREE, FREE, JsonString(words.phrase("freeTime")))
}
