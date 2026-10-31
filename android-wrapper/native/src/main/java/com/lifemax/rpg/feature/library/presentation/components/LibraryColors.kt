package com.lifemax.rpg.feature.library.presentation.components

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.lifemax.rpg.core.designsystem.boxShadow
import com.lifemax.rpg.core.designsystem.components.tokenColor
import com.lifemax.rpg.core.designsystem.cssLinearGradient
import com.lifemax.rpg.core.designsystem.mix

/**
 * The Library's colours: the page's palette tokens, the mixes its styles make of them, and the
 * colours its styles fix themselves (named here, in one place). The light theme's own rules (white
 * fields, white text on the accent, its tip and shadows) are applied here too.
 */
@Immutable
internal class LibraryColors(private val tokens: Map<String, Long>, val light: Boolean) {
    private fun token(key: String) = tokenColor(tokens, key)

    val bg = token("bg")
    val panel = token("panel")
    val panel2 = token("panel2")
    val text = token("text")
    val muted = token("muted")
    val muted2 = token("muted2")
    val accent = token("accent")
    val accentDim = token("accent-dim")
    val accentStrong = token("accent-strong")
    val line = token("line")
    val lineSoft = token("line-soft")
    val good = token("good")

    /** The search box and the selects turn white in the light theme; the check box does not. */
    val field = if (light) Color.White else bg
    val onAccent = if (light) Color.White else OnAccentDark
    val secondaryButton = if (light) panel else panel2
    val barTrack = if (light) BarTrackLight else bg
    /** --good-dim, which the page does not project. */
    val goodDim = if (light) GoodDimLight else GoodDimDark

    val tipFill =
        if (light) cssLinearGradient(135f, 0f to TipTopLight, 0.7f to Color.White)
        else cssLinearGradient(135f, 0f to accent.mix(0.14f, panel), 0.7f to panel)
    val tipBorder = if (light) TipBorderLight else accent.mix(0.35f, line)
    val completion = cssLinearGradient(90f, 0f to accent, 1f to good)
    val listened = cssLinearGradient(90f, 0f to accent, 1f to accentStrong)

    /** --shadow-sm under cards and the tip: one layer dark, two light. */
    fun smallShadow(shape: Shape): Modifier =
        if (light)
            Modifier.boxShadow(SmallShadowLightFar, 10.dp, shape, 2.dp)
                .boxShadow(SmallShadowLightNear, 2.dp, shape, 1.dp)
        else Modifier.boxShadow(SmallShadowDark, 8.dp, shape, 2.dp)

    /** A button's shadow, in the light theme only: the accent button's glow, or --shadow-sm. */
    fun buttonShadow(primary: Boolean, shape: Shape): Modifier =
        when {
            !light -> Modifier
            primary -> Modifier.boxShadow(AccentShadowLight, 8.dp, shape, 2.dp)
            else -> smallShadow(shape)
        }

    private companion object {
        val OnAccentDark = Color(0xFF0B1020)
        val BarTrackLight = Color(0xFFE8ECF4)
        val GoodDimDark = Color(0x223DD68C)
        val GoodDimLight = Color(0x180F9F6E)
        val TipTopLight = Color(0xFFE8EEFF)
        val TipBorderLight = Color(0xFFC5D0EA)
        val SmallShadowDark = Color(0x40000000)
        val SmallShadowLightNear = Color(0x081A2744)
        val SmallShadowLightFar = Color(0x0C1A2744)
        val AccentShadowLight = Color(0x333B5BDB)
    }
}

internal val LocalLibraryColors =
    staticCompositionLocalOf<LibraryColors> { error("No library colours") }

/**
 * Colours the page's styles fix in both themes: an unticked box's border, the tick, a finished
 * pill's border, the glow around the Completion bar's fill, and the browser's placeholder grey.
 */
internal val BoxBorder = Color(0xFF4A5568)
internal val CheckInk = Color(0xFF0C0E12)
internal val FinishedBorder = Color(0x553DD68C)
internal val CompletionGlow = Color(0x447C9CFF)
internal val PlaceholderGrey = Color(0xFF757575)
