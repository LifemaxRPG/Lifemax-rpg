package com.lifemax.rpg.feature.pet.room

import kotlin.math.PI
import kotlin.math.exp

/**
 * The numbers behind the camera-follow parallax: what the room camera's pan does to the depth sway.
 * The owner's words: the room should "move slightly when the camera moves with the cat".
 *
 * The mapping, in one place (sway units: -1 to 1, the same as the idle drift; a unit of sway moves
 * a point on floor row `r` by [Parallax.gainAtRow] pixels):
 * - **Where the camera is.** The screen's middle, in world x, is eased by [VIEW_TAU_S] into the
 *   *view* x (an exponential follower: frame-rate independent, and a camera cut becomes a short
 *   glide instead of a jump).
 * - **Pan (shifts, the floor, the cats).** The pan target is `-POSITION_GAIN * clamp((view -
 *   CENTRE_X) / RANGE_PX, -1, 1) - VELOCITY_GAIN * speed / 100`, limited to +-[LIMIT], where
 *   `speed` is the view's speed in px/s (`(screen - view) / VIEW_TAU_S`: the follower's own
 *   velocity, no finite difference). A camera right of the house's middle pushes the near rows left
 *   a little more than the wall, as a nearer layer scrolls faster; a camera moving right leans the
 *   same way for as long as it moves. A damped spring ([NATURAL_HZ], [DAMPING_RATIO]) follows the
 *   target, so the lean arrives and fades softly with no overshoot. It adds to the idle drift:
 *   `DepthSway.position = idle + pan`.
 * - **Perspective (the side a piece shows).** A piece shows the side that faces the middle of the
 *   screen: its yaw is `idle - PERSPECTIVE_GAIN * clamp((view - pieceX) / PERSPECTIVE_RANGE_PX, -1,
 *   1)`, so a piece left of the middle (view > pieceX) shows its right side, and the reverse. Only
 *   the inside of a piece (its sides and tops, and where a cat on its surface stands) turns with
 *   this yaw; its front plane moves with the floor it stands on, so it never slides on the floor.
 *
 * At MEDIUM the sofa (11 rows deep) shows 1.3 px of side at the edge of the screen, and at either
 * end of the house the wall has moved 0.6 px and a cat on the front rows 4 px: flat art stays
 * believable.
 */
internal object PanTuning {
    /** World x where the pan adds nothing: the middle of the house. */
    const val CENTRE_X = RoomLayout.HOUSE_X0 + RoomLayout.HOUSE_WIDTH / 2f

    /**
     * The camera travel from the house's middle to either end that gives the full position gain.
     */
    const val RANGE_PX = 192f

    /** Sway at either end of the house. */
    const val POSITION_GAIN = 0.4f

    /** Sway per 100 px/s of camera speed, against the direction of travel. */
    const val VELOCITY_GAIN = 0.1f

    /** The most the pan adds to the sway, either way. */
    const val LIMIT = 0.5f

    /** The time the view takes to cover 63 per cent of the way to the screen's middle. */
    const val VIEW_TAU_S = 0.3f

    /** The pan spring: natural frequency (Hz) and damping ratio (1: no overshoot). */
    const val NATURAL_HZ = 0.8f
    const val DAMPING_RATIO = 1f

    /** How much yaw a piece at the edge of the perspective range gets (sway units). */
    const val PERSPECTIVE_GAIN = 0.45f

    /** Distance between the view and a piece that gives the full perspective yaw. */
    const val PERSPECTIVE_RANGE_PX = 110f
}

/**
 * The follower behind the camera-follow parallax ([PanTuning] has the mapping): an eased view x,
 * and a damped pan spring. Plain arithmetic on a few floats, stepped by [DepthSway] in its fixed
 * slices, so the result does not depend on the frame rate and a frame allocates nothing.
 */
internal class PanFollow {
    /** The eased x of the middle of the screen, in world px. */
    var viewX = 0f
        private set

    /** The pan the spring has reached, in sway units. */
    var pan = 0f
        private set

    /** How fast the view moves, px/s (positive to the right). */
    var speed = 0f
        private set

    private var panVelocity = 0f
    private var started = false

    /** Put the view on [x] and the pan at its resting value (a cut, or the first look). */
    fun cutTo(x: Float) {
        viewX = x
        speed = 0f
        pan = target()
        panVelocity = 0f
        started = true
    }

    /** Forget everything: the next [advance] starts from where the camera is. */
    fun reset() {
        started = false
        viewX = 0f
        pan = 0f
        panVelocity = 0f
        speed = 0f
    }

    /** One slice of [h] seconds toward the screen middle [screenX]. */
    fun advance(screenX: Float, h: Float) {
        if (!started) cutTo(screenX)
        val toward = screenX - viewX
        speed = toward / PanTuning.VIEW_TAU_S
        viewX += toward * (1f - exp(-h / PanTuning.VIEW_TAU_S))
        val w = (2.0 * PI * PanTuning.NATURAL_HZ).toFloat()
        val acceleration = w * w * (target() - pan) - 2f * PanTuning.DAMPING_RATIO * w * panVelocity
        panVelocity += acceleration * h
        pan += panVelocity * h
    }

    /** Where the pan spring is pulled: the position term plus the lean into the camera's motion. */
    internal fun target(): Float {
        val place = clamp((viewX - PanTuning.CENTRE_X) / PanTuning.RANGE_PX)
        val lean = speed / SPEED_UNIT_PX_S
        val t = -PanTuning.POSITION_GAIN * place - PanTuning.VELOCITY_GAIN * lean
        return t.coerceIn(-PanTuning.LIMIT, PanTuning.LIMIT)
    }

    /**
     * The yaw a piece standing at world x [pieceX] gets from where it sits against the screen's
     * middle: a piece left of the middle shows its right side (negative), and the reverse.
     */
    fun perspective(pieceX: Float): Float =
        -PanTuning.PERSPECTIVE_GAIN * clamp((viewX - pieceX) / PanTuning.PERSPECTIVE_RANGE_PX)

    private fun clamp(v: Float) = v.coerceIn(-1f, 1f)

    private companion object {
        const val SPEED_UNIT_PX_S = 100f
    }
}
