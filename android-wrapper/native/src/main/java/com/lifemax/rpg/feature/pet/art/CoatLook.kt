package com.lifemax.rpg.feature.pet.art

/**
 * What a coat's palette can take from a light, worked out once per palette from its colours alone
 * (so it holds for the shipped coats and for a genetic coat the page sends).
 *
 * A ramp is *healthy* if every tone of it has a colour and each tone is strictly brighter than the
 * one before. A ramp the palette does not use at all (no colour for any of its tones) is healthy
 * too: there is nothing to shift. An unhealthy ramp is left flat by [LitRecolour] (no shade shift),
 * which is flagged here and is not an error. [downGain] says how far a darkening light may move the
 * coat: a coat whose three darkest tones sit in a narrow brightness band (the black coat) barely
 * changes when moved darker, so it is allowed one step, not two.
 */
class CoatLook
private constructor(private val lum: Array<DoubleArray>, private val ok: BooleanArray) {
    /** Whether [family]'s ramp may be shifted. [ShadeFamily.FIXED] is never shifted. */
    fun healthy(family: ShadeFamily): Boolean = family != ShadeFamily.FIXED && ok[family.ordinal]

    /** The brightness (0..255) of tone [level] of [family], or -1 if the palette has no colour. */
    fun luminance(family: ShadeFamily, level: Int): Double = lum[family.ordinal][level]

    /** Brightest tone minus darkest tone of [family]; 0 if the ramp is not fully coloured. */
    fun spread(family: ShadeFamily): Double {
        val l = lum[family.ordinal]
        return if (l.all { it >= 0 }) l.last() - l.first() else 0.0
    }

    /**
     * How many tones darker than drawn a light may move the coat: 2, or 1 for a low-contrast one.
     */
    val downGain: Int
        get() {
            val l = lum[ShadeFamily.COAT.ordinal]
            if (l.any { it < 0 }) return 0
            return if (l[2] - l[0] >= DOWN_BAND) 2 else 1
        }

    companion object {
        /**
         * The three darkest coat tones must span at least this much brightness to allow 2 steps.
         */
        const val DOWN_BAND = 48.0

        /** The look of [palette]. */
        fun of(palette: CatPalette): CoatLook {
            val families = ShadeFamily.entries
            val lum = Array(families.size) { DoubleArray(families[it].levels) { -1.0 } }
            val ok = BooleanArray(families.size)
            for (family in families) {
                if (family == ShadeFamily.FIXED) continue
                val l = lum[family.ordinal]
                for (level in 0 until family.levels) {
                    val argb = palette.argb(family.base + 10 * level)
                    if (argb != CatPalette.CLEAR) l[level] = luminanceOf(argb)
                }
                val unused = l.all { it < 0 }
                ok[family.ordinal] =
                    unused || (l.all { it >= 0 } && (1 until l.size).all { l[it] > l[it - 1] })
            }
            return CoatLook(lum, ok)
        }

        /** Rec. 601 brightness of an ARGB colour, 0..255. */
        fun luminanceOf(argb: Int): Double =
            0.299 * ((argb shr 16) and 255) +
                0.587 * ((argb shr 8) and 255) +
                0.114 * (argb and 255)
    }
}
