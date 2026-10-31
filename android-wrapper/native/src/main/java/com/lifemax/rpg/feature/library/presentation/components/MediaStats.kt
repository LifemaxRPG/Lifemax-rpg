package com.lifemax.rpg.feature.library.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.components.ProgressBar
import com.lifemax.rpg.core.designsystem.glowPaint
import com.lifemax.rpg.feature.library.domain.MediaStat

/** The four stat cards, two to a row as on a phone, each row as tall as its taller card. */
@Composable
internal fun MediaStats(stats: List<MediaStat>) {
    Column(Modifier.padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        stats.chunked(2).forEach { pair ->
            Row(
                Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pair.forEach { StatCard(it, Modifier.weight(1f).fillMaxHeight()) }
            }
        }
    }
}

@Composable
private fun StatCard(stat: MediaStat, modifier: Modifier) {
    val c = LocalLibraryColors.current
    LibraryCard(modifier.semantics(mergeDescendants = true) {}) {
        Text(
            stat.label,
            color = c.muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 18.sp,
        )
        Text(
            stat.value,
            Modifier.padding(top = 8.dp, bottom = 4.dp),
            color = c.text,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.66).sp,
            lineHeight = 33.sp,
        )
        if (stat.foot.isNotEmpty())
            Text(stat.foot, color = c.muted2, fontSize = 12.sp, lineHeight = 18.sp)
        stat.bar?.let { pct ->
            ProgressBar(
                pct / 100f,
                c.completion,
                Modifier.padding(top = 4.dp).fillGlow(pct / 100f, CompletionGlow),
                height = 7.dp,
                track = c.barTrack,
                outline = c.lineSoft,
                inset = 1.dp,
            )
        }
    }
}

/**
 * The glow around the page's bar fill (box-shadow 0 0 12px), which shows inside the bar past the
 * fill's end. The bar has a 1 dp outline with the fill inside it.
 */
private fun Modifier.fillGlow(fraction: Float, color: Color): Modifier = drawWithCache {
    val inset = 1.dp.toPx()
    val end = inset + (size.width - 2 * inset) * fraction.coerceIn(0f, 1f)
    val radius = size.height / 2 - inset
    val inside =
        Path().apply {
            addRoundRect(
                RoundRect(
                    inset,
                    inset,
                    size.width - inset,
                    size.height - inset,
                    CornerRadius(radius),
                )
            )
        }
    val paint = glowPaint(color, 12.dp.toPx())
    onDrawWithContent {
        drawContent()
        clipPath(inside) {
            clipRect(left = end) {
                drawIntoCanvas {
                    it.drawRoundRect(inset, inset, end, size.height - inset, radius, radius, paint)
                }
            }
        }
    }
}
