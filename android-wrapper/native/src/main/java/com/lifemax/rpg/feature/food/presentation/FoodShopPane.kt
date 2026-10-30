package com.lifemax.rpg.feature.food.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import java.util.Locale
import org.json.JSONArray

/** The Shopping pane. */
@Composable
internal fun ColumnScope.FoodShopPane(c: FoodContext) {
    with(c) {
        val shopItems = shopObj?.optJSONArray("items") ?: JSONArray()
        val activeItems =
            (0 until shopItems.length())
                .mapNotNull { shopItems.optJSONObject(it) }
                .filter { !it.optBoolean("home") }
        val checkedCount = activeItems.count { it.optBoolean("checked") }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Brush.verticalGradient(listOf(cardTop, cardBottom)))
                        .border(1.dp, panelBorder, RoundedCornerShape(20.dp))
                        .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "7-Day Smart Shopping List",
                        color = fg,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = "$checkedCount of ${activeItems.size} items in basket",
                        color = muted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                HeaderButton(label = "Clear ticks", width = null, light = light) {
                    dispatch("sdClearShop", emptyMap())
                }
            }

            val aisles = activeItems.map { it.optString("aisleName", "Pantry") }.distinct()
            aisles.forEach { aisleName ->
                val rows = activeItems.filter { it.optString("aisleName", "Pantry") == aisleName }
                Column(
                    modifier =
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Brush.verticalGradient(listOf(cardTop, cardBottom)))
                            .border(1.dp, panelBorder, RoundedCornerShape(20.dp))
                            .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = aisleName.uppercase(Locale.US),
                        color = BlueAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.4.sp,
                    )
                    rows.forEach { item ->
                        val itemId = item.optString("id")
                        val checked = item.optBoolean("checked")
                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(sunken)
                                    .clickable {
                                        if (itemId.isNotEmpty()) {
                                            dispatch("sdCheck", mapOf("itemId" to itemId))
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Box(
                                    modifier =
                                        Modifier.size(20.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                if (checked) LunchGreen else Color.Transparent
                                            )
                                            .border(
                                                1.dp,
                                                if (checked) LunchGreen else Color(0xFF334155),
                                                RoundedCornerShape(6.dp),
                                            ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (checked) {
                                        Text(
                                            text = "✓",
                                            color = BgObsidian,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Black,
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = item.optString("name"),
                                        color = if (checked) subtle else fg,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text =
                                            "${item.optInt("grams")}g needed · ${item.optInt("meals")} meals",
                                        color = subtle,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                            Text(
                                text = "${item.optInt("packs")} × ${item.optString("unit")}",
                                color = if (checked) subtle else BlueAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }
                }
            }
        }
    }
}
