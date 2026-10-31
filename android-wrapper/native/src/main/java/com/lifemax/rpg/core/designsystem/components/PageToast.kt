package com.lifemax.rpg.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.boxShadow
import kotlinx.coroutines.delay

/**
 * The page's toast (.toast) with [message], in the page's palette [tokens]: --panel3 with a --line
 * border and a deep shadow, or white with a soft shadow in the [light] theme. A screen lays it over
 * its bottom edge, where the page shows it. [shown] (0 to 1) is how far it has risen into view,
 * read as it is drawn; [playToast] runs it as the page does. TalkBack reads it as it appears.
 */
@Composable
fun PageToast(
    message: String,
    tokens: Map<String, Long>,
    light: Boolean,
    modifier: Modifier = Modifier,
    shown: (() -> Float)? = null,
) {
    val shape = RoundedCornerShape(12.dp)
    Text(
        message,
        modifier
            .then(
                if (shown == null) Modifier
                else
                    Modifier.graphicsLayer {
                        alpha = shown()
                        translationY = (1 - shown()) * 10.dp.toPx()
                    }
            )
            .fillMaxWidth()
            .boxShadow(if (light) ShadowLight else ShadowDark, 40.dp, shape, 12.dp)
            .clip(shape)
            .background(if (light) Color.White else tokenColor(tokens, "panel3"))
            .border(1.dp, tokenColor(tokens, "line"), shape)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = tokenColor(tokens, "text"),
        fontSize = 13.5.sp,
        fontWeight = FontWeight(650),
        lineHeight = 18.sp,
    )
}

/** A toast's life on the page (toast() and .toast): it rises in, stays 2.2 s, and fades away. */
suspend fun Animatable<Float, AnimationVector1D>.playToast() {
    animateTo(1f, tween(FADE_MS))
    delay(STAY_MS)
    animateTo(0f, tween(FADE_MS))
}

private const val FADE_MS = 200
private const val STAY_MS = 2200L
private val ShadowDark = Color(0x88000000)
private val ShadowLight = Color(0x281A2744)
