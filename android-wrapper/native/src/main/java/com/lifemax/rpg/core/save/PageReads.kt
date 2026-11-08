package com.lifemax.rpg.core.save

import com.lifemax.rpg.core.economy.jsNumber

/**
 * How one save value is read into the model: the page's own reading (ECONOMY_SPEC NUM-3), and, in
 * [SaveReading.STRICT], a record of every value whose JSON type the page never writes there. Both
 * readings give the same model value; strictness only decides whether the save is refused.
 *
 * `null` and a missing key are told apart where the page tells them apart: a number missing is NaN
 * (`Number(undefined)`) and `null` is 0 (`Number(null)`); an optional field ([given]) treats both
 * as "not set".
 */
internal class PageReads(private val reading: SaveReading) {
    val problems = mutableListOf<SaveProblem>()

    /** One place in the save: a tracked [field] and the path inside it. */
    data class At(val field: String, val path: String = "") {
        fun key(name: String) = At(field, "$path.$name")

        /** A key the owner chose (a skill, a day): never named in a refusal. */
        fun anyKey() = At(field, "$path.*")

        fun index(i: Int) = At(field, "$path[$i]")
    }

    private fun check(value: JsonValue?, at: At, expected: String, vararg kinds: JsonKind) {
        if (reading != SaveReading.STRICT || value == null || value.kind in kinds) return
        problems += SaveProblem(at.field, at.path, expected, value.kind.name)
    }

    /** `Number(v)`: missing is NaN, null 0. Strict: a number or null. */
    fun number(value: JsonValue?, at: At): Double {
        check(value, at, "number", JsonKind.NUMBER, JsonKind.NULL)
        return pageNumber(value)
    }

    /** `String(v)` for a field read as text: missing and null are blank. Strict: text or null. */
    fun text(value: JsonValue?, at: At): String {
        check(value, at, "text", JsonKind.STRING, JsonKind.NULL)
        return pageText(value)
    }

    /** Text that may be unset: missing and null are null. */
    fun optionalText(value: JsonValue?, at: At): String? = given(value)?.let { text(it, at) }

    /** Truthiness. Strict: a boolean, a number or null. */
    fun flag(value: JsonValue?, at: At): Boolean {
        check(value, at, "boolean", JsonKind.BOOLEAN, JsonKind.NUMBER, JsonKind.NULL)
        return truthy(value)
    }

    /**
     * An object's members; missing, null and anything else read as empty. Strict: object or null.
     */
    fun members(value: JsonValue?, at: At): Map<String, JsonValue> {
        check(value, at, "object", JsonKind.OBJECT, JsonKind.NULL)
        return (value as? JsonObject)?.members.orEmpty()
    }

    /** An array's items; missing, null and anything else read as empty. Strict: array or null. */
    fun items(value: JsonValue?, at: At): List<JsonValue> {
        check(value, at, "array", JsonKind.ARRAY, JsonKind.NULL)
        return (value as? JsonArray)?.items.orEmpty()
    }

    /** An element that must be an object (a record); anything else reads as an empty record. */
    fun record(value: JsonValue, at: At): Map<String, JsonValue> {
        check(value, at, "object", JsonKind.OBJECT)
        return (value as? JsonObject)?.members.orEmpty()
    }

    /** A map of numbers (`{attr: xp}`). */
    fun numbers(value: JsonValue?, at: At): Map<String, Double> =
        members(value, at).mapValues { number(it.value, at.anyKey()) }

    companion object {
        /** The value when the field is set: missing and null are both "not set". */
        fun given(value: JsonValue?): JsonValue? = value?.takeUnless { it == JsonNull }

        /** NUM-3, `Number(v)` on a JSON value. */
        fun pageNumber(value: JsonValue?): Double =
            when (value) {
                null -> Double.NaN
                JsonNull -> 0.0
                is JsonNumber -> value.value
                is JsonBool -> if (value.value) 1.0 else 0.0
                is JsonString -> jsNumber(value.value)
                is JsonArray ->
                    when (value.items.size) {
                        0 -> 0.0
                        1 -> pageNumber(value.items[0])
                        else -> Double.NaN
                    }
                is JsonObject -> Double.NaN
            }

        /** `String(v)` as the economy reads a text field (missing and null are blank). */
        fun pageText(value: JsonValue?): String =
            when (value) {
                null,
                JsonNull -> ""
                is JsonString -> value.value
                is JsonNumber ->
                    when {
                        value.value.isNaN() -> "NaN"
                        value.value.isInfinite() -> if (value.value > 0) "Infinity" else "-Infinity"
                        else -> jsNumberText(value.value)
                    }
                is JsonBool -> value.value.toString()
                is JsonArray -> value.items.joinToString(",") { pageText(it) }
                is JsonObject -> "[object Object]"
            }

        /** JavaScript truthiness. */
        fun truthy(value: JsonValue?): Boolean =
            when (value) {
                null,
                JsonNull -> false
                is JsonBool -> value.value
                is JsonNumber -> value.value != 0.0 && !value.value.isNaN()
                is JsonString -> value.value.isNotEmpty()
                is JsonArray,
                is JsonObject -> true
            }
    }
}
