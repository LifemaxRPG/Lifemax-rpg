package com.lifemax.rpg.feature.food.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** The Log pane: calories to go, macro rings and the day's entries. */
@Composable
internal fun ColumnScope.FoodLogPane(c: FoodContext) {
    with(c) {
        val left = calGoal - calLogged

        // Hero Dial Card with Card-Anchored AAA Plasma Ribbon & Ring Torch Canvas
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(Brush.verticalGradient(listOf(cardTop, cardBottom)))
                    .border(1.dp, panelBorder, RoundedCornerShape(26.dp))
                    .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // 320x320 Concentric 3-Ring Dial
                Box(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 310.dp).aspectRatio(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    val pAnim = flightProgress.value
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val factor = size.minDimension / 320f
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val calRatio =
                            if (calGoal > 0) (calLogged.toFloat() / calGoal).coerceIn(0f, 1f)
                            else 0f

                        // 72 Outer Calorie Ticks
                        for (i in 0 until 72) {
                            val a = i.toDouble() / 72.0 * PI * 2.0 - PI / 2.0
                            val isMajor = i % 6 == 0
                            val r0 = (if (isMajor) 137f else 141f) * factor
                            val r1 = 151f * factor
                            val tickLit = (i.toFloat() / 72f) < calRatio
                            drawLine(
                                color = if (tickLit) BlueAccent else Color(0xFF232838),
                                start =
                                    Offset(cx + cos(a).toFloat() * r0, cy + sin(a).toFloat() * r0),
                                end =
                                    Offset(cx + cos(a).toFloat() * r1, cy + sin(a).toFloat() * r1),
                                strokeWidth = (if (isMajor) 2.2f else 1.4f) * factor,
                                cap = StrokeCap.Round,
                            )
                        }

                        // 3 Concentric Macro Rings (r=126 Protein, r=101 Carbs, r=76 Fat)
                        val ringConfigs =
                            listOf(
                                Triple(126f, prevRatios.first to targetRatios.first, ProteinRose),
                                Triple(101f, prevRatios.second to targetRatios.second, CarbsAmber),
                                Triple(76f, prevRatios.third to targetRatios.third, FatTeal),
                            )
                        val strokeW = 16f * factor

                        ringConfigs.forEachIndexed { idx, (radBase, ratioPair, ringColor) ->
                            val r = radBase * factor
                            val topLeft = Offset(cx - r, cy - r)
                            val arcSize = Size(r * 2f, r * 2f)

                            // Track
                            drawArc(
                                color = Color(0xFF1B2030),
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeW, cap = StrokeCap.Round),
                            )

                            // Animated sweep during AAA torch weld
                            val (prevR, nextR) = ratioPair
                            val torchLocal =
                                if (flightActive) {
                                    ((pAnim - (0.36f + idx * 0.04f)) / 0.42f).coerceIn(0f, 1f)
                                } else 1f
                            val easedTorch = 1f - (1f - torchLocal).pow(3f)
                            val curRatio =
                                if (flightActive) {
                                    prevR + (nextR - prevR) * easedTorch
                                } else nextR

                            if (curRatio > 0.002f) {
                                drawArc(
                                    color = ringColor,
                                    startAngle = -90f,
                                    sweepAngle = 360f * curRatio,
                                    useCenter = false,
                                    topLeft = topLeft,
                                    size = arcSize,
                                    style = Stroke(width = strokeW, cap = StrokeCap.Round),
                                )
                            }

                            // AAA Plasma Ribbon & Circumferential Torch Head
                            if (flightActive) {
                                val ribbonPhase = ((pAnim - idx * 0.04f) / 0.36f).coerceIn(0f, 1f)
                                val tipAngleRad = (-PI / 2.0) + (curRatio * 2.0 * PI)
                                val tipX = cx + cos(tipAngleRad).toFloat() * r
                                val tipY = cy + sin(tipAngleRad).toFloat() * r

                                if (ribbonPhase > 0f && ribbonPhase < 1f) {
                                    val startX = cx + (idx - 1) * 95f * factor
                                    val startY = size.height + 36f * factor
                                    val easeR = 1f - (1f - ribbonPhase).pow(2.6f)
                                    val headX = startX + (tipX - startX) * easeR
                                    val headY = startY + (tipY - startY) * easeR

                                    drawCircle(
                                        color = ringColor.copy(alpha = 0.42f),
                                        radius = 18f * factor,
                                        center = Offset(headX, headY),
                                    )
                                    drawCircle(
                                        color = fg,
                                        radius = 5.5f * factor,
                                        center = Offset(headX, headY),
                                    )
                                }

                                if (torchLocal > 0f && torchLocal < 1f) {
                                    drawCircle(
                                        color = ringColor.copy(alpha = 0.55f),
                                        radius = 22f * factor,
                                        center = Offset(tipX, tipY),
                                    )
                                    drawCircle(
                                        color = fg,
                                        radius = 7f * factor,
                                        center = Offset(tipX, tipY),
                                    )
                                }
                            }
                        }
                    }

                    // Center Readout
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { dispatch("goals", emptyMap()) },
                    ) {
                        Text(
                            text = fmtNum(if (calGoal > 0) abs(left) else calLogged),
                            color = fg,
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            text =
                                if (calGoal == 0) "KCAL TODAY"
                                else if (left >= 0) "KCAL LEFT" else "KCAL OVER",
                            color = subtle,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.4.sp,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text =
                                if (calGoal > 0) "${fmtNum(calLogged)} of ${fmtNum(calGoal)}"
                                else "Set a daily target",
                            color = muted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                // 3 Macro Progress Tiles (PROTEIN | CARBS | FAT) with Glowing Neon Dots
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MacroTileCard(
                        label = "PROTEIN",
                        light = light,
                        dotColor = ProteinRose,
                        current = proLogged.roundToInt(),
                        goal = proGoal.roundToInt(),
                        modifier = Modifier.weight(1f),
                    )
                    MacroTileCard(
                        label = "CARBS",
                        light = light,
                        dotColor = CarbsAmber,
                        current = carbLogged.roundToInt(),
                        goal = carbGoal.roundToInt(),
                        modifier = Modifier.weight(1f),
                    )
                    MacroTileCard(
                        label = "FAT",
                        light = light,
                        dotColor = FatTeal,
                        current = fatLogged.roundToInt(),
                        goal = fatGoal.roundToInt(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // QUICK ADD Section (one tap, <currentSlot>)
        if (recents.length() > 0) {
            val slotLabel = data.optString("currentSlot", "breakfast").lowercase(Locale.US)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(
                    text = "QUICK ADD",
                    color = subtle,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.5.sp,
                )
                Text(
                    text = "one tap, $slotLabel",
                    color = subtle,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for (i in 0 until recents.length()) {
                    val rc = recents.optJSONObject(i) ?: continue
                    val rName = rc.optString("name", "Food")
                    val rKcal = rc.optInt("kcal", 0)
                    val rIdx = rc.optInt("index", i)

                    Column(
                        modifier =
                            Modifier.width(210.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Brush.verticalGradient(listOf(cardTop, cardBottom)))
                                .border(1.dp, panelBorder, RoundedCornerShape(18.dp))
                                .clickable { dispatch("quickAdd", mapOf("index" to rIdx)) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = rName,
                            color = fg,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "$rKcal kcal",
                            color = muted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        // WATER Card matching Screenshot_20261029_062450_LifeMax.jpg
        val waterMl = data.optInt("waterMl", 0)
        val waterGoal = goals.optInt("water", 2000).coerceAtLeast(1)
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brush.verticalGradient(listOf(cardTop, cardBottom)))
                    .border(1.dp, panelBorder, RoundedCornerShape(22.dp))
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Outlined water droplet badge (#132238)
                Box(
                    modifier =
                        Modifier.size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF132238)),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.size(22.dp)) {
                        val w = size.width
                        val h = size.height
                        val p =
                            Path().apply {
                                moveTo(w * 0.5f, h * 0.12f)
                                cubicTo(
                                    w * 0.78f,
                                    h * 0.42f,
                                    w * 0.82f,
                                    h * 0.62f,
                                    w * 0.82f,
                                    h * 0.68f,
                                )
                                cubicTo(
                                    w * 0.82f,
                                    h * 0.86f,
                                    w * 0.68f,
                                    h * 0.94f,
                                    w * 0.5f,
                                    h * 0.94f,
                                )
                                cubicTo(
                                    w * 0.32f,
                                    h * 0.94f,
                                    w * 0.18f,
                                    h * 0.86f,
                                    w * 0.18f,
                                    h * 0.68f,
                                )
                                cubicTo(
                                    w * 0.18f,
                                    h * 0.62f,
                                    w * 0.22f,
                                    h * 0.42f,
                                    w * 0.5f,
                                    h * 0.12f,
                                )
                                close()
                            }
                        drawPath(
                            path = p,
                            color = Color(0xFF4DA6FF),
                            style =
                                Stroke(
                                    width = 2.1.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round,
                                ),
                        )
                    }
                }

                Column {
                    Text(
                        text = "Water",
                        color = fg,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = "${fmtNum(waterMl)} / ${fmtNum(waterGoal)} ml",
                        color = muted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WaterPillButton("+250", light) { dispatch("water", mapOf("ml" to 250)) }
                WaterPillButton("+500", light) { dispatch("water", mapOf("ml" to 500)) }
            }
        }

        // MEALS Section Header
        Text(
            text = "MEALS",
            color = subtle,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.6.sp,
            modifier = Modifier.padding(top = 4.dp),
        )

        // 4 Meal Slot Cards (Breakfast, Lunch, Dinner, Snacks)
        val slotDefs =
            listOf(
                Triple("breakfast", "Breakfast", BreakfastAmber),
                Triple("lunch", "Lunch", LunchGreen),
                Triple("dinner", "Dinner", DinnerIndigo),
                Triple("snacks", "Snacks", SnacksCoral),
            )

        slotDefs.forEach { (slotId, slotTitle, slotDotColor) ->
            val slotEntries =
                (0 until entries.length())
                    .mapNotNull { entries.optJSONObject(it) }
                    .filter { it.optString("slot").equals(slotId, ignoreCase = true) }
            val slotKcal = slotEntries.sumOf { it.optDouble("kcal", 0.0) }.roundToInt()
            val canCopyYesterday = copyYesterday.optBoolean(slotId, false)

            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Brush.verticalGradient(listOf(cardTop, cardBottom)))
                        .border(1.dp, panelBorder, RoundedCornerShape(22.dp))
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        GlowingDot(color = slotDotColor)
                        Text(
                            text = slotTitle,
                            color = fg,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (slotEntries.isNotEmpty()) {
                            Text(
                                text = "${fmtNum(slotKcal)} kcal",
                                color = muted,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Box(
                            modifier =
                                Modifier.size(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(selectedPanel)
                                    .border(1.dp, panelBorder, RoundedCornerShape(12.dp))
                                    .clickable { dispatch("add", mapOf("slot" to slotId)) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "+",
                                color = BlueAccent,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                if (slotEntries.isEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        DashedSlotButton(
                            label = "Add ${slotTitle.lowercase(Locale.US)}",
                            textColor = Color(0xFF8AB4FF),
                            light = light,
                            modifier = Modifier.weight(if (canCopyYesterday) 1.25f else 1f),
                        ) {
                            dispatch("add", mapOf("slot" to slotId))
                        }

                        if (canCopyYesterday) {
                            DashedSlotButton(
                                label = "Copy yesterday",
                                textColor = muted,
                                light = light,
                                modifier = Modifier.weight(1f),
                            ) {
                                dispatch("copySlot", mapOf("slot" to slotId))
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        slotEntries.forEach { entry ->
                            val entryId = entry.optString("id")
                            val eName = entry.optString("name", "Food")
                            val eGrams = entry.optDouble("grams", 100.0).roundToInt()
                            val eKcal = entry.optDouble("kcal", 0.0).roundToInt()
                            val eP = entry.optDouble("p", 0.0).roundToInt()
                            val eC = entry.optDouble("c", 0.0).roundToInt()
                            val eF = entry.optDouble("f", 0.0).roundToInt()

                            Row(
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(sunken)
                                        .clickable {
                                            if (entryId.isNotEmpty()) {
                                                dispatch("entry", mapOf("id" to entryId))
                                            }
                                        }
                                        .padding(horizontal = 14.dp, vertical = 11.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = eName,
                                        color = fg,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "$eGrams g · P $eP · C $eC · F $eF",
                                        color = subtle,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                                Text(
                                    text = fmtNum(eKcal),
                                    color = fg,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
