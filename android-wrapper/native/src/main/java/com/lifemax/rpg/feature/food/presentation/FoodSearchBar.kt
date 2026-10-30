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

/** The floating "Search foods, or log a meal" bar. */
@Composable
internal fun BoxScope.FoodSearchBar(c: FoodContext) {
    with(c) {
        // Floating Bottom "Search foods, or log a meal" Bar matching original screenshots
        Box(
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = bottomInsetDp.dp + 12.dp)
        ) {
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xF5121621))
                        .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(22.dp))
                        .clickable { dispatch("add", emptyMap()) }
                        .padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(text = "🔍", color = muted, fontSize = 16.sp)
                    Text(
                        text = "Search foods, or log a meal",
                        color = muted,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Box(
                    modifier =
                        Modifier.size(44.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(
                                Brush.linearGradient(listOf(Color(0xFF93ADFF), Color(0xFF9F85FF)))
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "+",
                        color = BgObsidian,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
    }
}
