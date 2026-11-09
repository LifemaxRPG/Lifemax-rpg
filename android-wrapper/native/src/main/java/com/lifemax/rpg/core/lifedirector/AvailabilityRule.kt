package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.lifedirector.PageValues.clamp
import com.lifemax.rpg.core.lifedirector.PageValues.member
import com.lifemax.rpg.core.lifedirector.PageValues.number
import com.lifemax.rpg.core.lifedirector.PageValues.or
import com.lifemax.rpg.core.lifedirector.PageValues.orZero
import com.lifemax.rpg.core.lifedirector.PageValues.present
import com.lifemax.rpg.core.lifedirector.PageValues.round
import com.lifemax.rpg.core.lifedirector.PageValues.text
import com.lifemax.rpg.core.lifedirector.PageValues.truthy
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/** LDA-2: what a rule is for. The names are the page's. */
enum class RuleKind {
    WORK,
    CLASS,
    APPOINTMENT,
    UNAVAILABLE,
    SLEEP,
    PREFERRED;

    /** LDA-10: the kinds whose time is a commitment (commutes and decompression follow them). */
    val isCommitment: Boolean
        get() = this == WORK || this == CLASS || this == APPOINTMENT

    companion object {
        /** LDA-2: the stored kind if it is exactly one of the names, else from the legacy type. */
        fun of(kind: JsonValue?, legacyType: JsonValue?): RuleKind =
            entries.firstOrNull { kind is JsonString && kind.value == it.name }
                ?: LEGACY[text(legacyType)]
                ?: WORK

        /** LDA-2: the older `ruleType` names (including the misspelling the page accepts). */
        private val LEGACY =
            mapOf(
                "HARD_COMMITMENT" to WORK,
                "SLEEP_WINDOW" to SLEEP,
                "UNAVAILABLE" to UNAVAILABLE,
                "PREFFERED_WINDOW" to PREFERRED,
                "PREFERRED_WINDOW" to PREFERRED,
            )
    }
}

/**
 * LDA-4: a rule that repeats every [cycleLength] days instead of every week: the days `anchorDate +
 * k * cycleLength + d` for each `d` in [activeDays]. [anchorDate] is kept as stored, because only
 * text can be read as a date (LDA-6).
 */
data class RuleRotation(
    val anchorDate: JsonValue,
    val cycleLength: Double,
    val activeDays: List<Double>,
)

/**
 * LDA-1: a recurring availability rule as the engine reads it. Minutes count from local midnight;
 * [durationMin] may run past midnight into the next day. Fields the engine passes on without
 * reading are kept as stored ([title], [load], [days] entries, [decompressMin], [validFrom],
 * [validTo]), because the page compares and prints them as they are.
 */
data class AvailabilityRule(
    val id: String,
    val kind: RuleKind,
    val title: JsonValue,
    val days: List<JsonValue>,
    val startMin: Double,
    val durationMin: Double,
    val commuteMin: Double,
    val decompressMin: JsonValue,
    val load: JsonValue,
    val rotation: RuleRotation?,
    val validFrom: JsonValue,
    val validTo: JsonValue,
) {
    /**
     * LDA-5 to LDA-7: whether the rule applies on [date]. Throws [PageTypeError] for a rotation
     * whose anchor is not text (LDA-6, [LD05]).
     */
    fun appliesOn(date: CalendarDate): Boolean {
        val key = JsonString(CalendarMath.key(date))
        if (truthy(validFrom) && PageValues.less(key, validFrom)) return false
        if (truthy(validTo) && PageValues.less(validTo, key)) return false
        val r =
            rotation ?: return days.any { it is JsonNumber && it.value == date.weekday.toDouble() }
        val anchor =
            r.anchorDate as? JsonString ?: throw PageTypeError("key.split is not a function")
        val from = CalendarMath.parse(anchor.value)?.fields ?: INVALID
        val n = CalendarMath.daysBetween(from, date.fields)
        val index = ((n % r.cycleLength) + r.cycleLength) % r.cycleLength
        return PageValues.includes(r.activeDays, index)
    }

    /**
     * LDA-8 `materializeRule`: the rule's intervals from the day before [days] to the day after
     * them, each from its date's local [startMin] to `startMin + durationMin` (so past midnight it
     * ends on the next date), by the page's wall clock rules (LDT-6 to LDT-9).
     */
    fun intervals(days: List<PlanDay>, zone: PageZone): List<RuleInterval> {
        val first = days.firstOrNull() ?: throw PageTypeError("no plan days")
        val out = ArrayList<RuleInterval>()
        for (i in -1..days.size) {
            val date = requireNotNull(CalendarMath.plusDays(first.date, i))
            if (!appliesOn(date)) continue
            val start = AvailabilityTime.instant(date, startMin, zone)
            val end = AvailabilityTime.instant(date, startMin + durationMin, zone)
            out += RuleInterval(this, date, start, end)
        }
        return out
    }

    companion object {
        private val INVALID = DateFields(Double.NaN, Double.NaN, Double.NaN)
        private val EVERY_DAY = List(DAYS_IN_WEEK) { JsonNumber(it.toDouble()) }
        private const val DAYS_IN_WEEK = 7
        private const val LAST_WEEKDAY = 6.0
        private const val LAST_START = 1439.0
        private const val SHORTEST = 15.0
        private const val LONGEST = 1440.0
        private const val DEFAULT_LENGTH = 60.0
        private const val LONGEST_COMMUTE = 240.0
        private const val LONGEST_DECOMPRESS = 120.0
        private const val DEFAULT_LOAD = "MIXED_MODERATE"

        /**
         * LDA-1 `normalizeRule`: the stored rule [raw] (the [index]th of its list) as the engine
         * reads it. A stored `null` throws [PageTypeError]; any other value that is not an object
         * has no members and gives a rule of defaults.
         */
        fun of(raw: JsonValue?, index: Int): AvailabilityRule {
            if (!present(raw)) throw PageTypeError("Cannot read properties of $raw")
            fun field(key: String) = member(raw, key)
            val kind = RuleKind.of(field("kind"), field("ruleType"))
            val days =
                when (val d = field("days")) {
                    is JsonArray -> d.items
                    else ->
                        if (present(field("dayOfWeek"))) listOf(field("dayOfWeek")!!) else EVERY_DAY
                }
            val start = field("startMin").takeIf(::present) ?: orZero(field("startMinuteOfDay"))
            val length =
                field("durationMin").takeIf(::present)
                    ?: or(field("durationMinutes"), JsonNumber(DEFAULT_LENGTH))
            return AvailabilityRule(
                id = text(or(or(field("id"), field("ruleId")), "rule$index")),
                kind = kind,
                title = or(field("title"), kind.name),
                days =
                    days
                        .filter { number(it) >= 0 && number(it) <= LAST_WEEKDAY }
                        .sortedWith(compareBy { text(it) }),
                startMin = clamp(round(number(start)), 0.0, LAST_START),
                durationMin = clamp(round(number(length)), SHORTEST, LONGEST),
                commuteMin =
                    clamp(round(number(orZero(field("commuteMin")))), 0.0, LONGEST_COMMUTE),
                decompressMin =
                    field("decompressMin")?.takeIf(::present)?.let {
                        clamp(it, 0.0, LONGEST_DECOMPRESS)
                    } ?: JsonNull,
                load = or(or(field("load"), field("shiftIntensity")), DEFAULT_LOAD),
                rotation = rotationOf(field("rotation")),
                validFrom = or(field("validFrom"), JsonNull) ?: JsonNull,
                validTo = or(field("validTo"), JsonNull) ?: JsonNull,
            )
        }

        /** LDA-4: a rotation needs a truthy anchor and a cycle above 0; anything else is none. */
        private fun rotationOf(raw: JsonValue?): RuleRotation? {
            if (!truthy(raw) || !truthy(member(raw, "anchorDate"))) return null
            if (!(number(member(raw, "cycleLength")) > 0)) return null
            val active =
                when (val a = member(raw, "activeDays")) {
                    is JsonArray -> a.items.map(::number)
                    else ->
                        if (truthy(a)) throw PageTypeError("activeDays.map is not a function")
                        else emptyList()
                }
            return RuleRotation(
                member(raw, "anchorDate")!!,
                round(number(member(raw, "cycleLength"))),
                active,
            )
        }
    }
}

/** LDA-8: one interval of a rule, on the local [date] it starts from. */
data class RuleInterval(
    val rule: AvailabilityRule,
    val date: CalendarDate,
    val startEpoch: Double,
    val endEpoch: Double,
)

/** Wall clock instants as the availability layer asks for them (LDT-6, LDG-5). */
internal object AvailabilityTime {
    /**
     * `zonedToEpoch(date, minute)`: the page reads [minute] by `Date.UTC` (truncated, rolling into
     * the next day) and throws a `RangeError` when that is no date at all (a NaN minute).
     */
    fun instant(date: CalendarDate, minute: Double, zone: PageZone): Double {
        val at =
            WallClock.instantOf(
                date.year.toDouble(),
                date.month.toDouble(),
                date.day.toDouble(),
                minute,
                zone,
            )
        if (at.isNaN()) throw PageRangeError("Invalid time value")
        return at
    }
}
