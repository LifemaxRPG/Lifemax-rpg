package com.lifemax.rpg.core.lifedirector.readmodels

import com.lifemax.rpg.core.economy.jsNumber
import com.lifemax.rpg.core.lifedirector.CalendarMath
import com.lifemax.rpg.core.lifedirector.EcmaTime
import com.lifemax.rpg.core.lifedirector.PageRangeError
import com.lifemax.rpg.core.lifedirector.PageTypeError
import com.lifemax.rpg.core.lifedirector.PageValues
import com.lifemax.rpg.core.lifedirector.PageZone
import com.lifemax.rpg.core.lifedirector.WallClock
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import kotlin.math.floor

/** The phrases part b reads, by the function of `logic.js` that writes them inline (RM-21). */
internal object PanelPhrases {
    val REQUIRED: Map<String, Set<String>> =
        mapOf(
            "goalCards" to
                setOf("custom", "sep", "level", "pausedTail", "sessions", "week", "space", "dash") +
                    setOf("times", "comma", "projected", "period", "resume", "pause"),
            "nativeGoals" to setOf("percent", "remove", "add"),
            "questsModel" to
                setOf("notDetected", "missing", "comma", "period", "nextSuffix", "allDayReminder") +
                    setOf("donePeriod", "session", "plural", "scheduled", "couldNotFit", "locked") +
                    setOf("daily", "due", "noDeadline", "toneOk", "tonePassive", "toneNo") +
                    setOf("tracked", "allDay", "of", "earnedTail", "earned", "inProgress", "empty"),
            "nativeSettings" to setOf("restTokens", "restTail"),
            "graceLabel" to setOf("minuteMark"),
            "diagnosticsLine" to
                setOf("engine", "sep", "ms", "planned", "noPlan", "events", "host", "connected") +
                    setOf("missing", "comma", "rejected"),
            "timeModel" to
                setOf("off", "less", "more", "stepSleep", "stepOk", "stepCurrent", "stepWork") +
                    setOf(
                        "stepGoals",
                        "stepBuild",
                        "ledeFirst",
                        "lede",
                        "sleepTitle",
                        "sleepNote",
                    ) +
                    setOf("sleepCentre", "weeknightBed", "bedtime", "wakeUp", "weekendToggle") +
                    setOf(
                        "weekendBed",
                        "weekendWake",
                        "saveSleep",
                        "saveSleepFirst",
                        "commitTitle",
                    ) +
                    setOf(
                        "dotBlock",
                        "dotWork",
                        "sep",
                        "commitEmpty",
                        "commitAdd",
                        "routinesTitle",
                    ) +
                    setOf(
                        "routinesText",
                        "turnOff",
                        "turnOn",
                        "shorter",
                        "longer",
                        "routinesNote",
                    ) +
                    setOf("goalsTitle", "goalsEmpty", "goalsAdd", "goalsManage", "buildTitle") +
                    setOf("zone", "buildTail", "replan"),
            "daysText" to
                setOf("rotating", "onOff", "offTail", "everyDay", "weekdays", "weekends") +
                    setOf("sunThu", "friSat", "space"),
            "ruleText" to setOf("sep", "dash", "travel"),
            "hourWord" to setOf("midnight", "noon", "am", "pm", "pad", "oclock"),
            "ruleForm" to
                setOf("editPrefix", "addTitle", "name", "type", "rotating", "days", "daysOn") +
                    setOf("daysOff", "firstDay", "starts", "ends", "travel", "unwind", "tiring") +
                    setOf("note", "save", "add", "cancel", "remove"),
            "goalTypeModel" to
                setOf("title", "text", "comma", "customIcon", "customTitle", "customSub"),
            "goalFormModel" to
                setOf("name", "namePlaceholder", "language", "languageValue", "activity") +
                    setOf("minutes", "perWeek", "physical", "mental", "priority", "experience") +
                    setOf("customTitle", "foldSummary", "foldNote", "total", "totalPlaceholder") +
                    setOf("unit", "unitPlaceholder", "rate", "ratePlaceholder", "by", "save") +
                    setOf("cancel"),
            "activityAtForm" to
                setOf("title", "text", "name", "activity", "date", "start", "duration", "note") +
                    setOf("save", "cancel"),
            "disruptParts" to
                setOf("workTitle", "workButton", "date", "travel", "from", "until", "blockTitle") +
                    setOf("hourMark", "dayTitle", "dayButton", "sickTitle", "day", "plural") +
                    setOf("sickNote", "energyTitle", "low", "normal", "high", "energyNote"),
            "moveTimes" to setOf("space"),
            "moveModel" to setOf("titleOpen", "titleClose", "none", "notePrefix", "noteTail"),
            "workRules" to setOf("work"),
            "shifts" to setOf("keySep", "work", "oneKey", "extra"),
            "handleLabel" to setOf("bedtime", "wakeUp", "commitment", "starts", "ends"),
            "dragPreview" to setOf("ofSleep"),
            "nativeHandles" to setOf("space"),
            "dialHandles" to setOf("keySep"),
            "tagIcon" to setOf("fallback"),
        )
}

/**
 * The JavaScript the read models of part b perform on stored values, beyond [PageValues]: a member
 * read that throws on `undefined` and `null` as the page's does, `+` of two stored values, a list's
 * `length`, `includes` and `join`, and `Number.isSafeInteger`. A Kotlin `null` is `undefined`.
 */
internal object Js {
    /** `value.key`: a TypeError on undefined and null; a text's or list's `length`; else absent. */
    fun get(value: JsonValue?, key: String): JsonValue? =
        when (value) {
            null,
            JsonNull -> throw PageTypeError("Cannot read properties of $value (reading '$key')")
            is JsonObject -> value[key]
            is JsonArray -> if (key == "length") JsonNumber(value.items.size.toDouble()) else null
            is JsonString ->
                if (key == "length") JsonNumber(value.value.length.toDouble()) else null
            else -> null
        }

    /** `a + b`: text when either side is text (lists and objects count as their text). */
    fun add(a: JsonValue?, b: JsonValue?): JsonValue {
        val x = primitive(a)
        val y = primitive(b)
        return if (x is JsonString || y is JsonString)
            JsonString(PageValues.text(x) + PageValues.text(y))
        else JsonNumber(PageValues.number(x) + PageValues.number(y))
    }

    /** `value.length` as a number of items: a TypeError on undefined and null, NaN when absent. */
    fun length(value: JsonValue?): Double = PageValues.number(get(value, "length"))

    /** A list's items; a TypeError for anything that has no `map`/`filter`/`find`. */
    fun items(value: JsonValue?, what: String): List<JsonValue> =
        (value as? JsonArray)?.items ?: throw PageTypeError("$what is not a list")

    /** `(value || [])` as a list's items. */
    fun itemsOrNone(value: JsonValue?, what: String): List<JsonValue> =
        if (PageValues.truthy(value)) items(value, what) else emptyList()

    /** `list.includes(x)` (SameValueZero). */
    fun includes(list: List<JsonValue>, x: JsonValue?): Boolean =
        list.any {
            PageValues.same(it, x) ||
                (it is JsonNumber && x is JsonNumber && it.value.isNaN() && x.value.isNaN())
        }

    /** `list.join(sep)`: undefined and null items empty. */
    fun join(list: List<JsonValue?>, sep: String): String =
        list.joinToString(sep) { if (it == null || it == JsonNull) "" else PageValues.text(it) }

    /** `value.toLowerCase()`: a TypeError for anything but text. */
    fun lower(value: JsonValue?): String =
        (value as? JsonString)?.value?.lowercase(java.util.Locale.ROOT)
            ?: throw PageTypeError("toLowerCase is not a function")

    /** `Number.isSafeInteger(n)`. */
    fun safeInteger(n: Double): Boolean =
        n.isFinite() && n == floor(n) && kotlin.math.abs(n) <= MAX_SAFE

    /** `Number(text)` by the page's grammar. */
    fun number(text: String): Double = jsNumber(text)

    /** `{key: value}` with the members holding undefined left out, in the order given. */
    fun obj(vararg members: Pair<String, JsonValue?>): JsonObject {
        val m = LinkedHashMap<String, JsonValue>()
        for ((k, v) in members) if (v != null) m[k] = v
        return JsonObject(m)
    }

    fun s(text: String): JsonString = JsonString(text)

    fun n(value: Double): JsonNumber = JsonNumber(value)

    fun n(value: Int): JsonNumber = JsonNumber(value.toDouble())

    fun b(value: Boolean): JsonValue =
        if (value) com.lifemax.rpg.core.save.JsonBool.TRUE
        else com.lifemax.rpg.core.save.JsonBool.FALSE

    fun list(items: List<JsonValue>): JsonArray = JsonArray(items)

    private fun primitive(value: JsonValue?): JsonValue? =
        if (value is JsonArray || value is JsonObject) JsonString(PageValues.text(value)) else value

    private const val MAX_SAFE = 9007199254740991.0
}

/**
 * RM-22: times and dates as the page writes them in `en-US`, the locale it is recorded in (`fmt`
 * with the device's locale; logic.js:19-24, 618-622, 1481): `fTime` `9:05 AM`, `fDay` `Wed, Oct
 * 28`, `fHm` a minute of the day in UTC, and the dial's hour words with a 12-hour clock (`H12`). A
 * moment a JavaScript Date cannot hold throws a RangeError, as `Intl.DateTimeFormat.format` does.
 */
internal class ClockWords(private val words: PanelWords) {
    /** `en-US`'s own short names (Intl's locale data, not the page's words). */
    private val weekdays = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    private val months =
        listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    /** `fTime(ep, zone)`: `h:mm AM` in [zone]. */
    fun time(epochSeconds: Double, zone: PageZone): String {
        val p = parts(epochSeconds, zone)
        return twelve(p.hour, p.minute)
    }

    /** `fDay(ep, zone)`: `Wed, Oct 28` in [zone]. */
    fun day(epochSeconds: Double, zone: PageZone): String {
        val p = parts(epochSeconds, zone)
        return weekdays[p.weekday] + ", " + months[p.month - 1] + " " + p.day
    }

    /**
     * `fHm(m)`: minute [m] of the day (`Date.UTC(2020, 0, 1, floor(m / 60) % 24, m % 60)`, UTC).
     */
    fun hm(m: Double): String {
        val ms = EcmaTime.utc(YEAR, 0.0, 1.0, floor(m / HOUR) % HOURS, m % HOUR)
        if (ms.isNaN()) throw PageRangeError("Invalid time value")
        val minute = Math.floorMod(floor(ms / MS_PER_MINUTE).toLong(), MINUTES_PER_DAY)
        return twelve((minute / HOUR_L).toInt(), (minute % HOUR_L).toInt())
    }

    /** `hourWord(h)` (logic.js:620): `Midnight`, `Noon`, `3 AM`, or `03:00` on a 24-hour clock. */
    fun hourWord(h: Int): String {
        fun w(k: String) = words.phrase("hourWord", k)
        return if (words.h12)
            when {
                h == 0 -> w("midnight")
                h == NOON -> w("noon")
                else -> (h % NOON).toString() + if (h < NOON) w("am") else w("pm")
            }
        else h.toString().padStart(2, w("pad")[0]) + w("oclock")
    }

    /** `U.ymdKey(U.localParts(ep, zone))`. */
    fun dateKey(epochSeconds: Double, zone: PageZone): String =
        CalendarMath.key(parts(epochSeconds, zone).date)

    private fun parts(epochSeconds: Double, zone: PageZone) =
        try {
            WallClock.localParts(epochSeconds, zone)
        } catch (e: IllegalArgumentException) {
            throw PageRangeError("Invalid time value")
        }

    private fun twelve(h: Int, mi: Int): String {
        val h12 = if (h % NOON == 0) NOON else h % NOON
        return h12.toString() +
            ":" +
            mi.toString().padStart(2, '0') +
            if (h < NOON) " AM" else " PM"
    }

    private companion object {
        const val YEAR = 2020.0
        const val HOUR = 60.0
        const val HOUR_L = 60L
        const val HOURS = 24.0
        const val NOON = 12
        const val MS_PER_MINUTE = 60_000.0
        const val MINUTES_PER_DAY = 1440L
    }
}
