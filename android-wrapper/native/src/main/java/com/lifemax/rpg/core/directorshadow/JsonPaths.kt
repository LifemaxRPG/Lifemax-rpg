package com.lifemax.rpg.core.directorshadow

import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonValue
import com.lifemax.rpg.core.shadow.ShadowRedactor
import kotlin.math.abs
import kotlin.math.max

/**
 * One place where the page and native differ: its redacted path, and whether it is a last-bit
 * number (SH-32).
 */
data class PathDifference(val path: String, val lastBit: Boolean)

/**
 * Where two JSON values differ, as redacted paths (SH-31): members by name when the name is a
 * schema word (lower camel case, letters only), otherwise as `*#` and the name's salted hash (ids,
 * dates, anything the owner made); list items by index. Values are never named. Equality is the
 * page's normal form: `-0` is `0`, and NaN and the infinities are `null`, as `JSON.stringify`
 * writes them. Two finite numbers within one unit in the last place are reported as last-bit
 * differences (OQ-12, OQ-15).
 */
class JsonPaths(private val redactor: ShadowRedactor) {
    private val word = Regex("[a-z][A-Za-z]{0,39}")

    fun member(name: String): String = if (word.matches(name)) name else "*#" + redactor.hash(name)

    /** At most [limit] differences of [page] and [native], under the name [at]. */
    fun diff(
        page: JsonValue?,
        native: JsonValue?,
        at: String,
        limit: Int = DirectorShadowLimits.MAX_PATHS,
    ): List<PathDifference> {
        val out = ArrayList<PathDifference>()
        walk(stored(page), stored(native), at, out, limit)
        return out
    }

    private fun stored(v: JsonValue?): JsonValue? =
        if (v is JsonNumber && !v.value.isFinite()) JsonNull else v

    private fun walk(
        a: JsonValue?,
        b: JsonValue?,
        at: String,
        out: MutableList<PathDifference>,
        limit: Int,
    ) {
        if (out.size >= limit) return
        when {
            a is JsonObject && b is JsonObject -> {
                for (k in (a.members.keys + b.members.keys)) {
                    walk(stored(a.members[k]), stored(b.members[k]), "$at.${member(k)}", out, limit)
                    if (out.size >= limit) return
                }
            }
            a is JsonArray && b is JsonArray -> {
                if (a.items.size != b.items.size) out += PathDifference("$at[#]", false)
                for (i in 0 until minOf(a.items.size, b.items.size)) {
                    walk(stored(a.items[i]), stored(b.items[i]), "$at[$i]", out, limit)
                    if (out.size >= limit) return
                }
            }
            a is JsonNumber && b is JsonNumber -> {
                if (a.value == b.value) return
                val ulp = max(Math.ulp(a.value), Math.ulp(b.value))
                out += PathDifference(at, abs(a.value - b.value) <= ulp)
            }
            a == b -> Unit
            else -> out += PathDifference(at, false)
        }
    }
}
