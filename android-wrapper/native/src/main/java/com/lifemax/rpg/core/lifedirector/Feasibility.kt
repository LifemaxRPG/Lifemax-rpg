package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.lifedirector.PageValues.member
import com.lifemax.rpg.core.lifedirector.PageValues.number
import com.lifemax.rpg.core.lifedirector.PageValues.truthy
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import kotlin.math.floor

/**
 * LDK-2: why a placement is refused, with the page's numeric codes (`R`, `engine.js:792-793`) and
 * the reason in words (`R_TEXT`, `794-801`). [OK] is no refusal.
 */
enum class Refusal(val code: Int, val words: String) {
    OK(0, ""),
    BUSY(1, "no free window long enough"),
    WINDOW(2, "outside the allowed dates"),
    CAP(4, "your daily effort limit was already reached"),
    PER_DAY(5, "only one session of this kind is allowed per day"),
    SPACING(6, "it needs rest spacing from a similar session"),
    BEFORE_SLEEP(7, "it would end too close to bedtime"),
    FATIGUE(8, "estimated energy was too low"),
    POST_SHIFT(9, "heavy physical work earlier that day"),
    CONSECUTIVE(10, "it would extend a long run of demanding activities"),
    DOWNSTREAM(11, "it would leave a later demanding task under-energised"),
    PREREQUISITE(12, "its prerequisite is not scheduled yet"),
    HIGH_COUNT(13, "the day already has enough high-intensity sessions"),
    PRIOR_VOLUME(14, "it needs earlier study sessions first"),
    RECOVERY(15, "the only free time left is protected recovery"),
}

/**
 * LDK-1: the slots a task may use: from [earliestSlot] up to (not including) [latestSlot]; or
 * [blocked] (a missing prerequisite or too few earlier sessions) and no slots at all.
 */
class TaskWindow(val earliestSlot: Double, val latestSlot: Double, val blocked: Refusal? = null)

/** LDK-1 `taskEarliestLatest` and LDK-2 `checkHard`. */
object Feasibility {
    private const val MINUTE = 60.0
    private const val HOUR = 3600.0
    private const val WEEK = 7 * 86400.0
    private const val TASK_DRAIN = 0.015
    private const val SLOTS_PER_HOUR = 4
    private const val ENERGY_FLOOR = 0.45
    private const val HIGH = 4

    /**
     * LDK-1: a task's window: from the later of its window start and the clock, and after the end
     * of each session it depends on plus its lag (a prerequisite not on the plan blocks it), and
     * after the n-th earliest end of its prior-volume sessions (those on the plan inside its
     * window, and completed ones from a week before its window, whose start counts as their end;
     * too few block it); up to the earliest of its window end, its deadline and the plan's end. In
     * slots: the earliest rounded up (never before the plannable slot), the latest rounded down
     * (never past the grid).
     */
    fun window(s: PlanState, t: PlanTask): TaskWindow {
        val task = t.task
        var earliest = maxOf(task.windowStart, s.now)
        val latest = minOf(task.windowEnd, t.deadline, s.slots.endEpoch)
        for (d in task.dependsOn) {
            val inst =
                s.sessionById[JsonString("i" + d.substring(1))]
                    ?: return TaskWindow(Double.NaN, Double.NaN, Refusal.PREREQUISITE)
            earliest = maxOf(earliest, inst.endEpoch + task.minLagMin * MINUTE)
        }
        val prior = task.requiresPrior
        if (prior != null) {
            val ends = ArrayList<Double>()
            for (inst in s.sessions) if (
                inTemplates(prior, inst.templateId) &&
                    inst.startEpoch >= task.windowStart - 0 &&
                    inst.startEpoch < task.windowEnd
            )
                ends += inst.endEpoch
            val history = member(s.input, "completionHistory")
            if (history is JsonArray)
                for (h in history.items) {
                    val start = number(member(h, "startEpoch"))
                    if (
                        inTemplates(prior, member(h, "templateId")) &&
                            start >= task.windowStart - WEEK &&
                            start < task.windowEnd
                    )
                        ends += start
                }
            ends.sort()
            val count = number(prior.count)
            if (ends.size < count) return TaskWindow(Double.NaN, Double.NaN, Refusal.PRIOR_VOLUME)
            val at = count - 1
            val nth =
                if (at >= 0 && at < ends.size && at == floor(at)) ends[at.toInt()] else Double.NaN
            earliest = maxOf(earliest, nth)
        }
        return TaskWindow(
            maxOf(s.nowSlot, s.slots.slotAtOrAfter(earliest)),
            minOf(s.size.toDouble(), s.slots.slotAt(latest)),
        )
    }

    private fun inTemplates(prior: PriorVolume, id: JsonValue?) =
        id is JsonString && id.value in prior.templateIds

    /**
     * LDK-2 `checkHard`: whether task [t] may take slots [s] to `s + n` of waking day [c], checked
     * in the page's order, the first failure answering: inside its window; a free run of `n` from
     * `s`; the day's goal minutes within the daily maximum; fewer of its key that day than its
     * per-day limit; a high-intensity task only below the day's high-intensity limit; ending
     * outside its quiet time before sleep; its spacing from every session of its key; no heavy
     * physical task after a heavy shift; for a high-intensity task, enough energy in every slot
     * (0.45 after its own drain so far) and no run of demanding sessions longer than the maximum;
     * and every later high-intensity session of the day still with 0.45 after this one's drain.
     */
    fun check(s: PlanState, t: PlanTask, c: CycleState, slot: Int, n: Int, w: TaskWindow): Refusal {
        val task = t.task
        val p = s.prefs
        val g = s.energy
        if (slot < w.earliestSlot || slot + n > w.latestSlot) return Refusal.WINDOW
        if (g.runAhead[slot] < n) return Refusal.BUSY
        if (c.goalMinutes + n * PlanState.SLOT_MINUTES > number(p["maxDailyGoalMinutes"]))
            return Refusal.CAP
        val sameKey = c.perKey[task.spacingKey] ?: 0.0
        if (PageValues.atLeast(JsonNumber(sameKey), task.maxPerBwc)) return Refusal.PER_DAY
        if (
            t.high &&
                PageValues.atLeast(JsonNumber(c.highCount.toDouble()), p["maxHighIntensityPerDay"])
        )
            return Refusal.HIGH_COUNT
        val start = s.epochOf(slot.toDouble())
        val end = s.epochOf((slot + n).toDouble())
        if (
            truthy(task.avoidBeforeSleepMin) &&
                end > c.cycle.sleepEpoch - number(task.avoidBeforeSleepMin) * MINUTE
        )
            return Refusal.BEFORE_SLEEP
        val starts = s.spacingOf(task.spacingKey)
        if (starts != null && PageValues.order(task.spacingMinH, ZERO) == 1) {
            val limit = number(task.spacingMinH) * HOUR
            for (x in starts) if (Math.abs(start - x) < limit) return Refusal.SPACING
        }
        if (t.physicalHigh && c.cycle.heavyShiftEndSlot >= 0 && slot >= c.cycle.heavyShiftEndSlot)
            return Refusal.POST_SHIFT
        if (t.physicalHigh || t.cognitiveHigh) {
            if (tooTired(s, t, slot, n)) return Refusal.FATIGUE
            if (
                demandingRun(s, c, slot, n) * PlanState.SLOT_MINUTES >
                    number(p["maxConsecutiveHighLoadMinutes"])
            )
                return Refusal.CONSECUTIVE
        }
        if (c.sessions.isNotEmpty() && starvesLater(s, t, c, n, end)) return Refusal.DOWNSTREAM
        return Refusal.OK
    }

    private fun tooTired(s: PlanState, t: PlanTask, slot: Int, n: Int): Boolean {
        for (k in 0 until n) {
            val own = TASK_DRAIN * k / SLOTS_PER_HOUR
            if (
                t.physicalHigh &&
                    EnergyModel.at(s, slot + k, true) - own * t.physical < ENERGY_FLOOR
            )
                return true
            if (
                t.cognitiveHigh &&
                    EnergyModel.at(s, slot + k, false) - own * t.cognitive < ENERGY_FLOOR
            )
                return true
        }
        return false
    }

    /**
     * The run of demanding task or kept slots this placement would join, its own slots included.
     */
    private fun demandingRun(s: PlanState, c: CycleState, slot: Int, n: Int): Int {
        var run = n
        var i = slot - 1
        while (i >= c.start && demanding(s, i)) {
            run++
            i--
        }
        i = slot + n
        while (i < c.end && demanding(s, i)) {
            run++
            i++
        }
        return run
    }

    private fun demanding(s: PlanState, i: Int): Boolean {
        val g = s.energy
        val code = g.type[i]
        return (code == SlotCode.TASK.code || code == SlotCode.LOCKED.code) &&
            (g.taskPhysical[i] >= HIGH || g.taskCognitive[i] >= HIGH)
    }

    private fun starvesLater(
        s: PlanState,
        t: PlanTask,
        c: CycleState,
        n: Int,
        end: Double,
    ): Boolean {
        val drainP = TASK_DRAIN * t.physical * n / SLOTS_PER_HOUR
        val drainC = TASK_DRAIN * t.cognitive * n / SLOTS_PER_HOUR
        for (o in c.sessions) {
            if (o.startEpoch < end || !o.high) continue
            val os = s.slots.slotAt(o.startEpoch).toInt()
            if (
                PlanSession.atLeastFour(o.physical) &&
                    EnergyModel.at(s, os, true) - drainP < ENERGY_FLOOR
            )
                return true
            if (
                PlanSession.atLeastFour(o.cognitive) &&
                    EnergyModel.at(s, os, false) - drainC < ENERGY_FLOOR
            )
                return true
        }
        return false
    }

    private val ZERO = JsonNumber(0.0)
}
