package com.lifemax.rpg.core.golden

import org.json.JSONArray
import org.json.JSONObject

/**
 * Golden JSON as plain Kotlin values, and JSON-value equality. Objects are ordered maps, arrays
 * lists, numbers doubles and JSON null is null. A key that is absent stays absent, so "missing" and
 * "null" stay different.
 */
object GoldenJson {
    /**
     * [value] from org.json as plain values. Numbers go through their text, so a decimal is the
     * nearest double.
     */
    fun plain(value: Any?): Any? =
        when (value) {
            is JSONObject ->
                value.keys().asSequence().associateWithTo(linkedMapOf()) { plain(value.get(it)) }
            is JSONArray -> (0 until value.length()).map { plain(value.get(it)) }
            JSONObject.NULL -> null
            is Number -> value.toString().toDouble()
            else -> value
        }

    /**
     * The JSON normal form the recorder writes (`JSON.stringify`): NaN and the infinities become
     * null, -0 becomes 0, and every number is a double.
     */
    fun normal(value: Any?): Any? =
        when (value) {
            is Number ->
                value.toDouble().let { if (it.isFinite()) (if (it == 0.0) 0.0 else it) else null }
            is Map<*, *> -> value.entries.associate { (k, v) -> k.toString() to normal(v) }
            is List<*> -> value.map(::normal)
            else -> value
        }

    /**
     * JSON-value equality: key order is ignored, array order is not, numbers are equal as doubles.
     */
    fun same(a: Any?, b: Any?): Boolean = firstDifference(a, b) == null

    /**
     * Where [actual] first differs from [expected], with both values; null when they are the same.
     */
    fun firstDifference(actual: Any?, expected: Any?, path: String = ""): String? {
        val a = normal(actual)
        val e = normal(expected)
        fun here() = "${path.ifEmpty { "(value)" }}: expected ${show(e)}, got ${show(a)}"
        return when {
            a is Map<*, *> && e is Map<*, *> ->
                (e.keys + a.keys).firstNotNullOfOrNull { key ->
                    when {
                        !a.containsKey(key) -> "$path.$key: expected ${show(e[key])}, missing"
                        !e.containsKey(key) -> "$path.$key: not expected, got ${show(a[key])}"
                        else -> firstDifference(a[key], e[key], "$path.$key")
                    }
                }
            a is List<*> && e is List<*> ->
                if (a.size != e.size) "${here()} (length ${e.size}, got ${a.size})"
                else a.indices.firstNotNullOfOrNull { firstDifference(a[it], e[it], "$path[$it]") }
            a is Double && e is Double -> if (a == e) null else here()
            else -> if (a == e) null else here()
        }
    }

    /** Compact JSON, for failure messages. */
    fun show(value: Any?): String =
        when (value) {
            null -> "null"
            is String -> JSONObject.quote(value)
            is Double ->
                if (value == Math.rint(value) && !value.isInfinite()) value.toLong().toString()
                else value.toString()
            is Number -> show(value.toDouble())
            is Map<*, *> ->
                value.entries.joinToString(",", "{", "}") {
                    "${JSONObject.quote(it.key.toString())}:${show(it.value)}"
                }
            is List<*> -> value.joinToString(",", "[", "]") { show(it) }
            else -> value.toString()
        }
}
