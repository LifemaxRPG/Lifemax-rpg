package com.lifemax.rpg.feature.recipes.presentation.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

/**
 * A CSS outline of [width] (or a box-shadow spread as far) around a box with [radius] corners,
 * outside its border.
 */
internal fun Modifier.outline(color: Color, width: Dp, radius: Dp) = drawWithCache {
    val w = width.toPx()
    val stroke = Stroke(w)
    val corner = CornerRadius(radius.toPx() + w / 2)
    onDrawBehind {
        drawRoundRect(
            color,
            Offset(-w / 2, -w / 2),
            Size(size.width + w, size.height + w),
            corner,
            stroke,
        )
    }
}
