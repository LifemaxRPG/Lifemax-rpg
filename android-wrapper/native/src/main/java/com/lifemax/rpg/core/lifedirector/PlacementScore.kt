package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.lifedirector.PageValues.clamp
import com.lifemax.rpg.core.lifedirector.PageValues.number
import com.lifemax.rpg.core.lifedirector.PageValues.truthy
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/**
 * LDU-1: a placement's score in parts, as `scorePlacement(..., true)` reports it: the utility [u],
 * the fairness multiplier [fairness], the weighted sum before penalties, each weighted factor (in
 * the page's order: urgency, priority, energy, preference, spacing, lag, variety), the three
 * penalties, the wake phase, the two energies and whether the day is a preferred rest day.
 */
class ScoreDetail(
    val u: Double,
    val fairness: Double,
    val weightedSum: Double,
    val factors: List<Pair<String, Double>>,
    val fragmentation: Double,
    val movement: Double,
    val compression: Double,
    val phase: WakePhase,
    val energyP: Double,
    val energyC: Double,
    val restDay: Boolean,
)

/** LDU-1 `scorePlacement` and LDU-2 `fairnessMult`. */
object PlacementScore {
    private const val HOUR = 3600.0
    private const val W_URGENCY = 24.0
    private const val W_PRIORITY = 18.0
    private const val W_ENERGY = 18.0
    private const val W_PREFERENCE = 12.0
    private const val W_SPACING = 12.0
    private const val W_LAG = 10.0
    private const val W_VARIETY = 6.0
    private const val URGENCY_FLOOR = 0.85
    private const val URGENCY_EARLY = 0.15
    private const val PLAIN = 0.60
    private const val PREFERRED = 1.0
    private const val AVOIDED = 0.15
    private const val SPACING_LEAST_SIGMA = 4.0
    private const val SPACING_FLOOR = 0.6
    private const val VARIETY = 0.5
    private const val FRAGMENT_ONE = 3.0
    private const val FRAGMENT_TWO = 1.5
    private const val MOVE_SOON = 25.0
    private const val MOVE_LEAST = 5.0
    private const val MOVE_RANGE = 15.0
    private const val MOVE_MOST = 20.0
    private const val MOVE_SOON_HOURS = 24.0
    private const val MOVE_DECAY_HOURS = 72.0
    private const val COMPRESSION = 8.0
    private const val FAIR_GAIN = 1.5
    private const val FAIR_LOW = 0.75
    private const val FAIR_HIGH = 1.25
    private const val MICRO = 1e6

    /** `q6`: rounded to millionths (`Math.round(x * 1e6) / 1e6`). */
    fun q6(x: Double): Double = PageValues.round(x * MICRO) / MICRO

    /**
     * LDU-2: 1 for raw scores, a task with no goal, or no goal minutes yet; else `clamp(1 + 1.5 *
     * (target share - actual share), 0.75, 1.25)`, the actual share being the goal's minutes over
     * the total.
     */
    fun fairness(s: PlanState, t: PlanTask): Double {
        val goal = t.task.goalId
        if (s.rawScores || goal.isNullOrEmpty() || !truthy(s.fair.total)) return 1.0
        val target = s.fair.share[goal]?.takeIf { truthy(it) } ?: 0.0
        val actual = (s.fair.goalMinutes[goal]?.takeIf { truthy(it) } ?: 0.0) / s.fair.total
        return clamp(1 + FAIR_GAIN * (target - actual), FAIR_LOW, FAIR_HIGH)
    }

    /** LDU-1: the utility of task [t] at slots [slot] to `slot + n` of waking day [c]. */
    fun utility(s: PlanState, t: PlanTask, c: CycleState, slot: Int, n: Int): Double =
        evaluate(s, t, c, slot, n, null)

    /** LDU-1 with every part. */
    fun detail(s: PlanState, t: PlanTask, c: CycleState, slot: Int, n: Int): ScoreDetail {
        val out = arrayOfNulls<ScoreDetail>(1)
        evaluate(s, t, c, slot, n, out)
        return out[0]!!
    }

    private fun evaluate(
        s: PlanState,
        t: PlanTask,
        c: CycleState,
        slot: Int,
        n: Int,
        out: Array<ScoreDetail?>?,
    ): Double {
        val task = t.task
        val g = s.energy
        val start = s.epochOf(slot.toDouble())
        var urgency = t.prepared.urgencyBase
        if (truthy(task.deadlineEpoch)) {
            val span = maxOf(1.0, number(task.deadlineEpoch) - s.now)
            urgency *= URGENCY_FLOOR + URGENCY_EARLY * (1 - clamp((start - s.now) / span, 0.0, 1.0))
        }
        val eP = EnergyModel.at(s, slot, true)
        val eC = EnergyModel.at(s, slot, false)
        val energy = minOf(EnergyModel.fit(eP, t.physical), EnergyModel.fit(eC, t.cognitive))
        val phase = WakePhase.at((start - c.cycle.wakeEpoch) / HOUR)
        val minute = s.slots.localMinute[slot].toDouble()
        var pref = PLAIN
        if (s.plan.grid.preferred[slot]) pref = PREFERRED
        else if (hasPhase(task.phases, phase)) pref = PREFERRED
        for (w in s.avoidWindows) if (
            PageValues.atLeast(JsonNumber(minute), w.first) &&
                PageValues.less(JsonNumber(minute), w.second)
        )
            pref = AVOIDED
        val restDay = s.restDays[s.plan.days[s.slots.dayIndex[slot]].date.weekday]
        if (restDay && t.high) pref = AVOIDED
        if (task.preferRestDay == true && restDay) pref = maxOf(pref, PREFERRED)
        val space = spacing(s, t, start)
        var variety = 0.0
        val axis = t.physicalLeads
        for (i in intArrayOf(slot - 1, slot + n)) if (
            i >= 0 &&
                i < s.size &&
                g.type[i] == SlotCode.TASK.code &&
                (g.taskPhysical[i] > g.taskCognitive[i]) != axis
        )
            variety = VARIETY
        val lag = t.prepared.lag ?: Double.NaN
        val wsum =
            W_URGENCY * urgency +
                W_PRIORITY * t.prepared.priority +
                W_ENERGY * energy +
                W_PREFERENCE * pref +
                W_SPACING * space +
                W_LAG * lag +
                W_VARIETY * variety
        val gapBefore = g.runBack[slot] - 1
        val gapAfter = g.runAhead[slot] - n
        val fragmentation = gapPenalty(gapBefore) + gapPenalty(gapAfter)
        val movement = movement(s, t, start)
        val compression = (t.prepared.dOptSlots - n) * COMPRESSION
        val f = fairness(s, t)
        val u = q6(f * (wsum - fragmentation - movement - compression))
        if (out != null)
            out[0] =
                ScoreDetail(
                    u,
                    f,
                    wsum,
                    listOf(
                        "urgency" to W_URGENCY * urgency,
                        "priority" to W_PRIORITY * t.prepared.priority,
                        "energy" to W_ENERGY * energy,
                        "preference" to W_PREFERENCE * pref,
                        "spacing" to W_SPACING * space,
                        "lag" to W_LAG * lag,
                        "variety" to W_VARIETY * variety,
                    ),
                    fragmentation,
                    movement,
                    compression,
                    phase,
                    eP,
                    eC,
                    restDay,
                )
        return u
    }

    private fun gapPenalty(gap: Int): Double =
        when (gap) {
            1 -> FRAGMENT_ONE
            2 -> FRAGMENT_TWO
            else -> 0.0
        }

    /** `phases.includes(phase)`: a list's item, or a part of a text; anything else throws. */
    private fun hasPhase(phases: JsonValue, phase: WakePhase): Boolean =
        when (phases) {
            is JsonArray -> phases.items.any { it is JsonString && it.value == phase.name }
            is JsonString -> phases.value.contains(phase.name)
            else -> throw PageTypeError("phases.includes is not a function")
        }

    /**
     * The spacing factor: 1 with no session of the key; else, with `near` the hours to the nearest
     * one, the ideal gap `h` and `sig = max(4, h / 2)`: at or past the ideal `max(0.6, exp(-(near -
     * h)^2 / (2 (2 sig)^2)))`, before it `exp(-(near - h)^2 / (2 sig^2))` ([PlanState.exp]).
     */
    private fun spacing(s: PlanState, t: PlanTask, start: Double): Double {
        val starts = s.spacingOf(t.task.spacingKey)
        if (starts.isNullOrEmpty()) return 1.0
        var near = Double.POSITIVE_INFINITY
        for (x in starts) near = minOf(near, Math.abs(start - x) / HOUR)
        val ideal = t.idealGap
        val sig = maxOf(SPACING_LEAST_SIGMA, ideal * 0.5)
        val d = near - ideal
        return if (near >= ideal) {
            val wide = sig * 2
            maxOf(SPACING_FLOOR, s.exp(-(d * d) / (2 * (wide * wide))))
        } else s.exp(-(d * d) / (2 * sig * sig))
    }

    /**
     * The movement penalty of a session a re-plan found elsewhere: none at its old start; 25 when
     * that start is less than a day from the clock; else `clamp(5 + 15 * exp(-(hours - 24) / 72),
     * 5, 20)`.
     */
    private fun movement(s: PlanState, t: PlanTask, start: Double): Double {
        val orig = t.origStartEpoch
        if (!truthy(orig) || PageValues.same(orig, JsonNumber(start))) return 0.0
        val hours = (number(orig) - s.now) / HOUR
        return if (hours < MOVE_SOON_HOURS) MOVE_SOON
        else
            clamp(
                MOVE_LEAST + MOVE_RANGE * s.exp(-(hours - MOVE_SOON_HOURS) / MOVE_DECAY_HOURS),
                MOVE_LEAST,
                MOVE_MOST,
            )
    }
}
