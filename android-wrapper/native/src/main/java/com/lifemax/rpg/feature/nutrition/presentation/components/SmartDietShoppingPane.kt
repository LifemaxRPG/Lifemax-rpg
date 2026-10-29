package com.lifemax.rpg.feature.nutrition.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.LocalLifeMaxNutritionColors
import com.lifemax.rpg.feature.nutrition.domain.model.ShoppingListItem
import com.lifemax.rpg.feature.nutrition.domain.model.SmartDietShoppingProjection

/**
 * Pixel-accurate 1:1 Jetpack Compose reproduction of the Smart Diet `Shopping` pane (`sdShopHtml`
 * in `50-smartdiet.js` lines 1210-1280 and `08-food.css` lines 780-895).
 *
 * Includes:
 * - 7-day shopping header with checked count and `Reset` button
 * - Supermarket aisle groups (`Meat, fish & eggs`, `Dairy & chiller`, `Fruit & veg`, etc.)
 * - Pack-size calculation labels (`2 × 500g pack`)
 * - Perishable multi-trip warning pill (`"Buy twice, goes off"`)
 * - Collapsible `"Probably at home"` cupboard section (`.sd-home`)
 */
@Composable
fun SmartDietShoppingPane(
    shopping: SmartDietShoppingProjection,
    isPantryExpanded: Boolean,
    onToggleItem: (String) -> Unit,
    onResetChecks: () -> Unit,
    onTogglePantryExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalLifeMaxNutritionColors.current

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Header card (.sd-shop-head)
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.cardGradient)
                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(18.dp))
                    .padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "7-Day Smart Shopping List",
                    color = colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text =
                        "${shopping.checkedItemCount} of ${shopping.totalActiveItems} items ticked ·Exact UK pack sizes",
                    color = colors.textSubtle,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (shopping.checkedItemCount > 0) {
                Box(
                    modifier =
                        Modifier.clip(RoundedCornerShape(10.dp))
                            .background(colors.surfaceSunken)
                            .border(1.dp, colors.borderSubtle, RoundedCornerShape(10.dp))
                            .clickable(onClick = onResetChecks)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Untick all",
                        color = colors.textMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }

        // Supermarket Aisle Groups (.sd-aisle)
        for (group in shopping.aisleGroups) {
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.cardGradient)
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(18.dp))
                        .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = group.aisleName.uppercase(),
                        color = colors.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.6.sp,
                    )
                    Text(
                        text = "${group.items.size} items",
                        color = colors.textSubtle,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                for (item in group.items) {
                    ShoppingItemRow(item = item, onToggle = { onToggleItem(item.itemKey) })
                }
            }
        }

        // "Probably at home" Pantry Disclosure (.sd-home)
        if (shopping.pantryHomeItems.isNotEmpty()) {
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
                            .clickable(onClick = onTogglePantryExpanded)
                            .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text =
                            "Probably at home (cupboard staples · ${shopping.pantryHomeItems.size})",
                        color = colors.textMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = if (isPantryExpanded) "Hide ▲" else "Show ▼",
                        color = colors.textSubtle,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                if (isPantryExpanded) {
                    Column(
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        for (pantryItem in shopping.pantryHomeItems) {
                            Row(
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.surfaceSunken)
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = pantryItem.name,
                                    color = colors.textMuted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = pantryItem.unitPackLabel,
                                    color = colors.textSubtle,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShoppingItemRow(item: ShoppingListItem, onToggle: () -> Unit) {
    val colors = LocalLifeMaxNutritionColors.current

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceSunken)
                .clickable(onClick = onToggle)
                .padding(horizontal = 11.dp, vertical = 10.dp),
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
                    Modifier.size(20.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (item.isChecked) colors.statusOk else colors.surfaceElevated)
                        .border(
                            1.dp,
                            if (item.isChecked) colors.statusOk else colors.borderStrong,
                            RoundedCornerShape(6.dp),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                if (item.isChecked) {
                    Text(
                        "✓",
                        color = colors.bgPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    color = if (item.isChecked) colors.textSubtle else colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textDecoration =
                        if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "${item.totalGrams}g needed · ${item.usedInMealCount} meals",
                        color = colors.textSubtle,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (item.buyTwicePerishableWarning) {
                        Text(
                            text = "· Buy twice, goes off",
                            color = colors.statusWarn,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = item.unitPackLabel,
            color = if (item.isChecked) colors.textSubtle else colors.accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}
