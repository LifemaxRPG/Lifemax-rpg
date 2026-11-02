package com.lifemax.rpg.feature.pet.body

/** The short reactions that layer on top of whatever the cat is doing. */
enum class ReactionKind {
    /** A sudden fright: a hop, flat ears, a puffed tail. */
    STARTLE,

    /** The slow, affectionate blink. */
    SLOW_BLINK,

    /** Purring: a fast low-amplitude vibration of the body. */
    PURR,

    /** Begging for food: little bobs, tail up in a question, a look upward. */
    BEG,
}

/**
 * Layered reactions. Each is a timed envelope that nudges the springs once when it starts and then
 * contributes a small continuous part (eye closure, vibration, an ear or tail pose override) while
 * it lasts. Reactions never change the clip, so they sit on top of any state.
 */
internal class Reactions(
    private val body: BodySprings,
    private val ears: Ears,
    private val tail: Tail,
    private val whiskers: Whiskers,
    private val attention: Attention,
) {
    private val kinds = ReactionKind.entries
    private val age = FloatArray(kinds.size) { INACTIVE }
    private val length = FloatArray(kinds.size)
    private val pulses = IntArray(kinds.size)

    /** How closed the eyes are, 0 (open) to 1 (shut). */
    var eyeClose = 0f
        private set

    /** Fast vertical shake in pixels (purr). */
    var vibration = 0f
        private set

    /** An ear state the reaction wants, or null to leave the brain's state alone. */
    var earOverride: EarState? = null
        private set

    /** A tail state the reaction wants, or null to leave the brain's state alone. */
    var tailOverride: TailState? = null
        private set

    /** True if the given reaction is playing. */
    fun isActive(kind: ReactionKind) = age[kind.ordinal] >= 0f

    /** Start (or restart) [kind]; [seconds] is used by reactions with no fixed length (purr). */
    fun trigger(kind: ReactionKind, seconds: Float = 0f) {
        val i = kind.ordinal
        age[i] = 0f
        pulses[i] = 0
        length[i] = if (seconds > 0f) seconds else defaultLength(kind)
        when (kind) {
            ReactionKind.STARTLE -> startle()
            ReactionKind.BEG -> attention.glance(0f, Tuning.Reaction.BEG_LOOK_UP, length[i])
            else -> Unit
        }
    }

    fun update(dt: Float) {
        eyeClose = 0f
        vibration = 0f
        earOverride = null
        tailOverride = null
        for (i in kinds.indices) {
            if (age[i] < 0f) continue
            age[i] += dt
            if (age[i] >= length[i]) {
                age[i] = INACTIVE
                continue
            }
            apply(kinds[i], age[i], length[i])
        }
    }

    fun reset() {
        for (i in kinds.indices) age[i] = INACTIVE
        eyeClose = 0f
        vibration = 0f
        earOverride = null
        tailOverride = null
    }

    private fun defaultLength(kind: ReactionKind): Float =
        when (kind) {
            ReactionKind.STARTLE -> Tuning.Reaction.STARTLE_S
            ReactionKind.SLOW_BLINK -> Tuning.Reaction.SLOW_BLINK_S
            ReactionKind.PURR -> Tuning.Reaction.PURR_S
            ReactionKind.BEG -> Tuning.Reaction.BEG_S
        }

    private fun startle() {
        body.stretch(Tuning.Reaction.STARTLE_STRETCH)
        body.bob.kick(-Tuning.Reaction.STARTLE_HOP_VELOCITY)
        ears.pinBack()
        whiskers.jolt()
        tail.flickTip(Tuning.Reaction.STARTLE_TAIL_FLICK)
    }

    private fun apply(kind: ReactionKind, t: Float, len: Float) {
        when (kind) {
            ReactionKind.STARTLE -> {
                if (t < Tuning.Reaction.STARTLE_EARS_S) earOverride = EarState.AIRPLANE_FLAT
                tailOverride = TailState.HIGH_FLAG
            }
            ReactionKind.SLOW_BLINK -> {
                eyeClose = blinkEnvelope(t / len)
                if (earOverride == null) earOverride = EarState.RELAXED_SIDE
            }
            ReactionKind.PURR -> {
                val ramp = Tuning.Reaction.PURR_RAMP_S
                val envelope = minOf(1f, t / ramp, (len - t) / ramp)
                vibration =
                    envelope *
                        Tuning.Reaction.PURR_AMPLITUDE *
                        Mathx.sin(Mathx.TWO_PI * Tuning.Reaction.PURR_HZ * t)
            }
            ReactionKind.BEG -> {
                val pulse = (t / Tuning.Reaction.BEG_PULSE_S).toInt()
                if (pulse != pulses[kind.ordinal]) {
                    pulses[kind.ordinal] = pulse
                    body.bob.kick(Tuning.Reaction.BEG_BOB_VELOCITY)
                }
                if (tailOverride == null) tailOverride = TailState.QUESTION_MARK
                if (earOverride == null) earOverride = EarState.FORWARD
            }
        }
    }

    /** Close over the first quarter, hold to the middle, open slowly over the last half. */
    private fun blinkEnvelope(p: Float): Float =
        when {
            p < Tuning.Reaction.BLINK_CLOSE_END ->
                Mathx.smoothstep(0f, Tuning.Reaction.BLINK_CLOSE_END, p)
            p < Tuning.Reaction.BLINK_HOLD_END -> 1f
            else -> 1f - Mathx.smoothstep(Tuning.Reaction.BLINK_HOLD_END, 1f, p)
        }

    private companion object {
        const val INACTIVE = -1f
    }
}
