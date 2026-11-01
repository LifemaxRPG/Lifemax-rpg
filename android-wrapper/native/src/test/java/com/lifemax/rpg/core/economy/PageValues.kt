package com.lifemax.rpg.core.economy

/*
 * How the page reads a recorded JSON value (ECONOMY_SPEC section 0.1). The adapters use these to
 * turn a case's arguments into this module's types, as loosely as the page itself would.
 */

/** JavaScript `Number(v)` on a JSON value (NUM-3): absent is NaN, null and blank are 0. */
internal fun num(value: Any?, present: Boolean = true): Double =
    when {
        !present -> Double.NaN
        value == null -> 0.0
        value is Number -> value.toDouble()
        value is Boolean -> if (value) 1.0 else 0.0
        value is String -> jsNumber(value)
        value is List<*> ->
            when (value.size) {
                0 -> 0.0
                1 -> num(value[0])
                else -> Double.NaN
            }
        else -> Double.NaN
    }

/**
 * JavaScript `String(v)` for a form field: absent and null read as blank, whole numbers without
 * `.0`.
 */
internal fun text(value: Any?): String =
    when (value) {
        null -> ""
        is Double ->
            if (value == Math.rint(value) && !value.isInfinite()) value.toLong().toString()
            else value.toString()
        else -> value.toString()
    }

/** JavaScript truthiness. */
internal fun truthy(value: Any?): Boolean =
    when (value) {
        null -> false
        is Boolean -> value
        is Double -> value != 0.0 && !value.isNaN()
        is String -> value.isNotEmpty()
        else -> true
    }

@Suppress("UNCHECKED_CAST")
internal fun Any?.obj(): Map<String, Any?> = this as? Map<String, Any?> ?: emptyMap()

internal fun Any?.list(): List<Any?> = this as? List<Any?> ?: emptyList()

/** A number of a JSON object, NaN when absent. */
internal fun Map<String, Any?>.num(key: String): Double = num(this[key], containsKey(key))

internal fun Map<String, Any?>.str(key: String): String? = this[key]?.let(::text)
