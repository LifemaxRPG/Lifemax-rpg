package com.lifemax.rpg.core.shadow

import com.lifemax.rpg.core.economy.EconomyGoldenTest
import com.lifemax.rpg.core.economy.FixedAwards
import com.lifemax.rpg.core.economy.GoldenContent
import com.lifemax.rpg.core.economy.GoldenRun
import com.lifemax.rpg.core.economy.SaveCodec
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/**
 * The economy golden cases (`economy-golden.json`, 880 recorded from the real page) as the page's
 * transport would send them: every case whose calls are page entry points of the kinds WP6b
 * compares (plus pure reads, which change nothing) becomes one envelope per entry call, in file
 * order, as one long page session. The case's input state is the first action's before-state and
 * its recorded output the last action's after-state, toasts and return value; between two entry
 * calls of one case (four Logs in a row) the page's intermediate state is not recorded, so it is
 * native's own (the golden replay, [GoldenRun]) and only the last action of such a case carries the
 * page's recording ([Built.recorded]).
 *
 * What the goldens do not record is filled in as the page would send it: the number of writes is
 * native's own count for the action (the goldens hold no `writeSave()` calls; the corpus of WP6f
 * will), each stored unless the case fails its save; a `confirm` the case answers no to; a
 * `prompt`'s text; the form values; the plan's rest days in the default plan (Sundays from its
 * start, as [GoldenRun] reads them).
 */
internal object GoldenActions {
    /** The page entry each golden call names, by kind. */
    val ENTRIES: Map<String, String> =
        mapOf(
            "recordActivity" to "log.record",
            "deleteActivity" to "log.delete",
            "undoCat" to "fixed.undo",
            "lmvLogNow" to "voice.log",
            "LifeMaxHost.awardActivity" to "director.award",
            "LifeMaxHost.completeQuest" to "director.quest",
            "ecoCare" to "bonus.care",
            "ecoNutritionCheck" to "bonus.nutrition",
            "recordMoneyTransaction" to "money.record",
            "removeMoneyTransaction" to "money.remove",
            "planToggle" to "fixed.plan",
            "confirmAct" to "fixed.catalog",
            "jcClaim" to "coach.claim",
            "jcCatchUp" to "coach.catchup",
            "ecoBuy" to "spark.buy",
            "ecoAscend" to "ascend",
            "ecoTakeRest" to "rest.take",
            "saveRules" to "rules.save",
            "resetRules" to "rules.reset",
            "saveBase" to "rules.base",
            "saveDecay" to "rules.decay",
            "save" to "save.other",
            "load" to "boot.load",
        )

    /** Calls that read and change nothing; a case may have them around its entry calls. */
    val READS: Set<String> =
        setOf(
            "characterLevel",
            "totalXP",
            "attrLevel",
            "skillLevel",
            "levelData",
            "questLocked",
            "questKey",
            "isQuestDone",
            "activityDate",
            "activityPreview",
            "ecoDayXP",
            "ecoCap",
            "ecoPassive",
            "ecoPerk",
            "ecoSparks",
            "ecoStarXP",
            "ecoRestedPeek",
            "moneyBalance",
            "actStatus",
            "streak",
            "getDecay",
            "lastTrained",
            "ecoRestDay",
            "ecoRestUsed",
            "ecoActive",
            "getToday",
            "dhRerollCost",
            "LifeMaxHost.typeForTag",
        )

    /** One envelope of a case: [recorded] when its after-state is the page's own recording. */
    class Built(val caseId: String, val action: FakeShadowPage.Action, val recorded: Boolean)

    /** The page content the plan and catalog cases need, as the golden cases show it. */
    val CONTENT: ShadowContent =
        object : ShadowContent {
            override fun planDay(date: String): FixedAwards.PlanDay? =
                runCatching { GoldenContent.planDay(date) }.getOrNull()

            override fun planLinks(kind: String): List<String> =
                GoldenContent.planLinks[kind].orEmpty()

            override fun catalog(id: String): FixedAwards.CatalogEntry? =
                runCatching { GoldenContent.catalog(id) }.getOrNull()
        }

    private val DEFAULT_NOW = Instant.parse("2026-10-28T12:00:00.000Z").toEpochMilli()

    /** The golden cases that are entry points of WP6b's kinds. */
    fun inScope(): List<Map<String, Any?>> =
        EconomyGoldenTest.cases.filter { case ->
            val input = case["input"] as Map<*, *>
            val fns = (input["calls"] as List<*>).map { (it as Map<*, *>)["fn"] as String }
            "state" in input &&
                fns.any { it in ENTRIES } &&
                fns.all { it in ENTRIES || it in READS }
        }

    /** Every in-scope case's envelopes, in file order. */
    fun build(): List<Built> {
        val fresh = plain(EconomyGoldenTest.cases.first()["input"])["state"].let(::plain)
        return inScope().flatMap { build(it, fresh) }
    }

    private fun build(case: Map<String, Any?>, fresh: Map<String, Any?>): List<Built> {
        val id = case["id"] as String
        val input = plain(case["input"])
        val output = plain(case["output"])
        val start = fresh + plain(input["state"])
        val now = (input["now"] as? String)?.let { Instant.parse(it).toEpochMilli() } ?: DEFAULT_NOW
        val zone = (input["zone"] as? String) ?: "UTC"
        val run = GoldenRun(fresh, start, now, ZoneId.of(zone))
        val calls = (input["calls"] as List<*>).map(::plain)
        val last = calls.indexOfLast { it["fn"] in ENTRIES }
        val recordedAfter = recordedAfter(start, output)
        val goldenToasts = (output["toasts"] as? List<*>).orEmpty().map { it as String }
        val goldenReturns = output["returns"] as List<*>
        var page: Map<String, JsonValue> = json(start)
        var toastsSoFar = 0
        val out = ArrayList<Built>()
        for ((i, call) in calls.withIndex()) {
            val fn = call["fn"] as String
            if (fn !in ENTRIES) {
                run.call(call)
                continue
            }
            val kind = ENTRIES.getValue(fn)
            val env = plain(call["env"])
            val t0 = (call["at"] as? String)?.let { Instant.parse(it).toEpochMilli() } ?: now
            val before =
                if (kind == "boot.load")
                    json(plain(env["stored"])).filterKeys { it in ShadowKeys.SHADOW_SET }
                else page
            val readOnly = run.readOnly || env["failSave"] == "readonly"
            val nativeReturn = run.call(call)
            val recorded = i == last
            val after: Map<String, JsonValue>
            val toasts: List<String>
            val returns: JsonValue
            if (recorded) {
                after = loaded(kind, recordedAfter, before, output)
                toasts = goldenToasts.drop(toastsSoFar)
                returns = SaveCodec.json(goldenReturns[i])
            } else {
                after = loaded(kind, nativeState(page, run), before, null)
                toasts = run.toasts.drop(toastsSoFar)
                returns = SaveCodec.json(nativeReturn)
            }
            toastsSoFar = run.toasts.size
            val action =
                FakeShadowPage.Action(
                    kind = kind,
                    args = (call["args"] as? List<*>).orEmpty().map(SaveCodec::json),
                    before = before,
                    after = after,
                    returns = returns,
                    toasts = toasts,
                    env = env(kind, env, page, t0, zone),
                    flags = if (readOnly) listOf(ShadowFlags.READ_ONLY) else emptyList(),
                    t0 = t0,
                    t1 = t0,
                    zone = zone,
                )
            out += Built(id, withWrites(action, readOnly || env["failSave"] != null), recorded)
            page = after
        }
        return out
    }

    /** The case's state after its calls: the input, with the recorded changes and deletions. */
    private fun recordedAfter(
        start: Map<String, Any?>,
        output: Map<String, Any?>,
    ): Map<String, JsonValue> {
        val deleted = (output["deleted"] as? List<*>).orEmpty().map { it as String }.toSet()
        return json((start + plain(output["state"])) - deleted)
    }

    /**
     * The generator records only its FIELDS (and the extra fields a case names), and the state
     * after a `load()` is not the input state: `{...freshState(), ...stored}`, so a field the
     * recording does not list is as the stored save has it (the page's fresh state has none of
     * them).
     */
    private fun loaded(
        kind: String,
        after: Map<String, JsonValue>,
        stored: Map<String, JsonValue>,
        output: Map<String, Any?>?,
    ): Map<String, JsonValue> {
        if (kind != "boot.load") return after
        val listed =
            output
                ?.let {
                    plain(it["state"]).keys +
                        (it["deleted"] as? List<*>).orEmpty().map { d -> d as String }
                }
                .orEmpty()
        val out = LinkedHashMap(after)
        for (key in UNRECORDED - listed) stored[key]?.let { out[key] = it } ?: out.remove(key)
        return out
    }

    /**
     * Tracked keys outside `gen_economy_golden.js`'s FIELDS, recorded only by the cases that ask.
     */
    private val UNRECORDED = setOf("weight", "dailyDraw", "journalCoach", "dayReflections")

    /** The page's state after a call the goldens do not record: native's own replay of it. */
    private fun nativeState(
        before: Map<String, JsonValue>,
        run: GoldenRun,
    ): Map<String, JsonValue> {
        val out = LinkedHashMap(before)
        for ((key, value) in run.encoded()) if (value == SaveCodec.Absent) out.remove(key)
        else out[key] = SaveCodec.json(value)
        return out
    }

    /** The env parts the page records for [kind] beside its writes. */
    private fun env(
        kind: String,
        env: Map<String, Any?>,
        state: Map<String, JsonValue>,
        t0: Long,
        zone: String,
    ): Map<String, JsonValue> {
        val out = LinkedHashMap<String, JsonValue>()
        if (env["confirm"] == false) out["confirm"] = JsonArray(listOf(JsonBool.FALSE))
        (env["prompt"] as? String)?.let { out["prompt"] = JsonArray(listOf(JsonString(it))) }
        (env["dom"] as? Map<*, *>)?.let { out["dom"] = SaveCodec.json(it) }
        if (kind == "fixed.plan" || kind == "rest.take") out["plan"] = plan(state, t0, zone)
        return out
    }

    /** `env.plan` for the default plan: rest on Sundays from its start, nothing done today. */
    private fun plan(state: Map<String, JsonValue>, t0: Long, zone: String): JsonObject {
        val today = Instant.ofEpochMilli(t0).atZone(ZoneId.of(zone)).toLocalDate()
        val from = today.minusDays(PLAN_DAYS)
        val start = ((state["plan"] as? JsonObject)?.get("start") as? JsonString)?.value
        val rest =
            generateSequence(from) { it.plusDays(1) }
                .takeWhile { !it.isAfter(today) }
                .filter {
                    start != null && it.toString() >= start && it.dayOfWeek == DayOfWeek.SUNDAY
                }
                .map { JsonString(it.toString()) }
                .toList()
        return FakeShadowPage.obj(
            "done" to JsonBool.FALSE,
            "rest" to JsonArray(rest),
            "from" to JsonString(from.toString()),
        )
    }

    /**
     * The action with as many writes as native makes for it (the goldens hold none), each refused
     * when the case fails its save.
     */
    private fun withWrites(action: FakeShadowPage.Action, failing: Boolean): FakeShadowPage.Action {
        val probe = action.copy(saves = if (failing) listOf(false) else emptyList())
        val envelope =
            (ShadowEnvelopeJson.envelope(FakeShadowPage().send(probe)) as ShadowDecode.Ok).value
        val ran =
            ShadowRunner(CONTENT)
                .run(
                    envelope,
                    FakeShadowPage.shadow(action.before),
                    FakeShadowPage.shadow(action.after),
                )
        val writes = (ran as? RunOutcome.Ran)?.prediction?.saves ?: 1
        return action.copy(saves = List(writes) { !failing })
    }

    @Suppress("UNCHECKED_CAST")
    private fun plain(v: Any?): Map<String, Any?> = v as? Map<String, Any?> ?: emptyMap()

    private fun json(state: Map<String, Any?>): Map<String, JsonValue> =
        (SaveCodec.json(state) as JsonObject).members

    private const val PLAN_DAYS = 60L
}
