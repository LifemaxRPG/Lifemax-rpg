package com.lifemax.rpg.feature.pet.room

/**
 * How strong the 2.5D room's depth is: one named level that scales everything together, so the room
 * is judged (and chosen) as a whole and not by a dozen numbers. Every level is the same camera: a
 * point [depthGain] floor rows nearer than the wall moves that much more per unit of sway than the
 * wall does, so the side a piece shows, the way its back lags its front, the floor's shear and a
 * cat's parallax all come from one number and always agree. The levels only change how far the
 * camera swings; the art is never scaled or warped, and at rest every level is the painted room.
 *
 * @property wallGain pixels the wall moves per unit of sway (the sway runs -1 to 1).
 * @property depthGain extra pixels per unit of sway for each floor row a point stands in front of
 *   the wall ([Parallax.WALL_ROW]): the sofa, 11 rows deep, shows `11 * depthGain` pixels of side
 *   at full sway.
 * @property pitch how much of a piece's top the camera shows at full sway, in pixels per floor row
 *   of depth: the camera swings on a shallow arc, a little higher at either end ([pitchAt]).
 * @property idleAmplitude how far the camera's idle drift swings (share of the full sway).
 * @property shadowSize how much larger the contact shadows are, [shadowStrength] how much darker.
 * @property lightStrength the window light on the floor, [rimStrength] the rim light on the cats,
 *   [seamStrength] the dark where the wall meets the floor: each a multiplier on the subtle one.
 */
internal enum class DepthLevel(
    val wallGain: Float,
    val depthGain: Float,
    val pitch: Float,
    val idleAmplitude: Float,
    val shadowSize: Float,
    val shadowStrength: Float,
    val lightStrength: Float,
    val rimStrength: Float,
    val seamStrength: Float,
) {
    /** A whisper: the sofa shows 2 pixels of side at full sway. */
    SUBTLE(1f, 0.18f, 0.1f, 0.45f, 1f, 1f, 1f, 1f, 1f),

    /** The sofa shows 3 pixels of side at full sway, the bookcase its top. */
    MEDIUM(1.5f, 0.27f, 0.16f, 0.55f, 1.25f, 1.1f, 1.3f, 1.25f, 1.25f),

    /** The sofa shows 4 pixels of side at full sway. */
    RICH(2f, 0.36f, 0.22f, 0.65f, 1.5f, 1.2f, 1.6f, 1.5f, 1.5f);

    /** The camera's pitch at [sway]: pixels of top per floor row of depth, 0 at rest. */
    fun pitchAt(sway: Float): Float = pitch * sway * sway
}
