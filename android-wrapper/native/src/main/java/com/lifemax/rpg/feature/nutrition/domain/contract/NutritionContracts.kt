package com.lifemax.rpg.feature.nutrition.domain.contract

import com.lifemax.rpg.feature.nutrition.domain.model.*
import kotlinx.coroutines.flow.StateFlow

/**
 * Authoritative repository & action contracts for the LifeMax Native Nutrition tab. Complies with
 * CONTRACT.md Sections A10-A15 (Single Source of Truth, Session & Sequence Projection Stamping, and
 * Capability-Gated Mutation Dispatch).
 */
interface NutritionRepository {
    val projectionState: StateFlow<NutritionRepositoryState>

    suspend fun requestSync(selectedDayKey: String? = null)

    suspend fun dispatch(action: NutritionAction): NutritionMutationResult
}

sealed interface NutritionRepositoryState {
    data object Loading : NutritionRepositoryState

    data class Ready(val projection: NutritionProjection) : NutritionRepositoryState

    data class Error(
        val message: String,
        val canFallbackToWeb: Boolean = true,
        val lastKnownProjection: NutritionProjection? = null,
    ) : NutritionRepositoryState
}

sealed interface NutritionMutationResult {
    data class Confirmed(val projectionSequence: Long) : NutritionMutationResult

    data class Rejected(val reason: String) : NutritionMutationResult

    data class UnsupportedFallbackRequired(val targetWebRoute: String) : NutritionMutationResult
}

sealed interface NutritionAction {
    // Top-level & sub-pane navigation
    data class SelectTopTab(val tab: NutritionTopTab) : NutritionAction

    data class SelectMealsPane(val pane: MealsSubPane) : NutritionAction

    data class SelectDay(val dayKey: String) : NutritionAction

    data class ShiftDay(val deltaDays: Int) : NutritionAction

    // Food Logging & Editing (50-food.js)
    data class LogFoodPortion(
        val dayKey: String,
        val mealSlot: MealSlotType,
        val foodId: String,
        val grams: Double,
    ) : NutritionAction

    data class UpdateLoggedFoodItem(
        val dayKey: String,
        val entryIndex: Int,
        val newSlot: MealSlotType,
        val newGrams: Double,
    ) : NutritionAction

    data class RemoveLoggedFoodItem(val dayKey: String, val entryIndex: Int) : NutritionAction

    data class CreateCustomFood(
        val dayKey: String,
        val mealSlot: MealSlotType,
        val name: String,
        val brand: String,
        val servingG: Double,
        val cal: Int,
        val pro: Double,
        val carb: Double,
        val fat: Double,
        val fiber: Double,
        val sugar: Double,
        val sodiumMg: Int,
        val logImmediately: Boolean,
    ) : NutritionAction

    data class QuickAddCalories(
        val dayKey: String,
        val mealSlot: MealSlotType,
        val calories: Int,
        val label: String,
        val pro: Double,
        val carb: Double,
        val fat: Double,
    ) : NutritionAction

    // Water & Goals (30-food.js / 50-food.js)
    data class AdjustWaterMl(
        val dayKey: String,
        val deltaMl: Int,
        val resetToZero: Boolean = false,
    ) : NutritionAction

    data class UpdateNutritionGoals(
        val calGoal: Int,
        val proGoal: Int,
        val carbGoal: Int,
        val fatGoal: Int,
        val waterGoalMl: Int,
        val sdMode: SmartDietMode,
        val sdComplexity: SmartDietComplexity,
        val sdFuelLevel: SmartDietFuelLevel,
        val vegetarian: Boolean,
        val dairyFree: Boolean,
    ) : NutritionAction

    // Smart Diet Plan & Shopping (50-smartdiet.js)
    data class SmartDietEatNext(val dayKey: String) : NutritionAction

    data class SmartDietLogSlot(val dayKey: String, val slotId: MealSlotType) : NutritionAction

    data class SmartDietSkipSlot(val dayKey: String, val slotId: MealSlotType) : NutritionAction

    data class SmartDietSwapMeal(
        val dayKey: String,
        val slotId: MealSlotType,
        val newMealId: String,
    ) : NutritionAction

    data class SmartDietLogRemainingDay(val dayKey: String) : NutritionAction

    data class SmartDietUpdatePrefs(
        val complexity: SmartDietComplexity? = null,
        val fuelLevel: SmartDietFuelLevel? = null,
        val vegetarian: Boolean? = null,
        val dairyFree: Boolean? = null,
    ) : NutritionAction

    data class SmartDietToggleShopItem(val itemKey: String) : NutritionAction

    data object SmartDietResetShopChecks : NutritionAction

    // Recipes (30-food.js)
    data class LogRecipeToToday(val recipeId: String) : NutritionAction

    data class CreateCustomRecipe(
        val name: String,
        val tags: List<String>,
        val cal: Int,
        val pro: Int,
        val carb: Int,
        val fat: Int,
        val ingredients: List<String>,
        val instructions: String,
    ) : NutritionAction

    data class DeleteCustomRecipe(val recipeId: String) : NutritionAction
}

/**
 * Enforces CONTRACT.md Section A12: Rejects stale or out-of-order projections from the same WebView
 * session while allowing sequence reset when a new webSessionId boots.
 */
class ProjectionSequenceGate {
    var activeSessionId: String? = null
        private set

    var lastAcceptedSequence: Long = -1L
        private set

    @Synchronized
    fun shouldAccept(webSessionId: String, projectionSequence: Long): Boolean {
        if (webSessionId.isBlank()) return false
        if (activeSessionId != webSessionId) {
            activeSessionId = webSessionId
            lastAcceptedSequence = projectionSequence
            return true
        }
        if (projectionSequence <= lastAcceptedSequence) {
            return false
        }
        lastAcceptedSequence = projectionSequence
        return true
    }
}
