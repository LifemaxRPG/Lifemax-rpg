package com.lifemax.rpg.core.lifedirector.host

import com.lifemax.rpg.core.directorstore.PageJson
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
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * The host-level golden cases, `ld-host-golden.json.gz` (recorded from logic.js in the page in
 * Chromium; format in `tools/gen_ld_host_golden.js`), replayed through [HostCommands]: every step
 * runs natively at the recorded clock against a [ReplayHost] holding the page's host answers, and
 * its reply, host calls, the page's volatile state and `ST` must be the page's. `computeTimeMs` (a
 * timing, LDO) is not compared.
 */
object HostReplay {
    const val FILE = "ld-host-golden.json.gz"

    /**
     * One case: its id, family, rule ids, the start, the steps (the boot first) and their outcomes.
     */
    class Case(
        val id: String,
        val family: String,
        val rule: String,
        val start: JsonObject,
        val steps: List<JsonObject>,
        val out: List<JsonObject>,
    )

    private fun file(name: String): File {
        val start = File(System.getProperty("user.dir") ?: ".").absoluteFile
        return generateSequence(start) { it.parentFile }
            .map { File(it, "lifemax_modular_v73/tests/fixtures/$name") }
            .firstOrNull { it.isFile } ?: error("$name not found above $start")
    }

    /** Every case of [name] (this file, or WP-LD10's timer golden, which has its format). */
    fun forEachCase(name: String = FILE, visit: (Case) -> Unit) {
        GZIPInputStream(file(name).inputStream()).bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (line in lines) {
                if (!line.startsWith("{\"id\"")) continue
                val read = JsonReader.read(line.removeSuffix(","))
                check(read is JsonRead.Parsed) {
                    "a case of $name does not parse: ${line.take(80)}"
                }
                val c = KernelGolden.decode(read.value) as JsonObject
                visit(
                    Case(
                        text(c["id"]),
                        text(c["family"]),
                        text(c["rule"]),
                        c["start"] as JsonObject,
                        (c["steps"] as JsonArray).items.map { it as JsonObject },
                        (c["out"] as JsonArray).items.map { it as JsonObject },
                    )
                )
            }
        }
    }

    private fun text(v: JsonValue?) = (v as? JsonString)?.value ?: ""

    private fun field(v: JsonValue?, k: String) = (v as? JsonObject)?.get(k)

    private const val SKIPPED = "computeTimeMs"

    /** The first difference between [a] (the page's) and [b] (native's), or null. */
    fun difference(a: JsonValue?, b: JsonValue?, path: String = ""): String? {
        if (a is JsonObject && b is JsonObject) {
            for (k in (a.members.keys + b.members.keys).toSet()) {
                if (k == SKIPPED) continue
                difference(a[k], b[k], "$path.$k")?.let {
                    return it
                }
            }
            return null
        }
        if (a is JsonArray && b is JsonArray) {
            if (a.items.size != b.items.size)
                return "$path: ${a.items.size} items, native ${b.items.size}"
            for (i in a.items.indices) difference(a.items[i], b.items[i], "$path[$i]")?.let {
                return it
            }
            return null
        }
        if (a is JsonNumber && b is JsonNumber)
            return if (a.value == b.value || (a.value.isNaN() && b.value.isNaN())) null
            else "$path: ${a.value} native ${b.value}"
        return if (a == b) null else "$path: ${show(a)} native ${show(b)}"
    }

    private fun show(v: JsonValue?) = v?.let { PageJson.text(it).take(200) } ?: "undefined"

    private val LISTS =
        mapOf(
            "planned" to ("schedule" to "scheduledInstances"),
            "history" to ("schedule" to "historyInstances"),
            "rule" to ("availability" to "rules"),
            "goal" to (null to "goals"),
            "commitment" to (null to "fixedCommitments"),
            "blackout" to ("availability" to "blackouts"),
            "focus" to ("ui" to "focus"),
        )

    /** The tools/ld_host_vm.js resolver: `{$ref, n, field, plus}` read from [st]. */
    fun resolve(v: JsonValue?, st: JsonValue?): JsonValue? =
        when (v) {
            is JsonArray -> JsonArray(v.items.map { resolve(it, st) ?: JsonNull })
            is JsonObject ->
                if (v["\$ref"] == null)
                    JsonObject(v.members.mapValues { resolve(it.value, st) ?: JsonNull })
                else {
                    val kind = text(v["\$ref"])
                    val (outer, inner) = LISTS.getValue(kind)
                    val holder = if (outer == null) st else field(st, outer)
                    val list =
                        when (val held = field(holder, inner)) {
                            is JsonArray -> held.items
                            is JsonObject -> if (kind == "focus") listOf(held) else emptyList()
                            else -> emptyList()
                        }
                    val n = (v["n"] as JsonNumber).value.toInt()
                    val item = list.getOrNull(if (n < 0) list.size + n else n)
                    if (item == null) JsonString("\$missing")
                    else {
                        val x =
                            field(
                                item,
                                text(v["field"]).ifEmpty {
                                    when (kind) {
                                        "planned",
                                        "history" -> "instanceId"
                                        "focus" -> "start"
                                        else -> "id"
                                    }
                                },
                            )
                        val plus = (v["plus"] as? JsonNumber)?.value ?: 0.0
                        if (x is JsonNumber && plus != 0.0) JsonNumber(x.value + plus) else x
                    }
                }
            else -> v
        }

    /** The state a step's delta leaves ([prev] with the members set and removed). */
    fun applyDelta(prev: JsonObject, delta: JsonObject): JsonObject {
        val m = LinkedHashMap(prev.members)
        for (k in ((delta["removed"] as? JsonArray)?.items ?: emptyList())) m.remove(text(k))
        for ((k, v) in (delta["set"] as JsonObject).members) m[k] = v
        return JsonObject(m)
    }

    /** The page's volatile state as the recorder writes it, from native's. */
    fun session(s: HostState): JsonObject {
        val m = LinkedHashMap<String, JsonValue>()
        m["banner"] = s.banner ?: JsonNull
        m["notice"] =
            s.notice?.let {
                JsonObject(
                    linkedMapOf(
                        "id" to JsonNumber(it.id),
                        "text" to JsonString(it.text),
                        "at" to JsonNumber(it.at),
                    )
                )
            } ?: JsonNull
        m["tab"] = JsonString(s.tab)
        m["dayIdx"] = JsonNumber(s.dayIdx)
        m["pendingDial"] = s.pendingDial ?: JsonNull
        m["takeBack"] =
            s.takeBack?.let {
                JsonObject(
                    linkedMapOf(
                        "id" to JsonString(it.id),
                        "text" to JsonString(it.text),
                        "at" to JsonNumber(it.at),
                        "before" to it.before,
                        "left" to JsonString(it.left),
                    )
                )
            } ?: JsonNull
        m["takenBackOp"] = s.takenBackOp
        m["lastSave"] =
            JsonObject(
                LinkedHashMap<String, JsonValue>().also { o ->
                    o["ok"] = JsonBool.of(s.lastSave.ok)
                    s.lastSave.error?.let { o["error"] = JsonString(it) }
                }
            )
        m["lastFinishRefusal"] = s.lastFinishRefusal?.let(::JsonString) ?: JsonNull
        return JsonObject(m)
    }

    /** A take-back's `left` text compared as the plan it holds (its `computeTimeMs` aside). */
    private fun sessionDifference(page: JsonObject, native: JsonObject): String? {
        val left = field(field(page, "takeBack"), "left")
        val nativeLeft = field(field(native, "takeBack"), "left")
        if (left is JsonString && nativeLeft is JsonString) {
            val a = (JsonReader.read(left.value) as JsonRead.Parsed).value
            val b = (JsonReader.read(nativeLeft.value) as JsonRead.Parsed).value
            difference(a, b, ".takeBack.left")?.let {
                return it
            }
            fun strip(o: JsonObject) =
                JsonObject(
                    o.members.mapValues { (k, v) ->
                        if (k == "takeBack" && v is JsonObject) JsonObject(v.members - "left")
                        else v
                    }
                )
            return difference(strip(page), strip(native))
        }
        return difference(page, native)
    }

    /** A recorded call made comparable: `store.load`'s report as native models it. */
    private fun comparable(call: JsonObject): JsonObject {
        if (text(call["fn"]) != "store.load") return call
        val out = call["out"] as JsonObject
        val r = out["report"] as JsonObject
        val report =
            JsonObject(
                r.members.filterKeys {
                    it in setOf("source", "recovered", "quarantined", "migratedFrom")
                }
            )
        return JsonObject(call.members + ("out" to JsonObject(out.members + ("report" to report))))
    }

    private fun recordedCall(c: HostCall) =
        JsonObject(
            linkedMapOf("fn" to JsonString(c.fn), "args" to JsonArray(c.args), "out" to c.out)
        )

    /**
     * The first difference of the case, or null when every step is the page's; [observe] sees the
     * native state after each step (the boot is step 0).
     */
    fun replay(c: Case, observe: (Int, HostState) -> Unit = { _, _ -> }): String? {
        var state = HostState()
        var page = JsonObject(emptyMap())
        val retires = field(field(c.start, "host"), "retired") != null
        val ids = IntArray(1)
        val steps = listOf(JsonObject(linkedMapOf("op" to JsonString("boot")))) + c.steps
        for ((i, raw) in steps.withIndex()) {
            val out = c.out[i]
            val at = (out["at"] as JsonNumber).value
            val s = resolve(JsonObject(raw.members - "at"), state.st) as JsonObject
            val op = text(s["op"])
            val recorded = (out["calls"] as JsonArray).items.map { it as JsonObject }
            val host = ReplayHost(recorded, retires, ids)
            var reply: JsonValue = JsonNull
            var calls: List<HostCall> = emptyList()
            when (op) {
                "banner" -> state = state.copy(banner = s["banner"]?.takeIf { it != JsonNull })
                "focus" -> state = state.copy(st = withFocus(state.st, s["focus"]))
                "storage" -> Unit
                "host" -> Unit
                else -> {
                    val command =
                        command(op, s, state) ?: return "${c.id} step $i: no native command for $op"
                    val o = HostCommands.run(state, at, host, command)
                    state = o.state
                    if (op == "boot" && c.start["banner"] != null)
                        state = state.copy(banner = c.start["banner"]?.takeIf { it != JsonNull })
                    reply = HostCommands.json(o.reply)
                    calls = o.calls
                }
            }
            val where = "${c.id} step $i ($op${s["name"]?.let { " " + text(it) } ?: ""})"
            if (host.mismatches.isNotEmpty()) return "$where: ${host.mismatches.first()}"
            difference(out["reply"], reply, ".reply")?.let {
                return "$where: $it"
            }
            val callsPage = JsonArray(recorded.map(::comparable))
            val callsNative = JsonArray(calls.map(::recordedCall).map(::comparable))
            difference(callsPage, callsNative, ".calls")?.let {
                return "$where: $it"
            }
            sessionDifference(out["session"] as JsonObject, session(state))?.let {
                return "$where: session$it"
            }
            page = applyDelta(page, out["state"] as JsonObject)
            difference(page, state.st ?: JsonNull, ".ST")?.let {
                return "$where: $it"
            }
            observe(i, state)
        }
        return null
    }

    private fun withFocus(st: JsonValue?, focus: JsonValue?): JsonValue? {
        val o = st as? JsonObject ?: return st
        val ui = o["ui"] as? JsonObject ?: return st
        return JsonObject(
            o.members + ("ui" to JsonObject(ui.members + ("focus" to (focus ?: JsonNull))))
        )
    }

    private fun number(v: JsonValue?): Double = (v as? JsonNumber)?.value ?: Double.NaN

    private fun command(op: String, s: JsonObject, state: HostState): HostCommand? =
        when (op) {
            "boot" -> HostCommand.Boot
            "today" -> HostCommand.Today(text(s["name"]), s["payload"])
            "do" -> HostCommand.Do(text(s["name"]), s["payload"])
            "tick" -> HostCommand.Tick
            "focusTick" -> HostCommand.FocusTick
            "syncHost" -> HostCommand.SyncHost
            "processMisses" -> HostCommand.ProcessMisses
            "maybeRollover" -> HostCommand.MaybeRollover
            "repairEarlyDone" -> HostCommand.RepairEarlyDone
            "applyDial" -> HostCommand.ApplyDial(s["P"]?.takeIf { it != JsonNull })
            "applyOutput" ->
                HostCommand.ApplyOutput(
                    s["out"] ?: field(state.st, "schedule") ?: JsonNull,
                    s["label"],
                )
            "finish" -> {
                val opt = s["opt"]
                HostCommand.Finish(
                    text(s["id"]),
                    number(s["minutes"]),
                    HostCompletion.FinishOptions(
                        hostActivityId = (field(opt, "hostActivityId") as? JsonString)?.value,
                        awardXp = field(opt, "awardXp") != JsonBool.FALSE,
                        startEpoch = (field(opt, "startEpoch") as? JsonNumber)?.value,
                        silent = field(opt, "silent") == JsonBool.TRUE,
                        quietRefusal = field(opt, "quietRefusal") == JsonBool.TRUE,
                    ),
                )
            }
            "logToday" ->
                HostCommand.LogToday(
                    text(s["id"]),
                    number(s["minutes"]),
                    (s["opId"] as? JsonString)?.value,
                )
            else -> null
        }
}
