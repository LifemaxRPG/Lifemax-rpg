package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.lifedirector.PageValues.member
import com.lifemax.rpg.core.lifedirector.PageValues.text
import com.lifemax.rpg.core.lifedirector.PageValues.truthy
import com.lifemax.rpg.core.lifedirector.ScheduleJson.num
import com.lifemax.rpg.core.lifedirector.ScheduleJson.obj
import com.lifemax.rpg.core.lifedirector.ScheduleJson.str
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonValue

/** What a caller may watch of a plan as it is made (the recorder's stages): nothing by default. */
interface ScheduleWatch {
    /** LDX-3: a repair target's turn starts. */
    fun target(s: PlanState, t: PlanTask, log: RepairLog) {}

    /** The repair is over. */
    fun repaired(s: PlanState, log: RepairLog, records: Map<String, PlacementRecord>) {}

    /** The fill pass is over (or was not needed). */
    fun filled(s: PlanState, records: Map<String, PlacementRecord>) {}
}

/**
 * LDX, LDO: `generateSchedule` (`engine.js:1205-1307`) from end to end: the construction
 * ([PlanConstruction], WP-LD4), the repair and the fill pass ([LocalRepair]), the rejections, the
 * explanations and the output with its validation, written as the page's object (the stored
 * schedule).
 */
object PlanSchedule {
    const val ENGINE_VERSION = "1.1.0"
    const val SCHEMA_VERSION = 1
    private const val URGENT_TIER = 3
    private const val SLOT_MINUTES = 15.0
    private const val NANOS_PER_MS = 1e6
    private const val TENTHS = 10.0
    private const val ON_TRACK = "ON_TRACK"

    /** The plan for [input]; [watch] sees the repair's stages. */
    fun generate(input: JsonValue?, watch: ScheduleWatch? = null): JsonValue {
        val t0 = System.nanoTime()
        val c = PlanConstruction.construct(input)
        val s = c.state
        val records = LinkedHashMap<String, PlacementRecord>()
        for (p in c.placements) records[LocalRepair.key(p.session)] =
            PlacementRecord.of(p.selection)
        val targets = c.active.filter { !it.isPlaced && it.task.tier >= URGENT_TIER }
        val log =
            LocalRepair.repair(
                s,
                targets,
                records,
                watch = watch?.let { w -> { t, l -> w.target(s, t, l) } },
            )
        watch?.repaired(s, log, records)
        if (log.changed) LocalRepair.fill(s, c.active, records)
        watch?.filled(s, records)
        val rejected = ArrayList<JsonValue>()
        c.demand.candidates.rejected.mapTo(rejected, ScheduleJson::questRejection)
        for (t in c.tasks) if (!t.isPlaced) rejected += ScheduleJson.rejection(Rejections.of(s, t))
        for (d in c.prepared.displaced) rejected += ScheduleJson.displaced(d.session)
        val planned =
            s.sessions.sortedWith { a, b ->
                when {
                    a.startEpoch != b.startEpoch ->
                        PageValues.byDifference(a.startEpoch - b.startEpoch)
                    PageValues.less(a.instanceId, b.instanceId) -> -1
                    PageValues.less(b.instanceId, a.instanceId) -> 1
                    else -> 0
                }
            }
        for (x in planned) if (x.explanation == null)
            x.explanation = Explanations.explain(s, x, records[LocalRepair.key(x)])
        return assemble(s, input, c, planned, rejected, log, t0)
    }

    /** LDO-8 `assembleOutput` (`engine.js:1504-1527`). */
    private fun assemble(
        s: PlanState,
        input: JsonValue?,
        c: Construction,
        planned: List<PlanSession>,
        rejected: List<JsonValue>,
        log: RepairLog,
        t0: Long,
    ): JsonValue {
        val plannedJson = planned.map(ScheduleJson::session)
        val recovery = PlanSegments.recoveryBlocks(s)
        val validation =
            ScheduleValidation.validate(
                input,
                JsonArray(plannedJson),
                JsonArray(rejected),
                null,
                s.fair,
            )
        val capacity = PlanSegments.capacity(s)
        val allocation =
            allocation(
                s,
                c.demand.expansion.plans,
                planned.map { it.goalLinks to it.durationSlots },
            )
        val valid = (validation as JsonObject)["isValid"] == JsonBool.TRUE
        val warned = ((validation["warnings"] as JsonArray).items).isNotEmpty()
        val status =
            if (!valid) "INVARIANT_VIOLATION"
            else if (warned) "CONSTRAINT_WARNING" else "VALID_SCHEDULE"
        val hash = InputHash.of(input)
        val segments = PlanSegments.segments(s)
        val coverage = coverage(c, planned)
        val elapsed = PageValues.round((System.nanoTime() - t0) / NANOS_PER_MS * TENTHS) / TENTHS
        val cand = c.demand.candidates
        val expansion = c.demand.expansion
        return obj {
            this["engineVersion"] = str(ENGINE_VERSION)
            this["schemaVersion"] = num(SCHEMA_VERSION)
            this["status"] = str(status)
            this["generatedAtEpoch"] = num(s.now)
            this["computeTimeMs"] = num(elapsed)
            this["timezone"] = s.plan.zoneValue
            this["horizonStartEpoch"] = num(s.slots.startEpoch)
            this["horizonEndEpoch"] = num(s.slots.endEpoch)
            this["inputHash"] = str(hash)
            this["scheduledInstances"] = JsonArray(plannedJson)
            this["historyInstances"] = JsonArray(c.prepared.history)
            this["recoveryBlocks"] = JsonArray(recovery)
            this["passiveReminders"] =
                JsonArray(cand.passiveReminders.map(ScheduleJson::passiveReminder))
            this["unscheduledDiagnostics"] = JsonArray(rejected)
            this["clarificationPrompts"] =
                JsonArray(expansion.clarificationPrompts.map(ScheduleJson::prompt))
            this["trajectoryWarnings"] =
                JsonArray(expansion.trajectoryWarnings.map(ScheduleJson::trajectory))
            this["warnings"] = JsonArray(s.plan.warnings.map(ScheduleJson::warning))
            this["validation"] = validation
            this["capacity"] = JsonArray(capacity)
            this["segments"] = JsonArray(segments)
            this["goalAllocation"] = JsonArray(allocation)
            this["questCoverage"] = JsonArray(coverage)
            this["displacedLockedIds"] =
                JsonArray(
                    c.prepared.displaced.map {
                        ScheduleJson.orNull(member(it.session, "instanceId"))
                    }
                )
            this["repair"] = repairJson(log)
            this["iterations"] = num(s.iterations)
        }
    }

    /** LDX-3's log as the output reports it. */
    fun repairJson(log: RepairLog): JsonValue = obj {
        this["attempts"] = num(log.attempts)
        this["successes"] = num(log.successes)
        this["rollbacks"] = num(log.rollbacks)
        this["moves"] =
            JsonArray(
                log.moves.map { m ->
                    obj {
                        this["type"] = str(m.type)
                        this["taskId"] = str(m.taskId)
                        when (m.type) {
                            "COMPRESSION" -> this["bwcId"] = m.cycleId?.let(::str)
                            "SWAP" -> this["relocated"] = m.other
                            else -> this["shed"] = m.other
                        }
                    }
                }
            )
    }

    /**
     * LDO-6: one row per goal with a plan, in the order of goal ids: its title, priority, target
     * share, status (the first plan's, replaced by any plan's status that is not on track), the
     * projection of its first plan and each plan's template numbers; then the minutes and sessions
     * of every planned session linked to it ([planned]: each session's goal links and length in
     * slots), and its share of all such minutes (of 1 when none).
     */
    internal fun allocation(
        s: PlanState,
        plans: List<SessionPlan>,
        planned: List<Pair<List<JsonValue>, Double>>,
    ): List<JsonValue> {
        class Row(val plan: SessionPlan) {
            var status = plan.status
            var minutes = 0.0
            var sessions = 0
            val templates = ArrayList<JsonValue>()
        }
        val rows = LinkedHashMap<String, Row>()
        for (p in plans) {
            val row = rows.getOrPut(p.goal.id) { Row(p) }
            row.templates += obj {
                this["templateId"] = p.template["templateId"]
                this["title"] = p.template["title"]
                this["med"] = p.fMED
                this["base"] = num(p.base)
                this["stretch"] = num(p.stretch)
                this["minutes"] = num(p.dOpt)
            }
            if (p.status != ON_TRACK) row.status = p.status
        }
        for ((links, slots) in planned) for (gid in links) rows[text(gid)]?.let {
            it.minutes += slots * SLOT_MINUTES
            it.sessions++
        }
        val total =
            rows.values.fold(0.0) { m, r -> m + r.minutes }.let { if (truthy(it)) it else 1.0 }
        return rows.entries
            .sortedWith { a, b -> a.key.compareTo(b.key) }
            .map { (id, r) ->
                obj {
                    this["goalId"] = str(id)
                    this["title"] = str(r.plan.goal.title)
                    this["priority"] = num(r.plan.goal.priority)
                    this["targetShare"] = num(s.fair.share[id]?.takeIf(PageValues::truthy) ?: 0.0)
                    this["scheduledMin"] = num(r.minutes)
                    this["plannedSessions"] = num(r.sessions)
                    this["status"] = str(r.status)
                    this["projectionEpoch"] = r.plan.projection?.let(::num) ?: JsonNull
                    this["templates"] = JsonArray(r.templates)
                    this["actualShare"] = num(r.minutes / total)
                }
            }
    }

    /**
     * LDO-7, LDQ-4 `questCoverage` on the planned sessions: a session's quest ids are its links' (a
     * stored session's as stored: no list throws, once an active quest asks).
     */
    private fun coverage(c: Construction, planned: List<PlanSession>): List<JsonValue> {
        val cand = c.demand.candidates
        if (cand.quests.none { it.active }) return emptyList()
        val ids =
            planned.map { x ->
                val links: List<JsonValue> =
                    if (x.stored != null)
                        (x.stored["linkedQuestRequirementIds"] as? JsonArray)?.items
                            ?: throw PageTypeError(
                                "linkedQuestRequirementIds.some is not a function"
                            )
                    else x.questLinks
                links.map {
                    if (!PageValues.present(it))
                        throw PageTypeError("Cannot read properties of null")
                    member(it, "questId")
                }
            }
        return PlayerQuests.coverage(cand.quests, ids) { id ->
                cand.questStatus[id]?.takeIf(PageValues::truthy)
            }
            .map(ScheduleJson::coverage)
    }
}
