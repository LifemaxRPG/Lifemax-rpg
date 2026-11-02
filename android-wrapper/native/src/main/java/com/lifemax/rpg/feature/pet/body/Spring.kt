package com.lifemax.rpg.feature.pet.body

/**
 * How a spring behaves, in the terms an animator thinks in: how fast it swings and how quickly the
 * swinging dies. A damping ratio of 1 is critical (fastest settle, no overshoot); below 1 the
 * spring overshoots and rings, above 1 it creeps.
 */
class SpringParams(val frequencyHz: Float, val dampingRatio: Float) {
    /** Angular frequency in radians per second. */
    val omega: Float = Mathx.TWO_PI * frequencyHz

    /** Linear blend between two parameter sets, for chains whose tip is livelier than the root. */
    fun lerp(other: SpringParams, t: Float): SpringParams =
        SpringParams(
            Mathx.lerp(frequencyHz, other.frequencyHz, t),
            Mathx.lerp(dampingRatio, other.dampingRatio, t),
        )
}

/**
 * One damped spring with a position and a velocity, advanced with semi-implicit Euler. Stable and
 * accurate at the body's 1/120 s sub-step for every parameter set in [Tuning].
 */
class Spring(val params: SpringParams, var pos: Float = 0f) {
    var vel = 0f

    /** Advance one tick toward [target]. */
    fun step(target: Float, dt: Float) {
        val w = params.omega
        val accel = -w * w * (pos - target) - 2f * params.dampingRatio * w * vel
        vel += accel * dt
        pos += vel * dt
    }

    /** Add an instant change of velocity (a flick). */
    fun kick(dv: Float) {
        vel += dv
    }

    /** Add an instant change of position (a hit). */
    fun displace(dp: Float) {
        pos += dp
    }

    /** Jump to [value] and stop. */
    fun snap(value: Float) {
        pos = value
        vel = 0f
    }
}
