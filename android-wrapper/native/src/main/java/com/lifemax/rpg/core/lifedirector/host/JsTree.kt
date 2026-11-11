package com.lifemax.rpg.core.lifedirector.host

import com.lifemax.rpg.core.lifedirector.PageValues
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/** A JavaScript object as `logic.js` holds it in memory: members in the order they were added. */
typealias JsObj = LinkedHashMap<String, Any?>

/** A JavaScript array. */
typealias JsArr = ArrayList<Any?>

/**
 * A JavaScript error the page would throw at that point (HC-5): its [name] (`TypeError`) and
 * message, which a caller that catches it shows (the tick's banner, HC-79).
 */
class JsThrow(val name: String, message: String) : RuntimeException(message)

/**
 * The page's state in memory (HC-5): `logic.js` changes `ST` in place, and keeps references into it
 * (a session it found, then changed), so the host commands work on a mutable tree, not on
 * [JsonValue]. JavaScript's `undefined` (a member that is not there) is Kotlin `null`; JSON `null`
 * is [JsonNull]; numbers are [Double], text [String], booleans [Boolean]. The engine's ported
 * functions take [JsonValue]: [json] and [of] convert at that boundary.
 */
object Js {
    /** [v] as a mutable tree. */
    fun of(v: JsonValue?): Any? =
        when (v) {
            null -> null
            is JsonObject -> JsObj().also { o -> for ((k, x) in v.members) o[k] = of(x) }
            is JsonArray -> JsArr(v.items.size).also { a -> for (x in v.items) a.add(of(x)) }
            is JsonString -> v.value
            is JsonNumber -> v.value
            is JsonBool -> v.value
            JsonNull -> JsonNull
        }

    /**
     * [v] as JSON, as `JSON.stringify` would see it: a member holding `undefined` is left out, an
     * array item `undefined` is `null`.
     */
    fun json(v: Any?): JsonValue? =
        when (v) {
            null -> null
            is Map<*, *> -> {
                val m = LinkedHashMap<String, JsonValue>()
                for ((k, x) in v) json(x)?.let { m[k.toString()] = it }
                JsonObject(m)
            }
            is List<*> -> JsonArray(v.map { json(it) ?: JsonNull })
            is String -> JsonString(v)
            is Double -> JsonNumber(v)
            is Int -> JsonNumber(v.toDouble())
            is Long -> JsonNumber(v.toDouble())
            is Boolean -> JsonBool.of(v)
            is JsonValue -> v
            else -> JsonNull
        }

    /** [json], with `undefined` itself as JSON `null`. */
    fun value(v: Any?): JsonValue = json(v) ?: JsonNull

    /** `JSON.parse(JSON.stringify(v))`: a deep copy, NaN and the infinities as `null`. */
    fun copy(v: Any?): Any? =
        when (v) {
            is Map<*, *> -> {
                val o = JsObj()
                for ((k, x) in v) if (x != null) o[k.toString()] = copy(x)
                o
            }
            is List<*> -> JsArr(v.size).also { a -> for (x in v) a.add(copy(x) ?: JsonNull) }
            is Double -> if (v.isFinite()) (if (v == 0.0) 0.0 else v) else JsonNull
            else -> v
        }

    /** `Object.assign({}, a, b...)`: a shallow copy with later members over earlier ones. */
    fun assign(vararg parts: Map<String, Any?>?): JsObj {
        val o = JsObj()
        for (p in parts) if (p != null) o.putAll(p)
        return o
    }

    /** A new object of the pairs, in order. */
    fun obj(vararg pairs: Pair<String, Any?>): JsObj = JsObj().also { o -> o.putAll(pairs) }

    /** A new array holding [items]. */
    fun arrOf(items: Collection<Any?>): JsArr = JsArr(items)

    /** A new array of the items. */
    fun arr(vararg items: Any?): JsArr = JsArr(items.toList())

    /** [v] when it is an object. */
    fun o(v: Any?): JsObj? = asObj(v)

    @Suppress("UNCHECKED_CAST") private fun asObj(v: Any?): JsObj? = v as? JsObj

    /** [v] when it is an array. */
    @Suppress("UNCHECKED_CAST") fun a(v: Any?): JsArr? = v as? JsArr

    /** `v.key` where reading it from `null` or `undefined` throws (the page's `TypeError`). */
    fun get(v: Any?, key: String): Any? =
        when (v) {
            null ->
                throw JsThrow("TypeError", "Cannot read properties of undefined (reading '$key')")
            JsonNull ->
                throw JsThrow("TypeError", "Cannot read properties of null (reading '$key')")
            is Map<*, *> -> v[key]
            is String -> if (key == "length") v.length.toDouble() else null
            is List<*> -> if (key == "length") v.size.toDouble() else null
            else -> null
        }

    /** `v.key`, `undefined` for anything that is not an object. */
    fun at(v: Any?, key: String): Any? = (v as? Map<*, *>)?.get(key)

    /** An object member that must be an array, as the page iterates it (`|| []` applied). */
    fun list(v: Any?): JsArr = a(v) ?: JsArr()

    /** JavaScript truthiness. */
    fun truthy(v: Any?): Boolean =
        when (v) {
            null,
            JsonNull -> false
            is Boolean -> v
            is Double -> !(v == 0.0 || v.isNaN())
            is String -> v.isNotEmpty()
            else -> true
        }

    /** `Number(v)`. */
    fun num(v: Any?): Double = PageValues.number(json(v))

    /** `String(v)`. */
    fun text(v: Any?): String = if (v == null) "undefined" else PageValues.text(json(v))

    /** The text of a number as JavaScript writes it. */
    fun numText(v: Double): String = PageValues.numberText(v)

    /** `a === b` for primitives (objects: the same reference). */
    fun same(a: Any?, b: Any?): Boolean =
        when {
            a is Double && b is Double -> a == b
            a is Map<*, *> || a is List<*> -> a === b
            else -> a == b
        }

    /** `typeof v === 'boolean'`. */
    fun isBool(v: Any?): Boolean = v is Boolean

    /** `Number.isInteger(v)`. */
    fun isInteger(v: Any?): Boolean = v is Double && v.isFinite() && v == Math.floor(v)

    /** `Math.round`. */
    fun round(v: Double): Double = PageValues.round(v)
}
