package com.lifemax.rpg.feature.pet.body

/**
 * The small unprompted movements that keep a cat from ever being frozen: weight shifts, ear flicks
 * and glances. Timers come from the cat's own random stream, and each cat has its own fidget
 * factor, so two cats with different seeds never move in step.
 *
 * Ear flicks happen in any posture (rarely when asleep, like a dreamer); weight shifts and glances
 * only while the cat is calmly sitting with nothing to look at.
 */
internal class IdleLife(
    private val rng: Rng,
    private val body: BodySprings,
    private val ears: Ears,
    private val attention: Attention,
) {
    /** How restless this cat is: 1 is average; the range makes some cats twitchy and some stoic. */
    val fidget: Float = rng.range(Tuning.Idle.FIDGET_MIN, Tuning.Idle.FIDGET_MAX)

    private var shiftIn = interval(Tuning.Idle.SHIFT_MIN_S, Tuning.Idle.SHIFT_MAX_S)
    private var flickIn = interval(Tuning.Idle.FLICK_MIN_S, Tuning.Idle.FLICK_MAX_S)
    private var glanceIn = interval(Tuning.Idle.GLANCE_MIN_S, Tuning.Idle.GLANCE_MAX_S)

    /**
     * Advance one tick. [calm] is true when the cat sits quietly with an empty queue; [asleep]
     * slows the flicks right down.
     */
    fun update(dt: Float, calm: Boolean, asleep: Boolean) {
        flickIn -= dt
        if (flickIn <= 0f) {
            if (!asleep || rng.chance(Tuning.Idle.DREAM_FLICK_CHANCE)) {
                ears.flick(if (rng.chance(HALF)) -1 else 1)
            }
            flickIn = interval(Tuning.Idle.FLICK_MIN_S, Tuning.Idle.FLICK_MAX_S)
        }
        if (!calm) return
        shiftIn -= dt
        if (shiftIn <= 0f) {
            shiftWeight()
            shiftIn = interval(Tuning.Idle.SHIFT_MIN_S, Tuning.Idle.SHIFT_MAX_S)
        }
        glanceIn -= dt
        if (glanceIn <= 0f) {
            if (!attention.locked) glance()
            glanceIn = interval(Tuning.Idle.GLANCE_MIN_S, Tuning.Idle.GLANCE_MAX_S)
        }
    }

    private fun interval(lo: Float, hi: Float): Float = rng.range(lo, hi) / fidget

    private fun shiftWeight() {
        val side = rng.sign()
        body.rotation.kick(side * Tuning.Idle.SHIFT_ROTATION_VELOCITY)
        body.squashY.kick(-Tuning.Idle.SHIFT_SQUASH_VELOCITY)
        body.squashX.kick(Tuning.Idle.SHIFT_SQUASH_VELOCITY * Tuning.Squash.VOLUME_SHARE)
    }

    private fun glance() {
        attention.glance(
            rng.range(-Tuning.Idle.GLANCE_X, Tuning.Idle.GLANCE_X),
            rng.range(-Tuning.Idle.GLANCE_Y, Tuning.Idle.GLANCE_Y),
            rng.range(Tuning.Idle.GLANCE_HOLD_MIN_S, Tuning.Idle.GLANCE_HOLD_MAX_S),
        )
    }

    private companion object {
        const val HALF = 0.5f
    }
}
