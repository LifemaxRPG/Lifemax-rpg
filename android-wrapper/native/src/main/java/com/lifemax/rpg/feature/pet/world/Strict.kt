package com.lifemax.rpg.feature.pet.world

import org.json.JSONArray
import org.json.JSONObject

/*
 * Strict reading of the cat world's JSON (pet.world.v1). Every reader throws [WorldFormatException]
 * on a key missing or unknown, a value of the wrong kind (org.json would quietly turn a number into
 * a string), a number out of its range or a list over its length, so a malformed frame is refused
 * whole and the caller keeps its last good one. Messages name keys, never values: nothing private
 * reaches a log. Pure Kotlin and org.json: no Android type (HeadlessCheckTest).
 */

/** A frame, a blob or a command that is not the shape the contract says. [code] is for counting. */
class WorldFormatException(val code: String, detail: String) :
    IllegalArgumentException("$code: $detail")

internal fun fail(code: String, detail: String): Nothing = throw WorldFormatException(code, detail)

internal fun ensure(ok: Boolean, code: String, detail: () -> String) {
    if (!ok) fail(code, detail())
}

/** Throws unless this object has every one of [keys], and no key but those and [optional]. */
internal fun JSONObject.exactKeys(
    keys: Set<String>,
    what: String,
    optional: Set<String> = emptySet(),
) {
    val have = keys().asSequence().toSet()
    ensure(have.containsAll(keys) && (keys + optional).containsAll(have), "shape") {
        "$what keys differ: unknown ${have - keys - optional}, missing ${keys - have}"
    }
}

private fun JSONObject.need(key: String): Any {
    ensure(has(key), "shape") { "missing $key" }
    return get(key)
}

internal fun JSONObject.text(key: String, max: Int, pattern: Regex? = null): String {
    val v = need(key) as? String ?: fail("type", "$key is not a string")
    ensure(v.length <= max, "range") { "$key is too long" }
    ensure(pattern == null || pattern.matches(v), "range") { "$key is not in its form" }
    return v
}

internal fun JSONObject.flag(key: String): Boolean =
    need(key) as? Boolean ?: fail("type", "$key is not a boolean")

/** A whole number in [lo]..[hi] (a float with no fraction counts: JSON has one kind of number). */
internal fun JSONObject.whole(key: String, lo: Long, hi: Long): Long {
    val n = need(key) as? Number ?: fail("type", "$key is not a number")
    val whole = (n !is Double && n !is Float) || n.toDouble() % 1.0 == 0.0
    ensure(whole, "type") { "$key is not whole" }
    val v = n.toLong()
    ensure(v in lo..hi, "range") { "$key out of range" }
    return v
}

internal fun JSONObject.int(key: String, lo: Int, hi: Int): Int =
    whole(key, lo.toLong(), hi.toLong()).toInt()

/** A finite number in [lo]..[hi]. */
internal fun JSONObject.real(key: String, lo: Double, hi: Double): Double {
    val n = need(key) as? Number ?: fail("type", "$key is not a number")
    val v = n.toDouble()
    ensure(v.isFinite() && v >= lo && v <= hi, "range") { "$key out of range" }
    return v
}

internal fun JSONObject.isNullAt(key: String): Boolean = need(key) == JSONObject.NULL

/** The object at [key] with exactly [keys] (and perhaps [optional]). */
internal fun JSONObject.child(
    key: String,
    keys: Set<String>,
    optional: Set<String> = emptySet(),
): JSONObject =
    (need(key) as? JSONObject ?: fail("type", "$key is not an object")).also {
        it.exactKeys(keys, key, optional)
    }

/** The object at [key] whose keys are free (a map), at most [max] of them, each matching [id]. */
internal fun JSONObject.map(key: String, max: Int, id: Regex): JSONObject {
    val m = need(key) as? JSONObject ?: fail("type", "$key is not an object")
    ensure(m.length() <= max, "range") { "$key has too many entries" }
    for (k in m.keys()) ensure(id.matches(k), "range") { "$key has a key out of its form" }
    return m
}

internal fun JSONObject.list(key: String, max: Int): JSONArray {
    val a = need(key) as? JSONArray ?: fail("type", "$key is not a list")
    ensure(a.length() <= max, "range") { "$key is too long" }
    return a
}

internal fun JSONArray.objAt(i: Int, keys: Set<String>, what: String): JSONObject =
    (get(i) as? JSONObject ?: fail("type", "$what is not an object")).also {
        it.exactKeys(keys, what)
    }

internal fun JSONArray.textAt(i: Int, max: Int, pattern: Regex, what: String): String {
    val v = get(i) as? String ?: fail("type", "$what is not a string")
    ensure(v.length <= max && pattern.matches(v), "range") { "$what is not in its form" }
    return v
}

internal fun JSONArray.wholeAt(i: Int, lo: Long, hi: Long, what: String): Long {
    val n = get(i) as? Number ?: fail("type", "$what is not a number")
    ensure((n !is Double && n !is Float) || n.toDouble() % 1.0 == 0.0, "type") {
        "$what is not whole"
    }
    ensure(n.toLong() in lo..hi, "range") { "$what out of range" }
    return n.toLong()
}

/** The UTF-8 size of a JSON value as it is sent. */
internal fun jsonBytes(value: Any): Int = value.toString().toByteArray(Charsets.UTF_8).size

/** Rounds to three decimals, the precision the world is saved with. */
internal fun round3(v: Double): Double = Math.round(v * THOUSAND) / THOUSAND

private const val THOUSAND = 1000.0
