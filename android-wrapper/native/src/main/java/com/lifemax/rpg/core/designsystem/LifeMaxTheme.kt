package com.lifemax.rpg.core.designsystem

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/** Runtime-selected CSS tokens remain authoritative during the UI migration. */
@Composable
fun LifeMaxTheme(tokens: Map<String, Long>, light: Boolean, content: @Composable () -> Unit) {
    fun c(key: String) = Color((tokens[key] ?: 0xffeef1f6).toInt())
    val colors =
        if (light)
            lightColorScheme(
                background = c("bg"),
                surface = c("panel"),
                onSurface = c("text"),
                onBackground = c("text"),
                primary = c("accent"),
                outline = c("line"),
                secondary = c("accent"),
                secondaryContainer = c("accent").copy(alpha = .16f),
                onSecondaryContainer = c("text"),
                onSurfaceVariant = c("muted"),
            )
        else
            darkColorScheme(
                background = c("bg"),
                surface = c("panel"),
                onSurface = c("text"),
                onBackground = c("text"),
                primary = c("accent"),
                outline = c("line"),
                secondary = c("accent"),
                secondaryContainer = c("accent").copy(alpha = .16f),
                onSecondaryContainer = c("text"),
                onSurfaceVariant = c("muted"),
            )
    MaterialTheme(colorScheme = colors) {
        CompositionLocalProvider(LocalContentColor provides colors.onBackground, content = content)
    }
}
