package com.lifemax.rpg.feature.pet.room

/**
 * How strong the 2.5D room's depth is: one named level that scales everything together, so the room
 * is judged (and chosen) as a whole and not by a dozen numbers. [SUBTLE] is the first look;
 * [MEDIUM] and [RICH] push the same effects further without changing what they are: the furniture
 * art is only ever moved and darkened, never scaled, skewed or warped.
 *
 * @property wall pixels of parallax per unit of sway for the wall and floor, [back] for furniture
 *   against the wall, [mid] for standing furniture, [front] for near things, [nearest] for a cat at
 *   the glass; the cats and floor props run from [wall] at the back to [nearest] by depth.
 * @property idleAmplitude how far the camera's idle drift swings (share of the full sway).
 * @property maxFacePx the thickest a side face ever is, in art pixels; [faceTopPx] and
 *   [faceSideRestPx] are its top and side at rest and [faceSwayPx] how much more side the sway
 *   shows per unit of sway.
 * @property shadowSize how much larger the contact shadows are, [shadowStrength] how much darker.
 * @property lightStrength the window light on the floor, [rimStrength] the rim light on the cats,
 *   [seamStrength] the dark where the wall meets the floor: each a multiplier on the subtle one.
 */
internal enum class DepthLevel(
    val wall: Float,
    val back: Float,
    val mid: Float,
    val front: Float,
    val nearest: Float,
    val idleAmplitude: Float,
    val maxFacePx: Int,
    val faceTopPx: Int,
    val faceSideRestPx: Int,
    val faceSwayPx: Float,
    val shadowSize: Float,
    val shadowStrength: Float,
    val lightStrength: Float,
    val rimStrength: Float,
    val seamStrength: Float,
) {
    /** What was first built: parallax 1 to 6 px, a face of at most 6 px. */
    SUBTLE(1f, 2f, 3f, 5f, 6f, 0.45f, 6, 1, 2, 3f, 1f, 1f, 1f, 1f, 1f),

    /** About a half more: parallax 2 to 9 px, a face of at most 9 px. */
    MEDIUM(2f, 3f, 5f, 7f, 9f, 0.55f, 9, 2, 3, 5f, 1.25f, 1.1f, 1.3f, 1.25f, 1.25f),

    /** About twice: parallax 3 to 12 px, a face of at most 12 px. */
    RICH(3f, 5f, 7f, 10f, 12f, 0.65f, 12, 2, 4, 7f, 1.5f, 1.2f, 1.6f, 1.5f, 1.5f);

    /** The most any plane or cat shifts, in whole pixels. */
    val maxShift: Int
        get() = Math.ceil(nearest.toDouble()).toInt()
}
