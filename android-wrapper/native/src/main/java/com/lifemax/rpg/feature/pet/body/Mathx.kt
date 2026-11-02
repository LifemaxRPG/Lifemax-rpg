package com.lifemax.rpg.feature.pet.body

import kotlin.math.floor

/**
 * Small float helpers. `sin` and `cos` are plain float arithmetic (range reduction plus a
 * polynomial) rather than `Math.sin`, for two reasons: the result is bit-identical on every JVM and
 * every JIT tier (`Math.sin` may differ in the last place), and `StrictMath.sin` allocates scratch
 * arrays on some JDKs, which would break the no-allocation rule of the step path.
 */
internal object Mathx {
    const val PI = 3.1415927f
    const val TWO_PI = 6.2831855f

    private const val HALF_PI = 1.5707964f
    private const val INV_6 = 1f / 6f
    private const val INV_120 = 1f / 120f
    private const val INV_5040 = 1f / 5040f
    private const val INV_362880 = 1f / 362880f
    private const val INV_39916800 = 1f / 39916800f

    /** Sine, accurate to about 1e-6 for any finite input. */
    fun sin(x: Float): Float {
        // Bring x into [-pi, pi], then fold into [-pi/2, pi/2] where the series converges fast.
        var r = x - TWO_PI * floor(x / TWO_PI + 0.5f)
        if (r > HALF_PI) r = PI - r else if (r < -HALF_PI) r = -PI - r
        val r2 = r * r
        val poly =
            1f -
                r2 *
                    (INV_6 -
                        r2 * (INV_120 - r2 * (INV_5040 - r2 * (INV_362880 - r2 * INV_39916800))))
        return r * poly
    }

    fun cos(x: Float): Float = sin(x + HALF_PI)

    fun clamp(v: Float, lo: Float, hi: Float): Float = if (v < lo) lo else if (v > hi) hi else v

    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    fun abs(v: Float): Float = if (v < 0f) -v else v

    fun sign(v: Float): Float = if (v < 0f) -1f else 1f

    /** JavaScript's `Math.round`: halves go up. The web room rounds with it. */
    fun jsRound(v: Float): Float = floor(v + 0.5f)

    /** Move [v] toward [target] by at most [maxStep]. */
    fun moveToward(v: Float, target: Float, maxStep: Float): Float =
        if (target - v > maxStep) v + maxStep else if (v - target > maxStep) v - maxStep else target

    /** Smooth 0..1 ramp: 0 at or below [e0], 1 at or above [e1]. */
    fun smoothstep(e0: Float, e1: Float, v: Float): Float {
        val t = clamp((v - e0) / (e1 - e0), 0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
