package com.lifemax.rpg.feature.pet.body

import com.lifemax.rpg.feature.pet.room.RoomLayout

/**
 * Where a finger lands in the room, in the room's own pixels (world x, screen row), as the page
 * tests it (`petCatBox` and `petHit` in 16-runtime.js): a cat's box is 52 pixels wide and 41 tall,
 * scaled by its depth, standing on its feet; the food bowl is a small rectangle around its rim.
 * Pure arithmetic: nothing here allocates.
 */
internal object TouchHit {
    /** What a touch landed on besides a cat. */
    const val ROOM = -1
    const val BOWL = -2

    private const val HALF_WIDTH = 26f
    private const val ABOVE_FEET = 38f
    private const val BELOW_FEET = 3f
    private const val BOWL_REACH_X = 10f
    private const val BOWL_ABOVE = 4f
    private const val BOWL_BELOW = 14f

    /** Whether ([x], [y]) is inside the box of the cat in [pose]. */
    fun onCat(pose: CatPose, x: Float, y: Float): Boolean {
        val k = pose.depthScale
        return x >= pose.x - HALF_WIDTH * k &&
            x <= pose.x + HALF_WIDTH * k &&
            y >= pose.y - ABOVE_FEET * k &&
            y <= pose.y + BELOW_FEET * k
    }

    /** Whether ([x], [y]) is on the food bowl. */
    fun onBowl(x: Float, y: Float): Boolean =
        x >= RoomLayout.BOWL_FOOD_X - BOWL_REACH_X &&
            x <= RoomLayout.BOWL_FOOD_X + BOWL_REACH_X &&
            y >= RoomLayout.BOWL_TOP - BOWL_ABOVE &&
            y <= RoomLayout.BOWL_TOP + BOWL_BELOW

    /**
     * The cat under ([x], [y]), else [BOWL] or [ROOM]. Of two cats whose boxes overlap, the one in
     * front (deeper, then lower on the screen) is the one that was drawn on top, so it is the one
     * the finger meets. A cat wins over the bowl: it is in front of it.
     */
    fun at(bodies: Array<CatBody>, x: Float, y: Float): Int {
        var best = ROOM
        for (i in bodies.indices) {
            val pose = bodies[i].pose
            if (!onCat(pose, x, y)) continue
            if (best == ROOM || inFront(pose, bodies[best].pose)) best = i
        }
        if (best != ROOM) return best
        return if (onBowl(x, y)) BOWL else ROOM
    }

    private fun inFront(a: CatPose, b: CatPose): Boolean = a.z > b.z || (a.z == b.z && a.y > b.y)
}
