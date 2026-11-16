package com.lifemax.rpg.feature.pet.presentation

import com.lifemax.rpg.feature.pet.art.CatLightKey
import com.lifemax.rpg.feature.pet.art.RigCompose
import com.lifemax.rpg.feature.pet.art.RigFrames
import com.lifemax.rpg.feature.pet.art.RigMorph
import com.lifemax.rpg.feature.pet.art.RigSet
import com.lifemax.rpg.feature.pet.body.CatPose
import kotlin.math.floor

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
 * frame) but a lift of the back: the rows above [cut] behind the head ([head]) rise by [rise] whole
 * device pixels, the few rows just above the cut sharing the stretch and the few columns next to
 * the head lifting less, while the head, the face, the ears and everything under the cut keep their
 * very pixels. A resting cat therefore draws the very same pixels from one frame to the next,
 * changes only when the art's frame, a breath step or a rig step changes, and a breath step moves
 * only the back.
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
     * The first picture column of the head, which stays still while the cat breathes (the art faces
     * right; mirroring is the transform's): [width] when the art does not say where the head is.
     */
    var head = 0
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
        head = headOf(pose, rig, frameWidth, marginLeft)
    }

    private fun headOf(pose: CatPose, rig: RigSet?, frameWidth: Int, marginLeft: Int): Int {
        if (rig == null || !rig.has(pose.clip)) return width
        val morph = if (slot >= 0) rig.morph else RigMorph.NEUTRAL
        val column = RigCompose.headColumn(rig.rigFrame(pose.clip, pose.frame), morph, frameWidth)
        if (column < 0) return width
        return (column - SteadyTuning.HEAD_BACK_COLUMNS + marginLeft).coerceIn(0, width)
    }

    /**
     * The pieces of the picture to draw, at [s] device pixels to an art pixel, into [out] ([BAND]
     * numbers each: the first picture row, the row after the piece, the piece's top in device
     * pixels below the paws before the transform, its height in device pixels, its first picture
     * column and the column after it); returns how many. A breath that has not risen is the whole
     * picture in one piece, as it always was. Otherwise the pieces are side by side and one above
     * the other without overlapping, so their order does not matter: the head's columns and the
     * rows under the cut in one piece each, unmoved; the back in a piece lifted [rise] pixels over
     * a few rows that share the stretch ([SteadyTuning.BREATH_EASE_ROWS]); and between back and
     * head a few one-column strips lifted less and less ([SteadyTuning.BREATH_EASE_COLUMNS]). Every
     * piece of more than one row is moved, never stretched, so it draws the art's own pixels.
     */
    fun bands(s: Int, out: IntArray): Int {
        val y0 = top * s
        if (rise == 0) {
            out.put(0, 0, height, y0, height * s, 0, width)
            return 1
        }
        var n = 0
        val still = head.coerceIn(0, width)
        val ease = minOf(SteadyTuning.BREATH_EASE_COLUMNS, still)
        val back = still - ease
        if (still > 0) n = out.put(n, cut, height, y0 + cut * s, (height - cut) * s, 0, still)
        if (back > 0) n = lifted(n, s, out, 0, back, 1.0)
        for (k in 0 until ease) {
            val c = back + k
            n = lifted(n, s, out, c, c + 1, (ease - k).toDouble() / (ease + 1))
        }
        if (still < width) n = out.put(n, 0, height, y0, height * s, still, width)
        return n
    }

    /**
     * The rows above the cut of columns [from] until [to], lifted [share] of the breath: the rows
     * above the eased ones in one piece, then each eased row on its own.
     */
    private fun lifted(n0: Int, s: Int, out: IntArray, from: Int, to: Int, share: Double): Int {
        val rows = minOf(SteadyTuning.BREATH_EASE_ROWS, cut)
        val full = cut - rows
        var n = n0
        if (full > 0) n = out.piece(n, s, 0, full, from, to, share, rows)
        for (r in full until cut) n = out.piece(n, s, r, r + 1, from, to, share, rows)
        return n
    }

    private fun IntArray.piece(
        n: Int,
        s: Int,
        rowFrom: Int,
        rowTo: Int,
        from: Int,
        to: Int,
        share: Double,
        rows: Int,
    ): Int {
        val up = lift(rowFrom, share, rows)
        val dstHeight = (rowTo - rowFrom) * s + up - lift(rowTo, share, rows)
        if (dstHeight <= 0) return n
        return put(n, rowFrom, rowTo, (top + rowFrom) * s - up, dstHeight, from, to)
    }

    /** Device pixels the top edge of picture row [edge] rises in a column lifted [share]. */
    private fun lift(edge: Int, share: Double, rows: Int): Int {
        val ease = if (rows == 0) 1.0 else ((cut - edge).toDouble() / rows).coerceIn(0.0, 1.0)
        val v = rise * share * ease
        return (if (v >= 0) floor(v + HALF) else -floor(-v + HALF)).toInt()
    }

    /** Write piece [band]; returns the next piece's number. */
    private fun IntArray.put(
        band: Int,
        from: Int,
        to: Int,
        dstTop: Int,
        dstHeight: Int,
        colFrom: Int,
        colTo: Int,
    ): Int {
        val i = band * BAND
        this[i] = from
        this[i + 1] = to
        this[i + 2] = dstTop
        this[i + 3] = dstHeight
        this[i + 4] = colFrom
        this[i + 5] = colTo
        return band + 1
    }

    companion object {
        /** Numbers per piece in [bands]. */
        const val BAND = 6

        /** The most pieces a drawing has: under the cut, the head, and the back's eased strips. */
        const val MAX_BANDS =
            2 + (1 + SteadyTuning.BREATH_EASE_COLUMNS) * (1 + SteadyTuning.BREATH_EASE_ROWS)

        private const val HALF = 0.5
    }
}
