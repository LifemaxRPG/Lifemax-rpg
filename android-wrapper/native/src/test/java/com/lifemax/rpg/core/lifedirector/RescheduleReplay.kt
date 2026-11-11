package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.lifedirector.KernelGolden.threw
import com.lifemax.rpg.core.lifedirector.PageValues.member
import com.lifemax.rpg.core.lifedirector.ScheduleJson.obj
import com.lifemax.rpg.core.lifedirector.ScheduleJson.str
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonRead
import com.lifemax.rpg.core.save.JsonReader
import com.lifemax.rpg.core.save.JsonValue
import com.lifemax.rpg.core.save.JsonWriter
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * The rescheduling golden cases, `ld-reschedule-golden.json.gz` (recorded from the page in
 * Chromium; format in `tools/gen_ld_reschedule_golden.js`), read one line at a time (no case is
 * kept once replayed), and the runner that plays a case's steps natively and writes each step's
 * answer as the recorder does.
 */
object RescheduleReplay {
    const val FILE = "ld-reschedule-golden.json.gz"

    private fun file(): File {
        val start = File(System.getProperty("user.dir") ?: ".").absoluteFile
        return generateSequence(start) { it.parentFile }
            .map { File(it, "lifemax_modular_v73/tests/fixtures/$FILE") }
            .firstOrNull { it.isFile } ?: error("$FILE not found above $start")
    }

    /** Calls [visit] with every case, in the file's order. */
    fun forEachCase(visit: (KernelGolden.Case) -> Unit) {
        GZIPInputStream(file().inputStream()).bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (line in lines) {
                if (!line.startsWith("{\"id\"")) continue
                val read = JsonReader.read(line.removeSuffix(","))
                check(read is JsonRead.Parsed) {
                    "a case of $FILE does not parse: ${line.take(80)}"
                }
                val c = read.value as JsonObject
                visit(
                    KernelGolden.Case(
                        KernelGolden.text(c["id"]),
                        KernelGolden.text(c["family"]),
                        KernelGolden.text(c["rule"]),
                        KernelGolden.text(c["fn"]),
                        (c["args"] as JsonArray).items,
                        KernelGolden.decode(c["out"]!!),
                    )
                )
            }
        }
    }

    private fun without(v: JsonValue?, key: String): JsonValue =
        (v as? JsonObject)?.let {
            JsonObject(LinkedHashMap(it.members).also { m -> m.remove(key) })
        } ?: JsonNull

    private fun assign(base: JsonValue, extra: JsonObject): JsonValue =
        JsonObject(LinkedHashMap((base as JsonObject).members).also { it.putAll(extra.members) })

    private fun marker(kind: String, name: String) = obj { this[kind] = str(name) }

    /**
     * The recorder's runner: [input] planned, then each step's `set` and `store` applied and the
     * reschedule run on what the step before left.
     */
    fun reschedule(input: JsonValue, steps: List<JsonValue>): JsonValue {
        var engineInput: JsonValue = input
        var current: JsonValue = PlanSchedule.generate(input)
        val out = ArrayList<JsonValue>()
        for (st in steps) {
            (member(st, "set") as? JsonObject)?.let {
                engineInput = assign(engineInput, ScheduleJson.clone(it) as JsonObject)
            }
            if (PageValues.truthy(member(st, "store")))
                engineInput =
                    assign(
                        engineInput,
                        obj {
                            this["existingSchedule"] =
                                ScheduleJson.clone(member(current, "scheduledInstances")!!)
                        },
                    )
            val request = obj {
                this["engineInput"] = engineInput
                this["currentOutput"] = current
                this["trigger"] = member(st, "trigger")?.let(ScheduleJson::clone)
            }
            val r =
                try {
                    Reschedule.run(request)
                } catch (e: PageTypeError) {
                    out += threw("TypeError")
                    continue
                } catch (e: PageRangeError) {
                    out += threw("RangeError")
                    continue
                } catch (e: EngineRefusal) {
                    out += threw("Error")
                    continue
                }
            out += obj {
                this["rejectedMove"] = r.rejectedMove ?: JsonNull
                this["changeRecord"] =
                    r.changeRecord?.let { without(it, "computeTimeMs") } ?: JsonNull
                this["engineInput"] =
                    when {
                        r.engineInput === engineInput -> marker("\$same", "engineInput")
                        JsonWriter.write(r.engineInput!!) == JsonWriter.write(engineInput) ->
                            marker("\$equal", "engineInput")
                        else -> r.engineInput
                    }
                this["updatedSchedule"] =
                    if (r.updatedSchedule === current) marker("\$same", "currentOutput")
                    else without(r.updatedSchedule, "computeTimeMs")
            }
            engineInput = r.engineInput!!
            current = r.updatedSchedule
        }
        return obj { this["steps"] = JsonArray(out) }
    }

    /** What the native code gives for [case]. */
    fun run(case: KernelGolden.Case): JsonValue {
        check(case.fn == "reschedule") { "no adapter for ${case.fn}" }
        return try {
            reschedule(case.args[0], (case.args[1] as JsonArray).items)
        } catch (e: PageTypeError) {
            threw("TypeError")
        } catch (e: PageRangeError) {
            threw("RangeError")
        } catch (e: EngineRefusal) {
            threw("Error")
        }
    }

    /**
     * Where [a] (the page's) and [b] (native) first differ, member order included (the order is
     * part of the stored plan: it reaches `inputHash` and the saved text); null when the same.
     */
    fun firstDifference(a: JsonValue, b: JsonValue, at: String): String? =
        when {
            a is JsonObject && b is JsonObject -> {
                val ka = a.members.keys.toList()
                val kb = b.members.keys.toList()
                (ka + kb).distinct().firstNotNullOfOrNull { k ->
                    val x = a[k]
                    val y = b[k]
                    if (x == null || y == null)
                        "$at.$k (page ${x?.let(KernelGolden::show)}, native ${y?.let(KernelGolden::show)})"
                    else firstDifference(x, y, "$at.$k")
                } ?: if (ka != kb) "$at: member order page $ka native $kb" else null
            }
            a is JsonArray && b is JsonArray ->
                (0 until maxOf(a.items.size, b.items.size)).firstNotNullOfOrNull { i ->
                    val x = a.items.getOrNull(i)
                    val y = b.items.getOrNull(i)
                    if (x == null || y == null) "$at[$i] (lengths ${a.items.size} ${b.items.size})"
                    else firstDifference(x, y, "$at[$i]")
                }
            a == b -> null
            else -> "$at: page ${KernelGolden.show(a)} native ${KernelGolden.show(b)}"
        }

    /** Null when [case] gives the page's answer, else where the answers first differ. */
    fun difference(case: KernelGolden.Case): String? {
        val got =
            try {
                run(case)
            } catch (e: RuntimeException) {
                return "${case.id}: threw ${e::class.simpleName}: ${e.message} at ${e.stackTrace.take(4).joinToString(" < ")}"
            }
        return firstDifference(case.out, got, "out")?.let { "${case.id}: $it" }
    }
}
