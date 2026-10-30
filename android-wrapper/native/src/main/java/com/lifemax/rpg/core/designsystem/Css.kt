package com.lifemax.rpg.core.designsystem

import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
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
 * CSS box-shadow 0 [dy] [blur] [color] under a box of [shape]. The shadow is also drawn under the
 * box, which hides it as long as the box is opaque. None before Android 9, which cannot blur it:
 * unblurred, it would be a slab under the box.
 */
fun Modifier.boxShadow(color: Color, blur: Dp, shape: Shape, dy: Dp = 0.dp): Modifier =
    if (!CAN_BLUR) this
    else
        drawWithCache {
            val paint = glowPaint(color, blur.toPx())
            val outline = shape.createOutline(size, layoutDirection, this)
            val y = dy.toPx()
            onDrawBehind {
                translate(top = y) { drawIntoCanvas { it.drawOutline(outline, paint) } }
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

/** CSS color-mix(in srgb, this [amount], [other]). */
fun Color.mix(amount: Float, other: Color): Color =
    Color(
        red = red * amount + other.red * (1 - amount),
        green = green * amount + other.green * (1 - amount),
        blue = blue * amount + other.blue * (1 - amount),
        alpha = alpha * amount + other.alpha * (1 - amount),
    )
