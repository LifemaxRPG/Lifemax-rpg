package com.lifemax.rpg.feature.lifedirector.presentation.components

import android.graphics.Matrix
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * How Life Director's surfaces are painted: card slices that join across list items, and the
 * washes of style.css. Its shadows, glows and linear gradients are the core ones (Css.kt).
 */

/** Which part of a card a list item is, so a card can span several items of a lazy list. */
enum class Slice {
    WHOLE,
    TOP,
    MIDDLE,
    BOTTOM,
}

/**
 * Draws this item's part of a rounded card: the corners of the parts it continues into are pushed
 * out of sight, so the parts join into one card. [washes] are painted over the fill, inside it.
 */
fun Modifier.cardSlice(
    slice: Slice,
    radius: Dp,
    fill: Brush,
    border: Color,
    washes: List<Brush> = emptyList(),
): Modifier = drawWithCache {
    val r = radius.toPx()
    val above = if (slice == Slice.MIDDLE || slice == Slice.BOTTOM) 2 * r else 0f
    val below = if (slice == Slice.TOP || slice == Slice.MIDDLE) 2 * r else 0f
    val hairline = 1.dp.toPx()
    val inset = hairline / 2
    val outline =
        Path().apply {
            val box = Rect(inset, inset - above, size.width - inset, size.height + below - inset)
            addRoundRect(RoundRect(box, CornerRadius(r)))
        }
    val edge = Stroke(hairline)
    onDrawBehind {
        clipRect {
            drawPath(outline, fill)
            washes.forEach { drawPath(outline, it) }
            drawPath(outline, border, style = edge)
        }
    }
}

/**
 * A CSS radial-gradient ellipse from [color] to transparent at 60% of its radii. Centre and radii
 * are in widths of the box it fills, so it keeps its shape at any width.
 */
fun ellipseWash(cx: Float, cy: Float, rx: Float, ry: Float, color: Color): Brush =
    object : ShaderBrush() {
        override fun createShader(size: Size): Shader {
            val w = size.width
            val centre = Offset(cx * w, cy * w)
            return RadialGradientShader(
                    centre,
                    rx * w,
                    listOf(color, color.copy(alpha = 0f)),
                    listOf(0f, .6f),
                )
                .apply {
                    setLocalMatrix(Matrix().apply { setScale(1f, ry / rx, centre.x, centre.y) })
                }
        }
    }

/**
 * Lets content run [by] past both sides of its parent's padding, as the web's negative margins do.
 */
fun Modifier.bleed(by: Dp): Modifier = layout { measurable, constraints ->
    val extra = by.roundToPx() * 2
    val placeable =
        measurable.measure(
            constraints.copy(
                minWidth = constraints.minWidth + extra,
                maxWidth = constraints.maxWidth + extra,
            )
        )
    layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
}
