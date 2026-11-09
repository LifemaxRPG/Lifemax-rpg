package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.lifedirector.ScheduleJson.num
import com.lifemax.rpg.core.lifedirector.ScheduleJson.obj
import com.lifemax.rpg.core.lifedirector.ScheduleJson.str
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonValue

/** LDO-3 to LDO-5: the plan's time line as the page reports it, read off the final slot codes. */
object PlanSegments {
    private const val SLOT_MINUTES = 15
    private val CODES = SlotCode.entries.associateBy { it.code }

    /**
     * LDO-3 `segments` (`engine.js:1468-1482`): the plan cut into runs of one slot code and one
     * painting block; a run of a session's slots (task or kept) also ends where the session ends,
     * the session being the first on the plan whose time holds the run's first slot. Each run has
     * its times, the code's long name, a title (the session's; `Free time` and `Protected downtime`
     * for free and recovery slots; else the block's, or the code's name with no block), a kind
     * (`TASK`, the block's, `RECOVERY` or `FREE`), the session's id and the block's reference.
     * Members the session or block lacks are left out.
     */
    fun segments(s: PlanState): List<JsonValue> {
        val out = ArrayList<JsonValue>()
        val type = s.energy.type
        val tag = s.energy.tag
        val n = s.size
        var i = 0
        while (i < n) {
            val code = type[i]
            var j = i + 1
            val at = s.epochOf(i.toDouble())
            val owner =
                if (code == SlotCode.TASK.code || code == SlotCode.LOCKED.code)
                    s.sessions.firstOrNull { at >= it.startEpoch && at < it.endEpoch }
                else null
            while (
                j < n &&
                    type[j] == code &&
                    tag[j] == tag[i] &&
                    (owner == null || s.epochOf(j.toDouble()) < owner.endEpoch)
            ) j++
            val block = if (tag[i] >= 0) s.plan.grid.blocks[tag[i]] else null
            val meta = block?.meta
            val name = CODES.getValue(code).pageName
            out += obj {
                this["startEpoch"] = num(at)
                this["endEpoch"] = num(s.epochOf(j.toDouble()))
                this["code"] = str(name)
                this["title"] =
                    when {
                        owner != null -> titleOf(owner)
                        code == SlotCode.FREE.code -> str("Free time")
                        code == SlotCode.RECOVERY.code -> str("Protected downtime")
                        block != null -> meta?.title
                        else -> str(name)
                    }
                this["kind"] =
                    when {
                        owner != null -> str("TASK")
                        block != null -> meta?.kind
                        code == SlotCode.RECOVERY.code -> str("RECOVERY")
                        else -> str("FREE")
                    }
                this["instanceId"] = owner?.let { it.instanceId } ?: JsonNull
                this["refId"] = meta?.refId?.let(::str) ?: JsonNull
            }
            i = j
        }
        return out
    }

    private fun titleOf(x: PlanSession): JsonValue? =
        x.stored?.let { PageValues.member(it, "title") } ?: x.commitment?.task?.task?.title

    /**
     * LDO-4 `capacityByDay` (`engine.js:1483-1495`): for each plan day, minutes by what fills its
     * slots: free, sessions (task or kept), recovery, hard time (commitments, commutes,
     * emergencies, personal), sleep, and the rest as upkeep; `otherMin` is always 0; usable is free
     * plus sessions plus recovery.
     */
    fun capacity(s: PlanState): List<JsonValue> {
        val type = s.energy.type
        return s.plan.days.map { d ->
            val a = ((d.startEpoch - s.slots.startEpoch) / SlotGrid.SLOT_SECONDS).toInt()
            var free = 0
            var task = 0
            var recovery = 0
            var hard = 0
            var sleep = 0
            var maint = 0
            for (i in a until a + d.slots.toInt()) {
                when (CODES.getValue(type[i])) {
                    SlotCode.FREE -> free += SLOT_MINUTES
                    SlotCode.TASK,
                    SlotCode.LOCKED -> task += SLOT_MINUTES
                    SlotCode.RECOVERY -> recovery += SLOT_MINUTES
                    SlotCode.HARD,
                    SlotCode.COMMUTE,
                    SlotCode.EMERGENCY,
                    SlotCode.PERSONAL -> hard += SLOT_MINUTES
                    SlotCode.SLEEP -> sleep += SLOT_MINUTES
                    else -> maint += SLOT_MINUTES
                }
            }
            obj {
                this["dateKey"] = str(d.key)
                this["dow"] = num(d.date.weekday)
                this["freeMin"] = num(free)
                this["taskMin"] = num(task)
                this["recoveryMin"] = num(recovery)
                this["hardMin"] = num(hard)
                this["sleepMin"] = num(sleep)
                this["maintMin"] = num(maint)
                this["otherMin"] = num(0)
                this["usableMin"] = num(free + task + recovery)
            }
        }
    }

    /**
     * LDO-5 `recoveryBlocks` (`engine.js:887-896`): each run of recovery slots, with the id of the
     * waking day its first slot belongs to (null for none).
     */
    fun recoveryBlocks(s: PlanState): List<JsonValue> {
        val out = ArrayList<JsonValue>()
        val type = s.energy.type
        val owner = s.plan.grid.wakeCycle
        var i = 0
        while (i < s.size) {
            if (type[i] != SlotCode.RECOVERY.code) {
                i++
                continue
            }
            var j = i
            while (j < s.size && type[j] == SlotCode.RECOVERY.code) j++
            val day = owner[i]
            out += obj {
                this["startEpoch"] = num(s.epochOf(i.toDouble()))
                this["endEpoch"] = num(s.epochOf(j.toDouble()))
                this["recoveryType"] = str("PROTECTED_DOWNTIME")
                this["bwcId"] = if (day >= 0) str(s.cycles[day].cycle.id) else JsonNull
            }
            i = j
        }
        return out
    }
}
