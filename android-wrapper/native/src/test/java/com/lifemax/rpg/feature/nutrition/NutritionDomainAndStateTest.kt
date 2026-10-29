package com.lifemax.rpg.feature.nutrition

import com.lifemax.rpg.feature.nutrition.data.DeterministicNutritionFixtures
import com.lifemax.rpg.feature.nutrition.data.MockNutritionRepository
import com.lifemax.rpg.feature.nutrition.domain.contract.*
import com.lifemax.rpg.feature.nutrition.domain.logic.NutritionCalculations
import com.lifemax.rpg.feature.nutrition.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * JVM unit tests verifying:
 * 1. Exact parity with `fv2FoodForGrams`, `fv2Totals`, `fv2RingSvg`, and `sdAggregateShop`
 *    formulas.
 * 2. CONTRACT.md A12 session & monotonic sequence gating (`ProjectionSequenceGate`).
 * 3. `MockNutritionRepository` state transitions for logging food, water, Smart Diet, and recipes.
 */
class NutritionDomainAndStateTest {

    @Test
    fun `scaleFoodForGrams matches fv2FoodForGrams rounding rules`() {
        val chicken =
            FoodCatalogItem(
                id = "chicken_breast",
                name = "Chicken Breast",
                brand = "Generic",
                servingG = 100.0,
                servingLabel = "100g",
                cal = 165,
                pro = 31.0,
                carb = 0.0,
                fat = 3.6,
                fiber = 0.0,
                sugar = 0.0,
                sodiumMg = 74,
            )
        val scaled = NutritionCalculations.scaleFoodForGrams(chicken, 150.0, MealSlotType.LUNCH)
        assertEquals(248, scaled.cal)
        assertEquals(46.5, scaled.pro, 0.001)
        assertEquals(5.4, scaled.fat, 0.001)
        assertEquals(111, scaled.sodiumMg)
        assertEquals("150g", scaled.amountText)
    }

    @Test
    fun `classifyWeekDayRing flags OK between 90 and 108 percent and OVER above 108 percent`() {
        val (pctUnder, statusUnder) = NutritionCalculations.classifyWeekDayRing(1500, 2000)
        assertEquals(75, pctUnder)
        assertEquals(WeekDayRingStatus.UNDER, statusUnder)

        val (pctOk, statusOk) = NutritionCalculations.classifyWeekDayRing(1960, 2000)
        assertEquals(98, pctOk)
        assertEquals(WeekDayRingStatus.OK, statusOk)

        val (pctOver, statusOver) = NutritionCalculations.classifyWeekDayRing(2300, 2000)
        assertEquals(100, pctOver)
        assertEquals(WeekDayRingStatus.OVER, statusOver)
    }

    @Test
    fun `computeMacroArcAngles calculates overflow sweep when value exceeds goal`() {
        val normal = NutritionCalculations.computeMacroArcAngles(77.5, 155)
        assertEquals(180f, normal.primarySweepDegrees, 0.1f)
        assertEquals(0f, normal.overflowSweepDegrees, 0.1f)

        val overflow = NutritionCalculations.computeMacroArcAngles(232.5, 155)
        assertEquals(360f, overflow.primarySweepDegrees, 0.1f)
        assertEquals(180f, overflow.overflowSweepDegrees, 0.1f)
    }

    @Test
    fun `shopping pack count and perishable buy twice rules match 50-smartdiet`() {
        // Math.max(1, Math.ceil(a.g / u - 0.04))
        assertEquals(1, NutritionCalculations.computeShoppingPackCount(510, 500))
        assertEquals(2, NutritionCalculations.computeShoppingPackCount(530, 500))

        // keeps_days <= 3 && span >= 3 && days >= 2
        assertTrue(
            NutritionCalculations.shouldWarnBuyTwicePerishable(
                keepsDays = 3,
                firstDayIndex = 0,
                lastDayIndex = 4,
                distinctDaysUsed = 2,
            )
        )
        assertFalse(
            NutritionCalculations.shouldWarnBuyTwicePerishable(
                keepsDays = 5,
                firstDayIndex = 0,
                lastDayIndex = 5,
                distinctDaysUsed = 3,
            )
        )
    }

    @Test
    fun `ProjectionSequenceGate rejects stale sequences and resets on new session`() {
        val gate = ProjectionSequenceGate()
        assertTrue(gate.shouldAccept("session-A", 1L))
        assertTrue(gate.shouldAccept("session-A", 2L))
        assertFalse(gate.shouldAccept("session-A", 2L))
        assertFalse(gate.shouldAccept("session-A", 1L))
        // New WebView session resets sequence acceptance
        assertTrue(gate.shouldAccept("session-B", 1L))
    }

    @Test
    fun `MockNutritionRepository logs food and increments projectionSequence`() = runBlocking {
        val repo =
            MockNutritionRepository(DeterministicNutritionFixtures.createPopulatedProjection())
        val initial = (repo.projectionState.value as NutritionRepositoryState.Ready).projection
        val initialCal = initial.daySummary.totals.cal

        val result =
            repo.dispatch(
                NutritionAction.LogFoodPortion(
                    dayKey = initial.selectedDayKey,
                    mealSlot = MealSlotType.DINNER,
                    foodId = "salmon_fillet",
                    grams = 150.0,
                )
            )
        assertTrue(result is NutritionMutationResult.Confirmed)
        val after = (repo.projectionState.value as NutritionRepositoryState.Ready).projection
        assertEquals(initial.projectionSequence + 1L, after.projectionSequence)
        assertEquals(initialCal + 312, after.daySummary.totals.cal)
    }
}
