package com.lifemax.rpg.core.lifedirector

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * LDT-4: the wall clock of an instant in a zone, to the second. [hour] is 0 to 23 and [weekday] is
 * 0 for Sunday to 6 for Saturday.
 */
data class LocalParts(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val second: Int,
    val weekday: Int,
) {
    /** The local calendar date. */
    val date: CalendarDate
        get() = CalendarDate(year, month, day, weekday)

    /** Minutes since the local midnight by the wall clock (a fold repeats them). */
    val minuteOfDay: Int
        get() = hour * MINUTES_PER_HOUR + minute

    private companion object {
        const val MINUTES_PER_HOUR = 60
    }
}

/**
 * Wall clock and instants in a zone (LDT-4 to LDT-9). Epochs are seconds as doubles (LDN-2).
 *
 * The page reads a zone through `Intl.DateTimeFormat`; here the zone's rules come from `java.time`.
 * The two agree on every offset of the zone matrix and of every tz database zone in 2024-2029
 * except where their tz database versions differ (LDT-22). How an instant is chosen for a wall
 * clock time that a clock change skips or repeats is the page's own rule, not `java.time`'s
 * default, and is spelled out at [instantOf].
 */
object WallClock {
    private const val MS_PER_SECOND = 1000.0
    private const val SUNDAY_AS_ZERO = 7

    /**
     * LDT-4 `localParts`: the wall clock at [epochSeconds] in [zone]. The instant is taken to the
     * millisecond toward zero, as `new Date(epochSeconds * 1000)` does, and the seconds field is
     * whole seconds of the wall clock.
     */
    fun localParts(epochSeconds: Double, zone: PageZone): LocalParts {
        val ms = EcmaTime.timeClip(epochSeconds * MS_PER_SECOND)
        require(!ms.isNaN()) { "Invalid time value" }
        val instant = Instant.ofEpochMilli(ms.toLong())
        val local =
            LocalDateTime.ofEpochSecond(
                instant.epochSecond + zone.offsetAt(instant),
                instant.nano,
                ZoneOffset.UTC,
            )
        return LocalParts(
            local.year,
            local.monthValue,
            local.dayOfMonth,
            local.hour,
            local.minute,
            local.second,
            local.dayOfWeek.value % SUNDAY_AS_ZERO,
        )
    }

    /**
     * LDT-5 `tzOffsetSec`: the wall clock at [epochSeconds] read as if it were UTC, minus
     * [epochSeconds]. For a whole-second instant that is the zone's offset in seconds; a fraction
     * of a second in [epochSeconds] carries into the result (`-0.5` for a half second in UTC).
     */
    fun offsetSeconds(epochSeconds: Double, zone: PageZone): Double {
        val p = localParts(epochSeconds, zone)
        val wall =
            EcmaTime.utc(
                p.year.toDouble(),
                (p.month - 1).toDouble(),
                p.day.toDouble(),
                p.hour.toDouble(),
                p.minute.toDouble(),
                p.second.toDouble(),
            ) / MS_PER_SECOND
        return wall - epochSeconds
    }

    /**
     * LDT-6 to LDT-9 `zonedToEpoch`: the instant at which the wall clock in [zone] shows
     * [minuteOfDay] minutes after the start of [day].[month].[year].
     *
     * LDT-6: the wall clock `W` is `Date.UTC(year, month - 1, day, 0, minuteOfDay)` in seconds, so
     * minutes past 1440 (or below 0) roll into the next (previous) date, and an out-of-range month
     * or day rolls over too; NaN for an Invalid Date. Then, with the zone's offsets:
     * - LDT-7 a wall clock that occurs once gives that instant;
     * - LDT-8 one inside a spring gap gives `W - (offset before the gap)`: the wall clock moved
     *   forward by the length of the gap (02:30 in a one-hour 02:00 gap is 03:30);
     * - LDT-9 one inside an autumn fold gives the occurrence whose offset is the one in force at
     *   the instant `W` itself (the wall clock read as UTC). West of Greenwich that instant falls
     *   before the change, so the first occurrence; at Greenwich and east of it, after the change,
     *   so the second. `java.time`'s default (always the first) differs east of the Americas.
     */
    fun instantOf(
        year: Double,
        month: Double,
        day: Double,
        minuteOfDay: Double,
        zone: PageZone,
    ): Double {
        val wallMs = EcmaTime.utc(year, month - 1, day, 0.0, minuteOfDay)
        if (wallMs.isNaN()) return Double.NaN
        val wall = (wallMs / MS_PER_SECOND).toLong()
        val local = LocalDateTime.ofEpochSecond(wall, 0, ZoneOffset.UTC)
        val valid = zone.offsetsOf(local)
        val offset =
            when (valid.size) {
                1 -> valid.single()
                0 -> zone.offsetBeforeGap(local)
                else -> zone.offsetAt(Instant.ofEpochSecond(wall))
            }
        return (wall - offset).toDouble()
    }

    /** [instantOf] for whole numbers. */
    fun instantOf(year: Int, month: Int, day: Int, minuteOfDay: Int, zone: PageZone): Double =
        instantOf(year.toDouble(), month.toDouble(), day.toDouble(), minuteOfDay.toDouble(), zone)

    /** [instantOf] at [minuteOfDay] of a calendar date. */
    fun instantOf(date: CalendarDate, minuteOfDay: Int, zone: PageZone): Double =
        instantOf(date.year, date.month, date.day, minuteOfDay, zone)
}
