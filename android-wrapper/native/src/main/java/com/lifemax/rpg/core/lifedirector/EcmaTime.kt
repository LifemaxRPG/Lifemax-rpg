package com.lifemax.rpg.core.lifedirector

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.truncate

/**
 * JavaScript's own date arithmetic, which the engine uses for every calendar step (LDN-3): a time
 * value is milliseconds since 1970-01-01T00:00:00Z as a double, NaN for "Invalid Date".
 *
 * `Date.UTC(year, month, date, hours, minutes, seconds)` truncates every argument toward zero,
 * reads a year from 0 to 99 as 1900 to 1999, rolls a month outside 0..11 and a day outside the
 * month over into the next or previous year or month, and gives NaN for a non-finite argument or a
 * result more than 8.64e15 ms from 1970.
 */
internal object EcmaTime {
    /** Milliseconds in a day: every day of a time value has exactly this many. */
    const val MS_PER_DAY: Double = 86_400_000.0

    /** The furthest time value from 1970 that a date can hold, in either direction. */
    private const val MAX_TIME_VALUE: Double = 8.64e15

    /**
     * The largest year magnitude V8 computes before giving up (the result is out of range anyway).
     */
    private const val MAX_YEAR: Double = 1_000_000.0

    /** The largest month magnitude V8 computes before giving up. */
    private const val MAX_MONTH: Double = 10_000_000.0

    private const val MS_PER_HOUR = 3_600_000.0
    private const val MS_PER_MINUTE = 60_000.0
    private const val MS_PER_SECOND = 1_000.0
    private const val MONTHS = 12.0
    private val TWO_DIGIT_YEARS = 0.0..99.0
    private const val TWENTIETH_CENTURY = 1900.0

    /** ToIntegerOrInfinity: NaN is 0, everything else is truncated toward zero, and -0 is 0. */
    fun integer(value: Double): Double =
        if (value.isNaN()) 0.0 else truncate(value).let { if (it == 0.0) 0.0 else it }

    /** `Date.UTC(year, month0, date, hours, minutes, seconds)`: a time value, or NaN. */
    fun utc(
        year: Double,
        month0: Double,
        date: Double,
        hours: Double = 0.0,
        minutes: Double = 0.0,
        seconds: Double = 0.0,
    ): Double {
        if (year.isNaN()) return Double.NaN
        val whole = integer(year)
        val fullYear = if (whole in TWO_DIGIT_YEARS) TWENTIETH_CENTURY + whole else year
        return timeClip(day(fullYear, month0, date) * MS_PER_DAY + time(hours, minutes, seconds))
    }

    /** The day number (days since 1970-01-01) of [date] of month [month0] of [year], or NaN. */
    private fun day(year: Double, month0: Double, date: Double): Double {
        if (!year.isFinite() || !month0.isFinite() || !date.isFinite()) return Double.NaN
        val y = integer(year)
        val m = integer(month0)
        if (abs(y) > MAX_YEAR || abs(m) > MAX_MONTH) return Double.NaN
        val rolledYear = y + floor(m / MONTHS)
        val month = m - floor(m / MONTHS) * MONTHS
        val first = LocalDate.of(rolledYear.toInt(), month.toInt() + 1, 1).toEpochDay()
        return first.toDouble() + integer(date) - 1
    }

    private fun time(hours: Double, minutes: Double, seconds: Double): Double {
        if (!hours.isFinite() || !minutes.isFinite() || !seconds.isFinite()) return Double.NaN
        return integer(hours) * MS_PER_HOUR +
            integer(minutes) * MS_PER_MINUTE +
            integer(seconds) * MS_PER_SECOND
    }

    /** TimeClip: NaN outside the representable range, else truncated toward zero. */
    fun timeClip(value: Double): Double =
        if (!value.isFinite() || abs(value) > MAX_TIME_VALUE) Double.NaN else integer(value)

    /** The UTC calendar date of a time value that is not NaN. */
    fun dateOf(timeValue: Double): LocalDate =
        LocalDate.ofEpochDay(Math.floorDiv(timeValue.toLong(), MS_PER_DAY.toLong()))
}
