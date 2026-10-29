package com.lifemax.rpg.feature.nutrition.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.LocalLifeMaxNutritionColors
import com.lifemax.rpg.feature.nutrition.domain.contract.NutritionAction
import com.lifemax.rpg.feature.nutrition.domain.logic.NutritionCalculations
import com.lifemax.rpg.feature.nutrition.domain.model.*
import com.lifemax.rpg.feature.nutrition.presentation.state.NutritionSheetState
import com.lifemax.rpg.feature.nutrition.presentation.state.SearchFilterTab
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pixel-accurate 1:1 Jetpack Compose modal bottom sheets reproducing all 7 sheets from:
 * - `50-food.js` (`fv2RenderSheet`: `search`, `portion`, `edit`, `create`, `quick`, `goal`)
 * - `50-smartdiet.js` (`sdOpenSwap`: `SmartDietSwap`)
 */
@Composable
fun NutritionBottomSheetHost(
    sheetState: NutritionSheetState,
    projection: NutritionProjection,
    onUpdateSearchSheet: (String?, SearchFilterTab?, MealSlotType?) -> Unit,
    onSelectFoodForPortion: (FoodCatalogItem, MealSlotType) -> Unit,
    onOpenCreateFoodSheet: (MealSlotType) -> Unit,
    onDispatchAction: (NutritionAction, Boolean) -> Unit,
    onCloseSheet: () -> Unit,
) {
    if (sheetState is NutritionSheetState.Hidden) return
    val colors = LocalLifeMaxNutritionColors.current

    // Full-screen translucent backdrop (.fv2-modal-bg) with bottom-aligned sheet (.fv2-sheet)
    Box(
        modifier =
            Modifier.fillMaxSize().background(Color(0x9E04070E)).clickable(onClick = onCloseSheet),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .widthIn(max = 680.dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(colors.cardGradient)
                    .border(
                        1.dp,
                        colors.borderStrong,
                        RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    )
                    .clickable(enabled = false) {}
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Grab handle (.fv2-grab: 38x4dp)
            Box(
                modifier =
                    Modifier.align(Alignment.CenterHorizontally)
                        .size(width = 38.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(colors.borderStrong)
            )

            when (sheetState) {
                is NutritionSheetState.SearchFood ->
                    SearchFoodSheetContent(
                        state = sheetState,
                        projection = projection,
                        onUpdateSearch = onUpdateSearchSheet,
                        onSelectFood = { food ->
                            onSelectFoodForPortion(food, sheetState.targetSlot)
                        },
                        onOpenCreateFood = { onOpenCreateFoodSheet(sheetState.targetSlot) },
                        onClose = onCloseSheet,
                    )

                is NutritionSheetState.PortionPicker ->
                    PortionPickerSheetContent(
                        state = sheetState,
                        selectedDayKey = projection.selectedDayKey,
                        onConfirmLog = { slot, grams ->
                            onDispatchAction(
                                NutritionAction.LogFoodPortion(
                                    dayKey = projection.selectedDayKey,
                                    mealSlot = slot,
                                    foodId = sheetState.food.id,
                                    grams = grams,
                                ),
                                true,
                            )
                        },
                        onClose = onCloseSheet,
                    )

                is NutritionSheetState.EditLoggedItem ->
                    EditLoggedItemSheetContent(
                        state = sheetState,
                        selectedDayKey = projection.selectedDayKey,
                        onSaveEdit = { newSlot, newGrams ->
                            onDispatchAction(
                                NutritionAction.UpdateLoggedFoodItem(
                                    dayKey = projection.selectedDayKey,
                                    entryIndex = sheetState.entry.entryIndex,
                                    newSlot = newSlot,
                                    newGrams = newGrams,
                                ),
                                true,
                            )
                        },
                        onRemoveEntry = {
                            onDispatchAction(
                                NutritionAction.RemoveLoggedFoodItem(
                                    dayKey = projection.selectedDayKey,
                                    entryIndex = sheetState.entry.entryIndex,
                                ),
                                true,
                            )
                        },
                        onClose = onCloseSheet,
                    )

                is NutritionSheetState.CreateCustomFood ->
                    CreateCustomFoodSheetContent(
                        targetSlot = sheetState.targetSlot,
                        selectedDayKey = projection.selectedDayKey,
                        onCreateFood = { action -> onDispatchAction(action, true) },
                        onClose = onCloseSheet,
                    )

                is NutritionSheetState.QuickAddCalories ->
                    QuickAddCaloriesSheetContent(
                        targetSlot = sheetState.targetSlot,
                        selectedDayKey = projection.selectedDayKey,
                        onQuickAdd = { action -> onDispatchAction(action, true) },
                        onClose = onCloseSheet,
                    )

                is NutritionSheetState.GoalSettings ->
                    GoalSettingsSheetContent(
                        projection = projection,
                        onSaveGoals = { action -> onDispatchAction(action, true) },
                        onClose = onCloseSheet,
                    )

                is NutritionSheetState.SmartDietSwap ->
                    SmartDietSwapSheetContent(
                        state = sheetState,
                        selectedDayKey = projection.selectedDayKey,
                        onConfirmSwap = { altMealId ->
                            onDispatchAction(
                                NutritionAction.SmartDietSwapMeal(
                                    dayKey = projection.selectedDayKey,
                                    slotId = sheetState.slot,
                                    newMealId = altMealId,
                                ),
                                true,
                            )
                        },
                        onClose = onCloseSheet,
                    )

                NutritionSheetState.Hidden -> Unit
            }
        }
    }
}

@Composable
private fun SheetHeaderRow(title: String, onClose: () -> Unit) {
    val colors = LocalLifeMaxNutritionColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Black)
        Box(
            modifier =
                Modifier.size(30.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceSunken)
                    .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Text("✕", color = colors.textMuted, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun SearchFoodSheetContent(
    state: NutritionSheetState.SearchFood,
    projection: NutritionProjection,
    onUpdateSearch: (String?, SearchFilterTab?, MealSlotType?) -> Unit,
    onSelectFood: (FoodCatalogItem) -> Unit,
    onOpenCreateFood: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalLifeMaxNutritionColors.current
    val recentIds =
        remember(projection.recentFoods) { projection.recentFoods.map { it.id }.toSet() }

    val results =
        remember(projection.foodCatalog, state.query, state.activeFilterTab, recentIds) {
            val base =
                when (state.activeFilterTab) {
                    SearchFilterTab.ALL -> projection.foodCatalog
                    SearchFilterTab.RECENT -> projection.recentFoods
                    SearchFilterTab.CUSTOM -> projection.foodCatalog.filter { it.isCustom }
                }
            base
                .map { food ->
                    food to NutritionCalculations.scoreFoodSearch(food, state.query, recentIds)
                }
                .filter { it.second >= 0 }
                .sortedByDescending { it.second }
                .map { it.first }
        }

    SheetHeaderRow(title = "Add to ${state.targetSlot.label}", onClose = onClose)

    // Meal slot selector pills
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (slot in MealSlotType.entries) {
            val active = state.targetSlot == slot
            Box(
                modifier =
                    Modifier.weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (active) colors.accent.copy(alpha = 0.16f) else colors.surfaceSunken
                        )
                        .border(
                            1.dp,
                            if (active) colors.accent.copy(alpha = 0.45f) else colors.borderSubtle,
                            RoundedCornerShape(10.dp),
                        )
                        .clickable { onUpdateSearch(null, null, slot) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    slot.label,
                    color = if (active) colors.accent else colors.textMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }

    OutlinedTextField(
        value = state.query,
        onValueChange = { onUpdateSearch(it, null, null) },
        placeholder = { Text("Search foods (e.g. chicken, oats, yogurt)...", fontSize = 12.sp) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    // Filter tabs (All | Recent | My foods) + Create Custom Food button
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (tab in SearchFilterTab.entries) {
                val active = state.activeFilterTab == tab
                Box(
                    modifier =
                        Modifier.clip(CircleShape)
                            .background(
                                if (active) colors.accent.copy(alpha = 0.16f)
                                else colors.surfaceSunken
                            )
                            .clickable { onUpdateSearch(null, tab, null) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        tab.label,
                        color = if (active) colors.accent else colors.textSubtle,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }

        Text(
            text = "+ Create food",
            color = colors.accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.clickable(onClick = onOpenCreateFood),
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (food in results.take(12)) {
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceSunken)
                        .clickable { onSelectFood(food) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        food.name,
                        color = colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${food.brand} · ${food.servingLabel} · P ${food.pro}g · C ${food.carb}g · F ${food.fat}g",
                        color = colors.textSubtle,
                        fontSize = 11.sp,
                    )
                }
                Text(
                    "${food.cal} kcal",
                    color = colors.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

@Composable
private fun PortionPickerSheetContent(
    state: NutritionSheetState.PortionPicker,
    selectedDayKey: String,
    onConfirmLog: (MealSlotType, Double) -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalLifeMaxNutritionColors.current
    var selectedSlot by remember(state.targetSlot) { mutableStateOf(state.targetSlot) }
    var gramsText by remember(state.food) { mutableStateOf("${state.food.servingG.roundToInt()}") }

    val gramsValue = (gramsText.toDoubleOrNull() ?: state.food.servingG).coerceAtLeast(1.0)
    val scaled =
        remember(state.food, gramsValue, selectedSlot) {
            NutritionCalculations.scaleFoodForGrams(state.food, gramsValue, selectedSlot)
        }

    SheetHeaderRow(title = state.food.name, onClose = onClose)
    Text(
        text = "${state.food.brand} · Base serving ${state.food.servingLabel}",
        color = colors.textSubtle,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
    )

    // Live macro preview strip
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceSunken)
                .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${scaled.cal}",
                color = colors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
            )
            Text("kcal", color = colors.textSubtle, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${scaled.pro}g",
                color = colors.macroProtein,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                "Protein",
                color = colors.textSubtle,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${scaled.carb}g",
                color = colors.macroCarbs,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
            )
            Text("Carbs", color = colors.textSubtle, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${scaled.fat}g",
                color = colors.macroFat,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
            )
            Text("Fat", color = colors.textSubtle, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }

    // Portion preset buttons (0.5x, 1x, 1.5x, 2x, 100g)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val presets =
            listOf(
                "0.5×" to (state.food.servingG * 0.5).roundToInt(),
                "1×" to state.food.servingG.roundToInt(),
                "1.5×" to (state.food.servingG * 1.5).roundToInt(),
                "2×" to (state.food.servingG * 2.0).roundToInt(),
                "100g" to 100,
            )
        for ((label, presetG) in presets) {
            Box(
                modifier =
                    Modifier.weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surfaceSunken)
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(10.dp))
                        .clickable { gramsText = "$presetG" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = colors.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }

    OutlinedTextField(
        value = gramsText,
        onValueChange = { gramsText = it },
        label = { Text("Portion weight (grams)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    Box(
        modifier =
            Modifier.fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(colors.primaryCtaGradient)
                .border(1.dp, colors.accent.copy(alpha = 0.45f), RoundedCornerShape(13.dp))
                .clickable { onConfirmLog(selectedSlot, gramsValue) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Add to ${selectedSlot.label} (${scaled.cal} kcal)",
            color = colors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun EditLoggedItemSheetContent(
    state: NutritionSheetState.EditLoggedItem,
    selectedDayKey: String,
    onSaveEdit: (MealSlotType, Double) -> Unit,
    onRemoveEntry: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalLifeMaxNutritionColors.current
    var slot by remember(state.entry) { mutableStateOf(state.entry.mealSlot) }
    var gramsText by remember(state.entry) { mutableStateOf("${state.entry.grams.roundToInt()}") }

    SheetHeaderRow(title = "Edit ${state.entry.name}", onClose = onClose)

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (s in MealSlotType.entries) {
            val active = slot == s
            Box(
                modifier =
                    Modifier.weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (active) colors.accent.copy(alpha = 0.16f) else colors.surfaceSunken
                        )
                        .clickable { slot = s },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    s.label,
                    color = if (active) colors.accent else colors.textMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }

    OutlinedTextField(
        value = gramsText,
        onValueChange = { gramsText = it },
        label = { Text("Grams") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier =
                Modifier.weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.statusBad.copy(alpha = 0.15f))
                    .border(1.dp, colors.statusBad.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .clickable(onClick = onRemoveEntry),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Remove",
                color = colors.statusBad,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }

        Box(
            modifier =
                Modifier.weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.primaryCtaGradient)
                    .border(1.dp, colors.accent.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .clickable {
                        val g = max(1.0, gramsText.toDoubleOrNull() ?: state.entry.grams)
                        onSaveEdit(slot, g)
                    },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Save changes",
                color = colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun CreateCustomFoodSheetContent(
    targetSlot: MealSlotType,
    selectedDayKey: String,
    onCreateFood: (NutritionAction.CreateCustomFood) -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalLifeMaxNutritionColors.current
    var name by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("My food") }
    var servingG by remember { mutableStateOf("100") }
    var cal by remember { mutableStateOf("250") }
    var pro by remember { mutableStateOf("25") }
    var carb by remember { mutableStateOf("20") }
    var fat by remember { mutableStateOf("8") }

    SheetHeaderRow(title = "Create custom food", onClose = onClose)

    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Food name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = brand,
            onValueChange = { brand = it },
            label = { Text("Brand / note") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = servingG,
            onValueChange = { servingG = it },
            label = { Text("Serving (g)") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = cal,
            onValueChange = { cal = it },
            label = { Text("Calories") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = pro,
            onValueChange = { pro = it },
            label = { Text("Protein (g)") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = carb,
            onValueChange = { carb = it },
            label = { Text("Carbs (g)") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = fat,
            onValueChange = { fat = it },
            label = { Text("Fat (g)") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }

    Box(
        modifier =
            Modifier.fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(colors.primaryCtaGradient)
                .border(1.dp, colors.accent.copy(alpha = 0.45f), RoundedCornerShape(13.dp))
                .clickable {
                    if (name.isNotBlank()) {
                        onCreateFood(
                            NutritionAction.CreateCustomFood(
                                dayKey = selectedDayKey,
                                mealSlot = targetSlot,
                                name = name.trim(),
                                brand = brand.trim(),
                                servingG = servingG.toDoubleOrNull() ?: 100.0,
                                cal = cal.toIntOrNull() ?: 0,
                                pro = pro.toDoubleOrNull() ?: 0.0,
                                carb = carb.toDoubleOrNull() ?: 0.0,
                                fat = fat.toDoubleOrNull() ?: 0.0,
                                fiber = 0.0,
                                sugar = 0.0,
                                sodiumMg = 0,
                                logImmediately = true,
                            )
                        )
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Save & log to ${targetSlot.label}",
            color = colors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun QuickAddCaloriesSheetContent(
    targetSlot: MealSlotType,
    selectedDayKey: String,
    onQuickAdd: (NutritionAction.QuickAddCalories) -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalLifeMaxNutritionColors.current
    var slot by remember(targetSlot) { mutableStateOf(targetSlot) }
    var cal by remember { mutableStateOf("350") }
    var label by remember { mutableStateOf("Quick add") }
    var pro by remember { mutableStateOf("25") }
    var carb by remember { mutableStateOf("30") }
    var fat by remember { mutableStateOf("12") }

    SheetHeaderRow(title = "Quick add calories", onClose = onClose)

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (s in MealSlotType.entries) {
            val active = slot == s
            Box(
                modifier =
                    Modifier.weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (active) colors.accent.copy(alpha = 0.16f) else colors.surfaceSunken
                        )
                        .clickable { slot = s },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    s.label,
                    color = if (active) colors.accent else colors.textMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = cal,
            onValueChange = { cal = it },
            label = { Text("Calories (kcal)") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            label = { Text("Label") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = pro,
            onValueChange = { pro = it },
            label = { Text("Protein (g)") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = carb,
            onValueChange = { carb = it },
            label = { Text("Carbs (g)") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = fat,
            onValueChange = { fat = it },
            label = { Text("Fat (g)") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }

    Box(
        modifier =
            Modifier.fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(colors.primaryCtaGradient)
                .border(1.dp, colors.accent.copy(alpha = 0.45f), RoundedCornerShape(13.dp))
                .clickable {
                    onQuickAdd(
                        NutritionAction.QuickAddCalories(
                            dayKey = selectedDayKey,
                            mealSlot = slot,
                            calories = cal.toIntOrNull() ?: 0,
                            label = label.ifBlank { "Quick add" },
                            pro = pro.toDoubleOrNull() ?: 0.0,
                            carb = carb.toDoubleOrNull() ?: 0.0,
                            fat = fat.toDoubleOrNull() ?: 0.0,
                        )
                    )
                },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Add to ${slot.label}",
            color = colors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun GoalSettingsSheetContent(
    projection: NutritionProjection,
    onSaveGoals: (NutritionAction.UpdateNutritionGoals) -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalLifeMaxNutritionColors.current
    val g = projection.goals
    val sd = projection.smartDietPlan

    var calGoal by remember(g) { mutableStateOf("${g.calGoal}") }
    var proGoal by remember(g) { mutableStateOf("${g.proGoal}") }
    var carbGoal by remember(g) { mutableStateOf("${g.carbGoal}") }
    var fatGoal by remember(g) { mutableStateOf("${g.fatGoal}") }
    var waterGoal by remember(g) { mutableStateOf("${g.waterGoalMl}") }

    SheetHeaderRow(title = "Nutrition Goals & Targets", onClose = onClose)

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = calGoal,
            onValueChange = { calGoal = it },
            label = { Text("Daily Calories") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = waterGoal,
            onValueChange = { waterGoal = it },
            label = { Text("Water Goal (ml)") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = proGoal,
            onValueChange = { proGoal = it },
            label = { Text("Protein (g)") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = carbGoal,
            onValueChange = { carbGoal = it },
            label = { Text("Carbs (g)") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = fatGoal,
            onValueChange = { fatGoal = it },
            label = { Text("Fat (g)") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }

    Box(
        modifier =
            Modifier.fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(colors.primaryCtaGradient)
                .border(1.dp, colors.accent.copy(alpha = 0.45f), RoundedCornerShape(13.dp))
                .clickable {
                    onSaveGoals(
                        NutritionAction.UpdateNutritionGoals(
                            calGoal = calGoal.toIntOrNull() ?: g.calGoal,
                            proGoal = proGoal.toIntOrNull() ?: g.proGoal,
                            carbGoal = carbGoal.toIntOrNull() ?: g.carbGoal,
                            fatGoal = fatGoal.toIntOrNull() ?: g.fatGoal,
                            waterGoalMl = waterGoal.toIntOrNull() ?: g.waterGoalMl,
                            sdMode = sd.mode,
                            sdComplexity = sd.complexity,
                            sdFuelLevel = sd.fuelLevel,
                            vegetarian = sd.vegetarian,
                            dairyFree = sd.dairyFree,
                        )
                    )
                },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Save Goals",
            color = colors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun SmartDietSwapSheetContent(
    state: NutritionSheetState.SmartDietSwap,
    selectedDayKey: String,
    onConfirmSwap: (String) -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalLifeMaxNutritionColors.current

    SheetHeaderRow(title = "Swap ${state.slot.label}", onClose = onClose)
    Text(
        text = "Current: ${state.currentMeal.title} (${state.currentMeal.cal} kcal)",
        color = colors.textSubtle,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
    )

    for (alt in state.alternatives) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surfaceSunken)
                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(14.dp))
                    .clickable { onConfirmSwap(alt.mealId) }
                    .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    alt.title,
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    "${alt.prepMinutes}m · ${alt.cal} kcal · P ${alt.pro}g · C ${alt.carb}g · F ${alt.fat}g",
                    color = colors.textSubtle,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Box(
                modifier =
                    Modifier.clip(RoundedCornerShape(9.dp))
                        .background(colors.accent.copy(alpha = 0.16f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    "Swap",
                    color = colors.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}
