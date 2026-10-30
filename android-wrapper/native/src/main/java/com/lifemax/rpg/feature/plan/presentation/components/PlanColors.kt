package com.lifemax.rpg.feature.plan.presentation.components

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import com.lifemax.rpg.core.designsystem.components.tokenColor
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The plan view's colours: the page's palette tokens, the mixes its styles make of them, and the
 * few colours the styles fix themselves (named here, in one place). The light theme's own rules
 * (white fields and buttons, darker tags) are applied here too.
 */
@Immutable
internal class PlanColors(private val tokens: Map<String, Long>, light: Boolean) {
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

    val hero = CssLinearGradient(120f, listOf(HeroTint, Color.Transparent), listOf(0f, 0.6f))
    val cycle = CssLinearGradient(90f, listOf(accent, CycleEnd))
    val progress = CssLinearGradient(90f, listOf(accent, good))
    val weekIdle = CssLinearGradient(155f, listOf(bgElev, panel2.mix(0.65f, bgElev)))
    val weekActive =
        CssLinearGradient(145f, listOf(accent.mix(0.20f, panel), accent.mix(0.07f, bgElev)))
    val dayIdle = CssLinearGradient(160f, listOf(bgElev, panel2.mix(0.45f, bgElev)))
    val daySelected =
        CssLinearGradient(155f, listOf(accent.mix(0.22f, panel), accent.mix(0.07f, bgElev)))

    private companion object {
        val BoxBorderDark = Color(0xFF4A5568)
        val OnAccentDark = Color(0xFF0B1020)
        val TrackLight = Color(0xFFE8ECF4)
        val NoteFillLight = Color(0xFFEEF3FC)
        val NoteBorderLight = Color(0xFFC5D0E6)
        val HeroTint = Color(0x0D7C9CFF)
        val CycleEnd = Color(0xFFA9BAFF)
    }
}

internal val LocalPlanColors = staticCompositionLocalOf<PlanColors> { error("No plan colours") }

/**
 * Colours the page's styles fix in both themes: the tick in a done box, the dot on the selected
 * week, and the drop shadows of the calendar and of its sessions count.
 */
internal val CheckInk = Color(0xFF0C0E12)
internal val SelectedWeekInk = Color.White
internal val CardShadow = Color(0x30000000)
internal val CountShadow = Color(0x18000000)

/** CSS color-mix(in srgb, this [amount], [other]). */
internal fun Color.mix(amount: Float, other: Color): Color =
    Color(
        red = red * amount + other.red * (1 - amount),
        green = green * amount + other.green * (1 - amount),
        blue = blue * amount + other.blue * (1 - amount),
        alpha = alpha * amount + other.alpha * (1 - amount),
    )

/**
 * CSS linear-gradient(angle, colors): 0° points up and 90° right, and the gradient line runs corner
 * to corner of the box it fills, so it fits any size the way the page's does.
 */
internal class CssLinearGradient(
    degrees: Float,
    private val colors: List<Color>,
    private val stops: List<Float>? = null,
) : ShaderBrush() {
    private val dx = sin(Math.toRadians(degrees.toDouble())).toFloat()
    private val dy = -cos(Math.toRadians(degrees.toDouble())).toFloat()

    override fun createShader(size: Size): Shader {
        val half = (abs(size.width * dx) + abs(size.height * dy)) / 2
        val cx = size.width / 2
        val cy = size.height / 2
        return LinearGradientShader(
            Offset(cx - dx * half, cy - dy * half),
            Offset(cx + dx * half, cy + dy * half),
            colors,
            stops,
        )
    }
}
