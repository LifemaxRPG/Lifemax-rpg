package com.lifemax.rpg.feature.food.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.components.PaneSwitch
import java.util.Locale

/** The Meals/Recipes switch, the day heading with its Targets and day arrows. */
@Composable
internal fun ColumnScope.FoodDateHeader(c: FoodContext) {
    with(c) {
        Spacer(Modifier.height(topInsetDp.dp))
        PaneSwitch(listOf("Meals", "Recipes"), "Meals", light, { dispatch("recipes", emptyMap()) })
        // 1. Shared Date Header (TODAY / Thursday, Oct 29 | Targets | ‹ | ›)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = data.optString("eyebrow", "TODAY").uppercase(Locale.US),
                    color = subtle,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.8.sp,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = data.optString("dayLabel", data.optString("day", "Today")),
                    color = fg,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeaderButton(label = "Targets", width = null, light = light) {
                    dispatch("goals", emptyMap())
                }
                HeaderButton(label = "‹", width = 42.dp, light = light) {
                    dispatch("previous", emptyMap())
                }
                HeaderButton(label = "›", width = 42.dp, light = light) {
                    dispatch("next", emptyMap())
                }
            }
        }
    }
}
