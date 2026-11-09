package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.lifedirector.PageValues.clamp
import com.lifemax.rpg.core.lifedirector.PageValues.number

/** LDU-1: where in the waking day a slot falls, by hours since waking. */
enum class WakePhase {
    WAKE,
    PEAK,
    DIP,
    SECONDARY,
    WIND;

    companion object {
        private const val WAKE_END = 1.0
        private const val PEAK_END = 5.5
        private const val DIP_END = 7.5
        private const val SECONDARY_END = 11.5

        /** `phaseOf`: under 1 hour WAKE, under 5.5 PEAK, under 7.5 DIP, under 11.5 SECONDARY. */
        fun at(hours: Double): WakePhase =
            when {
                hours < WAKE_END -> WAKE
                hours < PEAK_END -> PEAK
                hours < DIP_END -> DIP
                hours < SECONDARY_END -> SECONDARY
                else -> WIND
            }
    }
}

/**
 * LDR-2, LDR-5: the energy model. Each slot of a waking day has a physical and a cognitive base
 * level, a curve of the hours since waking scaled by how much of the sleep need the night before
 * gave; what the day has drained so far (LDE-3) is taken off, the result cut to 0..1, and a
 * check-in pulls it toward the reported level.
 */
object EnergyModel {
    private const val HOUR = 3600.0
    private const val FLOOR_FACTOR = 0.5
    private const val CEILING_FACTOR = 1.05
    private const val CHECK_IN_HALF_LIFE = 6 * HOUR
    private const val FIT_SLOPE = 0.30
    private const val FIT_DEMAND = 0.18
    private const val FIT_SHORTFALL = 2.8

    /** The physical base at [t] hours since waking (`baselineP`). */
    fun physicalBase(t: Double): Double =
        when {
            t < 1 -> 0.55 + 0.25 * t
            t < 5.5 -> 0.90
            t < 7.5 -> 0.80
            t < 11.5 -> 0.90
            t <= 16.5 -> 0.80 - 0.5 * (t - 11.5) / 5
            else -> 0.30
        }

    /** The cognitive base at [t] hours since waking (`baselineC`). */
    fun cognitiveBase(t: Double): Double =
        when {
            t < 1 -> 0.50 + 0.35 * t
            t < 5.5 -> 0.95
            t < 7.5 -> 0.70
            t < 11.5 -> 0.80
            t <= 16.5 -> 0.75 - 0.45 * (t - 11.5) / 5
            else -> 0.30
        }

    /**
     * LDR-2 `initEnergy`: for each waking day, its sleep factor `clamp(sleep before / need, 0.5,
     * 1.05)`, and each of its slots' bases times that factor, stored in single precision; then its
     * drain (LDE-3).
     */
    fun initialise(s: PlanState) {
        val need = number(s.sleepTarget)
        for (c in s.cycles) {
            c.sleepFactor = clamp(c.cycle.sleepBeforeMin / need, FLOOR_FACTOR, CEILING_FACTOR)
            for (i in c.start until c.end) {
                val tau = (s.epochOf(i.toDouble()) - c.cycle.wakeEpoch) / HOUR
                s.baseP[i] = (physicalBase(tau) * c.sleepFactor).toFloat()
                s.baseC[i] = (cognitiveBase(tau) * c.sleepFactor).toFloat()
            }
            s.recompute(c)
        }
    }

    /**
     * LDR-5 `energyAt`: the base less the drain at slot [i] (both single-precision values, taken as
     * doubles), cut to 0..1; from a check-in's instant on, `a * level + (1 - a) * e` with `a = 0.5
     * ^ (hours since / 6)` ([PageMath.pow], kept per slot). [physical] picks the axis.
     */
    fun at(s: PlanState, i: Int, physical: Boolean): Double {
        val g = s.energy
        var e =
            if (physical) s.baseP[i].toDouble() - g.drainPhysical[i].toDouble()
            else s.baseC[i].toDouble() - g.drainCognitive[i].toDouble()
        e = clamp(e, 0.0, 1.0)
        val ck = s.checkIn ?: return e
        val t = s.epochOf(i.toDouble())
        if (t >= ck.at) {
            val a = checkInPull(s, i, t - ck.at)
            e = a * ck.value + (1 - a) * e
        }
        return e
    }

    /**
     * The check-in's pull at slot [i], `0.5 ^ (since / 6 h)`: it depends on the slot alone, so it
     * is worked out once per slot and kept (the same value, [PageMath.pow] being a function of its
     * arguments; a pow per energy read was most of placement's time).
     */
    private fun checkInPull(s: PlanState, i: Int, since: Double): Double {
        val kept = s.checkInPull[i]
        if (!kept.isNaN()) return kept
        val a = PageMath.pow(FLOOR_FACTOR, since / CHECK_IN_HALF_LIFE)
        s.checkInPull[i] = a
        return a
    }

    /**
     * LDU-1 `energyFit`: how well energy [avail] suits a demand of [intensity]: with `d = avail -
     * 0.18 * intensity`, `1 - 0.30 * d` when `d >= 0`, else `max(0, 1 - 2.8 * d * d)`.
     */
    fun fit(avail: Double, intensity: Double): Double {
        val d = avail - FIT_DEMAND * intensity
        return if (d >= 0) 1 - FIT_SLOPE * d else maxOf(0.0, 1 - FIT_SHORTFALL * d * d)
    }
}
