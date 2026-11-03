package com.lifemax.rpg.feature.pet.body

/**
 * The mood made continuous on the body (docs/specs/CAT_BRAIN_PLAN.md 1.7, `CatBody.tone`): valence
 * and arousal (-1..1) become small biases on the springs that are already there, so a content cat
 * carries its tail a little higher and its ears a little softer, an excited one walks faster and
 * breathes shallower, a calm one breathes deeper. Ear and tail states ([EarState], [TailState])
 * stay the brain's discrete targets; this only leans them. Neutral mood (0, 0) is exactly the body
 * as it was: every bias is skipped, so existing traces stay byte-identical. Pupils have no art (the
 * 1x rows do not dilate) and are left alone.
 */
internal class BodyTone {
    /** Added to the ears' look bias (forward positive, -1..1 like [Attention.x]). */
    var earLean = 0f
        private set

    /** Added to the tail root's aim, radians (up positive). */
    var tailLift = 0f
        private set

    /** Walk and run speed multiplier. */
    var pace = 1f
        private set

    /** Breathing depth multiplier. */
    var breathDepth = 1f
        private set

    /** True at the neutral mood: nothing to add. */
    var neutral = true
        private set

    /** Sets the tone from [valence] and [arousal] (clamped to -1..1). */
    fun set(valence: Float, arousal: Float) {
        val t = ToneTuning
        val v = Mathx.clamp(valence, -1f, 1f)
        val a = Mathx.clamp(arousal, -1f, 1f)
        neutral = v == 0f && a == 0f
        earLean = a * t.EAR_AROUSAL + v * t.EAR_VALENCE
        tailLift = v * t.TAIL_VALENCE + a * t.TAIL_AROUSAL
        pace = Mathx.clamp(1f + a * t.PACE, 1f - t.PACE, 1f + t.PACE)
        breathDepth = Mathx.clamp(1f - a * t.BREATH, 1f - t.BREATH, 1f + t.BREATH)
    }

    /** The ears' look bias with the tone leaned in. */
    fun ear(lookBias: Float): Float = if (neutral) lookBias else lookBias + earLean

    /** The tail root's push with the tone added. */
    fun tail(inertia: Float): Float = if (neutral) inertia else inertia + tailLift

    /** A breathing scale with its depth toned. */
    fun breath(scale: Float): Float = if (neutral) scale else 1f + (scale - 1f) * breathDepth
}

/** How far the mood leans the body (see [BodyTone]). */
internal object ToneTuning {
    /** Arousal pricks the ears forward, valence softens them a touch. */
    const val EAR_AROUSAL = 0.35f
    const val EAR_VALENCE = -0.1f

    /** Radians on the tail root: content carries it higher, excitement a little more. */
    const val TAIL_VALENCE = 0.3f
    const val TAIL_AROUSAL = 0.12f

    /** Walk speed ×0.85..1.15 with arousal (plan 1.7). */
    const val PACE = 0.15f

    /** Breath depth ×0.75..1.25: calm deeper, excited shallower. */
    const val BREATH = 0.25f
}
