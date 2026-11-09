package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.lifedirector.PageValues.text
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/**
 * LDX-2: everything the repair may change and a roll-back gives back (`snapshot`,
 * `engine.js:1119-1129`): the slot arrays (codes, owners, intensities, drains, free runs), each
 * waking day's sessions, key counts, goal minutes and high count, the session list, the fairness
 * ledger, the spacing ledger, each task's placed flag and cover, and how many quest links each
 * session had. Not kept: the iteration count, the tasks' `displacedBy`, the recovery counts and the
 * base energies (the repair never changes the last two).
 */
class PlanSnapshot
private constructor(
    private val type: IntArray,
    private val owner: IntArray,
    private val physical: IntArray,
    private val cognitive: IntArray,
    private val drainP: FloatArray,
    private val drainC: FloatArray,
    private val runAhead: ShortArray,
    private val runBack: ShortArray,
    private val days: List<DayCopy>,
    private val sessions: List<PlanSession>,
    private val goalMinutes: Map<String, Double>,
    private val total: Double,
    private val share: Map<String, Double>,
    private val spacing: List<Pair<JsonValue?, List<Double>>>,
    private val placed: BooleanArray,
    private val covered: List<JsonValue?>,
    private val links: IntArray,
) {
    private class DayCopy(
        val sessions: List<PlanSession>,
        val perKey: Map<String, Double>,
        val goalMinutes: Double,
        val highCount: Int,
    )

    /**
     * LDX-2 `restore`: the state as it was taken. The session index by id is built again from the
     * list (the last of a repeated id wins); a session's quest links are cut back to their number
     * then (links the repair added are dropped; none is put back).
     */
    fun restore(s: PlanState) {
        val e = s.energy
        type.copyInto(e.type)
        owner.copyInto(s.owner)
        physical.copyInto(e.taskPhysical)
        cognitive.copyInto(e.taskCognitive)
        drainP.copyInto(e.drainPhysical)
        drainC.copyInto(e.drainCognitive)
        runAhead.copyInto(e.runAhead)
        runBack.copyInto(e.runBack)
        s.cycles.forEachIndexed { i, c ->
            val d = days[i]
            c.sessions.clear()
            c.sessions += d.sessions
            c.perKey.clear()
            c.perKey.putAll(d.perKey)
            c.goalMinutes = d.goalMinutes
            c.highCount = d.highCount
        }
        s.sessions.clear()
        s.sessions += sessions
        s.sessionById.clear()
        for (x in s.sessions) s.sessionById[x.instanceId] = x
        s.fair.goalMinutes.clear()
        s.fair.goalMinutes.putAll(goalMinutes)
        s.fair.total = total
        s.fair.share.clear()
        s.fair.share.putAll(share)
        s.spacing.clear()
        for ((k, v) in spacing) s.spacing[k] = v.toMutableList()
        s.tasks.forEachIndexed { i, t ->
            t.isPlaced = placed[i]
            t.coveredBy = covered[i]
        }
        s.sessions.forEachIndexed { i, x ->
            while (x.questLinks.size > links[i]) x.questLinks.removeAt(x.questLinks.size - 1)
        }
    }

    companion object {
        /** LDX-2 `snapshot` of [s]. */
        fun of(s: PlanState): PlanSnapshot {
            val e = s.energy
            return PlanSnapshot(
                e.type.copyOf(),
                s.owner.copyOf(),
                e.taskPhysical.copyOf(),
                e.taskCognitive.copyOf(),
                e.drainPhysical.copyOf(),
                e.drainCognitive.copyOf(),
                e.runAhead.copyOf(),
                e.runBack.copyOf(),
                s.cycles.map {
                    DayCopy(
                        it.sessions.toList(),
                        LinkedHashMap(it.perKey),
                        it.goalMinutes,
                        it.highCount,
                    )
                },
                s.sessions.toList(),
                LinkedHashMap(s.fair.goalMinutes),
                s.fair.total,
                LinkedHashMap(s.fair.share),
                s.spacing.map { (k, v) -> k to v.toList() },
                BooleanArray(s.tasks.size) { s.tasks[it].isPlaced },
                s.tasks.map { it.coveredBy },
                IntArray(s.sessions.size) { linkCount(s.sessions[it]) },
            )
        }

        /**
         * `linkedQuestRequirementIds.length`: a kept session's stored list (a text counts its
         * units; no list at all stops the page with a `TypeError`), a committed one's links.
         */
        fun linkCount(x: PlanSession): Int {
            val stored = x.stored ?: return x.questLinks.size
            return when (val v = stored["linkedQuestRequirementIds"]) {
                null,
                JsonNull ->
                    throw PageTypeError("Cannot read properties of undefined (reading 'length')")
                is JsonArray -> v.items.size
                is JsonString -> v.value.length
                else -> Int.MAX_VALUE
            }
        }
    }
}

/** LDX-1: taking a session off the plan. */
object PlanRemoval {
    /**
     * LDX-1 `removeInst` (`engine.js:1088-1105`): the session's slots (rounded out to whole slots,
     * cut to the grid) become free with no owner or intensity; it leaves the session list (by
     * identity; a session not in it takes the last one with it, as `splice(-1, 1)` does) and the
     * index by id; its waking day (the first with its day id) loses it, one of its key, its minutes
     * and, if high, one of its high count, and the day's drain is worked out again; the spacing
     * ledger loses its start (the first equal one); the fairness ledger its minutes; its task is no
     * longer placed, and every task it covered is uncovered.
     */
    fun remove(s: PlanState, session: PlanSession) {
        val size = s.size.toDouble()
        val a = PageValues.clamp(s.slots.slotAt(session.startEpoch), 0.0, size)
        val b = PageValues.clamp(s.slots.slotAtOrAfter(session.endEpoch), 0.0, size)
        var i = a
        while (i < b) {
            val k = i.toInt()
            s.energy.type[k] = SlotCode.FREE.code
            s.owner[k] = -1
            s.energy.taskPhysical[k] = 0
            s.energy.taskCognitive[k] = 0
            i += 1
        }
        spliceOut(s.sessions, session)
        s.sessionById.remove(session.instanceId)
        val minutes = (b - a) * PlanState.SLOT_MINUTES
        val day = s.cycles.firstOrNull { it.cycle.id == session.cycleId }
        if (day != null) {
            spliceOut(day.sessions, session)
            val key = text(session.spacingKey)
            day.perKey[key] = (day.perKey[key] ?: Double.NaN) - 1
            day.goalMinutes -= minutes
            if (session.high) day.highCount--
            s.recompute(day)
        }
        s.spacing[session.spacingKey]?.let { starts ->
            val at = starts.indexOfFirst { it == session.startEpoch }
            if (at >= 0) starts.removeAt(at)
        }
        for (gid in session.goalLinks) {
            val k = text(gid)
            s.fair.goalMinutes[k] = (s.fair.goalMinutes[k] ?: Double.NaN) - minutes
        }
        if (session.goalLinks.isNotEmpty()) s.fair.total -= minutes
        (session.taskId as? JsonString)?.let { s.taskById[it.value]?.isPlaced = false }
        for (u in s.tasks) if (
            u.coveredBy != null && PageValues.same(u.coveredBy, session.instanceId)
        ) {
            u.isPlaced = false
            u.coveredBy = null
        }
    }

    /** `list.splice(list.indexOf(x), 1)` by identity. */
    private fun spliceOut(list: MutableList<PlanSession>, x: PlanSession) {
        val at = list.indexOfFirst { it === x }
        if (at >= 0) list.removeAt(at) else if (list.isNotEmpty()) list.removeAt(list.size - 1)
    }
}
