package com.lifemax.rpg.feature.recipes.presentation.components

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.lifemax.rpg.core.designsystem.components.tokenColor

/**
 * The Recipes view's colours: the page's palette tokens, and the light theme's own rules (white
 * fields and button text, paler tags).
 */
@Immutable
internal class RecipesColors(tokens: Map<String, Long>, val light: Boolean) {
    val bg = tokenColor(tokens, "bg")
    val bgElev = tokenColor(tokens, "bg-elev")
    val panel = tokenColor(tokens, "panel")
    val panel2 = tokenColor(tokens, "panel2")
    val text = tokenColor(tokens, "text")
    val textDim = tokenColor(tokens, "text-dim")
    val muted = tokenColor(tokens, "muted")
    val accent = tokenColor(tokens, "accent")
    val accentDim = tokenColor(tokens, "accent-dim")
    val accentStrong = tokenColor(tokens, "accent-strong")
    val line = tokenColor(tokens, "line")
    val lineSoft = tokenColor(tokens, "line-soft")

    val field = if (light) Color.White else bg
    val tagFill = if (light) panel2 else bgElev
    val tagText = if (light) textDim else muted
    val onAccent = if (light) Color.White else OnAccentDark
    val buttonEdge = if (light) ButtonEdgeLight else ButtonEdgeDark

    private companion object {
        val OnAccentDark = Color(0xFF0B1020)
        val ButtonEdgeDark = Color(0x22FFFFFF)
        val ButtonEdgeLight = Color(0x44FFFFFF)
    }
}

internal val LocalRecipesColors =
    staticCompositionLocalOf<RecipesColors> { error("No recipes colours") }

/**
 * Colours the page's styles fix: the cards' drop shadows (one layer in the dark theme, two in the
 * light), the light theme's glow under a button, the tint of an ingredient (its own variables are
 * undefined, so the page draws the fallback) and the browser's placeholder grey.
 */
internal val CardShadowDark = Color(0x40000000)
internal val CardShadowLightNear = Color(0x081A2744)
internal val CardShadowLightFar = Color(0x0C1A2744)
internal val ButtonGlowLight = Color(0x333B5BDB)
internal val IngredientFill = Color(0x147F7F7F)
internal val Placeholder = Color(0xFF757575)
