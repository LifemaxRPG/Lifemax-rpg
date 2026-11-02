package com.lifemax.rpg.feature.journal.presentation.body

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lifemax.rpg.core.designsystem.cssLinearGradient
import com.lifemax.rpg.core.designsystem.glowPaint

/*
 * The Journal's CSS boxes drawn as the page draws them: glows with a spread (box-shadow
 * x y blur spread), a border with a thicker coloured left edge, a dashed border and the entry's
 * card.
 */

/**
 * CSS box-shadow [dx] [dy] [blur] [spread] [color] around a box with corners of [radius]. When
 * [clipOut] the glow is not drawn under the box, as a browser draws it, so a translucent box (a
 * cell, a node, a tinted card) is not brightened by its own shadow.
 */
internal fun Modifier.glow(
    color: Color,
    blur: Dp,
    radius: Dp,
    spread: Dp = 0.dp,
    dx: Dp = 0.dp,
    dy: Dp = 0.dp,
    clipOut: Boolean = true,
): Modifier = drawWithCache {
    val s = spread.toPx()
    val r = radius.toPx()
    val paint = glowPaint(color, blur.toPx())
    val box = roundPath(Offset.Zero, size, r)
    val shadow =
        RoundRect(
            -s + dx.toPx(),
            -s + dy.toPx(),
            size.width + s + dx.toPx(),
            size.height + s + dy.toPx(),
            CornerRadius((r + s).coerceAtLeast(0f)),
        )
    onDrawBehind {
        val draw = { drawIntoCanvas { it.drawPath(Path().apply { addRoundRect(shadow) }, paint) } }
        if (clipOut) clipPath(box, ClipOp.Difference) { draw() } else draw()
    }
}

/** A solid ring [width] wide outside a box of corner [radius] (box-shadow 0 0 0 width color). */
internal fun Modifier.ring(color: Color, width: Dp, radius: Dp): Modifier = drawWithCache {
    val w = width.toPx()
    val r = radius.toPx()
    val outer = roundPath(Offset(-w, -w), Size(size.width + 2 * w, size.height + 2 * w), r + w)
    val ring = Path().apply { op(outer, roundPath(Offset.Zero, size, r), PathOperation.Difference) }
    onDrawBehind { drawPath(ring, color) }
}

private fun roundPath(at: Offset, size: Size, r: Float) =
    Path().apply {
        addRoundRect(RoundRect(at.x, at.y, at.x + size.width, at.y + size.height, CornerRadius(r)))
    }

/**
 * A CSS border of 1 dp in [color] with a left edge [left] wide in [leftColor], around corners of
 * [radius]: the left colour takes the corners up to the line through the outer and inner corners,
 * as a browser joins two border colours.
 */
internal fun Modifier.leftEdgeBorder(
    color: Color,
    leftColor: Color,
    left: Dp,
    radius: Dp,
): Modifier = drawWithCache {
    val b = 1.dp.toPx()
    val l = left.toPx()
    val r = radius.toPx()
    val w = size.width
    val h = size.height
    val outer = roundPath(Offset.Zero, size, r)
    val inner =
        Path().apply {
            addRoundRect(
                RoundRect(
                    left = l,
                    top = b,
                    right = w - b,
                    bottom = h - b,
                    topLeftCornerRadius = CornerRadius(r - l, r - b),
                    topRightCornerRadius = CornerRadius(r - b),
                    bottomRightCornerRadius = CornerRadius(r - b),
                    bottomLeftCornerRadius = CornerRadius(r - l, r - b),
                )
            )
        }
    val ring = Path().apply { op(outer, inner, PathOperation.Difference) }
    // the joins run from the outer corner through the inner one (slope b / l), out past the curve
    val reach = r * 2
    val side =
        Path().apply {
            moveTo(0f, 0f)
            lineTo(reach, reach * b / l)
            lineTo(reach, h - reach * b / l)
            lineTo(0f, h)
            close()
        }
    onDrawBehind {
        drawPath(ring, color)
        clipPath(side) { drawPath(ring, leftColor) }
    }
}

/** A 1 dp dashed CSS border (border: 1px dashed) around corners of [radius]. */
internal fun Modifier.dashedBorder(color: Color, radius: Dp): Modifier = drawWithCache {
    val b = 1.dp.toPx()
    val stroke = Stroke(b, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3 * b, 3 * b), 0f))
    val path =
        roundPath(
            Offset(b / 2, b / 2),
            Size(size.width - b, size.height - b),
            radius.toPx() - b / 2,
        )
    onDrawBehind { drawPath(path, color, style = stroke) }
}

/** CSS box-shadow inset 0 1px 0 [color]: a line of light along the inside of the top edge. */
internal fun Modifier.insetTopEdge(color: Color, radius: Dp): Modifier = drawWithCache {
    val r = radius.toPx()
    val box = roundPath(Offset.Zero, size, r)
    val lower = roundPath(Offset(0f, 1.dp.toPx()), size, r)
    val edge = Path().apply { op(box, lower, PathOperation.Difference) }
    onDrawWithContent {
        drawContent()
        drawPath(edge, color)
    }
}

/** The page's --shadow (or --shadow-sm when [small]) under a box with corners of [radius]. */
internal fun Modifier.journalShadow(c: JournalBodyColors, radius: Dp, small: Boolean = false) =
    if (small) glow(c.shadowSmall, c.shadowSmallBlur.dp, radius, dy = 2.dp, clipOut = false)
    else glow(c.shadowFar, c.shadowFarBlur.dp, radius, dy = 8.dp, clipOut = false)

/**
 * The entry's card (#view-history .journal-entry): the panel fading to the raised ground, its
 * border and shadow, with the page's padding inside the border.
 */
@Composable
internal fun JournalEntryCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalJournalBodyColors.current
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier
            .journalShadow(c, 24.dp)
            .clip(shape)
            .background(cssLinearGradient(180f, 0f to c.panel, 1f to c.bgElev))
            .border(1.dp, c.line, shape)
    ) {
        Column(
            Modifier.padding(start = 19.dp, end = 19.dp, top = 21.dp, bottom = 23.dp),
            content = content,
        )
    }
}
