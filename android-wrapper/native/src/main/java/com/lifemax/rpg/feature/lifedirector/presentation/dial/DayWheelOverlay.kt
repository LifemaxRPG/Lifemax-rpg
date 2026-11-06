package com.lifemax.rpg.feature.lifedirector.presentation.dial

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.lifemax.rpg.feature.lifedirector.domain.StripDay
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorPalette as P

/** Reveals dates on the hour track; the highlighted segment glides between day detents. */
@Composable
internal fun DayWheelOverlay(
    days: List<StripDay>,
    selected: Int,
    shown: Boolean,
    animate: Boolean,
) {
    val opacity by
        animateFloatAsState(
            if (shown) 1f else 0f,
            tween(if (animate) 180 else 0),
            label = "calendar ring",
        )
    val position by
        animateFloatAsState(
            selected.toFloat(),
            tween(if (animate) 100 else 0),
            label = "calendar date",
        )
    val paint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }
    if (days.size < 2 || opacity <= 0f) return
    Canvas(
        Modifier.fillMaxSize().semantics {
            contentDescription = "Choose day: ${days[selected].label} ${days[selected].date}"
        }
    ) {
        val u = size.width / 360f
        val radius = 171f * u
        val width = 14f * u
        val origin = Offset(center.x - radius, center.y - radius)
        val diameter = Size(radius * 2, radius * 2)
        val step = 360f / days.size
        drawCircle(P.bg0.copy(alpha = opacity), radius, style = Stroke(width + 4f * u))
        days.forEachIndexed { i, _ ->
            drawArc(
                P.line.copy(alpha = opacity),
                -90f + i * step - step / 2 + 2f,
                step - 4f,
                false,
                origin,
                diameter,
                style = Stroke(width, cap = StrokeCap.Round),
            )
        }
        drawArc(
            P.task.copy(alpha = opacity * .85f),
            -90f + position * step - step / 2 + 2f,
            step - 4f,
            false,
            origin,
            diameter,
            style = Stroke(width, cap = StrokeCap.Round),
        )
        drawIntoCanvas { canvas ->
            paint.textSize = 9f * u
            paint.isFakeBoldText = true
            days.forEachIndexed { i, day ->
                paint.color = (if (i == selected) P.bg0 else P.ink).copy(alpha = opacity).toArgb()
                val at = DialGeometry.point(171f, i * step) * u
                canvas.nativeCanvas.drawText(
                    "${day.label} ${day.date}",
                    at.x,
                    at.y - (paint.ascent() + paint.descent()) / 2,
                    paint,
                )
            }
        }
    }
}

/** Immutable callbacks read through DialInput so an active pointer never sees stale projections. */
internal class DayWheelInput(
    val count: Int,
    val selected: Int,
    val begin: () -> Unit,
    val preview: (Int) -> Unit,
    val end: (Int?) -> Unit,
)
