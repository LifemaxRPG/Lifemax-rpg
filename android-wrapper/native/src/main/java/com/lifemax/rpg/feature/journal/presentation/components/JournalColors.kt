package com.lifemax.rpg.feature.journal.presentation.components

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.lifemax.rpg.core.designsystem.PageColors
import com.lifemax.rpg.core.designsystem.boxShadow
import com.lifemax.rpg.core.designsystem.cssLinearGradient
import com.lifemax.rpg.core.designsystem.cssRadialGradient

/**
 * The Journal's colours (07-finishing.css, "v128 · Journal redesign"): the page's palette, and the
 * colours its rules fix, named here once.
 *
 * The page draws the Journal's fills, tracks and lines in white at a small alpha in both themes
 * (`#ffffff0a`, `#ffffff0d`, ...), so on the light theme they all but vanish. Owner choice 2 of the
 * plan would mix one `--jx-ink` (white on dark, the text colour on light) at each rule's alpha; the
 * page has not done that yet, so [ink] is white in both themes, as the page draws it, and becomes
 * the text colour on light in the same change that brings `--jx-ink` to the page.
 */
@Immutable
internal class JournalColors(tokens: Map<String, Long>, light: Boolean) :
    PageColors(tokens, light) {
    /** --jx-ink: what the Journal's white-alpha fills and lines are mixed from. */
    val ink = Color.White

    /** The hero and the lock card: the panel gradient under the hero's two glows. */
    val panelFade = cssLinearGradient(180f, 0f to panel, 1f to bgElev)
    val heroGlowBlue =
        cssRadialGradient(1.2f, 1.5f, 0f, 0f, 0f to GlowBlue, 0.55f to GlowBlue.copy(alpha = 0f))
    val heroGlowAmber =
        cssRadialGradient(0.9f, 1.2f, 1f, 1f, 0f to GlowAmber, 0.6f to GlowAmber.copy(alpha = 0f))
    val lockGlow =
        cssRadialGradient(1.2f, 1.3f, 0.5f, 0f, 0f to GlowBlue, 0.6f to GlowBlue.copy(alpha = 0f))

    /** The hero's inner top highlight (`inset 0 1px 0 #ffffff14`). */
    val heroEdge = Color(0x14FFFFFF)

    /** The ghost button's fill (.jx-ghost) and a stat's base fill (.jx-stat). */
    val ghostFill = ink.copy(alpha = 0x0A / 255f)
    val statBase = ink.copy(alpha = 0x06 / 255f)

    /** A rhythm cell with nothing logged, and today's outline (.jx-cell.off, .jx-cell.now). */
    val cellOff = ink.copy(alpha = 0x0D / 255f)
    val cellToday = Color(0xCCFFFFFF)

    /**
     * The page's --shadow under the hero and the lock card: a deep drop in the dark theme, two soft
     * layers in the light (00-00-foundation.css).
     */
    fun Modifier.journalShadow(shape: Shape): Modifier =
        if (light)
            boxShadow(ShadowLightFar, 28.dp, shape, 8.dp)
                .boxShadow(ShadowLightNear, 2.dp, shape, 1.dp)
        else boxShadow(ShadowDark, 24.dp, shape, 8.dp)

    private companion object {
        val GlowBlue = Color(0x267C9CFF)
        val GlowAmber = Color(0x1CF59E0B)
        val ShadowDark = Color(0x55000000)
        val ShadowLightFar = Color(0x141A2744)
        val ShadowLightNear = Color(0x0A1A2744)
    }
}

internal val LocalJournalColors =
    staticCompositionLocalOf<JournalColors> { error("No journal colours") }

/** A colour the page projects (ARGB through NativeTabKit.color). */
internal fun argb(color: Long) = Color(color.toInt())
