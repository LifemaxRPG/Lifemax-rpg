package com.lifemax.rpg.feature.library.presentation.components

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.lifemax.rpg.core.designsystem.PageColors
import com.lifemax.rpg.core.designsystem.PageInk
import com.lifemax.rpg.core.designsystem.cssLinearGradient

/**
 * The Library's colours: the page's (PageColors), the mixes its styles make of them, and the few
 * colours its styles fix themselves (named here, in one place). The light theme's own rules for the
 * Library (its secondary buttons and its bar track) are applied here too.
 */
@Immutable
internal class LibraryColors(tokens: Map<String, Long>, light: Boolean) :
    PageColors(tokens, light) {
    val secondaryButton = if (light) panel else panel2
    val barTrack = if (light) PageInk.TrackLight else bg
    /** --good-dim, which the page does not project. */
    val goodDim = if (light) GoodDimLight else GoodDimDark

    val completion = cssLinearGradient(90f, 0f to accent, 1f to good)
    val listened = cssLinearGradient(90f, 0f to accent, 1f to accentStrong)

    private companion object {
        val GoodDimDark = Color(0x223DD68C)
        val GoodDimLight = Color(0x180F9F6E)
    }
}

internal val LocalLibraryColors =
    staticCompositionLocalOf<LibraryColors> { error("No library colours") }

/**
 * Colours the page's styles fix in both themes beyond PageInk's: a finished pill's border and the
 * glow around the Completion bar's fill.
 */
internal val FinishedBorder = Color(0x553DD68C)
internal val CompletionGlow = Color(0x447C9CFF)
