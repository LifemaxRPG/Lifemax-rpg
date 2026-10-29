package com.lifemax.rpg.feature.nutrition.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.draw.scale
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
import com.lifemax.rpg.feature.nutrition.domain.logic.NutritionCalculations
import com.lifemax.rpg.feature.nutrition.domain.model.DayMacroTotals
import com.lifemax.rpg.feature.nutrition.domain.model.NutritionGoals
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Represents a recently logged food/meal macro payload for triggering the curved Bezier Macro
 * Flight & Ring Impact Surge animation on the hero dial.
 */
@Immutable
data class MacroBurstEvent(
    val eventId: Long,
    val addedCal: Int,
    val addedPro: Double,
    val addedCarb: Double,
    val addedFat: Double,
)

/**
 * Pixel-accurate 1:1 Jetpack Compose reproduction of `.fv2-hero` and `fv2RingSvg` from
 * `lifemax_modular_v73/src/runtime/segments/50-food.js` (lines 522-562) and
 * `lifemax_modular_v73/src/styles/08-food.css` (lines 76-153), enhanced with:
 * - Curved Bezier macro orb flight from the meal cards into each concentric ring (`r=126`, `r=101`,
 *   `r=76`)
 * - Ring impact shockwave ripples and glowing arc surge
 * - Smoothly animated calorie & macro number counters
 */
@Composable
fun MacroDialHeroCard(
    totals: DayMacroTotals,
    goals: NutritionGoals,
    onOpenGoalSheet: () -> Unit,
    burstEvent: MacroBurstEvent? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalLifeMaxNutritionColors.current

    // Smooth animated calorie counter & ring sweep angles
    val animatedCal by
        animateIntAsState(
            targetValue = totals.cal,
            animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing),
            label = "animatedCal",
        )

    val proAnglesTarget = NutritionCalculations.computeMacroArcAngles(totals.pro, goals.proGoal)
    val carbAnglesTarget = NutritionCalculations.computeMacroArcAngles(totals.carb, goals.carbGoal)
    val fatAnglesTarget = NutritionCalculations.computeMacroArcAngles(totals.fat, goals.fatGoal)

    val proSweep by
        animateFloatAsState(
            targetValue = proAnglesTarget.primarySweepDegrees,
            animationSpec =
                tween(durationMillis = 780, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)),
            label = "proSweep",
        )
    val carbSweep by
        animateFloatAsState(
            targetValue = carbAnglesTarget.primarySweepDegrees,
            animationSpec =
                tween(durationMillis = 820, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)),
            label = "carbSweep",
        )
    val fatSweep by
        animateFloatAsState(
            targetValue = fatAnglesTarget.primarySweepDegrees,
            animationSpec =
                tween(durationMillis = 860, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)),
            label = "fatSweep",
        )

    // Flight & Impact progress (0f..1f) triggered whenever burstEvent.eventId changes
    val burstProgress = remember { Animatable(1f) }
    LaunchedEffect(burstEvent?.eventId) {
        if (burstEvent != null) {
            burstProgress.snapTo(0f)
            burstProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1050, easing = LinearOutSlowInEasing),
            )
        }
    }

    val isBurstActive = burstEvent != null && burstProgress.value < 0.99f
    val centerScale =
        if (isBurstActive && burstProgress.value in 0.55f..0.92f) {
            val t = (burstProgress.value - 0.55f) / 0.37f
            1f + 0.12f * sin(t * Math.PI.toFloat())
        } else {
            1f
        }

    val calDiff = goals.calGoal - animatedCal
    val isOverCal = calDiff < 0

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(colors.cardGradient)
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(24.dp))
                .padding(start = 16.dp, top = 18.dp, end = 16.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(264.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val scale = size.minDimension / 320f
                val cx = size.width / 2f
                val cy = size.height / 2f

                // 1. 72 perimeter ticks (50-food.js lines 526-534)
                val tickMajorColor = if (colors.isLight) Color(0x2E0F172A) else Color(0x2EFFFFFF)
                val tickMinorColor = if (colors.isLight) Color(0x140F172A) else Color(0x13FFFFFF)
                for (i in 0 until 72) {
                    val angleRad = ((i.toDouble() / 72.0) * Math.PI * 2.0) - (Math.PI / 2.0)
                    val isMajor = i % 6 == 0
                    val r0 = (if (isMajor) 137f else 141f) * scale
                    val r1 = 151f * scale
                    val x1 = cx + (cos(angleRad) * r0).toFloat()
                    val y1 = cy + (sin(angleRad) * r0).toFloat()
                    val x2 = cx + (cos(angleRad) * r1).toFloat()
                    val y2 = cy + (sin(angleRad) * r1).toFloat()
                    drawLine(
                        color = if (isMajor) tickMajorColor else tickMinorColor,
                        start = Offset(x1, y1),
                        end = Offset(x2, y2),
                        strokeWidth = (if (isMajor) 2f else 1.3f) * scale,
                        cap = StrokeCap.Round,
                    )
                }

                // 2. Three concentric macro rings (50-food.js lines 535-558)
                data class AnimatedRingSpec(
                    val radiusSvg: Float,
                    val sweepDeg: Float,
                    val overflowSweepDeg: Float,
                    val color: Color,
                    val controlOffsetX: Float,
                )

                val rings =
                    listOf(
                        AnimatedRingSpec(
                            126f,
                            proSweep,
                            proAnglesTarget.overflowSweepDegrees,
                            colors.macroProtein,
                            -95f * scale,
                        ),
                        AnimatedRingSpec(
                            101f,
                            carbSweep,
                            carbAnglesTarget.overflowSweepDegrees,
                            colors.macroCarbs,
                            0f,
                        ),
                        AnimatedRingSpec(
                            76f,
                            fatSweep,
                            fatAnglesTarget.overflowSweepDegrees,
                            colors.macroFat,
                            95f * scale,
                        ),
                    )

                val trackColor = if (colors.isLight) Color(0x140F172A) else Color(0x13FFFFFF)
                val mainStroke = Stroke(width = 16f * scale, cap = StrokeCap.Round)
                val overflowStroke = Stroke(width = 11f * scale, cap = StrokeCap.Round)

                for (ring in rings) {
                    val rPx = ring.radiusSvg * scale
                    val topLeft = Offset(cx - rPx, cy - rPx)
                    val arcSize = Size(rPx * 2f, rPx * 2f)

                    // Track circle
                    drawArc(
                        color = trackColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = mainStroke,
                    )

                    if (ring.sweepDeg > 0.5f) {
                        drawArc(
                            color = ring.color,
                            startAngle = -90f,
                            sweepAngle = ring.sweepDeg,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = mainStroke,
                        )
                    }

                    if (ring.overflowSweepDeg > 0.5f) {
                        drawArc(
                            color = colors.macroOverflow,
                            startAngle = -90f,
                            sweepAngle = ring.overflowSweepDeg,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = overflowStroke,
                        )
                    }

                    // 3. Curved Bezier Macro Orb Flight & Ring Impact Shockwave
                    if (isBurstActive) {
                        val p = burstProgress.value
                        val endAngleRad = Math.toRadians((-90f + ring.sweepDeg).toDouble())
                        val targetX = cx + (cos(endAngleRad) * rPx).toFloat()
                        val targetY = cy + (sin(endAngleRad) * rPx).toFloat()

                        if (p <= 0.62f) {
                            // Flight phase (0f..0.62f normalized to 0f..1f)
                            val t = (p / 0.62f).coerceIn(0f, 1f)
                            val startX = cx + ring.controlOffsetX * 0.45f
                            val startY = size.height + 24f * scale
                            val ctrlX = cx + ring.controlOffsetX * 1.35f
                            val ctrlY = cy + 40f * scale

                            val inv = 1f - t
                            val orbX = inv * inv * startX + 2f * inv * t * ctrlX + t * t * targetX
                            val orbY = inv * inv * startY + 2f * inv * t * ctrlY + t * t * targetY

                            // Outer glow halo
                            drawCircle(
                                color = ring.color.copy(alpha = 0.35f),
                                radius = 13f * scale,
                                center = Offset(orbX, orbY),
                            )
                            // Bright core orb
                            drawCircle(
                                color = ring.color,
                                radius = 6.5f * scale,
                                center = Offset(orbX, orbY),
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 3f * scale,
                                center = Offset(orbX, orbY),
                            )
                        } else {
                            // Impact shockwave phase (0.62f..1f)
                            val impactT = ((p - 0.62f) / 0.38f).coerceIn(0f, 1f)
                            val rippleAlpha = (1f - impactT).coerceIn(0f, 1f)

                            // Expanding impact ring at the arc tip
                            drawCircle(
                                color = ring.color.copy(alpha = rippleAlpha * 0.75f),
                                radius = (8f + 26f * impactT) * scale,
                                center = Offset(targetX, targetY),
                                style = Stroke(width = 2.5f * scale),
                            )
                            // Circumferential ring surge glow
                            drawArc(
                                color = ring.color.copy(alpha = rippleAlpha * 0.45f),
                                startAngle = -90f,
                                sweepAngle = ring.sweepDeg,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style =
                                    Stroke(
                                        width = (16f + 12f * impactT) * scale,
                                        cap = StrokeCap.Round,
                                    ),
                            )
                        }
                    }
                }
            }

            // Center button (.fv2-mid: 122px radial circle)
            val centerBg =
                if (colors.isLight) {
                    Brush.radialGradient(listOf(Color(0xFFFFFFFF), Color(0xFFF1F5F9)))
                } else {
                    Brush.radialGradient(listOf(Color(0xFA121B2A), Color(0xFA0B101A)))
                }

            Column(
                modifier =
                    Modifier.size(116.dp)
                        .scale(centerScale)
                        .clip(CircleShape)
                        .background(centerBg)
                        .border(
                            width = if (isBurstActive) 1.5.dp else 1.dp,
                            color = if (isBurstActive) colors.accent else colors.borderSubtle,
                            shape = CircleShape,
                        )
                        .clickable(onClick = onOpenGoalSheet)
                        .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "$animatedCal",
                    color = colors.textPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.8).sp,
                )
                Text(
                    text = "of ${goals.calGoal} kcal",
                    color = colors.textSubtle,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isOverCal) "${kotlin.math.abs(calDiff)} over" else "$calDiff left",
                    color = if (isOverCal) colors.statusBad else colors.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3 Macro summary tiles (.fv2-macros > .fv2-mt)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MacroTileItem(
                label = "Protein",
                valueG = totals.pro,
                goalG = goals.proGoal,
                dotColor = colors.macroProtein,
                addedDeltaG = if (isBurstActive) burstEvent?.addedPro else null,
                modifier = Modifier.weight(1f).clickable(onClick = onOpenGoalSheet),
            )
            MacroTileItem(
                label = "Carbs",
                valueG = totals.carb,
                goalG = goals.carbGoal,
                dotColor = colors.macroCarbs,
                addedDeltaG = if (isBurstActive) burstEvent?.addedCarb else null,
                modifier = Modifier.weight(1f).clickable(onClick = onOpenGoalSheet),
            )
            MacroTileItem(
                label = "Fat",
                valueG = totals.fat,
                goalG = goals.fatGoal,
                dotColor = colors.macroFat,
                addedDeltaG = if (isBurstActive) burstEvent?.addedFat else null,
                modifier = Modifier.weight(1f).clickable(onClick = onOpenGoalSheet),
            )
        }
    }
}

@Composable
private fun MacroTileItem(
    label: String,
    valueG: Double,
    goalG: Int,
    dotColor: Color,
    addedDeltaG: Double? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalLifeMaxNutritionColors.current
    val safeGoal = max(1, goalG)
    val ratio = valueG / safeGoal.toDouble()
    val fillFraction by
        animateFloatAsState(
            targetValue = ratio.coerceIn(0.0, 1.0).toFloat(),
            animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            label = "tileFill",
        )
    val overG = (valueG - safeGoal).roundToInt()
    val leftG = max(0, (safeGoal - valueG).roundToInt())
    val showDelta = addedDeltaG != null && addedDeltaG > 0.0

    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surfaceSunken)
                .border(
                    width = 1.dp,
                    color = if (showDelta) dotColor.copy(alpha = 0.65f) else colors.borderSubtle,
                    shape = RoundedCornerShape(14.dp),
                )
                .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dotColor))
                Text(
                    text = label,
                    color = colors.textMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showDelta) {
                Text(
                    text = "+${addedDeltaG!!.roundToInt()}g",
                    color = dotColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "${valueG.roundToInt()}g",
                color = colors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = "/ ${goalG}g",
                color = colors.textSubtle,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(if (colors.isLight) Color(0x140F172A) else Color(0x14FFFFFF))
        ) {
            Box(
                modifier =
                    Modifier.fillMaxWidth(fillFraction)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(dotColor)
            )
        }

        Spacer(modifier = Modifier.height(5.dp))

        Text(
            text = if (overG > 0) "${overG}g over" else "${leftG}g left",
            color = if (overG > 0) colors.statusBad else colors.textSubtle,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
