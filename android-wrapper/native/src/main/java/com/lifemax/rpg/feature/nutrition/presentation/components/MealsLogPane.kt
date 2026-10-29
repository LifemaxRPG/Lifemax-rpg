package com.lifemax.rpg.feature.nutrition.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.LocalLifeMaxNutritionColors
import com.lifemax.rpg.feature.nutrition.domain.model.*
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pixel-accurate 1:1 Jetpack Compose reproduction of the Food Tab v2 `Log` pane (`fv2Render` in
 * `50-food.js` lines 564-734 and `08-food.css` lines 18-329).
 *
 * Includes:
 * 1. `.fv2-head` Day navigation header (`‹`, Today / Date label, `›`, `Goals` button)
 * 2. `.fv2-week` 7-day Monday–Sunday adherence strip with 28x28 custom Canvas rings
 * 3. `.fv2-hero` 3-ring concentric macro dial card (`MacroDialHeroCard`)
 * 4. `.fv2-quick` horizontal quick-add recent food chips
 * 5. `.fv2-water` water tracker card (`+250`, `+500` ml)
 * 6. `.fv2-meals` 4 meal slot cards (`Breakfast`, `Lunch`, `Dinner`, `Snacks`)
 * 7. `.fv2-det` collapsible Nutrition details (Fiber, Sugar, Sodium + `.fv2-wk` 7-day bar chart)
 * 8. `#dietPlanWrap` RPG Class Fuel Plan card (`renderDietPlan()` in `30-food.js`)
 */
@Composable
fun MealsLogPane(
    projection: NutritionProjection,
    isNutritionDetailsExpanded: Boolean,
    onShiftDay: (Int) -> Unit,
    onSelectDay: (String) -> Unit,
    onOpenGoalSheet: () -> Unit,
    onQuickAddRecentFood: (FoodCatalogItem) -> Unit,
    onAdjustWaterMl: (Int, Boolean) -> Unit,
    onOpenSearchForSlot: (MealSlotType) -> Unit,
    onOpenEditEntry: (LoggedFoodEntry) -> Unit,
    onToggleNutritionDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalLifeMaxNutritionColors.current
    val daySummary = projection.daySummary
    val goals = projection.goals

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 1. Day Header (.fv2-head)
        DayNavigationHeader(
            selectedDayKey = projection.selectedDayKey,
            todayDayKey = projection.todayDayKey,
            xpAwardedToday = daySummary.xpAwardedToday,
            onPrevDay = { onShiftDay(-1) },
            onNextDay = { onShiftDay(1) },
            onJumpToday = { onSelectDay(projection.todayDayKey) },
            onOpenGoals = onOpenGoalSheet,
        )

        // 2. 7-Day Adherence Strip (.fv2-week)
        WeekAdherenceStrip(weekDays = projection.weekDays, onSelectDay = onSelectDay)

        // 3. Macro Dial Hero Card (.fv2-hero)
        MacroDialHeroCard(
            totals = daySummary.totals,
            goals = goals,
            onOpenGoalSheet = onOpenGoalSheet,
        )

        // 4. Quick Add Recent Chips (.fv2-quick)
        if (projection.recentFoods.isNotEmpty()) {
            RecentQuickChipsBar(
                recentFoods = projection.recentFoods,
                onChipClick = onQuickAddRecentFood,
            )
        }

        // 5. Water Intake Tracker (.fv2-water)
        WaterTrackerCard(
            waterMl = daySummary.waterMl,
            waterGoalMl = goals.waterGoalMl,
            onAddWater = { delta -> onAdjustWaterMl(delta, false) },
            onResetWater = { onAdjustWaterMl(0, true) },
        )

        // 6. Meal Slot Cards (.fv2-meals > .fv2-meal)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (slot in MealSlotType.entries) {
                MealSlotCard(
                    slot = slot,
                    entries = daySummary.entriesForSlot(slot),
                    slotCalories = daySummary.slotCalories(slot),
                    onAddFoodClick = { onOpenSearchForSlot(slot) },
                    onEntryClick = onOpenEditEntry,
                )
            }
        }

        // 7. Collapsible Nutrition Details & 7-Day Calorie Bar Chart (.fv2-det)
        NutritionDetailsSection(
            totals = daySummary.totals,
            weekDays = projection.weekDays,
            calGoal = goals.calGoal,
            isExpanded = isNutritionDetailsExpanded,
            onToggleExpand = onToggleNutritionDetails,
            onSelectDay = onSelectDay,
        )

        // 8. RPG Class Fuel Plan Card (#dietPlanWrap)
        ClassFuelPlanCard(plan = projection.classFuelPlan)
    }
}

@Composable
private fun DayNavigationHeader(
    selectedDayKey: String,
    todayDayKey: String,
    xpAwardedToday: Boolean,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    onJumpToday: () -> Unit,
    onOpenGoals: () -> Unit,
) {
    val colors = LocalLifeMaxNutritionColors.current
    val isToday = selectedDayKey == todayDayKey

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier =
                    Modifier.size(34.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(colors.surfaceSunken)
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(11.dp))
                        .clickable(onClick = onPrevDay),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "‹",
                    color = colors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }

            Column(
                modifier =
                    Modifier.clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onJumpToday)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (isToday) "Today" else selectedDayKey,
                    color = colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = if (isToday) selectedDayKey else "Tap for today",
                    color = colors.textSubtle,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Box(
                modifier =
                    Modifier.size(34.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(colors.surfaceSunken)
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(11.dp))
                        .clickable(onClick = onNextDay),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "›",
                    color = colors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (xpAwardedToday) {
                Box(
                    modifier =
                        Modifier.clip(RoundedCornerShape(11.dp))
                            .background(colors.statusOk.copy(alpha = 0.14f))
                            .border(
                                1.dp,
                                colors.statusOk.copy(alpha = 0.4f),
                                RoundedCornerShape(11.dp),
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "+8 XP Fuel",
                        color = colors.statusOk,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }

            Box(
                modifier =
                    Modifier.height(34.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(colors.surfaceSunken)
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(11.dp))
                        .clickable(onClick = onOpenGoals)
                        .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Goals",
                    color = colors.textMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

@Composable
private fun WeekAdherenceStrip(weekDays: List<WeekDayStatus>, onSelectDay: (String) -> Unit) {
    val colors = LocalLifeMaxNutritionColors.current

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (day in weekDays) {
            val borderColor =
                if (day.isSelected) {
                    colors.accent.copy(alpha = 0.45f)
                } else {
                    Color.Transparent
                }
            val bgBrush =
                if (day.isSelected) {
                    colors.activePillGradient
                } else {
                    Brush.verticalGradient(listOf(colors.surfaceSunken, colors.surfaceSunken))
                }

            val ringColor =
                when (day.ringStatus) {
                    WeekDayRingStatus.OK -> colors.statusOk
                    WeekDayRingStatus.OVER -> colors.statusBad
                    WeekDayRingStatus.UNDER -> colors.accent
                }

            Column(
                modifier =
                    Modifier.weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(bgBrush)
                        .border(1.dp, borderColor, RoundedCornerShape(14.dp))
                        .clickable { onSelectDay(day.dayKey) }
                        .padding(vertical = 7.dp, horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = day.shortDayName,
                    color = if (day.isSelected) colors.textPrimary else colors.textSubtle,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                )

                // 28x28 SVG progress ring (.fv2-wd-ring)
                Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokePx = 2.6.dp.toPx()
                        val radius = 11.dp.toPx()
                        val topLeft = Offset(center.x - radius, center.y - radius)
                        val arcSize = Size(radius * 2f, radius * 2f)

                        drawArc(
                            color = if (colors.isLight) Color(0x140F172A) else Color(0x14FFFFFF),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokePx),
                        )

                        if (day.ringPct > 0) {
                            drawArc(
                                color = ringColor,
                                startAngle = -90f,
                                sweepAngle = (day.ringPct / 100f) * 360f,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokePx, cap = StrokeCap.Round),
                            )
                        }
                    }

                    Text(
                        text = "${day.dayOfMonth}",
                        color = if (day.isToday) colors.accent else colors.textPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentQuickChipsBar(
    recentFoods: List<FoodCatalogItem>,
    onChipClick: (FoodCatalogItem) -> Unit,
) {
    val colors = LocalLifeMaxNutritionColors.current

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "QUICK ADD RECENTS",
            color = colors.textSubtle,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.6.sp,
            modifier = Modifier.padding(horizontal = 2.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            for (food in recentFoods) {
                Row(
                    modifier =
                        Modifier.clip(CircleShape)
                            .background(colors.surfaceSunken)
                            .border(1.dp, colors.borderSubtle, CircleShape)
                            .clickable { onChipClick(food) }
                            .padding(horizontal = 11.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "+",
                        color = colors.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = food.name,
                        color = colors.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text(
                        text = "${food.cal}",
                        color = colors.textSubtle,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun WaterTrackerCard(
    waterMl: Int,
    waterGoalMl: Int,
    onAddWater: (Int) -> Unit,
    onResetWater: () -> Unit,
) {
    val colors = LocalLifeMaxNutritionColors.current
    val safeGoal = max(500, waterGoalMl)
    val fillPct = (waterMl.toFloat() / safeGoal.toFloat()).coerceIn(0f, 1f)

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.cardGradient)
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Droplet badge (.fv2-water-ico: 38x38dp)
        Box(
            modifier =
                Modifier.size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.waterAccent.copy(alpha = 0.15f))
                    .border(1.dp, colors.waterAccent.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
                    .clickable(onClick = onResetWater),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "💧", fontSize = 16.sp)
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = "Water",
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = "$waterMl / $waterGoalMl ml",
                    color = colors.textSubtle,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(if (colors.isLight) Color(0x140F172A) else Color(0x14FFFFFF))
            ) {
                Box(
                    modifier =
                        Modifier.fillMaxWidth(fillPct)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(colors.waterAccent, colors.waterCyan)
                                )
                            )
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WaterQuickBtn(label = "+250", onClick = { onAddWater(250) })
            WaterQuickBtn(label = "+500", onClick = { onAddWater(500) })
        }
    }
}

@Composable
private fun WaterQuickBtn(label: String, onClick: () -> Unit) {
    val colors = LocalLifeMaxNutritionColors.current
    Box(
        modifier =
            Modifier.height(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.waterAccent.copy(alpha = 0.14f))
                .border(1.dp, colors.waterAccent.copy(alpha = 0.30f), RoundedCornerShape(10.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (colors.isLight) Color(0xFF0369A1) else Color(0xFF9DD6FF),
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
private fun MealSlotCard(
    slot: MealSlotType,
    entries: List<LoggedFoodEntry>,
    slotCalories: Int,
    onAddFoodClick: () -> Unit,
    onEntryClick: (LoggedFoodEntry) -> Unit,
) {
    val colors = LocalLifeMaxNutritionColors.current
    val slotDotColor = colors.slotColor(slot)

    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.cardGradient)
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(20.dp))
    ) {
        // Header (.fv2-meal-h)
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(start = 15.dp, top = 13.dp, end = 15.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(slotDotColor))
                Column {
                    Text(
                        text = slot.label,
                        color = colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text =
                            if (entries.isEmpty()) "Nothing logged yet"
                            else "${entries.size} item${if (entries.size == 1) "" else "s"}",
                        color = colors.textSubtle,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "$slotCalories kcal",
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Box(
                    modifier =
                        Modifier.size(30.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.accent.copy(alpha = 0.13f))
                            .border(
                                1.dp,
                                colors.accent.copy(alpha = 0.32f),
                                RoundedCornerShape(10.dp),
                            )
                            .clickable(onClick = onAddFoodClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "+",
                        color = colors.accent,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }

        // Logged items (.fv2-items)
        if (entries.isNotEmpty()) {
            Column(
                modifier =
                    Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (entry in entries) {
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surfaceSunken)
                                .clickable { onEntryClick(entry) }
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.name,
                                color = colors.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text =
                                    "${entry.amountText} · P ${entry.pro.roundToInt()}g · C ${entry.carb.roundToInt()}g · F ${entry.fat.roundToInt()}g",
                                color = colors.textSubtle,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "${entry.cal}",
                            color = colors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NutritionDetailsSection(
    totals: DayMacroTotals,
    weekDays: List<WeekDayStatus>,
    calGoal: Int,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onSelectDay: (String) -> Unit,
) {
    val colors = LocalLifeMaxNutritionColors.current

    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.cardGradient)
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(18.dp))
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(horizontal = 15.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Nutrition details & 7-day trend",
                color = colors.textMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = if (isExpanded) "Hide ▲" else "Show ▼",
                color = colors.textSubtle,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        if (isExpanded) {
            Column(
                modifier =
                    Modifier.fillMaxWidth().padding(start = 15.dp, end = 15.dp, bottom = 15.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MicroRow(
                    label = "Dietary Fiber",
                    valueText = "${totals.fiber}g / 28g",
                    ratio = (totals.fiber / 28.0).toFloat(),
                )
                MicroRow(
                    label = "Total Sugars",
                    valueText = "${totals.sugar}g / 50g",
                    ratio = (totals.sugar / 50.0).toFloat(),
                )
                MicroRow(
                    label = "Sodium",
                    valueText = "${totals.sodiumMg}mg / 2300mg",
                    ratio = (totals.sodiumMg / 2300.0).toFloat(),
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "7-DAY CALORIE INTAKE",
                    color = colors.textSubtle,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.6.sp,
                )

                // 7-day bar chart (.fv2-wk)
                Row(
                    modifier = Modifier.fillMaxWidth().height(106.dp).padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    val maxCal =
                        max(calGoal, weekDays.maxOfOrNull { it.totalCal } ?: calGoal)
                            .coerceAtLeast(1)
                    for (day in weekDays) {
                        val barFill =
                            (day.totalCal.toFloat() / maxCal.toFloat()).coerceIn(0.06f, 1f)
                        Column(
                            modifier =
                                Modifier.weight(1f).fillMaxHeight().clickable {
                                    onSelectDay(day.dayKey)
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                        ) {
                            Text(
                                text = if (day.totalCal > 0) "${day.totalCal}" else "–",
                                color = colors.textSubtle,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier.width(16.dp).weight(1f),
                                contentAlignment = Alignment.BottomCenter,
                            ) {
                                Box(
                                    modifier =
                                        Modifier.fillMaxWidth()
                                            .fillMaxHeight(barFill)
                                            .clip(
                                                RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)
                                            )
                                            .background(
                                                when (day.ringStatus) {
                                                    WeekDayRingStatus.OK -> colors.statusOk
                                                    WeekDayRingStatus.OVER -> colors.statusBad
                                                    WeekDayRingStatus.UNDER -> colors.accent
                                                }
                                            )
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = day.shortDayName,
                                color = if (day.isSelected) colors.accent else colors.textSubtle,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MicroRow(label: String, valueText: String, ratio: Float) {
    val colors = LocalLifeMaxNutritionColors.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                label,
                color = colors.textMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                valueText,
                color = colors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(if (colors.isLight) Color(0x140F172A) else Color(0x14FFFFFF))
        ) {
            Box(
                modifier =
                    Modifier.fillMaxWidth(ratio.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(colors.accent)
            )
        }
    }
}

@Composable
private fun ClassFuelPlanCard(plan: ClassFuelPlanProjection) {
    val colors = LocalLifeMaxNutritionColors.current

    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.cardGradient)
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(20.dp))
                .padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "CLASS FUEL BLUEPRINT",
                color = colors.accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.7.sp,
            )
            Text(
                text = plan.macroSplitText,
                color = colors.textSubtle,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Text(
            text = plan.planTitle,
            color = colors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
        )

        Text(text = plan.coachingTip, color = colors.textMuted, fontSize = 12.sp)
    }
}
