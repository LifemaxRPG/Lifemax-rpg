package com.lifemax.rpg.feature.food.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The Monday to Sunday strip. */
@Composable
internal fun ColumnScope.FoodWeekStrip(c: FoodContext) {
    with(c) {
        // 2. 7-Day Monday-Sunday Week Strip (M 26 .. T 29 .. S 1)
        if (week.length() > 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (i in 0 until week.length()) {
                    val wd = week.optJSONObject(i) ?: continue
                    val isSelected = wd.optBoolean("selected")
                    val dName = wd.optString("w", "M")
                    val dNum = wd.optInt("dNum", i + 1)
                    val dayKey = wd.optString("day")
                    val frac = wd.optDouble("frac", 0.0).toFloat().coerceIn(0f, 1f)

                    Column(
                        modifier =
                            Modifier.weight(1f)
                                .height(80.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (isSelected) selectedPanel else Color.Transparent)
                                .clickable {
                                    if (dayKey.isNotEmpty()) {
                                        dispatch("selectDay", mapOf("day" to dayKey))
                                    }
                                }
                                .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = dName,
                            color = if (isSelected) fg else subtle,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )

                        if (isSelected) {
                            Column(
                                modifier = Modifier.size(38.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Box(
                                    modifier =
                                        Modifier.size(5.dp).clip(CircleShape).background(BlueAccent)
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = dNum.toString(),
                                    color = fg,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier.size(38.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val r = size.minDimension / 2f - 3.dp.toPx()
                                    val c = Offset(size.width / 2f, size.height / 2f)
                                    drawCircle(
                                        color = trackColor,
                                        radius = r,
                                        center = c,
                                        style = Stroke(width = 2.6.dp.toPx()),
                                    )
                                    if (frac > 0.01f) {
                                        drawArc(
                                            color = BlueAccent,
                                            startAngle = -90f,
                                            sweepAngle = 360f * frac,
                                            useCenter = false,
                                            topLeft = Offset(c.x - r, c.y - r),
                                            size = Size(r * 2f, r * 2f),
                                            style =
                                                Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round),
                                        )
                                    }
                                    // 12 o'clock top blue dot matching original screenshot
                                    drawCircle(
                                        color = BlueAccent,
                                        radius = 2.1.dp.toPx(),
                                        center = Offset(c.x, c.y - r),
                                    )
                                }
                                Text(
                                    text = dNum.toString(),
                                    color = fg,
                                    fontSize = 14.sp,
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
