package com.lifemax.rpg.core.lifedirector

/**
 * LDT-15: one local day of the plan. It runs from [startEpoch] (the instant of its local midnight
 * by [WallClock.instantOf], so a day whose midnight is skipped starts at the end of the gap) to
 * [endEpoch], the next day's start; [slots] is its length in 15-minute slots (96 on a plain day, 92
 * or 100 on a clock-change day, 94 or 98 for a half-hour change). [key] is [date]'s
 * [CalendarMath.key].
 */
data class PlanDay(
    val index: Int,
    val date: CalendarDate,
    val key: String,
    val startEpoch: Double,
    val endEpoch: Double,
    val slots: Double,
)

/** The plan's days and the local-day boundaries around them (LDT-15, LDT-18, LDT-19). */
object PlanHorizon {
    private const val DAYS_IN_WEEK = 7
    private const val MONDAY_SHIFT = 6

    /**
     * LDT-15 `buildHorizon`: [count] consecutive local days of [zone], the first being the local
     * date of [startEpoch] (the day [startEpoch] falls in, not the next one). Empty for 0 or less.
     */
    fun days(startEpoch: Double, count: Int, zone: PageZone): List<PlanDay> {
        var date = WallClock.localParts(startEpoch, zone).date
        val out = ArrayList<PlanDay>(maxOf(count, 0))
        for (index in 0 until count) {
            val next = requireNotNull(CalendarMath.plusDays(date, 1)) { "Invalid time value" }
            val start = WallClock.instantOf(date, 0, zone)
            val end = WallClock.instantOf(next, 0, zone)
            out +=
                PlanDay(
                    index,
                    date,
                    CalendarMath.key(date),
                    start,
                    end,
                    (end - start) / SlotGrid.SLOT_SECONDS,
                )
            date = next
        }
        return out
    }

    /**
     * LDT-18 `dayEndAfter`: the start (local midnight, as in [days]) of the local date after the
     * one [epochSeconds] falls in.
     */
    fun nextDayStart(epochSeconds: Double, zone: PageZone): Double {
        val date = WallClock.localParts(epochSeconds, zone).date
        return WallClock.instantOf(requireNotNull(CalendarMath.plusDays(date, 1)), 0, zone)
    }

    /**
     * LDT-19, LDT-21: the Monday that starts the local week of [epochSeconds] (weeks start on
     * Monday; a Sunday belongs to the week that began six days before).
     */
    fun monday(epochSeconds: Double, zone: PageZone): CalendarDate {
        val date = WallClock.localParts(epochSeconds, zone).date
        return requireNotNull(
            CalendarMath.plusDays(date, -((date.weekday + MONDAY_SHIFT) % DAYS_IN_WEEK))
        )
    }

    /** LDT-19 `weekKey` (the page's): the [CalendarMath.key] of [monday]. */
    fun weekKey(epochSeconds: Double, zone: PageZone): String =
        CalendarMath.key(monday(epochSeconds, zone))

    /**
     * LDT-19 `weekBounds` (the LifeMax host's): this local week's start (Monday's midnight), its
     * end (the next Monday's), the end of the week after, and Monday's key.
     */
    fun weekBounds(epochSeconds: Double, zone: PageZone): WeekBounds {
        val monday = monday(epochSeconds, zone)
        fun mondayPlus(days: Int) =
            WallClock.instantOf(requireNotNull(CalendarMath.plusDays(monday, days)), 0, zone)
        return WeekBounds(
            mondayPlus(0),
            mondayPlus(DAYS_IN_WEEK),
            mondayPlus(2 * DAYS_IN_WEEK),
            CalendarMath.key(monday),
        )
    }
}

/**
 * LDT-19: a local week's [start], [end] and [nextEnd] (epoch seconds) and its Monday's [mondayKey].
 */
data class WeekBounds(
    val start: Double,
    val end: Double,
    val nextEnd: Double,
    val mondayKey: String,
)
