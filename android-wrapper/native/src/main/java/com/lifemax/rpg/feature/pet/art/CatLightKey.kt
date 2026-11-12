package com.lifemax.rpg.feature.pet.art

/**
 * The colour a light washes a cat with (see [CatLight.wash]): its RGB and how far, 0..255, an
 * opaque colour moves toward it. [darkOnly] lights touch only a low-contrast coat (the black coat,
 * [CoatLook.downGain] 1): the night's small blue lift that a multiply cannot give a near-black.
 */
enum class LightTint(val rgb: Int, val wash: Int, val darkOnly: Boolean = false) {
    NONE(0xFFFFFF, 0),
    /** Sunlight through the window: warm white, 12 percent. */
    SUN(0xFFE6B0, 31),
    /** Moonlight: cool blue, 14 percent. */
    MOON(0x96AAFF, 36),
    /** The floor lamp: warm orange, 16 percent. */
    LAMP(0xFFC27A, 41),
    /** The low light of dawn and dusk at a window: orange, 14 percent. */
    DUSK(0xFFB27A, 36),
    /** Night anywhere, for dark coats only: a faint blue-violet lift, 10 percent. */
    NIGHT(0x8C8CD2, 26, darkOnly = true),
    /** A heart or a sparkle (WP-L9, not used yet): warm pink, 18 percent. */
    SPARK(0xFF9EC8, 46),
}

/**
 * A cat's light as one small number, so it can be held steady, compared and used as part of a cache
 * key: half-steps of shade (-4..3, a half step is the dither of [CatLight.half]), a [LightTint] and
 * whether the coat is flattened. [NEUTRAL] (0) is no light at all: the art exactly as drawn, which
 * the app draws by its old path.
 */
object CatLightKey {
    /** No light: draw the cat as it is. */
    const val NEUTRAL = 0

    /** The lightest and darkest half-steps a key holds. */
    const val MIN_HALF_STEPS = -4
    const val MAX_HALF_STEPS = 3

    /** How many distinct keys there are (every key is below this). */
    const val COUNT = 1 shl 7

    /** The key of [halfSteps] (clamped into range), [tint] and [flatten]. */
    fun pack(halfSteps: Int, tint: LightTint, flatten: Boolean = false): Int {
        val h = halfSteps.coerceIn(MIN_HALF_STEPS, MAX_HALF_STEPS)
        return (h and 7) or (tint.ordinal shl 3) or (if (flatten) 1 shl 6 else 0)
    }

    /** The half-steps of [key]. */
    fun halfSteps(key: Int): Int {
        val h = key and 7
        return if (h >= 4) h - 8 else h
    }

    /** The tint of [key]. */
    fun tint(key: Int): LightTint =
        LightTint.entries.getOrElse((key shr 3) and 7) { LightTint.NONE }

    /** Whether [key] flattens the coat. */
    fun flatten(key: Int): Boolean = (key shr 6) and 1 == 1

    /** [key] with its flattening set to [flatten]; the neutral key stays neutral. */
    fun withFlatten(key: Int, flatten: Boolean): Int =
        if (key == NEUTRAL) NEUTRAL else (key and FLAT.inv()) or (if (flatten) FLAT else 0)

    private const val FLAT = 1 shl 6

    /**
     * What [key] does to a coat whose [look] is given: whole tones and the dither's half, the wash,
     * the flattening and the virtual tones past the ramp's ends. [NEUTRAL] is [CatLight.NEUTRAL].
     */
    fun light(key: Int, look: CoatLook): CatLight {
        if (key == NEUTRAL) return CatLight.NEUTRAL
        val h = halfSteps(key)
        val tint = tint(key)
        val washed = tint != LightTint.NONE && (!tint.darkOnly || look.downGain == 1)
        return CatLight(
            shadeOffset = h / 2,
            half = h % 2,
            wash = if (washed) (tint.wash shl 24) or tint.rgb else 0,
            flatten = flatten(key),
            virtual = true,
        )
    }

    /** A short label for the developer readout: "+1.5 sun", "-1 flat", "0". */
    fun label(key: Int): String {
        if (key == NEUTRAL) return "0"
        val h = halfSteps(key)
        val steps = if (h % 2 == 0) "${h / 2}" else "${h / 2.0}"
        val sign = if (h > 0) "+" else ""
        val tint =
            tint(key).takeIf { it != LightTint.NONE }?.let { " ${it.name.lowercase()}" } ?: ""
        return "$sign$steps$tint${if (flatten(key)) " flat" else ""}"
    }
}
