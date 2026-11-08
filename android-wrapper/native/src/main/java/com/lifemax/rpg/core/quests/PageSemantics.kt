package com.lifemax.rpg.core.quests

import com.lifemax.rpg.core.economy.jsNumber
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import com.lifemax.rpg.core.save.PageReads
import com.lifemax.rpg.core.save.jsNumberText

/**
 * Where the page itself would throw a `TypeError` (reading a property of `null`, calling `filter`
 * on something that is not an array). It never leaves this package: [HandDealer] turns it into
 * [HandStep.Threw] with the save as the page leaves it at that point.
 */
internal class PageThrew(reason: String) : RuntimeException(reason, null, false, false)

/**
 * JavaScript's own semantics on save values, as far as the Daily Hand's code reaches them. A Kotlin
 * `null` is `undefined` (a key the object does not have); [JsonNull] is `null`.
 */
internal object Js {
    /** `String(v)`, as `+` concatenation and property keys use it. */
    fun string(value: JsonValue?): String =
        when (value) {
            null -> "undefined"
            JsonNull -> "null"
            is JsonString -> value.value
            is JsonNumber -> numberText(value.value)
            is JsonBool -> value.value.toString()
            is JsonArray -> value.items.joinToString(",") { if (it == JsonNull) "" else string(it) }
            is JsonObject -> "[object Object]"
        }

    /** `Number.prototype.toString()`, NaN and the infinities included. */
    fun numberText(value: Double): String =
        when {
            value.isNaN() -> "NaN"
            value == Double.POSITIVE_INFINITY -> "Infinity"
            value == Double.NEGATIVE_INFINITY -> "-Infinity"
            else -> jsNumberText(value)
        }

    /** `Number(v)` (ToNumber; arrays and objects through their text). */
    fun number(value: JsonValue?): Double =
        when (value) {
            null -> Double.NaN
            JsonNull -> 0.0
            is JsonNumber -> value.value
            is JsonBool -> if (value.value) 1.0 else 0.0
            is JsonString -> jsNumber(value.value)
            is JsonArray,
            is JsonObject -> jsNumber(string(value))
        }

    /** `Number(v) || 0`: NaN and zero (either sign) become 0. */
    fun numberOrZero(value: JsonValue?): Double =
        number(value).let { if (it.isNaN() || it == 0.0) 0.0 else it }

    /** JavaScript truthiness. */
    fun truthy(value: JsonValue?): Boolean = PageReads.truthy(value)

    /**
     * `a === b` between two values read from the save: arrays and objects are never the same
     * object, numbers compare as doubles (NaN never equal, 0 equals -0).
     */
    fun strictEquals(a: JsonValue?, b: JsonValue?): Boolean =
        when {
            a == null || b == null -> a == null && b == null
            a is JsonNumber && b is JsonNumber -> a.value == b.value
            a is JsonArray || a is JsonObject -> false
            else -> a == b
        }

    /** `a === 'text'`. */
    fun isText(value: JsonValue?, text: String): Boolean =
        value is JsonString && value.value == text

    /**
     * `v.key` for a named property the save may hold: an object's member, `undefined` on any other
     * value, and a `TypeError` on `undefined` and `null`.
     */
    fun prop(value: JsonValue?, key: String): JsonValue? =
        when (value) {
            null,
            JsonNull ->
                throw PageThrew("Cannot read properties of ${string(value)} (reading '$key')")
            is JsonObject -> value[key]
            else -> null
        }

    /**
     * `v.length === 3`-style reads: an array's size, a string's UTF-16 length, an object's own
     * `length` member when it is a number; null when the value has no length.
     */
    fun length(value: JsonValue?): Double? =
        when (value) {
            is JsonArray -> value.items.size.toDouble()
            is JsonString -> value.value.length.toDouble()
            is JsonObject -> (value["length"] as? JsonNumber)?.value
            else -> null
        }

    /** The property names every plain object inherits: `DH_CLASS_ATTR[name]` is then truthy. */
    val OBJECT_PROTOTYPE_KEYS =
        setOf(
            "constructor",
            "__defineGetter__",
            "__defineSetter__",
            "hasOwnProperty",
            "__lookupGetter__",
            "__lookupSetter__",
            "isPrototypeOf",
            "propertyIsEnumerable",
            "toString",
            "valueOf",
            "__proto__",
            "toLocaleString",
        )

    /**
     * An object with [key] set to [value]: an existing key keeps its place, a new one comes last.
     */
    fun JsonObject.with(key: String, value: JsonValue): JsonObject =
        JsonObject(LinkedHashMap(members).apply { put(key, value) })

    /**
     * `o[key] = value` on a plain object, in JavaScript's key order: array-index keys (canonical
     * integers below 2^32 - 1) come first in ascending order, every other key in the order it was
     * added.
     */
    fun JsonObject.withIndexKey(index: Int, value: JsonValue): JsonObject {
        val all = LinkedHashMap(members).apply { put(index.toString(), value) }
        val (indexes, names) = all.entries.partition { isArrayIndex(it.key) }
        val ordered = LinkedHashMap<String, JsonValue>()
        indexes.sortedBy { it.key.toLong() }.forEach { ordered[it.key] = it.value }
        names.forEach { ordered[it.key] = it.value }
        return JsonObject(ordered)
    }

    /** `a[index] = value` on an array; a gap left behind is a hole, which JSON writes as null. */
    fun JsonArray.withItem(index: Int, value: JsonValue): JsonArray {
        val list = items.toMutableList()
        while (list.size <= index) list += JsonNull
        list[index] = value
        return JsonArray(list)
    }

    private fun isArrayIndex(key: String): Boolean =
        key.isNotEmpty() &&
            key.length <= 10 &&
            key.all { it in '0'..'9' } &&
            (key == "0" || key[0] != '0') &&
            key.toLong() < 4294967295L
}
