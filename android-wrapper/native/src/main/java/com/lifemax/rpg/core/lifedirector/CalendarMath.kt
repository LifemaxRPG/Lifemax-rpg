package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.economy.jsNumber
import com.lifemax.rpg.core.save.jsNumberText

/**
 * A date as the engine writes it down, `{y, mo, d}`: year, month (1 to 12) and day, not necessarily
 * valid. The fields are doubles because they may come from text (`parseYmd`) and so be fractional
 * or NaN; [CalendarMath] rolls an out-of-range month or day over, as `Date.UTC` does (LDN-3).
 */
data class DateFields(val year: Double, val month: Double, val day: Double) {
    constructor(
        year: Int,
        month: Int,
        day: Int,
    ) : this(year.toDouble(), month.toDouble(), day.toDouble())
}

/**
 * A real calendar date, the result of every calendar step (LDT-11): [weekday] is 0 for Sunday to 6
 * for Saturday.
 */
data class CalendarDate(val year: Int, val month: Int, val day: Int, val weekday: Int) {
    /** The same date as [DateFields]. */
    val fields: DateFields
        get() = DateFields(year, month, day)
}

/** Calendar steps without a time zone (LDT-11 to LDT-14). Every result is the page's. */
object CalendarMath {
    private const val DAYS_IN_WEEK = 7
    private const val THURSDAY = 4

    /**
     * LDT-11 `ymdAdd`: [days] days after [date] (before it when negative), rolled over by
     * `Date.UTC` rules; null where the page gets an Invalid Date (a NaN field, or outside +-8.64e15
     * ms).
     */
    fun plusDays(date: DateFields, days: Double): CalendarDate? =
        fromTimeValue(EcmaTime.utc(date.year, date.month - 1, date.day + days))

    /** LDT-11 [plusDays] for a whole number of days. */
    fun plusDays(date: DateFields, days: Int): CalendarDate? = plusDays(date, days.toDouble())

    /** LDT-11 [plusDays] from a real date. */
    fun plusDays(date: CalendarDate, days: Int): CalendarDate? = plusDays(date.fields, days)

    /**
     * LDT-12 `ymdKey`: the year as JavaScript writes the number (not padded: year 5 is `5`), then
     * the month and the day each padded to two digits with a leading zero, joined by `-`. A NaN
     * field reads `NaN`.
     */
    fun key(date: DateFields): String =
        number(date.year) +
            "-" +
            number(date.month).padStart(2, '0') +
            "-" +
            number(date.day).padStart(2, '0')

    /** LDT-12 [key] of a real date. */
    fun key(date: CalendarDate): String = key(date.fields)

    /**
     * LDT-13 `parseYmd`: [text] split at every `-`, the first three pieces read as JavaScript
     * numbers (`Number`: blank is 0, `0x` hexadecimal, surrounding white space allowed, a missing
     * piece is NaN), then [plusDays] by 0. Null for an Invalid Date.
     */
    fun parse(text: String): CalendarDate? {
        val pieces = text.split('-')
        fun piece(index: Int): Double = pieces.getOrNull(index)?.let(::jsNumber) ?: Double.NaN
        return plusDays(DateFields(piece(0), piece(1), piece(2)), 0.0)
    }

    /**
     * LDT-14 `daysBetween`: whole days from [from] to [to] (negative when [to] is earlier), each
     * date read by `Date.UTC` rules; NaN when either is an Invalid Date.
     */
    fun daysBetween(from: DateFields, to: DateFields): Double {
        val a = EcmaTime.utc(from.year, from.month - 1, from.day)
        val b = EcmaTime.utc(to.year, to.month - 1, to.day)
        val days = (b - a) / EcmaTime.MS_PER_DAY
        return if (days.isNaN()) days else Math.round(days).toDouble()
    }

    /** The date of a time value; null for NaN. */
    internal fun fromTimeValue(timeValue: Double): CalendarDate? {
        if (timeValue.isNaN()) return null
        val date = EcmaTime.dateOf(timeValue)
        val weekday = Math.floorMod(date.toEpochDay() + THURSDAY, DAYS_IN_WEEK.toLong()).toInt()
        return CalendarDate(date.year, date.monthValue, date.dayOfMonth, weekday)
    }

    /** `String(number)`: NaN and the infinities by name (a key field is never written as JSON). */
    private fun number(value: Double): String =
        when {
            value.isNaN() -> "NaN"
            value.isInfinite() -> if (value > 0) "Infinity" else "-Infinity"
            else -> jsNumberText(value)
        }
}
