package com.lifemax.rpg.core.lifedirector.readmodels

import com.lifemax.rpg.core.lifedirector.KernelGolden
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
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * The read-model golden cases of part b, `ld-readmodels-b-golden.json.gz` (recorded from the page
 * in Chromium; format in `lifemax_modular_v73/tools/gen_ld_readmodels_b_golden.js`), with the
 * states of part a's file, and the replay that computes every section of a case natively and writes
 * it as the recorder does.
 */
object PanelsReplay {
    const val FILE = "ld-readmodels-b-golden.json.gz"

    /**
     * One case: part a's state [state] changed by [set], at [nowMillis], with LifeMax's answers.
     */
    class Case(
        val id: String,
        val family: String,
        val rule: String,
        val state: String,
        val set: List<JsonValue>,
        val nowMillis: Long,
        val deviceZone: String,
        val load: String?,
        val forms: List<Pair<String, String>>,
        val moves: List<String>,
        val shifts: List<Pair<Double, Double>>,
        val handleDays: List<Int>,
        val out: JsonObject,
    )

    val cases: List<Case> by lazy { load() }

    /** Part a's states (the file this one names in its header), as text. */
    val states: Map<String, String> by lazy { partAStates() }

    private fun fixture(name: String): File {
        val start = File(System.getProperty("user.dir") ?: ".").absoluteFile
        return generateSequence(start) { it.parentFile }
            .map { File(it, "lifemax_modular_v73/tests/fixtures/$name") }
            .firstOrNull { it.isFile } ?: error("$name not found above $start")
    }

    private fun parse(line: String): JsonObject {
        val read = JsonReader.read(line.removeSuffix(","))
        check(read is JsonRead.Parsed) { "a line does not parse: ${line.take(80)}" }
        return read.value as JsonObject
    }

    private fun text(v: JsonValue?): String = (v as JsonString).value

    private fun partAStates(): Map<String, String> {
        val out = HashMap<String, String>()
        GZIPInputStream(fixture(ReadModelsReplay.FILE).inputStream())
            .bufferedReader(Charsets.UTF_8)
            .useLines { lines ->
                for (line in lines) if (line.startsWith("{\"state\"")) {
                    val o = parse(line)
                    out[text(o["state"])] = JsonWriter.write(o["st"]!!)
                }
            }
        return out
    }

    private fun load(): List<Case> {
        val out = ArrayList<Case>()
        GZIPInputStream(fixture(FILE).inputStream()).bufferedReader(Charsets.UTF_8).useLines { lines
            ->
            for (line in lines) {
                if (!line.startsWith("{\"id\"")) continue
                val c = parse(line)
                fun list(k: String) = (c[k] as JsonArray).items
                out +=
                    Case(
                        text(c["id"]),
                        text(c["family"]),
                        text(c["rule"]),
                        text(c["state"]),
                        list("set"),
                        (c["nowMs"] as JsonNumber).value.toLong(),
                        text(c["deviceZone"]),
                        (c["load"] as? JsonString)?.value,
                        list("forms").map {
                            val p = (it as JsonArray).items
                            text(p[0]) to text(p[1])
                        },
                        list("moves").map(::text),
                        list("shifts").map {
                            val p = (it as JsonArray).items
                            (p[0] as JsonNumber).value to (p[1] as JsonNumber).value
                        },
                        list("handleDays").map { (it as JsonNumber).value.toInt() },
                        KernelGolden.decode(c["out"]!!) as JsonObject,
                    )
            }
        }
        return out
    }

    /** The case's state: part a's text parsed afresh, with the case's changes made. */
    fun stateOf(c: Case): JsonValue {
        val read = JsonReader.read(states.getValue(c.state))
        check(read is JsonRead.Parsed)
        var st = read.value
        for (e in c.set) {
            val entry = (e as JsonArray).items
            st = setAt(st, (entry[0] as JsonArray).items, entry.getOrNull(1))
        }
        return st
    }

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

    /**
     * LifeMax as the page's host answered in the case (`out.host`); it notes which questions native
     * asked, so they are compared with the page's too.
     */
    class RecordedHost(private val seen: JsonObject) : DirectorHostView {
        val asked = LinkedHashSet<String>()

        override fun capabilities(): HostCapabilities {
            asked += "capabilities"
            val c =
                seen["capabilities"] as? JsonObject ?: return HostCapabilities(false, listOf("?"))
            return HostCapabilities(
                c["connected"] == JsonBool.TRUE,
                (c["missing"] as JsonArray).items.map(::text),
            )
        }

        override fun milestones(): List<Milestone> {
            asked += "milestones"
            val m = seen["milestones"] as? JsonArray ?: return emptyList()
            return m.items.map {
                val o = it as JsonObject
                Milestone(
                    (o["title"] as? JsonString)?.value,
                    (o["desc"] as? JsonString)?.value,
                    o["done"] == JsonBool.TRUE,
                    o["locked"] == JsonBool.TRUE,
                )
            }
        }
    }

    /** Whatever replaces part of the read models in a mutation check (null: the real one). */
    class Mutation(
        val words: PanelWords? = null,
        val move: ((DirectorPanels, String) -> ReadAnswer<MoveSheet?>)? = null,
        val shifts: ((DirectorPanels, Double, Double) -> ReadAnswer<List<Shift>>)? = null,
        val handles: ((DirectorPanels, Int) -> ReadAnswer<DayHandles?>)? = null,
    )

    /** An answer as the recorder writes it. */
    fun <T> json(a: ReadAnswer<T>, write: (T & Any) -> JsonValue): JsonValue =
        when (a) {
            is ReadAnswer.Threw -> JsonObject(mapOf("threw" to JsonString(a.error)))
            is ReadAnswer.Value -> a.value?.let(write) ?: JsonNull
        }

    /** The case's sections computed natively, in the recorder's order and form. */
    fun replay(c: Case, mutation: Mutation = Mutation()): JsonObject {
        val st = stateOf(c)
        val host = RecordedHost(c.out["host"] as JsonObject)
        val models = DirectorReadModels(st, c.nowMillis, c.deviceZone, { _, _, _ -> null })
        val p = DirectorPanels(models, host, c.load, mutation.words ?: PanelWords.packaged)
        val out = LinkedHashMap<String, JsonValue>()
        out["goals"] = json(p.goals()) { it.json() }
        out["quests"] = json(p.quests()) { it.json() }
        out["settings"] = json(p.settings()) { it.json() }
        out["time"] = json(p.time()) { it.json() }
        out["disrupt"] = json(p.disrupt()) { it.json() }
        out["workRules"] = json(p.workRules()) { l -> JsonArray(l.map { it.json() }) }
        out["forms"] = JsonArray(c.forms.map { (k, id) -> json(p.form(k, id)) { it.json() } })
        out["moves"] =
            JsonArray(
                c.moves.map { id -> json(mutation.move?.invoke(p, id) ?: p.move(id)) { it.json() } }
            )
        out["shifts"] =
            JsonArray(
                c.shifts.map { (a, b) ->
                    json(mutation.shifts?.invoke(p, a, b) ?: p.shifts(a, b)) { l ->
                        JsonArray(l.map { it.json() })
                    }
                }
            )
        out["handles"] =
            JsonArray(
                c.handleDays.map { k ->
                    json(mutation.handles?.invoke(p, k) ?: p.handles(k)) { it.json() }
                }
            )
        out["host"] = c.out["host"]!!
        out["asked"] = JsonArray(host.asked.map(::JsonString))
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
            if (got != want) return "${c.id} $k:\n  page   ${show(want)}\n  native ${show(got)}"
        }
        val asked = (mine["asked"] as JsonArray).items.map(::text).toSet()
        val pageAsked = (c.out["host"] as JsonObject).members.keys
        if (asked != pageAsked) return "${c.id}: native asked LifeMax $asked, the page $pageAsked"
        return null
    }

    private fun show(v: JsonValue?): String =
        if (v == null) "absent" else JsonWriter.write(v).take(SHOWN)

    private const val SHOWN = 1200
}
