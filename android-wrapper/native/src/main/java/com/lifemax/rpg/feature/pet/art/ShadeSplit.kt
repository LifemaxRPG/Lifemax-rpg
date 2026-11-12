package com.lifemax.rpg.feature.pet.art

/**
 * What a cat sheet's pixel is made of: a base colour and a shade. The sheets bake their shading
 * into the index (20 to 50 is the coat from dark to light, drawn lit from the upper left); the
 * split separates "which material" ([SplitSheet.base]) from "how lit" ([SplitSheet.shade]), so a
 * light can later move the shade without touching the hand-drawn art. Nothing here changes a pixel:
 * [ShadeSplit.join] of [ShadeSplit.split] is the sheet it started as.
 */
enum class ShadeFamily(
    /** The index of shade level 0, the darkest tone of the ramp (-1 for [FIXED]). */
    val base: Int,
    /** How many tones the ramp has. */
    val levels: Int,
) {
    /** The coat ramp, 20 to 50, dark to light. */
    COAT(20, 4),

    /** Stripes and markings, 60 to 80, darker to lighter. */
    STRIPE(60, 3),

    /** White patches, 90 to 110. */
    WHITE(90, 3),

    /** Nose, ears and mouth, 120 to 140. */
    PINK(120, 3),

    /** Outline, pupil, iris, details and any value not listed: never shaded. */
    FIXED(-1, 1),
}

/** A sheet split into base indices and shade levels, both `width * height`, row by row. */
class SplitSheet(val width: Int, val height: Int, val base: ByteArray, val shade: ByteArray) {
    init {
        require(base.size == width * height && shade.size == width * height) {
            "a split sheet of ${width}x$height needs ${width * height} pixels"
        }
    }

    /** The base index (0..255) of pixel [i]. */
    fun baseAt(i: Int): Int = base[i].toInt() and 255

    /** The shade level of pixel [i]. */
    fun shadeAt(i: Int): Int = shade[i].toInt()
}

/**
 * The split rule: a pure table over the 256 index values. Every ramp is a run of tens, so a ramp
 * index is `family base + 10 * level`; a value in no ramp keeps itself as its base with shade 0,
 * which is why the split loses nothing for a sheet the table has never seen.
 */
object ShadeSplit {
    private val familyOf = Array(256) { ShadeFamily.FIXED }
    private val baseOf = IntArray(256) { it }
    private val levelOf = IntArray(256)

    init {
        for (family in ShadeFamily.entries) {
            if (family == ShadeFamily.FIXED) continue
            for (level in 0 until family.levels) {
                val index = family.base + 10 * level
                familyOf[index] = family
                baseOf[index] = family.base
                levelOf[index] = level
            }
        }
    }

    /** The family of palette index [index]; [ShadeFamily.FIXED] for anything not in a ramp. */
    fun family(index: Int): ShadeFamily =
        if (index in 0..255) familyOf[index] else ShadeFamily.FIXED

    /** The index of [level] in [family]'s ramp; [level] is clamped into the ramp. */
    fun indexOf(family: ShadeFamily, level: Int): Int {
        require(family != ShadeFamily.FIXED) { "a fixed index has no ramp" }
        return family.base + 10 * level.coerceIn(0, family.levels - 1)
    }

    /**
     * Splits [sheet]. Throws [IllegalArgumentException] for a value outside 0..255 (a decoded 8-bit
     * PNG cannot hold one).
     */
    fun split(sheet: IndexSheet): SplitSheet {
        val n = sheet.indices.size
        val base = ByteArray(n)
        val shade = ByteArray(n)
        for (i in 0 until n) {
            val v = sheet.indices[i]
            require(v in 0..255) { "sheet value $v is not 0..255" }
            base[i] = baseOf[v].toByte()
            shade[i] = levelOf[v].toByte()
        }
        return SplitSheet(sheet.width, sheet.height, base, shade)
    }

    /** The sheet [split] was made from. */
    fun join(split: SplitSheet): IndexSheet =
        IndexSheet(
            split.width,
            split.height,
            IntArray(split.base.size) { split.baseAt(it) + 10 * split.shadeAt(it) },
        )
}
