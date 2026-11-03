package com.lifemax.rpg.feature.pet.brain

import com.lifemax.rpg.feature.pet.body.EarState
import com.lifemax.rpg.feature.pet.body.TailState

/**
 * Valence and arousal (plan 1.7), -1..1, each decaying to a baseline with its own half-life in
 * closed form per quantum (v 4 min, a 2 min), nudged by events ([bump]). The baseline comes from
 * the cat's character, hunger, tiredness and what it is doing; [ear] and [tail] map the mood to the
 * body's spring targets, held for a few quanta before they change so the ears never flicker.
 */
class Mood {
    private val t = BrainTuning.Mood

    var valence = 0.0
        private set

    var arousal = 0.0
        private set

    private val vKeep = Math.pow(HALF, BrainTuning.Time.QUANTUM_S / t.VALENCE_HALF_LIFE)
    private val aKeep = Math.pow(HALF, BrainTuning.Time.QUANTUM_S / t.AROUSAL_HALF_LIFE)

    private var earWant = EarState.FORWARD
    private var earHeld = 0
    private var tailWant = TailState.NEUTRAL
    private var tailHeld = 0

    /** The ear state the body shows. */
    var ear = EarState.FORWARD
        private set

    /** The tail state the body shows. */
    var tail = TailState.NEUTRAL
        private set

    /** Sets the mood (a restored world, a test). */
    fun set(v: Double, a: Double) {
        valence = v.coerceIn(-1.0, 1.0)
        arousal = a.coerceIn(-1.0, 1.0)
    }

    /** An event: [dv] and [da] on top of the mood, clamped. */
    fun bump(dv: Double, da: Double) = set(valence + dv, arousal + da)

    /**
     * One quantum: decay toward the baseline of a cat of [persona] doing [activity] with [energy]
     * and at a [peak] (0..1) of its day.
     */
    fun quantum(persona: Persona, activity: Activity, energy: Double, peak: Double) {
        val hunger = if (persona.vacation) 0 else persona.hungerStage
        val v0 =
            t.BASE_VALENCE + t.FRIENDLY_VALENCE * (persona.friendly - HALF) -
                t.SKITTISH_VALENCE * (persona.skittish - HALF) -
                t.HUNGER_VALENCE_PER_STAGE * hunger
        val a0 =
            activity.arousal * AROUSAL_SHARE + PEAK_AROUSAL * peak -
                t.TIRED_AROUSAL * (1.0 - energy)
        valence = v0 + (valence - v0) * vKeep
        arousal = a0.coerceIn(-1.0, 1.0) + (arousal - a0.coerceIn(-1.0, 1.0)) * aKeep
        hold(activity)
    }

    private fun hold(activity: Activity) {
        val e = wantedEar(activity)
        if (e == earWant) earHeld++
        else {
            earWant = e
            earHeld = 0
        }
        if (earHeld >= t.AFFECT_HOLD_QUANTA) ear = earWant
        val tl = activity.tail ?: wantedTail()
        if (tl == tailWant) tailHeld++
        else {
            tailWant = tl
            tailHeld = 0
        }
        if (tailHeld >= t.AFFECT_HOLD_QUANTA) tail = tailWant
    }

    private fun wantedEar(activity: Activity): EarState =
        when {
            activity.asleep -> EarState.RELAXED_SIDE
            valence < t.EARS_FLAT_V && arousal > t.EARS_FLAT_A -> EarState.AIRPLANE_FLAT
            activity.tail == TailState.TIP_TWITCH -> EarState.FORWARD
            valence > t.EARS_RELAXED_V && arousal < t.EARS_RELAXED_A -> EarState.RELAXED_SIDE
            else -> EarState.FORWARD
        }

    private fun wantedTail(): TailState =
        when {
            valence > t.TAIL_FLAG_V && arousal > t.TAIL_FLAG_A -> TailState.HIGH_FLAG
            valence < t.TAIL_SWISH_V && arousal > t.TAIL_SWISH_A -> TailState.LOW_SWISH
            valence < t.TAIL_TUCK_V && arousal <= 0.0 -> TailState.TUCKED_CURL
            else -> TailState.NEUTRAL
        }

    private companion object {
        const val HALF = 0.5
        const val AROUSAL_SHARE = 0.7
        const val PEAK_AROUSAL = 0.2
    }
}
