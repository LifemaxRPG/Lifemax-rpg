package com.lifemax.rpg.feature.overview.presentation.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.LocalPageColors
import com.lifemax.rpg.core.designsystem.boxShadow
import com.lifemax.rpg.core.designsystem.components.PixelIcon
import com.lifemax.rpg.core.designsystem.components.tapTarget
import com.lifemax.rpg.core.designsystem.glowPaint
import com.lifemax.rpg.core.designsystem.mix
import com.lifemax.rpg.feature.overview.domain.EditorRow
import com.lifemax.rpg.feature.overview.domain.EditorWords

/*
 * One editor row as the page draws it (.ov-er in 03-features.css): the tile's colour bar, its icon,
 * its name and line, the eye and the grip. Lifting is the caller's (scale, offset); the row draws
 * its shadow from [lift].
 */

@Composable
internal fun EditorRowView(
    row: EditorRow,
    words: EditorWords,
    lift: Float,
    dimmed: Boolean,
    onToggle: () -> Unit,
    grip: Modifier,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    val tile = Color(row.color.toInt())
    val off = row.hidden
    Row(
        modifier
            .graphicsAlpha(if (dimmed) 0.82f else 1f)
            .then(
                if (lift > 0f)
                    Modifier.boxShadow(
                        Lifted.copy(alpha = Lifted.alpha * lift),
                        34.dp,
                        RowShape,
                        14.dp,
                    )
                else Modifier
            )
            .clip(RowShape)
            .background(c.panel2)
            .border(1.dp, if (lift > 0f) tile.mix(0.6f, c.lineSoft) else c.lineSoft, RowShape)
            .drawBehind {
                // the ::before bar: 3 px of the tile's colour with its 10 px glow
                val a = if (off) 0.15f else 1f
                val bar = Size(3.dp.toPx(), size.height)
                drawIntoCanvas {
                    it.drawRect(
                        0f,
                        0f,
                        bar.width,
                        bar.height,
                        glowPaint(tile.copy(alpha = a), 10.dp.toPx()),
                    )
                }
                drawRect(tile, Offset.Zero, bar, a)
            }
            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RowIcon(row.icon, tile, off)
        Column(Modifier.weight(1f)) {
            Text(
                row.name,
                color = if (off) c.muted else c.text,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 18.125.sp,
            )
            Text(
                if (off) words.hiddenSub else row.sub,
                color = c.muted,
                fontSize = 12.sp,
                lineHeight = 16.2.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Eye(!off, (if (off) words.show else words.hide) + row.name, onToggle)
        Grip(if (lift > 0f) c.text else c.muted, grip)
    }
}

/** .ov-er-ic: the tile's icon on a tinted tile; grey and faded when the tile is hidden. */
@Composable
private fun RowIcon(icon: String, tile: Color, off: Boolean) {
    val c = LocalPageColors.current
    // filter: grayscale(1) on the icon and its tile, then opacity .5
    val ink = if (off) tile.grey() else tile
    val fill = tile.mix(0.15f, c.panel).let { if (off) it.grey() else it }
    val edge = tile.copy(alpha = 0.4f).let { if (off) it.grey() else it }
    Box(
        Modifier.graphicsAlpha(if (off) 0.5f else 1f)
            .size(36.dp)
            .clip(IconShape)
            .background(fill)
            .border(1.dp, edge, IconShape),
        contentAlignment = Alignment.Center,
    ) {
        PixelIcon(icon, ink, Modifier.size(16.dp))
    }
}

/**
 * .ov-er-eye: shows or hides the tile; a toggle button for TalkBack, as the page's aria-pressed.
 */
@Composable
private fun Eye(on: Boolean, label: String, onToggle: () -> Unit) {
    val c = LocalPageColors.current
    val eye = remember { PathParser().parsePathString(EYE).toPath() }
    Box(
        Modifier.size(40.dp)
            .clip(IconShape)
            .semantics { contentDescription = label }
            .tapTarget(Role.Button, onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(20.dp).graphicsAlpha(if (on) 0.75f else 0.5f)) {
            // the SVG's stroke-width 2 is in its 24-unit box, so it scales with it
            val stroke = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            scale(size.width / 24, Offset.Zero) {
                drawPath(eye, c.text, style = stroke)
                drawCircle(c.text, 3f, Offset(12f, 12f), style = stroke)
                if (!on) drawLine(c.text, Offset(3f, 3f), Offset(21f, 21f), 2f, StrokeCap.Round)
            }
        }
    }
}

/** .ov-er-grip: three bars. [modifier] carries the drag; TalkBack moves the row by its actions. */
@Composable
private fun Grip(ink: Color, modifier: Modifier) {
    Box(modifier.size(44.dp).clip(IconShape).clearAndSetSemantics {}, Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) { Box(Modifier.width(18.dp).height(2.dp).clip(BarShape).background(ink)) }
        }
    }
}

/** CSS grayscale(1): the colour's luminance as a grey, keeping its alpha. */
private fun Color.grey(): Color {
    val l = 0.2126f * red + 0.7152f * green + 0.0722f * blue
    return Color(l, l, l, alpha)
}

private fun Modifier.graphicsAlpha(a: Float): Modifier =
    if (a >= 1f) this else graphicsLayer { alpha = a }

private val RowShape = RoundedCornerShape(14.dp)
private val IconShape = RoundedCornerShape(11.dp)
private val BarShape = RoundedCornerShape(2.dp)

/** The lifted row's drop shadow (#000a). */
private val Lifted = Color(0xAA000000)

/** The page's eye (ovEye), in its 24-unit box. */
private const val EYE = "M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z"
