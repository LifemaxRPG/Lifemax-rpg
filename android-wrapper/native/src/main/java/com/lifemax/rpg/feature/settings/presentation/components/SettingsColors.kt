package com.lifemax.rpg.feature.settings.presentation.components

import androidx.compose.ui.graphics.Color
import com.lifemax.rpg.core.designsystem.PageColors

/*
 * Colours the Settings view's styles fix rather than take from the palette. Each is used only where
 * the page uses it; which theme it belongs to is decided in [SettingsInk].
 */

/** Text on a selected category chip, in both themes (07-finishing.css:313). */
internal val ChipOnText = Color(0xFF0B1220)

/** The text on the accent button in the dark theme (.btn, 01-base.css:26). */
internal val OnAccentDark = Color(0xFF0B1020)

/** The light top edge of the accent button (inset 0 1 0, 01-base.css:29; light 00-00:72). */
internal val ButtonEdgeDark = Color(0x22FFFFFF)
internal val ButtonEdgeLight = Color(0x44FFFFFF)

/** The light theme's glow under the accent button (00-00-foundation.css:72). */
internal val ButtonGlowLight = Color(0x333B5BDB)

/** The page's small shadow under a card (--shadow-sm and the dark .card shadow). */
internal val CardShadowDark = Color(0x40000000)
internal val CardShadowLightNear = Color(0x081A2744)
internal val CardShadowLightFar = Color(0x0C1A2744)

/** The light theme's .note (00-00-foundation.css:92). */
internal val NoteFillLight = Color(0xFFEEF3FC)
internal val NoteEdgeLight = Color(0xFFC5D0E6)

/** --warn, the edge of a warning note (00-00-foundation.css:22 and 57). */
internal val WarnDark = Color(0xFFF0A35A)
internal val WarnLight = Color(0xFFC2410C)

/** The XP rules' number box (04-runtime.js:218), white in the light theme like every field. */
internal val RuleFieldDark = Color(0xFF0B1223)

/** The browser's placeholder grey in an empty field. */
internal val Placeholder = Color(0xFF757575)

/** The colours of Settings' own classes in the theme of [c]. */
internal class SettingsInk(c: PageColors) {
    /** .btn's text: dark on the dark theme's accent, white in the light theme (00-00:70). */
    val onAccent = if (c.light) Color.White else OnAccentDark

    /** .btn.secondary: panel2 with the text colour, the panel in the light theme (00-00:74). */
    val secondary = if (c.light) c.panel else c.panel2

    /** A select drawn with LMA_IN (33-android.js:10): panel2, white in the light theme. */
    val inField = if (c.light) Color.White else c.panel2

    /** A form field (.field input, 01-base.css:205): the page's background, white when light. */
    val formField = if (c.light) Color.White else c.bg

    /** The XP rules' number box. */
    val ruleField = if (c.light) Color.White else RuleFieldDark

    val noteFill = if (c.light) NoteFillLight else c.bgElev
    val noteEdge = if (c.light) NoteEdgeLight else c.line
    val noteText = if (c.light) c.textDim else c.muted
    val warn = if (c.light) WarnLight else WarnDark
}
