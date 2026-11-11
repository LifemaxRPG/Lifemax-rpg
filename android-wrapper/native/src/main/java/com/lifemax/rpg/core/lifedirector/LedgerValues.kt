package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.lifedirector.PageValues.number
import com.lifemax.rpg.core.lifedirector.PageValues.text
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/**
 * The ledger's strict decoder refused a stored shape that the page's own writers never produce and
 * that native does not reproduce (LED-21, the deviations of LIFE_DIRECTOR_LEDGER_SPEC.md section
 * 6): a list where the page keeps an object, or a member named like one of `Object.prototype`'s.
 */
class LedgerRefusal(message: String) : RuntimeException(message)

/**
 * LED-21: the JavaScript value rules the ledger code depends on, applied to stored values as
 * `JSON.parse` gives them (a Kotlin `null` is a member that is absent, [JsonNull] a stored `null`).
 * Nothing is mutated: every write returns a new object, in which a member written again keeps its
 * place, as JavaScript keeps it.
 */
internal object LedgerValues {
    /** `Object.prototype`'s own members: a plain object "has" them all (LG03). */
    val INHERITED =
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

    private fun nullRead(key: String): Nothing =
        throw PageTypeError("Cannot read properties of null (reading '$key')")

    private fun assignTo(key: String): Nothing =
        throw PageTypeError("Cannot create property '$key' on a primitive")

    private fun notAList(): Nothing = throw LedgerRefusal("a list where the page keeps an object")

    private fun inherited(key: String): Nothing = throw LedgerRefusal("the inherited member '$key'")

    /** `text[key]` for the index and `length` keys a text answers; absent for any other. */
    private fun ofText(s: String, key: String): JsonValue? {
        if (key == "length") return JsonNumber(s.length.toDouble())
        val i = key.toIntOrNull()?.takeIf { it >= 0 && it.toString() == key } ?: return null
        return s.getOrNull(i)?.let { JsonString(it.toString()) }
    }

    /**
     * `value.name` for a member name the engine writes in its code (`playerId`, `events`, `tier`):
     * an object's own member, a list's or a text's `length`, absent for anything else; null and
     * absent throw `TypeError`.
     */
    fun field(value: JsonValue?, name: String): JsonValue? =
        when (value) {
            null,
            JsonNull -> nullRead(name)
            is JsonObject -> value[name]
            is JsonArray -> if (name == "length") JsonNumber(value.items.size.toDouble()) else null
            is JsonString -> ofText(value.value, name)
            else -> null
        }

    /**
     * `map[key]` for a key that comes from data (an event id, a spacing key, a day id): an own
     * member; a name of [INHERITED] or a list as the map is refused (native keeps only own members,
     * LG03); a text answers its index and `length` keys.
     */
    fun lookup(map: JsonValue?, key: String): JsonValue? =
        when (map) {
            null,
            JsonNull -> nullRead(key)
            is JsonObject -> map[key] ?: if (key in INHERITED) inherited(key) else null
            is JsonArray -> notAList()
            is JsonString -> if (key in INHERITED) inherited(key) else ofText(map.value, key)
            else -> if (key in INHERITED) inherited(key) else null
        }

    /**
     * Whether `map[key]` is truthy, where only that is asked: as [lookup], but an inherited member
     * (a function or `Object.prototype`) counts as true, as it does on the page (LG03).
     */
    fun isSet(map: JsonValue?, key: String): Boolean =
        when (map) {
            null,
            JsonNull -> nullRead(key)
            is JsonObject -> map[key]?.let(PageValues::truthy) ?: (key in INHERITED)
            is JsonArray -> notAList()
            is JsonString -> key in INHERITED || PageValues.truthy(ofText(map.value, key))
            else -> key in INHERITED
        }

    /**
     * `target[key] = value` in strict mode: a copy of the object with the member set (absent
     * [value]: set to `undefined`, which the page's JSON leaves out); a primitive, null or absent
     * target throws `TypeError`; a list is refused.
     */
    fun put(target: JsonValue?, key: String, value: JsonValue?): JsonObject =
        when (target) {
            is JsonObject -> {
                if (key == "__proto__") inherited(key)
                val m = LinkedHashMap(target.members)
                if (value == null) m.remove(key) else m[key] = value
                JsonObject(m)
            }
            is JsonArray -> notAList()
            null,
            JsonNull -> nullRead(key)
            else -> assignTo(key)
        }

    /** `delete target[key]` on an object. */
    fun remove(target: JsonObject, key: String): JsonObject =
        JsonObject(LinkedHashMap(target.members).apply { remove(key) })

    /** An array or object counts as its text in `+` and comparisons; anything else as itself. */
    private fun primitive(v: JsonValue?): JsonValue? =
        if (v is JsonArray || v is JsonObject) JsonString(text(v)) else v

    /**
     * `a + b`: when either side is text (an array or object counts as its text) the texts joined
     * (`undefined` and `null` by name), else the numbers added (absent is NaN).
     */
    fun add(a: JsonValue?, b: JsonValue?): JsonValue {
        val x = primitive(a)
        val y = primitive(b)
        return if (x is JsonString || y is JsonString) JsonString(text(x) + text(y))
        else JsonNumber(number(x) + number(y))
    }

    fun add(a: JsonValue?, b: Double): JsonValue = add(a, JsonNumber(b))

    /** `value.length`: a list's size, a text's length, an object's `length` member. */
    fun lengthOf(value: JsonValue?): JsonValue? = field(value, "length")

    /**
     * `list.includes(x)`: a list holds an item of the same kind and value (`NaN` finds `NaN`; two
     * stored objects are never the same); a text holds `String(x)` as a part; anything else throws
     * `TypeError`.
     */
    fun includes(list: JsonValue?, x: JsonValue?): Boolean =
        when (list) {
            is JsonArray ->
                list.items.any { item ->
                    when {
                        x == null -> false
                        item is JsonNumber && x is JsonNumber ->
                            item.value == x.value || (item.value.isNaN() && x.value.isNaN())
                        item is JsonArray || item is JsonObject -> false
                        else -> item == x
                    }
                }
            is JsonString -> list.value.contains(text(x))
            else -> throw PageTypeError("includes is not a function")
        }

    /**
     * `list.concat(x).slice(-keep)`: a list with [x] added (a list's items each; absent as null),
     * then its last [keep] items; for text, the texts joined and its last [keep] UTF-16 units.
     */
    fun concatKeepLast(list: JsonValue?, x: JsonValue?, keep: Int): JsonValue =
        when (list) {
            is JsonArray -> {
                val all = list.items + ((x as? JsonArray)?.items ?: listOf(x ?: JsonNull))
                JsonArray(all.takeLast(keep))
            }
            is JsonString -> JsonString((list.value + text(x)).takeLast(keep))
            else -> throw PageTypeError("concat is not a function")
        }

    /**
     * What `Object.assign({}, value)` copies: an object's members, a list's items and a text's
     * characters by index; nothing from anything else.
     */
    fun ownMembers(value: JsonValue?): LinkedHashMap<String, JsonValue> =
        when (value) {
            is JsonObject -> LinkedHashMap(value.members)
            is JsonArray ->
                value.items.withIndex().associateTo(LinkedHashMap()) { (i, v) -> i.toString() to v }
            is JsonString ->
                value.value.withIndex().associateTo(LinkedHashMap()) { (i, c) ->
                    i.toString() to JsonString(c.toString())
                }
            else -> LinkedHashMap()
        }

    /** The items `for (const x of value)` visits: a list's items, a text's characters. */
    fun iterate(value: JsonValue?): List<JsonValue> =
        when (value) {
            is JsonArray -> value.items
            is JsonString -> value.value.map { JsonString(it.toString()) }
            else -> throw PageTypeError("is not iterable")
        }

    /** The items `value.map`, `.filter` and `.some` visit: only a list has them. */
    fun items(value: JsonValue?): List<JsonValue> =
        (value as? JsonArray)?.items ?: throw PageTypeError("is not a function")

    /** `value === text`. */
    fun isText(value: JsonValue?, text: String): Boolean =
        value is JsonString && value.value == text

    fun num(v: Double): JsonValue = JsonNumber(v)

    fun bool(v: Boolean): JsonValue = JsonBool.of(v)

    fun str(v: String): JsonValue = JsonString(v)
}
