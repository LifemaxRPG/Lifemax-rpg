package com.lifemax.rpg.feature.pet.room

import com.lifemax.rpg.feature.pet.art.ArgbImage
import kotlin.math.abs
import kotlin.math.atan2

/**
 * One piece of furniture as a small solid: its painted sprite, and for every painted pixel a column
 * of solid running from [front] to [back], in floor rows behind the piece's front plane
 * (`tools/pet_room_volume.js` writes the depth maps from measured profiles). Seen straight on, as
 * the camera at rest sees it, only the fronts show and the solid is the sprite exactly; the sway
 * shows the sides and tops between columns of different depth ([VolumeRaster]).
 *
 * @property colours the painted sprite, `0xAARRGGBB`; a clear pixel has no column.
 * @property front where each column starts, in floor rows behind the front plane.
 * @property back where each column ends (always more than its front).
 */
internal class VolumeModel(
    val width: Int,
    val height: Int,
    val colours: IntArray,
    val front: FloatArray,
    val back: FloatArray,
) {
    /** The piece's own colours and how they shade into each other. */
    val palette = Palette(colours)

    /** Each pixel's colour as an index into [palette], -1 where the sprite is clear. */
    val index = IntArray(colours.size) { if (solid(it)) palette.indexOf(colours[it]) else -1 }

    /** The deepest any column reaches. */
    val depth: Float = back.indices.maxOfOrNull { if (solid(it)) back[it] else 0f } ?: 0f

    /** Whether pixel [i] has a column. */
    fun solid(i: Int): Boolean = colours[i] ushr 24 != 0

    companion object {
        /** Depth maps hold eighths of a floor row: red is the front, green the back. */
        const val UNIT = 8f

        /** The model of [sprite] with the depth map [map] (the same size). */
        fun decode(sprite: ArgbImage, map: ArgbImage): VolumeModel {
            require(sprite.width == map.width && sprite.height == map.height) {
                "a depth map is the size of its sprite"
            }
            val n = sprite.pixels.size
            val front = FloatArray(n)
            val back = FloatArray(n)
            for (i in 0 until n) {
                val m = map.pixels[i]
                val painted = sprite.pixels[i] ushr 24 != 0
                require(painted == (m ushr 24 != 0)) { "a depth map covers its sprite exactly" }
                front[i] = ((m shr 16) and 255) / UNIT
                back[i] = ((m shr 8) and 255) / UNIT
                require(!painted || back[i] > front[i]) { "a column ends behind its front" }
            }
            return VolumeModel(sprite.width, sprite.height, sprite.pixels, front, back)
        }
    }
}

/** The numbers that decide which of a piece's colours shade into which. */
internal object RampTuning {
    /** A colour darker than this (luminance 0 to 255) is ink: an outline, never a shade. */
    const val INK_LUMINANCE = 40

    /** A colour with less chroma than this is a grey; greys shade into greys. */
    const val GREY_CHROMA = 24

    /** Two coloured colours shade into each other when their hues are this close (degrees). */
    const val HUE_TOLERANCE = 30f

    /** A shade never jumps more than this much luminance from the colour it shades. */
    const val MAX_JUMP = 110

    /** The art's own outline colour (palette index 10 of the cats). */
    const val ART_INK = 0xFF1E162D.toInt()
}

/**
 * A piece's own colours arranged into ramps, the way a pixel artist shades: a face turned from the
 * light takes the next darker colour of the same ramp (the blue of the sofa's cushions steps down
 * to the blue of its shadows), a face turned to the light the next lighter one. A shade never
 * leaves the piece's palette, so a rendered piece is drawn only in the colours it was painted in.
 *
 * Ramps are found from the colours: greys go with greys, other colours with those of a close hue,
 * ordered by luminance. Ink (the outline) is never a shade; [ink] is the colour a piece outlines a
 * new edge in: the art's outline colour if the piece has it, else its darkest colour.
 */
internal class Palette(pixels: IntArray) {
    /** The distinct opaque colours, in the order first met. */
    val colours: IntArray

    private val luminance: IntArray
    private val ramps: Array<IntArray>
    private val rank: IntArray

    /** The palette index of the colour a new edge is outlined in. */
    val ink: Int

    init {
        val seen = LinkedHashSet<Int>()
        for (p in pixels) if (p ushr 24 != 0) seen.add(p or OPAQUE)
        colours = seen.toIntArray()
        luminance = IntArray(colours.size) { luminanceOf(colours[it]) }
        ramps = Array(colours.size) { i -> rampOf(i) }
        rank = IntArray(colours.size) { i -> ramps[i].indexOf(i) }
        val art = colours.indexOf(RampTuning.ART_INK)
        ink = if (art >= 0) art else colours.indices.minByOrNull { luminance[it] } ?: 0
    }

    /** The palette index of [argb] (alpha ignored), or -1 if the piece was not painted with it. */
    fun indexOf(argb: Int): Int = colours.indexOf(argb or OPAQUE)

    /**
     * The colour [steps] places along [index]'s ramp (negative darker, positive lighter), stopping
     * at the ramp's end and at a jump in luminance too big to be a shade.
     */
    fun step(index: Int, steps: Int): Int {
        val ramp = ramps[index]
        var at = rank[index]
        val dir = if (steps < 0) -1 else 1
        repeat(abs(steps)) {
            val next = at + dir
            if (next !in ramp.indices) return ramp[at]
            if (abs(luminance[ramp[next]] - luminance[ramp[at]]) > RampTuning.MAX_JUMP) {
                return ramp[at]
            }
            at = next
        }
        return ramp[at]
    }

    private fun rampOf(i: Int): IntArray {
        if (isInk(i)) return intArrayOf(i)
        val mates = colours.indices.filter { j -> !isInk(j) && mates(i, j) }
        return mates.sortedWith(compareBy({ luminance[it] }, { it })).toIntArray()
    }

    private fun isInk(i: Int) = luminance[i] < RampTuning.INK_LUMINANCE

    private fun mates(i: Int, j: Int): Boolean {
        val gi = chroma(colours[i]) < RampTuning.GREY_CHROMA
        val gj = chroma(colours[j]) < RampTuning.GREY_CHROMA
        if (gi || gj) return gi && gj
        val d = abs(hue(colours[i]) - hue(colours[j]))
        return minOf(d, 360f - d) <= RampTuning.HUE_TOLERANCE
    }

    companion object {
        private const val OPAQUE = 0xFF000000.toInt()

        /** Luminance 0 to 255 (Rec. 601 weights). */
        fun luminanceOf(argb: Int): Int {
            val r = (argb shr 16) and 255
            val g = (argb shr 8) and 255
            val b = argb and 255
            return (299 * r + 587 * g + 114 * b) / 1000
        }

        private fun chroma(argb: Int): Int {
            val r = (argb shr 16) and 255
            val g = (argb shr 8) and 255
            val b = argb and 255
            return maxOf(r, g, b) - minOf(r, g, b)
        }

        /** Hue in degrees, 0 to 360. */
        private fun hue(argb: Int): Float {
            val r = ((argb shr 16) and 255).toDouble()
            val g = ((argb shr 8) and 255).toDouble()
            val b = (argb and 255).toDouble()
            val h = Math.toDegrees(atan2(Math.sqrt(3.0) * (g - b), 2 * r - g - b)).toFloat()
            return if (h < 0f) h + 360f else h
        }
    }
}
