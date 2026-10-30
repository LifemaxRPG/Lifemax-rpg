package com.lifemax.rpg.feature.plan.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.boxShadow
import com.lifemax.rpg.core.designsystem.components.SurfaceCard

/** The page's .card at phone width: its soft border, its drop shadow and 15 dp padding. */
@Composable
internal fun PlanCard(content: @Composable ColumnScope.() -> Unit) {
    val c = LocalPlanColors.current
    val shadow =
        if (c.light)
            Modifier.boxShadow(CardShadowLightFar, 10.dp, CardShape, 2.dp)
                .boxShadow(CardShadowLightNear, 2.dp, CardShape, 1.dp)
        else Modifier.boxShadow(CardShadowDark, 8.dp, CardShape, 2.dp)
    SurfaceCard(shadow, PaddingValues(15.dp), c.lineSoft, content)
}

/** The corners of SurfaceCard, which the shadow follows. */
private val CardShape = RoundedCornerShape(14.dp)

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
