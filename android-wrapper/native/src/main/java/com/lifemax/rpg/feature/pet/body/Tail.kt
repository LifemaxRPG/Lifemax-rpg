package com.lifemax.rpg.feature.pet.body

/** The brain's tail states (`data-tail` in the web room). They are targets for the tail springs. */
enum class TailState {
    NEUTRAL,
    TIP_TWITCH,
    LOW_SWISH,
    TUCKED_CURL,
    QUESTION_MARK,
    HIGH_FLAG,
}

/**
 * A tail made of a chain of springs. The root segment aims at the state's base angle; each segment
 * after it aims at its share of the state's curl, plus a fraction of how far its parent is from its
 * own aim. That fraction is what makes a movement travel down the tail like a wave: the root moves,
 * the tip arrives later and overshoots. Bends are radians per segment, positive upward.
 */
internal class Tail(private val rng: Rng) {
    private val segments =
        Array(SEGMENTS) { i ->
            Spring(
                Tuning.Springs.TAIL_ROOT.lerp(Tuning.Springs.TAIL_TIP, i.toFloat() / (SEGMENTS - 1))
            )
        }
    private var swishPhase = 0f
    private var nextTwitchIn =
        rng.range(Tuning.Tail.TWITCH_GAP_NEUTRAL_MIN, Tuning.Tail.TWITCH_GAP_NEUTRAL_MAX)

    /** Bend of segment [i] (0 is the root), radians. */
    fun bend(i: Int): Float = segments[i].pos

    /**
     * Advance one tick. [inertia] (radians) is how far the body's acceleration pushes the root up
     * (speeding up) or down (braking).
     */
    fun update(dt: Float, state: TailState, inertia: Float) {
        swishPhase += dt * Tuning.Tail.SWISH_HZ
        if (swishPhase >= 1f) swishPhase -= 1f
        val swish = swishAmplitude(state) * Mathx.sin(Mathx.TWO_PI * swishPhase)
        val curlShare = curl(state) / (SEGMENTS - 1)
        var parentDeviation = 0f
        for (i in 0 until SEGMENTS) {
            val own =
                if (i == 0) base(state) + inertia + swish
                else curlShare + swish * Tuning.Tail.SWISH_TIP_SHARE
            segments[i].step(own + Tuning.Tail.COUPLING * parentDeviation, dt)
            parentDeviation = segments[i].pos - own
        }
        updateTwitch(dt, state)
    }

    /** Swing the whole tail the other way as the body turns (it lags, then follows). */
    fun swing(dir: Int) {
        segments[0].kick(-dir * Tuning.Tail.TURN_KICK)
    }

    /** A flick of the tip, as for a twitch or a startle. */
    fun flickTip(strength: Float) {
        segments[SEGMENTS - 1].kick(rng.sign() * Tuning.Tail.TWITCH_KICK * strength)
        segments[SEGMENTS - 2].kick(
            rng.sign() * Tuning.Tail.TWITCH_KICK * strength * Tuning.Tail.TWITCH_SPILL
        )
    }

    private fun updateTwitch(dt: Float, state: TailState) {
        nextTwitchIn -= dt
        if (nextTwitchIn > 0f) return
        flickTip(1f)
        nextTwitchIn =
            when (state) {
                TailState.TIP_TWITCH ->
                    rng.range(Tuning.Tail.TWITCH_GAP_ACTIVE_MIN, Tuning.Tail.TWITCH_GAP_ACTIVE_MAX)
                TailState.LOW_SWISH,
                TailState.TUCKED_CURL ->
                    rng.range(Tuning.Tail.TWITCH_GAP_QUIET_MIN, Tuning.Tail.TWITCH_GAP_QUIET_MAX)
                else ->
                    rng.range(
                        Tuning.Tail.TWITCH_GAP_NEUTRAL_MIN,
                        Tuning.Tail.TWITCH_GAP_NEUTRAL_MAX,
                    )
            }
    }

    private fun base(state: TailState): Float =
        when (state) {
            TailState.NEUTRAL,
            TailState.TIP_TWITCH -> Tuning.Tail.BASE_NEUTRAL
            TailState.LOW_SWISH -> Tuning.Tail.BASE_LOW
            TailState.TUCKED_CURL -> Tuning.Tail.BASE_TUCKED
            TailState.QUESTION_MARK -> Tuning.Tail.BASE_QUESTION
            TailState.HIGH_FLAG -> Tuning.Tail.BASE_FLAG
        }

    private fun curl(state: TailState): Float =
        when (state) {
            TailState.NEUTRAL,
            TailState.TIP_TWITCH -> Tuning.Tail.CURL_NEUTRAL
            TailState.LOW_SWISH -> Tuning.Tail.CURL_LOW
            TailState.TUCKED_CURL -> Tuning.Tail.CURL_TUCKED
            TailState.QUESTION_MARK -> Tuning.Tail.CURL_QUESTION
            TailState.HIGH_FLAG -> Tuning.Tail.CURL_FLAG
        }

    private fun swishAmplitude(state: TailState): Float =
        if (state == TailState.LOW_SWISH) Tuning.Tail.SWISH_AMPLITUDE else 0f

    companion object {
        const val SEGMENTS = Tuning.Tail.SEGMENTS
    }
}

/**
 * Breathing as a slow scale of the torso. Rate and depth glide between an awake rhythm and a
 * slower, deeper sleeping one instead of switching, so a cat dropping off visibly settles.
 */
internal class Breathing {
    private var phase = 0f
    private var rate = Tuning.Breath.AWAKE_HZ
    private var depth = Tuning.Breath.AWAKE_DEPTH

    /** Torso scale for the renderer, 1 plus or minus the depth. */
    val scale: Float
        get() = 1f + depth * Mathx.sin(Mathx.TWO_PI * phase)

    fun update(dt: Float, asleep: Boolean) {
        val k = Mathx.clamp(dt / Tuning.Breath.GLIDE_S, 0f, 1f)
        rate += ((if (asleep) Tuning.Breath.ASLEEP_HZ else Tuning.Breath.AWAKE_HZ) - rate) * k
        depth +=
            ((if (asleep) Tuning.Breath.ASLEEP_DEPTH else Tuning.Breath.AWAKE_DEPTH) - depth) * k
        phase += rate * dt
        if (phase >= 1f) phase -= 1f
    }
}
