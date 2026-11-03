package com.lifemax.rpg.feature.pet.room

import kotlin.math.PI
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

    /** The spring is stepped in fixed slices so the result does not depend on the frame rate. */
    const val SUBSTEP_S = 1f / 240f

    /** A stalled frame longer than this (the app was in the background) counts as this long. */
    const val MAX_FRAME_S = 0.25f
}

/**
 * The camera's sway: a damped spring that follows a target made of a slow idle drift and, if the
 * phone gives one, a tilt. Its [position] runs from -1 (full left) to 1 (full right); the planes of
 * the room turn it into pixels ([Parallax]).
 *
 * Plain arithmetic on a few floats, no clock of its own (the caller says how long a frame took),
 * nothing allocated. The spring is stepped in fixed [SwayTuning.SUBSTEP_S] slices, so a second
 * stepped as sixty frames and as thirty give the same position to a few millionths. With
 * [reducedMotion] it never leaves 0, whatever the tilt.
 *
 * @param reducedMotion the owner's "reduce motion" setting: no sway.
 */
internal class DepthSway(
    reducedMotion: Boolean = false,
    private val idleAmplitude: Float = SwayTuning.IDLE_AMPLITUDE,
) {
    /** Where the camera is, -1 to 1. */
    var position = 0f
        private set

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
                position = 0f
                velocity = 0f
            }
        }

    private var time = 0f
    private var pending = 0f

    /**
     * Put the camera at rest at [at] (-1 to 1) at once: a screenshot, a test, a cut. A no-op with
     * reduced motion.
     */
    fun restAt(at: Float) {
        if (reducedMotion) return
        position = at.coerceIn(-1f, 1f)
        velocity = 0f
    }

    /** Advance by [dt] seconds. */
    fun step(dt: Float) {
        if (reducedMotion || dt <= 0f) return
        pending += dt.coerceAtMost(SwayTuning.MAX_FRAME_S)
        while (pending >= SwayTuning.SUBSTEP_S) {
            pending -= SwayTuning.SUBSTEP_S
            slice()
        }
    }

    private fun slice() {
        val h = SwayTuning.SUBSTEP_S
        time += h
        val w = (2.0 * PI * SwayTuning.NATURAL_HZ).toFloat()
        val acceleration =
            w * w * (target(time) - position) - 2f * SwayTuning.DAMPING_RATIO * w * velocity
        velocity += acceleration * h
        position = (position + velocity * h).coerceIn(-1f, 1f)
    }

    /** The point the spring is pulled toward at [t] seconds: the idle drift plus the tilt. */
    fun target(t: Float): Float {
        val a = sin(TURN * (t / SwayTuning.IDLE_PERIOD_A_S))
        val b = sin(TURN * (t / SwayTuning.IDLE_PERIOD_B_S + SwayTuning.IDLE_PHASE_B))
        val idle = idleAmplitude * (a * (1f - SwayTuning.IDLE_MIX) + b * SwayTuning.IDLE_MIX)
        return (idle + tilt * SwayTuning.TILT_RANGE).coerceIn(-1f, 1f)
    }

    private companion object {
        const val TURN = (2.0 * PI).toFloat()
    }
}
