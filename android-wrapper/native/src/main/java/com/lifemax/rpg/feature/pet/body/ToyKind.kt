package com.lifemax.rpg.feature.pet.body

import kotlin.math.atan2
import kotlin.math.sqrt

/** How a cat carries a toy in its mouth. */
enum class HoldStyle {
    /** Held between the jaws: the toy sits at the mouth and bobs on a short spring. */
    MOUTH,

    /** Hangs from the mouth by a tail or quill: a damped pendulum on a stiff rod. */
    HANG,
}

/**
 * The toys of the room (`PET_TOYS` in the web room) with the numbers that make each move and hang
 * differently. Sizes are the room atlas sprites' (`ball_a`, `ball_b` 9x9, `mouse` 14x8, `feather`
 * 12x7; the yarn ball and the spring have no atlas sprite yet and carry the web's draw heights, 9
 * and 10). All values are room pixels and seconds; [ToyTuning] holds the world they live in.
 *
 * The motion numbers start from the web's `petToyUpdate`: bounce [restitution] 0.36 (0.62 for the
 * spring) and the per-toy sliding drag [groundDrag] (1.5 for balls, 4.6 for the mouse and feather,
 * 0.9 for yarn). What changed: the web used one bounce for every ball; here the jingle ball is a
 * little livelier, the mouse and feather barely bounce, and the light toys fall slowly and drag
 * through the air ([gravityScale], [airDrag]).
 *
 * @property key the web's toy id (also the atlas sprite name where there is one).
 * @property width sprite width.
 * @property height sprite height.
 * @property restitution share of the vertical speed kept by a bounce.
 * @property groundDrag exponential decay (per second) of sliding or rolling speed on a surface.
 * @property airDrag exponential decay (per second) of speed in the air.
 * @property gravityScale 1 for a heavy toy, below 1 for a light one that floats down.
 * @property impactGrip share of the sideways speed kept when it hits a surface.
 * @property rolls true when the toy rolls (its spin follows its speed) instead of sliding.
 * @property flutter sideways wobble of a light toy in motion, px/s^2.
 * @property hold how it hangs from the mouth.
 * @property gripU the sprite pixel the mouth holds (tail tip, quill), x.
 * @property gripV the sprite pixel the mouth holds, y.
 * @property holdHz mouth hold: the spring's frequency; hanging: the rod's.
 * @property holdZeta damping ratio of that spring (low: bouncy).
 * @property holdX where the toy sits relative to the mouth anchor, along the cat's facing.
 * @property holdY where the toy sits relative to the mouth anchor, down.
 * @property swingDrag exponential air drag while hanging (high for the feather).
 * @property footfallKick the downward speed a footfall puts into the carried toy, px/s.
 * @property footfallSway the sideways share of that kick.
 * @property thread true when a thread trails from the toy (yarn).
 */
enum class ToyKind(
    val key: String,
    val width: Int,
    val height: Int,
    val restitution: Float,
    val groundDrag: Float,
    val airDrag: Float,
    val gravityScale: Float,
    val impactGrip: Float,
    val rolls: Boolean,
    val flutter: Float,
    val hold: HoldStyle,
    val gripU: Float,
    val gripV: Float,
    val holdHz: Float,
    val holdZeta: Float,
    val holdX: Float,
    val holdY: Float,
    val swingDrag: Float,
    val footfallKick: Float,
    val footfallSway: Float,
    val thread: Boolean = false,
) {
    BALL_A(
        "ball_a",
        9,
        9,
        0.44f,
        1.5f,
        0.15f,
        1f,
        0.85f,
        true,
        0f,
        HoldStyle.MOUTH,
        4.5f,
        4.5f,
        7f,
        0.45f,
        -2.5f,
        0.5f,
        1f,
        18f,
        0.3f,
    ),
    BALL_B(
        "ball_b",
        9,
        9,
        0.36f,
        1.5f,
        0.15f,
        1f,
        0.8f,
        true,
        0f,
        HoldStyle.MOUTH,
        4.5f,
        4.5f,
        6.5f,
        0.5f,
        -2.5f,
        0.5f,
        1f,
        18f,
        0.3f,
    ),
    MOUSE(
        "mouse",
        14,
        8,
        0.2f,
        4.6f,
        0.3f,
        1f,
        0.55f,
        false,
        0f,
        HoldStyle.HANG,
        0.5f,
        6.5f,
        9f,
        0.4f,
        -1f,
        0.5f,
        2.6f,
        22f,
        0.15f,
    ),
    FEATHER(
        "feather",
        12,
        7,
        0.1f,
        4.6f,
        1.4f,
        0.4f,
        0.5f,
        false,
        55f,
        HoldStyle.HANG,
        0.5f,
        5.5f,
        9f,
        0.5f,
        -1f,
        0.5f,
        3.2f,
        18f,
        0.25f,
    ),
    YARN(
        "yarn",
        9,
        9,
        0.3f,
        0.9f,
        0.15f,
        1f,
        0.9f,
        true,
        0f,
        HoldStyle.MOUTH,
        4.5f,
        4.5f,
        6f,
        0.4f,
        -2.5f,
        0.5f,
        1f,
        20f,
        0.3f,
        true,
    ),
    SPRING(
        "spring",
        8,
        10,
        0.62f,
        1.5f,
        0.1f,
        1f,
        0.8f,
        false,
        0f,
        HoldStyle.MOUTH,
        4f,
        5f,
        3.2f,
        0.12f,
        -2.5f,
        0.5f,
        1f,
        60f,
        0.3f,
    );

    /** Half the sprite's height: how far the centre rides above the surface it rests on. */
    val halfHeight: Float
        get() = height / 2f

    /** Distance from the grip to the sprite's centre: the length of the hanging rod. */
    val rodLength: Float
        get() {
            val dx = width / 2f - gripU
            val dy = height / 2f - gripV
            return sqrt(dx * dx + dy * dy)
        }

    /** Direction (radians, y down) from the grip to the centre in the sprite as drawn. */
    val restAngle: Float
        get() = atan2(height / 2f - gripV, width / 2f - gripU)

    companion object {
        /** The kind with web id [key], or null. */
        fun of(key: String): ToyKind? = entries.firstOrNull { it.key == key }
    }
}

/** The numbers of the toy world that do not belong to one kind of toy. */
object ToyTuning {
    /** Pixels per second squared; the web's `petToyUpdate` uses 320 as well. */
    const val GRAVITY = 320f

    /** A landing slower than this does not bounce (web: 45). */
    const val BOUNCE_MIN_SPEED = 45f

    /** A sliding toy slower than this stops (web: 2). */
    const val STOP_SPEED = 2f

    /** Share of the speed a toy keeps when it hits a wall or the side of furniture (web: 0.6). */
    const val WALL_RESTITUTION = 0.6f

    /** The toy turns back this far inside the house's ends. */
    const val WALL_MARGIN = 6f

    /** A toy still counts as on a surface this far past its ends. */
    const val EDGE_MARGIN = 4f

    /** Depth units per second a toy in the air drifts toward the depth of what it will land on. */
    const val Z_PULL = 90f

    /** A toy lands on a surface only when its depth is within this many units of the surface's. */
    const val Z_LAND_TOLERANCE = 6f

    /** The deepest and shallowest a floor toy may roll to. */
    const val Z_MIN = 5f
    const val Z_MAX = 95f

    /** Spin kicked into a toy by a hard landing, per px/s of impact speed. */
    const val SPIN_PER_IMPACT = 0.02f

    /** Rate (per second) at which the spin of a sliding toy dies. */
    const val SPIN_DAMP = 6f

    /** Squash of the sprite on a bounce, per px/s of impact speed. */
    const val SQUASH_PER_IMPACT = 0.0022f
    const val SQUASH_MAX = 0.4f

    /** A toy this near the bottom of a slow bounce is called at rest. */
    const val REST_VY = 20f

    /** Squash spring of a toy's sprite. */
    val SQUASH = SpringParams(9f, 0.35f)

    /** Sub-step of the toy world: the cat body's. */
    const val DT = Tuning.Time.SUBSTEP
}

/** The mouth: where a toy is taken, carried and let go (`Carry`, `ToyDirector`). */
object CarryTuning {
    /** The toy's lift from the floor into the mouth: seconds, and the arc's height in px. */
    const val LIFT_S = 0.2f
    const val LIFT_ARC_PX = 4f

    /** Squash of the toy at the moment of the bite, and its stretch while it rises. */
    const val GRAB_SQUASH = 0.32f
    const val LIFT_STRETCH = 0.12f

    /** Pause before the bite while the cat stands over the toy, ears forward (notice), seconds. */
    const val NOTICE_S = 0.3f

    /** The cat's reach plays the pickup row at this share of its own speed. */
    const val REACH_RATE = 0.9f

    /** After the bite the head comes up for this long before the step is over. */
    const val HEAD_UP_S = 0.12f

    /** A cat farther than this from its reach point walks to it. */
    const val REACH_TOLERANCE_PX = 1.5f

    /** A held toy farther than this many rod lengths from the mouth is snapped back. */
    const val MAX_STRETCH = 4f

    /** How many rod lengths the free end may be from the pivot before the rod pulls hard. */
    const val ROD_DAMP_ZETA = 0.35f

    /** Flick of the toss: the toy's speed at power 0 and 1, and the launch angle (rad up). */
    const val FLICK_MIN = 60f
    const val FLICK_MAX = 150f
    const val FLICK_ANGLE = 0.9f

    /** Spin (rad/s) added to a tossed toy. */
    const val TOSS_SPIN = 9f

    /** Arc height (px above the higher end) of a toss aimed at a surface, at power 0 and 1. */
    const val AIM_ARC_MIN = 10f
    const val AIM_ARC_MAX = 34f

    /** Set down: the head dips this far into the pickup row, seconds down and back up. */
    const val SETDOWN_DIP_PHASE = 0.58f
    const val SETDOWN_DOWN_S = 0.45f
    const val SETDOWN_UP_S = 0.3f
}
