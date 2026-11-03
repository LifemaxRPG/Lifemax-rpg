package com.lifemax.rpg.feature.pet.room

import com.lifemax.rpg.feature.pet.body.World
import kotlin.math.floor

/**
 * The planes of the room, from the far wall to the glass. A plane moves by its [Parallax] gain
 * times the camera sway, so the near planes move more than the far ones; the horizon, the camera
 * height and the cat's depth scale are not touched.
 */
internal enum class Plane {
    /** The sky behind the windows: fixed. */
    SKY,

    /** The painted wall and floor (the base of each room) and the flat things lying on it. */
    WALL,

    /** Furniture standing against the wall: the bookcase, the fridge. */
    BACK,

    /** Furniture on the floor at the cats' depth: the sofa, the counter, the cat tree. */
    MID,

    /** Things nearer than the cats: the floor lamp, the plant table. */
    FRONT;

    companion object {
        /** The plane the manifest calls [name] (`floor` is the wall's plane: the same floor). */
        fun of(name: String): Plane =
            when (name) {
                "floor",
                "wall" -> WALL
                "back" -> BACK
                "mid" -> MID
                "front" -> FRONT
                else -> throw IllegalArgumentException("no plane called $name")
            }
    }
}

/**
 * Pixels of shift per unit of sway at each plane (the sway runs -1 to 1, so these are the reach).
 */
internal object ParallaxTuning {
    const val SKY = 0f
    const val WALL = 1f
    const val BACK = 2f
    const val MID = 3f
    const val FRONT = 5f

    /** The most a cat at the glass (the nearest depth) shifts. */
    const val NEAREST = 6f
}

/**
 * Parallax: how many whole art pixels a plane, or a cat at a depth, moves for a sway. Everything is
 * rounded half up to whole pixels (so the art never shimmers between pixels) and the same numbers
 * serve the layers, the floor props and the cats, so a cat standing at a piece's depth moves
 * exactly as the piece does.
 */
internal object Parallax {
    /** The gain of [plane], in pixels per unit of sway. */
    fun gain(plane: Plane, level: DepthLevel = DepthLevel.SUBTLE): Float =
        when (plane) {
            Plane.SKY -> ParallaxTuning.SKY
            Plane.WALL -> level.wall
            Plane.BACK -> level.back
            Plane.MID -> level.mid
            Plane.FRONT -> level.front
        }

    /**
     * The gain of something standing on the floor at depth [z] (0 the back wall, 100 the glass):
     * the wall's at the back, the middle plane's at the art depth where the furniture stands, the
     * nearest at the glass.
     */
    fun gainAtZ(z: Float, level: DepthLevel = DepthLevel.SUBTLE): Float {
        val d = z.coerceIn(0f, World.MAX_Z)
        return if (d <= World.ART_Z) {
            level.wall + (level.mid - level.wall) * d / World.ART_Z
        } else {
            level.mid +
                (level.nearest - level.mid) * (d - World.ART_Z) / (World.MAX_Z - World.ART_Z)
        }
    }

    /** Whole pixels of shift for [sway] at [gain], rounded half up. */
    fun shift(sway: Float, gain: Float): Int = floor(sway * gain + 0.5f).toInt()

    /** Whole pixels of shift of [plane] for [sway]. */
    fun shift(sway: Float, plane: Plane, level: DepthLevel = DepthLevel.SUBTLE): Int =
        shift(sway, gain(plane, level))

    /** Whole pixels of shift of a floor object at depth [z] for [sway]. */
    fun shiftAtZ(sway: Float, z: Float, level: DepthLevel = DepthLevel.SUBTLE): Int =
        shift(sway, gainAtZ(z, level))

    /** The most any plane or cat can shift, in pixels: the margin the sky and the edges keep. */
    val maxShift: Int
        get() = DepthLevel.RICH.maxShift
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
