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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The Plan, Log and Shopping switcher. */
@Composable
internal fun ColumnScope.FoodPaneSwitcher(c: FoodContext) {
    with(c) {
        // 3. 3-Way Segmented Switcher (Plan | Log | Shopping)
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (light) Color.White else Color(0xFF12151F))
                    .border(1.dp, panelBorder, RoundedCornerShape(18.dp))
                    .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf("plan" to "Plan", "log" to "Log", "shop" to "Shopping").forEach { (id, label) ->
                val isOn = activePane == id
                Box(
                    modifier =
                        Modifier.weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(if (isOn) selectedPanel else Color.Transparent)
                            .clickable {
                                activePane = id
                                dispatch("pane", mapOf("pane" to id))
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Text(
                            text =
                                when (id) {
                                    "plan" -> "▣"
                                    "log" -> "☰"
                                    else -> "▤"
                                },
                            color = if (isOn) BlueAccent else muted,
                            fontSize = 13.sp,
                        )
                        Text(
                            text = label,
                            color = if (isOn) fg else muted,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}
