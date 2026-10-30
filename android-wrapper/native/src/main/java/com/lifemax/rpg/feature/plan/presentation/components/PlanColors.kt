package com.lifemax.rpg.feature.plan.presentation.components

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.lifemax.rpg.core.designsystem.components.tokenColor
import com.lifemax.rpg.core.designsystem.cssLinearGradient
import com.lifemax.rpg.core.designsystem.mix

/**
 * The plan view's colours: the page's palette tokens, the mixes its styles make of them, and the
 * few colours the styles fix themselves (named here, in one place). The light theme's own rules
 * (white fields and buttons, darker tags) are applied here too.
 */
@Immutable
internal class PlanColors(private val tokens: Map<String, Long>, val light: Boolean) {
    private fun token(key: String) = tokenColor(tokens, key)

    val bg = token("bg")
    val bgElev = token("bg-elev")
    val panel = token("panel")
    val panel2 = token("panel2")
    val panel3 = token("panel3")
    val text = token("text")
    val textDim = token("text-dim")
    val muted = token("muted")
    val muted2 = token("muted2")
    val accent = token("accent")
    val accentDim = token("accent-dim")
    val accentStrong = token("accent-strong")
    val line = token("line")
    val lineSoft = token("line-soft")
    val good = token("good")
    val gold = token("gold")
    val bad = token("bad")

    /** Fields, check boxes, the Leave button and toasts turn white in the light theme. */
    val field = if (light) Color.White else bg
    val secondaryButton = if (light) Color.White else panel2
    val toast = if (light) Color.White else panel3
    val toastShadow = if (light) ToastShadowLight else ToastShadowDark
    val boxBorder = if (light) line else BoxBorderDark
    /** A ticked box is green in the dark theme; the light theme's own rule keeps it white. */
    val tickedFill = if (light) Color.White else good
    val tickedBorder = if (light) line else good
    val track = if (light) TrackLight else bg
    val tagFill = if (light) panel2 else bgElev
    val tagText = if (light) textDim else muted
    val keyTagBorder = if (light) line else lineSoft
    val noteFill = if (light) NoteFillLight else bgElev
    val noteBorder = if (light) NoteBorderLight else line
    val noteText = if (light) textDim else muted
    val onAccent = if (light) Color.White else OnAccentDark

    val launcherResumeFill = accent.mix(0.08f, panel)
    val launcherResumeBorder = accent.mix(0.55f, line)
    val calendarBorder = accent.mix(0.24f, line)
    val countBorder = accent.mix(0.40f, line)
    val countGlow = accent.mix(0.20f, panel)
    val countRing = accent.copy(alpha = 0.05f)
    val bottomFill = bgElev.copy(alpha = 0.6f)
    val doneBorder = good.mix(0.40f, line)
    val missedBorder = bad.mix(0.48f, line)
    val selectedIconFill = accent.mix(0.17f, panel)
    val selectedIconBorder = accent.mix(0.25f, line)

    val hero = cssLinearGradient(120f, 0f to HeroTint, 0.6f to Color.Transparent)
    val cycle = cssLinearGradient(90f, 0f to accent, 1f to CycleEnd)
    val progress = cssLinearGradient(90f, 0f to accent, 1f to good)
    val weekIdle = cssLinearGradient(155f, 0f to bgElev, 1f to panel2.mix(0.65f, bgElev))
    val weekActive =
        cssLinearGradient(145f, 0f to accent.mix(0.20f, panel), 1f to accent.mix(0.07f, bgElev))
    val dayIdle = cssLinearGradient(160f, 0f to bgElev, 1f to panel2.mix(0.45f, bgElev))
    val daySelected =
        cssLinearGradient(155f, 0f to accent.mix(0.22f, panel), 1f to accent.mix(0.07f, bgElev))

    private companion object {
        val BoxBorderDark = Color(0xFF4A5568)
        val OnAccentDark = Color(0xFF0B1020)
        val TrackLight = Color(0xFFE8ECF4)
        val NoteFillLight = Color(0xFFEEF3FC)
        val NoteBorderLight = Color(0xFFC5D0E6)
        val HeroTint = Color(0x0D7C9CFF)
        val CycleEnd = Color(0xFFA9BAFF)
        val ToastShadowDark = Color(0x88000000)
        val ToastShadowLight = Color(0x281A2744)
    }
}

internal val LocalPlanColors = staticCompositionLocalOf<PlanColors> { error("No plan colours") }

/**
 * Colours the page's styles fix: the tick in a done box, the dot on the selected week, the drop
 * shadows of the cards (one layer in the dark theme, two in the light), the calendar and its
 * sessions count, and the XP celebration's white with the dark drop under its "+N XP".
 */
internal val CheckInk = Color(0xFF0C0E12)
internal val SelectedWeekInk = Color.White
internal val CardShadowDark = Color(0x40000000)
internal val CardShadowLightNear = Color(0x081A2744)
internal val CardShadowLightFar = Color(0x0C1A2744)
internal val CalendarShadow = Color(0x30000000)
internal val CountShadow = Color(0x18000000)
internal val FxWhite = Color.White
internal val LabelDrop = Color(0x77000000)
