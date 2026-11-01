package com.lifemax.rpg.feature.lifedirector.domain

/**
 * A sideways flick between Life Director's sections (phase 3A), decided from where the finger went
 * and how fast: far enough sideways (a third of [MIN_DP] when fast), clearly more sideways than up
 * or down. Pure, so it is tested without a screen.
 */
object SectionFlick {
    const val MIN_DP = 72f
    const val FAST_DP_PER_S = 900f
    private const val SIDEWAYS = 1.6f

    /** -1: the section to the left (a flick to the right), 1: the one to the right, 0: no flick. */
    fun decide(dxDp: Float, dyDp: Float, velocityXDpPerS: Float): Int {
        val ax = kotlin.math.abs(dxDp)
        if (ax < kotlin.math.abs(dyDp) * SIDEWAYS) return 0
        val far =
            ax >= MIN_DP || (ax >= MIN_DP / 3 && kotlin.math.abs(velocityXDpPerS) >= FAST_DP_PER_S)
        return if (!far) 0 else if (dxDp < 0) 1 else -1
    }

    /** The section a flick lands on, in the order of the tabs, or null at either end. */
    fun neighbour(tabs: List<String>, current: String, direction: Int): String? {
        val i = tabs.indexOf(current)
        if (i < 0 || direction == 0) return null
        return tabs.getOrNull(i + direction)
    }
}
