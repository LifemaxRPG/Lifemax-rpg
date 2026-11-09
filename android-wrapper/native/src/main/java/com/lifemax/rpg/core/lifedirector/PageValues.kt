package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.economy.jsNumber
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import com.lifemax.rpg.core.save.jsNumberText
import kotlin.math.floor

/** The engine stopped where the page throws a `TypeError` (LDG-5): a method missing on a value. */
class PageTypeError(message: String) : RuntimeException(message)

/** The engine stopped where the page throws a `RangeError` (LDG-5): a date `Intl` cannot read. */
class PageRangeError(message: String) : RuntimeException(message)

/** The engine refused its input with an `Error` of its own (LDG-5). */
class EngineRefusal(message: String) : RuntimeException(message)

/**
 * The few JavaScript value rules the availability layer depends on (LDG-1, LDG-6), applied to
 * stored values as `JSON.parse` gives them. A Kotlin `null` stands for a member that is absent
 * (`undefined`); [JsonNull] is a stored `null`. Nothing here is a general JavaScript: only the
 * operations the engine performs on its input.
 */
internal object PageValues {
    private val ZERO = JsonNumber(0.0)

    /** The member [key] of [value] when it is an object; absent for anything else. */
    fun member(value: JsonValue?, key: String): JsonValue? = (value as? JsonObject)?.get(key)

    /** Whether the page counts [value] as true: not absent, null, false, 0, NaN or empty text. */
    fun truthy(value: JsonValue?): Boolean =
        when (value) {
            null,
            JsonNull -> false
            is JsonBool -> value.value
            is JsonNumber -> !(value.value == 0.0 || value.value.isNaN())
            is JsonString -> value.value.isNotEmpty()
            else -> true
        }

    /** `first || second`. */
    fun or(first: JsonValue?, second: JsonValue?): JsonValue? = if (truthy(first)) first else second

    /** `value || fallback` for a text fallback. */
    fun or(value: JsonValue?, fallback: String): JsonValue =
        if (truthy(value)) value!! else JsonString(fallback)

    /** `value || 0`, as a number value. */
    fun orZero(value: JsonValue?): JsonValue = if (truthy(value)) value!! else ZERO

    /** Not absent and not null: the page's `value != null`. */
    fun present(value: JsonValue?): Boolean = value != null && value != JsonNull

    /**
     * `Number(value)`: absent is NaN, null and false 0, true 1, text by the page's number grammar,
     * an array or object through its text (`[30]` is 30, `{}` NaN).
     */
    fun number(value: JsonValue?): Double =
        when (value) {
            null -> Double.NaN
            JsonNull -> 0.0
            is JsonBool -> if (value.value) 1.0 else 0.0
            is JsonNumber -> value.value
            is JsonString -> jsNumber(value.value)
            is JsonArray,
            is JsonObject -> jsNumber(text(value))
        }

    /**
     * `String(value)`: absent `undefined`, numbers as JavaScript writes them, an array as its items
     * joined by `,` (null items empty), an object `[object Object]`.
     */
    fun text(value: JsonValue?): String =
        when (value) {
            null -> "undefined"
            JsonNull -> "null"
            is JsonBool -> value.value.toString()
            is JsonNumber -> numberText(value.value)
            is JsonString -> value.value
            is JsonArray -> value.items.joinToString(",") { if (it == JsonNull) "" else text(it) }
            is JsonObject -> "[object Object]"
        }

    /** `String(number)`: NaN and the infinities by name, `-0` as `0`. */
    fun numberText(value: Double): String =
        when {
            value.isNaN() -> "NaN"
            value == Double.POSITIVE_INFINITY -> "Infinity"
            value == Double.NEGATIVE_INFINITY -> "-Infinity"
            else -> jsNumberText(value)
        }

    /**
     * `left < right` for two stored values: when both are text (an array or object counts as its
     * text) they compare by UTF-16 units; otherwise as numbers, and NaN is never less.
     */
    fun less(left: JsonValue?, right: JsonValue?): Boolean {
        val a = primitive(left)
        val b = primitive(right)
        return if (a is JsonString && b is JsonString) a.value < b.value else number(a) < number(b)
    }

    /**
     * `number + value` (LDG-6): text when [value] is text (or an array or object), so `3 + "8"` is
     * `"38"`; a number otherwise.
     */
    fun plus(number: Double, value: JsonValue?): JsonValue =
        when (val p = primitive(value)) {
            is JsonString -> JsonString(numberText(number) + p.value)
            else -> JsonNumber(number + number(p))
        }

    /** `value + number`, read back as a number (LDS-7: `"30" + 15` is `"3015"`). */
    fun plusAsNumber(value: JsonValue?, number: Double): Double =
        when (val p = primitive(value)) {
            is JsonString -> jsNumber(p.value + numberText(number))
            else -> number(p) + number
        }

    /** `Math.round`: ties toward +infinity, and -0 for -0 and for values from -0.5 up to 0. */
    fun round(value: Double): Double {
        if (value.isNaN() || value.isInfinite() || value == 0.0) return value
        if (value < 0 && value >= -HALF) return -0.0
        val down = floor(value)
        return if (value - down >= HALF) down + 1 else down
    }

    /** LDG-1 `clamp` of a number: `x < lo ? lo : x > hi ? hi : x` (NaN passes through). */
    fun clamp(value: Double, low: Double, high: Double): Double =
        if (value < low) low else if (value > high) high else value

    /** LDG-1 `clamp` of a stored value: the value itself (not its number) when it is in range. */
    fun clamp(value: JsonValue, low: Double, high: Double): JsonValue {
        val n = number(value)
        return if (n < low) JsonNumber(low) else if (n > high) JsonNumber(high) else value
    }

    /** `list.includes(x)` for a number: the same number (`-0` is `0`, NaN finds NaN). */
    fun includes(values: List<Double>, x: Double): Boolean =
        values.any { it == x || (it.isNaN() && x.isNaN()) }

    /** The order of a JavaScript comparator `a - b` read by a sort: NaN and 0 alike are "equal". */
    fun byDifference(difference: Double): Int =
        if (difference < 0) -1 else if (difference > 0) 1 else 0

    /** An array or object counts as its text; anything else as itself. */
    private fun primitive(value: JsonValue?): JsonValue? =
        if (value is JsonArray || value is JsonObject) JsonString(text(value)) else value

    private const val HALF = 0.5
}
