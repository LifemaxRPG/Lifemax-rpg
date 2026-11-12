package com.lifemax.rpg.feature.pet.art

/**
 * How a cat is lit: a [shadeOffset] (tones lighter, negative for darker, applied to the coat ramp
 * as given and to the smaller ramps scaled down to half, rounded away from zero) and a multiplying
 * [tint] (0xRRGGBB, white leaves colours as they are).
 */
class CatLight(val shadeOffset: Int = 0, val tint: Int = 0xFFFFFF) {
    /** True if this light changes nothing: the output is exactly [Recolour]'s. */
    val neutral: Boolean
        get() = shadeOffset == 0 && (tint and 0xFFFFFF) == 0xFFFFFF

    companion object {
        val NEUTRAL = CatLight()
    }
}

/**
 * [Recolour] for a [SplitSheet]: base plus shade, looked up in the palette, with a [CatLight]
 * moving the shade and tinting the result. With [CatLight.NEUTRAL] it produces exactly the pixels
 * [Recolour.region] produces for the sheet that was split. Hand-drawn art is never touched: only
 * which tone of a ramp is looked up changes, and only for ramps [CoatLook] calls healthy.
 */
object LitRecolour {

    /** The part of [split] inside [rect] in [palette] under [light]. */
    fun region(
        split: SplitSheet,
        palette: CatPalette,
        rect: FrameRect,
        light: CatLight = CatLight.NEUTRAL,
        look: CoatLook = CoatLook.of(palette),
    ): ArgbImage {
        require(
            rect.x >= 0 &&
                rect.y >= 0 &&
                rect.x + rect.width <= split.width &&
                rect.y + rect.height <= split.height
        ) {
            "$rect is outside the ${split.width}x${split.height} sheet"
        }
        val neutral = light.neutral
        // one colour per (base, shade) pair; a pair is at most base 120 + 2 shades, so 3 * 256 is
        // generous and index arithmetic stays branch-free in the pixel loop
        val lookup = IntArray(256 * SHADES)
        for (base in 0 until 256) {
            for (shade in 0 until SHADES) {
                lookup[base * SHADES + shade] =
                    if (neutral) palette.argb(base + 10 * shade)
                    else lit(base, shade, palette, light, look)
            }
        }
        val out = IntArray(rect.width * rect.height)
        for (y in 0 until rect.height) {
            val from = (rect.y + y) * split.width + rect.x
            for (x in 0 until rect.width) {
                out[y * rect.width + x] =
                    lookup[split.baseAt(from + x) * SHADES + split.shadeAt(from + x)]
            }
        }
        return ArgbImage(rect.width, rect.height, out)
    }

    private fun lit(
        base: Int,
        shade: Int,
        palette: CatPalette,
        light: CatLight,
        look: CoatLook,
    ): Int {
        val family = ShadeSplit.family(base)
        val drawn = palette.argb(base + 10 * shade)
        var argb = drawn
        if (family != ShadeFamily.FIXED && base == family.base && look.healthy(family)) {
            val moved = ShadeSplit.indexOf(family, shade + shift(family, light.shadeOffset, look))
            val colour = palette.argb(moved)
            if (colour != CatPalette.CLEAR) argb = colour
        }
        return if (argb == CatPalette.CLEAR) argb else tinted(argb, light.tint)
    }

    private fun shift(family: ShadeFamily, offset: Int, look: CoatLook): Int =
        when {
            family == ShadeFamily.COAT -> offset.coerceIn(-look.downGain, MAX_UP)
            offset > 0 -> (offset + 1) / 2
            else -> -((-offset + 1) / 2)
        }

    private fun tinted(argb: Int, tint: Int): Int {
        if ((tint and 0xFFFFFF) == 0xFFFFFF) return argb
        fun channel(c: Int, t: Int) = (c * t + 127) / 255
        val r = channel((argb shr 16) and 255, (tint shr 16) and 255)
        val g = channel((argb shr 8) and 255, (tint shr 8) and 255)
        val b = channel(argb and 255, tint and 255)
        return (argb and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
    }

    /** The most tones lighter than drawn the coat may be moved. */
    const val MAX_UP = 1

    private const val SHADES = 4
}
