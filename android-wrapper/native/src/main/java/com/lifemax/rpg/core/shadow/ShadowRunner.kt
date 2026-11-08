package com.lifemax.rpg.core.shadow

import com.lifemax.rpg.core.adapters.ContextFactory
import com.lifemax.rpg.core.adapters.DeviceClock
import com.lifemax.rpg.core.adapters.NoHealthReads
import com.lifemax.rpg.core.adapters.SaveView
import com.lifemax.rpg.core.economy.EconomyClock
import com.lifemax.rpg.core.economy.EconomyContext
import com.lifemax.rpg.core.economy.EconomySave
import com.lifemax.rpg.core.economy.IdSource
import com.lifemax.rpg.core.economy.SavePipeline
import com.lifemax.rpg.core.economy.Storage
import com.lifemax.rpg.core.quests.ClassPacks
import com.lifemax.rpg.core.quests.HandStep
import com.lifemax.rpg.core.save.EconomySaveCodec
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import com.lifemax.rpg.core.save.SaveDecoding
import com.lifemax.rpg.core.save.SaveEncoding
import com.lifemax.rpg.core.save.SaveFields
import com.lifemax.rpg.core.save.SaveReading
import com.lifemax.rpg.core.save.SaveRefusalReason
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** Native's answer for one action: what the page should have been left with and shown. */
class Prediction(
    /** The tracked keys native would leave, as the codec merges them over the before-state. */
    val after: Map<String, JsonValue>?,
    val returns: JsonValue,
    val toasts: List<String>,
    /** The writes native attempted (`writeSave()` calls). */
    val saves: Int,
    /**
     * Ids native asked for beyond those the page minted (N1: `ids.extra`), in an action whose
     * writes stored (a take-back drops the record the page minted an id for, so the id is not
     * seen).
     */
    val extraIds: Int,
    /** Why the codec would not write native's save (`codec.encode`, a difference), or null. */
    val encodeRefusal: String?,
    /**
     * Whether a strict writer would refuse a NaN native's save holds where the page stores null
     * (`codec.nonFinite`, counted; the state is compared as the page writes it).
     */
    val nonFinite: Boolean,
    /** Whether this action had the full encode check ([ShadowRunner] samples it on large saves). */
    val encodeChecked: Boolean,
    /**
     * The tracked field the strict reading refused in the before-state (`codec.strict`), or null.
     */
    val strictRefusal: String?,
    /** Whether the storage refused the action's writes (the D13 evidence). */
    val unsaved: Boolean,
    /** The composition's writes and Daily Hand checks, in order ([PageHand.trace]). */
    val trace: List<String> = emptyList(),
)

/** What running one action natively gave. */
sealed interface RunOutcome {
    /** Not compared, for [reason] (a `skipped.*` word). */
    data class Skipped(val reason: String) : RunOutcome

    /** Native's prediction. */
    data class Ran(val prediction: Prediction) : RunOutcome
}

/**
 * Runs one page action natively on the page's before-state (ECONOMY_SHADOW_PLAN 2.6, WP6b, WP6c):
 * the kind's entry ([ShadowEntries] on the economy's model, [QuestEntries] on the page's JSON)
 * through the WP5 adapters ([ContextFactory] over the before-state, a clock fixed at `t0` in the
 * page's zone, the page's locale), with the ids the page minted ([ReplayIds]), the storage outcome
 * the page met and the plan facts it recorded, and every save composed as the page composes it: the
 * save, the Daily Hand's check in the redraw inside it, then the check of the save's wrapper
 * ([PageHand]). Decides first whether the action can be compared at all: its kind, a throw, a day
 * that changed during it (N3), a zone the device's tz data reads differently (N4), a mixed storage
 * outcome, an input that does not read. Pure and read-only: nothing is written.
 *
 * @param content page content the plan and catalog actions need, and how the recording page ran
 *   ([ShadowContent.setup]).
 * @param pageSchema the page's `SAVE_SCHEMA` (from `configure`), for a save from a newer build.
 * @param mutation changes native's result before it is compared: the mutation tests' way to make
 *   native wrong on purpose and see the comparison notice. The app passes none.
 * @param quests native's quest content and numbers ([QuestNative.PACKAGED] in the app).
 */
class ShadowRunner(
    private val content: ShadowContent = ShadowContent.NONE,
    private val pageSchema: () -> Int = { 1 },
    private val mutation: EntryMutation = EntryMutation.NONE,
    private val quests: QuestNative = QuestNative.PACKAGED,
) {
    /** Native's prediction for [envelope], given the page's [before] and [after] shadow keys. */
    fun run(
        envelope: ShadowEnvelope,
        before: Map<String, JsonValue>,
        after: Map<String, JsonValue>,
    ): RunOutcome {
        val kind = envelope.kind
        val load = kind == ShadowEntries.BOOT_LOAD
        val entry = ShadowEntries.of(kind)
        val handEntry = QuestEntries.of(kind)
        when {
            kind in ShadowEntries.LATER -> return skip(LATER)
            kind in ShadowEntries.NOT_NATIVE -> return skip(NOT_NATIVE)
            entry == null && handEntry == null && !load -> return skip(UNKNOWN_KIND)
            ShadowFlags.THREW in envelope.flags && handEntry?.comparesThrow != true ->
                return skip(THREW)
        }
        val zone = ZoneId.of(envelope.zone)
        if (day(envelope.t0, zone) != day(envelope.t1, zone)) return skip(MIDNIGHT)
        val offset =
            zone.rules.getOffset(Instant.ofEpochMilli(envelope.t0)).totalSeconds /
                SECONDS_PER_MINUTE
        if (offset != envelope.offsetMinutes) return skip(TZ_DATA)
        val storage = storage(envelope) ?: return skip(MIXED_STORAGE)
        val input =
            try {
                EntryInput(envelope, entry?.shape ?: handEntry?.shape ?: EnvShape.SAVES)
            } catch (_: InputRefused) {
                return skip(INPUT)
            }
        val start = if (load) PageFresh.under(before) ?: return skip(CONTENT) else before
        val stored = JsonObject(start)
        val state = PageState(stored)
        // an economy entry needs the model of the save; a hand entry reads it only when it asks
        val view =
            try {
                state.economy()
            } catch (_: CodecRefused) {
                if (entry != null || load) return skip(CODEC) else null
            }
        val ids = ReplayIds.of(before, after)
        val plan = EnvPlanFacts(input.plan, day(envelope.t0, zone))
        val setup = content.setup()
        var saves = 0
        lateinit var ctx: EconomyContext
        val hand = PageHand(state, quests.dealer(setup), setup) { ctx }
        ctx =
            ContextFactory(
                    FixedClock(envelope.t0, zone),
                    { view?.let { SaveView(it, stored) } },
                    plan,
                    NoHealthReads,
                    ids,
                    Locale.forLanguageTag(envelope.locale),
                )
                .begin(storage)
                .economy
                .copy(
                    attempts = {
                        saves++
                        hand.wrote(it)
                    },
                    handCheck = quests.check(hand),
                )
        val run =
            try {
                when {
                    load -> load(state, ctx).also { saves += it.second }.first
                    entry != null -> {
                        val ran =
                            entry.run(input, state.economy(), ctx, EntryWorld(before, content))
                        state.update(ran.save)
                        ran.returns to ran.toasts
                    }
                    else ->
                        handEntry!!.run(input, ctx, HandWorld(state, hand, quests, setup)).let {
                            it.returns to it.toasts
                        }
                }
            } catch (_: InputRefused) {
                return skip(INPUT)
            } catch (_: ContentMissing) {
                return skip(CONTENT)
            } catch (_: CodecRefused) {
                return skip(CODEC)
            }
        if (plan.outside) return skip(PLAN_WINDOW)
        val (returns, toasts) = mutated(kind, view, state, run, ctx)
        val merged =
            try {
                state.json()
            } catch (_: CodecRefused) {
                null
            }
        val check =
            if (state.usedEconomy && merged != null && checkDue(state.records)) faithful(state)
            else null
        val prediction =
            Prediction(
                after = merged?.members?.filterKeys { it in COMPARED_KEYS },
                returns = returns,
                toasts = toasts,
                saves = saves,
                extraIds = if (storage == Storage.Writable) ids.extra else 0,
                encodeRefusal = check?.refusal ?: if (merged == null) MERGE_FAILED else null,
                nonFinite = check?.nonFinite == true,
                encodeChecked = check != null,
                strictRefusal = state.strictRefusal,
                unsaved = storage != Storage.Writable,
                trace = hand.trace.toList(),
            )
        return RunOutcome.Ran(prediction)
    }

    /** [mutation] applied to native's result (the tests' seam; the app's is none). */
    private fun mutated(
        kind: String,
        before: EconomySave?,
        state: PageState,
        run: Pair<JsonValue, List<String>>,
        ctx: EconomyContext,
    ): Pair<JsonValue, List<String>> {
        if (mutation === EntryMutation.NONE || before == null) return run
        val ran = EntryRun(state.economy(), run.first, run.second)
        val changed = mutation.apply(kind, before, ran, ctx)
        state.update(changed.save)
        return changed.returns to changed.toasts
    }

    /**
     * The boot's `load()` (SAVE-5): the stored save spread over the fresh state, rules merged,
     * migrated, the class's quest pack applied (`migrateSave`), then written once; a save from a
     * newer build is shown read-only, and a migration that throws leaves the save read-only too,
     * with nothing written.
     */
    private fun load(
        state: PageState,
        ctx: EconomyContext,
    ): Pair<Pair<JsonValue, List<String>>, Int> {
        val schema = (state.json()["schema"] as? JsonNumber)?.value ?: 0.0
        val loaded = SavePipeline.load(state.economy(), ctx, fromNewerBuild = schema > pageSchema())
        state.update(loaded.save)
        val nothing = JsonNull to emptyList<String>()
        if (loaded.readOnly) return nothing to 0
        val content = quests.content ?: throw ContentMissing()
        val pack = ClassPacks(content).apply(state.json())
        state.adopt(pack.save)
        return nothing to if (pack is HandStep.Threw) 0 else 1
    }

    /** What the codec's own write of native's save says (the encode check, plan 1.3 point 5). */
    private class Faithful(val refusal: String?, val nonFinite: Boolean)

    private var actions = 0

    /**
     * Whether this action gets the full encode check (the text written and read back, which costs
     * about five decodings): every action of a save of up to [FULL_CHECK_RECORDS] records, and one
     * in [CHECK_EVERY] above that (the native budget of section 4); the comparison itself always
     * runs on the codec's merge.
     */
    private fun checkDue(records: Int): Boolean =
        records <= FULL_CHECK_RECORDS || actions++ % CHECK_EVERY == 0

    /**
     * Writes native's save with the codec, over the document it was last read from. A NaN where the
     * page stores null (JSON.stringify) is counted, not a difference (a strict native writer would
     * refuse it: each needs a WP8 ruling), and the write is checked again as the page reads; any
     * other refusal is the difference `codec.encode`.
     */
    private fun faithful(state: PageState): Faithful? {
        val (document, save) =
            try {
                state.encodable()
            } catch (_: CodecRefused) {
                null
            } ?: return null
        val first = EconomySaveCodec.encode(document, save)
        if (first !is SaveEncoding.Refused) return Faithful(null, false)
        if (first.refusal.reason != SaveRefusalReason.NON_FINITE_NUMBER)
            return Faithful(first.refusal.reason.name, false)
        val page =
            EconomySaveCodec.decode(document.stored, SaveReading.AS_PAGE) as? SaveDecoding.Decoded
        val again = page?.let { EconomySaveCodec.encode(it.document, save) }
        return Faithful((again as? SaveEncoding.Refused)?.refusal?.reason?.name, true)
    }

    /**
     * The storage outcome the page met (2.6): read-only when the page said so, every write stored
     * (or none attempted) is writable, every write refused is full; a mix is not compared.
     */
    private fun storage(envelope: ShadowEnvelope): Storage? {
        val saves = envelope.env.saves + envelope.env.journal
        return when {
            ShadowFlags.READ_ONLY in envelope.flags -> Storage.ReadOnly
            saves.all { it } -> Storage.Writable
            saves.none { it } -> Storage.Full
            else -> null
        }
    }

    private fun day(millis: Long, zone: ZoneId) = EconomyClock(millis, zone).today

    private fun skip(reason: String) = RunOutcome.Skipped(reason)

    /** The clock of one action: `t0` in the page's zone, read once (DeviceClock semantics). */
    private class FixedClock(private val millis: Long, private val zone: ZoneId) : DeviceClock {
        override fun nowMillis(): Long = millis

        override fun zone(): ZoneId = zone
    }

    companion object {
        /** The Daily Hand's keys outside the tracked ones, compared whole (plan 1.3 point 2). */
        val HAND_KEYS: List<String> = listOf("classContent", "dhMig")

        /** Every key a prediction holds: the tracked ones and the Daily Hand's own. */
        val COMPARED_KEYS: Set<String> = SaveFields.TRACKED.toSet() + HAND_KEYS

        const val LATER = "later"
        const val NOT_NATIVE = "notNative"
        const val UNKNOWN_KIND = "unknownKind"
        const val THREW = "threw"
        const val MIDNIGHT = "midnight"
        const val TZ_DATA = "tzdata"
        const val MIXED_STORAGE = "mixedStorage"
        const val INPUT = "input"
        const val CONTENT = "content"
        const val CODEC = "codec"
        const val PLAN_WINDOW = "planWindow"
        private const val MERGE_FAILED = "MERGE"
        private const val FULL_CHECK_RECORDS = 2_000
        private const val CHECK_EVERY = 16
        private const val SECONDS_PER_MINUTE = 60
    }
}

/** A change to native's result for one action ([ShadowRunner]'s test seam). */
fun interface EntryMutation {
    /** [run] changed, for an action of [kind] that started from [before]. */
    fun apply(kind: String, before: EconomySave, run: EntryRun, ctx: EconomyContext): EntryRun

    companion object {
        /** No change: what the app runs. */
        val NONE = EntryMutation { _, _, run, _ -> run }
    }
}

/**
 * N1, the ids the page minted (ECONOMY_SHADOW_PLAN 1.4): native's id source hands out, in order,
 * the UUIDs and the four-character tokens of the records the page added in this action (read from
 * its after-state). Asking for more than the page minted is counted in [extra] (`ids.extra`) and
 * answered with an id the page cannot have.
 */
class ReplayIds(private val uuids: List<String>, private val tokens: List<String>) : IdSource {
    private var nextUuid = 0
    private var nextToken = 0

    /** Ids asked for beyond those the page minted. */
    var extra = 0
        private set

    override fun uuid(): String = uuids.getOrNull(nextUuid++) ?: "shadow-extra-${++extra}"

    override fun token(): String = tokens.getOrNull(nextToken++) ?: EXTRA_TOKEN.also { extra++ }

    companion object {
        private val UUID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        private val TOKEN = Regex("act_\\d+_([0-9a-z]{4})")
        private const val EXTRA_TOKEN = "----"

        /** The ids of the activity records [after] has past the end of [before]'s. */
        fun of(before: Map<String, JsonValue>, after: Map<String, JsonValue>): ReplayIds {
            val had = (before["activities"] as? JsonArray)?.items?.size ?: 0
            val added = (after["activities"] as? JsonArray)?.items?.drop(had).orEmpty()
            val ids = added.mapNotNull { ((it as? JsonObject)?.get("id") as? JsonString)?.value }
            return ReplayIds(
                ids.filter(UUID::matches),
                ids.mapNotNull { TOKEN.matchEntire(it)?.groupValues?.get(1) },
            )
        }
    }
}
