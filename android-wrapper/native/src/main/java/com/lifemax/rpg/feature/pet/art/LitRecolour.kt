package com.lifemax.rpg.feature.pet.art

/**
 * How a cat is lit: a [shadeOffset] (tones lighter, negative for darker, applied to the coat ramp
 * as given and to the smaller ramps scaled down to half, rounded away from zero) and a multiplying
 * [tint] (0xRRGGBB, white leaves colours as they are).
 *
 * The rest is what WP-L3 of docs/specs/CAT_LIGHTING_PLAN.md added, all off by default:
 * - [half] (-1, 0 or 1) is half a tone more that way, drawn as ordered dithering: one more tone on
 *   every other pixel, a checker on the sheet's own pixel grid. Frames are an even number of pixels
 *   wide and high, so the checker is anchored to the frame: a still cat's dither is still, and a
 *   moving cat's dither moves with its art.
 * - [wash] (0xAARRGGBB) mixes every opaque colour A/255 of the way toward the light's colour RGB:
 *   the warm of the sun or the lamp, the cool of the moon. It is what a near-black coat shows when
 *   lit, where a multiply cannot (plan section 3.4).
 * - [flatten] compresses the coat ramp toward its middle (tone 0 drawn as 1, tone 3 as 2): the
 *   baked upper-left light is played down when the light comes from the other side (option A of
 *   section 4.4, for a mirrored cat).
 * - [virtual] lets the coat go one tone past either end of its ramp: below the darkest a quarter of
 *   the way toward the outline (only where the outline is darker, so a black coat is not lifted by
 *   "shadow"), above the lightest a quarter of the way toward the light's colour (the sheen).
 */
class CatLight(
    val shadeOffset: Int = 0,
    val tint: Int = 0xFFFFFF,
    val half: Int = 0,
    val wash: Int = 0,
    val flatten: Boolean = false,
    val virtual: Boolean = false,
) {
    init {
        require(half in -1..1) { "half is -1, 0 or 1, not $half" }
    }

    /** True if this light changes nothing: the output is exactly [Recolour]'s. */
    val neutral: Boolean
        get() =
            shadeOffset == 0 &&
                (tint and 0xFFFFFF) == 0xFFFFFF &&
                half == 0 &&
                (wash ushr 24) == 0 &&
                !flatten

    companion object {
        val NEUTRAL = CatLight()
    }
}

/**
 * [Recolour] for a [SplitSheet] or an [IndexSheet]: base plus shade, looked up in the palette, with
 * a [CatLight] moving the shade and tinting the result. With [CatLight.NEUTRAL] it produces exactly
 * the pixels [Recolour.region] produces for the sheet. Hand-drawn art is never touched: only which
 * tone of a ramp is looked up changes, and only for ramps [CoatLook] calls healthy.
 *
 * Every light is a table of 256 colours, one per index (two with [CatLight.half]: the checker's
 * other half), so lighting a frame is the same table lookup [Recolour] does.
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
        val lo = table(palette, light, look, 0)
        val hi = if (light.half != 0) table(palette, light, look, light.half) else lo
        val out = IntArray(rect.width * rect.height)
        for (y in 0 until rect.height) {
            val sy = rect.y + y
            val from = sy * split.width + rect.x
            for (x in 0 until rect.width) {
                val index = split.baseAt(from + x) + 10 * split.shadeAt(from + x)
                val t = if (((rect.x + x + sy) and 1) == 0) hi else lo
                out[y * rect.width + x] = if (index in 0..255) t[index] else CatPalette.CLEAR
            }
        }
        return ArgbImage(rect.width, rect.height, out)
    }

    /** The part of [sheet] inside [rect] in [palette] under [light] (no split needed). */
    fun region(
        sheet: IndexSheet,
        palette: CatPalette,
        rect: FrameRect,
        light: CatLight = CatLight.NEUTRAL,
        look: CoatLook = CoatLook.of(palette),
    ): ArgbImage {
        val lo = table(palette, light, look, 0)
        val hi = if (light.half != 0) table(palette, light, look, light.half) else lo
        val out = IntArray(rect.width * rect.height)
        into(sheet, rect, lo, hi, out, rect.width)
        return ArgbImage(rect.width, rect.height, out)
    }

    /**
     * Writes the part of [sheet] inside [rect] into [out] ([stride] pixels a row, from 0): [hi] on
     * the checker's pixels (where sheet x + y is even), [lo] elsewhere; pass the same table twice
     * for no dithering. Allocates nothing.
     */
    fun into(
        sheet: IndexSheet,
        rect: FrameRect,
        lo: IntArray,
        hi: IntArray,
        out: IntArray,
        stride: Int,
    ) {
        require(
            rect.x >= 0 &&
                rect.y >= 0 &&
                rect.x + rect.width <= sheet.width &&
                rect.y + rect.height <= sheet.height
        ) {
            "$rect is outside the ${sheet.width}x${sheet.height} sheet"
        }
        for (y in 0 until rect.height) {
            val sy = rect.y + y
            val from = sy * sheet.width + rect.x
            for (x in 0 until rect.width) {
                val index = sheet.indices[from + x]
                val t = if (((rect.x + x + sy) and 1) == 0) hi else lo
                out[y * stride + x] = if (index in 0..255) t[index] else CatPalette.CLEAR
            }
        }
    }

    /**
     * The colour of every index 0..255 under [light], with [extra] tones added to its offset (the
     * dither's second table). With a neutral light this is the palette itself.
     */
    fun table(palette: CatPalette, light: CatLight, look: CoatLook, extra: Int = 0): IntArray {
        if (light.neutral && extra == 0) return IntArray(256) { palette.argb(it) }
        return IntArray(256) { lit(it, palette, light, look, light.shadeOffset + extra) }
    }

    private fun lit(
        index: Int,
        palette: CatPalette,
        light: CatLight,
        look: CoatLook,
        offset: Int,
    ): Int {
        val family = ShadeSplit.family(index)
        val drawn = palette.argb(index)
        if (drawn == CatPalette.CLEAR) return drawn
        var argb = drawn
        if (family != ShadeFamily.FIXED && look.healthy(family)) {
            var level = (index - family.base) / 10
            if (light.flatten && family == ShadeFamily.COAT) level = level.coerceIn(1, 2)
            val moved = level + shift(family, offset, look)
            argb =
                when {
                    light.virtual && family == ShadeFamily.COAT && moved >= family.levels ->
                        mix(
                            palette.argb(ShadeSplit.indexOf(family, family.levels - 1)),
                            sheenOf(light),
                            VIRTUAL_MIX,
                        )
                    light.virtual && family == ShadeFamily.COAT && moved < 0 -> below(palette, look)
                    else ->
                        palette.argb(ShadeSplit.indexOf(family, moved)).takeIf {
                            it != CatPalette.CLEAR
                        } ?: drawn
                }
        }
        argb = tinted(argb, light.tint)
        val a = light.wash ushr 24
        return if (a == 0) argb else mix(argb, light.wash and 0xFFFFFF, a / 255.0)
    }

    /** The virtual tone under the coat's darkest: toward the outline, if the outline is darker. */
    private fun below(palette: CatPalette, look: CoatLook): Int {
        val darkest = palette.argb(ShadeSplit.indexOf(ShadeFamily.COAT, 0))
        val outline = palette.argb(OUTLINE)
        if (outline == CatPalette.CLEAR) return darkest
        val coatLum = look.luminance(ShadeFamily.COAT, 0)
        return if (CoatLook.luminanceOf(outline) < coatLum)
            mix(darkest, outline and 0xFFFFFF, VIRTUAL_MIX)
        else darkest
    }

    /** The colour a sheen goes toward: the light's own, or white. */
    private fun sheenOf(light: CatLight): Int =
        if ((light.wash ushr 24) == 0) 0xFFFFFF else light.wash and 0xFFFFFF

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

    /** [argb] mixed [t] of the way toward [rgb], each channel rounded half up; alpha kept. */
    private fun mix(argb: Int, rgb: Int, t: Double): Int {
        fun channel(shift: Int): Int {
            val a = (argb shr shift) and 255
            val b = (rgb shr shift) and 255
            return Math.floor(a + (b - a) * t + 0.5).toInt().coerceIn(0, 255)
        }
        return (argb and 0xFF000000.toInt()) or
            (channel(16) shl 16) or
            (channel(8) shl 8) or
            channel(0)
    }

    /** The most tones lighter than drawn the coat may be moved. */
    const val MAX_UP = 1

    /** How far a virtual tone goes toward the outline or the light: a quarter. */
    const val VIRTUAL_MIX = 0.25

    private const val OUTLINE = 10
}
