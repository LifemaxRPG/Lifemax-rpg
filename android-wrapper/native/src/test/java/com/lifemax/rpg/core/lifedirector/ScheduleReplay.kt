package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.lifedirector.KernelGolden.threw
import com.lifemax.rpg.core.lifedirector.PageValues.member
import com.lifemax.rpg.core.lifedirector.PageValues.numberText
import com.lifemax.rpg.core.lifedirector.PageValues.text
import com.lifemax.rpg.core.lifedirector.ScheduleJson.bool
import com.lifemax.rpg.core.lifedirector.ScheduleJson.num
import com.lifemax.rpg.core.lifedirector.ScheduleJson.obj
import com.lifemax.rpg.core.lifedirector.ScheduleJson.str
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonRead
import com.lifemax.rpg.core.save.JsonReader
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * The schedule golden cases, `ld-schedule-golden.json.gz` (recorded from the page in Chromium;
 * format in `tools/gen_ld_schedule_golden.js`), read one line at a time (the file is large: no case
 * is kept once replayed), and the adapters that run each page function name natively and write its
 * answer as the page's JSON.
 */
object ScheduleReplay {
    const val FILE = "ld-schedule-golden.json.gz"

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

    /** generateSchedule's answer without its measured time. */
    private fun generate(input: JsonValue?): JsonValue {
        val out = PlanSchedule.generate(input) as JsonObject
        return JsonObject(out.members.filterKeys { it != "computeTimeMs" })
    }

    /** The recorder's STATE of the plan (tools/gen_ld_schedule_golden.js). */
    private fun state(s: PlanState): JsonValue = obj {
        this["instances"] =
            JsonArray(
                s.sessions.map { x ->
                    val st = x.stored
                    val start = if (st != null) text(st["startEpoch"]) else numberText(x.startEpoch)
                    val slots =
                        if (st != null) text(st["durationSlots"]) else numberText(x.durationSlots)
                    val links = PlanSnapshot.linkCount(x)
                    str(
                        text(x.instanceId) +
                            "@" +
                            start +
                            "+" +
                            slots +
                            (if (links > 0) "#$links" else "")
                    )
                }
            )
        this["placed"] =
            str(
                s.tasks.joinToString("") { t ->
                    if (t.isPlaced) (if (PageValues.truthy(t.coveredBy)) "c" else "1")
                    else if (t.displacedBy != null) "d" else "0"
                }
            )
        this["fair"] = obj {
            this["goalMin"] = JsonObject(s.fair.goalMinutes.mapValues { num(it.value) })
            this["total"] = num(s.fair.total)
        }
        this["bw"] =
            JsonArray(
                s.cycles.map { c ->
                    JsonArray(
                        listOf(
                            num(c.goalMinutes),
                            num(c.highCount),
                            str(c.sessions.joinToString(",") { text(it.instanceId) }),
                            JsonObject(c.perKey.mapValues { num(it.value) }),
                        )
                    )
                }
            )
        this["spacing"] =
            JsonArray(
                s.spacing.map { (k, v) ->
                    JsonArray(listOf(k ?: JsonNull, JsonArray(v.map(::num))))
                }
            )
        this["type"] = str(s.energy.type.joinToString(""))
        val e = s.energy
        this["sums"] =
            JsonArray(
                listOf(
                    num(e.drainPhysical.fold(0.0) { m, x -> m + x }),
                    num(e.drainCognitive.fold(0.0) { m, x -> m + x }),
                    num(e.runAhead.fold(0.0) { m, x -> m + x }),
                    num(e.runBack.fold(0.0) { m, x -> m + x }),
                    num(e.taskPhysical.fold(0.0) { m, x -> m + x }),
                    num(e.taskCognitive.fold(0.0) { m, x -> m + x }),
                    num(s.owner.fold(0.0) { m, x -> m + x }),
                )
            )
    }

    private fun records(m: Map<String, PlacementRecord>) =
        JsonArray(m.map { (id, r) -> ScheduleJson.record(id, r) })

    private fun repair(input: JsonValue?): JsonValue {
        val targets = ArrayList<JsonValue>()
        var repaired: JsonValue = JsonNull
        var filled: JsonValue = JsonNull
        PlanSchedule.generate(
            input,
            object : ScheduleWatch {
                override fun target(s: PlanState, t: PlanTask, log: RepairLog) {
                    targets += obj {
                        this["t"] = str(t.id)
                        this["tier"] = num(t.task.tier)
                        this["placed"] = bool(t.isPlaced)
                        this["iterations"] = num(s.iterations)
                        this["log"] =
                            JsonArray(
                                listOf(
                                    num(log.attempts),
                                    num(log.successes),
                                    num(log.rollbacks),
                                    bool(log.changed),
                                )
                            )
                        this["state"] = state(s)
                    }
                }

                override fun repaired(
                    s: PlanState,
                    log: RepairLog,
                    records: Map<String, PlacementRecord>,
                ) {
                    repaired = obj {
                        this["log"] =
                            (PlanSchedule.repairJson(log) as JsonObject).let {
                                JsonObject(it.members + ("changed" to bool(log.changed)))
                            }
                        this["iterations"] = num(s.iterations)
                        this["state"] = state(s)
                        this["records"] = records(records)
                    }
                }

                override fun filled(s: PlanState, records: Map<String, PlacementRecord>) {
                    filled = obj {
                        this["state"] = state(s)
                        this["records"] = records(records)
                    }
                }
            },
        )
        return obj {
            this["targets"] = JsonArray(targets)
            this["repaired"] = repaired
            this["filled"] = filled
        }
    }

    private fun validate(v: JsonValue?): JsonValue =
        ScheduleValidation.validate(
            member(v, "engineInput"),
            member(v, "planned"),
            member(v, "rejected"),
            member(v, "displacedIds"),
        )

    /** What each page function name runs natively. */
    val ADAPTERS: Map<String, (List<JsonValue>) -> JsonValue> =
        mapOf(
            "generate" to { a -> generate(a[0]) },
            "repair" to { a -> repair(a[0]) },
            "validate" to { a -> validate(a[0]) },
            "hash" to { a -> JsonString(InputHash.of(a[0])) },
        )

    /** What the native code gives for [case], written as the page writes its answer. */
    fun run(case: KernelGolden.Case): JsonValue =
        try {
            ADAPTERS.getValue(case.fn)(case.args)
        } catch (e: PageTypeError) {
            threw("TypeError")
        } catch (e: PageRangeError) {
            threw("RangeError")
        } catch (e: EngineRefusal) {
            threw("Error")
        }

    /** Null when [case] gives the page's answer, else where the answers first differ. */
    fun difference(case: KernelGolden.Case): String? {
        val got =
            try {
                run(case)
            } catch (e: RuntimeException) {
                return "${case.id}: threw ${e::class.simpleName}: ${e.message} at ${e.stackTrace.take(3).joinToString(" < ")}"
            }
        return if (got == case.out) null
        else "${case.id}: " + PlacementReplay.firstDifference(case.out, got, "out")
    }
}
