package com.lifemax.rpg.feature.pet.brain

/**
 * The cat's drives (plan 1.3), all 0..1; [key] is the blob's `dr` key. [ENERGY] is the inverse of
 * sleep pressure (1 rested); the others are wishes (1 the strongest).
 */
enum class Drive(val key: String) {
    ENERGY("en"),
    PLAY("pl"),
    CURIOSITY("cu"),
    SOCIAL("so"),
    COMPANY("co"),
    COMFORT("cm"),
    TERRITORY("te"),
    GROOM("gr"),
}

/**
 * The levels of one cat's [Drive]s and how they move. Every change is a relaxation toward a target
 * in closed form, `x ← t + (x − t)·e^(−q/τ)` for a whole think quantum q, never an Euler step on
 * frame time (plan 1.2): so a drive is bounded by its start and its target, moves monotonically
 * toward the target, and is the same at every quantum whatever the host frame rate. The factor
 * `e^(−q/τ)` is computed only when τ changes (an activity change), so a quantum costs a multiply.
 * Nothing here allocates after construction.
 */
class Drives {
    private val level = DoubleArray(COUNT)
    private val lastTau = DoubleArray(COUNT) { Double.NaN }
    private val factor = DoubleArray(COUNT)

    /** The level of [d], 0..1. */
    operator fun get(d: Drive): Double = level[d.ordinal]

    /** Sets [d] to [v] (clamped to 0..1): a restored world or a test. */
    operator fun set(d: Drive, v: Double) {
        level[d.ordinal] = v.coerceIn(BrainTuning.Drives.FLOOR, BrainTuning.Drives.CEIL)
    }

    /**
     * One think quantum of [d] relaxing toward [target] (0..1) with time constant [tauS] seconds; a
     * non-positive or infinite time constant holds the drive where it is.
     */
    fun relax(d: Drive, target: Double, tauS: Double) {
        if (tauS <= 0.0 || tauS == Double.POSITIVE_INFINITY) return
        val i = d.ordinal
        if (tauS != lastTau[i]) {
            lastTau[i] = tauS
            factor[i] = StrictMath.exp(-BrainTuning.Time.QUANTUM_S / tauS)
        }
        val t = target.coerceIn(BrainTuning.Drives.FLOOR, BrainTuning.Drives.CEIL)
        level[i] = t + (level[i] - t) * factor[i]
    }

    /**
     * A discrete step of [d] a [share] (0..1) of the way to [target]: an activity's aftermath (a
     * meal makes the cat want to wash). Bounded and monotone like [relax].
     */
    fun nudge(d: Drive, target: Double, share: Double) {
        val i = d.ordinal
        val s = share.coerceIn(0.0, 1.0)
        level[i] += (target.coerceIn(0.0, 1.0) - level[i]) * s
    }

    /** Caps [d] at [max] (a weak cat's energy). */
    fun cap(d: Drive, max: Double) {
        if (level[d.ordinal] > max) level[d.ordinal] = max
    }

    companion object {
        val ALL: Array<Drive> = Drive.entries.toTypedArray()
        val COUNT = ALL.size
    }
}

/** Named response curves on 0..1 (plan 1.5): how a drive level becomes a share of appeal. */
object Curves {
    /** Rises with [x]: x to the power [k] (k > 1 waits for a strong wish). */
    fun rise(x: Double, k: Double = 1.0): Double = Math.pow(x.coerceIn(0.0, 1.0), k)

    /** Falls with [x]: (1 − x) to the power [k]. */
    fun fall(x: Double, k: Double = 1.0): Double = Math.pow(1.0 - x.coerceIn(0.0, 1.0), k)

    /** Peaks at [centre] with half-width [width] (a Gaussian bump, 1 at the centre). */
    fun bell(x: Double, centre: Double, width: Double): Double {
        val u = (x - centre) / width
        return Math.exp(-u * u)
    }

    /** Linear interpolation from [lo] at 0 to [hi] at 1. */
    fun lerp(lo: Double, hi: Double, t: Double): Double = lo + (hi - lo) * t.coerceIn(0.0, 1.0)
}
