package com.lifemax.rpg.core.directorshadow

import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonRead
import com.lifemax.rpg.core.save.JsonReader
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import com.lifemax.rpg.core.shadow.ShadowDecode
import com.lifemax.rpg.core.shadow.ShadowRefusal
import java.time.DateTimeException
import java.time.ZoneId

/** The owner's switch and the page's session (SH-2). */
data class DirectorConfigure(
    val enabled: Boolean,
    val sid: String,
    val build: String,
    val fingerprint: String,
    val faults: Int,
)

/** One part of a base: the whole state's changes from nothing, in slices (SH-8). */
data class DirectorBasePart(
    val sid: String,
    val seq: Long,
    val part: Int,
    val parts: Int,
    val text: String,
)

/** What the page's read models answered, with what they read (SH-12). */
data class ReadFacts(
    val nowMillis: Long,
    val deviceZone: String,
    val planZone: String,
    val planOffset: Int?,
    val xp: List<JsonValue>,
    val out: JsonObject,
)

/** One plan the page's engine made: its input, its output, and the plan zone's offset (SH-15). */
data class EngineFacts(
    val planZone: String,
    val planOffset: Int?,
    val input: JsonValue,
    val output: JsonValue,
)

/** One `observe` message (SH-3). */
data class DirectorEnvelope(
    val sid: String,
    val seq: Long,
    val base: Long,
    val kind: String,
    val t0: Long,
    val t1: Long,
    val zone: String,
    val ops: List<TextOp>,
    val costMs: Double,
    val flags: List<String>,
    val bytes: Int?,
    val read: ReadFacts?,
    val engine: EngineFacts?,
)

/**
 * The strict readers of the Life Director check's messages (SH-2, SH-3), on `core/save`'s
 * [JsonReader], as the economy check's are: exactly the v1 keys, `scope: "director"`, every value
 * of its kind and in range, or the whole message is refused with a reason that names a key, never a
 * value. Never throws.
 */
object DirectorShadowJson {
    const val SAVE = "director.save"
    const val READ = "director.read"
    const val PLAN = "director.plan"
    const val REPAIR = "director.repair"
    val KINDS = setOf(SAVE, READ, PLAN, REPAIR)

    private val COMMON =
        setOf(
            "v",
            "scope",
            "sid",
            "seq",
            "base",
            "kind",
            "t0",
            "t1",
            "zone",
            "ops",
            "cost",
            "flags",
        )
    private val CONFIGURE = setOf("v", "scope", "enabled", "sid", "page")
    private val PAGE = setOf("build", "fingerprint", "faults")
    private val BASE = setOf("v", "scope", "sid", "seq", "part", "parts", "text")
    private val READ_KEYS = setOf("nowMs", "deviceZone", "planZone", "planOffset", "xp", "out")
    private val ENGINE_KEYS = setOf("planZone", "planOffset", "input", "output")
    private val SID = Regex("[A-Za-z0-9_-]{1,40}")
    private val KIND = Regex("director\\.[a-z]{1,20}")
    private val FLAG = Regex("[a-z][A-Za-z]{0,23}")
    private const val MAX_OFFSET_SECONDS = 18 * 3600
    private const val MAX_TEXT = 200

    private class Refusal(val reason: ShadowRefusal, val at: String) : RuntimeException()

    private fun fail(reason: ShadowRefusal, at: String): Nothing = throw Refusal(reason, at)

    private fun <T> read(text: String, limit: Int, f: (JsonObject) -> T): ShadowDecode<T> {
        if (text.length > limit) return ShadowDecode.Refused(ShadowRefusal.TOO_LARGE, "")
        val root =
            when (val r = JsonReader.read(text)) {
                is JsonRead.Parsed -> r.value
                is JsonRead.Malformed -> return ShadowDecode.Refused(ShadowRefusal.NOT_JSON, "")
            }
        if (root !is JsonObject) return ShadowDecode.Refused(ShadowRefusal.NOT_AN_OBJECT, "")
        return try {
            ShadowDecode.Ok(f(root))
        } catch (e: Refusal) {
            ShadowDecode.Refused(e.reason, e.at)
        }
    }

    private class Fields(
        val o: JsonObject,
        val at: String,
        keys: Set<String>,
        optional: Set<String> = emptySet(),
    ) {
        init {
            for (k in o.members.keys) if (k !in keys && k !in optional)
                fail(ShadowRefusal.UNKNOWN_KEY, at + k)
            for (k in keys) if (k !in o.members) fail(ShadowRefusal.MISSING_KEY, at + k)
        }

        fun get(k: String): JsonValue? = o.members[k]

        fun text(k: String, max: Int = MAX_TEXT): String {
            val v = (get(k) as? JsonString)?.value ?: fail(ShadowRefusal.WRONG_TYPE, at + k)
            if (v.length > max) fail(ShadowRefusal.TOO_LARGE, at + k)
            return v
        }

        fun whole(k: String, min: Long = 0, max: Long = Long.MAX_VALUE / 2): Long {
            val n = (get(k) as? JsonNumber)?.value ?: fail(ShadowRefusal.WRONG_TYPE, at + k)
            if (n != Math.floor(n) || n < min || n > max) fail(ShadowRefusal.OUT_OF_RANGE, at + k)
            return n.toLong()
        }

        fun version() {
            if ((get("v") as? JsonNumber)?.value != DirectorShadowLimits.PROTOCOL.toDouble())
                fail(ShadowRefusal.UNSUPPORTED, at + "v")
            if (text("scope") != DirectorShadowLimits.SCOPE)
                fail(ShadowRefusal.OUT_OF_RANGE, at + "scope")
        }

        fun sid(): String =
            text("sid").also { if (!SID.matches(it)) fail(ShadowRefusal.OUT_OF_RANGE, at + "sid") }
    }

    fun configure(text: String): ShadowDecode<DirectorConfigure> =
        read(text, 4096) { root ->
            val o = Fields(root, "", CONFIGURE)
            o.version()
            val enabled =
                (o.get("enabled") as? JsonBool)?.value ?: fail(ShadowRefusal.WRONG_TYPE, "enabled")
            val page =
                Fields(
                    o.get("page") as? JsonObject ?: fail(ShadowRefusal.WRONG_TYPE, "page"),
                    "page.",
                    PAGE,
                )
            DirectorConfigure(
                enabled,
                o.sid(),
                page.text("build"),
                page.text("fingerprint"),
                page.whole("faults", 0, 1000).toInt(),
            )
        }

    fun basePart(text: String): ShadowDecode<DirectorBasePart> =
        read(text, DirectorShadowLimits.MAX_BASE_PART_CHARS + 1024) { root ->
            val o = Fields(root, "", BASE)
            o.version()
            val parts = o.whole("parts", 1, DirectorShadowLimits.MAX_BASE_PARTS.toLong()).toInt()
            val part = o.whole("part", 0, parts - 1L).toInt()
            DirectorBasePart(
                o.sid(),
                o.whole("seq", 1),
                part,
                parts,
                o.text("text", DirectorShadowLimits.MAX_BASE_PART_CHARS),
            )
        }

    /** The changes of an assembled base (`{"ops": [...]}`). */
    fun baseOps(text: String): ShadowDecode<List<TextOp>> =
        read(text, Int.MAX_VALUE) { root -> ops(Fields(root, "", setOf("ops")).get("ops")) }

    fun envelope(text: String): ShadowDecode<DirectorEnvelope> =
        read(text, DirectorShadowLimits.MAX_ENVELOPE_CHARS) { root ->
            val o = Fields(root, "", COMMON, setOf("bytes", "read", "engine"))
            o.version()
            val seq = o.whole("seq", 1)
            val base = o.whole("base", 0)
            if (base >= seq) fail(ShadowRefusal.OUT_OF_RANGE, "base")
            val kind = o.text("kind", 32)
            if (!KIND.matches(kind)) fail(ShadowRefusal.BAD_KIND, "kind")
            val t0 = o.whole("t0")
            val t1 = o.whole("t1")
            if (t1 < t0) fail(ShadowRefusal.BAD_TIME, "t1")
            val cost =
                Fields(
                    o.get("cost") as? JsonObject ?: fail(ShadowRefusal.WRONG_TYPE, "cost"),
                    "cost.",
                    setOf("ms"),
                )
            val ms =
                (cost.get("ms") as? JsonNumber)?.value?.takeIf { it.isFinite() && it >= 0 }
                    ?: fail(ShadowRefusal.OUT_OF_RANGE, "cost.ms")
            val flags =
                (o.get("flags") as? JsonArray ?: fail(ShadowRefusal.WRONG_TYPE, "flags"))
                    .items
                    .map {
                        ((it as? JsonString)?.value ?: fail(ShadowRefusal.WRONG_TYPE, "flags"))
                            .also { f ->
                                if (!FLAG.matches(f)) fail(ShadowRefusal.OUT_OF_RANGE, "flags")
                            }
                    }
            val bytes =
                o.get("bytes")?.let {
                    o.whole("bytes", 2, DirectorShadowLimits.MAX_STATE_CHARS.toLong()).toInt()
                }
            val read = o.get("read")?.let { readFacts(it) }
            val engine = o.get("engine")?.let { engineFacts(it) }
            when (kind) {
                SAVE ->
                    if (bytes == null || read != null || engine != null)
                        fail(ShadowRefusal.MISSING_KEY, "bytes")
                READ ->
                    if (read == null || bytes != null || engine != null)
                        fail(ShadowRefusal.MISSING_KEY, "read")
                PLAN,
                REPAIR ->
                    if (engine == null || bytes != null || read != null)
                        fail(ShadowRefusal.MISSING_KEY, "engine")
            }
            val ops = ops(o.get("ops"))
            if ((kind == PLAN || kind == REPAIR) && ops.isNotEmpty())
                fail(ShadowRefusal.BAD_DELTA, "ops")
            DirectorEnvelope(
                o.sid(),
                seq,
                base,
                kind,
                t0,
                t1,
                zone(o.text("zone"), "zone"),
                ops,
                ms,
                flags,
                bytes,
                read,
                engine,
            )
        }

    private fun readFacts(v: JsonValue): ReadFacts {
        val o =
            Fields(v as? JsonObject ?: fail(ShadowRefusal.WRONG_TYPE, "read"), "read.", READ_KEYS)
        val xp = (o.get("xp") as? JsonArray ?: fail(ShadowRefusal.WRONG_TYPE, "read.xp")).items
        return ReadFacts(
            o.whole("nowMs"),
            zone(o.text("deviceZone"), "read.deviceZone"),
            o.text("planZone"),
            offset(o.get("planOffset"), "read.planOffset"),
            xp,
            o.get("out") as? JsonObject ?: fail(ShadowRefusal.WRONG_TYPE, "read.out"),
        )
    }

    private fun engineFacts(v: JsonValue): EngineFacts {
        val o =
            Fields(
                v as? JsonObject ?: fail(ShadowRefusal.WRONG_TYPE, "engine"),
                "engine.",
                ENGINE_KEYS,
            )
        return EngineFacts(
            o.text("planZone"),
            offset(o.get("planOffset"), "engine.planOffset"),
            o.get("input")!!,
            o.get("output")!!,
        )
    }

    private fun offset(v: JsonValue?, at: String): Int? =
        when (v) {
            JsonNull -> null
            is JsonNumber ->
                v.value
                    .takeIf { it == Math.floor(it) && kotlin.math.abs(it) <= MAX_OFFSET_SECONDS }
                    ?.toInt() ?: fail(ShadowRefusal.OUT_OF_RANGE, at)
            else -> fail(ShadowRefusal.WRONG_TYPE, at)
        }

    private fun zone(id: String, at: String): String =
        try {
            ZoneId.of(id).id
        } catch (_: DateTimeException) {
            fail(ShadowRefusal.BAD_ZONE, at)
        }

    private fun ops(v: JsonValue?): List<TextOp> {
        val list = (v as? JsonArray ?: fail(ShadowRefusal.WRONG_TYPE, "ops")).items
        if (list.size > DirectorShadowLimits.MAX_OPS) fail(ShadowRefusal.TOO_LARGE, "ops")
        return list.map { item ->
            val parts = (item as? JsonArray)?.items ?: fail(ShadowRefusal.BAD_DELTA, "ops")
            if (parts.size != 3) fail(ShadowRefusal.BAD_DELTA, "ops")
            val kind = (parts[0] as? JsonString)?.value ?: fail(ShadowRefusal.BAD_DELTA, "ops")
            val path = (parts[1] as? JsonArray)?.items ?: fail(ShadowRefusal.BAD_DELTA, "ops")
            if (path.size > DirectorShadowLimits.MAX_PATH) fail(ShadowRefusal.BAD_DELTA, "ops")
            val steps =
                path.map { s ->
                    when (s) {
                        is JsonString -> s.value
                        is JsonNumber ->
                            s.value
                                .takeIf { it == Math.floor(it) && it >= 0 && it < Int.MAX_VALUE }
                                ?.toInt() ?: fail(ShadowRefusal.BAD_DELTA, "ops")
                        else -> fail(ShadowRefusal.BAD_DELTA, "ops")
                    }
                }
            val arg = parts[2]
            when (kind) {
                "o" ->
                    TextOp(
                        'o',
                        steps,
                        keys =
                            (arg as? JsonArray ?: fail(ShadowRefusal.BAD_DELTA, "ops")).items.map {
                                (it as? JsonString)?.value ?: fail(ShadowRefusal.BAD_DELTA, "ops")
                            },
                    )
                "a" ->
                    TextOp(
                        'a',
                        steps,
                        length =
                            ((arg as? JsonNumber)?.value?.takeIf {
                                    it == Math.floor(it) &&
                                        it >= 0 &&
                                        it <= DirectorShadowLimits.MAX_OPS * 100.0
                                } ?: fail(ShadowRefusal.BAD_DELTA, "ops"))
                                .toInt(),
                    )
                "t" ->
                    TextOp(
                        't',
                        steps,
                        text = (arg as? JsonString)?.value ?: fail(ShadowRefusal.BAD_DELTA, "ops"),
                    )
                else -> fail(ShadowRefusal.BAD_DELTA, "ops")
            }
        }
    }
}
