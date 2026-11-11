package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/**
 * Builds a page object member by member; a member set to null is left out (the page's undefined),
 * as `JSON.stringify` leaves it out.
 */
class PageObject {
    private val members = LinkedHashMap<String, JsonValue>()

    operator fun set(key: String, value: JsonValue?) {
        if (value != null) members[key] = value else members.remove(key)
    }

    fun done(): JsonObject = JsonObject(members)
}

/**
 * LDO-8: the plan's parts written as the page's objects (the stored schedule is these objects):
 * sessions, task snapshots, rejections, warnings and the goal and quest reports.
 */
object ScheduleJson {
    fun obj(fill: PageObject.() -> Unit): JsonObject = PageObject().apply(fill).done()

    fun num(v: Double): JsonValue = JsonNumber(v)

    fun num(v: Int): JsonValue = JsonNumber(v.toDouble())

    fun str(v: String): JsonValue = JsonString(v)

    fun bool(v: Boolean): JsonValue = JsonBool.of(v)

    fun strings(v: List<String>): JsonValue = JsonArray(v.map(::JsonString))

    fun orNull(v: JsonValue?): JsonValue = v ?: JsonNull

    /**
     * `deepClone` (`JSON.parse(JSON.stringify(x))`): NaN and the infinities become null and -0
     * becomes 0, at every depth.
     */
    fun clone(v: JsonValue): JsonValue =
        when (v) {
            is JsonNumber ->
                if (v.value.isNaN() || v.value.isInfinite()) JsonNull
                else if (v.value == 0.0) JsonNumber(0.0) else v
            is JsonArray -> JsonArray(v.items.map(::clone))
            is JsonObject -> JsonObject(v.members.mapValues { clone(it.value) })
            else -> v
        }

    /** A quest link as the page's object holds it. */
    fun link(l: QuestLink): JsonValue = Placer.linkJson(l)

    /**
     * LDO-8 `taskSnapshot` (`engine.js:1110-1115`): the members of a task the page copies onto its
     * session as `source`, each as `deepClone` leaves it; members the task does not have are left
     * out.
     */
    fun snapshot(t: CandidateTask): JsonValue =
        obj {
                if (t.hasCoverable)
                    this["coverable"] =
                        t.coverable?.let { c ->
                            obj {
                                this["reqTag"] = c.reqTag
                                this["start"] = num(c.start)
                                this["end"] = num(c.end)
                                this["link"] = link(c.link)
                                this["exactOnly"] = bool(c.exactOnly)
                            }
                        } ?: JsonNull
                this["id"] = str(t.id)
                this["kind"] = str(t.kind.name)
                this["title"] = t.title
                this["templateId"] = str(t.templateId)
                this["tag"] = t.tag
                this["goalId"] = t.goalId?.let(::str) ?: JsonNull
                this["goalTitle"] = t.goalTitle?.let(::str)
                this["linkedGoalIds"] = strings(t.linkedGoalIds)
                this["linkedQuestRequirementIds"] =
                    JsonArray(t.linkedQuestRequirementIds.map(::link))
                this["priority"] = num(t.priority)
                this["questWeight"] = num(t.questWeight)
                this["tier"] = num(t.tier)
                this["tierReason"] = str(t.tierReason)
                this["dOpt"] = num(t.dOpt)
                this["dMin"] = num(t.dMin)
                this["dMax"] = num(t.dMax)
                this["IP"] = t.physical
                this["IC"] = t.cognitive
                this["spacingKey"] = str(t.spacingKey)
                this["spacingMinH"] = t.spacingMinH
                this["spacingIdealH"] = t.spacingIdealH
                this["maxPerBwc"] = t.maxPerBwc
                this["avoidBeforeSleepMin"] = t.avoidBeforeSleepMin
                this["phases"] = t.phases
                this["preferRestDay"] = t.preferRestDay?.let(::bool)
                this["windowStart"] = num(t.windowStart)
                this["windowEnd"] = num(t.windowEnd)
                this["deadlineEpoch"] = t.deadlineEpoch
                this["week"] = num(t.week)
                this["sessionIndex"] = num(t.sessionIndex)
                this["dependsOn"] = strings(t.dependsOn)
                this["minLagMin"] = num(t.minLagMin)
                this["exclusive"] = bool(t.exclusive)
                this["requiresPrior"] =
                    t.requiresPrior?.let { p ->
                        obj {
                            this["templateIds"] = strings(p.templateIds)
                            this["count"] = p.count
                        }
                    } ?: JsonNull
            }
            .let(::clone)

    /**
     * LDO-8: a session on the plan as the page's object. A committed one has the members `commit`
     * gives it (`engine.js:1053-1060`); a kept one is the stored session with its waking day and
     * `kept` (the copy `prepareState` places, LDR-3). Either way with its explanation (LDO-2).
     */
    fun session(x: PlanSession): JsonValue {
        val stored = x.stored
        if (stored != null) {
            val m = LinkedHashMap(stored.members)
            m["bwcId"] = JsonString(x.cycleId)
            m["kept"] = JsonBool.TRUE
            x.explanation?.let { m["explanation"] = it }
            return JsonObject(m)
        }
        val c = x.commitment!!
        val t = c.task.task
        return obj {
            this["instanceId"] = x.instanceId
            this["taskId"] = x.taskId
            this["templateId"] = x.templateId
            this["title"] = t.title
            this["tag"] = t.tag
            this["kind"] = str(t.kind.name)
            this["goalId"] = c.goalId?.let(::str) ?: JsonNull
            this["goalTitle"] = t.goalTitle?.takeIf { it.isNotEmpty() }?.let(::str) ?: JsonNull
            this["linkedGoalIds"] = JsonArray(x.goalLinks)
            this["linkedQuestRequirementIds"] = JsonArray(x.questLinks.toList())
            this["startEpoch"] = num(x.startEpoch)
            this["endEpoch"] = num(x.endEpoch)
            this["durationSlots"] = num(x.durationSlots)
            this["minDurationSlots"] = num(c.minSlots)
            this["optDurationSlots"] = num(c.optSlots)
            this["tier"] = num(c.tier)
            this["lifecycleState"] = str("PLANNED")
            this["isUserLocked"] = bool(false)
            this["bwcId"] = str(x.cycleId)
            this["IP"] = x.physical
            this["IC"] = x.cognitive
            this["spacingKey"] = x.spacingKey
            this["compressed"] = bool(c.compressed)
            this["origStartEpoch"] = orNull(c.origStartEpoch)
            this["explanation"] = orNull(x.explanation)
            this["moves"] = num(0)
            this["source"] = c.task.snapshot ?: snapshot(t)
        }
    }

    /** LDJ-1 `diag` of a task the plan leaves out. */
    fun rejection(r: Rejection): JsonValue = obj {
        this["taskId"] = str(r.taskId)
        this["title"] = r.title
        this["tier"] = num(r.tier)
        this["reasonCode"] = str(r.reasonCode)
        this["humanExplanation"] = str(r.explanation)
        this["linkedQuestIds"] = strings(r.linkedQuestIds)
        this["goalId"] = r.goalId?.let(::str) ?: JsonNull
        this["perDay"] =
            r.perDay?.let { m ->
                JsonObject(m.entries.associate { it.key.toString() to num(it.value) })
            }
    }

    /** LDC-4's `diag` of a quest that makes no sessions. */
    fun questRejection(r: QuestRejection): JsonValue = obj {
        this["taskId"] = str(r.questId)
        this["title"] = JsonNull
        this["tier"] = JsonNull
        this["reasonCode"] = str(r.reasonCode)
        this["humanExplanation"] = str(r.explanation)
        this["linkedQuestIds"] = JsonArray.EMPTY
        this["goalId"] = JsonNull
    }

    /** LDO-1: a stored pinned session that now clashes with something stronger, excused. */
    fun displaced(session: JsonValue): JsonValue {
        val id = PageValues.member(session, "instanceId")
        return obj {
            this["taskId"] = id
            this["title"] = JsonNull
            this["tier"] = JsonNull
            this["reasonCode"] = str("EXCUSED_DISPLACED")
            this["humanExplanation"] =
                str(
                    "\"" +
                        PageValues.text(PageValues.member(session, "title")) +
                        "\" was locked but now conflicts with a commitment, so it was excused without penalty."
                )
            this["linkedQuestIds"] = JsonArray.EMPTY
            this["goalId"] = JsonNull
            this["instanceId"] = id
        }
    }

    /** An availability warning (LDS, LDA). */
    fun warning(w: AvailabilityWarning): JsonValue = obj {
        this["code"] = str(w.code)
        this["date"] = w.date?.let(::str)
        this["startEpoch"] = w.startEpoch?.let(::num)
        this["message"] = str(w.message)
    }

    fun passiveReminder(p: PassiveReminder): JsonValue = obj {
        this["questId"] = str(p.questId)
        this["title"] = str(p.title)
        this["anchor"] = str(p.anchor)
    }

    fun prompt(p: ClarificationPrompt): JsonValue = obj {
        this["goalId"] = str(p.goalId)
        this["title"] = str(p.title)
        this["reasonCode"] = str(p.reasonCode)
        this["missingParameters"] = strings(p.missingParameters)
        this["suggestedTemplateIds"] = strings(p.suggestedTemplateIds)
    }

    fun trajectory(w: TrajectoryWarning): JsonValue = obj {
        this["goalId"] = str(w.goalId)
        this["code"] = str(w.code)
        this["projectedCompletionEpoch"] = num(w.projectedCompletionEpoch)
        this["message"] = str(w.message)
    }

    /** LDQ-4's row. */
    fun coverage(r: QuestCoverageRow): JsonValue = obj {
        this["questId"] = str(r.quest.id)
        this["title"] = str(r.quest.title)
        this["tier"] = str(r.quest.tier)
        this["status"] = r.status
        this["deadlineEpoch"] = r.quest.deadlineEpoch
        this["scheduledSessions"] = num(r.scheduledSessions)
        this["remainingCount"] = num(r.remainingCount)
        this["recurrence"] = r.quest.recurrence
        this["covered"] = r.covered?.let(::bool) ?: JsonNull
    }

    /** LDO-2: a placement record (`placedMeta`'s entry) as the page holds it. */
    fun record(id: String, r: PlacementRecord): JsonValue = obj {
        this["id"] = str(id)
        this["phase"] = num(r.phase)
        val s = r.selection
        if (s != null) {
            this["k"] = num(s.k)
            this["b"] = num(s.cycle)
            this["slot"] = num(s.slot)
            this["n"] = num(s.n)
            this["score"] = num(s.score)
            this["regret"] = num(s.regret)
            this["count"] = num(s.count)
            this["key"] = num(s.key)
            this["F"] = num(s.fairness)
            if (s.capRelaxed) this["capRelaxed"] = bool(true)
        } else {
            this["move"] = r.move?.let(::str)
            this["score"] = num(r.score)
            this["count"] = num(r.count)
        }
    }
}
