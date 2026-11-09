package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import com.lifemax.rpg.core.save.JsonWriter

/**
 * LDO-9 `inputHash` (`engine.js:1529-1532`): `stableId("in", A + B)` over the input without its
 * `clock` member, where B is its JSON text and A its JSON text written with the sorted list of its
 * top-level names as `JSON.stringify`'s replacer, which keeps, in every object at every depth, only
 * the members so named, in that sorted order. B covers the whole input, so the hash does too (plan
 * D12's "drops nested keys" is true of A only).
 */
object InputHash {
    private const val CLOCK = "clock"

    fun of(input: JsonValue?): String {
        val pick = (input as? JsonObject)?.members?.filterKeys { it != CLOCK } ?: emptyMap()
        val names = pick.keys.distinct().sorted()
        val listed = StringBuilder().also { listed(it, JsonObject(pick), names) }.toString()
        val full = StringBuilder().also { full(it, JsonObject(pick)) }.toString()
        return EngineHash.stableId("in", listed + full)
    }

    /** `JSON.stringify(value, names)`. */
    private fun listed(out: StringBuilder, value: JsonValue, names: List<String>) {
        when (value) {
            is JsonObject -> {
                out.append('{')
                var first = true
                for (k in names) {
                    val v = value[k] ?: continue
                    if (!first) out.append(',')
                    first = false
                    out.append(JsonWriter.write(JsonString(k))).append(':')
                    listed(out, v, names)
                }
                out.append('}')
            }
            is JsonArray -> {
                out.append('[')
                value.items.forEachIndexed { i, v ->
                    if (i > 0) out.append(',')
                    listed(out, v, names)
                }
                out.append(']')
            }
            else -> out.append(JsonWriter.write(value))
        }
    }

    /**
     * `JSON.stringify(value)`, an object's members in the order the page's objects keep them
     * (PageKeys).
     */
    private fun full(out: StringBuilder, value: JsonValue) {
        when (value) {
            is JsonObject -> {
                out.append('{')
                PageKeys.ordered(value.members.keys).forEachIndexed { i, k ->
                    if (i > 0) out.append(',')
                    out.append(JsonWriter.write(JsonString(k))).append(':')
                    full(out, value.members.getValue(k))
                }
                out.append('}')
            }
            is JsonArray -> {
                out.append('[')
                value.items.forEachIndexed { i, v ->
                    if (i > 0) out.append(',')
                    full(out, v)
                }
                out.append(']')
            }
            else -> out.append(JsonWriter.write(value))
        }
    }
}
