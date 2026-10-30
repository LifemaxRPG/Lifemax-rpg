package com.lifemax.rpg.feature.lifedirector.presentation.components

import android.graphics.BlurMaskFilter
import android.graphics.Matrix
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/*
 * How Life Director's surfaces are painted: card slices that join across list items, the CSS
 * gradients and washes of style.css, and its blurred shadows and glows.
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
 * A CSS linear-gradient at [degrees] (0 points up, 90 right) over whatever box it fills, its line
 * long enough that the corners take the end colours, as in CSS.
 */
fun cssLinearGradient(degrees: Float, vararg stops: Pair<Float, Color>): Brush =
    object : ShaderBrush() {
        override fun createShader(size: Size): Shader {
            val t = Math.toRadians(degrees.toDouble())
            val direction = Offset(sin(t).toFloat(), -cos(t).toFloat())
            val length = abs(size.width * direction.x) + abs(size.height * direction.y)
            val half = direction * (length / 2)
            return LinearGradientShader(
                size.center - half,
                size.center + half,
                stops.map { it.second },
                stops.map { it.first },
            )
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

/**
 * A CSS box-shadow: this rounded box, blurred by [blur] in [color], [dy] lower, behind it. None
 * where the blur cannot be drawn: unblurred, it would be a slab under the box.
 */
fun Modifier.softShadow(color: Color, blur: Dp, corner: Dp, dy: Dp = 0.dp): Modifier {
    if (!CAN_BLUR) return this
    return drawWithCache {
        val paint = glowPaint(color, blur.toPx())
        val r = corner.toPx()
        val drop = dy.toPx()
        onDrawBehind {
            drawIntoCanvas {
                it.drawRoundRect(0f, drop, size.width, size.height + drop, r, r, paint)
            }
        }
    }
}

/**
 * A paint that blurs what it draws as a CSS shadow of [blurPx] does: CSS blurs with a deviation of
 * half the blur, and Android turns a radius r into a deviation of 0.57735 r + 0.5. Before Android 9
 * the blur is ignored, which is harmless only where the shape lies under what it lights.
 */
fun glowPaint(color: Color, blurPx: Float): Paint =
    Paint().apply {
        this.color = color
        asFrameworkPaint().maskFilter =
            BlurMaskFilter(
                ((blurPx / 2 - .5f) / .57735f).coerceAtLeast(.5f),
                BlurMaskFilter.Blur.NORMAL,
            )
    }

/** Whether the hardware renderer draws a blur mask (Android 9 and later). */
private val CAN_BLUR = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
