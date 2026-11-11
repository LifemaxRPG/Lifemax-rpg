package com.lifemax.rpg.core.directorstore

import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonRead
import com.lifemax.rpg.core.save.JsonReader
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import com.lifemax.rpg.core.save.JsonWriter
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * The store golden cases, `ld-store-golden.json.gz` (recorded from the page in Chromium; format in
 * `tools/gen_ld_store_golden.js`): the seed texts the page wrote, then one case per line, read one
 * line at a time. [Refs] resolves the cases' text and state arguments and writes texts back the way
 * the recorder does (inline, a seed's name, or length and FNV-1a).
 */
object StoreGolden {
    const val FILE = "ld-store-golden.json.gz"
    private const val INLINE_UNITS = 2000
    private const val FNV_OFFSET = 0x811c9dc5L
    private const val FNV_PRIME = 0x01000193L
    private const val UINT32 = 0xffffffffL

    /** A recorded case; [args] and [out] as the file has them. */
    class Case(
        val id: String,
        val family: String,
        val rule: String,
        val fn: String,
        val args: JsonObject,
        val out: JsonValue,
    )

    fun file(): File {
        val start = File(System.getProperty("user.dir") ?: ".").absoluteFile
        return generateSequence(start) { it.parentFile }
            .map { File(it, "lifemax_modular_v73/tests/fixtures/$FILE") }
            .firstOrNull { it.isFile } ?: error("$FILE not found above $start")
    }

    /** The seeds, then [visit] with every case in the file's order. */
    fun read(visit: (Refs, Case) -> Unit) {
        GZIPInputStream(file().inputStream()).bufferedReader(Charsets.UTF_8).useLines { lines ->
            val seeds = LinkedHashMap<String, String>()
            var refs: Refs? = null
            var inSeeds = false
            for (line in lines) {
                when {
                    line.endsWith("\"seeds\":{") -> inSeeds = true
                    line == "},\"cases\":[" -> {
                        inSeeds = false
                        refs = Refs(seeds)
                    }
                    inSeeds -> {
                        val member = parse("{" + line.removeSuffix(",") + "}") as JsonObject
                        for ((k, v) in member.members) seeds[k] = (v as JsonString).value
                    }
                    line.startsWith("{\"id\"") -> {
                        val c = parse(line.removeSuffix(",")) as JsonObject
                        visit(
                            refs!!,
                            Case(
                                str(c["id"]),
                                str(c["family"]),
                                str(c["rule"]),
                                str(c["fn"]),
                                c["args"] as JsonObject,
                                c["out"]!!,
                            ),
                        )
                    }
                }
            }
        }
    }

    fun parse(text: String): JsonValue =
        when (val read = JsonReader.read(text)) {
            is JsonRead.Parsed -> read.value
            is JsonRead.Malformed -> error("not JSON at ${read.offset}: ${read.reason}")
        }

    fun str(value: JsonValue?): String = (value as JsonString).value

    fun obj(vararg members: Pair<String, JsonValue>): JsonObject = JsonObject(linkedMapOf(*members))

    fun show(value: JsonValue?): String =
        if (value == null) "undefined" else JsonWriter.write(value).take(1500)

    /** FNV-1a 32 over UTF-16 units, as 8 hex digits. */
    fun fnv(s: String): String {
        var h = FNV_OFFSET
        for (c in s) h = ((h xor c.code.toLong()) * FNV_PRIME) and UINT32
        return h.toString(16).padStart(8, '0')
    }

    /** A text argument could not be read by native (DV-1); the case is a known deviation. */
    class BeyondNative(val where: String) : RuntimeException(where, null, false, false)

    /** Arguments in, recorded texts out, against the file's seeds. */
    class Refs(val seeds: Map<String, String>) {
        private val names =
            HashMap<String, String>().also { m -> for ((k, v) in seeds) m.putIfAbsent(v, k) }

        fun ref(text: String?): JsonValue =
            when {
                text == null -> JsonNull
                text in names -> obj("seed" to JsonString(names.getValue(text)))
                text.length <= INLINE_UNITS -> JsonString(text)
                else ->
                    obj("len" to JsonNumber(text.length.toDouble()), "fnv" to JsonString(fnv(text)))
            }

        /**
         * A recorded state: the text of JSON.stringify with `{$num}` for the numbers JSON cannot
         * hold.
         */
        fun state(value: JsonValue?): JsonValue =
            if (value == null) JsonNull else ref(PageJson.canonical(value))

        fun text(arg: JsonValue): String {
            if (arg is JsonString) return arg.value
            val o = arg as JsonObject
            o["seed"]?.let {
                return seeds.getValue(str(it))
            }
            (o["repeat"] as? JsonObject)?.let { r ->
                return str(r["unit"]).repeat((r["count"] as JsonNumber).value.toInt())
            }
            val g = o["grow"] as JsonObject
            val base = parseState(text(g["from"]!!)) as JsonObject
            val members = base.members as LinkedHashMap<String, JsonValue>
            val field = str(g["field"])
            members[field] = JsonString("")
            val pad = (g["length"] as JsonNumber).value.toInt() - PageJson.text(base).length
            members[field] = JsonString("x".repeat(pad))
            return PageJson.text(base)
        }

        /** `JSON.parse(text)` as native reads it; a text too deep for native is [BeyondNative]. */
        fun parseState(text: String): JsonValue =
            when (val read = JsonReader.read(text)) {
                is JsonRead.Parsed -> read.value
                is JsonRead.Malformed ->
                    if (DirectorStore.isTooDeep(read)) throw BeyondNative("argument")
                    else error("a state argument is not JSON: ${read.reason}")
            }

        /** A state argument: `{text}` parsed, or `{tree}` with `{$num}` read back. */
        fun stateArg(arg: JsonValue): JsonValue {
            val o = arg as JsonObject
            return if ("tree" in o) uncanon(o["tree"]!!) else parseState(text(o["text"]!!))
        }

        fun uncanon(v: JsonValue): JsonValue =
            when (v) {
                is JsonArray -> JsonArray(v.items.map(::uncanon))
                is JsonObject ->
                    if (v.members.keys == setOf(NUM)) special(str(v[NUM]))
                    else JsonObject(LinkedHashMap(v.members.mapValues { uncanon(it.value) }))
                else -> v
            }

        private fun special(name: String): JsonNumber =
            JsonNumber(
                when (name) {
                    "NaN" -> Double.NaN
                    "Infinity" -> Double.POSITIVE_INFINITY
                    "-Infinity" -> Double.NEGATIVE_INFINITY
                    else -> -0.0
                }
            )
    }

    /** `{$num}` for a number JSON cannot write, else the number. */
    fun canonNumber(n: Double): JsonValue =
        PageJson.special(n)?.let { obj(NUM to JsonString(it)) } ?: JsonNumber(n)

    private const val NUM = "\$num"
}
