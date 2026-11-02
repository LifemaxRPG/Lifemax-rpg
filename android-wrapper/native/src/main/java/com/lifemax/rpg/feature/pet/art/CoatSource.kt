package com.lifemax.rpg.feature.pet.art

/**
 * Which sheet of a coat: the web draws a cat from one of these, by its pose and age.
 *
 * [key] is the name used in file names and in the web's pseudo-URLs: `adult`, `depth`, `st3` (adult
 * art, kitten stage 3) and `dst3` (depth art, kitten stage 3).
 */
sealed class SheetKind(val key: String) {
    /** The 28 side-view rows of a grown cat. */
    data object Adult : SheetKind("adult")

    /** The front, back, sniff and bonk rows of a grown cat. */
    data object Depth : SheetKind("depth")

    /** The side-view rows of a kitten at [index] (0..5). */
    data class Stage(val index: Int) : SheetKind("st$index")

    /** The depth rows of a kitten at [index] (0..5). */
    data class DepthStage(val index: Int) : SheetKind("dst$index")

    companion object {
        /**
         * The kind for a kitten ([stage] >= 0) or a grown cat ([Growth.ADULT]); [depth] picks the
         * depth rows.
         */
        fun of(stage: Int, depth: Boolean): SheetKind =
            when {
                stage < 0 -> if (depth) Depth else Adult
                else -> if (depth) DepthStage(stage) else Stage(stage)
            }
    }
}

/**
 * Coats native does not ship. The four legacy coats come with the app; every other coat (the
 * genetic `dna_...` keys) is composed by the web page, which will implement this interface and hand
 * native what it composed.
 *
 * Both answers are index art, like the shipped sheets: [sheet] is an 8-bit grey PNG (the same
 * format and layout as `sheets/<kind>-<coat>.png`), [palette] the coat's own colours.
 */
interface CoatSource {
    /** The sheet PNG of [coat] for [kind], or null if this source does not have it (yet). */
    fun sheet(coat: String, kind: SheetKind): ByteArray?

    /** The colours of [coat], or null if this source does not have them (yet). */
    fun palette(coat: String): CatPalette?
}
