package com.lifemax.rpg.feature.pet.body

/**
 * The floor the room's solid furniture stands on, so a cat walking on the floor goes round it
 * instead of through it (the owner's "still clipped into sofa").
 *
 * The 2.5D room draws a cat on the floor by the row of its feet: a piece whose front row is nearer
 * is drawn over it. A walk headed straight for its end, in house x and depth, so a cat going from
 * the corner at the sofa's far end (in front of the sofa) to behind the bed cut diagonally through
 * the sofa: from about x -90 on its feet were behind the sofa's front, inside its seat, and by the
 * sofa's right end only its ears showed above the arm. The room drew what the body said, a cat
 * inside the sofa. Now [next] gives a walk a corner to go by: along the front of the piece, past
 * its end, and only then back into the room.
 *
 * Each [Piece] is a piece of `room/layers.json` that stands on the floor, as world x and the floor
 * rows its solid covers (its base row less its volume's front, back by the volume's depth); the cat
 * tree is not here, since the cats walk under its platforms (its own rule is [Occluders.clipped]).
 * FootprintsTest checks the numbers against the shipped manifest.
 */
object Footprints {
    /** A piece's footprint: world x [x0]..[x1], floor rows [back]..[front]. */
    class Piece(val name: String, val x0: Float, val x1: Float, val back: Float, val front: Float) {
        /** The nearest depth whose row is [Tuning.FRONT_MARGIN_ROWS] in front of the piece. */
        val clearZ: Float = World.depthAtRow(front + Tuning.FRONT_MARGIN_ROWS)

        /** Whether a cat with feet at [x], depth [z] ([half] its half width) is inside it. */
        fun inside(x: Float, z: Float, half: Float): Boolean {
            if (x <= x0 - half || x >= x1 + half) return false
            val row = World.floorY(z)
            return row > back && row <= front + CAT_AFTER
        }
    }

    /** The numbers of a walk round furniture. */
    object Tuning {
        /** A cat going round a piece walks this many floor rows in front of it. */
        const val FRONT_MARGIN_ROWS = 2f

        /** Half a walking cat's width at full scale, art pixels (it touches what it passes). */
        const val HALF_W = 10f

        /** A corner is this far (art pixels, at full scale) to the side of the piece's end. */
        const val SIDE_MARGIN = 4f

        /** The most corners one walk goes by. */
        const val MAX_CORNERS = 4
    }

    /** The floor-standing pieces of both rooms but the cat tree (`room/layers.json`). */
    val pieces: List<Piece> =
        listOf(
            Piece("floor_lamp", -183f, -162f, 123f, 129f),
            Piece("sofa", -162f, -30f, 117f, 128f),
            Piece("sofa_cushion_pink", -68f, -52f, 126f, 129f),
            Piece("side_table_plant", -26f, -7f, 120f, 126f),
            Piece("fridge", 206f, 246f, 117f, 128f),
            Piece("counter", 252f, 376f, 117f, 128f),
        )

    /** Half the width of a walking cat at depth [z]. */
    fun halfAt(z: Float): Float = Tuning.HALF_W * World.depthScale(z)

    /** The piece a cat at [x], [z] stands inside, or null. */
    fun insideAny(x: Float, z: Float): Piece? {
        val half = halfAt(z)
        for (i in pieces.indices) if (pieces[i].inside(x, z, half)) return pieces[i]
        return null
    }

    /**
     * The corner a cat on the floor at ([x], [z]) walking to ([x1], [z1]) goes by first, into [out]
     * (x, depth); false when the straight way is clear. A piece the walk ends inside does not count
     * (the cat goes where it was sent; no place the brain picks is inside one).
     */
    fun next(x: Float, z: Float, x1: Float, z1: Float, out: FloatArray): Boolean {
        val half = halfAt(z)
        for (i in pieces.indices) {
            val p = pieces[i]
            if (p.inside(x1, z1, halfAt(z1))) continue
            if (p.inside(x, z, half)) { // started inside (placed there): out to its front first
                return set(out, x, p.clearZ)
            }
            if (!crosses(p, x, z, x1, z1)) continue
            val left = p.x0 - half - Tuning.SIDE_MARGIN
            val right = p.x1 + half + Tuning.SIDE_MARGIN
            val beside = x <= left || x >= right
            val behind = World.floorY(z1) <= p.back && x1 > left && x1 < right
            if (beside && (behind || z < p.clearZ)) {
                // beside the piece: along its side to a place behind it, else out to its front
                return set(out, x, if (behind) z1 else p.clearZ)
            }
            // in front of the piece: along its front to the end on the way
            val end =
                when {
                    x1 >= right -> right
                    x1 <= left -> left
                    x1 - left < right - x1 -> left
                    else -> right
                }
            return set(out, end, maxOf(z, p.clearZ))
        }
        return false
    }

    /**
     * Whether a walk from ([x], [z]) to ([x1], [z1]) may pass inside [p]. A walk is not a straight
     * line: x and depth each ease toward the goal at their own speed, so the cat may reach its
     * depth first and then walk along. Each only ever moves toward the goal, so the walk stays in
     * the box the two points span: the walk is clear when that box is.
     */
    private fun crosses(p: Piece, x: Float, z: Float, x1: Float, z1: Float): Boolean {
        val half = halfAt(maxOf(z, z1))
        if (maxOf(x, x1) <= p.x0 - half || minOf(x, x1) >= p.x1 + half) return false
        val near = World.floorY(maxOf(z, z1))
        val far = World.floorY(minOf(z, z1))
        return near > p.back && far <= p.front + CAT_AFTER
    }

    private fun set(out: FloatArray, x: Float, z: Float): Boolean {
        out[0] = World.clampHouse(x)
        out[1] = Mathx.clamp(z, 0f, World.MAX_Z)
        return true
    }

    /** A cat sorts just after what stands on its own row (DepthOrder's rule). */
    private const val CAT_AFTER = 0.001f
}

/**
 * One floor walk's way round the furniture ([Footprints]): where it ends, and the corner the cat
 * heads for now. Kept by the body; nothing here allocates.
 */
internal class FloorWay {
    /** Where the walk ends. */
    var endX = 0f
        private set

    var endZ = 0f
        private set

    /** Where the cat heads now: a corner on the way, or the end. */
    var goalX = 0f
        private set

    var goalZ = 0f
        private set

    private var corners = 0
    private val corner = FloatArray(2)

    /** True while the cat heads for a corner, not the end. */
    val atCorner: Boolean
        get() = goalX != endX || goalZ != endZ

    /** A new walk from ([x], [z]) to ([x1], [z1]) on the floor. */
    fun begin(x: Float, z: Float, x1: Float, z1: Float) {
        endX = x1
        endZ = z1
        corners = 0
        from(x, z)
    }

    /** A walk on a raised surface: straight to ([x1], [z1]). */
    fun straight(x1: Float, z1: Float) {
        endX = x1
        endZ = z1
        goalX = x1
        goalZ = z1
    }

    /** The cat reached its corner at ([x], [z]): the next corner or the end. */
    fun from(x: Float, z: Float) {
        if (corners < Footprints.Tuning.MAX_CORNERS && Footprints.next(x, z, endX, endZ, corner)) {
            corners++
            goalX = corner[0]
            goalZ = corner[1]
        } else {
            goalX = endX
            goalZ = endZ
        }
    }
}
