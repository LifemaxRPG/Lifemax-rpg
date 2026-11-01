package com.lifemax.rpg.feature.overview.presentation.cards

import kotlin.math.roundToLong

/*
 * The sizes the page's styles give the week chart and the health mini-bars, in dp (CSS px), so the
 * native drawing puts each bar and the pace line where the page does. The page has already turned
 * XP into fractions (ovWeekData, ovHealthData); this only lays them out.
 */
internal object CardsBGeometry {
    /** `.ov-bars`: the chart's height. */
    const val CHART = 132f

    /** `.ov-bar`'s padding above and below its column, and the gap above the day's letter. */
    const val BAR_PAD_TOP = 4f
    const val BAR_PAD_BOTTOM = 2f
    const val BAR_GAP = 6f

    /** The day's letter's line at the page's size (10 px × 1.5). */
    const val LABEL_LINE = 15f

    /** How far the page lifts the pace line above the fraction: `calc(frac*100% + 22px)`. */
    const val PACE_LIFT = 22f

    /**
     * An empty day's block. The page's `.ov-bs i.empty` asks for 4 px, but the shared `.empty`
     * class (01-base.css:226) pads it 36 px above and below, so it draws 72 px plus its 1 px top
     * border: a tall grey slab, which is what the owner sees, so it is drawn that way.
     */
    const val EMPTY_DAY = 73f

    /** `.ovh-days i`: the tallest mini-bar, the shortest, and the bar's widest. */
    const val HEALTH_BAR = 38f
    const val HEALTH_MIN = 4f
    const val HEALTH_WIDEST = 26f

    /**
     * The height of a day's stacked column (`.ov-bs`, flex 1): the chart less the bar's padding,
     * the gap and the letter's line, [labelLine] tall at the reader's text size.
     */
    fun column(labelLine: Float = LABEL_LINE): Float =
        (CHART - BAR_PAD_TOP - BAR_PAD_BOTTOM - BAR_GAP - labelLine).coerceAtLeast(0f)

    /**
     * Each segment's height, bottom up: its fraction of the column, as the page's `height:N%`
     * (border-box, so the 1 px top border is inside it). Never more than the column in all.
     */
    fun segments(fracs: List<Float>, column: Float): List<Float> {
        var left = column
        return fracs.map { f ->
            val h = (tenths(f).coerceAtLeast(0f) * column).coerceAtMost(left)
            left -= h
            h
        }
    }

    /**
     * The pace line's distance from the chart's bottom: the page's `frac × chart + 22 px`. A larger
     * letter line ([labelLine]) lifts it by as much, so it keeps its place over the bars.
     */
    fun paceBottom(frac: Float, labelLine: Float = LABEL_LINE): Float =
        (tenths(frac).coerceIn(0f, 1f) * CHART + PACE_LIFT + (labelLine - LABEL_LINE)).coerceIn(
            0f,
            CHART,
        )

    /**
     * A fraction as the page writes it into its styles: a percentage to one decimal
     * (`(frac*100).toFixed(1)+'%'`).
     */
    fun tenths(frac: Float): Float = (frac.toDouble() * 1000).roundHalfUp() / 1000f

    /** A health mini-bar's height: `max(4, round(frac × 38))` px, rounded as JS rounds. */
    fun healthBar(frac: Float): Float =
        maxOf(HEALTH_MIN, (frac.toDouble() * HEALTH_BAR).roundHalfUp().toFloat())

    private fun Double.roundHalfUp(): Long = Math.floor(this + 0.5).roundToLong()
}

/** The week bar's buzz as the page plays it on a pick (ovPick: `haptic(6)`). */
internal const val WEEK_PICK_MS = 6L
