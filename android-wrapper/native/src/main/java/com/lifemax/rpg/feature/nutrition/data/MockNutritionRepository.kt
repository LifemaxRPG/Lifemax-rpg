package com.lifemax.rpg.feature.nutrition.data

import com.lifemax.rpg.feature.nutrition.domain.contract.*
import com.lifemax.rpg.feature.nutrition.domain.logic.NutritionCalculations
import com.lifemax.rpg.feature.nutrition.domain.model.*
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Deterministic in-memory NutritionRepository used for standalone previews,
 * NutritionPreviewActivity, and JVM unit tests. Executes the exact same calculation formulas as
 * 50-food.js and 50-smartdiet.js.
 */
class MockNutritionRepository(
    initialProjection: NutritionProjection =
        DeterministicNutritionFixtures.createPopulatedProjection()
) : NutritionRepository {

    private var sequenceCounter: Long = initialProjection.projectionSequence
    private val _projectionState =
        MutableStateFlow<NutritionRepositoryState>(
            NutritionRepositoryState.Ready(initialProjection)
        )
    override val projectionState: StateFlow<NutritionRepositoryState> =
        _projectionState.asStateFlow()

    fun loadPreset(projection: NutritionProjection) {
        sequenceCounter = projection.projectionSequence
        _projectionState.value = NutritionRepositoryState.Ready(projection)
    }

    override suspend fun requestSync(selectedDayKey: String?) {
        val current =
            (_projectionState.value as? NutritionRepositoryState.Ready)?.projection ?: return
        if (selectedDayKey != null && selectedDayKey != current.selectedDayKey) {
            dispatch(NutritionAction.SelectDay(selectedDayKey))
        }
    }

    override suspend fun dispatch(action: NutritionAction): NutritionMutationResult {
        val current =
            (_projectionState.value as? NutritionRepositoryState.Ready)?.projection
                ?: return NutritionMutationResult.Rejected("Repository is not in Ready state")

        sequenceCounter += 1L
        val updated = reduceProjection(current, action, sequenceCounter)
        _projectionState.value = NutritionRepositoryState.Ready(updated)
        return NutritionMutationResult.Confirmed(sequenceCounter)
    }

    private fun reduceProjection(
        current: NutritionProjection,
        action: NutritionAction,
        nextSeq: Long,
    ): NutritionProjection {
        return when (action) {
            is NutritionAction.SelectTopTab ->
                current.copy(projectionSequence = nextSeq, activeTopTab = action.tab)

            is NutritionAction.SelectMealsPane ->
                current.copy(projectionSequence = nextSeq, activeMealsPane = action.pane)

            is NutritionAction.SelectDay -> {
                val updatedWeek =
                    current.weekDays.map { it.copy(isSelected = it.dayKey == action.dayKey) }
                current.copy(
                    projectionSequence = nextSeq,
                    selectedDayKey = action.dayKey,
                    weekDays = updatedWeek,
                )
            }

            is NutritionAction.ShiftDay -> {
                val currentIdx =
                    current.weekDays
                        .indexOfFirst { it.dayKey == current.selectedDayKey }
                        .let { if (it < 0) 2 else it }
                val nextIdx =
                    (currentIdx + action.deltaDays).coerceIn(0, current.weekDays.lastIndex)
                val nextDayKey = current.weekDays[nextIdx].dayKey
                reduceProjection(current, NutritionAction.SelectDay(nextDayKey), nextSeq)
            }

            is NutritionAction.LogFoodPortion -> {
                val food =
                    current.foodCatalog.firstOrNull { it.id == action.foodId } ?: return current
                val nextIndex = current.daySummary.entries.size
                val newEntry =
                    NutritionCalculations.scaleFoodForGrams(
                        food = food,
                        grams = action.grams,
                        slot = action.mealSlot,
                        entryIndex = nextIndex,
                    )
                val updatedEntries = current.daySummary.entries + newEntry
                rebuildWithEntries(current, updatedEntries, nextSeq)
            }

            is NutritionAction.UpdateLoggedFoodItem -> {
                val target =
                    current.daySummary.entries.firstOrNull { it.entryIndex == action.entryIndex }
                        ?: return current
                val baseGrams = if (target.grams > 0.0) target.grams else 100.0
                val ratio = max(1.0, action.newGrams) / baseGrams
                val roundedGrams = max(1.0, action.newGrams).roundToInt()
                val updatedEntry =
                    target.copy(
                        mealSlot = action.newSlot,
                        grams = roundedGrams.toDouble(),
                        amountText = "${roundedGrams}g",
                        cal = (target.cal * ratio).roundToInt(),
                        pro = NutritionCalculations.round1(target.pro * ratio),
                        carb = NutritionCalculations.round1(target.carb * ratio),
                        fat = NutritionCalculations.round1(target.fat * ratio),
                        fiber = NutritionCalculations.round1(target.fiber * ratio),
                        sugar = NutritionCalculations.round1(target.sugar * ratio),
                        sodiumMg = (target.sodiumMg * ratio).roundToInt(),
                    )
                val updatedEntries =
                    current.daySummary.entries.map {
                        if (it.entryIndex == action.entryIndex) updatedEntry else it
                    }
                rebuildWithEntries(current, updatedEntries, nextSeq)
            }

            is NutritionAction.RemoveLoggedFoodItem -> {
                val filtered =
                    current.daySummary.entries
                        .filterNot { it.entryIndex == action.entryIndex }
                        .mapIndexed { idx, item -> item.copy(entryIndex = idx) }
                rebuildWithEntries(current, filtered, nextSeq)
            }

            is NutritionAction.CreateCustomFood -> {
                val newId = "cf_${System.currentTimeMillis()}"
                val newFood =
                    FoodCatalogItem(
                        id = newId,
                        name = action.name.trim(),
                        brand = action.brand.ifBlank { "My food" },
                        servingG = max(1.0, action.servingG),
                        servingLabel = "${action.servingG.roundToInt()}g",
                        cal = max(0, action.cal),
                        pro = max(0.0, action.pro),
                        carb = max(0.0, action.carb),
                        fat = max(0.0, action.fat),
                        fiber = max(0.0, action.fiber),
                        sugar = max(0.0, action.sugar),
                        sodiumMg = max(0, action.sodiumMg),
                        isCustom = true,
                    )
                val nextCatalog = listOf(newFood) + current.foodCatalog
                val nextRecent =
                    (listOf(newFood) + current.recentFoods).distinctBy { it.id }.take(8)
                val withFood =
                    current.copy(
                        projectionSequence = nextSeq,
                        foodCatalog = nextCatalog,
                        recentFoods = nextRecent,
                    )
                if (action.logImmediately) {
                    val newEntry =
                        NutritionCalculations.scaleFoodForGrams(
                            food = newFood,
                            grams = newFood.servingG,
                            slot = action.mealSlot,
                            entryIndex = withFood.daySummary.entries.size,
                        )
                    rebuildWithEntries(withFood, withFood.daySummary.entries + newEntry, nextSeq)
                } else {
                    withFood
                }
            }

            is NutritionAction.QuickAddCalories -> {
                val newEntry =
                    LoggedFoodEntry(
                        entryIndex = current.daySummary.entries.size,
                        id = "qa_${System.currentTimeMillis()}",
                        foodId = "quick_add",
                        name = action.label.ifBlank { "Quick add" },
                        brand = "Quick entry",
                        mealSlot = action.mealSlot,
                        grams = 100.0,
                        amountText = "1 entry",
                        cal = max(0, action.calories),
                        pro = max(0.0, action.pro),
                        carb = max(0.0, action.carb),
                        fat = max(0.0, action.fat),
                    )
                rebuildWithEntries(current, current.daySummary.entries + newEntry, nextSeq)
            }

            is NutritionAction.AdjustWaterMl -> {
                val nextWater =
                    if (action.resetToZero) {
                        0
                    } else {
                        (current.daySummary.waterMl + action.deltaMl).coerceIn(0, 12000)
                    }
                current.copy(
                    projectionSequence = nextSeq,
                    daySummary = current.daySummary.copy(waterMl = nextWater),
                )
            }

            is NutritionAction.UpdateNutritionGoals -> {
                val updatedGoals =
                    NutritionGoals(
                        calGoal = action.calGoal.coerceIn(800, 12000),
                        proGoal = action.proGoal.coerceIn(20, 600),
                        carbGoal = action.carbGoal.coerceIn(20, 1200),
                        fatGoal = action.fatGoal.coerceIn(15, 400),
                        waterGoalMl = action.waterGoalMl.coerceIn(500, 10000),
                    )
                val updatedPlan =
                    current.smartDietPlan.copy(
                        mode = action.sdMode,
                        complexity = action.sdComplexity,
                        fuelLevel = action.sdFuelLevel,
                        vegetarian = action.vegetarian,
                        dairyFree = action.dairyFree,
                        targetCal = updatedGoals.calGoal,
                        targetPro = updatedGoals.proGoal,
                        targetCarb = updatedGoals.carbGoal,
                        targetFat = updatedGoals.fatGoal,
                    )
                current.copy(
                    projectionSequence = nextSeq,
                    goals = updatedGoals,
                    smartDietPlan = updatedPlan,
                )
            }

            is NutritionAction.SmartDietEatNext -> {
                val slot = current.smartDietPlan.upNextSlot ?: return current
                reduceProjection(
                    current,
                    NutritionAction.SmartDietLogSlot(action.dayKey, slot),
                    nextSeq,
                )
            }

            is NutritionAction.SmartDietLogSlot -> {
                val meal =
                    current.smartDietPlan.plannedMeals.firstOrNull { it.slot == action.slotId }
                        ?: return current
                val updatedMeals =
                    current.smartDietPlan.plannedMeals.map {
                        if (it.slot == action.slotId) {
                            it.copy(isLogged = true, isSkipped = false, loggedActualCal = it.cal)
                        } else it
                    }
                val nextUpNext = updatedMeals.firstOrNull { !it.isLogged && !it.isSkipped }?.slot
                val loggedEntry =
                    LoggedFoodEntry(
                        entryIndex = current.daySummary.entries.size,
                        id = "sd_${System.currentTimeMillis()}",
                        foodId = meal.mealId,
                        name = meal.title,
                        brand = "Smart Diet",
                        mealSlot = meal.slot,
                        grams = meal.ingredients.sumOf { it.grams }.toDouble().coerceAtLeast(100.0),
                        amountText = "1 meal",
                        cal = meal.cal,
                        pro = meal.pro.toDouble(),
                        carb = meal.carb.toDouble(),
                        fat = meal.fat.toDouble(),
                        fiber = meal.fiber.toDouble(),
                    )
                val withLogged =
                    rebuildWithEntries(current, current.daySummary.entries + loggedEntry, nextSeq)
                withLogged.copy(
                    smartDietPlan =
                        withLogged.smartDietPlan.copy(
                            plannedMeals = updatedMeals,
                            upNextSlot = nextUpNext,
                        )
                )
            }

            is NutritionAction.SmartDietSkipSlot -> {
                val updatedMeals =
                    current.smartDietPlan.plannedMeals.map {
                        if (it.slot == action.slotId) it.copy(isSkipped = true) else it
                    }
                val nextUpNext = updatedMeals.firstOrNull { !it.isLogged && !it.isSkipped }?.slot
                current.copy(
                    projectionSequence = nextSeq,
                    smartDietPlan =
                        current.smartDietPlan.copy(
                            plannedMeals = updatedMeals,
                            upNextSlot = nextUpNext,
                        ),
                )
            }

            is NutritionAction.SmartDietSwapMeal -> {
                val candidates = current.smartDietPlan.swapCandidatesBySlot[action.slotId].orEmpty()
                val chosen =
                    candidates.firstOrNull { it.mealId == action.newMealId } ?: return current
                val updatedMeals =
                    current.smartDietPlan.plannedMeals.map { meal ->
                        if (meal.slot == action.slotId) {
                            meal.copy(
                                mealId = chosen.mealId,
                                title = chosen.title,
                                prepMinutes = chosen.prepMinutes,
                                cal = chosen.cal,
                                pro = chosen.pro,
                                carb = chosen.carb,
                                fat = chosen.fat,
                            )
                        } else meal
                    }
                current.copy(
                    projectionSequence = nextSeq,
                    smartDietPlan = current.smartDietPlan.copy(plannedMeals = updatedMeals),
                )
            }

            is NutritionAction.SmartDietLogRemainingDay -> {
                var acc = current
                for (meal in current.smartDietPlan.plannedMeals) {
                    if (!meal.isLogged && !meal.isSkipped) {
                        acc =
                            reduceProjection(
                                acc,
                                NutritionAction.SmartDietLogSlot(action.dayKey, meal.slot),
                                nextSeq,
                            )
                    }
                }
                acc
            }

            is NutritionAction.SmartDietUpdatePrefs -> {
                val plan = current.smartDietPlan
                val nextFuel = action.fuelLevel ?: plan.fuelLevel
                val adjustedTargets =
                    NutritionCalculations.computeSmartDietTargets(current.goals, nextFuel)
                current.copy(
                    projectionSequence = nextSeq,
                    smartDietPlan =
                        plan.copy(
                            complexity = action.complexity ?: plan.complexity,
                            fuelLevel = nextFuel,
                            vegetarian = action.vegetarian ?: plan.vegetarian,
                            dairyFree = action.dairyFree ?: plan.dairyFree,
                            targetCal = adjustedTargets.calGoal,
                            targetPro = adjustedTargets.proGoal,
                            targetCarb = adjustedTargets.carbGoal,
                            targetFat = adjustedTargets.fatGoal,
                        ),
                )
            }

            is NutritionAction.SmartDietToggleShopItem -> {
                val updatedAisles =
                    current.smartDietShop.aisleGroups.map { group ->
                        group.copy(
                            items =
                                group.items.map { item ->
                                    if (item.itemKey == action.itemKey) {
                                        item.copy(isChecked = !item.isChecked)
                                    } else item
                                }
                        )
                    }
                val allItems = updatedAisles.flatMap { it.items }
                current.copy(
                    projectionSequence = nextSeq,
                    smartDietShop =
                        current.smartDietShop.copy(
                            aisleGroups = updatedAisles,
                            checkedItemCount = allItems.count { it.isChecked },
                        ),
                )
            }

            is NutritionAction.SmartDietResetShopChecks -> {
                val resetAisles =
                    current.smartDietShop.aisleGroups.map { group ->
                        group.copy(items = group.items.map { it.copy(isChecked = false) })
                    }
                current.copy(
                    projectionSequence = nextSeq,
                    smartDietShop =
                        current.smartDietShop.copy(aisleGroups = resetAisles, checkedItemCount = 0),
                )
            }

            is NutritionAction.LogRecipeToToday -> {
                val recipe =
                    current.recipesState.recipes.firstOrNull { it.id == action.recipeId }
                        ?: return current
                val newEntry =
                    LoggedFoodEntry(
                        entryIndex = current.daySummary.entries.size,
                        id = "rec_${System.currentTimeMillis()}",
                        foodId = recipe.id,
                        name = "🍲 ${recipe.name}",
                        brand = "Recipe",
                        mealSlot = MealSlotType.DINNER,
                        grams = 350.0,
                        amountText = "1 serving",
                        cal = recipe.cal,
                        pro = recipe.pro.toDouble(),
                        carb = recipe.carb.toDouble(),
                        fat = recipe.fat.toDouble(),
                    )
                rebuildWithEntries(current, current.daySummary.entries + newEntry, nextSeq)
            }

            is NutritionAction.CreateCustomRecipe -> {
                val newRecipe =
                    RecipeItem(
                        id = "cr_${System.currentTimeMillis()}",
                        name = action.name.trim(),
                        tags = if (action.tags.isEmpty()) listOf("Custom") else action.tags,
                        cal = max(0, action.cal),
                        pro = max(0, action.pro),
                        carb = max(0, action.carb),
                        fat = max(0, action.fat),
                        ingredients = action.ingredients,
                        instructions = action.instructions,
                        isCustom = true,
                    )
                current.copy(
                    projectionSequence = nextSeq,
                    recipesState =
                        current.recipesState.copy(
                            recipes = listOf(newRecipe) + current.recipesState.recipes
                        ),
                )
            }

            is NutritionAction.DeleteCustomRecipe -> {
                current.copy(
                    projectionSequence = nextSeq,
                    recipesState =
                        current.recipesState.copy(
                            recipes =
                                current.recipesState.recipes.filterNot {
                                    it.id == action.recipeId && it.isCustom
                                }
                        ),
                )
            }
        }
    }

    private fun rebuildWithEntries(
        current: NutritionProjection,
        entries: List<LoggedFoodEntry>,
        nextSeq: Long,
    ): NutritionProjection {
        val totals = NutritionCalculations.computeDayTotals(entries)
        val xpWon = NutritionCalculations.isWithinDailyXpWindow(totals.cal, current.goals.calGoal)
        val updatedDaySummary =
            current.daySummary.copy(entries = entries, totals = totals, xpAwardedToday = xpWon)
        val updatedWeek =
            current.weekDays.map { day ->
                if (day.dayKey == current.selectedDayKey) {
                    val (pct, status) =
                        NutritionCalculations.classifyWeekDayRing(totals.cal, current.goals.calGoal)
                    day.copy(totalCal = totals.cal, ringPct = pct, ringStatus = status)
                } else {
                    day
                }
            }
        return current.copy(
            projectionSequence = nextSeq,
            daySummary = updatedDaySummary,
            weekDays = updatedWeek,
        )
    }
}
