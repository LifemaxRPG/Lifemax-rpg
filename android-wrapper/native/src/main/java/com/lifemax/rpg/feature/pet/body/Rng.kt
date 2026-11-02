package com.lifemax.rpg.feature.pet.body

/**
 * A tiny seeded random source (the same mulberry32 family the web room uses). Every draw is a few
 * integer operations on one field: no allocation, and the same seed always gives the same sequence,
 * which is what makes two cats differ while one cat replays exactly.
 */
class Rng(seed: Int) {
    private var state = seed

    /** The next float in `[0, 1)`. */
    fun nextFloat(): Float {
        state += GOLDEN_STEP
        var t = state
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + (t xor (t ushr 7)) * (t or 61))
        return ((t xor (t ushr 14)) ushr 8) / UNIT_24_BIT
    }

    /** A float in `[lo, hi)`. */
    fun range(lo: Float, hi: Float): Float = lo + (hi - lo) * nextFloat()

    /** True with probability [p]. */
    fun chance(p: Float): Boolean = nextFloat() < p

    /** Plus or minus one, evenly. */
    fun sign(): Float = if (nextFloat() < 0.5f) -1f else 1f

    companion object {
        private const val GOLDEN_STEP = 0x6D2B79F5
        private const val UNIT_24_BIT = 16777216f
        private const val FNV_OFFSET = 0x811C9DC5.toInt()
        private const val FNV_PRIME = 16777619

        /** FNV-1a over the characters, the hash the web room seeds its cats with. */
        fun hash(text: String): Int {
            var h = FNV_OFFSET
            for (i in text.indices) h = (h xor text[i].code) * FNV_PRIME
            return h
        }

        /**
         * An independent stream for one concern of one cat, so streams never disturb each other.
         */
        fun derive(seed: Int, salt: Int): Rng = Rng((seed xor salt) * FNV_PRIME + salt)
    }
}
