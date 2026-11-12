package com.lifemax.rpg.feature.pet.presentation

import com.lifemax.rpg.feature.pet.art.CatLightKey
import com.lifemax.rpg.feature.pet.art.RigCompose
import com.lifemax.rpg.feature.pet.art.RigFrames
import com.lifemax.rpg.feature.pet.art.RigSet
import com.lifemax.rpg.feature.pet.body.CatPose

/**
 * What [drawSpriteAt] draws for one cat at one instant, decided without touching a canvas (so the
 * steadiness tests rasterise exactly this; docs/specs/PET_SPRITE_STEADINESS.md): which picture,
 * where it lies against the paws, the screen transform it is drawn with, and how the breath lifts
 * its back.
 *
 * The rules: the picture is drawn at a whole number of device pixels per art pixel and moved by
 * whole device pixels; the turn, squash and stretch and the rig's grid point are the cat's
 * [SpriteSteady] steps, so they only change when the springs have moved a real step; and the breath
 * is not a scale of the whole picture (which would re-sample a different few rows of the coat every
 * frame) but a lift of the rows above [cut] by [rise] whole device pixels, the row at the cut
 * stretching to close the gap. A resting cat therefore draws the very same pixels from one frame to
 * the next, and changes only when the art's frame, a breath step or a rig step changes.
 */
internal class SpritePlan {
    /** The rig slot of [RigFrames] to draw, or -1 for the sheet's own frame. */
    var slot = -1
        private set

    /**
     * The light key the picture is drawn in ([CatLightKey]): the rig's slot is composed from art
     * lit by it, and without a rig slot the sheet's row lit by it is drawn. Neutral draws the art
     * exactly as it has always been drawn.
     */
    var light = CatLightKey.NEUTRAL
        private set

    /** True if [slot] was composed by this plan (its bitmap is out of date). */
    var fresh = false
        private set

    /** The picture's width and height in art pixels. */
    var width = 0
        private set

    var height = 0
        private set

    /** The picture's left and top edges against the paws, in art pixels. */
    var left = 0
        private set

    var top = 0
        private set

    /** The screen transform about the paws: turn in degrees, scale x (with the mirroring), y. */
    var turnDeg = 0f
        private set

    var scaleX = 1f
        private set

    var scaleY = 1f
        private set

    /** Device pixels the picture rows above [cut] are lifted; 0 draws the picture in one piece. */
    var rise = 0
        private set

    /** The first picture row that stays planted while the cat breathes. */
    var cut = 0
        private set

    /**
     * Decide the drawing of [pose] with [steady] (read here), at [s] device pixels to an art pixel,
     * for a cat with [rig] (or none: the sheet is drawn as it is) whose clip's paws are on frame
     * row [feet] of a [frameWidth] x [frameHeight] frame; rig pictures come from [frames]. With
     * [motion] false the rig's parts stay as drawn.
     */
    fun plan(
        pose: CatPose,
        rig: RigSet?,
        feet: Int,
        frameWidth: Int,
        frameHeight: Int,
        steady: SpriteSteady,
        frames: RigFrames,
        s: Int,
        motion: Boolean,
        light: Int = CatLightKey.NEUTRAL,
    ) {
        steady.read(pose, rig?.kitten ?: false, motion, s)
        this.light = light
        slot =
            if (rig == null) -1 else frames.slotFor(rig, pose.clip, pose.frame, steady.grid, light)
        fresh = slot >= 0 && frames.wasComposed
        val marginLeft = if (slot >= 0) RigCompose.MARGIN_LEFT else 0
        val marginTop = if (slot >= 0) RigCompose.MARGIN_TOP else 0
        width = if (slot >= 0) frames.width else frameWidth
        height = if (slot >= 0) frames.height else frameHeight
        left = -(frameWidth / 2 + marginLeft)
        top = 1 - feet - marginTop
        turnDeg = steady.turnDeg
        scaleX = if (pose.flip) -steady.scaleX else steady.scaleX
        scaleY = steady.scaleY
        rise = steady.rise
        cut = (SpriteSteady.cutRow(pose.clip, feet) + marginTop).coerceIn(1, height - 1)
    }

    /**
     * The bands of the picture to draw, in order, at [s] device pixels to an art pixel, into [out]
     * ([BAND] numbers each: the first picture row, the row after the band, the band's top in device
     * pixels below the paws before the transform, and its height in device pixels); returns how
     * many. A breath that has not risen is the whole picture in one band, as it always was.
     */
    fun bands(s: Int, out: IntArray): Int {
        val y0 = top * s
        if (rise == 0) {
            out.put(0, 0, height, y0, height * s)
            return 1
        }
        out.put(0, cut, height, y0 + cut * s, (height - cut) * s)
        if (rise < 0) {
            out.put(1, 0, cut, y0 - rise, cut * s)
            return 2
        }
        // the row above the cut stretches over the gap the lifted rows leave
        out.put(1, cut - 1, cut, y0 + (cut - 1) * s - rise, s + rise)
        out.put(2, 0, cut - 1, y0 - rise, (cut - 1) * s)
        return MAX_BANDS
    }

    private fun IntArray.put(band: Int, from: Int, to: Int, dstTop: Int, dstHeight: Int) {
        val i = band * BAND
        this[i] = from
        this[i + 1] = to
        this[i + 2] = dstTop
        this[i + 3] = dstHeight
    }

    companion object {
        /** Numbers per band in [bands]. */
        const val BAND = 4

        /** The most bands a drawing has. */
        const val MAX_BANDS = 3
    }
}
