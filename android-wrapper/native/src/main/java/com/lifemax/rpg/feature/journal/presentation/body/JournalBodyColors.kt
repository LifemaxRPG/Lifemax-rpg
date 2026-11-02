package com.lifemax.rpg.feature.journal.presentation.body

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.lifemax.rpg.core.designsystem.PageColors

/**
 * The Journal body's colours (07-finishing.css, the Journal pane): the page's palette, and the inks
 * its rules fix. The page draws most of its tracks, rules and boxes in white or black at a low
 * alpha in both themes (no light-theme rule overrides them), so the light theme shows them as the
 * page does: faint or invisible. The light theme's own rules (white fields and icon boxes, the day
 * number's gradient, the softer shadows) are applied here.
 */
@Immutable
internal class JournalBodyColors(tokens: Map<String, Long>, light: Boolean) :
    PageColors(tokens, light) {
    /** The reflection box's field (html.light-theme textarea is white). */
    val textarea = if (light) Color.White else bg
    /** An activity's icon box (.acticon; html.light-theme .acticon). */
    val iconBox = if (light) Color.White else panel2
    val iconBoxBorder = if (light) line else lineSoft

    /** The day number's gradient (.jx-bignum, and its light-theme rule). */
    val bigNumber: Brush =
        if (light) Brush.verticalGradient(listOf(text, accent))
        else Brush.verticalGradient(0f to Color.White, 0.6f to accentStrong, 1f to accent)

    /**
     * --shadow and --shadow-sm: the far layer of each (the dark theme's inset edge is drawn apart).
     */
    val shadowFar = if (light) Color(0x141A2744) else Color(0x55000000)
    val shadowFarBlur = if (light) 28f else 24f
    val shadowSmall = if (light) Color(0x0C1A2744) else Color(0x40000000)
    val shadowSmallBlur = if (light) 10f else 8f

    /**
     * The archetype colour [c] mixed into a transparent ground, as color-mix(c a%, transparent): a
     * browser mixes with premultiplied alpha, so the colour keeps its hue and only fades.
     */
    fun tint(c: Color, amount: Float): Color = c.copy(alpha = c.alpha * amount)
}

internal val LocalJournalBodyColors =
    staticCompositionLocalOf<JournalBodyColors> { error("No journal colours") }

/** The page's fixed inks for the Journal, the same in both themes. */
internal object JournalInk {
    fun white(alpha: Int) = Color(alpha shl 24 or 0xFFFFFF)

    fun black(alpha: Int) = Color(alpha shl 24)

    val Amber = Color(0xFFF59E0B)
    /** A review tile's subtitle (.jx-rev small). */
    val AmberText = Color(0xFFF5C26B)
    /** Text on the review badge (.jx-rev-ico). */
    val OnAmber = Color(0xFF1A1204)
    /** Text on an archetype badge (.jc-badge). */
    val OnBadge = Color(0xFF06121A)
    /** The counter once claimed or long enough (.jc-count.on). */
    val CounterOn = Color(0xFF34D399)
    /** The care note's blue (.jc-care). */
    val Care = Color(0xFF38BDF8)
    /** The welcome card's moon (the page's svg). */
    val MoonLight = Color(0xFFFDE68A)
    /** The day's XP glow (.jx-dayxp b text-shadow). */
    val XpGlow = Color(0x553DD68C)
    /** The coach card's colour on a review (jcReviewHtml's --jc). */
    val Coach = Color(0xFFF59E0B)
}

/** An ARGB colour as the projection writes it. */
internal fun argb(value: Long) = Color(value.toInt())
