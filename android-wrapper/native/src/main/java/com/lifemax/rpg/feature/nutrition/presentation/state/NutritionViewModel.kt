package com.lifemax.rpg.feature.nutrition.presentation.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifemax.rpg.feature.nutrition.domain.contract.*
import com.lifemax.rpg.feature.nutrition.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Lifecycle-aware state holder for the Native Nutrition & Recipes destination. Collects
 * authoritative projections from `NutritionRepository` and dispatches typed `NutritionAction`
 * commands without fabricating unverified local state.
 */
class NutritionViewModel(
    private val repository: NutritionRepository,
    private val onFallbackToWebRequested: ((route: String) -> Unit)? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NutritionUiState())
    val uiState: StateFlow<NutritionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.projectionState.collect { repoState ->
                when (repoState) {
                    is NutritionRepositoryState.Loading -> {
                        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                    }
                    is NutritionRepositoryState.Ready -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = null,
                                projection = repoState.projection,
                            )
                        }
                    }
                    is NutritionRepositoryState.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = repoState.message,
                                projection = repoState.lastKnownProjection ?: it.projection,
                            )
                        }
                    }
                }
            }
        }
    }

    fun dispatchAction(action: NutritionAction, closeSheetOnConfirm: Boolean = false) {
        viewModelScope.launch {
            when (val result = repository.dispatch(action)) {
                is NutritionMutationResult.Confirmed -> {
                    if (closeSheetOnConfirm) {
                        _uiState.update { it.copy(activeSheet = NutritionSheetState.Hidden) }
                    }
                }
                is NutritionMutationResult.Rejected -> {
                    _uiState.update { it.copy(statusBannerMessage = result.reason) }
                }
                is NutritionMutationResult.UnsupportedFallbackRequired -> {
                    onFallbackToWebRequested?.invoke(result.targetWebRoute)
                }
            }
        }
    }

    // Sheet controllers
    fun openSearchSheet(slot: MealSlotType) {
        _uiState.update { it.copy(activeSheet = NutritionSheetState.SearchFood(targetSlot = slot)) }
    }

    fun updateSearchSheet(
        query: String? = null,
        filterTab: SearchFilterTab? = null,
        slot: MealSlotType? = null,
    ) {
        _uiState.update { state ->
            val current =
                state.activeSheet as? NutritionSheetState.SearchFood ?: return@update state
            state.copy(
                activeSheet =
                    current.copy(
                        query = query ?: current.query,
                        activeFilterTab = filterTab ?: current.activeFilterTab,
                        targetSlot = slot ?: current.targetSlot,
                    )
            )
        }
    }

    fun openPortionSheet(food: FoodCatalogItem, slot: MealSlotType) {
        _uiState.update {
            it.copy(
                activeSheet =
                    NutritionSheetState.PortionPicker(
                        targetSlot = slot,
                        food = food,
                        amountValue = food.servingG,
                        unitMode = PortionUnitMode.GRAMS,
                    )
            )
        }
    }

    fun openEditItemSheet(entry: LoggedFoodEntry) {
        _uiState.update { it.copy(activeSheet = NutritionSheetState.EditLoggedItem(entry = entry)) }
    }

    fun openCreateFoodSheet(slot: MealSlotType) {
        _uiState.update {
            it.copy(activeSheet = NutritionSheetState.CreateCustomFood(targetSlot = slot))
        }
    }

    fun openQuickAddSheet(slot: MealSlotType) {
        _uiState.update {
            it.copy(activeSheet = NutritionSheetState.QuickAddCalories(targetSlot = slot))
        }
    }

    fun openGoalSettingsSheet() {
        _uiState.update { it.copy(activeSheet = NutritionSheetState.GoalSettings) }
    }

    fun openSmartDietSwapSheet(slot: MealSlotType) {
        val plan = _uiState.value.projection?.smartDietPlan ?: return
        val currentMeal = plan.plannedMeals.firstOrNull { it.slot == slot } ?: return
        val alternatives = plan.swapCandidatesBySlot[slot].orEmpty()
        _uiState.update {
            it.copy(
                activeSheet =
                    NutritionSheetState.SmartDietSwap(
                        slot = slot,
                        currentMeal = currentMeal,
                        alternatives = alternatives,
                    )
            )
        }
    }

    fun closeSheet() {
        _uiState.update { it.copy(activeSheet = NutritionSheetState.Hidden) }
    }

    // Disclosure toggles
    fun toggleNutritionDetails() {
        _uiState.update { it.copy(isNutritionDetailsExpanded = !it.isNutritionDetailsExpanded) }
    }

    fun toggleUpNextIngredients() {
        _uiState.update { it.copy(showUpNextIngredients = !it.showUpNextIngredients) }
    }

    fun toggleTimelineSlot(slot: MealSlotType) {
        _uiState.update { state ->
            val next =
                if (state.expandedTimelineSlots.contains(slot)) {
                    state.expandedTimelineSlots - slot
                } else {
                    state.expandedTimelineSlots + slot
                }
            state.copy(expandedTimelineSlots = next)
        }
    }

    fun toggleSmartDietSettings() {
        _uiState.update { it.copy(isSmartDietSettingsExpanded = !it.isSmartDietSettingsExpanded) }
    }

    fun togglePantryHome() {
        _uiState.update { it.copy(isPantryHomeExpanded = !it.isPantryHomeExpanded) }
    }

    // Recipes filters
    fun setRecipeSearchQuery(query: String) {
        _uiState.update { it.copy(recipeSearchQuery = query) }
    }

    fun setSelectedRecipeTag(tag: String) {
        _uiState.update { it.copy(selectedRecipeTag = tag) }
    }

    fun toggleNewRecipeCreator() {
        _uiState.update { it.copy(isNewRecipeCreatorOpen = !it.isNewRecipeCreatorOpen) }
    }

    fun clearStatusBanner() {
        _uiState.update { it.copy(statusBannerMessage = null) }
    }
}
