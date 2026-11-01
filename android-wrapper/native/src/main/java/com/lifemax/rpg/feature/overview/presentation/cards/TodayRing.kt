package com.lifemax.rpg.feature.overview.presentation.cards

import android.graphics.Paint as FrameworkPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.cssBlurRadius
import com.lifemax.rpg.core.designsystem.glowPaint
import com.lifemax.rpg.feature.overview.domain.HeroRing
import com.lifemax.rpg.feature.overview.presentation.LocalOverviewArrival
import com.lifemax.rpg.feature.overview.presentation.LocalOverviewColors
import com.lifemax.rpg.feature.overview.presentation.OverviewArrival
import com.lifemax.rpg.feature.overview.presentation.argb

/**
 * Today's ring (.ov-ring): the track, then one glowing arc per attribute as the page's SVG draws
 * them (r 62 and stroke 11 in a 148 square, pathLength 100, from twelve o'clock clockwise, round
 * caps), and in the middle "TODAY", today's XP (gold and glowing over the pace) and the pace line.
 */
@Composable
internal fun TodayRing(ring: HeroRing, modifier: Modifier = Modifier) {
    val c = LocalOverviewColors.current
    val density = LocalDensity.current.density
    val arrival = LocalOverviewArrival.current
    Box(modifier.size(RING_DP.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val unit = size.minDimension / RING_DP
            drawCircle(c.line.copy(alpha = 0.9f), R * unit, style = Stroke(STROKE * unit))
        }
        // the arcs (.ov-segs) sweep in on an arrival: from -130 degrees and scale .92, faded in,
        // about the ring's centre; still, they are drawn as they are
        Canvas(
            Modifier.fillMaxSize()
                .then(
                    if (arrival == null) Modifier
                    else
                        Modifier.graphicsLayer {
                            val p = OverviewArrival.ring(arrival.ms)
                            rotationZ = -130f * (1f - p)
                            scaleX = .92f + .08f * p
                            scaleY = scaleX
                            alpha = p
                        }
                )
        ) {
            val unit = size.minDimension / RING_DP
            val radius = R * unit
            val stroke = STROKE * unit
            val box = Size(radius * 2, radius * 2)
            val topLeft = Offset(center.x - radius, center.y - radius)
            ring.arcs.forEach { arc ->
                val start = -90f - arc.offset * 3.6f
                val sweep = arc.length * 3.6f
                val color = argb(arc.color)
                // drop-shadow(0 0 5px) in the arc's colour at 40 % (--c: color66)
                drawIntoCanvas { canvas ->
                    val paint = glowPaint(color.copy(alpha = 0.4f), 5 * unit)
                    paint.asFrameworkPaint().apply {
                        style = FrameworkPaint.Style.STROKE
                        strokeWidth = stroke
                        strokeCap = FrameworkPaint.Cap.ROUND
                    }
                    canvas.drawArc(
                        topLeft.x,
                        topLeft.y,
                        topLeft.x + box.width,
                        topLeft.y + box.height,
                        start,
                        sweep,
                        false,
                        paint,
                    )
                }
                drawArc(
                    color,
                    start,
                    sweep,
                    false,
                    topLeft,
                    box,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                ring.label,
                color = c.muted,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.8.sp,
                lineHeight = 13.5.sp,
            )
            Text(
                // today's XP counts up on an arrival, as the page's ovReplay does
                (arrival?.let { OverviewArrival.count(ring.today, it.ms) } ?: ring.today)
                    .toString(),
                color = if (ring.over) c.gold else c.text,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1.44).sp,
                lineHeight = 37.8.sp,
                style =
                    Tabular.copy(
                        shadow =
                            if (ring.over)
                                Shadow(
                                    c.gold.copy(alpha = 0.55f),
                                    Offset.Zero,
                                    cssBlurRadius(18 * density),
                                )
                            else null
                    ),
            )
            Text(
                ring.of,
                Modifier.padding(top = 2.dp),
                color = c.textDim,
                fontSize = 11.sp,
                lineHeight = 16.5.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private const val RING_DP = 148f
private const val R = 62f
private const val STROKE = 11f
