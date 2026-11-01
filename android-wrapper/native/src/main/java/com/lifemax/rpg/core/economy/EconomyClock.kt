package com.lifemax.rpg.core.economy

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * The device clock and time zone (TIME-1 to TIME-8). Day keys are `YYYY-MM-DD` strings compared as
 * strings (TIME-5), exactly as the save stores them; a record whose date cannot be read lands on
 * [INVALID_DAY], which never equals a real key.
 */
class EconomyClock(val now: Long, val zone: ZoneId) {
    /** TIME-3. */
    val today: String = localDate(now)

    /** TIME-2: the calendar date of an instant in the device zone. */
    fun localDate(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().toString()

    /**
     * TIME-7: today's local date moved back [days] days; every "last 7 days" window is
     * `daysAgo(6)..today`.
     */
    fun daysAgo(days: Long): String = LocalDate.parse(today).minusDays(days).toString()

    /** TIME-8: the Monday that starts this week. */
    val weekMonday: String =
        LocalDate.parse(today).let { it.minusDays(it.dayOfWeek.value - 1L) }.toString()

    /** TIME-4: the local day of a record's ISO date. */
    fun dayOf(isoDate: String?): String = parseInstant(isoDate)?.let(::localDate) ?: INVALID_DAY

    /**
     * An instant from the date forms this area writes (ISO UTC, or a local date and time); null
     * when unreadable.
     */
    fun parseInstant(text: String?): Long? {
        if (text.isNullOrBlank()) return null
        return runCatching { OffsetDateTime.parse(text).toInstant().toEpochMilli() }
            .recoverCatching { LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli() }
            .recoverCatching {
                LocalDate.parse(text).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            }
            .getOrNull()
    }

    /** The local wall-clock time [time] (`HH:mm:ss`) on [day], as an instant. */
    fun at(day: String, time: String): Long? = parseInstant("${day}T$time")

    companion object {
        const val INVALID_DAY = "Invalid Date"
        private val ISO =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

        /** A record's date: ISO-8601 in UTC with milliseconds (TIME-4). */
        fun iso(epochMillis: Long): String = ISO.format(Instant.ofEpochMilli(epochMillis))

        /** TIME-6: days since 1970-01-01 of a day key; null for a key that is not a date. */
        fun dayNumber(day: String): Long? =
            runCatching { LocalDate.parse(day).toEpochDay() }.getOrNull()

        /** TIME-6: the inverse of [dayNumber]. */
        fun dayKey(dayNumber: Long): String = LocalDate.ofEpochDay(dayNumber).toString()

        internal const val DAY_MILLIS = 86_400_000L
    }
}
