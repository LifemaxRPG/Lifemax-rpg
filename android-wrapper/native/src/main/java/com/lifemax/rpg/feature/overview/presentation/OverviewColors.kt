package com.lifemax.rpg.feature.overview.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import com.lifemax.rpg.core.designsystem.PageColors
import com.lifemax.rpg.core.designsystem.PageTheme
import com.lifemax.rpg.core.designsystem.cssLinearGradient
import com.lifemax.rpg.core.designsystem.mix

/**
 * The Overview's colours: the page's (PageColors), the mixes its styles make of them
 * (03-features.css .ov-*, 02-layout.css .feature-tip) and the few colours its styles fix, named
 * once here. The Overview's styles have no light-theme rules of their own beyond the page's shared
 * ones (the tip's, the button's), so the same mixes serve both themes.
 */
@Immutable
internal class OverviewColors(tokens: Map<String, Long>, light: Boolean) :
    PageColors(tokens, light) {
    val tipFill =
        if (light) cssLinearGradient(135f, 0f to TipTopLight, 0.7f to Color.White)
        else cssLinearGradient(135f, 0f to accent.mix(0.14f, panel), 0.7f to panel)
    val tipBorder = if (light) TipBorderLight else accent.mix(0.35f, line)

    /** .xpfill: the accent into --good. */
    val xpFill = cssLinearGradient(90f, 0f to accent, 1f to good)
    /** .xpbar's track: --bg, or the light theme's own. */
    val xpTrack = if (light) com.lifemax.rpg.core.designsystem.PageInk.TrackLight else bg

    /** .ov-editbtn: the panel at 70 % over the page. */
    val editFill = panel.copy(alpha = 0.7f)

    /** .btn.secondary in each theme. */
    val secondaryFill = if (light) panel else panel2

    companion object {
        val TipTopLight = Color(0xFFE8EEFF)
        val TipBorderLight = Color(0xFFC5D0EA)
        /** .ov-lvbadge's gold, its ink and its glow (#f2b44044). */
        val BadgeTop = Color(0xFFFFE08A)
        val BadgeBottom = Color(0xFFF2B440)
        val BadgeInk = Color(0xFF0B0E16)
        val BadgeGlow = Color(0x44F2B440)
        /** The streak's flame and lit days (#ff8a3d), and their glows. */
        val Flame = Color(0xFFFF8A3D)
        val FlameGlow = Color(0x77FF8A3D)
        val DotGlow = Color(0x88FF8A3D)
        /** .xpfill's glow (#7c9cff44). */
        val XpGlow = Color(0x447C9CFF)
        /** The crest's drop shadow (#0006). */
        val CrestShadow = Color(0x66000000)
    }
}

internal val LocalOverviewColors =
    staticCompositionLocalOf<OverviewColors> { error("No overview colours") }

/** The page's palette and type for the Overview, with the Overview's own colours. */
@Composable
internal fun OverviewTheme(
    tokens: Map<String, Long>,
    light: Boolean,
    content: @Composable () -> Unit,
) {
    val colors = remember(tokens, light) { OverviewColors(tokens, light) }
    PageTheme(colors) {
        CompositionLocalProvider(LocalOverviewColors provides colors, content = content)
    }
}

/** An ARGB colour from the page. */
internal fun argb(value: Long) = Color(value.toInt())

/**
 * CSS radial-gradient([rx]% [ry]% at [cx]% [cy]%, [color], transparent [stop]%) behind the box: an
 * ellipse sized by the box (the page's hero and luck glows). Drawn as a circle squashed to the
 * ellipse, as Compose's radial gradient is circular.
 */
internal fun Modifier.cssRadialGlow(
    rx: Float,
    ry: Float,
    cx: Float,
    cy: Float,
    color: Color,
    stop: Float,
): Modifier = drawBehind {
    val radiusX = size.width * rx
    val radiusY = size.height * ry
    if (radiusX <= 0f || radiusY <= 0f) return@drawBehind
    val k = radiusY / radiusX
    val centre = Offset(size.width * cx, size.height * cy)
    val brush =
        Brush.radialGradient(
            0f to color,
            stop to color.copy(alpha = 0f),
            center = centre,
            radius = radiusX,
        )
    // in the squashed space a box row y comes from centre.y + (y - centre.y) / k
    val top = centre.y - centre.y / k
    val bottom = centre.y + (size.height - centre.y) / k
    scale(1f, k, pivot = centre) {
        drawRect(brush, Offset(0f, top), Size(size.width, bottom - top))
    }
}
