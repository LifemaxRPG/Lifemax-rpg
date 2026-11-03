package com.lifemax.rpg.feature.pet.room

import com.lifemax.rpg.feature.pet.art.ArgbImage

/**
 * What the room painter needs from a drawing surface, in house pixels (the room's own pixels, 0 at
 * the house's left edge, 0 at the top of the walls; rows above 0 are the ceiling strip a tall card
 * shows). Two things implement it: the screen's Compose canvas, which scales every pixel to a whole
 * number of device pixels, and the test's software canvas, which writes a 576 x 144 picture that is
 * compared with the web room's own. Both draw nearest-neighbour, never smoothed.
 *
 * Colours are `0xAARRGGBB`, straight (not premultiplied) alpha, blended source over destination.
 */
internal interface RoomCanvas {
    /** Restrict everything that follows to this rectangle, until [unclip]. Clips do not nest. */
    fun clip(x: Int, y: Int, w: Int, h: Int)

    /** Lift the clip set by [clip]. */
    fun unclip()

    /** Fill a rectangle with [argb]. */
    fun fill(x: Int, y: Int, w: Int, h: Int, argb: Int)

    /**
     * Draw the [sw] x [sh] part of [image] at ([sx], [sy]) into the [dw] x [dh] rectangle at ([dx],
     * [dy]), nearest-neighbour (the destination is the source scaled by `dw / sw`, `dh / sh`).
     */
    fun image(
        image: RoomImage,
        sx: Int,
        sy: Int,
        sw: Int,
        sh: Int,
        dx: Int,
        dy: Int,
        dw: Int,
        dh: Int,
    )

    /** Fill the quadrilateral with corners (x0, y0) to (x3, y3) in order, edges anti-aliased. */
    fun quad(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        x3: Float,
        y3: Float,
        argb: Int,
    )

    /** Multiply everything in the rectangle by the colour [rgb] (`0xRRGGBB`): the time of day. */
    fun multiply(rgb: Int, x: Int, y: Int, w: Int, h: Int)

    /**
     * Draw [mask] (source over) with its top left at ([x], [y]). [version] names its contents: a
     * canvas that caches a copy of the pixels keeps it for as long as the version stays the same.
     */
    fun overlay(mask: ArgbImage, version: Int, x: Int, y: Int)

    /**
     * Draw the [sw] x [sh] part of [image] at ([sx], [sy]) with its top left at ([dx], [dy]),
     * unscaled: a picture the room renders while it runs (one camera step of the solid furniture,
     * [VolumeSheets]). A canvas that caches a bitmap of it keeps one per [slot] (0 until
     * [MAX_SHEETS]) for as long as that slot's [version] stays the same.
     */
    fun sheet(
        image: ArgbImage,
        slot: Int,
        version: Int,
        sx: Int,
        sy: Int,
        sw: Int,
        sh: Int,
        dx: Int,
        dy: Int,
    )

    companion object {
        /** How many [sheet] pictures a canvas keeps at once: one per camera step. */
        const val MAX_SHEETS = 2 * SheetTuning.MAX_STEPS_PER_SIDE + 1
    }
}

/**
 * The cats, as the room painter sees them: how many there are, where each sorts in depth, and a
 * call to draw one. The painter draws a cat between the furniture in front of it and behind it.
 */
internal interface CatLayer {
    /** How many cats are in the room (0, 1 or 2). */
    val count: Int

    /** Depth of cat [i]: the web's `z` (0 the back wall, 100 the glass). */
    fun depth(i: Int): Float

    /** Row of cat [i]'s feet, to order two cats at the same depth. */
    fun feet(i: Int): Float

    /** Draw cat [i] and its shadow. */
    fun draw(i: Int)

    /**
     * The key of the surface cat [i] stands on (`floor`, `couch`, `mid`, `top`, ...; the body's
     * [com.lifemax.rpg.feature.pet.body.SurfaceId] keys), or null when it does not say. The 2.5D
     * room draws a cat that stands on a piece of furniture just after that piece.
     */
    fun surface(i: Int): String? = null

    /**
     * Draw cat [i] in the 2.5D room: moved [shift] art pixels sideways by the parallax, with the
     * soft contact shadow and rim light the [style] asks for, painted through [canvas]. A layer
     * that cannot do this (the classic room) draws as [draw] does.
     */
    fun draw(i: Int, shift: Int, style: RoomStyle, canvas: RoomCanvas) = draw(i)
}

/** An empty room: the fidelity test and the room without cats. */
internal object NoCats : CatLayer {
    override val count = 0

    override fun depth(i: Int) = 0f

    override fun feet(i: Int) = 0f

    override fun draw(i: Int) = Unit
}
