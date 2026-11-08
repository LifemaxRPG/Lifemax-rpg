package com.lifemax.rpg.core.shadow

import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonRead
import com.lifemax.rpg.core.save.JsonReader
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import com.lifemax.rpg.core.save.JsonWriter
import java.time.DateTimeException
import java.time.ZoneId

/** Why a message from the page was refused. The name is all that is counted or reported. */
enum class ShadowRefusal(val word: String) {
    NOT_JSON("notJson"),
    NOT_AN_OBJECT("notObject"),
    TOO_LARGE("tooLarge"),
    UNSUPPORTED("unsupported"),
    UNKNOWN_KEY("unknownKey"),
    MISSING_KEY("missingKey"),
    WRONG_TYPE("wrongType"),
    OUT_OF_RANGE("outOfRange"),
    BAD_KIND("badKind"),
    BAD_TIME("badTime"),
    BAD_ZONE("badZone"),
    NOT_SHADOW_KEY("notShadowKey"),
    BAD_DELTA("badDelta"),
}

/** A decoded message, or why not; [at] names the place with fixed key names only, never a value. */
sealed interface ShadowDecode<out T> {
    data class Ok<T>(val value: T) : ShadowDecode<T>

    data class Refused(val reason: ShadowRefusal, val at: String) : ShadowDecode<Nothing>
}

/**
 * The strict readers of the page's messages (ECONOMY_SHADOW_PLAN 2.4), on `core/save`'s
 * [JsonReader] (the page's own `JSON.parse`), not `org.json`. Each accepts exactly the v1 key set;
 * a key missing or unknown, a value of the wrong kind or out of range, a key outside the shadow
 * keys, `t1 < t0`, an unknown zone or a malformed delta refuses the whole message. Never throws.
 */
object ShadowEnvelopeJson {
    private val ENVELOPE =
        setOf(
            "v",
            "scope",
            "sid",
            "epoch",
            "seq",
            "base",
            "kind",
            "args",
            "t0",
            "t1",
            "zone",
            "offset",
            "locale",
            "env",
            "before",
            "after",
            "returns",
            "toasts",
            "cost",
            "flags",
        )
    private val BASE = setOf("v", "scope", "sid", "epoch", "seq", "part", "parts", "text")
    private val CONFIGURE = setOf("v", "enabled", "sid", "page")
    private val PAGE = setOf("build", "schema", "fingerprint", "faults")
    private val DELTA = setOf("set", "del", "rec")
    private val REC = setOf("len", "at")
    private val ENV_PARTS = setOf("saves", "plan", "health", "confirm", "prompt", "dom")
    private val SAVE = setOf("ok")
    private val COST = setOf("ms")
    private val SID = Regex("[A-Za-z0-9_-]{1,${ShadowLimits.MAX_SID_CHARS}}")
    private val LOCALE = Regex("[A-Za-z0-9-]{1,35}")
    private const val SCOPE = "economy"
    private const val MAX_OFFSET_MINUTES = 18 * 60

    /** Reads one `observe` envelope. */
    fun envelope(text: String): ShadowDecode<ShadowEnvelope> =
        read(text, ShadowLimits.MAX_ENVELOPE_CHARS) { root ->
            val o = Fields(root, "", ENVELOPE)
            o.version()
            o.scope()
            val seq = o.whole("seq", min = 1)
            val base = o.whole("base", min = 0)
            if (base >= seq) fail(ShadowRefusal.OUT_OF_RANGE, "base")
            val kind = o.text("kind", ShadowLimits.MAX_KIND_CHARS)
            if (!ShadowKeys.KIND_SHAPE.matches(kind)) fail(ShadowRefusal.BAD_KIND, "kind")
            val args = o.get("args") as? JsonArray ?: fail(ShadowRefusal.WRONG_TYPE, "args")
            if (JsonWriter.write(args).length > ShadowLimits.MAX_ARGS_CHARS)
                fail(ShadowRefusal.TOO_LARGE, "args")
            val t0 = o.whole("t0", min = 0)
            val t1 = o.whole("t1", min = 0)
            if (t1 < t0) fail(ShadowRefusal.BAD_TIME, "t1")
            ShadowEnvelope(
                sid = o.sid(),
                epoch = o.whole("epoch", min = 0),
                seq = seq,
                base = base,
                kind = kind,
                args = args,
                t0 = t0,
                t1 = t1,
                zone = zone(o.text("zone", ShadowLimits.MAX_LABEL_CHARS)),
                offsetMinutes =
                    o.whole("offset", min = -MAX_OFFSET_MINUTES.toLong())
                        .also {
                            if (it > MAX_OFFSET_MINUTES) fail(ShadowRefusal.OUT_OF_RANGE, "offset")
                        }
                        .toInt(),
                locale =
                    o.text("locale", ShadowLimits.MAX_LABEL_CHARS).also {
                        if (!LOCALE.matches(it)) fail(ShadowRefusal.OUT_OF_RANGE, "locale")
                    },
                env = env(o.get("env")),
                before = delta(o.get("before"), "before"),
                after = delta(o.get("after"), "after"),
                returns = o.get("returns"),
                toasts = toasts(o.get("toasts")),
                costMs = cost(o.get("cost")),
                flags = flags(o.get("flags")),
            )
        }

    /** Reads one `base` part. */
    fun basePart(text: String): ShadowDecode<ShadowBasePart> =
        read(text, ShadowLimits.MAX_BASE_PART_CHARS) { root ->
            val o = Fields(root, "", BASE)
            o.version()
            o.scope()
            val parts = o.whole("parts", min = 1)
            if (parts > ShadowLimits.MAX_BASE_PARTS) fail(ShadowRefusal.TOO_LARGE, "parts")
            val part = o.whole("part", min = 0)
            if (part >= parts) fail(ShadowRefusal.OUT_OF_RANGE, "part")
            ShadowBasePart(
                sid = o.sid(),
                epoch = o.whole("epoch", min = 0),
                seq = o.whole("seq", min = 1),
                part = part.toInt(),
                parts = parts.toInt(),
                text = o.text("text", ShadowLimits.MAX_BASE_PART_CHARS, allowEmpty = true),
            )
        }

    /** Reads the joined base text `{"keys":{...}}` into its shadow keys. */
    fun baseKeys(text: String): ShadowDecode<Map<String, JsonValue>> =
        read(text, ShadowLimits.MAX_HELD_CHARS) { root ->
            val keys = Fields(root, "", setOf("keys")).get("keys") as? JsonObject
            keys ?: fail(ShadowRefusal.WRONG_TYPE, "keys")
            for (k in keys.members.keys) if (k !in ShadowKeys.SHADOW_SET)
                fail(ShadowRefusal.NOT_SHADOW_KEY, "keys")
            keys.members
        }

    /** Reads one `configure` message. */
    fun configure(text: String): ShadowDecode<ShadowConfig> =
        read(text, CONFIGURE_MAX_CHARS) { root ->
            val o = Fields(root, "", CONFIGURE)
            o.version()
            val enabled = o.get("enabled") as? JsonBool ?: fail(ShadowRefusal.WRONG_TYPE, "enabled")
            val p = Fields(o.get("page"), "page", PAGE)
            ShadowConfig(
                enabled = enabled.value,
                sid = o.sid(),
                page =
                    ShadowPageInfo(
                        build = p.text("build", ShadowLimits.MAX_LABEL_CHARS, allowEmpty = true),
                        schema = p.whole("schema", min = 0).toInt(),
                        fingerprint =
                            p.text("fingerprint", ShadowLimits.MAX_LABEL_CHARS, allowEmpty = true),
                        faults = p.whole("faults", min = 0).toInt(),
                    ),
            )
        }

    private fun <T> read(text: String, maxChars: Int, decode: (JsonValue) -> T): ShadowDecode<T> {
        if (text.length > maxChars) return ShadowDecode.Refused(ShadowRefusal.TOO_LARGE, "")
        val parsed =
            when (val r = JsonReader.read(text)) {
                is JsonRead.Malformed -> return ShadowDecode.Refused(ShadowRefusal.NOT_JSON, "")
                is JsonRead.Parsed -> r.value
            }
        if (parsed !is JsonObject) return ShadowDecode.Refused(ShadowRefusal.NOT_AN_OBJECT, "")
        return try {
            ShadowDecode.Ok(decode(parsed))
        } catch (r: Refusal) {
            ShadowDecode.Refused(r.reason, r.at)
        }
    }

    private fun zone(id: String): String =
        try {
            ZoneId.of(id).id
        } catch (_: DateTimeException) {
            fail(ShadowRefusal.BAD_ZONE, "zone")
        }

    private fun env(value: JsonValue): ShadowEnv {
        val o = value as? JsonObject ?: fail(ShadowRefusal.WRONG_TYPE, "env")
        for (k in o.members.keys) if (k !in ENV_PARTS) fail(ShadowRefusal.UNKNOWN_KEY, "env")
        if (JsonWriter.write(o).length > ShadowLimits.MAX_ENV_CHARS)
            fail(ShadowRefusal.TOO_LARGE, "env")
        val saves = o["saves"]?.let { items(it, "env.saves") } ?: emptyList()
        if (saves.size > ShadowLimits.MAX_SAVES) fail(ShadowRefusal.TOO_LARGE, "env.saves")
        return ShadowEnv(
            saves =
                saves.map {
                    val s = Fields(it, "env.saves[]", SAVE)
                    (s.get("ok") as? JsonBool ?: fail(ShadowRefusal.WRONG_TYPE, "env.saves[].ok"))
                        .value
                },
            rest = JsonObject(o.members - "saves"),
        )
    }

    private fun delta(value: JsonValue, at: String): ShadowDelta {
        val o = Fields(value, at, DELTA)
        val set = o.get("set") as? JsonObject ?: fail(ShadowRefusal.WRONG_TYPE, "$at.set")
        val del =
            items(o.get("del"), "$at.del").map {
                (it as? JsonString ?: fail(ShadowRefusal.WRONG_TYPE, "$at.del")).value
            }
        val recObject = o.get("rec") as? JsonObject ?: fail(ShadowRefusal.WRONG_TYPE, "$at.rec")
        for (k in set.members.keys + del) if (k !in ShadowKeys.SHADOW_SET)
            fail(ShadowRefusal.NOT_SHADOW_KEY, at)
        for (k in recObject.members.keys) if (k !in ShadowKeys.REC_KEYS)
            fail(ShadowRefusal.NOT_SHADOW_KEY, "$at.rec")
        val named = set.members.keys.toList() + del + recObject.members.keys
        if (named.size != named.toSet().size) fail(ShadowRefusal.BAD_DELTA, at)
        val rec = recObject.members.mapValues { (key, v) -> record(v, "$at.rec.$key") }
        return ShadowDelta(set.members, del, rec)
    }

    private fun record(value: JsonValue, at: String): RecordDelta {
        val o = Fields(value, at, REC)
        val length = o.whole("len", min = 0)
        val changes = items(o.get("at"), "$at.at")
        if (changes.size > ShadowLimits.MAX_REC_CHANGES) fail(ShadowRefusal.TOO_LARGE, "$at.at")
        val seen = HashSet<Int>()
        return RecordDelta(
            length = length.toInt(),
            changes =
                changes.map { pair ->
                    val p = (pair as? JsonArray)?.items
                    if (p == null || p.size != 2) fail(ShadowRefusal.WRONG_TYPE, "$at.at[]")
                    val index = wholeOf(p[0], "$at.at[]", min = 0)
                    if (index >= length || !seen.add(index.toInt()))
                        fail(ShadowRefusal.BAD_DELTA, "$at.at[]")
                    RecordChange(index.toInt(), p[1])
                },
        )
    }

    private fun toasts(value: JsonValue): List<String> {
        val list = items(value, "toasts")
        if (list.size > ShadowLimits.MAX_TOASTS) fail(ShadowRefusal.TOO_LARGE, "toasts")
        return list.map {
            val s = (it as? JsonString ?: fail(ShadowRefusal.WRONG_TYPE, "toasts[]")).value
            if (s.length > ShadowLimits.MAX_TOAST_CHARS) fail(ShadowRefusal.TOO_LARGE, "toasts[]")
            s
        }
    }

    private fun cost(value: JsonValue): Double {
        val o = Fields(value, "cost", COST)
        val ms = (o.get("ms") as? JsonNumber ?: fail(ShadowRefusal.WRONG_TYPE, "cost.ms")).value
        if (!ms.isFinite() || ms < 0) fail(ShadowRefusal.OUT_OF_RANGE, "cost.ms")
        return ms
    }

    private fun flags(value: JsonValue): Set<String> {
        val list =
            items(value, "flags").map {
                (it as? JsonString ?: fail(ShadowRefusal.WRONG_TYPE, "flags[]")).value
            }
        if (list.any { it !in ShadowFlags.ALL } || list.size != list.toSet().size)
            fail(ShadowRefusal.OUT_OF_RANGE, "flags")
        return list.toSet()
    }

    private fun items(value: JsonValue, at: String): List<JsonValue> =
        (value as? JsonArray ?: fail(ShadowRefusal.WRONG_TYPE, at)).items

    private fun wholeOf(value: JsonValue, at: String, min: Long): Long {
        val n = (value as? JsonNumber ?: fail(ShadowRefusal.WRONG_TYPE, at)).value
        if (!n.isFinite() || n != Math.floor(n) || n > MAX_SAFE_INTEGER)
            fail(ShadowRefusal.WRONG_TYPE, at)
        if (n < min) fail(ShadowRefusal.OUT_OF_RANGE, at)
        return n.toLong()
    }

    /** One object with exactly [keys]; refuses any other key and a missing one. */
    private class Fields(value: JsonValue, val at: String, keys: Set<String>) {
        val members: Map<String, JsonValue> =
            (value as? JsonObject ?: fail(ShadowRefusal.WRONG_TYPE, at)).members

        init {
            for (k in members.keys) if (k !in keys) fail(ShadowRefusal.UNKNOWN_KEY, at)
            for (k in keys) if (k !in members) fail(ShadowRefusal.MISSING_KEY, join(k))
        }

        fun get(key: String): JsonValue = members.getValue(key)

        fun whole(key: String, min: Long): Long = wholeOf(get(key), join(key), min)

        fun text(key: String, max: Int, allowEmpty: Boolean = false): String {
            val s = (get(key) as? JsonString ?: fail(ShadowRefusal.WRONG_TYPE, join(key))).value
            if (s.length > max || (!allowEmpty && s.isEmpty()))
                fail(ShadowRefusal.OUT_OF_RANGE, join(key))
            return s
        }

        fun version() {
            val v = get("v")
            if (v !is JsonNumber || v.value != ShadowLimits.PROTOCOL.toDouble())
                fail(ShadowRefusal.UNSUPPORTED, "v")
        }

        fun scope() {
            if ((get("scope") as? JsonString)?.value != SCOPE)
                fail(ShadowRefusal.UNSUPPORTED, "scope")
        }

        fun sid(): String =
            text("sid", ShadowLimits.MAX_SID_CHARS).also {
                if (!SID.matches(it)) fail(ShadowRefusal.OUT_OF_RANGE, "sid")
            }

        private fun join(key: String) = if (at.isEmpty()) key else "$at.$key"
    }

    private class Refusal(val reason: ShadowRefusal, val at: String) : Exception(reason.word) {
        override fun fillInStackTrace(): Throwable = this
    }

    private fun fail(reason: ShadowRefusal, at: String): Nothing = throw Refusal(reason, at)

    private const val CONFIGURE_MAX_CHARS = 2 * 1024
    private const val MAX_SAFE_INTEGER = 9007199254740991.0
}
