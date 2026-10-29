package com.lifemax.rpg.feature.nutrition.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.LocalLifeMaxNutritionColors
import com.lifemax.rpg.feature.nutrition.domain.model.*
import kotlin.math.max

/**
 * Pixel-accurate 1:1 Jetpack Compose reproduction of the Smart Diet v198 `Plan` pane (`sdPlanHtml`
 * in `50-smartdiet.js` lines 1018-1208 and `08-food.css` lines 489-1350).
 *
 * Includes:
 * 1. `.sd-day` Daily progress header (`Today's plan`, logged/total meals, flat progress bar, macro
 *    targets)
 * 2. `.sd-next` Flat v197 "Up next" section with 4-stat grid (`.sd-stats`), primary `Eat this — log
 *    it` CTA, and 3-button secondary row (`Ingredients` | `Swap` | `Skip`)
 * 3. `.sd-coach` Adaptive nutrition coach status banner
 * 4. `.sd-prep` Tomorrow prep alert banner
 * 5. `.sd-set` Collapsible Smart Diet inline preferences (`Standard Diet` vs `Simple Recipes`, 5
 *    fuel modes `Auto`/`Rest`/`Light`/`Moderate`/`Hard`, `Vegetarian`, `Dairy-free`)
 * 6. `.sd-timeline` Full day meal timeline disclosure cards + `Log the rest of the day` button
 */
@Composable
fun SmartDietPlanPane(
    plan: SmartDietDayProjection,
    showUpNextIngredients: Boolean,
    expandedTimelineSlots: Set<MealSlotType>,
    isSettingsExpanded: Boolean,
    onEatNext: () -> Unit,
    onToggleUpNextIngredients: () -> Unit,
    onOpenSwapSheet: (MealSlotType) -> Unit,
    onSkipSlot: (MealSlotType) -> Unit,
    onLogSlot: (MealSlotType) -> Unit,
    onToggleTimelineSlot: (MealSlotType) -> Unit,
    onToggleSettings: () -> Unit,
    onUpdatePrefs: (SmartDietComplexity?, SmartDietFuelLevel?, Boolean?, Boolean?) -> Unit,
    onLogRemainingDay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalLifeMaxNutritionColors.current
    val completedMeals = plan.plannedMeals.count { it.isLogged || it.isSkipped }
    val totalMeals = max(1, plan.plannedMeals.size)
    val loggedCal = plan.plannedMeals.filter { it.isLogged }.sumOf { it.cal }
    val loggedPro = plan.plannedMeals.filter { it.isLogged }.sumOf { it.pro }
    val fillFraction = (loggedCal.toFloat() / max(1, plan.targetCal).toFloat()).coerceIn(0f, 1f)

    val upNextMeal =
        plan.upNextSlot?.let { slot -> plan.plannedMeals.firstOrNull { it.slot == slot } }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 1. v198 Daily Progress Header (.sd-day)
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.cardGradient)
                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(18.dp))
                    .padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Today's plan",
                        color = colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Box(
                        modifier =
                            Modifier.clip(CircleShape)
                                .background(colors.surfaceSunken)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "$completedMeals/$totalMeals meals",
                            color = colors.textMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }

                Box(
                    modifier =
                        Modifier.clip(RoundedCornerShape(9.dp))
                            .background(colors.surfaceSunken)
                            .border(1.dp, colors.borderSubtle, RoundedCornerShape(9.dp))
                            .clickable(onClick = onToggleSettings)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (isSettingsExpanded) "Close prefs" else "Diet prefs ⚙",
                        color = colors.textMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }

            // Progress bar (.sd-day-bar)
            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(if (colors.isLight) Color(0x140F172A) else Color(0x14FFFFFF))
            ) {
                Box(
                    modifier =
                        Modifier.fillMaxWidth(fillFraction)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(colors.accent)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "$loggedCal / ${plan.targetCal} kcal planned",
                    color = colors.textSubtle,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${loggedPro}g / ${plan.targetPro}g protein",
                    color = colors.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }

        // 2. Collapsible Smart Diet Preferences (.sd-set)
        if (isSettingsExpanded) {
            SmartDietPreferencesCard(plan = plan, onUpdatePrefs = onUpdatePrefs)
        }

        // 3. v197 Flat "Up Next" Card (.sd-next)
        if (upNextMeal != null) {
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.cardGradient)
                        .border(1.dp, colors.accent.copy(alpha = 0.28f), RoundedCornerShape(20.dp))
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text =
                            "UP NEXT · ${upNextMeal.slot.label.uppercase()} · ${upNextMeal.prepMinutes} MIN",
                        color = colors.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.6.sp,
                    )
                    if (upNextMeal.prepNote != null) {
                        Text(
                            text = "Prep tip",
                            color = colors.accentGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }

                Text(
                    text = upNextMeal.title,
                    color = colors.textPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 23.sp,
                )

                // 4-column stat strip (.sd-stats)
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surfaceSunken)
                            .border(1.dp, colors.borderSubtle, RoundedCornerShape(12.dp))
                            .padding(vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    StatCell(value = "${upNextMeal.cal}", unit = "kcal")
                    StatCell(value = "${upNextMeal.pro}g", unit = "protein")
                    StatCell(value = "${upNextMeal.carb}g", unit = "carbs")
                    StatCell(value = "${upNextMeal.fat}g", unit = "fat")
                }

                // Primary CTA (.sd-go)
                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .height(46.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(colors.primaryCtaGradient)
                            .border(
                                1.dp,
                                colors.accent.copy(alpha = 0.45f),
                                RoundedCornerShape(13.dp),
                            )
                            .clickable(onClick = onEatNext),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Eat this — log it",
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                    )
                }

                // 3-button secondary action row (.sd-na: Ingredients | Swap | Skip)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SecondaryPlanButton(
                        label = if (showUpNextIngredients) "Hide recipe" else "Ingredients",
                        onClick = onToggleUpNextIngredients,
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryPlanButton(
                        label = "Swap",
                        onClick = { onOpenSwapSheet(upNextMeal.slot) },
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryPlanButton(
                        label = "Skip",
                        onClick = { onSkipSlot(upNextMeal.slot) },
                        modifier = Modifier.weight(1f),
                    )
                }

                if (showUpNextIngredients) {
                    MealIngredientsAndStepsBox(meal = upNextMeal)
                }
            }
        }

        // 4. Coach & Tomorrow Prep Banners (.sd-coach / .sd-prep)
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surfaceSunken)
                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(16.dp))
                    .padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = plan.coachHeadline,
                color = colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(text = plan.coachDetail, color = colors.textMuted, fontSize = 12.sp)
            if (plan.tomorrowPrepBanner != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "⏱ ${plan.tomorrowPrepBanner}",
                    color = colors.accentGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // 5. Full Day Meal Timeline (.sd-timeline)
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.cardGradient)
                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(20.dp))
                    .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "FULL DAY MEAL PLAN",
                    color = colors.textSubtle,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.6.sp,
                )
                Text(
                    text = plan.resolvedFuelReason,
                    color = colors.textSubtle,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            for (meal in plan.plannedMeals) {
                val isOpen = expandedTimelineSlots.contains(meal.slot)
                TimelineMealRow(
                    meal = meal,
                    isExpanded = isOpen,
                    onToggleExpand = { onToggleTimelineSlot(meal.slot) },
                    onLogSlot = { onLogSlot(meal.slot) },
                    onSwapSlot = { onOpenSwapSheet(meal.slot) },
                    onSkipSlot = { onSkipSlot(meal.slot) },
                )
            }

            if (plan.plannedMeals.any { !it.isLogged && !it.isSkipped }) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .height(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surfaceSunken)
                            .border(1.dp, colors.borderSubtle, RoundedCornerShape(12.dp))
                            .clickable(onClick = onLogRemainingDay),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Log the rest of the day",
                        color = colors.textMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCell(value: String, unit: String) {
    val colors = LocalLifeMaxNutritionColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = colors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
        )
        Text(text = unit, color = colors.textSubtle, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SecondaryPlanButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalLifeMaxNutritionColors.current
    Box(
        modifier =
            modifier
                .height(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(colors.surfaceSunken)
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(11.dp))
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = colors.textMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
private fun MealIngredientsAndStepsBox(meal: SmartDietPlannedMeal) {
    val colors = LocalLifeMaxNutritionColors.current
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(13.dp))
                .background(colors.surfaceSunken)
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(13.dp))
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "INGREDIENTS",
            color = colors.textSubtle,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.6.sp,
        )
        for (ing in meal.ingredients) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    ing.name,
                    color = colors.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    ing.displayAmount,
                    color = colors.textMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (meal.steps.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "METHOD",
                color = colors.textSubtle,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.6.sp,
            )
            meal.steps.forEachIndexed { idx, step ->
                Text(text = "${idx + 1}. $step", color = colors.textMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun TimelineMealRow(
    meal: SmartDietPlannedMeal,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onLogSlot: () -> Unit,
    onSwapSlot: () -> Unit,
    onSkipSlot: () -> Unit,
) {
    val colors = LocalLifeMaxNutritionColors.current
    val statusBadgeText =
        when {
            meal.isLogged -> "✓ Logged"
            meal.isSkipped -> "Skipped"
            else -> "${meal.cal} kcal"
        }
    val statusColor =
        when {
            meal.isLogged -> colors.statusOk
            meal.isSkipped -> colors.textSubtle
            else -> colors.textPrimary
        }

    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surfaceSunken)
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier =
                        Modifier.size(9.dp)
                            .clip(CircleShape)
                            .background(colors.slotColor(meal.slot))
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${meal.slot.label} · ${meal.prepMinutes}m",
                        color = colors.textSubtle,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = meal.title,
                        color = colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = statusBadgeText,
                color = statusColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }

        if (isExpanded) {
            Column(
                modifier =
                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MealIngredientsAndStepsBox(meal = meal)
                if (!meal.isLogged) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SecondaryPlanButton(
                            label = "Log ${meal.slot.label}",
                            onClick = onLogSlot,
                            modifier = Modifier.weight(1f),
                        )
                        SecondaryPlanButton(
                            label = "Swap",
                            onClick = onSwapSlot,
                            modifier = Modifier.weight(1f),
                        )
                        SecondaryPlanButton(
                            label = "Skip",
                            onClick = onSkipSlot,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SmartDietPreferencesCard(
    plan: SmartDietDayProjection,
    onUpdatePrefs: (SmartDietComplexity?, SmartDietFuelLevel?, Boolean?, Boolean?) -> Unit,
) {
    val colors = LocalLifeMaxNutritionColors.current

    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.cardGradient)
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(18.dp))
                .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "MEAL STYLE",
            color = colors.textSubtle,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (comp in SmartDietComplexity.entries) {
                val active = plan.complexity == comp
                Column(
                    modifier =
                        Modifier.weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (active) colors.accent.copy(alpha = 0.16f)
                                else colors.surfaceSunken
                            )
                            .border(
                                1.dp,
                                if (active) colors.accent.copy(alpha = 0.45f)
                                else colors.borderSubtle,
                                RoundedCornerShape(12.dp),
                            )
                            .clickable { onUpdatePrefs(comp, null, null, null) }
                            .padding(10.dp)
                ) {
                    Text(
                        comp.label,
                        color = colors.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        comp.subLabel,
                        color = colors.textSubtle,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        Text(
            text = "TRAINING FUEL LEVEL",
            color = colors.textSubtle,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (fuel in SmartDietFuelLevel.entries) {
                val active = plan.fuelLevel == fuel
                Box(
                    modifier =
                        Modifier.weight(1f)
                            .height(32.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(
                                if (active) colors.accent.copy(alpha = 0.16f)
                                else colors.surfaceSunken
                            )
                            .border(
                                1.dp,
                                if (active) colors.accent.copy(alpha = 0.45f)
                                else colors.borderSubtle,
                                RoundedCornerShape(9.dp),
                            )
                            .clickable { onUpdatePrefs(null, fuel, null, null) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = fuel.label,
                        color = if (active) colors.accent else colors.textMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DietPrefToggleChip(
                label = "Vegetarian",
                checked = plan.vegetarian,
                onToggle = { onUpdatePrefs(null, null, !plan.vegetarian, null) },
                modifier = Modifier.weight(1f),
            )
            DietPrefToggleChip(
                label = "Dairy-free",
                checked = plan.dairyFree,
                onToggle = { onUpdatePrefs(null, null, null, !plan.dairyFree) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DietPrefToggleChip(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalLifeMaxNutritionColors.current
    Box(
        modifier =
            modifier
                .height(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (checked) colors.accent.copy(alpha = 0.16f) else colors.surfaceSunken
                )
                .border(
                    1.dp,
                    if (checked) colors.accent.copy(alpha = 0.45f) else colors.borderSubtle,
                    RoundedCornerShape(10.dp),
                )
                .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (checked) "✓ $label" else label,
            color = if (checked) colors.accent else colors.textMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}
