package com.lifemax.rpg.feature.pet.body

/** The brain's ear states (`data-ear` in the web room). They are targets for the ear springs. */
enum class EarState {
    FORWARD,
    AIRPLANE_FLAT,
    RADAR_SWIVEL,
    RELAXED_SIDE,
}

/**
 * Two ears on springs. An ear state sets where each ear is headed; the springs make it lag the
 * body, overshoot and ring before settling, so an ear that goes flat snaps down and wobbles back
 * rather than switching pose. Angles are radians from upright: negative leans left, positive right.
 */
internal class Ears {
    private val left = Spring(Tuning.Springs.EAR)
    private val right = Spring(Tuning.Springs.EAR)
    private val flatten = Spring(Tuning.Springs.EAR_FLAT)
    private var radarLeft = 0f
    private var radarRight = 0f

    val leftAngle: Float
        get() = left.pos

    val rightAngle: Float
        get() = right.pos

    /** How flat the ears lie, 0 (up) to 1 (airplane), overshoot allowed. */
    val flat: Float
        get() = flatten.pos

    /**
     * Advance one tick. [lookBias] (-1..1, forward positive) swivels both ears toward an attention
     * target; [speedShare] (0..1) lays them back and out as the cat gets faster.
     */
    fun update(dt: Float, state: EarState, lookBias: Float, speedShare: Float) {
        radarLeft = wrap(radarLeft + dt * Tuning.Ears.RADAR_HZ_LEFT)
        radarRight = wrap(radarRight + dt * Tuning.Ears.RADAR_HZ_RIGHT)
        val out = Tuning.Ears.OUT_ANGLE
        var t0: Float
        var t1: Float
        var flatTarget = 0f
        when (state) {
            EarState.FORWARD -> {
                t0 = 0f
                t1 = 0f
            }
            EarState.AIRPLANE_FLAT -> {
                t0 = -out
                t1 = out
                flatTarget = 1f
            }
            EarState.RADAR_SWIVEL -> {
                t0 = Tuning.Ears.RADAR_AMPLITUDE * Mathx.sin(Mathx.TWO_PI * radarLeft)
                t1 =
                    Tuning.Ears.RADAR_AMPLITUDE *
                        Mathx.sin(Mathx.TWO_PI * radarRight + Tuning.Ears.RADAR_PHASE)
            }
            EarState.RELAXED_SIDE -> {
                t0 = -out * Tuning.Ears.RELAXED_SHARE
                t1 = out * Tuning.Ears.RELAXED_SHARE
                flatTarget = Tuning.Ears.RELAXED_FLAT
            }
        }
        val splay = speedShare * Tuning.Ears.SPEED_SPLAY
        t0 += lookBias * Tuning.Ears.LOOK_LEAN - splay
        t1 += lookBias * Tuning.Ears.LOOK_LEAN + splay
        flatTarget += speedShare * Tuning.Ears.SPEED_FLATTEN
        left.step(t0, dt)
        right.step(t1, dt)
        flatten.step(flatTarget, dt)
    }

    /** Keep a phase in cycles inside 0..1, so a cat left running for days never loses precision. */
    private fun wrap(phase: Float): Float = if (phase >= 1f) phase - 1f else phase

    /** A quick twitch of one ear ([side] -1 left, +1 right), from idle life or a startle. */
    fun flick(side: Int) {
        val spring = if (side < 0) left else right
        spring.kick(side * Tuning.Ears.FLICK_VELOCITY)
    }

    /** Both ears drop at once (a startle). */
    fun pinBack() {
        flatten.kick(Tuning.Ears.PIN_VELOCITY)
    }
}

/**
 * Whiskers as one spring: forward and spread when the cat is curious or relaxed, swept back when
 * the ears are flat or the cat runs. -1 is fully back, +1 fully forward.
 */
internal class Whiskers {
    private val spring = Spring(Tuning.Springs.WHISKER)

    val offset: Float
        get() = spring.pos

    fun update(dt: Float, earsFlat: Float, speedShare: Float, lookAhead: Float) {
        val target =
            Tuning.Whiskers.REST + lookAhead * Tuning.Whiskers.LOOK_FORWARD -
                earsFlat * Tuning.Whiskers.EARS_BACK -
                speedShare * Tuning.Whiskers.SPEED_BACK
        spring.step(Mathx.clamp(target, -1f, 1f), dt)
    }

    /** Whiskers jolt back (a startle). */
    fun jolt() {
        spring.kick(-Tuning.Whiskers.JOLT_VELOCITY)
    }
}
