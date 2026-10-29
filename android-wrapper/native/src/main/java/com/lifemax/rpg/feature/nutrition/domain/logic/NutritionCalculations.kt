package com.lifemax.rpg.feature.nutrition.domain.logic

import com.lifemax.rpg.feature.nutrition.domain.model.*
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure domain calculation rules ported 1:1 from:
 * - lifemax_modular_v73/src/runtime/segments/50-food.js (fv2FoodForGrams, fv2Totals, fv2ScoreFood,
 *   fv2RingSvg)
 * - lifemax_modular_v73/src/runtime/segments/50-smartdiet.js (sdTarget, sdAggregateShop)
 * - lifemax_modular_v73/src/runtime/segments/44-rewards.js (ecoNutritionCheck ±15% rule)
 */
object NutritionCalculations {

    /** Mirrors fv2Round1(x) in 50-food.js: Math.round((Number(x) || 0) * 10) / 10 */
    fun round1(value: Double): Double = (value * 10.0).roundToInt() / 10.0

    /** Mirrors fv2FoodForGrams(food, grams, slot) in 50-food.js lines 257-274. */
    fun scaleFoodForGrams(
        food: FoodCatalogItem,
        grams: Double,
        slot: MealSlotType,
        entryIndex: Int = 0,
        epochMs: Long = System.currentTimeMillis(),
    ): LoggedFoodEntry {
        val safeGrams = max(1.0, grams)
        val baseServing = if (food.servingG > 0.0) food.servingG else 100.0
        val ratio = safeGrams / baseServing
        val roundedGrams = safeGrams.roundToInt()
        return LoggedFoodEntry(
            entryIndex = entryIndex,
            id = "f_${epochMs}_$entryIndex",
            foodId = food.id,
            name = food.name,
            brand = food.brand,
            mealSlot = slot,
            grams = roundedGrams.toDouble(),
            amountText = "${roundedGrams}g",
            cal = (food.cal * ratio).roundToInt(),
            pro = round1(food.pro * ratio),
            carb = round1(food.carb * ratio),
            fat = round1(food.fat * ratio),
            fiber = round1(food.fiber * ratio),
            sugar = round1(food.sugar * ratio),
            sodiumMg = (food.sodiumMg * ratio).roundToInt(),
            loggedAtEpochMs = epochMs,
        )
    }

    /** Mirrors fv2Totals(day) in 50-food.js lines 276-294. */
    fun computeDayTotals(entries: List<LoggedFoodEntry>): DayMacroTotals {
        var cal = 0.0
        var pro = 0.0
        var carb = 0.0
        var fat = 0.0
        var fiber = 0.0
        var sugar = 0.0
        var sodium = 0.0
        for (item in entries) {
            cal += item.cal
            pro += item.pro
            carb += item.carb
            fat += item.fat
            fiber += item.fiber
            sugar += item.sugar
            sodium += item.sodiumMg
        }
        return DayMacroTotals(
            cal = cal.roundToInt(),
            pro = round1(pro),
            carb = round1(carb),
            fat = round1(fat),
            fiber = round1(fiber),
            sugar = round1(sugar),
            sodiumMg = sodium.roundToInt(),
            entryCount = entries.size,
        )
    }

    /**
     * Mirrors 7-day strip ring status in 50-food.js lines 579-587: ratio = cal / calGoal ok:
     * ratio >= 0.9 && ratio <= 1.08 bad: ratio > 1.08
     */
    fun classifyWeekDayRing(totalCal: Int, calGoal: Int): Pair<Int, WeekDayRingStatus> {
        val safeGoal = max(1, calGoal)
        val ratio = totalCal.toDouble() / safeGoal.toDouble()
        val pct = (ratio * 100.0).roundToInt().coerceIn(0, 100)
        val status =
            when {
                ratio in 0.90..1.08 -> WeekDayRingStatus.OK
                ratio > 1.08 -> WeekDayRingStatus.OVER
                else -> WeekDayRingStatus.UNDER
            }
        return pct to status
    }

    /**
     * Mirrors ecoNutritionCheck() in 44-rewards.js lines 97-113: Awards +8 XP (70% Wellbeing / 30%
     * Physique) when totalCal is within ±15% of calGoal.
     */
    fun isWithinDailyXpWindow(totalCal: Int, calGoal: Int): Boolean {
        if (calGoal <= 0 || totalCal <= 0) return false
        val diffRatio = kotlin.math.abs(totalCal - calGoal).toDouble() / calGoal.toDouble()
        return diffRatio <= 0.15
    }

    /** Mirrors fv2RingSvg ring progress & overflow arc math in 50-food.js lines 522-562. */
    data class MacroArcAngles(val primarySweepDegrees: Float, val overflowSweepDegrees: Float)

    fun computeMacroArcAngles(value: Double, goal: Int): MacroArcAngles {
        val safeGoal = max(1.0, goal.toDouble())
        val ratio = (value / safeGoal).coerceAtLeast(0.0)
        val primaryPct = min(1.0, ratio)
        val overflowPct = (ratio - 1.0).coerceIn(0.0, 1.0)
        return MacroArcAngles(
            primarySweepDegrees = (primaryPct * 360.0).toFloat(),
            overflowSweepDegrees = (overflowPct * 360.0).toFloat(),
        )
    }

    /** Mirrors fv2ScoreFood(food, q, recentIds) in 50-food.js lines 235-255. */
    fun scoreFoodSearch(food: FoodCatalogItem, query: String, recentIds: Set<String>): Int {
        val s = query.trim().lowercase()
        val isRecent = recentIds.contains(food.id)
        if (s.isEmpty()) {
            return (if (isRecent) 200 else 0) + (if (food.isCustom) 60 else 0)
        }
        val name = food.name.lowercase()
        val brand = food.brand.lowercase()
        val hay = "$name $brand"
        var score = 0
        if (name == s) score += 500
        if (name.startsWith(s)) score += 260
        if (hay.contains(s)) score += 140
        val parts = s.split(Regex("\\s+")).filter { it.isNotEmpty() }
        for (p in parts) {
            if (name.startsWith(p)) score += 70 else if (hay.contains(p)) score += 35 else return -1
        }
        if (isRecent) score += 45
        if (food.isCustom) score += 25
        return score
    }

    /** Mirrors sdTarget(day) in 50-smartdiet.js lines 540-588. */
    fun computeSmartDietTargets(
        baseGoals: NutritionGoals,
        fuelLevel: SmartDietFuelLevel,
    ): NutritionGoals {
        val effectiveFuel =
            if (fuelLevel == SmartDietFuelLevel.AUTO) {
                SmartDietFuelLevel.MODERATE
            } else {
                fuelLevel
            }
        val adjustedCal = (baseGoals.calGoal + effectiveFuel.calDelta).coerceIn(1300, 4500)
        val proG = baseGoals.proGoal.coerceIn(70, 260)
        val carbG = max(90, ((adjustedCal * effectiveFuel.carbShare) / 4.0).roundToInt())
        val fatG = max(38, ((adjustedCal * effectiveFuel.fatShare) / 9.0).roundToInt())
        return baseGoals.copy(
            calGoal = adjustedCal,
            proGoal = proG,
            carbGoal = carbG,
            fatGoal = fatG,
        )
    }

    /**
     * Mirrors pack count & perishability calculation in sdAggregateShop (50-smartdiet.js lines
     * 815-829): packs = Math.max(1, Math.ceil(a.g / u - 0.04)) twice = (ing.keeps_days <= 3) &&
     * span >= 3 && a.days.length >= 2
     */
    fun computeShoppingPackCount(totalGrams: Int, unitPackGrams: Int): Int {
        if (unitPackGrams <= 0) return 1
        return max(1, ceil(totalGrams.toDouble() / unitPackGrams.toDouble() - 0.04).toInt())
    }

    fun shouldWarnBuyTwicePerishable(
        keepsDays: Int,
        firstDayIndex: Int,
        lastDayIndex: Int,
        distinctDaysUsed: Int,
    ): Boolean {
        val span = lastDayIndex - firstDayIndex
        return keepsDays in 1..3 && span >= 3 && distinctDaysUsed >= 2
    }
}
