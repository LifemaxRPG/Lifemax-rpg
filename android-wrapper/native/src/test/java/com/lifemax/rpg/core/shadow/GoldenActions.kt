package com.lifemax.rpg.core.shadow

import com.lifemax.rpg.core.economy.EconomyGoldenTest
import com.lifemax.rpg.core.economy.FixedAwards
import com.lifemax.rpg.core.economy.GoldenContent
import com.lifemax.rpg.core.economy.GoldenRun
import com.lifemax.rpg.core.economy.SaveCodec
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNull
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
 * start, as [GoldenRun] reads them), and from WP6c today's plan facts with every other kind, as the
 * page sends them to an app that compares quests.
 *
 * WP6c: the cases run as the recorder ran them ([PageSetup]): without the Daily Hand's library and
 * its checks, unless the case says `hand` (the library) or `verify` (its checks inside every save,
 * the `save/compose-*` family). In a hand case the page's state between two entry calls, and the
 * fields the case leaves out (`omit`, and `dailyDraw` unless it records it), are native's own
 * prediction for the action (the runner's composition), not the economy replay's.
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
            "toggleQuest" to "quest.toggle",
            "createQuest" to "quest.create",
            "dhReroll" to "hand.reroll",
            "dhAutoVerify" to "hand.verify",
            "dhEnsure" to "hand.ensure",
            "lmaHealthSync" to "health.sync",
            "applyDecay" to "decay.apply",
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
    class Built(
        val caseId: String,
        val action: FakeShadowPage.Action,
        val recorded: Boolean,
        val setup: PageSetup = ECONOMY,
    )

    /** How `gen_economy_golden.js` runs a case that says neither `hand` nor `verify`. */
    val ECONOMY = PageSetup(library = false, verify = false)

    /** The page content the plan and catalog cases need, as the golden cases show it. */
    val CONTENT: GoldenContentNow = GoldenContentNow()

    /** [ShadowContent] for the golden cases, run under the [setup] of the case being sent. */
    class GoldenContentNow : ShadowContent {
        /** The setup of the envelope being compared; set before each one is sent. */
        var setup: PageSetup = ECONOMY

        override fun setup(): PageSetup = setup

        override fun planDay(date: String): FixedAwards.PlanDay? =
            runCatching { GoldenContent.planDay(date) }.getOrNull()

        override fun planLinks(kind: String): List<String> = GoldenContent.planLinks[kind].orEmpty()

        override fun catalog(id: String): FixedAwards.CatalogEntry? =
            runCatching { GoldenContent.catalog(id) }.getOrNull()
    }

    /** The setup a case was recorded under (its `hand` and `verify` flags). */
    fun setupOf(input: Map<String, Any?>): PageSetup =
        PageSetup(
            library = input["hand"] == true || input["verify"] == true,
            verify = input["verify"] == true,
        )

    private val DEFAULT_NOW = Instant.parse("2026-10-28T12:00:00.000Z").toEpochMilli()

    /**
     * The golden cases that are entry points of the kinds compared (the shield tick's own cases as
     * decay passes with decay off, [DayGoldenActions.shieldAsDecay]).
     */
    fun inScope(): List<Map<String, Any?>> =
        EconomyGoldenTest.cases.map(DayGoldenActions::shieldAsDecay).filter { case ->
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
        val setup = setupOf(input)
        val hand = setup.library
        val unrecorded =
            (input["omit"] as? List<*>).orEmpty().map { it as String }.toSet() +
                (HAND_FIELDS - (input["fields"] as? List<*>).orEmpty().map { it as String }.toSet())
        val minted = intArrayOf(0)
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
                if (fn !in HAND_ONLY) run.call(call)
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
            val nativeReturn = if (hand) null else run.call(call)
            val recorded = i == last
            val after: Map<String, JsonValue>
            val toasts: List<String>
            val returns: JsonValue
            if (recorded) {
                after = loaded(kind, recordedAfter, before, output)
                toasts = goldenToasts.drop(toastsSoFar)
                returns = SaveCodec.json(goldenReturns[i])
            } else if (!hand) {
                after = loaded(kind, nativeState(page, run), before, null)
                toasts = run.toasts.drop(toastsSoFar)
                returns = SaveCodec.json(nativeReturn)
            } else {
                after = page
                toasts = emptyList()
                returns = JsonNull
            }
            if (!hand) toastsSoFar = run.toasts.size
            val drafted =
                FakeShadowPage.Action(
                    kind = kind,
                    args = (call["args"] as? List<*>).orEmpty().map(SaveCodec::json),
                    before = before,
                    after = after,
                    returns = returns,
                    toasts = toasts,
                    env = env(kind, call, page, t0, zone),
                    flags = if (readOnly) listOf(ShadowFlags.READ_ONLY) else emptyList(),
                    t0 = t0,
                    t1 = t0,
                    zone = zone,
                )
            val failing = readOnly || env["failSave"] != null
            val filled =
                if (hand)
                    nativeFilled(drafted, failing, setup, unrecorded.takeIf { recorded }, minted)
                else withWrites(drafted, failing, setup)
            // a composed case of one action recorded the page's own writes, in order (its trace)
            val trace = (output["trace"] as? List<*>)?.map { it as String }
            val action =
                if (trace == null || calls.count { it["fn"] in ENTRIES } != 1) filled
                else
                    filled.copy(
                        saves = writes(trace, "write:"),
                        env = filled.env + journal(writes(trace, "journal:")),
                    )
            if (hand) toastsSoFar += action.toasts.size
            out += Built(id, action, recorded, setup)
            page = action.after
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
        call: Map<String, Any?>,
        state: Map<String, JsonValue>,
        t0: Long,
        zone: String,
    ): Map<String, JsonValue> {
        val env = plain(call["env"])
        val out = LinkedHashMap<String, JsonValue>()
        if (kind == "health.sync") {
            val manual =
                EntryInput.truthy((call["args"] as? List<*>)?.firstOrNull()?.let(SaveCodec::json))
            out["health"] =
                DayGoldenActions.health(
                    plain(env["health"]).takeIf { env["health"] != null },
                    state,
                    manual,
                    t0,
                )
        }
        if (env["confirm"] == false) out["confirm"] = JsonArray(listOf(JsonBool.FALSE))
        (env["prompt"] as? String)?.let { out["prompt"] = JsonArray(listOf(JsonString(it))) }
        (env["dom"] as? Map<*, *>)?.let { out["dom"] = SaveCodec.json(it) }
        val window = kind == "fixed.plan" || kind == "rest.take"
        val days = if (kind == "decay.apply") DECAY_DAYS else if (window) PLAN_DAYS else 0
        out["plan"] = plan(state, t0, zone, days)
        return out
    }

    /**
     * `env.plan` for the default plan: rest on Sundays from its start, nothing done today; the plan
     * kinds' window of [days] before today, every other kind today alone (WP6c).
     */
    private fun plan(
        state: Map<String, JsonValue>,
        t0: Long,
        zone: String,
        days: Long,
    ): JsonObject {
        val today = Instant.ofEpochMilli(t0).atZone(ZoneId.of(zone)).toLocalDate()
        val from = today.minusDays(days)
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
    private fun withWrites(
        action: FakeShadowPage.Action,
        failing: Boolean,
        setup: PageSetup,
    ): FakeShadowPage.Action {
        val writes = predict(action, failing, setup)?.saves ?: 1
        return action.copy(saves = List(writes) { !failing })
    }

    /**
     * A hand case's action as the page sent it, where the recording does not say: native's own
     * writes, and, for an action whose after-state is not the page's ([fill] null: between two
     * entry calls), native's state, return value and toasts; for the recorded last action only the
     * fields in [fill] the case leaves out.
     */
    private fun nativeFilled(
        action: FakeShadowPage.Action,
        failing: Boolean,
        setup: PageSetup,
        fill: Set<String>?,
        minted: IntArray,
    ): FakeShadowPage.Action {
        // between two entry calls the page's ids are the case's counting UUIDs, handed out in turn
        val ids = if (fill == null) List(SPARE_IDS) { uuid(minted[0] + it + 1) } else emptyList()
        val p = predict(action, failing, setup, ids) ?: return action.copy(saves = listOf(!failing))
        minted[0] += newIds(action.before, p.after.orEmpty(), recorded = false)
        if (fill != null) minted[0] += newIds(action.before, action.after, recorded = true)
        val saves = List(p.saves) { !failing }
        val native = p.after ?: return action.copy(saves = saves)
        if (fill == null)
            return action.copy(
                saves = saves,
                after = action.before.filterKeys { it !in native } + native,
                returns = p.returns,
                toasts = p.toasts,
            )
        val after = LinkedHashMap(action.after)
        for (key in fill) native[key]?.let { after[key] = it } ?: after.remove(key)
        return action.copy(saves = saves, after = after)
    }

    /** The outcomes of the trace's writes named [prefix] (`write:` or `journal:`). */
    private fun writes(trace: List<String>, prefix: String): List<Boolean> =
        trace.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) == "true" }

    /** `env.journal` for [writes]; nothing when there were none. */
    private fun journal(writes: List<Boolean>): Map<String, JsonValue> =
        if (writes.isEmpty()) emptyMap()
        else
            mapOf(
                "journal" to JsonArray(writes.map { FakeShadowPage.obj("ok" to JsonBool.of(it)) })
            )

    /** The case's [n]th `crypto.randomUUID()`, as the recorder counts them. */
    private fun uuid(n: Int) = "00000000-0000-4000-8000-%012d".format(n)

    /** How many counting UUIDs [after]'s new activity records hold. */
    private fun newIds(
        before: Map<String, JsonValue>,
        after: Map<String, JsonValue>,
        recorded: Boolean,
    ): Int {
        if (recorded) return 0
        val had = (before["activities"] as? JsonArray)?.items?.size ?: 0
        return (after["activities"] as? JsonArray)?.items.orEmpty().drop(had).count {
            ((it as? JsonObject)?.get("id") as? JsonString)?.value?.startsWith(UUID_PREFIX) == true
        }
    }

    /**
     * Native's prediction for [action] under [setup] (its write count decides the envelope's), with
     * [ids] as the UUIDs the page would mint.
     */
    private fun predict(
        action: FakeShadowPage.Action,
        failing: Boolean,
        setup: PageSetup,
        ids: List<String> = emptyList(),
    ): Prediction? {
        val probe = action.copy(saves = if (failing) listOf(false) else emptyList())
        val envelope =
            (ShadowEnvelopeJson.envelope(FakeShadowPage().send(probe)) as ShadowDecode.Ok).value
        CONTENT.setup = setup
        val after =
            if (ids.isEmpty()) action.after
            else {
                val had = (action.before["activities"] as? JsonArray)?.items.orEmpty()
                val spare = ids.map { JsonObject(linkedMapOf("id" to JsonString(it))) }
                action.before + ("activities" to JsonArray(had + spare))
            }
        val ran =
            ShadowRunner(CONTENT)
                .run(envelope, FakeShadowPage.shadow(action.before), FakeShadowPage.shadow(after))
        return (ran as? RunOutcome.Ran)?.prediction
    }

    /** Daily Hand reads the economy replay does not know. */
    private val HAND_ONLY = setOf("dhCards")

    /** The Daily Hand's fields a hand case records only when it lists them. */
    private val HAND_FIELDS = setOf("dailyDraw", "dhMig", "classContent")

    /** UUIDs offered to native for an action between two entry calls (more than any one mints). */
    private const val SPARE_IDS = 8
    private const val UUID_PREFIX = "00000000-0000-4000-8000-"

    @Suppress("UNCHECKED_CAST")
    private fun plain(v: Any?): Map<String, Any?> = v as? Map<String, Any?> ?: emptyMap()

    private fun json(state: Map<String, Any?>): Map<String, JsonValue> =
        (SaveCodec.json(state) as JsonObject).members

    private const val PLAN_DAYS = 60L

    /** The decay pass's window: the furthest back the page's walk may look (2.3). */
    private const val DECAY_DAYS = 400L
}
