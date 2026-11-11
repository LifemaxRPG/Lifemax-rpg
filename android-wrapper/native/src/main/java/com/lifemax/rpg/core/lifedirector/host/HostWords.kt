package com.lifemax.rpg.core.lifedirector.host

import com.lifemax.rpg.core.lifedirector.CalendarDate
import com.lifemax.rpg.core.lifedirector.CalendarMath
import com.lifemax.rpg.core.lifedirector.EcmaTime
import com.lifemax.rpg.core.lifedirector.LocalParts
import com.lifemax.rpg.core.lifedirector.PageZone
import com.lifemax.rpg.core.lifedirector.WallClock
import com.lifemax.rpg.core.lifedirector.ZoneNames
import kotlin.math.floor

/**
 * The page's words and option tables the host commands use (`logic.js`, lines given at each), and
 * its clock helpers over the engine's (LDT-4, LDT-6, LDT-11, LDT-12). Times are written as the page
 * writes them in `en-US` (HC-6): `Wed, Nov 4` (`fDay`), `10:00 PM` (`fHm`).
 */
object HostWords {
    private const val MINUTES_PER_HOUR = 60
    private const val HOURS_PER_DAY = 24
    private const val NOON = 12
    private const val MS_PER_MINUTE = 60_000.0
    private const val MS_PER_SECOND = 1000.0
    private const val MINUTES_PER_DAY = 1440.0
    private val DAYS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    private val MONTHS =
        listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    /** `logic.js:1486-1487` ROUTINES and MEAL_RANGE: key, lowest, highest, step. */
    val ROUTINES =
        listOf(
            Range("morningRoutineMinutes", 0.0, 120.0, 5.0),
            Range("windDownMinutes", 0.0, 120.0, 5.0),
            Range("mealMinutes", 10.0, 90.0, 5.0),
        )

    /** `logic.js:1657-1662` PREF_RANGES and GRACE. */
    val PREF_RANGES =
        listOf(
            Range("maxDailyGoalMinutes", 30.0, 480.0, 15.0),
            Range("minDailyRecoveryMinutes", 30.0, 360.0, 15.0),
            Range("maxConsecutiveHighLoadMinutes", 30.0, 240.0, 15.0),
            Range("missGraceMinutes", 15.0, 240.0, 15.0),
        )

    /** `logic.js:1656, 1663, 1664`: the values `Do.pref` accepts for each key. */
    val PREF_OPTIONS =
        mapOf(
            "intensityLevel" to listOf("LIGHT", "BALANCED", "AGGRESSIVE", "HARDCORE"),
            "planningHorizonDays" to listOf("7", "14"),
            "consequenceMode" to listOf("LENIENT", "MODERATE", "HARDCORE_RPG"),
        )

    /** `logic.js:1556-1557` RULE_KINDS and RULE_LOADS. */
    val RULE_KINDS = listOf("WORK", "CLASS", "APPOINTMENT", "UNAVAILABLE")
    val RULE_LOADS = listOf("MIXED_MODERATE", "PHYSICAL_HEAVY", "COGNITIVE_HEAVY", "LIGHT")

    /** `logic.js:1455` KIND_LABEL. */
    val KIND_LABEL =
        mapOf(
            "WORK" to "Work",
            "CLASS" to "School / class",
            "APPOINTMENT" to "Regular commitment",
            "UNAVAILABLE" to "Unavailable",
        )

    /** `logic.js:1613-1614` CUSTOM_TAGS and GOAL_LEVELS (keys). */
    val CUSTOM_TAGS =
        listOf(
            "fitness.strength",
            "fitness.cardio",
            "fitness.mobility",
            "study.deep",
            "reading",
            "language",
            "creative.practice",
            "business",
            "admin.finance",
            "chores",
            "social",
            "wellbeing.mindfulness",
        )
    val GOAL_LEVELS = listOf("NOVICE", "INTERMEDIATE", "ADVANCED")

    /** `logic.js:2215-2229` disruptParts: the options of the parts that have them, by key. */
    val DISRUPT_OPTIONS: Map<String, List<Any>> =
        mapOf(
            "block" to listOf(1.0, 2.0, 3.0, 4.0, 6.0),
            "sick" to listOf(1.0, 2.0, 3.0),
            "energy" to listOf("LOW", "NORMAL", "HIGH"),
        )

    /** `logic.js:2215-2229` disruptParts: the keys of the parts whose command is `disrupt`. */
    val DISRUPT_KINDS = listOf("work", "block", "day", "sick")

    /** `logic.js:3398` DISRUPT_ID: the id prefix of each kind. */
    val DISRUPT_ID = mapOf("work" to "c", "block" to "b", "day" to "d", "sick" to "s")

    /** `logic.js:3395` REMOVE_WHY. */
    val REMOVE_WHY =
        mapOf(
            "running" to "Its timer is running. Stop the timer first.",
            "done" to "It is already done, so it stays.",
            "part" to "It is already part done, so it stays.",
            "off" to "It is already off the plan.",
        )

    /** `logic.js:279` XP_WORDS. */
    val XP_WORDS =
        mapOf(
            "HOST_NOT_CONNECTED" to "recorded (XP needs LifeMax)",
            "HOST_READ_ONLY" to "XP not saved: this version is read-only for your save",
            "HOST_SAVE_FAILED" to "XP not saved (storage full?)",
        )

    /** A stepper's range: key, lowest, highest, step. */
    data class Range(val key: String, val low: Double, val high: Double, val step: Double)

    /** The zone of [name] as the page's Intl reads it; an unknown name throws `RangeError`. */
    fun zone(name: String): PageZone =
        ZoneNames.resolve(name) ?: throw JsThrow("RangeError", "Invalid time zone specified: $name")

    /** LDT-4 `localParts(ep, zone)`. */
    fun parts(ep: Double, zone: String): LocalParts = WallClock.localParts(ep, zone(zone))

    /** LDT-12 `ymdKey(localParts(ep, zone))`: the local date's key. */
    fun dayKey(ep: Double, zone: String): String = CalendarMath.key(parts(ep, zone).date.fields)

    /** LDT-11 `ymdAdd`. */
    fun plusDays(date: CalendarDate, days: Int): CalendarDate =
        CalendarMath.plusDays(date, days) ?: throw JsThrow("RangeError", "Invalid time value")

    /** LDT-6 to LDT-9 `zonedToEpoch(y, mo, d, minute, zone)`. */
    fun zoned(y: Double, mo: Double, d: Double, minute: Double, zone: String): Double =
        WallClock.instantOf(y, mo, d, minute, zone(zone))

    /** `logic.js:148` weekKey: the key of the Monday of [now]'s local week. */
    fun weekKey(now: Double, zone: String): String {
        val p = parts(now, zone)
        return CalendarMath.key(plusDays(p.date, -((p.weekday + 6) % 7)).fields)
    }

    /** `logic.js:17` fDay: `Wed, Nov 4` in [zone]. */
    fun day(ep: Double, zone: String): String {
        val p = parts(ep, zone)
        return DAYS[p.weekday] + ", " + MONTHS[p.month - 1] + " " + p.day
    }

    /**
     * `logic.js:1470` fHm: [m] minutes after midnight as `h:mm AM` (`Date.UTC(2020, 0, 1, floor(m /
     * 60) % 24, m % 60)` written in UTC).
     */
    fun hm(m: Double): String {
        val hours = floor(m / MINUTES_PER_HOUR) % HOURS_PER_DAY
        val ms = EcmaTime.utc(2020.0, 0.0, 1.0, hours, m % MINUTES_PER_HOUR)
        if (ms.isNaN()) return "Invalid Date"
        val minuteOfDay =
            Math.floorMod(floor(ms / MS_PER_MINUTE).toLong(), MINUTES_PER_DAY.toLong())
        val h = (minuteOfDay / MINUTES_PER_HOUR).toInt()
        val mi = (minuteOfDay % MINUTES_PER_HOUR).toInt()
        val h12 = if (h % NOON == 0) NOON else h % NOON
        return h12.toString() +
            ":" +
            mi.toString().padStart(2, '0') +
            if (h < NOON) " AM" else " PM"
    }

    /** `logic.js:602` handleLabel. */
    fun handleLabel(kind: Any?, title: Any?, end: String): String {
        if (kind == "SLEEP") return if (end == "start") "Bedtime" else "Wake up"
        val name =
            if (Js.truthy(title)) Js.text(title) else KIND_LABEL[kind as? String] ?: "Commitment"
        return name + if (end == "start") " starts" else " ends"
    }

    /** The current time in epoch seconds, as `nowEpoch()` reads `Date.now()` (`logic.js:12`). */
    fun seconds(nowMs: Double): Double = floor(nowMs / MS_PER_SECOND)
}
