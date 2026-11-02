package com.lifemax.rpg.core.designsystem

import android.graphics.BlurMaskFilter
import android.graphics.Matrix
import android.graphics.RadialGradient
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/*
 * The page's CSS effects that Compose has no direct match for, drawn the way a browser draws them,
 * so a native screen can follow the page's styles number for number.
 */

/**
 * CSS box-shadow 0 [dy] [blur] [spread] [color] under a box of [shape]. A [spread] grows (or, below
 * zero, shrinks) the shadow's outline on every side before the blur; the shape keeps its corner
 * radii, where CSS adds the spread to them, which is invisible under the blur at the sizes the page
 * uses. The shadow is drawn under the box too, which hides it as long as the box is opaque; a
 * translucent box (a Journal cell, a timeline node) asks for [clipOutBox], which leaves the box's
 * own area out, as a browser does. None before Android 9, which cannot blur it: unblurred, it would
 * be a slab under the box.
 */
fun Modifier.boxShadow(
    color: Color,
    blur: Dp,
    shape: Shape,
    dy: Dp = 0.dp,
    spread: Dp = 0.dp,
    clipOutBox: Boolean = false,
): Modifier =
    if (!CAN_BLUR) this
    else
        drawWithCache {
            val paint = glowPaint(color, blur.toPx())
            val s = spread.toPx()
            val grown =
                Size(
                    (size.width + 2 * s).coerceAtLeast(0f),
                    (size.height + 2 * s).coerceAtLeast(0f),
                )
            val outline = shape.createOutline(grown, layoutDirection, this)
            val box =
                if (clipOutBox)
                    Path().apply {
                        addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache))
                    }
                else null
            val y = dy.toPx()
            onDrawBehind {
                val draw = {
                    translate(left = -s, top = y - s) {
                        drawIntoCanvas { it.drawOutline(outline, paint) }
                    }
                }
                if (box == null) draw() else clipPath(box, ClipOp.Difference) { draw() }
            }
        }

/**
 * A paint that blurs what it draws as a CSS shadow or glow of [blurPx] does. Before Android 9 the
 * blur is ignored, which is harmless only where the shape lies under what it lights.
 */
fun glowPaint(color: Color, blurPx: Float): Paint =
    Paint().also {
        it.color = color
        it.asFrameworkPaint().maskFilter =
            BlurMaskFilter(cssBlurRadius(blurPx), BlurMaskFilter.Blur.NORMAL)
    }

/** Whether the hardware renderer draws a blur mask (Android 9 and later). */
private val CAN_BLUR = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

/**
 * The Android blur radius (a mask filter's or a text shadow's) that blurs as a CSS blur of [blurPx]
 * does: CSS blurs with a deviation of half the blur, and Android turns a radius r into a deviation
 * of 0.57735 r + 0.5.
 */
fun cssBlurRadius(blurPx: Float): Float = ((blurPx / 2 - 0.5f) / 0.57735f).coerceAtLeast(0.5f)

/**
 * CSS linear-gradient at [degrees] (0 points up, 90 right) through colour [stops] (position 0 to 1
 * to colour). The gradient line runs corner to corner of the box it fills, so it fits any size the
 * way the page's does.
 */
fun cssLinearGradient(degrees: Float, vararg stops: Pair<Float, Color>): Brush =
    object : ShaderBrush() {
        private val dx = sin(Math.toRadians(degrees.toDouble())).toFloat()
        private val dy = -cos(Math.toRadians(degrees.toDouble())).toFloat()

        override fun createShader(size: Size): Shader {
            val half = (abs(size.width * dx) + abs(size.height * dy)) / 2
            val cx = size.width / 2
            val cy = size.height / 2
            return LinearGradientShader(
                Offset(cx - dx * half, cy - dy * half),
                Offset(cx + dx * half, cy + dy * half),
                stops.map { it.second },
                stops.map { it.first },
            )
        }
    }

/**
 * CSS radial-gradient([sizeX] [sizeY] at [atX] [atY], [stops]): an ellipse whose radii are the
 * fractions [sizeX] of the box's width and [sizeY] of its height, centred at the fractions [atX],
 * [atY] of the box, through colour [stops] (position 0 to 1 along the radius, to colour). It fits
 * any size the way the page's does. A stop CSS writes as `transparent` is given as its neighbour's
 * colour at alpha 0, because a browser blends premultiplied and Android does not: fading to
 * transparent black would darken the edge.
 */
fun cssRadialGradient(
    sizeX: Float,
    sizeY: Float,
    atX: Float,
    atY: Float,
    vararg stops: Pair<Float, Color>,
): Brush =
    object : ShaderBrush() {
        override fun createShader(size: Size): Shader {
            val rx = (size.width * sizeX).coerceAtLeast(0.01f)
            val ry = (size.height * sizeY).coerceAtLeast(0.01f)
            val cx = size.width * atX
            val cy = size.height * atY
            return RadialGradient(
                    cx,
                    cy,
                    rx,
                    stops.map { it.second.toArgb() }.toIntArray(),
                    stops.map { it.first }.toFloatArray(),
                    android.graphics.Shader.TileMode.CLAMP,
                )
                .apply { setLocalMatrix(Matrix().apply { setScale(1f, ry / rx, cx, cy) }) }
        }
    }

/** CSS color-mix(in srgb, this [amount], [other]). */
fun Color.mix(amount: Float, other: Color): Color =
    Color(
        red = red * amount + other.red * (1 - amount),
        green = green * amount + other.green * (1 - amount),
        blue = blue * amount + other.blue * (1 - amount),
        alpha = alpha * amount + other.alpha * (1 - amount),
    )
