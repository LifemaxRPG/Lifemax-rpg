package com.lifemax.rpg.feature.food.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt
import org.json.JSONArray

/** The Plan pane: the next meal up, its ready actions, the day so far. */
@Composable
internal fun ColumnScope.FoodPlanPane(c: FoodContext) {
    with(c) {
        val workoutHeading =
            planObj?.optString("workoutHeading", "TRAINING C · TRAINING DAY")
                ?: "TRAINING C · TRAINING DAY"
        val planTarget = planObj?.optInt("target", calGoal) ?: calGoal
        val planEaten = planObj?.optInt("eaten", calLogged) ?: calLogged
        val planLeft = max(0, planTarget - planEaten)
        val eatenP = planObj?.optInt("eatenP", proLogged.roundToInt()) ?: proLogged.roundToInt()
        val planP = planObj?.optInt("planP", proGoal.roundToInt()) ?: proGoal.roundToInt()
        val planItems = planObj?.optJSONArray("items") ?: JSONArray()
        val nextIdx = planObj?.optInt("nextIndex", 0) ?: 0
        val nextMeal =
            if (nextIdx >= 0 && nextIdx < planItems.length()) {
                planItems.optJSONObject(nextIdx)
            } else null

        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // Training & Calorie Progress Header
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        text = workoutHeading,
                        color = subtle,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.6.sp,
                    )
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(
                            text = fmtNum(planLeft),
                            color = fg,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            text = "kcal to go",
                            color = subtle,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Progress Track
                val pct = (planEaten.toFloat() / max(1, planTarget)).coerceIn(0f, 1f)
                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(trackColor)
                ) {
                    Box(
                        modifier =
                            Modifier.fillMaxHeight()
                                .fillMaxWidth(pct)
                                .clip(CircleShape)
                                .background(Brush.horizontalGradient(listOf(BlueAccent, FatTeal)))
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row {
                        Text(
                            text = fmtNum(planEaten),
                            color = fg,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            text = " of ${fmtNum(planTarget)} kcal",
                            color = subtle,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Row {
                        Text(
                            text = "Protein ",
                            color = subtle,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "$eatenP",
                            color = fg,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            text = " / $planP g",
                            color = subtle,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            // UP NEXT Hero Meal Block
            if (nextMeal != null) {
                val slotName = nextMeal.optString("slotName", "Breakfast").uppercase(Locale.US)
                val slotWhen = nextMeal.optString("slotWhen", "Morning").uppercase(Locale.US)
                val activeMin = nextMeal.optInt("activeMin", 3)
                val tagsLabel = nextMeal.optString("tagsLabel", "No cooking")
                val mealName = nextMeal.optString("name", "Cottage Cheese & Fruit Bowl")
                val mKcal = nextMeal.optInt("kcal", 573)
                val mP = nextMeal.optInt("p", 39)
                val mC = nextMeal.optInt("c", 69)
                val mF = nextMeal.optInt("f", 16)
                val topIngs =
                    nextMeal.optString("topIngs", "Cottage cheese · Apple · Rolled oats · Almonds")
                val mealIndex = nextMeal.optInt("index", 0)

                Column(modifier = Modifier.fillMaxWidth()) {
                    // Eyebrow Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            GlowingDot(color = BreakfastAmber)
                            Text(
                                text = "UP NEXT · $slotName · $slotWhen",
                                color = muted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.4.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = "$activeMin min · $tagsLabel",
                            color = muted,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.End,
                            modifier = Modifier.widthIn(max = 100.dp),
                            maxLines = 2,
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Giant Meal Title
                    Text(
                        text = mealName,
                        color = fg,
                        fontSize = 30.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.Black,
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // 4-Column Hairline Macro Table
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(panelBorder))
                    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        // KCAL
                        Column(
                            modifier =
                                Modifier.weight(1f).padding(vertical = 14.dp, horizontal = 4.dp)
                        ) {
                            Text(
                                text = mKcal.toString(),
                                color = fg,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Black,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "KCAL",
                                color = subtle,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.3.sp,
                            )
                        }
                        VerticalHairline(panelBorder)
                        // PROTEIN
                        Column(
                            modifier =
                                Modifier.weight(1f).padding(vertical = 14.dp, horizontal = 12.dp)
                        ) {
                            Text(
                                text = "${mP}g",
                                color = ProteinRose,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "PROTEIN",
                                color = subtle,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.3.sp,
                            )
                        }
                        VerticalHairline(panelBorder)
                        // CARBS
                        Column(
                            modifier =
                                Modifier.weight(1f).padding(vertical = 14.dp, horizontal = 12.dp)
                        ) {
                            Text(
                                text = "${mC}g",
                                color = CarbsAmber,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "CARBS",
                                color = subtle,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.3.sp,
                            )
                        }
                        VerticalHairline(panelBorder)
                        // FAT
                        Column(
                            modifier =
                                Modifier.weight(1f).padding(vertical = 14.dp, horizontal = 12.dp)
                        ) {
                            Text(
                                text = "${mF}g",
                                color = FatTeal,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "FAT",
                                color = subtle,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.3.sp,
                            )
                        }
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(panelBorder))

                    Spacer(modifier = Modifier.height(14.dp))

                    // Dot-separated Ingredients Line
                    Text(
                        text = topIngs,
                        color = Color(0xFF94A3B8),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Full-width White Pill CTA: Log this meal
                    Box(
                        modifier =
                            Modifier.fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color(0xFFF1F5F9))
                                .clickable { dispatch("sdLog", mapOf("index" to mealIndex)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Log this meal",
                            color = BgObsidian,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 3-Column Secondary Action Row: Ingredients | Swap | Skip
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .height(IntrinsicSize.Min)
                                .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier =
                                Modifier.weight(1f)
                                    .clickable { showUpNextIngredients = !showUpNextIngredients }
                                    .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (showUpNextIngredients) "Hide details" else "Ingredients",
                                color = fg,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        VerticalHairline(panelBorder)
                        Box(
                            modifier =
                                Modifier.weight(1f)
                                    .clickable { dispatch("sdSwap", mapOf("index" to mealIndex)) }
                                    .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Swap",
                                color = fg,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        VerticalHairline(panelBorder)
                        Box(
                            modifier =
                                Modifier.weight(1f)
                                    .clickable { dispatch("sdSkip", mapOf("index" to mealIndex)) }
                                    .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Skip",
                                color = fg,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    if (showUpNextIngredients) {
                        val ingsArr = nextMeal.optJSONArray("ings") ?: JSONArray()
                        Column(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Color(0xFF11141D))
                                    .border(1.dp, panelBorder, RoundedCornerShape(18.dp))
                                    .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            for (k in 0 until ingsArr.length()) {
                                val ig = ingsArr.optJSONObject(k) ?: continue
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = ig.optString("name"),
                                        color = fg,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = "${ig.optInt("grams")} g",
                                        color = muted,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }

                    // Coaching Tip Card
                    val coachText =
                        planObj?.optString(
                            "coachText",
                            "Start with $mealName. Carbs up front fuel today's session.",
                        ) ?: "Start with $mealName. Carbs up front fuel today's session."
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(top = 12.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF11141D))
                                .border(1.dp, panelBorder, RoundedCornerShape(20.dp))
                                .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(text = "✦", color = subtle, fontSize = 16.sp)
                        Text(text = coachText, color = muted, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                }
            }
        }
    }
}
