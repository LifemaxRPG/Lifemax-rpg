package com.lifemax.rpg.core.adapters

import com.lifemax.rpg.core.economy.DailyHandFacts
import com.lifemax.rpg.core.economy.EconomyClock
import com.lifemax.rpg.core.economy.EconomySave
import com.lifemax.rpg.core.quests.Js
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.SaveDecoding

/**
 * The decoded save the adapters read: the economy's model of it and the whole stored JSON (every
 * field, tracked or not). This is the one source of truth for every fact below while the page is
 * the writer of saves (CONTRACT: the page persists, native reads).
 */
class SaveView(val economy: EconomySave, val stored: JsonObject) {
    companion object {
        fun of(decoded: SaveDecoding.Decoded): SaveView =
            SaveView(decoded.save, decoded.document.stored)
    }
}

/** Where the latest decoded save comes from; null while there is none (before the first load). */
fun interface SaveSource {
    fun view(): SaveView?
}

/**
 * What the workout plan says about a day. Its content (the weeks, the exercises per day) is page
 * code (`planDay`, `dayDone`), not save data, so the plan screen's projection of it is the source
 * of truth; see `ProjectionPlanFacts`.
 */
interface PlanFacts {
    /** `dayDone(day)`: a workout day whose required sets are all ticked; false for rest days. */
    fun isDone(day: String): Boolean

    /** `planDay(day).kind === 'rest'`; false where the plan has no such day. */
    fun isRestDay(day: String): Boolean
}

/** No plan: nothing is done and nothing is a rest day. */
object NoPlanFacts : PlanFacts {
    override fun isDone(day: String) = false

    override fun isRestDay(day: String) = false
}

/**
 * The Daily Hand's outside facts, read from the stored save as the page's `dhVerifyMet` and
 * `luckToday` read them. A value the page would throw on (a `null` entry in a water log) reads as
 * absent, as a damaged log is skipped, not repaired.
 */
object SaveFacts {
    /** The page's `LUCK_VER`. */
    const val LUCK_VERSION = 2.0

    private const val DEFAULT_WATER_GOAL_ML = 2000.0
    private const val PERCENT = 100.0
    private val PET_COUNTS =
        mapOf("feed" to "fedToday", "pet" to "petsToday", "play" to "playsToday")

    /**
     * Today's water as a percentage of the goal: the entries dated [day] summed (`Number(ml) || 0`)
     * over `Math.max(1, Number(mealsMeta.waterGoalMl) || 2000)`. The page tests `total >= goal *
     * pct / 100`; for whole millilitres `total * 100 / goal >= pct` says the same (the gap between
     * two distinct values is far larger than a rounding step); fractional millilitres can differ in
     * the last bit.
     */
    fun waterPercent(stored: JsonObject, day: String): Double {
        val meta = stored["mealsMeta"] as? JsonObject
        val goal =
            maxOf(
                1.0,
                Js.numberOrZero(meta?.get("waterGoalMl")).takeIf { it != 0.0 }
                    ?: DEFAULT_WATER_GOAL_ML,
            )
        val total =
            ((stored["waterLog"] as? JsonArray)?.items.orEmpty())
                .filterIsInstance<JsonObject>()
                .filter { (it["date"] as? JsonString)?.value == day }
                .sumOf { Js.numberOrZero(it["ml"]) }
        return total * PERCENT / goal
    }

    /**
     * The cat's care counts for today, keyed `feed`, `pet` and `play`: zero unless the first cat
     * (`state.pet`, the cat the engine runs as outside a pair call) is alive and its `day` is
     * [day]. A count counts when it is greater than zero as JavaScript compares it.
     */
    fun petCounts(stored: JsonObject, day: String): Map<String, Int> {
        val pet = stored["pet"] as? JsonObject
        val today =
            pet != null && Js.truthy(pet["alive"]) && (pet["day"] as? JsonString)?.value == day
        return PET_COUNTS.mapValues { (_, key) -> if (today && Js.number(pet!![key]) > 0) 1 else 0 }
    }

    /**
     * `luckToday()?.score` as the page's golden context records it: the roll's score when it is
     * today's, of the current version and holds four dice, and it is a number; else null.
     */
    fun luckScore(stored: JsonObject, day: String): Double? {
        val luck = stored["luck"] as? JsonObject ?: return null
        val today = (luck["date"] as? JsonString)?.value == day
        val version = (luck["ver"] as? JsonNumber)?.value == LUCK_VERSION
        val dice = luck["dice"]
        val fourDice = Js.truthy(dice) && Js.length(dice) == FOUR
        return if (today && version && fourDice) (luck["score"] as? JsonNumber)?.value else null
    }

    private const val FOUR = 4.0
}

/** Builds [DailyHandFacts] for a reading of the clock, the save being the source of truth. */
class HandFactsAdapter(private val save: SaveSource, private val plan: PlanFacts) {
    /** The facts for today on [clock]; empty facts while there is no save. */
    fun facts(clock: EconomyClock): DailyHandFacts {
        val stored =
            save.view()?.stored ?: return DailyHandFacts(planDone = plan.isDone(clock.today))
        return DailyHandFacts(
            waterPercent = SaveFacts.waterPercent(stored, clock.today),
            petCounts = SaveFacts.petCounts(stored, clock.today),
            planDone = plan.isDone(clock.today),
        )
    }
}
