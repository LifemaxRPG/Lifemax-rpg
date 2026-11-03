package com.lifemax.rpg.feature.pet.body

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * One flight, solved: a ballistic arc from the take-off feet to the landing feet.
 *
 * @property apex highest point of the feet above the take-off row, px.
 * @property flight seconds from leaving the ground to touching down.
 * @property vx sideways speed during the flight, px/s (unsigned).
 * @property launchSpeed upward speed at take-off, px/s.
 * @property fallSpeed downward speed at touchdown, px/s.
 * @property effort 0..1, how much of the cat's best this jump uses: 0 is a hop, 1 the limit.
 */
class Arc(
    val apex: Float,
    val flight: Float,
    val vx: Float,
    val launchSpeed: Float,
    val fallSpeed: Float,
    val effort: Float,
)

/**
 * What one cat can jump. The model is plain ballistics: a flight leaves the ground with an upward
 * speed (limited by how high the cat can get, [apexStand]) and a sideways speed (limited by how
 * fast it can push off, [vxStand] standing and more off a running start). From those two limits
 * come the maximum rise from a standing start, the reach at a given rise, the extra a run-up adds,
 * and (a separate limit) the highest safe drop. There are no hand-made edges: a leap is possible
 * when [arc] finds a flight that fits.
 *
 * Build one with [of] (growth, hunger, rig morph, tiredness, mood); every number is in
 * [Tuning.Parkour].
 *
 * @property apexStand highest apex above the take-off feet from a standing start, px.
 * @property apexRunBonus extra apex a full-speed run-up adds.
 * @property vxStand sideways speed limit of a standing take-off, px/s.
 * @property runCarry share of the run-up's ground speed that carries into the flight (0: cannot
 *   run).
 * @property maxDrop furthest safe drop, px.
 * @property reachMax furthest sideways distance of any flight, take-off to landing, px.
 * @property sizeScale body size relative to a grown cat (1), for the record and the shadow.
 * @property weak true for a hungry cat: the planner counts its effort as more costly.
 */
class JumpCapability(
    val apexStand: Float,
    val apexRunBonus: Float,
    val vxStand: Float,
    val runCarry: Float,
    val maxDrop: Float,
    val reachMax: Float,
    val sizeScale: Float = 1f,
    val weak: Boolean = false,
) {
    /** The best apex there is: a full-speed run-up. */
    val apexBest: Float
        get() = apexStand + apexRunBonus

    /** The best sideways speed there is: a full-speed run-up. */
    val vxBest: Float
        get() = vxStand + runCarry * Tuning.Parkour.RUN_SPEED

    /** Ground speed after a run-up of [runUp] px from rest (constant acceleration, capped). */
    fun runSpeed(runUp: Float): Float =
        if (runCarry <= 0f || runUp <= 0f) 0f
        else minOf(Tuning.Parkour.RUN_SPEED, sqrt(2f * Tuning.Parkour.RUN_ACCEL * runUp))

    /** Highest apex off a run-up of [runUp] px. */
    fun apexLimit(runUp: Float): Float =
        apexStand + apexRunBonus * runSpeed(runUp) / Tuning.Parkour.RUN_SPEED

    /** Fastest sideways speed off a run-up of [runUp] px. */
    fun vxLimit(runUp: Float): Float = vxStand + runCarry * runSpeed(runUp)

    /** Highest rise (feet to feet) the cat can make: the apex limit less the clearance. */
    fun maxRise(runUp: Float = 0f): Float = apexLimit(runUp) - Tuning.Parkour.CLEARANCE

    /**
     * The flight for a rise of [rise] px (negative to go down) over [dx] px (unsigned) off a run-up
     * of [runUp] px, or null when no flight within the cat's limits fits. The apex is the lowest
     * one that keeps the speed to a comfortable share of the limit, or failing that to the limit.
     */
    fun arc(rise: Float, dx: Float, runUp: Float): Arc? {
        val g = Tuning.Parkour.GRAVITY
        if (rise < -maxDrop || dx > reachMax) return null
        val apexMax = apexLimit(runUp)
        val vxMax = vxLimit(runUp)
        val low = lowestApex(rise)
        if (low > apexMax) return null
        if (dx < MIN_DX) return arcFor(low, rise, dx, g, apexMax, vxMax)
        val comfort = vxMax * Tuning.Parkour.COMFORT
        var apex = maxOf(low, Ballistics.apexForSpeed(rise, dx, comfort, g))
        if (apex > apexMax) apex = maxOf(low, Ballistics.apexForSpeed(rise, dx, vxMax, g))
        if (apex > apexMax + EPS) return null
        return arcFor(minOf(apex, apexMax), rise, dx, g, apexMax, vxMax)
    }

    private fun arcFor(
        apex: Float,
        rise: Float,
        dx: Float,
        g: Float,
        apexMax: Float,
        vxMax: Float,
    ): Arc? {
        val flight = Ballistics.flightTime(apex, rise, g)
        val vx = dx / flight
        if (vx > vxMax + EPS) return null
        val effort = maxOf(apex / apexBest, vx / vxBest, (-rise) / maxDrop.coerceAtLeast(1f))
        val fall = sqrt(2f * g * maxOf(0f, apex - rise))
        return Arc(apex, flight, vx, sqrt(2f * g * apex), fall, minOf(1f, effort))
    }

    /** The lowest apex worth taking for a rise of [rise]: clears the target, or just hops. */
    fun lowestApex(rise: Float): Float =
        when {
            rise > Tuning.Parkour.STEP_MAX -> rise + Tuning.Parkour.CLEARANCE
            rise < -Tuning.Parkour.STEP_MAX -> Tuning.Parkour.DROP_APEX
            else -> maxOf(rise, 0f) + Tuning.Parkour.STEP_APEX
        }

    /**
     * The shortest run-up (0 for none) that makes a [rise] over [dx] possible with at most [runway]
     * px to run in, or -1 when none does.
     */
    fun runUpFor(rise: Float, dx: Float, runway: Float): Float {
        if (arc(rise, dx, 0f) != null) return 0f
        val longest = minOf(runway, Tuning.Parkour.RUN_UP_MAX_PX)
        if (longest < Tuning.Parkour.RUN_UP_MIN_PX || arc(rise, dx, longest) == null) return -1f
        var lo = 0f
        var hi = longest
        repeat(BISECT_STEPS) {
            val mid = (lo + hi) / 2f
            if (arc(rise, dx, mid) != null) hi = mid else lo = mid
        }
        return maxOf(hi, Tuning.Parkour.RUN_UP_MIN_PX)
    }

    companion object {
        private const val MIN_DX = 0.5f
        private const val EPS = 1e-3f
        private const val BISECT_STEPS = 14

        /** A grown, well-fed cat of the standard body. */
        val ADULT: JumpCapability = of(1f)

        /**
         * A cat of body scale [growth] (1 grown; the kitten stages are
         * [Tuning.Parkour.STAGE_SCALE], read from Growth), [weak] when hungry, with the rig's body
         * length [bodyLength] and leg length [legLength] (the `bl` and `lg` morph multipliers; 1
         * when unknown), tiredness [fatigue] 0..1 and mood [mood] -1..1.
         */
        fun of(
            growth: Float,
            weak: Boolean = false,
            bodyLength: Float = 1f,
            legLength: Float = 1f,
            fatigue: Float = 0f,
            mood: Float = 0f,
        ): JumpCapability {
            val p = Tuning.Parkour
            val size = growth.coerceIn(MIN_SIZE, 1f)
            val bl = bodyLength.coerceIn(p.MORPH_MIN, p.MORPH_MAX)
            val lg = legLength.coerceIn(p.MORPH_MIN, p.MORPH_MAX)
            val vigour =
                (1f - p.FATIGUE_LOSS * fatigue.coerceIn(0f, 1f) +
                        p.MOOD_GAIN * mood.coerceIn(-1f, 1f))
                    .coerceIn(MIN_VIGOUR, 1f + p.MOOD_GAIN)
            val feeble = if (weak) p.WEAK_SHARE else 1f
            val height = size.pow(p.SIZE_APEX_EXPONENT) * lg.pow(p.LEG_APEX_EXPONENT)
            val reach = size.pow(p.SIZE_VX_EXPONENT) * bl.pow(p.BODY_VX_EXPONENT)
            val k = vigour * feeble
            return JumpCapability(
                apexStand = p.APEX_STAND * height * k,
                apexRunBonus = p.APEX_RUN_BONUS * height * k,
                vxStand = p.VX_STAND * reach * k,
                runCarry = if (weak) p.WEAK_RUN_CARRY else p.RUN_CARRY,
                maxDrop = p.MAX_DROP * height * feeble,
                reachMax = p.REACH_MAX * reach * k,
                sizeScale = size,
                weak = weak,
            )
        }

        /**
         * The capability of a kitten at growth [stage] 0..5, or a grown cat for any other value.
         */
        fun forStage(stage: Int, weak: Boolean = false): JumpCapability =
            of(
                if (stage in Tuning.Parkour.STAGE_SCALE.indices) Tuning.Parkour.STAGE_SCALE[stage]
                else 1f,
                weak,
            )

        private const val MIN_SIZE = 0.3f
        private const val MIN_VIGOUR = 0.3f
    }
}

/** Ballistics for a flight over uniform gravity. A rise is positive upward. */
internal object Ballistics {
    /** Seconds from take-off to landing for an arc with [apex] above the take-off and a [rise]. */
    fun flightTime(apex: Float, rise: Float, g: Float): Float =
        sqrt(2f * apex / g) + sqrt(2f * maxOf(0f, apex - rise) / g)

    /**
     * The lowest apex whose flight covers [dx] at sideways speed [vx]; from `T = dx / vx` and
     * `sqrt(A) + sqrt(A - rise) = T * sqrt(g / 2)` (closed form), never below what the rise needs.
     */
    fun apexForSpeed(rise: Float, dx: Float, vx: Float, g: Float): Float {
        val k = dx / vx * sqrt(g / 2f)
        // K^2 <= rise: the flight asked for is shorter than the climb itself takes, so any apex
        // that clears the target does: the lowest.
        if (k * k <= rise) return maxOf(rise, 0f)
        val s = (k * k + rise) / (2f * k)
        return if (s <= 0f) 0f else maxOf(s * s, rise)
    }

    /** Feet height above the take-off at time [t] of an arc with launch speed [v0]. */
    fun lift(v0: Float, t: Float, g: Float): Float = v0 * t - 0.5f * g * t * t
}
