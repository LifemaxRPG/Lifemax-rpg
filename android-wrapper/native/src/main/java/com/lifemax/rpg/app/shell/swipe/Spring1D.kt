package com.lifemax.rpg.app.shell.swipe

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A damped spring of unit mass pulling a value toward [target], solved in closed form so any frame
 * time gives the exact state, with nothing integrated step by step. Underdamped (the pop) and
 * critically damped (every settle) springs are supported; [dampingRatio] lies in (0, 1].
 *
 * Time is animation time: real time divided by [durationScale] (`ValueAnimator.getDurationScale()`
 * when the spring started), so scale 2 takes twice as long and scale 0 lands at once. Velocities
 * are per second of animation time; at scale 1 that is real time. A finger's velocity is handed
 * over unconverted, as Compose's MotionDurationScale and DynamicAnimation do, so every scale gives
 * the same shape of motion, only slower or faster.
 *
 * Immutable. A spring that changes course is a new spring started where the old one is ([restart]),
 * so position and velocity stay continuous.
 */
data class Spring1D(
    val start: Float,
    val startVelocity: Float,
    val target: Float,
    val stiffness: Float,
    val dampingRatio: Float,
    val startNanos: Long,
    val durationScale: Float = 1f,
    /**
     * When true the value never passes [target] (a guard; [settle]'s velocity cap already holds).
     */
    val guarded: Boolean = false,
) {
    private val omega = sqrt(stiffness)
    private val zeta = dampingRatio.coerceIn(MIN_DAMPING, 1f)

    /** Seconds of animation time since the start; infinite at scale 0. */
    fun time(now: Long): Float {
        if (durationScale <= 0f) return Float.POSITIVE_INFINITY
        return (now - startNanos).coerceAtLeast(0L) / NANOS_PER_SECOND / durationScale
    }

    fun position(now: Long): Float = target + displacement(time(now))

    /** Velocity in units per second of animation time. */
    fun velocity(now: Long): Float {
        val t = time(now)
        if (t.isInfinite() || crossed(t)) return 0f
        val x0 = start - target
        val v0 = startVelocity
        return if (zeta >= CRITICAL) {
            (v0 - omega * (v0 + omega * x0) * t) * exp(-omega * t)
        } else {
            val wd = omega * sqrt(1f - zeta * zeta)
            val b = (v0 + zeta * omega * x0) / wd
            exp(-zeta * omega * t) * (v0 * cos(wd * t) - (zeta * omega * b + wd * x0) * sin(wd * t))
        }
    }

    /**
     * At rest within [epsilon] of the target: close enough, and slow enough to move less than
     * [epsilon] in a 60 Hz frame. Use half a pixel as a fraction of the width.
     */
    fun isAtRest(now: Long, epsilon: Float): Boolean =
        abs(position(now) - target) <= epsilon && abs(velocity(now)) <= epsilon * REST_RATE

    /**
     * A spring that starts at [now] from where this one is, as fast (unless told otherwise), toward
     * [target] with the given stiffness, damping and scale.
     */
    fun restart(
        now: Long,
        position: Float = position(now),
        velocity: Float = velocity(now),
        target: Float = this.target,
        stiffness: Float = this.stiffness,
        dampingRatio: Float = this.dampingRatio,
        durationScale: Float = this.durationScale,
    ): Spring1D =
        Spring1D(position, velocity, target, stiffness, dampingRatio, now, durationScale, guarded)

    private fun displacement(t: Float): Float {
        if (t.isInfinite() || crossed(t)) return 0f
        return free(t)
    }

    private fun free(t: Float): Float {
        val x0 = start - target
        val v0 = startVelocity
        return if (zeta >= CRITICAL) {
            (x0 + (v0 + omega * x0) * t) * exp(-omega * t)
        } else {
            val wd = omega * sqrt(1f - zeta * zeta)
            val b = (v0 + zeta * omega * x0) / wd
            exp(-zeta * omega * t) * (x0 * cos(wd * t) + b * sin(wd * t))
        }
    }

    /** A guarded spring has reached the far side of its target. */
    private fun crossed(t: Float): Boolean {
        if (!guarded) return false
        val side = sign(start - target)
        return side != 0f && sign(free(t)) == -side
    }

    companion object {
        private const val NANOS_PER_SECOND = 1e9f
        private const val CRITICAL = 0.9999f
        private const val MIN_DAMPING = 0.05f
        private const val REST_RATE = 60f

        /**
         * A critically damped settle from [from] toward [target], leaving at [velocity]. The
         * stiffness adapts to the speed, k = clamp((v/|Δ|)², [minStiffness], [maxStiffness]), so a
         * fast release lands sooner. Velocity toward the target is capped at √k·|Δ|: a critically
         * damped spring that leaves no faster than that can never cross its target, so a commit
         * never opens a gap past the far edge and a cancel never shows the other side. The value is
         * also guarded at the target. Scale 0 gives a spring already at rest on the target.
         */
        fun settle(
            from: Float,
            velocity: Float,
            target: Float,
            minStiffness: Float,
            maxStiffness: Float,
            now: Long,
            durationScale: Float = 1f,
        ): Spring1D {
            val delta = target - from
            val distance = abs(delta)
            if (durationScale <= 0f || distance < 1e-6f) {
                return Spring1D(target, 0f, target, minStiffness, 1f, now, durationScale, true)
            }
            val k =
                ((velocity / distance) * (velocity / distance)).coerceIn(minStiffness, maxStiffness)
            val cap = sqrt(k) * distance
            val toward = velocity * sign(delta)
            val v = if (toward > cap) cap * sign(delta) else velocity
            return Spring1D(from, v, target, k, 1f, now, durationScale, true)
        }
    }
}
