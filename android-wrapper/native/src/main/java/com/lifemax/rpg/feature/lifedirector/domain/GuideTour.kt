package com.lifemax.rpg.feature.lifedirector.domain

/**
 * A name tag the page pins on the dial for a guide step (tourTags in logic.js): a pill centred at
 * ([x], [y]) in the dial's 360-unit square, with the page's words and colour key ([kind]: a block
 * kind such as sleep or work, "now", or "" for the guide's own blue).
 */
data class GuideTag(val x: Float, val y: Float, val text: String, val kind: String)

/**
 * How the dial looks on a step of Today's guide: the opacity of each layer, as style.css dims them
 * under `.ld-dial[data-tour=…]`, and whether the handles pulse. Pure, so the rule is tested without
 * drawing. Steps without a dial mode leave the dial as it is ([NONE]).
 */
data class DialTour(
    val outer: Float = 1f,
    val words: Float = 1f,
    val inner: Float = 1f,
    val centre: Float = 1f,
    val handles: Float = 1f,
    val pulse: Boolean = false,
) {
    companion object {
        val NONE = DialTour()

        /**
         * The page's rules, mode by mode: the outer ring step dims the inner ring, the middle and
         * the handles to .12; the inner ring step the outer ring, the middle, the handles and the
         * words to .12; the middle's step the rings and the handles to .15 (the words .12); the
         * handles step the rings and the middle to .2 (the words .12) and pulses the handle rings.
         * The clock step only pins its tags. Anything else: the dial as it is.
         */
        fun of(mode: String): DialTour =
            when (mode) {
                "outer" -> DialTour(inner = .12f, centre = .12f, handles = .12f)
                "inner" -> DialTour(outer = .12f, words = .12f, centre = .12f, handles = .12f)
                "center" -> DialTour(outer = .15f, words = .12f, inner = .15f, handles = .15f)
                "handles" ->
                    DialTour(outer = .2f, words = .12f, inner = .2f, centre = .2f, pulse = true)
                else -> NONE
            }
    }
}
