package com.lifemax.rpg.core.economy

import kotlin.math.max
import kotlin.math.min

/** Section 11.2: quests and what completing one pays. */
object Quests {
    /** QUEST-1: the period a completion belongs to. */
    fun key(quest: Quest, clock: EconomyClock): String =
        when (quest.cadence) {
            "daily" -> "${quest.id}_${clock.today}"
            "weekly" -> "${quest.id}_${clock.weekMonday}"
            else -> "${quest.id}_once"
        }

    /** QUEST-1: whether the quest is done for its current period. */
    fun isDone(save: EconomySave, quest: Quest, clock: EconomyClock): Boolean =
        !save.questDone[key(quest, clock)].isNullOrEmpty()

    /**
     * QUEST-3: always a commit. Epics pay once a day outside the cap; dailies and weeklies pay
     * three per day or per rolling week [D09], through rested XP and the cap.
     */
    fun xpFor(save: EconomySave, quest: Quest, ctx: EconomyContext): Pair<Double, EconomySave> {
        var base = max(0.0, quest.xp.orZero)
        val today = ctx.today
        val quests = save.activities.filter { it.type == "quest" }
        if (quest.isEpic) {
            val paidToday = quests.count { it.epic == true && ctx.clock.dayOf(it.date) == today }
            return if (paidToday >= Eco.EPIC_PER_DAY) 0.0 to save else save.restedTake(base)
        }
        if (quest.cadence == "daily" && save.hasPerk("compound")) base = jsRound(base * 1.15)
        val since = if (quest.isWeekly) ctx.clock.daysAgo(6) else today
        val counted =
            quests.count { it.cadence == quest.cadence && ctx.clock.dayOf(it.date) >= since }
        if (counted >= Eco.DAILY_QUESTS) return 0.0 to save
        val paid = cap(save.restedPeek(base), save.activities.dayXp(today, ctx.clock))
        // D05: the whole bonus computed from the base is spent when the cap pays anything.
        return paid to if (paid > 0) save.restedTake(base).second else save
    }

    /**
     * QUEST-2: checking pays (and records even a refused completion), unchecking removes the
     * record. It saves in every case that gets past the lock [D16], and a failed save is not taken
     * back [D13]. [note] is the card's note field.
     */
    fun toggle(
        save: EconomySave,
        id: String,
        checked: Boolean,
        ctx: EconomyContext,
        note: String = "",
        verifyHand: Boolean = true,
    ): Outcome<Unit> {
        val quest = save.quests.firstOrNull { it.id == id } ?: return save.unchanged()
        val attr = quest.attribute?.takeIf { it.isNotEmpty() } ?: "discipline"
        if (checked && save.isLocked(quest))
            return save.result(
                Unit,
                "Locked until you reach level ${Levels.stageLevel(quest.stage)}",
            )
        val key = key(quest, ctx.clock)
        val done = save.questDone[key]
        val toggled: Outcome<Unit> =
            when {
                checked && done.isNullOrEmpty() -> complete(save, quest, key, attr, ctx, note)
                !checked && !done.isNullOrEmpty() ->
                    save
                        .copy(
                            questDone = save.questDone - key,
                            activities = save.activities.filterNot { it.id == done },
                        )
                        .result(Unit, "Quest undone · XP removed")
                else -> save.unchanged()
            }
        return toggled.then { SavePipeline.save(it, ctx, verifyHand) }.map {}
    }

    private fun complete(
        save: EconomySave,
        quest: Quest,
        key: String,
        attr: String,
        ctx: EconomyContext,
        note: String,
    ): Outcome<Unit> {
        // D17: two completions in the same millisecond get the same id.
        val recordId = "quest_${ctx.now}"
        val (xp, paid) =
            xpFor(save.copy(questDone = save.questDone + (key to recordId)), quest, ctx)
        val toasts = mutableListOf<String>()
        if (xp == 0.0 && quest.xp.orZero > 0)
            toasts +=
                when {
                    quest.isEpic -> "One epic quest reward per day"
                    quest.isWeekly -> "Quest XP limit reached for this week"
                    else -> "Quest XP limit reached for today"
                }
        val record =
            Activity(
                id = recordId,
                cadence = quest.cadence,
                epic = quest.isEpic,
                skill = "Quest Completion",
                type = "quest",
                name = "Quest: ${quest.name}",
                icon = "flag",
                duration = 0.0,
                xp = xp,
                primary = attr,
                date = ctx.nowIso,
                notes = note.trim().ifEmpty { "Quest completed" },
                split = listOf(XpShare(attr, xp)),
            )
        if (xp > 0) toasts += "Quest complete · +${xp.text()} XP"
        return Outcome(Unit, paid.copy(activities = paid.activities + record), toasts)
    }

    /** QUEST-4: a quest of the owner's own. [xpInput] is the form's text. */
    fun create(
        save: EconomySave,
        name: String,
        cadence: String,
        xpInput: String,
        ctx: EconomyContext,
    ): Outcome<Unit> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return save.result(Unit, "Give your quest a name")
        val quest =
            Quest(
                id = "custom_${ctx.now}",
                name = trimmed,
                desc = "Custom quest created by you.",
                cadence = cadence,
                xp = min(500.0, max(1.0, jsNumber(xpInput).or(25.0))),
                kind = "custom",
            )
        return SavePipeline.save(save.copy(quests = save.quests + quest), ctx)
            .map {}
            .toast("Quest created")
    }
}

/**
 * The Daily Hand's part in the economy (SAVE-2, SPARK-5): its cards are daily quests it can verify
 * and pay.
 */
object DailyHand {
    /**
     * SAVE-2, run after every save: pays, in order, each card quest not done today whose check
     * today's records meet. The saves it causes do not verify again.
     */
    fun verify(save: EconomySave, ctx: EconomyContext): Outcome<Unit> =
        save.quests
            .filter { it.hand }
            .fold(save.unchanged()) { outcome, card ->
                outcome.then { current ->
                    if (
                        Quests.isDone(current, card, ctx.clock) || !isMet(current, card.verify, ctx)
                    )
                        current.unchanged()
                    else Quests.toggle(current, card.id, true, ctx, verifyHand = false)
                }
            }

    /**
     * SAVE-2, whether a card's check is met: today's records other than quests and coach awards.
     */
    fun isMet(save: EconomySave, check: String?, ctx: EconomyContext): Boolean {
        val parts = check.orEmpty().split(":")
        val records =
            save.activities.onDay(ctx.today, ctx.clock).filter {
                it.type != "quest" && it.type != "coach"
            }
        return when (parts[0]) {
            "act" -> {
                val minutes = jsNumber(parts.getOrElse(2) { "" }).orZero
                records.any { it.type == parts.getOrNull(1) && it.duration.orZero >= minutes }
            }
            "any" ->
                records.any { it.duration.orZero >= jsNumber(parts.getOrElse(1) { "" }).orZero }
            "water" -> ctx.handFacts.waterPercent >= jsNumber(parts.getOrElse(1) { "" }).orZero
            "pet" -> (ctx.handFacts.petCounts[parts.getOrNull(1)] ?: 0) > 0
            "plan" -> ctx.handFacts.planDone
            else -> false
        }
    }

    /** SPARK-5: what the next reroll costs. */
    fun rerollCost(save: EconomySave): Double =
        if (save.rerollsUsed.orZero < 1) 0.0 else Sparks.REROLL_PRICE

    /**
     * SPARK-5: one free reroll a day, then 50 Sparks. [cardQuestId] is the quest of the card in the
     * slot; drawing its replacement is outside the economy.
     */
    fun reroll(save: EconomySave, cardQuestId: String?, ctx: EconomyContext): Outcome<Boolean> {
        val card = save.quests.firstOrNull { it.id == cardQuestId }
        if (card != null && Quests.isDone(save, card, ctx.clock))
            return save.result(false, "Finished cards cannot be rerolled")
        val cost = rerollCost(save)
        val paid = if (cost > 0) Sparks.spend(save, cost) else save.result(true)
        if (!paid.value) return paid
        return paid
            .mapSave { s, _ -> s.copy(rerollsUsed = s.rerollsUsed.orZero + 1) }
            .toast(if (cost > 0) "Rerolled for 50 Sparks" else "Free reroll used")
            .then { SavePipeline.save(it, ctx) }
            .map { true }
    }
}
