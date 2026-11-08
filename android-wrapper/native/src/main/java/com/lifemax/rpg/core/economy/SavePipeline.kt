package com.lifemax.rpg.core.economy

import kotlin.math.max
import kotlin.math.min

/**
 * Section 17: what `save()` does to a save before and after it is written, and the take-back of a
 * failed one.
 */
object SavePipeline {
    const val SAVE_FULL = "Could not save to this browser (storage full?)"
    const val SAVE_READ_ONLY = "Not saved: this version is read-only for your save"

    /** SAVE-4: why a save was not written. */
    fun unsavedMessage(ctx: EconomyContext): String =
        if (ctx.storage == Storage.ReadOnly) SAVE_READ_ONLY else SAVE_FULL

    /**
     * MIG-4: the rules of the old economy, rewritten to the new defaults when the owner never
     * changed them.
     */
    private val OLD_RULES =
        mapOf(
            "reading" to (20.0 to "discipline"),
            "business" to (25.0 to "discipline"),
            "strength" to (32.0 to "discipline"),
            "running" to (27.0 to "discipline"),
            "language" to (20.0 to "social"),
            "recovery" to (16.0 to "discipline"),
            "practical" to (23.0 to "intelligence"),
            "task" to (15.0 to ""),
            "custom" to (20.0 to ""),
        )

    /**
     * MIG-1 to MIG-6: the one-time move to the v2 economy. It keeps the character's level and
     * fraction, stops back-dated decay, rewrites untouched old rules, seeds Sparks, and marks every
     * record as credited. Run inside a save, it counts the record just added as old [D22].
     */
    fun migrated(save: EconomySave, ctx: EconomyContext): EconomySave {
        if (save.xpSchemaV2) return save
        val raw = Attribute.ids.sumOf { save.attrXp[it].orNaN.orZero }
        val old = Curve.Attribute.progress(raw)
        val target =
            Levels.characterCumulative(old.level) +
                jsRound(old.into / old.needed * Curve.Character.step(old.level))
        val today = EconomyClock.dayNumber(ctx.today)?.toDouble() ?: Double.NaN
        val decay = save.decay?.takeUnless { it.grace == 3.0 && it.rate == 2.0 }
        val rules =
            Rules(
                save.rules.byType.mapValues { (type, rule) ->
                    val (base, secondary) = OLD_RULES[type] ?: return@mapValues rule
                    val fresh = Rules.defaults.byType[type]
                    if (fresh != null && rule.base == base && rule.secondary == secondary)
                        rule.copy(
                            base = fresh.base,
                            primary = fresh.primary,
                            secondary = fresh.secondary,
                        )
                    else rule
                }
            )
        val money = save.money ?: Money()
        return save.copy(
            legacyCharXpBonus = max(0.0, jsRound(target - raw)),
            decayApplied = save.decayApplied.orEmpty() + Attribute.ids.associateWith { today },
            decay = decay,
            rules = rules,
            customRules = rules.byType,
            money = money.copy(peakBalance = money.balance),
            sparks = jsRound(raw * 0.5),
            activities = save.activities.map { if (it.sparked) it else it.copy(sparked = true) },
            xpSchemaV2 = true,
        )
    }

    /**
     * SAVE-1 and SAVE-2: rebuild the caches, migrate, credit Sparks, write, and (unless
     * [verifyHand] is off, as it is while the Daily Hand verifies) let the Daily Hand pay the cards
     * today's records fulfil. The value is whether the core save was written.
     */
    fun save(save: EconomySave, ctx: EconomyContext, verifyHand: Boolean = true): Outcome<Boolean> {
        val prepared =
            Sparks.synced(migrated(save.rebuilt(), ctx)).let {
                it.copy(customRules = it.rules.byType)
            }
        val written = ctx.storage == Storage.Writable
        ctx.attempts?.attempted(written)
        val core =
            if (ctx.storage == Storage.Full) prepared.result(written, SAVE_FULL)
            else prepared.result(written)
        if (!verifyHand) return core
        val hand = ctx.handCheck ?: return core.then { DailyHand.verify(it, ctx) }.map { written }
        // the page's order: the redraw inside the save (renderQuests) deals and verifies, then the
        // SAVE-2 wrapper around the save deals and verifies again
        return core.then { check(it, hand, ctx) }.then { check(it, hand, ctx) }.map { written }
    }

    /**
     * The quest board's redraw outside a save (`renderQuests()` after a refused toggle): one Daily
     * Hand check when the context composes it ([EconomyContext.handCheck]); nothing otherwise.
     */
    fun redraw(save: EconomySave, ctx: EconomyContext): Outcome<Unit> =
        ctx.handCheck?.let { check(save, it, ctx) } ?: save.unchanged()

    private fun check(save: EconomySave, hand: HandCheck, ctx: EconomyContext): Outcome<Unit> {
        val dealt = hand.deal(save)
        return if (dealt.verify) DailyHand.verify(dealt.save, ctx) else dealt.save.unchanged()
    }

    /**
     * SAVE-4: put the save back as it was before a failed attempt, and say why. The page redraws in
     * between (`takeBack` calls `renderAll()`), so a composed Daily Hand checks once more on the
     * restored save ([redraw]) before the message.
     */
    fun <T> takeBack(was: EconomySave, value: T, ctx: EconomyContext): Outcome<T> =
        redraw(was, ctx).then { it.result(value, unsavedMessage(ctx)) }

    /** What [load] hands back: the save, and whether it may be written. */
    data class Loaded(val save: EconomySave, val readOnly: Boolean)

    /**
     * SAVE-5. [stored] is the stored save already merged over the page's fresh state; a save from a
     * newer build is shown read-only and never migrated or written.
     */
    fun load(stored: EconomySave, ctx: EconomyContext, fromNewerBuild: Boolean = false): Loaded {
        val rebuilt = if (stored.activities.isEmpty()) stored else stored.rebuilt()
        val ruled = rebuilt.copy(rules = Rules.defaults.mergedWith(stored.customRules.orEmpty()))
        return if (fromNewerBuild) Loaded(ruled, readOnly = true)
        else Loaded(migrated(ruled, ctx), readOnly = false)
    }

    /**
     * UNDO-1, deleting an activity: removes the record, its money transactions, its plan day's link
     * and the quest marks that point at it, then saves. Sparks, rested XP and decay stay as they
     * are. [confirmed] is the owner's answer to "are you sure".
     */
    fun deleteActivity(
        save: EconomySave,
        id: String,
        ctx: EconomyContext,
        confirmed: Boolean = true,
    ): Outcome<Unit> {
        if (!confirmed) return save.unchanged()
        val removed =
            save.copy(
                activities = save.activities.filterNot { it.id == id },
                money =
                    save.money?.let { m ->
                        m.copy(transactions = m.transactions.filterNot { it.actId == id })
                    },
                planChecks =
                    save.planChecks.mapValues { (_, c) ->
                        if (c.actId == id) c.copy(actId = null) else c
                    },
                questDone = save.questDone.filterValues { it != id },
            )
        return save(removed, ctx).map {}.toast("Activity undone")
    }

    /** RULES-1, saving the rules: each edited type's base, 1 to 200 (blank or 0 means 1). */
    fun saveRuleBases(
        save: EconomySave,
        bases: Map<String, String>,
        ctx: EconomyContext,
    ): Outcome<Unit> {
        val rules =
            Rules(
                save.rules.byType.mapValues { (type, rule) ->
                    bases[type]?.let {
                        rule.copy(base = min(200.0, max(1.0, jsNumber(it).or(1.0))))
                    } ?: rule
                }
            )
        return save(save.copy(rules = rules), ctx).map {}.toast("XP rules saved")
    }

    /** RULES-1: the default rules restored. */
    fun resetRules(save: EconomySave, ctx: EconomyContext): Outcome<Unit> =
        save(save.copy(rules = Rules.defaults), ctx).map {}.toast("Default XP rules restored")
}
