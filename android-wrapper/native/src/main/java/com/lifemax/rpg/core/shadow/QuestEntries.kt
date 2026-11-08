package com.lifemax.rpg.core.shadow

import com.lifemax.rpg.core.economy.DailyHand
import com.lifemax.rpg.core.economy.EconomyContext
import com.lifemax.rpg.core.economy.HandCheck
import com.lifemax.rpg.core.economy.Quests
import com.lifemax.rpg.core.economy.SavePipeline
import com.lifemax.rpg.core.quests.BoardClock
import com.lifemax.rpg.core.quests.BoardTuning
import com.lifemax.rpg.core.quests.ClassPacks
import com.lifemax.rpg.core.quests.CustomQuests
import com.lifemax.rpg.core.quests.HandDealer
import com.lifemax.rpg.core.quests.HandOdds
import com.lifemax.rpg.core.quests.HandStep
import com.lifemax.rpg.core.quests.QuestContent
import com.lifemax.rpg.core.quests.QuestContentLoader
import com.lifemax.rpg.core.quests.QuestContentRead
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/**
 * Native's quest code as one bundle: the packaged quest content (the same file the page's content
 * fingerprint is checked against), the Daily Hand's odds and the board's tuning. The mutation tests
 * change one number here and expect the comparison to notice; the app uses [PACKAGED].
 */
class QuestNative(
    val content: QuestContent?,
    val odds: HandOdds = HandOdds.PAGE,
    val tuning: BoardTuning = BoardTuning.PAGE,
    /** The Daily Hand's check as a save composes it; the tests wrap it to compose it wrongly. */
    val check: (HandCheck) -> HandCheck = { it },
) {
    /** The Daily Hand's deal under [setup], or null without a library. */
    fun dealer(setup: PageSetup): HandDealer? =
        if (setup.library) content?.let { HandDealer(it, odds) } else null

    companion object {
        /** The app's: the packaged content and the page's numbers. */
        val PACKAGED: QuestNative by lazy {
            QuestNative((QuestContentLoader.packaged as? QuestContentRead.Loaded)?.content)
        }
    }
}

/** What one Daily Hand or quest entry did: its return value and its toasts, in order. */
class HandRun(val returns: JsonValue, val toasts: List<String> = emptyList())

/** What a Daily Hand or quest entry works with beside its input and context. */
class HandWorld(
    val state: PageState,
    val hand: PageHand,
    val quests: QuestNative,
    val setup: PageSetup,
) {
    /**
     * The `save()` calls a step asked for ([requests]), as the page runs them: composed with the
     * Daily Hand's checks, or only counted under [PageSetup.Saves.COUNTED]. The toasts they showed.
     */
    fun saved(requests: Int, ctx: EconomyContext): List<String> {
        val toasts = ArrayList<String>()
        repeat(requests) {
            if (setup.saves == PageSetup.Saves.COUNTED) ctx.attempts?.attempted(true)
            else {
                val out = SavePipeline.save(state.economy(), ctx)
                state.update(out.save)
                toasts += out.toasts
            }
        }
        return toasts
    }

    /** Takes [step]'s state as the page's. */
    fun <T> adopt(step: HandStep<T>): HandStep<T> = step.also { state.adopt(it.save) }
}

/**
 * One shadow point of WP6c whose page code works on the save as JSON (the Daily Hand, the quest
 * board): the env parts it may carry and the native call, glue included. With [comparesThrow] an
 * action the page threw out of is compared too: native's code stops where the page's does, with the
 * changes made before it ([HandStep.Threw]), so the partial state, the toasts and the `{threw:
 * true}` return must agree.
 */
class HandEntry(
    val kind: String,
    val shape: EnvShape,
    val comparesThrow: Boolean = true,
    val run: (EntryInput, EconomyContext, HandWorld) -> HandRun,
)

/**
 * The quest and Daily Hand kinds WP6c compares (ECONOMY_SHADOW_PLAN 1.2) that run on the page's
 * JSON: `quest.create` (`createQuest`, the board's form), `quest.pack` (`applyClassContent`),
 * `hand.ensure` (`dhEnsure`), `hand.reroll` (`dhReroll`) and `hand.verify` (`dhAutoVerify`).
 * `quest.toggle` is an economy entry ([ShadowEntries]).
 */
object QuestEntries {
    /** The quest board's form: name, cadence, XP. */
    private val BOARD_FORM = listOf("newQuestName", "newQuestCadence", "newQuestXP")

    private val all: List<HandEntry> =
        listOf(
            HandEntry("quest.create", EnvShape(dom = BOARD_FORM.toSet())) { input, ctx, w ->
                input.count(0, MAX_IGNORED_ARGS)
                create(input, ctx, w, BOARD_FORM)
            },
            HandEntry("quest.pack", EnvShape.SAVES) { input, _, w ->
                input.count(0)
                val content = w.quests.content ?: throw ContentMissing()
                threwOr(w.adopt(ClassPacks(content).apply(w.state.json()))) { HandRun(JsonNull) }
            },
            HandEntry("hand.ensure", EnvShape.SAVES) { input, _, w ->
                input.count(0)
                val step = w.hand.ensure() ?: return@HandEntry HandRun(JsonNull)
                threwOr(step) { HandRun(it.value) }
            },
            HandEntry("hand.reroll", EnvShape.SAVES) { input, ctx, w ->
                input.count(1)
                val dealer = w.quests.dealer(w.setup) ?: return@HandEntry THREW
                val json = w.state.json()
                val step = w.adopt(dealer.reroll(json, slot(input.arg(0)), w.hand.context(json)))
                threwOr(step) {
                    // dhReroll shows its message, then saves
                    val toasts = it.toasts + w.saved(it.saveRequests, ctx)
                    HandRun(JsonBool.of(it.value), toasts)
                }
            },
            // dhAutoVerify catches every error itself: a throw out of it is not its own
            HandEntry("hand.verify", EnvShape.SAVES, comparesThrow = false) { input, ctx, w ->
                input.count(0, MAX_IGNORED_ARGS)
                if (!w.setup.verify) return@HandEntry HandRun(ZERO)
                w.hand.checking()
                if (w.hand.ensure() is HandStep.Threw) return@HandEntry HandRun(ZERO)
                val before = w.state.economy()
                val cards = before.quests.filter { it.hand }
                val out = DailyHand.verify(before, ctx)
                w.state.update(out.save)
                val paid =
                    cards.count {
                        !Quests.isDone(before, it, ctx.clock) &&
                            Quests.isDone(out.save, it, ctx.clock)
                    }
                HandRun(JsonNumber(paid.toDouble()), out.toasts)
            },
        )

    private val byKind: Map<String, HandEntry> = all.associateBy { it.kind }

    /** The kinds here. */
    val KINDS: Set<String> = byKind.keys

    /** The entry of [kind], or null. */
    fun of(kind: String): HandEntry? = byKind[kind]

    /**
     * `createQuest()`: the quest from the form's fields, then the save it asks for, then "Quest
     * created" (the save's own messages come first).
     */
    private fun create(
        input: EntryInput,
        ctx: EconomyContext,
        w: HandWorld,
        form: List<String>,
    ): HandRun {
        val fields = form.map { id -> if (id in input.dom) input.domText(id) else null }
        // a field that is not on the page makes the page's own code throw
        if (fields.any { it == null }) EntryInput.refuse("env.dom")
        val (name, cadence, xp) = fields.map { it!! }
        val step =
            w.adopt(
                w.quests
                    .let { CustomQuests(it.tuning) }
                    .create(w.state.json(), BoardClock.of(ctx.clock), name, cadence, xp)
            )
        return threwOr(step) { HandRun(JsonNull, w.saved(it.saveRequests, ctx) + it.toasts) }
    }

    /** The run of a finished step, or the page's `{threw: true}` where the page throws. */
    private inline fun <T> threwOr(step: HandStep<T>, run: (HandStep.Done<T>) -> HandRun): HandRun =
        when (step) {
            is HandStep.Done -> run(step)
            is HandStep.Threw -> THREW
        }

    /**
     * A slot as `d.cards[slotIdx]` reads it: a whole number or its text names a slot; anything else
     * names none.
     */
    private fun slot(value: JsonValue?): Int =
        when (value) {
            is JsonNumber -> value.value.let { if (it == Math.rint(it)) it.toInt() else NO_SLOT }
            is JsonString -> value.value.takeIf { CANONICAL_INDEX.matches(it) }?.toInt() ?: NO_SLOT
            else -> NO_SLOT
        }

    /** What the page's transport sends for an action that threw. */
    private val THREW = HandRun(JsonObject(linkedMapOf("threw" to JsonBool.TRUE)))
    private val ZERO = JsonNumber(0.0)
    private val CANONICAL_INDEX = Regex("0|[1-9][0-9]{0,8}")
    private const val NO_SLOT = -1

    /** Entries a listener calls may be given an event; it is ignored. */
    private const val MAX_IGNORED_ARGS = 1
}
