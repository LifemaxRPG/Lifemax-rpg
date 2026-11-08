package com.lifemax.rpg.feature.pet.body

/**
 * The three poses for two cats the art lab ships (`lab.json` `pairs`, built by
 * `tools/pet_art/lib/lab.js`; docs/specs/PET_ART_LAB.md): which row each cat plays and how far cat
 * B's frame origin sits from cat A's, by growth stage. The brain is headless and cannot read the
 * asset, so the offsets are repeated here; PairPosesTest checks them against `lab.json` so the two
 * never drift apart.
 *
 * Offsets are frame pixels with x along A's facing; a frame pixel is a room pixel times the depth
 * scale ([World.depthScale], as the sprite drawing and the mouth anchors scale). A [dy] up the
 * screen (negative) is a little further back in the room.
 */
enum class PairPose(
    /** The row cat A plays. */
    val a: String,
    /** The row cat B plays. */
    val b: String,
    /** B faces A (it is drawn mirrored); false: the same way round. */
    val mirrorB: Boolean,
    /** The frame of A on which the two touch. */
    val contactFrame: Int,
    private val adult: IntArray,
    /** dx, dy by kitten stage 0..5. */
    private val stages: Array<IntArray>,
) {
    /** Two cats curled up together on the sleep row: B lies just behind A, the same way round. */
    SNUGGLE(
        "sleep",
        "sleep",
        false,
        0,
        intArrayOf(-23, -2),
        arrayOf(
            intArrayOf(-15, -1),
            intArrayOf(-17, -1),
            intArrayOf(-18, -1),
            intArrayOf(-19, -2),
            intArrayOf(-21, -2),
            intArrayOf(-22, -2),
        ),
    ),

    /** A licks B's head; B sits facing A, eyes closed, purring. */
    GROOM(
        "groom_other",
        "purr",
        true,
        1,
        intArrayOf(39, 0),
        arrayOf(
            intArrayOf(29, 0),
            intArrayOf(30, 0),
            intArrayOf(32, 0),
            intArrayOf(34, 0),
            intArrayOf(35, 0),
            intArrayOf(37, 0),
        ),
    ),

    /** Two standing cats lean in and touch noses. */
    GREET(
        "nose_touch",
        "nose_touch",
        true,
        2,
        intArrayOf(59, 0),
        arrayOf(
            intArrayOf(37, 0),
            intArrayOf(41, 0),
            intArrayOf(43, 0),
            intArrayOf(47, 0),
            intArrayOf(55, 0),
            intArrayOf(57, 0),
        ),
    );

    companion object {
        /**
         * Two cats on one raised piece (the sofa) stand at the same depth, and the room then draws
         * the second cat (slot 1) over the first (DepthOrder). So on furniture the cat that lies in
         * front in a pose is this slot, and the one behind the other.
         */
        const val DRAWN_FRONT_SLOT = 1
    }

    /** The lab's name of the pose (`lab.json` `pairs` key). */
    val key: String
        get() = name.lowercase()

    /** B's frame offset from A's at growth [stage] (-1 grown, 0..5 a kitten), frame pixels. */
    fun dx(stage: Int): Int = row(stage)[0]

    fun dy(stage: Int): Int = row(stage)[1]

    private fun row(stage: Int): IntArray = if (stage in stages.indices) stages[stage] else adult

    /**
     * How far apart, in room pixels along A's facing, B stands from A at floor depth [z] when A is
     * at [stageA] and B at [stageB]: the pose's offset for each and their mean (two different sizes
     * meet halfway), scaled by depth.
     */
    fun gap(stageA: Int, stageB: Int, z: Float): Float =
        (dx(stageA) + dx(stageB)) / 2f * World.depthScale(z)

    /**
     * How much further back (smaller floor depth) B lies than A at depth [z], from the pose's
     * screen offset: one floor row up the screen is this many depth units back.
     */
    fun backZ(stage: Int, z: Float): Float {
        val rowsPerZ = World.floorY(z + 1f) - World.floorY(z)
        if (rowsPerZ <= 0f) return 0f
        return -dy(stage) / rowsPerZ
    }
}
