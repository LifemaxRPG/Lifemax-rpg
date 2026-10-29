package com.lifemax.rpg.feature.nutrition.presentation.state

import com.lifemax.rpg.feature.nutrition.domain.model.*

/**
 * Modal bottom sheet state matching `fv2State.sheet` in `50-food.js` and `sdOpenSwap` in
 * `50-smartdiet.js`.
 */
sealed interface NutritionSheetState {
    data object Hidden : NutritionSheetState

    data class SearchFood(
        val targetSlot: MealSlotType,
        val query: String = "",
        val activeFilterTab: SearchFilterTab = SearchFilterTab.ALL,
    ) : NutritionSheetState

    data class PortionPicker(
        val targetSlot: MealSlotType,
        val food: FoodCatalogItem,
        val amountValue: Double = food.servingG,
        val unitMode: PortionUnitMode = PortionUnitMode.GRAMS,
    ) : NutritionSheetState

    data class EditLoggedItem(
        val entry: LoggedFoodEntry,
        val selectedSlot: MealSlotType = entry.mealSlot,
        val grams: Double = entry.grams,
    ) : NutritionSheetState

    data class CreateCustomFood(val targetSlot: MealSlotType) : NutritionSheetState

    data class QuickAddCalories(val targetSlot: MealSlotType) : NutritionSheetState

    data object GoalSettings : NutritionSheetState

    data class SmartDietSwap(
        val slot: MealSlotType,
        val currentMeal: SmartDietPlannedMeal,
        val alternatives: List<SmartDietSwapAlternative>,
    ) : NutritionSheetState
}

enum class SearchFilterTab(val id: String, val label: String) {
    ALL("all", "All"),
    RECENT("recent", "Recent"),
    CUSTOM("custom", "My foods"),
}

enum class PortionUnitMode(val id: String, val label: String) {
    GRAMS("g", "g"),
    SERVING("serv", "serving"),
}

/** Complete UI state consumed by `NutritionDestinationScreen`. */
data class NutritionUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val projection: NutritionProjection? = null,
    val activeSheet: NutritionSheetState = NutritionSheetState.Hidden,
    // Log pane collapsible state
    val isNutritionDetailsExpanded: Boolean = false,
    // Smart Diet Plan pane disclosure state
    val showUpNextIngredients: Boolean = false,
    val expandedTimelineSlots: Set<MealSlotType> = emptySet(),
    val isSmartDietSettingsExpanded: Boolean = false,
    // Shopping pane disclosure state
    val isPantryHomeExpanded: Boolean = false,
    // Recipes view state
    val recipeSearchQuery: String = "",
    val selectedRecipeTag: String = "All",
    val isNewRecipeCreatorOpen: Boolean = false,
    val statusBannerMessage: String? = null,
)
