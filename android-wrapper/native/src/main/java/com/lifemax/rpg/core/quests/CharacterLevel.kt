package com.lifemax.rpg.core.quests

import com.lifemax.rpg.core.economy.Curve
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonValue

/**
 * `characterLevel().level` from a save: `levelData(totalXP() + (legacyCharXPBonus || 0) +
 * sum(Object.values(ascended || {})), charXpForLevel)` (CURVE-3, CURVE-4). The stage locks read it.
 * The board takes the level as a plain input; this is how the app derives it from the save.
 */
object CharacterLevel {
    /**
     * The character level, or null when the save holds something this does not read (a value that
     * is not a number, where the page's `+` would join texts, or no `attrXP`, where the page
     * throws).
     */
    fun of(save: JsonObject): Int? {
        val attr = numbers(save["attrXP"]) ?: return null
        val legacy = save["legacyCharXPBonus"]
        val bonus =
            when {
                !Js.truthy(legacy) -> 0.0
                legacy is JsonNumber -> legacy.value
                else -> return null
            }
        val ascended = save["ascended"]
        val ascendedValues =
            if (Js.truthy(ascended)) numbers(ascended) ?: return null else emptyList()
        val total =
            attr.fold(0.0) { a, b -> a + b } + bonus + ascendedValues.fold(0.0) { a, b -> a + b }
        return Curve.Character.progress(total).level
    }

    private fun numbers(value: JsonValue?): List<Double>? =
        (value as? JsonObject)?.members?.values?.map {
            if (it is JsonNumber) it.value else return null
        }
}
