package com.lifemax.rpg.core.directorshadow

import com.lifemax.rpg.core.directorstore.PageJson
import com.lifemax.rpg.core.lifedirector.host.HostCall
import com.lifemax.rpg.core.lifedirector.host.HostCommand
import com.lifemax.rpg.core.lifedirector.host.HostCommands
import com.lifemax.rpg.core.lifedirector.host.HostCompletion
import com.lifemax.rpg.core.lifedirector.host.HostState
import com.lifemax.rpg.core.lifedirector.host.Notice
import com.lifemax.rpg.core.lifedirector.host.SaveOutcome
import com.lifemax.rpg.core.lifedirector.host.TakeBackState
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import com.lifemax.rpg.core.shadow.Difference
import com.lifemax.rpg.core.shadow.Verdict

/** A command check's verdict, and a take-back the page sent in full, kept by its digest for the commands after it (SH-41). */
data class CommandResult(val verdict: Verdict, val learned: Pair<String, TakeBackState>? = null)

/**
 * The Life Director check of a command or the tick (SH-36 to SH-46): WP-LD9's and WP-LD10's pure host commands
 * ([HostCommands.run]) on the state and the controller's memory the page had before it, at the page's moment, against a host
 * that answers with the page's host's answers ([PageAnswers]); then native's reply, its host and store calls with their
 * arguments, its memory after and its state after are compared with the page's. Nothing is awarded, written or shown.
 */
internal class CommandCheck(private val paths: JsonPaths) {
    fun run(before: JsonValue, after: JsonValue, f: CommandFacts, t1: Long, known: (String) -> TakeBackState?): CommandResult {
        val at = f.op + if (f.name.isNotEmpty()) "." + paths.member(f.name) else ""
        val command = command(f) ?: return CommandResult(Verdict.Skipped("unknownOp"))
        var learned: Pair<String, TakeBackState>? = null
        val takeBack: TakeBackState? =
            when (val t = f.before.members["takeBack"]) {
                null,
                JsonNull -> null
                is JsonObject -> {
                    val digest = str(t.members["digest"]) ?: return skipped(TAKE_BACK)
                    val full = t.members["before"]
                    val left = str(t.members["left"])
                    val given = known(digest)
                    if (full != null && left != null) {
                        val mine = TakeBackState(str(t.members["id"]) ?: "", str(t.members["text"]) ?: "", num(t.members["at"]) ?: 0.0, full, left)
                        // native writes the take-back's plan as JSON otherwise: its own take-back would not be the page's (SH-41)
                        if (digest(mine) != digest) return CommandResult(differed(listOf(PathDifference("$at.takeBack.digest", false))))
                        learned = digest to mine
                        mine
                    } else
                        given?.copy(id = str(t.members["id"]) ?: given.id, text = str(t.members["text"]) ?: given.text, at = num(t.members["at"]) ?: given.at)
                            ?: return skipped(TAKE_BACK)
                }
                else -> return skipped(TAKE_BACK)
            }
        val state = state(before, f.before, takeBack)
        val host = PageAnswers(f.calls, f.ids, f.deviceZone, f.retires)
        val o = HostCommands.run(state, f.nowMs.toDouble(), host, command)
        val same = { page: Double, native: Double -> native == f.nowMs.toDouble() && page >= f.nowMs && page <= t1 }
        val diffs = ArrayList<PathDifference>()
        val limit = DirectorShadowLimits.MAX_PATHS
        host.mismatches.firstOrNull()?.let { diffs += PathDifference(if (it >= f.calls.size) "$at.calls[#]" else "$at.calls[$it].fn", false) }
        if (host.mismatches.isEmpty() && o.calls.size != f.calls.size) diffs += PathDifference("$at.calls[#]", false)
        for (i in 0 until minOf(f.calls.size, o.calls.size)) {
            if (diffs.size >= limit) break
            val page = f.calls[i]
            val mine: HostCall = o.calls[i]
            if (page.fn != mine.fn) continue
            diffs += paths.diff(page.args, JsonArray(mine.args), "$at.calls[$i].${page.fn.replace(".", "")}", limit - diffs.size, emptySet(), same)
        }
        val ledger = f.op == "finish" || f.op == "logToday"
        if (diffs.size < limit) diffs += paths.diff(reply(f.reply, ledger), reply(HostCommands.json(o.reply), ledger), "$at.reply", limit - diffs.size, emptySet(), same)
        if (diffs.size < limit) diffs += paths.diff(f.after, session(o.state), "$at.session", limit - diffs.size, emptySet(), same)
        if (diffs.size < limit) diffs += paths.diff(after, o.state.st ?: JsonNull, "$at.state", limit - diffs.size, setOf(TIME), same)
        return CommandResult(verdict(diffs), learned)
    }

    private fun skipped(why: String) = CommandResult(Verdict.Skipped(why))

    private fun str(v: JsonValue?) = (v as? JsonString)?.value

    private fun num(v: JsonValue?) = (v as? JsonNumber)?.value

    /** The page's command as native's (SH-36). */
    private fun command(f: CommandFacts): HostCommand? {
        val p = f.payload as? JsonObject
        fun id(v: JsonValue?): String = (v as? JsonString)?.value ?: v?.let { PageJson.text(it) } ?: ""
        fun minutes(v: JsonValue?): Double = (v as? JsonNumber)?.value ?: Double.NaN
        return when (f.op) {
            "today" -> HostCommand.Today(f.name, f.payload)
            "do" -> HostCommand.Do(f.name, f.payload)
            "finish" -> {
                val opt = p?.members?.get("opt") as? JsonObject
                HostCommand.Finish(
                    id(p?.members?.get("id")),
                    minutes(p?.members?.get("minutes")),
                    HostCompletion.FinishOptions(
                        hostActivityId = str(opt?.members?.get("hostActivityId")),
                        awardXp = opt?.members?.get("awardXp") != JsonBool.FALSE,
                        startEpoch = num(opt?.members?.get("startEpoch")),
                        silent = opt?.members?.get("silent") == JsonBool.TRUE,
                        quietRefusal = opt?.members?.get("quietRefusal") == JsonBool.TRUE,
                    ),
                )
            }
            "logToday" -> HostCommand.LogToday(id(p?.members?.get("id")), minutes(p?.members?.get("minutes")), str(p?.members?.get("opId")))
            "tick" -> HostCommand.Tick
            "syncHost" -> HostCommand.SyncHost
            "processMisses" -> HostCommand.ProcessMisses
            "maybeRollover" -> HostCommand.MaybeRollover
            else -> null
        }
    }

    /** The controller's memory as the page had it (SH-39). */
    private fun state(st: JsonValue, s: JsonObject, takeBack: TakeBackState?): HostState {
        val m = s.members
        val notice = (m["notice"] as? JsonObject)?.members?.let { Notice(num(it["id"]) ?: 0.0, str(it["text"]) ?: "", num(it["at"]) ?: 0.0) }
        val save = (m["lastSave"] as? JsonObject)?.members
        return HostState(
            st = st,
            banner = m["banner"]?.takeIf { it != JsonNull },
            notice = notice,
            tab = str(m["tab"]) ?: "today",
            dayIdx = num(m["dayIdx"]) ?: 0.0,
            pendingDial = m["pendingDial"]?.takeIf { it != JsonNull },
            takeBack = takeBack,
            takenBackOp = m["takenBackOp"] ?: JsonNull,
            lastSave = SaveOutcome(save?.get("ok") == JsonBool.TRUE, str(save?.get("error"))),
            lastFinishRefusal = str(m["lastFinishRefusal"]),
        )
    }

    /** Native's memory in the page's words (the page module's session()). */
    private fun session(s: HostState): JsonObject {
        val m = LinkedHashMap<String, JsonValue>()
        m["banner"] = s.banner ?: JsonNull
        m["notice"] = s.notice?.let { JsonObject(linkedMapOf("id" to JsonNumber(it.id), "text" to JsonString(it.text), "at" to JsonNumber(it.at))) } ?: JsonNull
        m["tab"] = JsonString(s.tab)
        m["dayIdx"] = JsonNumber(s.dayIdx)
        m["pendingDial"] = s.pendingDial ?: JsonNull
        m["takeBack"] =
            s.takeBack?.let {
                JsonObject(linkedMapOf("id" to JsonString(it.id), "text" to JsonString(it.text), "at" to JsonNumber(it.at), "digest" to JsonString(digest(it))))
            } ?: JsonNull
        m["takenBackOp"] = s.takenBackOp
        m["lastSave"] =
            JsonObject(LinkedHashMap<String, JsonValue>().also { o ->
                o["ok"] = JsonBool.of(s.lastSave.ok)
                s.lastSave.error?.let { o["error"] = JsonString(it) }
            })
        m["lastFinishRefusal"] = s.lastFinishRefusal?.let(::JsonString) ?: JsonNull
        return JsonObject(m)
    }

    /** A completion's answer without its ledger, which the state after holds (SH-38). */
    private fun reply(v: JsonValue, ledger: Boolean): JsonValue =
        if (ledger && v is JsonObject) JsonObject(v.members.filterKeys { it != "ledger" }) else v

    private fun verdict(diffs: List<PathDifference>): Verdict =
        if (diffs.isEmpty()) Verdict.Same else differed(diffs)

    private fun differed(diffs: List<PathDifference>): Verdict {
        val found = diffs.map { Difference(it.path, if (it.lastBit) "lastBit" else it.path) }
        val unexplained = diffs.filter { !it.lastBit }.map { Difference(it.path, it.path) }
        return Verdict.Differed(
            found,
            unexplained,
            if (diffs.any { it.lastBit }) setOf(DirectorChecks.DA_FLOAT) else emptySet(),
            null,
            null,
        )
    }

    companion object {
        const val TAKE_BACK = "takeBack"
        private const val TIME = "computeTimeMs"

        /** FNV-1a over the UTF-16 code units, with the length, as the page module's digest(). */
        fun digest(s: String): String {
            var h = 0x811c9dc5.toInt()
            for (c in s) {
                h = h xor c.code
                h *= 0x01000193
            }
            return s.length.toString(36) + "." + (h.toLong() and 0xffffffffL).toString(36)
        }

        private val TIMING = Regex("\"computeTimeMs\":-?[0-9.eE+-]+")

        private fun timeless(t: String) = TIMING.replace(t, "\"computeTimeMs\":0")

        /** A take-back's digest: its plan and ledger as the page's JSON text, and the plan it left, their timings as 0. */
        fun digest(t: TakeBackState): String = digest(timeless(PageJson.text(t.before))) + ":" + digest(timeless(t.left))
    }
}
