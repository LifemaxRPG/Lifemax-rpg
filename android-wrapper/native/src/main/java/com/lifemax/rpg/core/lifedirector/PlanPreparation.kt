package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.lifedirector.PageValues.member
import com.lifemax.rpg.core.lifedirector.PageValues.number
import com.lifemax.rpg.core.lifedirector.PageValues.or
import com.lifemax.rpg.core.lifedirector.PageValues.truthy
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import kotlin.math.ceil

/** LDR-3: a stored session that is still to happen but now overlaps something stronger. */
class DisplacedSession(val session: JsonValue, val slotCode: Int)

/**
 * What [PlanPreparation.prepare] gives: the [state], and the stored sessions sorted into [history]
 * (done, or wholly before the clock), [kept] (pinned or started, now on the plan), [displaced] and
 * [replannable] (still to happen and free to move, by instance id in start order).
 */
class PreparedPlan(
    val state: PlanState,
    val history: List<JsonValue>,
    val kept: List<JsonValue>,
    val displaced: List<DisplacedSession>,
    val replannable: LinkedHashMap<JsonValue?, JsonValue>,
)

/**
 * LDR-1 to LDR-4 `prepareState` (`engine.js:1163-1188`), the front half shared by planning and
 * repair: the painted availability, the state with its energy, the stored sessions sorted and the
 * kept ones placed, then each waking day's protected recovery.
 */
object PlanPreparation {
    private const val MINUTE = 60.0
    private const val DEFAULT_SLEEP_NEED = 480.0
    private val ACTIVE_STATES = setOf("PLANNED", "IN_PROGRESS")
    private val CHECK_IN_LEVELS = mapOf("LOW" to 0.30, "NORMAL" to 0.65, "HIGH" to 0.95)

    fun prepare(input: JsonValue?): PreparedPlan {
        val s = state(input)
        val existing = listOf(member(input, "existingSchedule")).flatMap { stored(it, "slice") }
        val sorted =
            existing.sortedWith { a, b ->
                val d = number(member(a, "startEpoch")) - number(member(b, "startEpoch"))
                if (truthy(d)) PageValues.byDifference(d) else textOrder(a, b)
            }
        val history = ArrayList<JsonValue>()
        val kept = ArrayList<JsonValue>()
        val displaced = ArrayList<DisplacedSession>()
        val replannable = LinkedHashMap<JsonValue?, JsonValue>()
        val h0 = s.slots.startEpoch
        val hn = s.slots.endEpoch
        for (inst in sorted) {
            val state = member(inst, "lifecycleState")
            if (!(state is JsonString && state.value in ACTIVE_STATES)) {
                history += inst
                continue
            }
            val start = number(member(inst, "startEpoch"))
            val end = number(member(inst, "endEpoch"))
            if (end <= h0 || start >= hn) {
                if (end <= s.now) history += inst
                continue
            }
            if (!(truthy(member(inst, "isUserLocked")) || start <= s.now)) {
                replannable[member(inst, "instanceId")] = inst
                continue
            }
            val clash = clash(s, start, end)
            val cycle = cycleAt(s, maxOf(start, s.now))
            if (clash != null && end > s.now) {
                displaced += DisplacedSession(inst, clash)
                continue
            }
            if (cycle < 0) {
                history += inst
                continue
            }
            s.occupy(keptSession(inst, s.cycles[cycle].cycle.id), cycle, SlotCode.LOCKED)
            kept += inst
        }
        for (c in s.cycles) reserveRecovery(s, c)
        return PreparedPlan(s, history, kept, displaced, replannable)
    }

    /** LDR-1, LDR-2 `buildState`: the state, the history's spacing, the energy model. */
    private fun state(input: JsonValue?): PlanState {
        val plan = Availability.derive(input)
        val now = number(member(input, "currentTimeEpoch"))
        val lead = number(plan.prefs["leadMinutes"]) * MINUTE
        val nowSlot =
            PageValues.clamp(plan.grid.slots.slotAtOrAfter(now + lead), 0.0, plan.grid.count)
        val need =
            or(
                member(member(input, "playerAvailability"), "sleepRequirementMinutes"),
                JsonNumber(DEFAULT_SLEEP_NEED),
            )!!
        val s = PlanState(input, plan, now, nowSlot, need, checkIn(input))
        for (h in listOf(member(input, "completionHistory")).flatMap { stored(it, "iterable") }) {
            if (!PageValues.present(h)) throw PageTypeError("Cannot read properties of null")
            val key = member(h, "spacingKey")
            if (truthy(key)) s.addSpacing(key, number(member(h, "startEpoch")))
        }
        EnergyModel.initialise(s)
        return s
    }

    /** LDR-5: a check-in counts when its epoch is truthy and its level is LOW, NORMAL or HIGH. */
    private fun checkIn(input: JsonValue?): CheckIn? {
        val profile = member(input, "energyProfile")
        val ck = if (truthy(profile)) member(profile, "checkin") else null
        if (!truthy(ck) || !truthy(member(ck, "epoch"))) return null
        val level = (member(ck, "level") as? JsonString)?.value ?: return null
        return CHECK_IN_LEVELS[level]?.let { CheckIn(member(ck, "epoch")!!, it) }
    }

    /** The items of a stored list (`|| []` first): a list's items; another truthy value throws. */
    private fun stored(value: JsonValue?, method: String): List<JsonValue> =
        when {
            !truthy(value) -> emptyList()
            value is JsonArray -> value.items
            else -> throw PageTypeError("$method is not a function")
        }

    /** `cmpStr(a.instanceId, b.instanceId)` (LDG-2). */
    private fun textOrder(a: JsonValue, b: JsonValue): Int {
        val x = member(a, "instanceId")
        val y = member(b, "instanceId")
        return if (PageValues.less(x, y)) -1 else if (PageValues.less(y, x)) 1 else 0
    }

    /** The first slot from the plannable slot on, inside the session, that is not free. */
    private fun clash(s: PlanState, start: Double, end: Double): Int? {
        val a = PageValues.clamp(s.slots.slotAt(start), 0.0, s.size.toDouble())
        val b = PageValues.clamp(s.slots.slotAtOrAfter(end), 0.0, s.size.toDouble())
        var i = maxOf(a, s.nowSlot)
        while (i < b) {
            val code = s.code(i.toInt())
            if (code != SlotCode.FREE.code) return code
            i += 1
        }
        return null
    }

    /** `bwcIndexAt`: the waking day of the slot holding [epoch], -1 for none or off the grid. */
    fun cycleAt(s: PlanState, epoch: Double): Int {
        val slot = s.slots.slotAt(epoch)
        return if (slot >= 0 && slot < s.size) s.plan.grid.wakeCycle[slot.toInt()] else -1
    }

    /** A stored session as the kept copy `occupy` places (its own members, its day, kept). */
    private fun keptSession(inst: JsonValue, cycleId: String): PlanSession =
        PlanSession(
            member(inst, "instanceId"),
            member(inst, "taskId"),
            member(inst, "templateId"),
            member(inst, "tag"),
            number(member(inst, "startEpoch")),
            number(member(inst, "endEpoch")),
            member(inst, "IP"),
            member(inst, "IC"),
            member(inst, "spacingKey"),
            PlanState.iterate(member(inst, "linkedGoalIds"), "linkedGoalIds"),
            ArrayList(),
            number(member(inst, "durationSlots")),
            cycleId,
            kept = true,
            stored = inst as? JsonObject,
        )

    /**
     * LDR-4 `reserveRecoveryFor`: the free slots of the day from the plannable slot on are counted;
     * the latest of them, up to the day's recovery need (`ceil(minimum recovery / 15)` slots),
     * become protected recovery; a day that has fewer, is not over and did not start before the
     * plan is marked short. Then its drain is worked out again.
     */
    private fun reserveRecovery(s: PlanState, c: CycleState) {
        val need = ceil(number(s.prefs["minDailyRecoveryMinutes"]) / PlanState.SLOT_MINUTES)
        val from = maxOf(c.cycle.startSlot, s.nowSlot)
        var free = 0
        var i = from
        while (i < c.cycle.endSlot) {
            if (s.code(i.toInt()) == SlotCode.FREE.code) free++
            i += 1
        }
        c.freeSlots = free
        var left = minOf(need, free.toDouble())
        c.recoverySlots = left.toInt()
        c.recoveryShortfall = free < need && c.cycle.endSlot > s.nowSlot && !c.cycle.truncatedStart
        i = c.cycle.endSlot - 1
        while (i >= from && left > 0) {
            val k = i.toInt()
            if (s.code(k) == SlotCode.FREE.code) {
                s.energy.type[k] = SlotCode.RECOVERY.code
                left--
            }
            i -= 1
        }
        s.recompute(c)
    }
}
