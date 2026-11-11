package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.lifedirector.PageValues.member
import com.lifemax.rpg.core.lifedirector.PageValues.number
import com.lifemax.rpg.core.lifedirector.PageValues.text
import com.lifemax.rpg.core.lifedirector.PageValues.truthy
import com.lifemax.rpg.core.lifedirector.ScheduleJson.num
import com.lifemax.rpg.core.lifedirector.ScheduleJson.obj
import com.lifemax.rpg.core.lifedirector.ScheduleJson.str
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/**
 * LDZ-1 to LDZ-5: the rescheduled plan and its change record (`engine.js:1761-1789`): the state's
 * sessions (held ones as held, without the hold mark) and the held sessions that ended before the
 * plan, in start then id order, each with its explanation (given again to a session placed now);
 * the output `assembleOutput` writes (LDO-8) with the current plan's reminders, prompts, warnings
 * and the rejections it still has; and what changed.
 */
object RescheduleOutput {
    private const val NANOS_PER_MS = 1e6
    private const val TENTHS = 10.0
    private val REASONS =
        mapOf(
            "MISSED" to "MISSED_TASK_REPAIR",
            "SKIPPED" to "USER_SKIP",
            "RESIDUAL" to "PARTIAL_COMPLETION",
            "MOVE" to "USER_MANUAL_MOVE",
            "NEW_COMMITMENT" to "NEW_COMMITMENT_ADDED",
            "BLOCK_HOURS" to "TIME_BLOCKED",
            "BLACKOUT_DAY" to "DAY_BLACKOUT",
            "SICK" to "SICK_MODE_ACTIVATED",
            "QUEST_CHANGED" to "QUEST_MODIFIED",
        )

    /** Everything the reschedule worked out, for the writer. */
    class Parts(
        val p: TriggerPlan,
        val probe: HeldPlan,
        val current: JsonValue,
        val prevPlanned: List<StoredSession>,
        val carry: List<StoredSession>,
        val rec: RescheduleRecords,
        val placed: List<Reschedule.Placed>,
        val t0: Long,
    )

    /** One planned session: on the state ([session]) or carried as stored ([stored]). */
    private class Row(val session: PlanSession?, val stored: JsonObject?) {
        val start: Double =
            session?.stored?.let { number(it["startEpoch"]) }
                ?: session?.startEpoch
                ?: number(stored!!["startEpoch"])
        val id: JsonValue? = session?.instanceId ?: stored!!["instanceId"]
    }

    fun write(x: Parts): RescheduleResult {
        val p = x.p
        val s = x.probe.state
        val inPlan = s.sessions.map { it.instanceId }.toSet()
        val rows =
            (s.sessions.map { Row(it, null) } +
                    x.carry
                        .filter { it["instanceId"] !in inPlan && it["taskId"] !in p.gone }
                        .map { Row(null, it.json()) })
                .sortedWith { a, b ->
                    val d = a.start - b.start
                    if (truthy(d)) PageValues.byDifference(d) else HeldSessions.byId(a.id, b.id)
                }
        val planned = rows.map { json(s, it, x.rec) }
        val goalPlans =
            GoalPlans.expand(
                PageValues.or(member(p.input, "goals"), JsonArray.EMPTY),
                s.prefs["intensityLevel"],
                s.now,
            )
        val quests =
            PlanState.iterate(
                    PageValues.or(p.input["activeQuests"], JsonArray.EMPTY),
                    "activeQuests",
                )
                .map(PlayerQuests::of)
        val old =
            member(x.current, "unscheduledDiagnostics").takeIf(::truthy)?.let {
                (it as JsonArray).items
            } ?: emptyList()
        val rejected =
            p.rejected +
                old.filter { r ->
                    planned.none { PageValues.same(member(it, "taskId"), member(r, "taskId")) }
                }
        val elapsed = PageValues.round((System.nanoTime() - x.t0) / NANOS_PER_MS * TENTHS) / TENTHS
        val out = assemble(x, planned, rejected, goalPlans, quests, elapsed)
        return RescheduleResult(out, p.input, changeRecord(x, planned, elapsed), null)
    }

    /**
     * A planned session as the page's object, its explanation given (again) where the page gives
     * it.
     */
    private fun json(s: PlanState, row: Row, rec: RescheduleRecords): JsonValue {
        val x = row.session
        if (x == null) {
            val st = row.stored!!
            if (truthy(st["explanation"])) return st
            if (!truthy(st["isUserLocked"]))
                throw UnreadableSource("a carried session without an explanation")
            return JsonObject(LinkedHashMap(st.members).also { it["explanation"] = locked(st) })
        }
        val record = rec.records[LocalRepair.key(x)]
        val stored = x.stored
        if (stored != null) {
            val m = LinkedHashMap(stored.members)
            m.remove("_hold")
            if (!truthy(m["explanation"]) || record != null)
                m["explanation"] =
                    if (HeldSessions.kept(x) || HeldSessions.locked(x))
                        Explanations.explain(s, x, record)
                    else Explanations.explain(s, asPlaced(s, x, stored), record)
            return JsonObject(m)
        }
        if (!truthy(x.explanation) || record != null)
            x.explanation = Explanations.explain(s, x, record)
        val o = ScheduleJson.session(x) as JsonObject
        return if (x in rec.keptFalse)
            JsonObject(LinkedHashMap(o.members).also { it["kept"] = JsonBool.FALSE })
        else o
    }

    /**
     * LDZ-3: a held movable session explained as the page explains it (a session placed now can
     * share its id, LD49): scored as its task on the state (`taskById`, the last task of that id)
     * with the session's own length, links, goal title, compression and tier.
     */
    private fun asPlaced(s: PlanState, x: PlanSession, st: JsonObject): PlanSession {
        val t =
            HeldSessions.taskOf(s, x) ?: throw UnreadableSource("a held session whose task is gone")
        val task =
            PlanTask(t.task.copy(goalTitle = (st["goalTitle"] as? JsonString)?.value), t.prepared)
        return PlanSession(
            x.instanceId,
            x.taskId,
            x.templateId,
            x.tag,
            x.startEpoch,
            x.endEpoch,
            x.physical,
            x.cognitive,
            x.spacingKey,
            x.goalLinks,
            PlanState.iterate(st["linkedQuestRequirementIds"], "links").toMutableList(),
            x.durationSlots,
            x.cycleId,
            kept = false,
            commitment =
                Commitment(
                    task,
                    t.task.goalId,
                    HeldSessions.minSlots(x),
                    number(st["optDurationSlots"]),
                    number(st["tier"]).toInt(),
                    truthy(st["compressed"]),
                    st["origStartEpoch"],
                ),
        )
    }

    private fun locked(st: JsonObject): JsonValue = obj {
        this["tier"] = st["tier"]
        this["summary"] =
            str("You pinned this time, so Life Director never moves it automatically.")
        this["factors"] = JsonArray.EMPTY
        this["locked"] = JsonBool.TRUE
    }

    /**
     * LDZ-2 `assembleOutput` on the held state (no repair log; no fairness shares, LD48), validated
     * as LDZ-4.
     */
    private fun assemble(
        x: Parts,
        planned: List<JsonValue>,
        rejected: List<JsonValue>,
        goalPlans: GoalExpansion,
        quests: List<PlayerQuest>,
        elapsed: Double,
    ): JsonValue {
        val s = x.probe.state
        val input = x.p.input
        val recovery = PlanSegments.recoveryBlocks(s)
        val validation =
            ScheduleValidation.validate(
                input,
                JsonArray(planned),
                JsonArray(rejected),
                null,
                s.fair,
            ) as JsonObject
        val capacity = PlanSegments.capacity(s)
        val links =
            planned.map {
                PlanState.iterate(member(it, "linkedGoalIds"), "linkedGoalIds") to
                    number(member(it, "durationSlots"))
            }
        val allocation = PlanSchedule.allocation(s, goalPlans.plans, links)
        val valid = validation["isValid"] == JsonBool.TRUE
        val warned = (validation["warnings"] as JsonArray).items.isNotEmpty()
        val status =
            if (!valid) "INVARIANT_VIOLATION"
            else if (warned) "CONSTRAINT_WARNING" else "VALID_SCHEDULE"
        val hash = InputHash.of(input)
        val segments = PlanSegments.segments(s)
        val coverage =
            if (quests.none { it.active }) emptyList()
            else
                PlayerQuests.coverage(
                        quests,
                        planned.map { i ->
                            val l =
                                member(i, "linkedQuestRequirementIds") as? JsonArray
                                    ?: throw PageTypeError(
                                        "linkedQuestRequirementIds.some is not a function"
                                    )
                            l.items.map { member(it, "questId") }
                        },
                    ) {
                        null
                    }
                    .map(ScheduleJson::coverage)
        fun passed(key: String) = member(x.current, key).takeIf(::truthy) ?: JsonArray.EMPTY
        return obj {
            this["engineVersion"] = str(PlanSchedule.ENGINE_VERSION)
            this["schemaVersion"] = num(PlanSchedule.SCHEMA_VERSION)
            this["status"] = str(status)
            this["generatedAtEpoch"] = num(s.now)
            this["computeTimeMs"] = num(elapsed)
            this["timezone"] = s.plan.zoneValue
            this["horizonStartEpoch"] = num(s.slots.startEpoch)
            this["horizonEndEpoch"] = num(s.slots.endEpoch)
            this["inputHash"] = str(hash)
            this["scheduledInstances"] = JsonArray(planned)
            this["historyInstances"] = JsonArray(x.p.history)
            this["recoveryBlocks"] = JsonArray(recovery)
            this["passiveReminders"] = passed("passiveReminders")
            this["unscheduledDiagnostics"] = JsonArray(rejected)
            this["clarificationPrompts"] = passed("clarificationPrompts")
            this["trajectoryWarnings"] = passed("trajectoryWarnings")
            this["warnings"] = JsonArray(s.plan.warnings.map(ScheduleJson::warning))
            this["validation"] = validation
            this["capacity"] = JsonArray(capacity)
            this["segments"] = JsonArray(segments)
            this["goalAllocation"] = JsonArray(allocation)
            this["questCoverage"] = JsonArray(coverage)
            this["displacedLockedIds"] =
                JsonArray(x.probe.displaced.map { ScheduleJson.orNull(it["instanceId"]) })
            this["repair"] = JsonNull
            this["iterations"] = num(s.iterations)
        }
    }

    /**
     * LDZ-5: the change record: its id (a hash of the trigger, its session, the clock and the
     * number of actions), the clock, the trigger's reason (none for TIME_CHANGED), the actions, the
     * radius each re-placed task used, the sessions the actions name (once each, in order), and the
     * churn among the sessions that were still to come (other than the trigger's): those moved or
     * gone, not counting the ones the hold let go, and those only shortened.
     */
    private fun changeRecord(x: Parts, planned: List<JsonValue>, elapsed: Double): JsonValue {
        val p = x.p
        val trig = member(p.trigger, "instanceId")
        val future =
            x.prevPlanned.filter {
                number(it["startEpoch"]) > p.now && !PageValues.same(it["instanceId"], trig)
            }
        val excused = x.probe.displaced.map { it["instanceId"] }.toSet()
        val byId = HashMap<JsonValue?, JsonValue>()
        for (i in planned.asReversed()) byId[member(i, "instanceId")] = i
        val collateral =
            future.count { f ->
                if (f["instanceId"] in excused) return@count false
                val n = byId[f["instanceId"]]
                n == null || !PageValues.same(member(n, "startEpoch"), f["startEpoch"])
            }
        val compressed =
            future.count { f ->
                val n = byId[f["instanceId"]]
                n != null &&
                    PageValues.same(member(n, "startEpoch"), f["startEpoch"]) &&
                    !PageValues.same(member(n, "endEpoch"), f["endEpoch"])
            }
        val affected = LinkedHashSet<JsonValue>()
        for (a in p.actions) member(a, "instanceId")?.takeIf(::truthy)?.let(affected::add)
        val type = text(p.type)
        return obj {
            this["changeId"] =
                str(
                    EngineHash.stableId(
                        "chg",
                        type +
                            ":" +
                            text(PageValues.or(trig, "")) +
                            ":" +
                            text(p.nowValue) +
                            ":" +
                            p.actions.size,
                    )
                )
            this["timestampEpoch"] = p.nowValue
            this["triggerReason"] = REASONS[type]?.let(::str)
            this["actions"] = JsonArray(p.actions)
            this["radiusUsed"] =
                JsonArray(
                    x.placed.map { r ->
                        obj {
                            this["taskId"] = str(r.taskId)
                            this["title"] = r.title
                            this["radius"] = num(r.radius)
                            this["origInstanceId"] = r.origInstanceId
                        }
                    }
                )
            this["affectedInstanceIds"] = JsonArray(affected.toList())
            this["churn"] = obj {
                this["collateralMoved"] = num(collateral)
                this["compressed"] = num(compressed)
                this["futureInstances"] = num(future.size)
                this["ratio"] =
                    num(if (future.isNotEmpty()) collateral.toDouble() / future.size else 0.0)
            }
            this["computeTimeMs"] = num(elapsed)
        }
    }
}
