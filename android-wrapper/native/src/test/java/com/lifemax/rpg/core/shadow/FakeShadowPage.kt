package com.lifemax.rpg.core.shadow

import com.lifemax.rpg.core.golden.GoldenFiles
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonRead
import com.lifemax.rpg.core.save.JsonReader
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import com.lifemax.rpg.core.save.JsonWriter
import com.lifemax.rpg.core.save.sameStoredValue

/**
 * The page's side of the transport, as `50-native-shadow.js` builds it (ECONOMY_SHADOW_PLAN 2.2,
 * 2.3): the base in parts, then one envelope per action whose before-delta goes from what was last
 * sent to the action's before-state and whose after-delta goes from there to its after-state; a
 * record key sends its changed records, or the whole key past [ShadowLimits.MAX_REC_CHANGES].
 */
class FakeShadowPage(val sid: String = "p-test", var epoch: Long = 0) {
    var seq = 0L
        private set

    private var head = 0L
    private var cache: Map<String, JsonValue>? = null

    /** The base parts of [state], each slice at most [slice] characters. */
    fun base(state: Map<String, JsonValue>, slice: Int = SLICE): List<String> {
        val text = JsonWriter.write(obj("keys" to JsonObject(shadow(state))))
        val s = ++seq
        head = s
        cache = shadow(state)
        var size = slice
        while (true) {
            val parts = text.chunked(size)
            val messages = parts.mapIndexed { i, p -> part(s, i, parts.size, p) }
            if (messages.all { it.length <= ShadowLimits.MAX_BASE_PART_CHARS }) return messages
            size = size * 3 / 4
        }
    }

    private fun part(s: Long, i: Int, parts: Int, p: String): String =
        JsonWriter.write(
            obj(
                "v" to num(1),
                "scope" to JsonString("economy"),
                "sid" to JsonString(sid),
                "epoch" to num(epoch),
                "seq" to num(s),
                "part" to num(i),
                "parts" to num(parts),
                "text" to JsonString(p),
            )
        )

    /** One action's envelope, moving the page's cache to [after]. */
    fun envelope(
        kind: String,
        before: Map<String, JsonValue>,
        after: Map<String, JsonValue>,
        toasts: List<String> = emptyList(),
        flags: List<String> = emptyList(),
        t0: Long = T0,
    ): String =
        send(Action(kind, before = before, after = after, toasts = toasts, flags = flags, t0 = t0))

    /**
     * One page action as `50-native-shadow.js` records it (ECONOMY_SHADOW_PLAN 2.3): its arguments,
     * outside answers ([env] beside `saves`), return value, toasts and writes.
     */
    data class Action(
        val kind: String,
        val args: List<JsonValue> = emptyList(),
        val before: Map<String, JsonValue>,
        val after: Map<String, JsonValue>,
        val returns: JsonValue = JsonNull,
        val toasts: List<String> = emptyList(),
        val saves: List<Boolean> = listOf(true),
        val env: Map<String, JsonValue> = emptyMap(),
        val flags: List<String> = emptyList(),
        val t0: Long = T0,
        val t1: Long = t0 + 5,
        val zone: String = "UTC",
        /** Minutes east of UTC at [t0]; null computes it from [zone]. */
        val offset: Int? = null,
        val locale: String = "en-US",
    )

    /** The envelope of [a], moving the page's cache to its after-state. */
    fun send(a: Action): String {
        val b = shadow(a.before)
        val after = shadow(a.after)
        val s = ++seq
        val env = LinkedHashMap<String, JsonValue>()
        if (a.saves.isNotEmpty())
            env["saves"] = JsonArray(a.saves.map { obj("ok" to JsonBool.of(it)) })
        env.putAll(a.env)
        val offset =
            a.offset
                ?: (java.time.ZoneId.of(a.zone)
                    .rules
                    .getOffset(java.time.Instant.ofEpochMilli(a.t0))
                    .totalSeconds / 60)
        val text =
            JsonWriter.write(
                obj(
                    "v" to num(1),
                    "scope" to JsonString("economy"),
                    "sid" to JsonString(sid),
                    "epoch" to num(epoch),
                    "seq" to num(s),
                    "base" to num(head),
                    "kind" to JsonString(a.kind),
                    "args" to JsonArray(a.args),
                    "t0" to num(a.t0),
                    "t1" to num(a.t1),
                    "zone" to JsonString(a.zone),
                    "offset" to num(offset),
                    "locale" to JsonString(a.locale),
                    "env" to JsonObject(env),
                    "before" to delta(cache ?: emptyMap(), b),
                    "after" to delta(b, after),
                    "returns" to a.returns,
                    "toasts" to JsonArray(a.toasts.map { JsonString(it) }),
                    "cost" to obj("ms" to num(1.5)),
                    "flags" to JsonArray(a.flags.map { JsonString(it) }),
                )
            )
        head = s
        cache = after
        return text
    }

    companion object {
        const val SLICE = 200_000
        const val T0 = 1_793_700_000_123L

        fun delta(from: Map<String, JsonValue>, to: Map<String, JsonValue>): JsonObject {
            val set = LinkedHashMap<String, JsonValue>()
            val del = ArrayList<JsonValue>()
            val rec = LinkedHashMap<String, JsonValue>()
            for (k in ShadowKeys.SHADOW) {
                val a = from[k]
                val b = to[k]
                if (b == null) {
                    if (a != null) del += JsonString(k)
                    continue
                }
                if (k in ShadowKeys.REC_KEYS && b is JsonArray && a is JsonArray) {
                    val changed =
                        b.items.indices.filter { i ->
                            i >= a.items.size || !a.items[i].sameStoredValue(b.items[i])
                        }
                    if (changed.size > ShadowLimits.MAX_REC_CHANGES) set[k] = b
                    else if (changed.isNotEmpty() || a.items.size != b.items.size)
                        rec[k] =
                            obj(
                                "len" to num(b.items.size),
                                "at" to
                                    JsonArray(
                                        changed.map { JsonArray(listOf(num(it), b.items[it])) }
                                    ),
                            )
                } else if (a == null || !a.sameStoredValue(b)) set[k] = b
            }
            return obj("set" to JsonObject(set), "del" to JsonArray(del), "rec" to JsonObject(rec))
        }

        /** Only the shadow keys of a save, with foodLog cut to what the economy reads. */
        fun shadow(state: Map<String, JsonValue>): Map<String, JsonValue> =
            state.filterKeys { it in ShadowKeys.SHADOW_SET }

        fun obj(vararg m: Pair<String, JsonValue>) = JsonObject(linkedMapOf(*m))

        fun num(n: Number) = JsonNumber(n.toDouble())

        fun parse(text: String): JsonValue = (JsonReader.read(text) as JsonRead.Parsed).value

        fun fixture(name: String): JsonValue = parse(GoldenFiles.text(name))

        fun members(v: JsonValue?): Map<String, JsonValue> =
            (v as? JsonObject)?.members ?: emptyMap()
    }
}
