package com.lifemax.rpg.app.shell.swipe

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The shape of the drag (ADR 0011, the feel). u is the finger's travel toward the neighbour as a
 * fraction of the width, measured from the claim point; the result is how far the screen has moved,
 * o, in the same unit.
 *
 * ```
 * hold(u)    = u                                   u ≤ a          (1:1)
 *            = u − (1−m)(u−a)² / (2(b−a))          a < u ≤ b      (slope falls 1 → m)
 *            = hold(b) + m(u−b)                    u > b          (only on the event that latches)
 * through(u) = min(u, 1)                                          (latched: 1:1 again)
 * wall(u)    = E·(1 − e^(−κ·u/E))                                 (rubber band, never reaches E)
 * ```
 *
 * With the defaults the screen sits at .243/.280/.310/.333/.349 for u = .25/.30/.35/.40/.45, 42 dp
 * behind the finger on a 412 dp phone at the catch. Pure functions; negative u (the finger back
 * past the claim point) follows the 1:1 and rubber-band branches.
 */
object TensionCurve {
    fun hold(u: Float, tune: SwipeTune): Float {
        val a = tune.knee
        val b = tune.breakAt
        return when {
            u <= a -> u
            u <= b -> u - (1f - tune.heavy) * (u - a) * (u - a) / (2f * (b - a))
            else -> hold(b, tune) + tune.heavy * (u - b)
        }
    }

    fun holdSlope(u: Float, tune: SwipeTune): Float {
        val a = tune.knee
        val b = tune.breakAt
        return when {
            u <= a -> 1f
            u <= b -> 1f - (1f - tune.heavy) * (u - a) / (b - a)
            else -> tune.heavy
        }
    }

    /**
     * The inverse of [hold] up to the catch: the u whose hold(u) is [o] (a finger that catches a
     * screen already standing at o holds it where the curve says it stands, not where the finger
     * is). Beyond hold(b) the result is b: the caller carries the rest as a residual.
     */
    fun invertHold(o: Float, tune: SwipeTune): Float {
        val a = tune.knee
        val b = tune.breakAt
        if (o <= a) return o
        if (o >= hold(b, tune)) return b
        val k = (1f - tune.heavy) / (2f * (b - a))
        if (k <= 0f) return o
        // o = a + t − k·t² with t = u − a; the smaller root is the one on the rising branch
        val t = (1f - sqrt((1f - 4f * k * (o - a)).coerceAtLeast(0f))) / (2f * k)
        return a + t
    }

    fun through(u: Float): Float = min(u, 1f)

    fun throughSlope(u: Float): Float = if (u < 1f) 1f else 0f

    fun wall(u: Float, tune: SwipeTune): Float {
        val e = tune.wallMax
        return if (e <= 0f) 0f else e * (1f - exp(-tune.wallSlope * u / e))
    }

    fun wallSlope(u: Float, tune: SwipeTune): Float {
        val e = tune.wallMax
        return if (e <= 0f) 0f else tune.wallSlope * exp(-tune.wallSlope * u / e)
    }

    /** The latch with hysteresis: it engages at b and lets go only below b − h. */
    fun latched(u: Float, wasLatched: Boolean, tune: SwipeTune): Boolean =
        if (wasLatched) u >= tune.breakAt - tune.hyst else u >= tune.breakAt
}

/** A change of the latch, which plays a haptic. */
enum class Flip {
    /** Forward over the catch: the pop. */
    Latch,
    /** Back under b − h: the tension returns. */
    Unlatch,
}

/**
 * The tension of one drag toward one side: whether the catch has let go, and the residual offset
 * that keeps the screen continuous when it does. At a latch flip the curve jumps from s_old to
 * s_new, so the residual takes up the difference (r += s_old − s_new, keeping its velocity) and a
 * spring carries it back to 0: underdamped at a latch (the pop, about 7% overshoot with the
 * defaults), critically damped at an unlatch. The screen sits at o = clamp(s(u) + r, 0, 1).
 *
 * A [wall] side never latches and follows the rubber band. Immutable.
 */
data class Tension(val latched: Boolean = false, val residual: Spring1D? = null) {
    /** s(u): the curve this tension follows now. */
    fun shape(u: Float, tune: SwipeTune, wall: Boolean): Float =
        when {
            wall -> TensionCurve.wall(u, tune)
            latched -> TensionCurve.through(u)
            else -> TensionCurve.hold(u, tune)
        }

    /** ds/du at [u]. */
    fun slope(u: Float, tune: SwipeTune, wall: Boolean): Float =
        when {
            wall -> TensionCurve.wallSlope(u, tune)
            latched -> TensionCurve.throughSlope(u)
            else -> TensionCurve.holdSlope(u, tune)
        }

    fun residualAt(now: Long): Float = residual?.position(now) ?: 0f

    /** s(u) + r, before the clamp. Below 0 the screen has come back past where it started. */
    fun raw(u: Float, now: Long, tune: SwipeTune, wall: Boolean): Float =
        shape(u, tune, wall) + residualAt(now)

    /** o: the screen's offset toward this side, as a fraction of the width. */
    fun offset(u: Float, now: Long, tune: SwipeTune, wall: Boolean): Float =
        raw(u, now, tune, wall).coerceIn(0f, 1f)

    /**
     * do/dt for a finger moving at [du] (width fractions per second): the curve's plus the
     * residual's.
     */
    fun velocity(u: Float, du: Float, now: Long, tune: SwipeTune, wall: Boolean): Float =
        slope(u, tune, wall) * du + (residual?.velocity(now) ?: 0f)

    /** The residual has settled, so drawing needs no more frames until the finger moves. */
    fun isAtRest(now: Long, epsilon: Float): Boolean = residual?.isAtRest(now, epsilon) ?: true

    /**
     * The finger is now at [u]. Returns the new tension and the flip it caused, if any. A flip
     * moves the residual by the curve's jump and starts its spring at [now], scaled by
     * [durationScale] (0 makes the pop instant).
     */
    fun follow(
        u: Float,
        now: Long,
        durationScale: Float,
        tune: SwipeTune,
        wall: Boolean,
    ): Followed {
        val nowLatched = !wall && TensionCurve.latched(u, latched, tune)
        if (nowLatched == latched) return Followed(this, null)
        val next = copy(latched = nowLatched)
        val jump = shape(u, tune, wall) - next.shape(u, tune, wall)
        val damping = if (nowLatched) tune.popDamping else 1f
        val spring =
            residual?.restart(
                now,
                position = residual.position(now) + jump,
                target = 0f,
                stiffness = tune.popStiffness,
                dampingRatio = damping,
                durationScale = durationScale,
            ) ?: Spring1D(jump, 0f, 0f, tune.popStiffness, damping, now, durationScale)
        return Followed(next.copy(residual = spring), if (nowLatched) Flip.Latch else Flip.Unlatch)
    }

    /**
     * The same screen seen from the other side, after the finger has crossed back over the claim
     * point: unlatched, with the residual mirrored so the screen does not jump.
     */
    fun reversed(now: Long): Tension =
        Tension(
            latched = false,
            residual = residual?.let { it.restart(now, -it.position(now), -it.velocity(now)) },
        )

    data class Followed(val tension: Tension, val flip: Flip?)

    /** A finger that has caught a screen: the travel u it starts at, and the tension to follow. */
    data class Held(val u: Float, val tension: Tension)

    companion object {
        /** Just under the catch, where a held screen standing between hold(b) and b is started. */
        private const val UNDER_CATCH = 0.002f

        /**
         * The tension of a finger that catches a screen standing at [offset] (chained swipes: the
         * inverse curve). Up to the catch the finger starts at the u the curve maps to [offset]; at
         * or past it, latched at u = offset (1:1, as after the pop). Where the curve cannot reach
         * [offset] (between hold(b) and b, or on a wall's rubber band) the finger starts at the
         * nearest u and a critically damped residual carries the difference away, so the screen
         * does not jump when it is caught.
         */
        fun holding(
            offset: Float,
            now: Long,
            durationScale: Float,
            tune: SwipeTune,
            wall: Boolean,
        ): Held {
            val o = offset.coerceIn(0f, 1f)
            if (!wall && o >= tune.breakAt) return Held(o, Tension(latched = true))
            val reach = if (wall) 0f else TensionCurve.hold(tune.breakAt, tune)
            val u =
                when {
                    wall || o <= 0f -> 0f
                    o <= reach -> TensionCurve.invertHold(o, tune)
                    else -> tune.breakAt - UNDER_CATCH
                }
            val tension = Tension()
            val gap = o - tension.shape(u, tune, wall)
            if (abs(gap) < RESIDUAL_EPSILON) return Held(u, tension)
            val spring = Spring1D(gap, 0f, 0f, tune.popStiffness, 1f, now, durationScale)
            return Held(u, tension.copy(residual = spring))
        }

        private const val RESIDUAL_EPSILON = 1e-4f
    }
}
