package com.lifemax.rpg.feature.pet.room

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** The numbers behind the camera sway: how big, how slow, how soft. */
internal object SwayTuning {
    /** How far the idle drift swings, as a share of the full sway (-1 to 1). */
    const val IDLE_AMPLITUDE = 0.45f

    /** The two slow waves the idle drift is made of, in seconds, and the share of the second. */
    const val IDLE_PERIOD_A_S = 13f
    const val IDLE_PERIOD_B_S = 8.3f
    const val IDLE_MIX = 0.35f

    /** Where the second wave starts, in turns, so the drift does not begin from the centre. */
    const val IDLE_PHASE_B = 0.27f

    /** How far a full tilt of the phone pushes the sway target. */
    const val TILT_RANGE = 0.9f

    /** The spring that follows the target: its natural frequency (Hz) and damping ratio. */
    const val NATURAL_HZ = 0.7f
    const val DAMPING_RATIO = 0.95f

    /**
     * The drawn sway holds still while what moves it is this close to it (sway units; 0.01 is 0.09
     * px on the nearest floor row at MEDIUM) and nothing is moving faster than [REST_SPEED], so a
     * camera at rest draws the same frame every frame however its last digits wander
     * ([DepthSway.shown]).
     */
    const val REST_HOLD = 0.01f

    /** Sway units per second under which the camera counts as at rest for [REST_HOLD]. */
    const val REST_SPEED = 0.05f

    /** The spring is stepped in fixed slices so the result does not depend on the frame rate. */
    const val SUBSTEP_S = 1f / 240f

    /** A stalled frame longer than this (the app was in the background) counts as this long. */
    const val MAX_FRAME_S = 0.25f
}

/**
 * The camera's sway: a damped spring that follows a target made of a slow idle drift and, if the
 * phone gives one, a tilt, plus the pan of the room camera when it follows a cat ([PanFollow],
 * [follow]). Its [position] runs from -1 (full left) to 1 (full right); the planes of the room turn
 * it into pixels ([Parallax]).
 *
 * Plain arithmetic on a few floats, no clock of its own (the caller says how long a frame took),
 * nothing allocated. The spring is stepped in fixed [SwayTuning.SUBSTEP_S] slices, so a second
 * stepped as sixty frames and as thirty give the same position to a few millionths. With
 * [reducedMotion] it never leaves 0, whatever the tilt.
 *
 * The room is drawn from [shown], [shownIdle] and [shownPerspective], not from the exact values: a
 * snapshot that follows them exactly while the camera moves and holds still once it is at rest
 * (within [SwayTuning.REST_HOLD], slower than [SwayTuning.REST_SPEED]), so a camera that stands
 * still draws bit-identical frames and a value resting on a pixel's edge cannot flip it each frame.
 *
 * @param reducedMotion the owner's "reduce motion" setting: no sway.
 */
internal class DepthSway(
    reducedMotion: Boolean = false,
    private val idleAmplitude: Float = SwayTuning.IDLE_AMPLITUDE,
    private val restAngle: Float = 0f,
) {
    /** The idle drift, the tilt and the resting view angle alone (the spring), -1 to 1. */
    var idle = if (reducedMotion) 0f else restAngle.coerceIn(-1f, 1f)
        private set

    /** The pan of the room camera ([PanFollow.pan]) added to the spring; 0 unless [follow]ing. */
    val pan: Float
        get() = if (following) panFollow.pan else 0f

    /** Where the camera is, -1 to 1: the idle drift and tilt plus the pan. */
    val position: Float
        get() = (idle + pan).coerceIn(-1f, 1f)

    /** The [position] the room is drawn at: it holds still while the camera is at rest. */
    var shown = idle
        private set

    /** The [idle] the room is drawn at, held with [shown]. */
    var shownIdle = idle
        private set

    private var shownView = 0f

    /** How fast it is moving, in sway units per second. */
    var velocity = 0f
        private set

    /** The tilt of the phone, -1 (tilted left) to 1 (right); 0 when there is no sensor. */
    var tilt = 0f
        set(value) {
            field = value.coerceIn(-1f, 1f)
        }

    /** Whether the sway is held at 0. Turning it on puts the camera at rest at once. */
    var reducedMotion = reducedMotion
        set(value) {
            field = value
            if (value) {
                idle = 0f
                velocity = 0f
                panFollow.reset()
                show()
            }
        }

    private val panFollow = PanFollow()
    private var following = false
    private var screenX = 0f

    /** The eased x of the middle of the screen, world px (the perspective of the pieces). */
    val viewX: Float
        get() = panFollow.viewX

    /** The yaw a piece at world x [pieceX] gets from where it sits on the screen. */
    fun perspective(pieceX: Float): Float = if (following) panFollow.perspective(pieceX) else 0f

    /** [perspective] as the room is drawn: from the view x held with [shown]. */
    fun shownPerspective(pieceX: Float): Float =
        if (following) PanFollow.perspectiveAt(shownView, pieceX) else 0f

    /**
     * Follow the room camera: [screenX] is the world x at the middle of the screen. Call it each
     * frame before [step]; the first call puts the view there at once. A no-op with reduced motion.
     */
    fun follow(screenX: Float) {
        if (reducedMotion) return
        following = true
        this.screenX = screenX
    }

    private var time = 0f
    private var pending = 0f

    /**
     * Put the camera at rest at [at] (-1 to 1) at once: a screenshot, a test, a cut. A no-op with
     * reduced motion.
     */
    fun restAt(at: Float) {
        if (reducedMotion) return
        idle = at.coerceIn(-1f, 1f)
        velocity = 0f
        show()
    }

    /** Advance by [dt] seconds. */
    fun step(dt: Float) {
        if (reducedMotion || dt <= 0f) return
        pending += dt.coerceAtMost(SwayTuning.MAX_FRAME_S)
        while (pending >= SwayTuning.SUBSTEP_S) {
            pending -= SwayTuning.SUBSTEP_S
            slice()
        }
        if (moving() || strayed()) show()
    }

    /** Whether the camera has moved [SwayTuning.REST_HOLD] from where the room is drawn. */
    private fun strayed(): Boolean {
        val view = abs(panFollow.viewX - shownView) * PanTuning.PERSPECTIVE_GAIN
        val yaw = if (following) view / PanTuning.PERSPECTIVE_RANGE_PX else 0f
        return abs(position - shown) >= SwayTuning.REST_HOLD || yaw >= SwayTuning.REST_HOLD
    }

    /** Whether the camera is moving: the idle spring, the pan spring or the view. */
    private fun moving(): Boolean {
        val view = if (following) panFollow.restSpeed() else 0f
        return abs(velocity) + view >= SwayTuning.REST_SPEED
    }

    /** Draw from where the camera is now. */
    private fun show() {
        shown = position
        shownIdle = idle
        shownView = panFollow.viewX
    }

    private fun slice() {
        val h = SwayTuning.SUBSTEP_S
        time += h
        val w = (2.0 * PI * SwayTuning.NATURAL_HZ).toFloat()
        val acceleration =
            w * w * (target(time) - idle) - 2f * SwayTuning.DAMPING_RATIO * w * velocity
        velocity += acceleration * h
        idle = (idle + velocity * h).coerceIn(-1f, 1f)
        if (following) panFollow.advance(screenX, h)
    }

    /** The point the spring is pulled toward at [t] seconds: the idle drift plus the tilt. */
    fun target(t: Float): Float {
        val a = sin(TURN * (t / SwayTuning.IDLE_PERIOD_A_S))
        val b = sin(TURN * (t / SwayTuning.IDLE_PERIOD_B_S + SwayTuning.IDLE_PHASE_B))
        val idle = idleAmplitude * (a * (1f - SwayTuning.IDLE_MIX) + b * SwayTuning.IDLE_MIX)
        return (restAngle + idle + tilt * SwayTuning.TILT_RANGE).coerceIn(-1f, 1f)
    }

    private companion object {
        const val TURN = (2.0 * PI).toFloat()
    }
}
