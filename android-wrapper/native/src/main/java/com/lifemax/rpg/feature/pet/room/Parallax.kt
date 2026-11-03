package com.lifemax.rpg.feature.pet.room

import com.lifemax.rpg.feature.pet.body.World
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Parallax: how many whole art pixels a point of the room moves for a camera sway. Depth is
 * measured in floor rows, the row of floor a point stands on (the back wall is [WALL_ROW], the
 * glass is below the picture): the wall and everything on it moves by the level's wall gain, and a
 * point standing on row `r` moves `depthGain` more per row in front of the wall. The same rule
 * serves the floor (drawn row by row, so it shears and nothing slides on it), the furniture (its
 * front plane moves by its row, its back by the row it reaches back to), the floor props and the
 * cats, so a cat standing at a piece's depth moves exactly as the piece does. The sky does not
 * move.
 *
 * Everything is rounded half up to whole pixels, so the art never shimmers between pixels.
 */
internal object Parallax {
    /** The row where the back wall meets the floor (`World.floorY(0)`, 116.7, rounded up). */
    const val WALL_ROW = 117f

    /** The gain at floor row [row], in pixels per unit of sway. */
    fun gainAtRow(row: Float, level: DepthLevel): Float =
        level.wallGain + level.depthGain * (row - WALL_ROW).coerceAtLeast(0f)

    /** Whole pixels of shift for [sway] at [gain], rounded half up. */
    fun shift(sway: Float, gain: Float): Int = floor(sway * gain + 0.5f).toInt()

    /** Whole pixels of shift of the wall for [sway]. */
    fun wallShift(sway: Float, level: DepthLevel): Int = shift(sway, level.wallGain)

    /** Whole pixels of shift of a point standing on floor row [row] for [sway]. */
    fun shiftAtRow(sway: Float, row: Float, level: DepthLevel): Int =
        shift(sway, gainAtRow(row, level))

    /** Whole pixels of shift of a floor object (a cat, a prop) at depth [z] for [sway]. */
    fun shiftAtZ(sway: Float, z: Float, level: DepthLevel): Int =
        shiftAtRow(sway, World.floorY(z), level)

    /** The most the wall can shift, in pixels: the margin the sky behind the glass keeps. */
    val maxWallShift: Int
        get() = ceil(DepthLevel.RICH.wallGain.toDouble()).toInt()
}

/**
 * The page's depth maths run backwards: the depth z at which feet stand on screen row [y]
 * (`petZFromY`). A piece of furniture whose lowest pixel is on row y sorts with the cats at this
 * depth: a cat deeper than it is behind it.
 */
internal object DepthMath {
    private const val SCALE_AT_BACK_PCT = 55f
    private const val SCALE_RANGE_PCT = 130f

    /** The depth of feet on row [y], clamped to the room's 0 to 100. */
    fun zFromRow(y: Float): Float {
        val scale = (y - World.HORIZON_Y) / (World.FLOOR_Y_ART - World.HORIZON_Y) * 100f
        return ((scale - SCALE_AT_BACK_PCT) * 100f / SCALE_RANGE_PCT).coerceIn(0f, World.MAX_Z)
    }
}
