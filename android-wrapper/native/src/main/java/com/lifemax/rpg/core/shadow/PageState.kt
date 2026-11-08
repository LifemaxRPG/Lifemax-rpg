package com.lifemax.rpg.core.shadow

import com.lifemax.rpg.core.adapters.PageDays
import com.lifemax.rpg.core.adapters.SaveFacts
import com.lifemax.rpg.core.economy.Dealt
import com.lifemax.rpg.core.economy.Decay
import com.lifemax.rpg.core.economy.EconomyContext
import com.lifemax.rpg.core.economy.EconomySave
import com.lifemax.rpg.core.economy.HandCheck
import com.lifemax.rpg.core.quests.HandContext
import com.lifemax.rpg.core.quests.HandDealer
import com.lifemax.rpg.core.quests.HandIds
import com.lifemax.rpg.core.quests.HandSlot
import com.lifemax.rpg.core.quests.HandStep
import com.lifemax.rpg.core.quests.Js
import com.lifemax.rpg.core.save.EconomySaveCodec
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import com.lifemax.rpg.core.save.SaveDecoding
import com.lifemax.rpg.core.save.SaveDocument
import com.lifemax.rpg.core.save.SaveFields
import com.lifemax.rpg.core.save.SaveReading
import com.lifemax.rpg.core.save.sameStoredValue

/**
 * How the page that recorded an action was running (ECONOMY_SHADOW_PLAN WP6c). The app's page is
 * always [APP]: the Daily Hand's library is there, its check runs inside every save, and a save
 * writes. The page's own golden recorders switch parts off to record one rule at a time, and their
 * cases are replayed under the same switches: [library] off (no cards are dealt), [verify] off
 * (`dhBusy` held, so no check runs), [saves] counted and not run.
 */
data class PageSetup(
    val library: Boolean = true,
    val verify: Boolean = true,
    val saves: Saves = Saves.RUN,
) {
    /** What a `save()` the action asks for does. */
    enum class Saves {
        /** The page's save, composed with the Daily Hand's checks (the app). */
        RUN,

        /** Counted, not run (`gen_quests_golden.js`, `gen_quests_board_golden.js`). */
        COUNTED,
    }

    companion object {
        /** The app's page. */
        val APP = PageSetup()
    }
}

/** The before-state, or a state an action reached, could not be read by the economy's codec. */
class CodecRefused : Exception("codec") {
    override fun fillInStackTrace(): Throwable = this
}

/**
 * The page's state while one action runs natively, held as the page holds it (a JSON object, every
 * key and key order kept) and, when an economy rule needs it, as the economy's model of it. The
 * Daily Hand's steps work on the JSON ([HandDealer], `ClassPacks`, `CustomQuests`); the economy's
 * on the model; each hands over to the other through the codec (its merge, then a page-tolerant
 * reading), so a card dealt inside a save keeps every field the page gives it.
 *
 * The first reading is strict when it can be ([strictRefusal] names the tracked field a strict
 * reading refused, `codec.strict`); later readings read as the page does.
 */
class PageState(start: JsonObject) {
    private var json: JsonObject? = start
    private var document: SaveDocument? = null
    private var save: EconomySave? = null
    private var read = false

    /** The tracked field the first strict reading refused, or null. */
    var strictRefusal: String? = null
        private set

    /** Whether any step needed the economy's model (the encode check applies only then). */
    var usedEconomy = false
        private set

    /** The state as the page holds it now. */
    fun json(): JsonObject {
        json?.let {
            return it
        }
        val merged = EconomySaveCodec.merged(document!!, save!!) ?: throw CodecRefused()
        json = merged
        return merged
    }

    /** The economy's model of the state now; [CodecRefused] when the codec cannot read it. */
    fun economy(): EconomySave {
        usedEconomy = true
        save?.let {
            return it
        }
        val source = json!!
        val decoded = if (read) asPage(source) else first(source)
        read = true
        document = decoded.document
        save = decoded.save
        return decoded.save
    }

    /** The model after an economy step; the JSON follows on the next [json]. */
    fun update(next: EconomySave) {
        if (next === save) return
        economy()
        save = next
        json = null
    }

    /** The JSON after a Daily Hand step; the model follows on the next [economy]. */
    fun adopt(next: JsonObject) {
        if (json === next) return
        json = next
        save = null
        document = null
    }

    /** The decoded document and model the state was last read into, for the encode check. */
    fun encodable(): Pair<SaveDocument, EconomySave>? {
        economy()
        return document?.let { d -> save?.let { d to it } }
    }

    /**
     * A key outside the economy's model as the state holds it now, read without merging: the
     * economy changes none but `dailyDraw.rerollsUsed` (and `quests` records, which [PageHand]
     * reads from the model).
     */
    fun stored(key: String): JsonValue? = json?.get(key) ?: document?.stored?.get(key)

    /** The records of the ledger as last read, for the encode check's sampling. */
    val records: Int
        get() = save?.activities?.size ?: 0

    private fun first(source: JsonObject): SaveDecoding.Decoded {
        val strict = EconomySaveCodec.decode(source, SaveReading.STRICT)
        if (strict is SaveDecoding.Decoded) return strict
        val field = (strict as SaveDecoding.Refused).refusal.problems.firstOrNull()?.field.orEmpty()
        strictRefusal = field.takeIf { it in SaveFields.TRACKED } ?: "save"
        return asPage(source)
    }

    private fun asPage(source: JsonObject): SaveDecoding.Decoded =
        EconomySaveCodec.decode(source, SaveReading.AS_PAGE) as? SaveDecoding.Decoded
            ?: throw CodecRefused()
}

/**
 * The Daily Hand around a save, as the page composes it (SAVE-2, ECONOMY_SHADOW_PLAN WP6c):
 * `dhAutoVerify` deals first (`dhEnsure`, [HandDealer.ensure] on the page's JSON), then verifies; a
 * deal that throws leaves its partial changes and verifies nothing; while the hand is busy (the
 * recorders' `dhBusy`) nothing runs. The deal reads today's luck from the state, rest days through
 * the economy's `ecoRestDay` on the state as it is at that moment, and a record's day as the page
 * reads it.
 */
class PageHand(
    private val state: PageState,
    private val dealer: HandDealer?,
    private val setup: PageSetup,
    private val ctx: () -> EconomyContext,
) : HandCheck {
    /**
     * What the composition did, in order, in the golden family's words (`save/compose-*`):
     * `write:<stored>` for each write the action attempted and `check` for each Daily Hand check
     * that ran.
     */
    val trace = ArrayList<String>()

    /** Notes a write ([stored] or refused) in [trace]. */
    fun wrote(stored: Boolean) {
        trace += "write:$stored"
    }

    /** Notes a check that runs in [trace] (the page's `dhAutoVerify` outside its own saves). */
    fun checking() {
        trace += CHECK
    }

    override fun deal(save: EconomySave): Dealt {
        if (!setup.verify) return Dealt(save, false)
        checking()
        // most saves meet today's hand already dealt: nothing to merge, nothing to deal
        if (dealer == null || settled(save, ctx().today)) return Dealt(save, true)
        state.update(save)
        return when (val step = ensure()) {
            null -> Dealt(save, true)
            else -> Dealt(state.economy(), step is HandStep.Done<*>)
        }
    }

    /**
     * `dhEnsure()` on the state now: the step, or null when there is no library (the page then
     * deals nothing and returns null).
     */
    fun ensure(): HandStep<JsonObject>? {
        val d = dealer ?: return null
        val before = state.json()
        val step = d.ensure(before, context(before))
        if (changed(before, step.save)) state.adopt(step.save)
        return step
    }

    /** What the draw reads outside the save, at this moment of the action. */
    fun context(json: JsonObject): HandContext {
        val c = ctx()
        return HandContext(
            today = c.today,
            luckScore = SaveFacts.luckScore(json, c.today),
            isRestDay = { day -> Decay.isRestDay(state.economy(), day, c) },
            dayOf = { date -> PageDays.dayOf(date, c.clock) },
        )
    }

    /**
     * Whether `dhEnsure()` would change nothing on [save] today: migrated, no card of another day,
     * today's draw of three cards stored and a quest for each of them (QST-41 to QST-43, the
     * conditions of each of its steps). Read from the model and the stored draw, without a merge.
     */
    private fun settled(save: EconomySave, today: String): Boolean {
        if (!Js.truthy(state.stored("dhMig")) || state.stored("quests") !is JsonArray) return false
        val prefix = HandIds.dayPrefix(today)
        if (save.quests.any { it.hand && !it.id.startsWith(prefix) }) return false
        val draw = state.stored("dailyDraw") as? JsonObject ?: return false
        if (!Js.isText(draw["date"], today)) return false
        val cards = (draw["cards"] as? JsonArray)?.items ?: return false
        if (cards.size != HandSlot.COUNT) return false
        val ids = save.quests.mapTo(HashSet()) { it.id }
        return cards.all { (Js.prop(it, "id") as? JsonString)?.value in ids }
    }

    private fun changed(a: JsonObject, b: JsonObject): Boolean =
        DEALT.any { k -> !same(a[k], b[k]) }

    private fun same(a: JsonValue?, b: JsonValue?) =
        a === b || (a != null && b != null && a.sameStoredValue(b))

    companion object {
        /** What a deal may change ([HandDealer]: only these keys ever change). */
        val DEALT = listOf("quests", "dailyDraw", "dhMig", "sparks", "questDone")

        private const val CHECK = "check"
    }
}
