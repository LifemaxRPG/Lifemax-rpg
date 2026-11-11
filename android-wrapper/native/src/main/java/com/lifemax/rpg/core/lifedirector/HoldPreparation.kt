package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.lifedirector.PageValues.member
import com.lifemax.rpg.core.lifedirector.PageValues.number
import com.lifemax.rpg.core.lifedirector.PageValues.truthy
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import kotlin.math.ceil

/**
 * What [HoldPreparation.prepare] gives: the [state] with the held sessions on it, and the held
 * sessions it could not keep ([displaced], as they were held), in the order they were let go.
 */
class HeldPlan(val state: PlanState, val displaced: List<JsonObject>)

/**
 * A session on a rescheduled plan, read as `rescheduleActivity` reads it: a held session by its
 * stored members (the held copy, LDB-1), a session placed during the reschedule by what it took
 * from its task.
 */
internal object HeldSessions {
    fun locked(x: PlanSession): Boolean = x.stored?.let { truthy(it["isUserLocked"]) } ?: false

    fun kept(x: PlanSession): Boolean = x.stored?.let { truthy(it["kept"]) } ?: false

    fun tier(x: PlanSession): JsonValue? {
        val st = x.stored
        return if (st != null) st["tier"] else JsonNumber(x.commitment!!.tier.toDouble())
    }

    fun minSlots(x: PlanSession): Double {
        val st = x.stored
        return if (st != null) number(st["minDurationSlots"]) else x.commitment!!.minSlots
    }

    /** The length as stored (a held session's member), for the change record. */
    fun slotsValue(x: PlanSession): JsonValue? {
        val st = x.stored
        return if (st != null) st["durationSlots"] else JsonNumber(x.durationSlots)
    }

    /** Whether its task is on the state (`S.taskById.has(i.taskId)`). */
    fun hasTask(s: PlanState, x: PlanSession): Boolean =
        (x.taskId as? JsonString)?.let { s.taskById.containsKey(it.value) } ?: false

    fun taskOf(s: PlanState, x: PlanSession): PlanTask? =
        (x.taskId as? JsonString)?.let { s.taskById[it.value] }

    /** `cmpStr(a.instanceId, b.instanceId)` (LDG-2). */
    fun byId(a: JsonValue?, b: JsonValue?): Int =
        if (PageValues.less(a, b)) -1 else if (PageValues.less(b, a)) 1 else 0
}

/**
 * LDB-1 to LDB-3 `prepareStateWithHold` (`engine.js:1798-1842`): a fresh state for the input with
 * no stored sessions, on which the held sessions are put back where they are, pinned ones first,
 * then by start and id. A held session that overlaps time no longer free (from the plannable slot
 * on, unless it is over) or that no waking day holds is let go; a pinned or started one is kept
 * locked; any other is placed as a movable task session again, with its task made from its snapshot
 * (LDB-2). Then recovery is reserved on every day, and a day left short of its downtime lets go of
 * its movable sessions, lowest tier and latest first, until it is not (LDB-3).
 */
object HoldPreparation {
    private const val SAFETY = 20

    fun prepare(input: JsonValue?, hold: List<JsonObject>): HeldPlan {
        val inp =
            JsonObject(
                LinkedHashMap((input as JsonObject).members).also {
                    it["existingSchedule"] = JsonArray.EMPTY
                }
            )
        val s = PlanPreparation.state(inp)
        val displaced = ArrayList<JsonObject>()
        val goals = GoalPlans.info(member(input, "goals"), s.now)
        val ordered =
            hold.sortedWith { a, b ->
                val la = if (truthy(a["isUserLocked"])) 1 else 0
                val lb = if (truthy(b["isUserLocked"])) 1 else 0
                val d = number(a["startEpoch"]) - number(b["startEpoch"])
                when {
                    lb != la -> lb - la
                    truthy(d) -> PageValues.byDifference(d)
                    else -> HeldSessions.byId(a["instanceId"], b["instanceId"])
                }
            }
        val tasks = ArrayList(s.tasks)
        for (inst in ordered) {
            val start = number(inst["startEpoch"])
            val end = number(inst["endEpoch"])
            if (end <= s.slots.startEpoch || start >= s.slots.endEpoch) continue
            val clash = PlanPreparation.clash(s, start, end)
            val cycle = PlanPreparation.cycleAt(s, maxOf(start, s.now))
            if ((clash != null && end > s.now) || cycle < 0) {
                displaced += inst
                continue
            }
            val pinned = inst["isUserLocked"]
            val locked = truthy(pinned) || start <= s.now
            val copy = LinkedHashMap(inst.members)
            copy["bwcId"] = JsonString(s.cycles[cycle].cycle.id)
            copy["kept"] = if (truthy(pinned)) pinned!! else JsonBool.of(start <= s.now)
            val source = inst["source"]
            if (!locked && truthy(source)) {
                val made = SourceTask.make(source)
                made["id"] = PageValues.or(inst["taskId"], member(source, "id"))
                val t = made.planTask(s, goals)
                t.isPlaced = true
                copy["taskId"] = JsonString(t.id)
                tasks += t
                s.tasks = tasks.toList()
            }
            val session =
                PlanPreparation.keptSession(JsonObject(copy), s.cycles[cycle].cycle.id, locked)
            s.occupy(session, cycle, if (locked) SlotCode.LOCKED else SlotCode.TASK)
        }
        for (c in s.cycles) PlanPreparation.reserveRecovery(s, c)
        protectRecovery(s, hold, displaced)
        return HeldPlan(s, displaced)
    }

    /**
     * LDB-3: in each day that did not start before the plan and is not over, while its recovery is
     * below the smaller of the need and its free slots plus its movable sessions' stored lengths,
     * its movable session of the lowest tier (then the latest, then the smaller id) is taken off
     * (and let go as it was held), the day's recovery freed and reserved again; at most 20 times.
     */
    private fun protectRecovery(
        s: PlanState,
        hold: List<JsonObject>,
        displaced: MutableList<JsonObject>,
    ) {
        val need = ceil(number(s.prefs["minDailyRecoveryMinutes"]) / PlanState.SLOT_MINUTES)
        for (c in s.cycles) {
            if (c.cycle.truncatedStart || c.cycle.endSlot <= s.nowSlot) continue
            var guard = 0
            while (guard++ < SAFETY) {
                val movable =
                    c.sessions.filter {
                        !HeldSessions.kept(it) && !HeldSessions.locked(it) && it.startEpoch > s.now
                    }
                val held = movable.fold(0.0) { n, x -> n + x.durationSlots }
                if (c.recoverySlots >= minOf(need, c.freeSlots + held) || movable.isEmpty()) break
                val victim =
                    movable
                        .sortedWith { a, b ->
                            val d = number(HeldSessions.tier(a)) - number(HeldSessions.tier(b))
                            when {
                                truthy(d) -> PageValues.byDifference(d)
                                truthy(b.startEpoch - a.startEpoch) ->
                                    PageValues.byDifference(b.startEpoch - a.startEpoch)
                                else -> HeldSessions.byId(a.instanceId, b.instanceId)
                            }
                        }[0]
                PlanRemoval.remove(s, victim)
                displaced +=
                    hold.firstOrNull { PageValues.same(it["instanceId"], victim.instanceId) }
                        ?: victim.stored!!
                var i = c.cycle.startSlot
                while (i < c.cycle.endSlot) {
                    val k = i.toInt()
                    if (s.energy.type[k] == SlotCode.RECOVERY.code)
                        s.energy.type[k] = SlotCode.FREE.code
                    i += 1
                }
                PlanPreparation.reserveRecovery(s, c)
            }
        }
    }
}
