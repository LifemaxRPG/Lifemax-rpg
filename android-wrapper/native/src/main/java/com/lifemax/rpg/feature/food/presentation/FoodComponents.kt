package com.lifemax.rpg.feature.food.presentation

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun GlowingDot(color: Color) {
    Box(modifier = Modifier.size(16.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(color = color.copy(alpha = 0.35f), radius = size.minDimension / 2f)
            drawCircle(color = color, radius = 4.5.dp.toPx())
        }
    }
}

@Composable
internal fun MacroTileCard(
    label: String,
    light: Boolean,
    dotColor: Color,
    current: Int,
    goal: Int,
    modifier: Modifier = Modifier,
) {
    val left = goal - current
    val pct = if (goal > 0) (current.toFloat() / goal).coerceIn(0f, 1f) else 0f

    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(18.dp))
                .background(if (light) Color(0xFFF3F6FA) else SunkenTile)
                .border(
                    1.dp,
                    if (light) Color(0xFFD2D9E7) else BorderSubtle,
                    RoundedCornerShape(18.dp),
                )
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            GlowingDot(color = dotColor)
            Text(
                text = label,
                color = if (light) Color(0xFF475569) else TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.1.sp,
                maxLines = 1,
            )
        }

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "$current",
                color = if (light) Color(0xFF0F172A) else Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = if (goal > 0) " / $goal g" else " g",
                color = if (light) Color(0xFF64748B) else TextSubtle,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(if (light) Color(0xFFE4E9F2) else Color(0xFF191E2B))
        ) {
            Box(
                modifier =
                    Modifier.fillMaxHeight()
                        .fillMaxWidth(pct)
                        .clip(CircleShape)
                        .background(dotColor)
            )
        }

        Text(
            text =
                if (goal == 0) "no target"
                else if (left >= 0) "$left g left" else "${-left} g over",
            color = if (light) Color(0xFF64748B) else TextSubtle,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun FoodTopTabButton(
    label: String,
    selected: Boolean,
    light: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier =
            Modifier.clip(shape)
                .background(
                    if (selected) (if (light) Color(0xFFE8EDF9) else PillSelected)
                    else Color.Transparent
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) BlueAccent else if (light) Color(0xFF64748B) else TextMuted,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun HeaderButton(
    label: String,
    width: androidx.compose.ui.unit.Dp?,
    light: Boolean = false,
    onClick: () -> Unit,
) {
    val mod =
        if (width != null) {
            Modifier.size(width, 42.dp)
        } else {
            Modifier.height(42.dp).padding(horizontal = 2.dp)
        }
    Box(
        modifier =
            mod.clip(RoundedCornerShape(14.dp))
                .background(if (light) Color.White else Color(0xFF171B26))
                .border(
                    1.dp,
                    if (light) Color(0xFFD2D9E7) else Color(0x16FFFFFF),
                    RoundedCornerShape(14.dp),
                )
                .clickable(onClick = onClick)
                .padding(horizontal = if (width == null) 14.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color =
                if (label == "Targets") BlueAccent
                else if (light) Color(0xFF182132) else Color(0xFFE2E8F0),
            fontSize = if (label == "Targets") 14.sp else 18.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
internal fun WaterPillButton(label: String, light: Boolean, onClick: () -> Unit) {
    Box(
        modifier =
            Modifier.height(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (light) Color.White else Color(0xFF171B26))
                .border(
                    1.dp,
                    if (light) Color(0xFFD2D9E7) else BorderSubtle,
                    RoundedCornerShape(14.dp),
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (light) Color(0xFF0F172A) else Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
internal fun DashedSlotButton(
    label: String,
    textColor: Color,
    light: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .height(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (light) Color(0xFFF3F6FA) else SunkenTile)
                .border(
                    1.dp,
                    if (light) Color(0xFFD2D9E7) else Color(0x1AFFFFFF),
                    RoundedCornerShape(14.dp),
                )
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun VerticalHairline(color: Color) {
    Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(color))
}
