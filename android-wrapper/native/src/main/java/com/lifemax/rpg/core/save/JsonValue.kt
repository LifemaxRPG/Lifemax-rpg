package com.lifemax.rpg.core.save

/**
 * A JSON value exactly as the page's `JSON.parse` would see it. The save codec keeps the whole
 * stored save in this form, so a field native does not model is carried through untouched.
 *
 * Equality is JSON-value equality: object key order is ignored, array order is not, strings compare
 * by their UTF-16 units, and numbers by their binary64 value ([JsonNumber]).
 */
sealed interface JsonValue {
    /** The kind of value, for refusals that must not repeat the value itself. */
    val kind: JsonKind
}

/** The six JSON kinds. */
enum class JsonKind {
    OBJECT,
    ARRAY,
    STRING,
    NUMBER,
    BOOLEAN,
    NULL,
}

/**
 * A JSON object; [members] keep the order the text gave them (a repeated key keeps its first
 * place).
 */
class JsonObject(val members: Map<String, JsonValue>) : JsonValue {
    override val kind: JsonKind
        get() = JsonKind.OBJECT

    operator fun get(key: String): JsonValue? = members[key]

    operator fun contains(key: String): Boolean = key in members

    override fun equals(other: Any?): Boolean = other is JsonObject && members == other.members

    override fun hashCode(): Int = members.hashCode()

    override fun toString(): String = JsonWriter.write(this)

    companion object {
        val EMPTY = JsonObject(emptyMap())
    }
}

/** A JSON array. */
class JsonArray(val items: List<JsonValue>) : JsonValue {
    override val kind: JsonKind
        get() = JsonKind.ARRAY

    override fun equals(other: Any?): Boolean = other is JsonArray && items == other.items

    override fun hashCode(): Int = items.hashCode()

    override fun toString(): String = JsonWriter.write(this)

    companion object {
        val EMPTY = JsonArray(emptyList())
    }
}

/** A JSON string; [value] may hold lone surrogates, as a JavaScript string can. */
data class JsonString(val value: String) : JsonValue {
    override val kind: JsonKind
        get() = JsonKind.STRING
}

/**
 * A JSON number. [value] is what `JSON.parse` makes of it; [lexeme] is the text it was read from,
 * or null for a number native computed. A number read from the save is written back from its
 * [lexeme], so it keeps the same text as well as the same value; a computed one is written as
 * `JSON.stringify` writes it ([JsonWriter]).
 *
 * Two numbers are equal when their values are the same double, bit for bit (every NaN alike): `-0`
 * and `0` differ, and the lexeme does not count (`1` and `1.0` are the same number, as in
 * JavaScript).
 */
class JsonNumber(val value: Double, val lexeme: String? = null) : JsonValue {
    override val kind: JsonKind
        get() = JsonKind.NUMBER

    override fun equals(other: Any?): Boolean =
        other is JsonNumber && value.toBits() == other.value.toBits()

    override fun hashCode(): Int = value.toBits().hashCode()

    override fun toString(): String = JsonWriter.write(this)
}

/** `true` or `false`. */
enum class JsonBool(val value: Boolean) : JsonValue {
    TRUE(true),
    FALSE(false);

    override val kind: JsonKind
        get() = JsonKind.BOOLEAN

    companion object {
        fun of(value: Boolean): JsonBool = if (value) TRUE else FALSE
    }
}

/** `null`. */
data object JsonNull : JsonValue {
    override val kind: JsonKind
        get() = JsonKind.NULL
}

/**
 * JSON-value equality under the page's normal form (`JSON.stringify`): `-0` equals `0`, and a
 * non-finite number equals `null`, because that is what the page would store for it.
 */
fun JsonValue.sameStoredValue(other: JsonValue): Boolean {
    val a = storedForm(this)
    val b = storedForm(other)
    return when {
        a is JsonObject && b is JsonObject ->
            a.members.keys == b.members.keys &&
                a.members.all { (k, v) -> v.sameStoredValue(b.members.getValue(k)) }
        a is JsonArray && b is JsonArray ->
            a.items.size == b.items.size &&
                a.items.indices.all { a.items[it].sameStoredValue(b.items[it]) }
        a is JsonNumber && b is JsonNumber -> a.value == b.value
        else -> a == b
    }
}

private fun storedForm(value: JsonValue): JsonValue =
    if (value is JsonNumber && !value.value.isFinite()) JsonNull else value
