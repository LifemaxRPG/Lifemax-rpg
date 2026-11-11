package com.lifemax.rpg.core.lifedirector.readmodels

import com.lifemax.rpg.core.lifedirector.CalendarMath
import com.lifemax.rpg.core.lifedirector.GridBlock
import com.lifemax.rpg.core.lifedirector.PageRangeError
import com.lifemax.rpg.core.lifedirector.PageTypeError
import com.lifemax.rpg.core.lifedirector.PageValues
import com.lifemax.rpg.core.lifedirector.PageValues.member
import com.lifemax.rpg.core.lifedirector.PageValues.number
import com.lifemax.rpg.core.lifedirector.PageValues.text
import com.lifemax.rpg.core.lifedirector.PageValues.truthy
import com.lifemax.rpg.core.lifedirector.PageZone
import com.lifemax.rpg.core.lifedirector.PlanDay
import com.lifemax.rpg.core.lifedirector.PlanHorizon
import com.lifemax.rpg.core.lifedirector.WallClock
import com.lifemax.rpg.core.lifedirector.ZoneNames
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonValue

/**
 * The Life Director read models of WP-LD8a (LIFE_DIRECTOR_READMODELS_SPEC.md): from a saved Life
 * Director state [state] (the page's `ST`, as JSON; only read, never changed), the moment
 * [nowMillis] (the page's `Date.now()`), the zone the device reports [deviceZone] and the LifeMax
 * XP preview [xp], exactly what the page's `logic.js` computes for its own screens and its native
 * projection: the plan's days, a day's availability preview, each day's blocks, the calendar's
 * days, today and tomorrow as one list, what is happening now, the week, and the week as the
 * projection carries it. Nothing here writes, saves or sends anything, so a shadow check (plan
 * WP-LD12) can run it beside the page and compare [ReadModelJson]'s page-shaped JSON with the
 * page's own answer. The read models throw [PageTypeError] and [PageRangeError] where the page
 * throws a TypeError or RangeError.
 */
class DirectorReadModels(
    val state: JsonValue?,
    val nowMillis: Long,
    val deviceZone: String,
    val xp: SessionXpPreview,
    val words: ReadModelWords = ReadModelWords.packaged,
) {
    /** RM-2 `nowEpoch()`: whole seconds, rounded down. */
    val nowEpoch: Double = Math.floorDiv(nowMillis, MS_PER_SECOND).toDouble()

    /** `ST.schedule`, the saved plan (absent: null). */
    val schedule: JsonValue?
        get() = member(state, "schedule")

    /**
     * RM-1 `tz()`: the stored zone `ST.availability.timezone` when it is truthy, else the
     * device's. A state without `availability` throws, as `ST.availability.timezone` does.
     */
    fun zoneValue(): JsonValue {
        val availability = member(state, "availability")
        if (availability == null || availability == JsonNull)
            throw PageTypeError("Cannot read properties of undefined (reading 'timezone')")
        val stored = member(availability, "timezone")
        return if (truthy(stored)) stored!! else com.lifemax.rpg.core.save.JsonString(deviceZone)
    }

    /** [zoneValue] as a zone; a name the page's `Intl` does not know throws a RangeError. */
    fun zone(): PageZone = resolve(zoneValue())

    /**
     * RM-3 `horizonDays(ST.schedule, tz())` (logic.js:394-402): the plan's local days
     * (`buildHorizon` from its start, as many as `Math.round` of its length in days), less the days
     * already over (`endEpoch <= now`); when none is left, the one day [nowEpoch] falls in.
     */
    fun horizonDays(): List<PlanDay> {
        val s = requireSchedule()
        val start = number(member(s, "horizonStartEpoch"))
        val count = PageValues.round((number(member(s, "horizonEndEpoch")) - start) / DAY)
        val zone = zone()
        val days = horizon(start, count, zone)
        val current = days.filter { it.endEpoch > nowEpoch }
        return current.ifEmpty { horizon(nowEpoch, 1.0, zone) }
    }

    /**
     * RM-4 `calendarDays(offset, tz())` (logic.js:3308-3315): seven local days from the date
     * [offset] days after today's; null when that date's midnight is not a finite instant.
     */
    fun calendarDays(offset: Int): List<PlanDay>? {
        val zone = zone()
        val today = WallClock.localParts(nowEpoch, zone).date
        val date = CalendarMath.plusDays(today, offset) ?: return null
        val start = WallClock.instantOf(date, 0, zone)
        if (!start.isFinite()) return null
        return horizon(start, WEEK, zone)
    }

    /**
     * RM-5 `unplannedSegments(day, tz())` (logic.js:404-425): the availability the engine derives
     * for the three days around [day] from the stored availability, commitments and preferences,
     * with no sessions: each run of slots painted by one block of a visible kind, cut to [day].
     */
    fun unplannedSegments(day: PlanDay): List<GridBlock> = DayBlocks.unplanned(this, day)

    /** RM-6 to RM-9 `dayBlocks(ST.schedule, day, tz())` (logic.js:426-457). */
    fun dayBlocks(day: PlanDay): List<DayBlock> = DayBlocks.of(this, requireSchedule(), day)

    /** RM-11 `liveTimeline(now)` (logic.js:1058-1071). */
    fun liveTimeline(): List<DayBlock> = LiveNow.timeline(this)

    /** RM-12, RM-13 `nowState(now)` (logic.js:1120-1135). */
    fun nowState(): NowState = LiveNow.nowState(this)

    /** RM-14 to RM-18, RM-20 `weekModel()` (logic.js:1318-1345). */
    fun weekModel(): WeekModel = Week.model(this)

    /**
     * RM-19 `nativeWeek()` (logic.js:3272-3288): the week as the native projection carries it;
     * null while set-up is not finished or there is no plan.
     */
    fun weekProjection(): WeekProjection? =
        if (!truthy(member(state, "setupComplete")) || !truthy(schedule)) null
        else WeekProjections.of(this, weekModel())

    /** The sessions stored in the plan [s], scheduled first, then history (`concat`). */
    internal fun sessionsOf(s: JsonValue?): List<StoredSession> {
        fun list(key: String, which: SessionList): List<StoredSession> {
            val v = member(s, key)
            if (!truthy(v)) return emptyList()
            val items = (v as? JsonArray)?.items ?: throw PageTypeError("$key is not a list")
            return items.mapIndexed { k, item -> StoredSession(which, k, item) }
        }
        return list("scheduledInstances", SessionList.SCHEDULED) +
            list("historyInstances", SessionList.HISTORY)
    }

    internal fun requireSchedule(): JsonValue {
        val s = schedule
        if (s == null || s == JsonNull)
            throw PageTypeError("Cannot read properties of null (reading 'horizonStartEpoch')")
        return s
    }

    /** `ST.ui.focus` (RM-13): the page's timer, absent when there is none. */
    internal fun focus(): JsonValue? {
        val ui = member(state, "ui")
        if (ui == null || ui == JsonNull)
            throw PageTypeError("Cannot read properties of undefined (reading 'focus')")
        return member(ui, "focus")
    }

    companion object {
        private const val MS_PER_SECOND = 1000L
        private const val DAY = 86400.0
        private const val WEEK = 7.0

        /** A zone value as the page's `Intl` reads it (`String(value)`), else a RangeError. */
        fun resolve(value: JsonValue): PageZone =
            ZoneNames.resolve(text(value)) ?: throw PageRangeError("Invalid time zone specified")

        /**
         * `buildHorizon(start, count, zone)`: the loop runs while its index is below [count], so a
         * NaN or non-positive count gives no day; the start's local date is read first either way
         * (an invalid start throws a RangeError).
         */
        internal fun horizon(start: Double, count: Double, zone: PageZone): List<PlanDay> {
            if (!start.isFinite() || EcmaTimeLimit.invalid(start))
                throw PageRangeError("Invalid time value")
            val n = if (count.isNaN() || count <= 0) 0 else kotlin.math.ceil(count).toInt()
            return PlanHorizon.days(start, n, zone)
        }
    }
}

/** Whether an epoch (seconds) is past what a JavaScript Date can hold. */
internal object EcmaTimeLimit {
    private const val LIMIT_MS = 8.64e15

    fun invalid(epochSeconds: Double): Boolean = kotlin.math.abs(epochSeconds * 1000.0) > LIMIT_MS
}
