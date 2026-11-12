package com.lifemax.rpg.feature.pet.light

/**
 * The weather outside the windows: cosmetic only (docs/specs/CAT_LIGHTING_PLAN.md OQ-L1). It dims
 * the sun and moon light on the floor and on the cats ([LightData.sun]); nothing reads a real
 * forecast, and nothing goes to the page.
 */
enum class Weather(val key: String) {
    CLEAR("clear"),
    OVERCAST("overcast"),
    RAIN("rain");

    companion object {
        /**
         * The seeded cosmetic weather of local day [epochDay] (days since 1970-01-01): the same day
         * always looks the same. About seven days in ten are clear, two overcast, one rainy.
         */
        fun seeded(epochDay: Long): Weather {
            var h = epochDay * -0x61c8864680b583ebL
            h = h xor (h ushr 29)
            h *= -0x4b47d5b1a1e3b6b9L
            h = h xor (h ushr 32)
            val roll = Math.floorMod(h, 100L)
            return when {
                roll < CLEAR_IN_100 -> CLEAR
                roll < CLEAR_IN_100 + OVERCAST_IN_100 -> OVERCAST
                else -> RAIN
            }
        }

        private const val CLEAR_IN_100 = 70
        private const val OVERCAST_IN_100 = 20
    }
}
