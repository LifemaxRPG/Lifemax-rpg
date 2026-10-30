package com.lifemax.rpg.feature.recipes.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.boxShadow
import com.lifemax.rpg.core.designsystem.components.SurfaceCard
import com.lifemax.rpg.core.designsystem.components.tapTarget

/** The page's .card at phone width: its soft border and drop shadow, and [padding] (15 dp). */
@Composable
internal fun RecipesCard(
    modifier: Modifier = Modifier,
    padding: Dp = 15.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalRecipesColors.current
    val shadow =
        if (c.light)
            Modifier.boxShadow(CardShadowLightFar, 10.dp, CardShape, 2.dp)
                .boxShadow(CardShadowLightNear, 2.dp, CardShape, 1.dp)
        else Modifier.boxShadow(CardShadowDark, 8.dp, CardShape, 2.dp)
    SurfaceCard(modifier.then(shadow), PaddingValues(padding), c.lineSoft, content)
}

/** The corners of SurfaceCard, which the shadow follows. */
private val CardShape = RoundedCornerShape(14.dp)

/** The page's accent caption (.eyebrow), upper case. */
@Composable
internal fun RecipesEyebrow(text: String) {
    Text(
        text.uppercase(),
        color = LocalRecipesColors.current.accent,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.32.sp,
        lineHeight = 16.5.sp,
    )
}

/** The page's small bordered label (.tag), on one line. */
@Composable
internal fun RecipesTag(text: String) {
    val c = LocalRecipesColors.current
    val shape = RoundedCornerShape(7.dp)
    Text(
        text,
        Modifier.clip(shape)
            .background(c.tagFill)
            .border(1.dp, c.line, shape)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        color = c.tagText,
        fontSize = 11.5.sp,
        lineHeight = 17.25.sp,
        softWrap = false,
    )
}

/**
 * The page's primary button (.btn): accent, with a light top edge and, in the light theme, a glow.
 * It is at least 48 dp tall, where the page's is 41. [label] is what TalkBack says: [text] without
 * its symbols.
 */
@Composable
internal fun RecipesButton(
    text: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalRecipesColors.current
    Box(
        modifier
            .then(
                if (c.light) Modifier.boxShadow(ButtonGlowLight, 8.dp, ButtonShape, 2.dp)
                else Modifier
            )
            .clip(ButtonShape)
            .background(c.accent)
            .drawBehind { drawRect(c.buttonEdge, size = Size(size.width, 1.dp.toPx())) }
            .semantics { contentDescription = label }
            .tapTarget(Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = c.onAccent,
            fontSize = 14.sp,
            fontWeight = FontWeight(650),
            lineHeight = 21.sp,
            softWrap = false,
        )
    }
}

private val ButtonShape = RoundedCornerShape(8.dp)

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
