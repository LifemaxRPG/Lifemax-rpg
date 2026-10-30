package com.lifemax.rpg.feature.plan.presentation.components

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The page's small bordered label (.tag). */
@Composable
internal fun PlanTag(text: String, modifier: Modifier = Modifier, weight: FontWeight? = null) {
    val c = LocalPlanColors.current
    val shape = RoundedCornerShape(7.dp)
    Text(
        text,
        modifier
            .clip(shape)
            .background(c.tagFill)
            .border(1.dp, c.line, shape)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        color = c.tagText,
        fontSize = 11.5.sp,
        fontWeight = weight,
        lineHeight = 17.25.sp,
    )
}

/**
 * CSS box-shadow 0 [y] [blur] [color] under a box with [corner] rounding (half its size for a
 * circle). The shadow is also drawn under the box, which hides it as long as the box is opaque.
 */
internal fun Modifier.boxShadow(color: Color, blur: Dp, corner: Dp, y: Dp = 0.dp): Modifier =
    drawWithCache {
        // CSS blurs with sigma blur/2; an Android blur radius r has sigma 0.57735 r + 0.5
        val radius = ((blur.toPx() / 2 - 0.5f) / 0.57735f).coerceAtLeast(0.5f)
        val paint =
            Paint().also {
                it.color = color
                it.asFrameworkPaint().maskFilter =
                    BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
            }
        val r = corner.toPx()
        val dy = y.toPx()
        onDrawBehind {
            drawIntoCanvas { it.drawRoundRect(0f, dy, size.width, size.height + dy, r, r, paint) }
        }
    }
