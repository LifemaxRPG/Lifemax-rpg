package com.lifemax.rpg.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.lifemax.rpg.core.designsystem.components.tokenColor

/**
 * The page's palette tokens, and the light theme's own rules for the page's shared classes (white
 * fields and button text, paler tags), for a screen that follows the page's styles. A screen with
 * colours of its own extends it.
 */
@Immutable
open class PageColors(val tokens: Map<String, Long>, val light: Boolean) {
    val bg = tokenColor(tokens, "bg")
    val bgElev = tokenColor(tokens, "bg-elev")
    val panel = tokenColor(tokens, "panel")
    val panel2 = tokenColor(tokens, "panel2")
    val panel3 = tokenColor(tokens, "panel3")
    val text = tokenColor(tokens, "text")
    val textDim = tokenColor(tokens, "text-dim")
    val muted = tokenColor(tokens, "muted")
    val muted2 = tokenColor(tokens, "muted2")
    val accent = tokenColor(tokens, "accent")
    val accentDim = tokenColor(tokens, "accent-dim")
    val accentStrong = tokenColor(tokens, "accent-strong")
    val line = tokenColor(tokens, "line")
    val lineSoft = tokenColor(tokens, "line-soft")
    val good = tokenColor(tokens, "good")
    val gold = tokenColor(tokens, "gold")
    val bad = tokenColor(tokens, "bad")

    val field = if (light) Color.White else bg
    val tagFill = if (light) panel2 else bgElev
    val tagText = if (light) textDim else muted
    val onAccent = if (light) Color.White else OnAccentDark

    private companion object {
        val OnAccentDark = Color(0xFF0B1020)
    }
}

val LocalPageColors = staticCompositionLocalOf<PageColors> { error("No page colours") }

/**
 * The page's [colors] and type: Material colours from the same tokens, and the page's text as CSS
 * sets it ([CssText]).
 */
@Composable
fun PageTheme(colors: PageColors, content: @Composable () -> Unit) {
    LifeMaxTheme(colors.tokens, colors.light) {
        CompositionLocalProvider(LocalPageColors provides colors) { CssText(content) }
    }
}
