package com.lifemax.rpg.core.shadow

import com.lifemax.rpg.core.quests.BoardGoldenReplay
import com.lifemax.rpg.core.quests.HandGoldenReplay
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import java.time.Instant
import java.time.ZoneId

/**
 * The Daily Hand and quest board golden cases (`quests-golden.json`, 842, and
 * `quests-board-golden.json`, 1011, recorded from the real page) as the page's transport would send
 * them (ECONOMY_SHADOW_PLAN WP6c): every case whose calls are entry points (`dhEnsure`, `dhReroll`;
 * `applyClassContent`, `createQuest`) becomes one envelope per entry call, in file order, as one
 * long page session. Every call's output is the page's own recording, so each envelope carries the
 * page's before- and after-state, return value, toasts and save requests. The other calls read (the
 * draw's helpers, the board's projections and its ledger: read-only, plan 1.2) or set a field
 * between two actions (`assign`, an edit the next envelope's before-delta carries).
 *
 * The recorders count `save()` and do not run it, hold the Daily Hand's checks (`dhBusy`) and keep
 * its library: the envelopes are compared under that [SETUP]. Each call's outside answers become
 * `env`: `env.plan` from the recorded rest days (the plan's part of `ecoRestDay`, today's window),
 * the form's fields for `createQuest`.
 */
internal object HandGoldenActions {
    /** How both recorders run the page. */
    val SETUP = PageSetup(library = true, verify = false, saves = PageSetup.Saves.COUNTED)

    /** The entry each recorded call names, by kind. */
    val ENTRIES: Map<String, String> =
        mapOf(
            "dhEnsure" to "hand.ensure",
            "dhReroll" to "hand.reroll",
            "applyClassContent" to "quest.pack",
            "createQuest" to "quest.create",
        )

    /** Calls that change nothing (or only a field, between two actions: `assign`). */
    val READS: Set<String> =
        setOf(
            "dhSeed",
            "dhRng",
            "dhMakeQuest",
            "dhRecentAttrXP",
            "dhWeakestAttr",
            "dhDraw",
            "dhCards",
            "dhRerollCost",
            "questToday",
            "questKey",
            "isQuestDone",
            "stageLvl",
            "stageOf",
            "questLocked",
            "renderQuests",
            "renderLedger",
            ASSIGN,
        )

    /**
     * One envelope; [threw] when the page threw (compared: native must throw at the same place).
     */
    class Built(
        val caseId: String,
        val file: String,
        val action: FakeShadowPage.Action,
        val threw: Boolean,
    )

    /** The cases of [file] whose calls are entry points. */
    fun inScope(file: String): List<JsonObject> =
        cases(file).filter { case ->
            val fns = calls(case).map { text(it["fn"]) }
            fns.any { it in ENTRIES } && fns.all { it in ENTRIES || it in READS }
        }

    /** Every in-scope case's envelopes, both files, in file order. */
    fun build(): List<Built> = FILES.flatMap { f -> inScope(f).flatMap { build(it, f) } }

    /** The cases of one file. */
    fun cases(file: String): List<JsonObject> =
        if (file == HandGoldenReplay.FILE) HandGoldenReplay.cases else BoardGoldenReplay.cases

    private fun build(case: JsonObject, file: String): List<Built> {
        val id = text(case["id"])
        val input = case["input"] as JsonObject
        val zone = text(input["zone"])
        var now = text(input["now"])
        var page: Map<String, JsonValue> = (input["state"] as JsonObject).members
        val outs = ((case["output"] as JsonObject)["calls"] as JsonArray).items
        val out = ArrayList<Built>()
        for ((i, call) in calls(case).withIndex()) {
            (call["at"] as? JsonString)?.let { now = it.value }
            val fn = text(call["fn"])
            val args = (call["args"] as? JsonArray)?.items.orEmpty()
            if (fn == ASSIGN) {
                page = page + (text(args[0]) to args[1])
                continue
            }
            val kind = ENTRIES[fn] ?: continue
            val expected = outs[i] as JsonObject
            val t0 = Instant.parse(now).toEpochMilli()
            val after = after(page, expected)
            val returns = expected["returns"] ?: JsonObject.EMPTY
            val threw = returns is JsonObject && "threw" in returns
            val saves = (expected["saves"] as? JsonNumber)?.value?.toInt() ?: 0
            val action =
                FakeShadowPage.Action(
                    kind = kind,
                    args = if (fn == "createQuest") emptyList() else args,
                    before = page,
                    after = after,
                    returns = if (threw) THREW else returns,
                    toasts = (expected["toasts"] as? JsonArray)?.items.orEmpty().map(::text),
                    saves = List(saves) { true },
                    env = env(fn, call["ctx"] as JsonObject, zone, t0),
                    flags = if (threw) listOf(ShadowFlags.THREW) else emptyList(),
                    t0 = t0,
                    t1 = t0,
                    zone = zone,
                )
            out += Built(id, file, action, threw)
            page = after
        }
        return out
    }

    /** The state after a call: the recorded changes and deletions of the recorder's OUT fields. */
    private fun after(
        before: Map<String, JsonValue>,
        expected: JsonObject,
    ): Map<String, JsonValue> {
        val changed = (expected["state"] as? JsonObject)?.members.orEmpty()
        val deleted = (expected["deleted"] as? JsonArray)?.items.orEmpty().map(::text).toSet()
        return (before + changed) - deleted
    }

    /**
     * What the page records for the call: today's plan facts (the recorded `ecoRestDay` answers
     * stand for the plan's part of it; a day not recorded is no rest day) and, for `createQuest`,
     * the form's fields after the browser's own sanitising.
     */
    private fun env(fn: String, ctx: JsonObject, zone: String, t0: Long): Map<String, JsonValue> {
        val today = Instant.ofEpochMilli(t0).atZone(ZoneId.of(zone)).toLocalDate()
        val rest = (ctx["rest"] as? JsonObject)?.members.orEmpty()
        val restDays =
            rest.filter { (day, v) -> v == JsonBool.TRUE && day == today.toString() }.keys
        val out = LinkedHashMap<String, JsonValue>()
        out["plan"] =
            FakeShadowPage.obj(
                "done" to JsonBool.FALSE,
                "rest" to JsonArray(restDays.map { JsonString(it) }),
                "from" to JsonString(today.toString()),
            )
        if (fn == "createQuest") {
            val fields = ctx["fields"] as JsonObject
            out["dom"] =
                FakeShadowPage.obj(
                    "newQuestName" to (fields["name"] as JsonString),
                    "newQuestCadence" to (fields["cadence"] as JsonString),
                    "newQuestXP" to (fields["xp"] as JsonString),
                )
        }
        return out
    }

    private fun calls(case: JsonObject): List<JsonObject> =
        ((case["input"] as JsonObject)["calls"] as JsonArray).items.map { it as JsonObject }

    private fun text(v: JsonValue?): String = (v as JsonString).value

    /** What the page's transport sends as the return value of an action that threw. */
    private val THREW = FakeShadowPage.obj("threw" to JsonBool.TRUE)
    private const val ASSIGN = "assign"
    val FILES = listOf(HandGoldenReplay.FILE, BoardGoldenReplay.FILE)
}
