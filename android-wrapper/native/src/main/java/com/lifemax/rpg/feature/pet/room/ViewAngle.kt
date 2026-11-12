package com.lifemax.rpg.feature.pet.room

/**
 * The one tuning object of the live rooms' depth: how strong it starts and the permanent view
 * angle. The owner's report: the 3D room looked flat. At rest the camera was dead on (sway 0), and
 * the layers only moved against each other while the camera panned.
 *
 * [VIEW_ANGLE] is a constant sway the camera rests at, so the layers are always offset from each
 * other: the wall moves [Parallax.wallShift] pixels, a point on floor row `r` moves
 * [Parallax.shiftAtRow], and a piece's yaw (which side it shows) starts from it. Everything is
 * whole pixels and constant at rest (the room draws identical frames), and a cat, its shadow and
 * the floor under it all take the same shift for the same depth, so feet do not slide.
 */
internal object LiveDepthTuning {
    /** The strongest level the live rooms start at (the guard steps down only under overload). */
    val BEST_LEVEL = DepthLevel.RICH

    /** The sway the camera rests at, -1 to 1 (positive: the near rows are shifted right). */
    const val VIEW_ANGLE = 0.6f

    /** The floor row of the front of the room, the nearest layer. */
    const val NEAR_ROW = 144f
}

/** The pixels a view angle puts between the layers of the room, for a level (whole pixels). */
internal class LayerOffsets(val wall: Int, val near: Int) {
    /** The offset between the nearest floor row and the wall. */
    val between: Int
        get() = near - wall

    override fun toString() = "wall $wall px, near $near px (offset $between px)"

    companion object {
        /** Offsets at [sway] (default: the live view angle). */
        fun at(level: DepthLevel, sway: Float = LiveDepthTuning.VIEW_ANGLE) =
            LayerOffsets(
                Parallax.wallShift(sway, level),
                Parallax.shiftAtRow(sway, LiveDepthTuning.NEAR_ROW, level),
            )
    }
}
