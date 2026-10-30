package com.lifemax.rpg.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A pill-shaped bar filled with [fill] to [fraction] (clamped to 0..1), over a [track] with a
 * hairline [outline]; pass `Color.Transparent` for none. TalkBack reads it as a progress value.
 */
@Composable
fun ProgressBar(
    fraction: Float,
    fill: Brush,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    track: Color = MaterialTheme.colorScheme.background,
    outline: Color = MaterialTheme.colorScheme.outline.copy(alpha = .5f),
) {
    val filled = fraction.coerceIn(0f, 1f)
    Box(
        modifier
            .progressSemantics(filled)
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(track)
            .border(1.dp, outline, CircleShape)
    ) {
        Box(Modifier.fillMaxWidth(filled).fillMaxHeight().clip(CircleShape).background(fill))
    }
}

/** The web's `.ch-bar` fill: [color] mixed 62 % with black at the left, rising to [color]. */
fun barFade(color: Color): Brush =
    Brush.horizontalGradient(
        listOf(Color(color.red * .62f, color.green * .62f, color.blue * .62f), color)
    )
