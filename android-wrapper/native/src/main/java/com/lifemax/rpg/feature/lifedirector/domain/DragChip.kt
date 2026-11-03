package com.lifemax.rpg.feature.lifedirector.domain

/** The size of the chip as it was measured, in pixels. */
data class ChipSize(val width: Float, val height: Float) {
    companion object {
        val Zero = ChipSize(0f, 0f)
    }
}

/**
 * Where the small chip that stands for a lifted session is drawn, with no view in it so the
 * placement can be tested with fake sizes. The chip is centred on the finger's column and sits
 * [GAP_DP] above it, so the finger does not cover it; it never leaves the list's sides, and when
 * the finger is too near the top for it to fit above, it goes below instead. It is placed from the
 * finger alone: where the finger took hold of a row or a dial segment does not matter, and what is
 * over the trash is decided by the finger too ([DragModel.hovers]), never by the chip.
 */
object DragChip {
    /** The widest the chip grows, as a share of the list's width. */
    const val MAX_WIDTH_SHARE = .6f

    /**
     * The space between the finger and the chip's near edge, and the margin to the list's sides.
     */
    const val GAP_DP = 36f
    const val SIDE_DP = 12f

    /** The chip's widest, in pixels, on a list [listWidth] pixels wide. */
    fun maxWidth(listWidth: Float): Float = listWidth * MAX_WIDTH_SHARE

    /**
     * The chip's top left for a finger at [finger] on a list [listWidth] pixels wide, [density]
     * pixels per dp. [top] is the highest the chip may go (the list's top edge).
     */
    fun origin(
        finger: DragPoint,
        size: ChipSize,
        listWidth: Float,
        density: Float,
        top: Float = 0f,
    ): DragPoint {
        val side = SIDE_DP * density
        val gap = GAP_DP * density
        val x =
            (finger.x - size.width / 2).coerceIn(side, maxOf(side, listWidth - side - size.width))
        val above = finger.y - gap - size.height
        return DragPoint(x, if (above >= top) above else finger.y + gap)
    }

    /** The top left that puts a chip of [size] centred on [centre]. */
    fun centredOn(centre: DragPoint, size: ChipSize) =
        DragPoint(centre.x - size.width / 2, centre.y - size.height / 2)
}
