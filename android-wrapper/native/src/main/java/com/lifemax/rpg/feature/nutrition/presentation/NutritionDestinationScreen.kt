package com.lifemax.rpg.feature.nutrition.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.LifeMaxNutritionTheme
import com.lifemax.rpg.core.designsystem.LocalLifeMaxNutritionColors
import com.lifemax.rpg.feature.nutrition.domain.contract.NutritionAction
import com.lifemax.rpg.feature.nutrition.domain.contract.NutritionRepository
import com.lifemax.rpg.feature.nutrition.domain.model.*
import com.lifemax.rpg.feature.nutrition.presentation.components.*
import com.lifemax.rpg.feature.nutrition.presentation.state.NutritionUiState
import com.lifemax.rpg.feature.nutrition.presentation.state.NutritionViewModel

/**
 * Top-level Jetpack Compose destination for the LifeMax RPG Nutrition / Meals & Recipes feature.
 *
 * Designed to mount directly inside `MainActivity`'s `NativeShellCoordinator` /
 * `LiveOverviewShell.kt` without replacing `MainActivity` or altering `com.lifemax.rpg`
 * (`CONTRACT.md` Sections A6-A8).
 */
@Composable
fun NutritionDestinationScreen(
    repository: NutritionRepository,
    initialTopTab: NutritionTopTab = NutritionTopTab.MEALS,
    onOpenWebFallback: (targetRoute: String) -> Unit = {},
    onNavigateBackToOverview: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewModel =
        remember(repository) {
            NutritionViewModel(
                repository = repository,
                onFallbackToWebRequested = onOpenWebFallback,
            )
        }
    val uiState by viewModel.uiState.collectAsState()
    val projection = uiState.projection

    LaunchedEffect(initialTopTab) {
        viewModel.dispatchAction(NutritionAction.SelectTopTab(initialTopTab))
    }

    LifeMaxNutritionTheme(
        themeMode = projection?.themeMode ?: NutritionThemeMode.DARK,
        accentHex = projection?.accentHex ?: "#00F5D4",
    ) {
        NutritionDestinationContent(
            uiState = uiState,
            viewModel = viewModel,
            onOpenWebFallback = onOpenWebFallback,
            modifier = modifier,
        )
    }
}

@Composable
fun NutritionDestinationContent(
    uiState: NutritionUiState,
    viewModel: NutritionViewModel,
    onOpenWebFallback: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalLifeMaxNutritionColors.current
    val projection = uiState.projection

    Box(modifier = modifier.fillMaxSize().background(colors.bgPrimary)) {
        if (uiState.isLoading && projection == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.accent)
            }
            return@Box
        }

        if (projection == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = uiState.errorMessage ?: "Nutrition projection unavailable.",
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier =
                        Modifier.clip(RoundedCornerShape(12.dp))
                            .background(colors.primaryCtaGradient)
                            .border(
                                1.dp,
                                colors.accent.copy(alpha = 0.45f),
                                RoundedCornerShape(12.dp),
                            )
                            .clickable { onOpenWebFallback("meals") }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "Open Legacy Web Nutrition View",
                        color = colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
            return@Box
        }

        // Scrollable primary viewport (.fv2-root: max-width 680dp, bottom padding for floating
        // dock)
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .padding(bottom = 112.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().widthIn(max = 680.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 1. Top Segmented Switcher (.foodseg > .libseg: Meals | Recipes)
                TopFoodSegmentedBar(
                    activeTab = projection.activeTopTab,
                    onSelectTab = { tab ->
                        viewModel.dispatchAction(NutritionAction.SelectTopTab(tab))
                    },
                )

                if (projection.activeTopTab == NutritionTopTab.RECIPES) {
                    RecipesView(
                        recipesProjection = projection.recipesState,
                        searchQuery = uiState.recipeSearchQuery,
                        selectedTag = uiState.selectedRecipeTag,
                        isNewRecipeCreatorOpen = uiState.isNewRecipeCreatorOpen,
                        onSearchQueryChange = viewModel::setRecipeSearchQuery,
                        onSelectTag = viewModel::setSelectedRecipeTag,
                        onToggleNewRecipeCreator = viewModel::toggleNewRecipeCreator,
                        onCreateCustomRecipe = { name, tags, cal, pro, carb, fat, ings, inst ->
                            viewModel.dispatchAction(
                                NutritionAction.CreateCustomRecipe(
                                    name,
                                    tags,
                                    cal,
                                    pro,
                                    carb,
                                    fat,
                                    ings,
                                    inst,
                                )
                            )
                        },
                        onLogRecipeToToday = { recipeId ->
                            viewModel.dispatchAction(NutritionAction.LogRecipeToToday(recipeId))
                        },
                        onDeleteCustomRecipe = { recipeId ->
                            viewModel.dispatchAction(NutritionAction.DeleteCustomRecipe(recipeId))
                        },
                    )
                } else {
                    // 2. Smart Diet 3-Pane Segmented Bar (.sd-panes: Plan | Log | Shopping)
                    MealsSubPaneSwitcher(
                        activePane = projection.activeMealsPane,
                        shoppingCount =
                            projection.smartDietShop.totalActiveItems -
                                projection.smartDietShop.checkedItemCount,
                        onSelectPane = { pane ->
                            viewModel.dispatchAction(NutritionAction.SelectMealsPane(pane))
                        },
                    )

                    when (projection.activeMealsPane) {
                        MealsSubPane.PLAN ->
                            SmartDietPlanPane(
                                plan = projection.smartDietPlan,
                                showUpNextIngredients = uiState.showUpNextIngredients,
                                expandedTimelineSlots = uiState.expandedTimelineSlots,
                                isSettingsExpanded = uiState.isSmartDietSettingsExpanded,
                                onEatNext = {
                                    viewModel.dispatchAction(
                                        NutritionAction.SmartDietEatNext(projection.selectedDayKey)
                                    )
                                },
                                onToggleUpNextIngredients = viewModel::toggleUpNextIngredients,
                                onOpenSwapSheet = viewModel::openSmartDietSwapSheet,
                                onSkipSlot = { slot ->
                                    viewModel.dispatchAction(
                                        NutritionAction.SmartDietSkipSlot(
                                            projection.selectedDayKey,
                                            slot,
                                        )
                                    )
                                },
                                onLogSlot = { slot ->
                                    viewModel.dispatchAction(
                                        NutritionAction.SmartDietLogSlot(
                                            projection.selectedDayKey,
                                            slot,
                                        )
                                    )
                                },
                                onToggleTimelineSlot = viewModel::toggleTimelineSlot,
                                onToggleSettings = viewModel::toggleSmartDietSettings,
                                onUpdatePrefs = { comp, fuel, veg, df ->
                                    viewModel.dispatchAction(
                                        NutritionAction.SmartDietUpdatePrefs(comp, fuel, veg, df)
                                    )
                                },
                                onLogRemainingDay = {
                                    viewModel.dispatchAction(
                                        NutritionAction.SmartDietLogRemainingDay(
                                            projection.selectedDayKey
                                        )
                                    )
                                },
                            )

                        MealsSubPane.LOG ->
                            MealsLogPane(
                                projection = projection,
                                isNutritionDetailsExpanded = uiState.isNutritionDetailsExpanded,
                                onShiftDay = { delta ->
                                    viewModel.dispatchAction(NutritionAction.ShiftDay(delta))
                                },
                                onSelectDay = { dayKey ->
                                    viewModel.dispatchAction(NutritionAction.SelectDay(dayKey))
                                },
                                onOpenGoalSheet = viewModel::openGoalSettingsSheet,
                                onQuickAddRecentFood = { food ->
                                    viewModel.openPortionSheet(food, MealSlotType.SNACKS)
                                },
                                onAdjustWaterMl = { delta, reset ->
                                    viewModel.dispatchAction(
                                        NutritionAction.AdjustWaterMl(
                                            projection.selectedDayKey,
                                            delta,
                                            reset,
                                        )
                                    )
                                },
                                onOpenSearchForSlot = viewModel::openSearchSheet,
                                onOpenEditEntry = viewModel::openEditItemSheet,
                                onToggleNutritionDetails = viewModel::toggleNutritionDetails,
                            )

                        MealsSubPane.SHOPPING ->
                            SmartDietShoppingPane(
                                shopping = projection.smartDietShop,
                                isPantryExpanded = uiState.isPantryHomeExpanded,
                                onToggleItem = { itemKey ->
                                    viewModel.dispatchAction(
                                        NutritionAction.SmartDietToggleShopItem(itemKey)
                                    )
                                },
                                onResetChecks = {
                                    viewModel.dispatchAction(
                                        NutritionAction.SmartDietResetShopChecks
                                    )
                                },
                                onTogglePantryExpanded = viewModel::togglePantryHome,
                            )
                    }
                }
            }
        }

        // Floating Bottom Action Dock (#fv2Dock: visible on Meals -> Log pane)
        if (
            projection.activeTopTab == NutritionTopTab.MEALS &&
                projection.activeMealsPane == MealsSubPane.LOG
        ) {
            FloatingFoodDock(
                onLogFood = { viewModel.openSearchSheet(MealSlotType.LUNCH) },
                onQuickCalories = { viewModel.openQuickAddSheet(MealSlotType.SNACKS) },
                modifier =
                    Modifier.align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }

        // Modal Bottom Sheet Host (.fv2-modal-bg > .fv2-sheet)
        NutritionBottomSheetHost(
            sheetState = uiState.activeSheet,
            projection = projection,
            onUpdateSearchSheet = viewModel::updateSearchSheet,
            onSelectFoodForPortion = viewModel::openPortionSheet,
            onOpenCreateFoodSheet = viewModel::openCreateFoodSheet,
            onDispatchAction = viewModel::dispatchAction,
            onCloseSheet = viewModel::closeSheet,
        )
    }
}

@Composable
private fun TopFoodSegmentedBar(
    activeTab: NutritionTopTab,
    onSelectTab: (NutritionTopTab) -> Unit,
) {
    val colors = LocalLifeMaxNutritionColors.current

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surfaceSunken)
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(14.dp))
                .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (tab in NutritionTopTab.entries) {
            val active = activeTab == tab
            Box(
                modifier =
                    Modifier.weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (active) colors.activePillGradient
                            else
                                Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                        )
                        .border(
                            1.dp,
                            if (active) colors.accent.copy(alpha = 0.35f) else Color.Transparent,
                            RoundedCornerShape(10.dp),
                        )
                        .clickable { onSelectTab(tab) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tab.label,
                    color = if (active) colors.textPrimary else colors.textMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

@Composable
private fun MealsSubPaneSwitcher(
    activePane: MealsSubPane,
    shoppingCount: Int,
    onSelectPane: (MealsSubPane) -> Unit,
) {
    val colors = LocalLifeMaxNutritionColors.current

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surfaceSunken)
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(14.dp))
                .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (pane in MealsSubPane.entries) {
            val active = activePane == pane
            val labelText =
                if (pane == MealsSubPane.SHOPPING && shoppingCount > 0) {
                    "Shopping ($shoppingCount)"
                } else {
                    pane.label
                }
            Box(
                modifier =
                    Modifier.weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(
                            if (active) colors.activePillGradient
                            else
                                Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                        )
                        .border(
                            1.dp,
                            if (active) colors.accent.copy(alpha = 0.32f) else Color.Transparent,
                            RoundedCornerShape(11.dp),
                        )
                        .clickable { onSelectPane(pane) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = labelText,
                    color = if (active) colors.textPrimary else colors.textMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

@Composable
private fun FloatingFoodDock(
    onLogFood: () -> Unit,
    onQuickCalories: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalLifeMaxNutritionColors.current
    val dockSurface = if (colors.isLight) Color(0xF2FFFFFF) else Color(0xEB0F1622)

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(dockSurface)
                .border(1.dp, colors.borderStrong, RoundedCornerShape(18.dp))
                .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier =
                Modifier.weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(colors.primaryCtaGradient)
                    .border(1.dp, colors.accent.copy(alpha = 0.42f), RoundedCornerShape(13.dp))
                    .clickable(onClick = onLogFood),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "+ Log food",
                color = colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
            )
        }

        Box(
            modifier =
                Modifier.height(44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(colors.surfaceSunken)
                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(13.dp))
                    .clickable(onClick = onQuickCalories)
                    .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "⚡ Quick kcal",
                color = colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}
