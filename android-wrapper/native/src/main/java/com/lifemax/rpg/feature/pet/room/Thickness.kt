package com.lifemax.rpg.feature.pet.room

import com.lifemax.rpg.feature.pet.art.ArgbImage
import kotlin.math.abs
import kotlin.math.floor

/** The numbers behind the slight thickness of a piece: how thick, how dark. */
internal object FaceTuning {
    /** The top face at rest, in art pixels: the piece's silhouette copied this far up. */
    const val TOP_PX = 1

    /** The side face at rest, in art pixels (to the right: the light comes from the left). */
    const val SIDE_REST_PX = 2

    /**
     * How much more side face the camera sway shows, per unit of sway (it grows on the near side).
     */
    const val SIDE_SWAY_PX = 3f

    /** The face is never thicker than this, in art pixels, whatever the sway. */
    const val MAX_PX = 6

    /** How dark the face is: its colour is the piece's own at this many percent. */
    const val SHADE_PERCENT = 56
}

/**
 * Slight thickness, the cheap honest way: a piece's side and top face is a darker copy of its own
 * silhouette, pushed back by a few art pixels and drawn under the piece. There is no perspective:
 * the copy is only moved, never scaled or warped, and is never more than [FaceTuning.MAX_PX] pixels
 * out, so the cat tree reads as a tree with a trunk, not as a box.
 *
 * The face is the sweep of the silhouette along a vector ([vector]) minus the silhouette itself.
 * The room draws it as the dark atlas copy of the piece at each of the sweep's steps ([offsets]),
 * farthest first, and then the piece over them; [face] is the same thing as a mask, which is what
 * the tests and the previews check the drawing against.
 */
internal object Thickness {
    /**
     * The face vector for [sway], in art pixels, into `out[0]` (x) and `out[1]` (y; up is
     * negative): a little to the right and up at rest, shifting as the camera sways (a back plane
     * moves less than a front one, so the face slides the other way), clamped to
     * [FaceTuning.MAX_PX].
     */
    fun vector(sway: Float, out: IntArray) {
        val vx = FaceTuning.SIDE_REST_PX - floor(sway * FaceTuning.SIDE_SWAY_PX + 0.5f).toInt()
        out[0] = vx.coerceIn(-FaceTuning.MAX_PX, FaceTuning.MAX_PX)
        out[1] = -FaceTuning.TOP_PX
    }

    /**
     * The steps of the sweep along ([vx], [vy]): one-pixel steps from just off the piece to the
     * full vector, as x, y pairs in `out` (farthest first). Returns how many steps there are (0 for
     * a zero vector). `out` needs room for `2 * (max(|vx|, |vy|))` numbers.
     */
    fun offsets(vx: Int, vy: Int, out: IntArray): Int {
        val steps = maxOf(abs(vx), abs(vy))
        for (i in 0 until steps) {
            val t = steps - i
            out[2 * i] = Math.floorDiv(2 * vx * t + steps, 2 * steps)
            out[2 * i + 1] = Math.floorDiv(2 * vy * t + steps, 2 * steps)
        }
        return steps
    }

    /**
     * The face of [sprite] along ([vx], [vy]) as a mask over the sprite's box grown to hold it:
     * true where the swept silhouette is and the silhouette itself is not. The box starts at
     * (`min(0, vx)`, `min(0, vy)`) relative to the sprite and is `width + |vx|` by `height + |vy|`.
     */
    fun face(sprite: ArgbImage, vx: Int, vy: Int): BooleanArray {
        val w = sprite.width + abs(vx)
        val h = sprite.height + abs(vy)
        val x0 = minOf(0, vx)
        val y0 = minOf(0, vy)
        val mask = BooleanArray(w * h)
        val steps = IntArray(2 * maxOf(abs(vx), abs(vy)))
        val n = offsets(vx, vy, steps)
        for (y in 0 until sprite.height) for (x in 0 until sprite.width) {
            if (sprite[x, y] ushr 24 == 0) continue
            for (i in 0 until n) {
                val px = x + steps[2 * i] - x0
                val py = y + steps[2 * i + 1] - y0
                mask[py * w + px] = true
            }
        }
        for (y in 0 until sprite.height) for (x in 0 until sprite.width) {
            if (sprite[x, y] ushr 24 != 0) mask[(y - y0) * w + (x - x0)] = false
        }
        return mask
    }

    /**
     * [argb] shaded to the face colour: its own colour at [FaceTuning.SHADE_PERCENT] percent, alpha
     * kept.
     */
    fun shade(argb: Int): Int {
        val a = argb ushr 24
        if (a == 0) return 0
        fun ch(shift: Int) = ((argb shr shift) and 255) * FaceTuning.SHADE_PERCENT / 100
        return (a shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
