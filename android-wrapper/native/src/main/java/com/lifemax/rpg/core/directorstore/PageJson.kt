package com.lifemax.rpg.core.directorstore

import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import com.lifemax.rpg.core.save.JsonWriter
import com.lifemax.rpg.core.save.jsNumberText

/**
 * The texts the page's store writes, from a [JsonValue] held as the page holds it in memory
 * (LIFE_DIRECTOR_STORE_SPEC.md LST-17, LST-22).
 *
 * [text] is `JSON.stringify(value)`: what the page writes after parsing a stored text (numbers as
 * `Number.prototype.toString` gives them, whatever text they were read from, `-0` as `0`, NaN and
 * the infinities as `null`), with an object's members in JavaScript's own order: array-index keys
 * ascending first, then the others in the order they were added ([JsValues.isArrayIndex]).
 * [preserving] writes the same layout but keeps every number's own text, so a value native did not
 * change is written back exactly as it was read; on a text the page wrote, the two are the same.
 */
object PageJson {
    /** `JSON.stringify(value)`. */
    fun text(value: JsonValue): String = write(value, Form.PAGE)

    /** The page's layout with each read number's own text kept ([JsonNumber.lexeme]). */
    fun preserving(value: JsonValue): String = write(value, Form.PRESERVING)

    /**
     * `JSON.stringify` of the value with NaN, the infinities and `-0` written `{"$num": "NaN"}` and
     * so on: the golden cases' text for a state, which tells those numbers apart.
     */
    fun canonical(value: JsonValue): String = write(value, Form.CANONICAL)

    /**
     * `JSON.stringify(value, null, 1)`: one space per level, `": "` after a key, `{}` and `[]`
     * empty.
     */
    fun indented(value: JsonValue): String =
        StringBuilder().also { appendIndented(it, value, 0) }.toString()

    /** An object's members in JavaScript's own-key order. */
    fun ordered(obj: JsonObject): List<Map.Entry<String, JsonValue>> {
        val entries = obj.members.entries
        if (entries.none { JsValues.isArrayIndex(it.key) }) return entries.toList()
        val (indexes, others) = entries.partition { JsValues.isArrayIndex(it.key) }
        return indexes.sortedBy { it.key.toLong() } + others
    }

    private enum class Form {
        PAGE,
        PRESERVING,
        CANONICAL,
    }

    private fun write(value: JsonValue, form: Form): String =
        StringBuilder().also { append(it, value, form) }.toString()

    private fun append(out: StringBuilder, value: JsonValue, form: Form) {
        when (value) {
            is JsonObject -> {
                out.append('{')
                ordered(value).forEachIndexed { i, (key, member) ->
                    if (i > 0) out.append(',')
                    out.append(JsonWriter.write(JsonString(key))).append(':')
                    append(out, member, form)
                }
                out.append('}')
            }
            is JsonArray -> {
                out.append('[')
                value.items.forEachIndexed { i, item ->
                    if (i > 0) out.append(',')
                    append(out, item, form)
                }
                out.append(']')
            }
            is JsonNumber -> out.append(number(value, form))
            is JsonString,
            is JsonBool,
            JsonNull -> out.append(JsonWriter.write(value))
        }
    }

    private fun number(n: JsonNumber, form: Form): String =
        when (form) {
            Form.PAGE -> jsNumberText(n.value)
            Form.PRESERVING -> n.lexeme ?: jsNumberText(n.value)
            Form.CANONICAL ->
                special(n.value)?.let { "{\"$NUM\":\"$it\"}" } ?: jsNumberText(n.value)
        }

    /** The name `{$num}` gives a number JSON cannot write, or null for an ordinary number. */
    fun special(n: Double): String? =
        when {
            n.isNaN() -> "NaN"
            n == Double.POSITIVE_INFINITY -> "Infinity"
            n == Double.NEGATIVE_INFINITY -> "-Infinity"
            n == 0.0 && 1.0 / n < 0 -> "-0"
            else -> null
        }

    private fun appendIndented(out: StringBuilder, value: JsonValue, level: Int) {
        val children: List<Pair<String?, JsonValue>> =
            when (value) {
                is JsonObject -> ordered(value).map { it.key to it.value }
                is JsonArray -> value.items.map { null to it }
                else -> {
                    append(out, value, Form.PAGE)
                    return
                }
            }
        val (open, close) = if (value is JsonObject) '{' to '}' else '[' to ']'
        if (children.isEmpty()) {
            out.append(open).append(close)
            return
        }
        out.append(open)
        children.forEachIndexed { i, (key, child) ->
            out.append(if (i > 0) ",\n" else "\n").append(" ".repeat(level + 1))
            if (key != null) out.append(JsonWriter.write(JsonString(key))).append(": ")
            appendIndented(out, child, level + 1)
        }
        out.append('\n').append(" ".repeat(level)).append(close)
    }

    private const val NUM = "\$num"
}
