package com.lifemax.rpg.core.lifedirector.readmodels

import com.lifemax.rpg.core.economy.XpShare
import com.lifemax.rpg.core.lifedirector.EngineRefusal
import com.lifemax.rpg.core.lifedirector.KernelGolden
import com.lifemax.rpg.core.lifedirector.PageRangeError
import com.lifemax.rpg.core.lifedirector.PageTypeError
import com.lifemax.rpg.core.lifedirector.PlanDay
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
 * The read-model golden cases, `ld-readmodels-golden.json.gz` (recorded from the page in Chromium;
 * format in `lifemax_modular_v73/tools/gen_ld_readmodels_golden.js`), and the replay that computes
 * every section of a case natively, in the recorder's order, and writes it as the recorder does.
 */
object ReadModelsReplay {
    const val FILE = "ld-readmodels-golden.json.gz"

    /** One case: [state] changed by [set], at [nowMillis], with the device zone and host. */
    class Case(
        val id: String,
        val family: String,
        val rule: String,
        val state: String,
        val set: List<JsonValue>,
        val nowMillis: Long,
        val deviceZone: String,
        val host: String,
        val calendar: List<Int>?,
        val out: JsonObject,
    )

    /** The file: the states (as text, parsed per case) and the cases. */
    class Golden(val states: Map<String, String>, val cases: List<Case>)

    val golden: Golden by lazy { load() }

    private fun file(): File {
        val start = File(System.getProperty("user.dir") ?: ".").absoluteFile
        return generateSequence(start) { it.parentFile }
            .map { File(it, "lifemax_modular_v73/tests/fixtures/$FILE") }
            .firstOrNull { it.isFile } ?: error("$FILE not found above $start")
    }

    private fun parse(line: String): JsonObject {
        val read = JsonReader.read(line.removeSuffix(","))
        check(read is JsonRead.Parsed) { "a line of $FILE does not parse: ${line.take(80)}" }
        return read.value as JsonObject
    }

    private fun text(v: JsonValue?): String = (v as JsonString).value

    private fun load(): Golden {
        val states = LinkedHashMap<String, String>()
        val cases = ArrayList<Case>()
        GZIPInputStream(file().inputStream()).bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (line in lines) {
                if (line.startsWith("{\"state\"")) {
                    val o = parse(line)
                    states[text(o["state"])] = JsonWriter.write(o["st"]!!)
                } else if (line.startsWith("{\"id\"")) {
                    val c = parse(line)
                    cases +=
                        Case(
                            text(c["id"]),
                            text(c["family"]),
                            text(c["rule"]),
                            text(c["state"]),
                            (c["set"] as JsonArray).items,
                            (c["nowMs"] as JsonNumber).value.toLong(),
                            text(c["deviceZone"]),
                            text(c["host"]),
                            (c["calendar"] as? JsonArray)?.items?.map {
                                (it as JsonNumber).value.toInt()
                            },
                            KernelGolden.decode(c["out"]!!) as JsonObject,
                        )
                }
            }
        }
        return Golden(states, cases)
    }

    /** The case's state: its text parsed afresh, with the case's changes made. */
    fun stateOf(c: Case): JsonValue {
        val read = JsonReader.read(golden.states.getValue(c.state))
        check(read is JsonRead.Parsed)
        var st = read.value
        for (e in c.set) {
            val entry = (e as JsonArray).items
            val path = (entry[0] as JsonArray).items
            st = setAt(st, path, entry.getOrNull(1))
        }
        return st
    }

    /** [root] with the member at [path] set to [value] (removed when null), copied along it. */
    private fun setAt(root: JsonValue, path: List<JsonValue>, value: JsonValue?): JsonValue {
        val key = path.first()
        val rest = path.drop(1)
        return when (root) {
            is JsonObject -> {
                val k = text(key)
                val m = LinkedHashMap(root.members)
                if (rest.isEmpty()) {
                    if (value == null) m.remove(k) else m[k] = value
                } else m[k] = setAt(root.members.getValue(k), rest, value)
                JsonObject(m)
            }
            is JsonArray -> {
                val i = (key as JsonNumber).value.toInt()
                val items = ArrayList(root.items)
                items[i] = if (rest.isEmpty()) value ?: JsonNull else setAt(items[i], rest, value)
                JsonArray(items)
            }
            else -> error("no member on the way to $path")
        }
    }

    /** The recorder's error names for the page's throws. */
    fun errorName(e: Throwable): String =
        when (e) {
            is PageTypeError -> "TypeError"
            is PageRangeError -> "RangeError"
            is IllegalArgumentException -> "RangeError"
            is EngineRefusal -> "Error"
            else -> throw e
        }

    private fun threw(e: Throwable): JsonValue =
        JsonObject(mapOf("threw" to JsonString(errorName(e))))

    private inline fun section(f: () -> JsonValue): JsonValue =
        try {
            f()
        } catch (e: RuntimeException) {
            threw(e)
        }

    /**
     * An XP host that answers with the recorded answers, in order, and keeps the calls asked of it
     * as the recorder writes them, so the call list is compared too.
     */
    class RecordedXp(private val recorded: List<JsonValue>, private val connected: Boolean) :
        SessionXpPreview {
        val calls = ArrayList<JsonValue>()

        override fun preview(type: String, minutes: Double, dateKey: String): XpPreview? {
            if (!connected) return null
            val args =
                JsonArray(
                    listOf(JsonString(type), JsonNumber(minutes), JsonNumber(1.0), JsonString(dateKey))
                )
            val r = recorded.getOrNull(calls.size) as? JsonObject
            val out = if (r != null && r["args"] == args) r["out"] ?: JsonNull else JsonNull
            calls += JsonObject(linkedMapOf("args" to args, "out" to out))
            val o = out as? JsonObject ?: return null
            return XpPreview(
                (o["xp"] as JsonNumber).value,
                text(o["primary"]),
                (o["split"] as JsonArray).items.map {
                    val s = it as JsonObject
                    XpShare(text(s["attr"]), (s["xp"] as JsonNumber).value)
                },
            )
        }
    }

    /** Whatever replaces a read model in a mutation check (null: the real one). */
    class Mutation(
        val blocks: ((DirectorReadModels, PlanDay) -> List<DayBlock>)? = null,
        val now: ((DirectorReadModels) -> NowState)? = null,
        val week: ((DirectorReadModels) -> WeekProjection?)? = null,
        val words: ReadModelWords? = null,
    )

    /** The case's sections computed natively, in the recorder's order and form. */
    fun replay(c: Case, mutation: Mutation = Mutation()): JsonObject {
        val st = stateOf(c)
        val xp = RecordedXp((c.out["xp"] as JsonArray).items, c.host != "off")
        val m =
            DirectorReadModels(st, c.nowMillis, c.deviceZone, xp, mutation.words ?: ReadModelWords.packaged)
        val dayBlocks = mutation.blocks ?: { r, d -> r.dayBlocks(d) }
        val out = LinkedHashMap<String, JsonValue>()
        var days: List<PlanDay>? = null
        out["days"] = section { ReadModelJson.days(m.horizonDays().also { days = it }) }
        out["unplanned"] = section {
            ReadModelJson.segments(m.unplannedSegments(days?.first() ?: throw PageTypeError("days")))
        }
        out["blocks"] = section {
            JsonArray((days ?: throw PageTypeError("days")).map { ReadModelJson.blocks(dayBlocks(m, it)) })
        }
        out["timeline"] = section { ReadModelJson.blocks(m.liveTimeline()) }
        out["now"] = section { ReadModelJson.now(mutation.now?.invoke(m) ?: m.nowState()) }
        out["week"] = section { ReadModelJson.weekModel(m.weekModel()) }
        out["native"] = section { ReadModelJson.week(mutation.week?.invoke(m) ?: m.weekProjection()) }
        c.calendar?.let { offsets ->
            out["calendar"] =
                JsonArray(
                    offsets.map { offset ->
                        JsonObject(
                            linkedMapOf(
                                "offset" to JsonNumber(offset.toDouble()),
                                "days" to section { m.calendarDays(offset)?.let(ReadModelJson::days) ?: JsonNull },
                                "blocks" to
                                    section {
                                        m.calendarDays(offset)?.let { cd ->
                                            JsonArray(cd.map { ReadModelJson.blocks(dayBlocks(m, it)) })
                                        } ?: JsonNull
                                    },
                            )
                        )
                    }
                )
        }
        out["xp"] = JsonArray(xp.calls)
        return JsonObject(out)
    }

    /** The first section of [c] that differs from the page's, or null when all agree. */
    fun difference(c: Case, mutation: Mutation = Mutation()): String? {
        val mine =
            try {
                replay(c, mutation)
            } catch (e: RuntimeException) {
                return "${c.id}: threw $e"
            }
        for ((k, want) in c.out.members) {
            val got = mine[k]
            if (got != want)
                return "${c.id} $k:\n  page   ${show(want)}\n  native ${show(got)}"
        }
        if (mine.members.keys != c.out.members.keys) return "${c.id}: sections ${mine.members.keys}"
        return null
    }

    private fun show(v: JsonValue?): String = if (v == null) "absent" else JsonWriter.write(v).take(SHOWN)

    private const val SHOWN = 900
}
