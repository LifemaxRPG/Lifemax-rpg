package com.lifemax.rpg.feature.character.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.feature.character.domain.*
import kotlin.math.*

@Composable
internal fun NativeRadar(
    attrs: List<AttributeProjection>,
    cap: Int,
    compare: Boolean,
    selected: String,
    onSelect: (String) -> Unit,
) {
    // Presentation geometry only: levels/cap/previous values come from the owner.
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        val side = maxWidth
        Canvas(Modifier.fillMaxWidth().height(side)) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension * .30f
            fun point(i: Int, level: Float): Offset {
                val angle = -PI / 2 + i * PI / 4
                val fraction =
                    .1f + .9f * (level.coerceIn(1f, cap.toFloat()) - 1) / (cap - 1).coerceAtLeast(1)
                return center +
                    Offset(
                        cos(angle).toFloat() * radius * fraction,
                        sin(angle).toFloat() * radius * fraction,
                    )
            }
            fun polygon(levels: List<Float>) =
                Path().apply {
                    levels.forEachIndexed { i, l ->
                        val v = point(i, l)
                        if (i == 0) moveTo(v.x, v.y) else lineTo(v.x, v.y)
                    }
                    close()
                }
            for (q in listOf(.25f, .5f, .75f, 1f)) drawPath(
                polygon(List(8) { cap * q }),
                Color(0xff6b7384).copy(alpha = .3f),
                style = Stroke(1.dp.toPx()),
            )
            attrs.forEachIndexed { i, _ ->
                drawLine(Color(0xff6b7384).copy(alpha = .25f), center, point(i, cap.toFloat()))
            }
            if (compare)
                drawPath(
                    polygon(attrs.map { it.previous.toFloat() }),
                    Color(0xffc58bff).copy(alpha = .6f),
                    style =
                        Stroke(
                            1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 5f)),
                        ),
                )
            val path = polygon(attrs.map { it.level.toFloat() })
            drawPath(
                path,
                Brush.radialGradient(listOf(Color(0x197c9cff), Color(0x759b7bff)), center, radius),
            )
            drawPath(
                path,
                Brush.linearGradient(listOf(Color(0xff7ab0ff), Color(0xffc58bff))),
                style = Stroke(2.dp.toPx()),
            )
            attrs.forEachIndexed { i, a ->
                drawCircle(
                    Color(a.color.toInt()),
                    if (a.id == selected) 6.dp.toPx() else 4.dp.toPx(),
                    point(i, a.level.toFloat()),
                )
            }
        }
        attrs.forEachIndexed { i, a ->
            val angle = -PI / 2 + i * PI / 4
            val x = (.5f + cos(angle).toFloat() * .40f)
            val y = (.5f + sin(angle).toFloat() * .40f)
            Column(
                Modifier.offset(x = side * x - 38.dp, y = side * y - 18.dp).width(76.dp).clickable {
                    onSelect(a.id)
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    a.short + if (a.focus) " ★" else "",
                    fontSize = 10.sp,
                    color = Color(a.color.toInt()),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "LV ${a.level}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f),
                )
            }
        }
    }
}
