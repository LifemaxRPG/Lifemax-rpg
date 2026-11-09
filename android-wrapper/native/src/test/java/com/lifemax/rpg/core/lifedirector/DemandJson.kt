package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.lifedirector.KernelGolden.array
import com.lifemax.rpg.core.lifedirector.KernelGolden.bool
import com.lifemax.rpg.core.lifedirector.KernelGolden.number
import com.lifemax.rpg.core.lifedirector.KernelGolden.string
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonValue

/**
 * The demand layer's results written as the page's JSON (`tools/gen_ld_demand_golden.js`): member
 * names, the members the page leaves out, and its constants. Only the replay uses these.
 */
object DemandJson {
    private class Obj {
        val m = linkedMapOf<String, JsonValue>()

        operator fun set(key: String, value: JsonValue?) {
            if (value != null) m[key] = value
        }

        fun done() = JsonObject(m)
    }

    private fun obj(fill: Obj.() -> Unit): JsonObject = Obj().apply(fill).done()

    private fun strings(values: List<String>) = array(values.map(::string))

    private fun nullable(value: String?): JsonValue = value?.let(::string) ?: JsonNull

    fun profile(p: TagProfile): JsonValue = obj {
        this["IP"] = number(p.physical)
        this["IC"] = number(p.cognitive)
        this["d"] = number(p.minutes)
    }

    fun goal(g: PlayerGoal): JsonValue = obj {
        this["id"] = string(g.id)
        this["title"] = string(g.title)
        this["archetypeId"] = g.archetypeId
        this["subject"] = nullable(g.subject)
        this["priority"] = number(g.priority)
        this["level"] = string(g.level)
        this["customTemplates"] = array(g.customTemplates)
        this["disabledTemplates"] = array(g.disabledTemplates)
        this["target"] = g.target
        this["createdEpoch"] = g.createdEpoch
        this["status"] = g.status
    }

    fun template(t: ActivityTemplate): JsonValue = JsonObject(t.members)

    fun plan(p: SessionPlan): JsonValue = obj {
        this["goal"] = goal(p.goal)
        this["template"] = template(p.template)
        planNumbers(this, p)
    }

    private fun planNumbers(o: Obj, p: SessionPlan) {
        o["fMED"] = p.fMED
        o["base"] = number(p.base)
        o["stretch"] = number(p.stretch)
        o["dMin"] = number(p.dMin)
        o["dOpt"] = number(p.dOpt)
        o["dMax"] = number(p.dMax)
        o["status"] = string(p.status)
        o["projection"] = p.projection?.let(::number) ?: JsonNull
        o["spacingMinH"] = p.spacingMinH
    }

    /** The demand case's summary of a plan: its numbers and its goal and template ids. */
    fun planSummary(p: SessionPlan): JsonValue = obj {
        this["goalId"] = string(p.goal.id)
        this["templateId"] = string(p.template.templateId)
        planNumbers(this, p)
    }

    fun prompts(e: GoalExpansion): JsonValue =
        array(
            e.clarificationPrompts.map {
                obj {
                    this["goalId"] = string(it.goalId)
                    this["title"] = string(it.title)
                    this["reasonCode"] = string(it.reasonCode)
                    this["missingParameters"] = strings(it.missingParameters)
                    this["suggestedTemplateIds"] = strings(it.suggestedTemplateIds)
                }
            }
        )

    fun warnings(e: GoalExpansion): JsonValue =
        array(
            e.trajectoryWarnings.map {
                obj {
                    this["goalId"] = string(it.goalId)
                    this["code"] = string(it.code)
                    this["projectedCompletionEpoch"] = number(it.projectedCompletionEpoch)
                    this["message"] = string(it.message)
                }
            }
        )

    fun expansion(e: GoalExpansion): JsonValue = obj {
        this["plans"] = array(e.plans.map(::plan))
        this["clarificationPrompts"] = prompts(e)
        this["trajectoryWarnings"] = warnings(e)
    }

    fun info(m: Map<String, GoalInfo>): JsonValue =
        JsonObject(
            m.mapValues { (_, i) ->
                obj {
                    this["lag"] = number(i.lag)
                    this["priority"] = number(i.priority)
                    this["title"] = string(i.title)
                }
            }
        )

    fun quest(q: PlayerQuest): JsonValue = obj {
        this["id"] = string(q.id)
        this["title"] = string(q.title)
        this["tier"] = string(q.tier)
        this["status"] = q.status
        this["deadlineEpoch"] = q.deadlineEpoch
        this["prerequisiteQuestIds"] = strings(q.prerequisiteQuestIds)
        this["exclusive"] = bool(q.exclusive)
        this["schedulingMode"] = q.schedulingMode
        this["recurrence"] = q.recurrence
        this["minSessionMinutes"] = q.minSessionMinutes
        this["sessionMinutes"] = q.sessionMinutes
        this["minIntensity"] = q.minIntensity
        this["appliedEventIds"] = q.appliedEventIds
        this["bounty"] = q.bounty
        this["source"] = q.source
        this["onlyDays"] = q.onlyDays?.let(::array) ?: JsonNull
        this["windowStartEpoch"] = q.windowStartEpoch
        this["hostRef"] = q.hostRef
        this["requirements"] =
            array(
                q.requirements.map { r ->
                    obj {
                        this["id"] = string(r.id)
                        this["tag"] = r.tag
                        this["targetCount"] = number(r.targetCount)
                        this["currentCount"] = number(r.currentCount)
                        this["targetMinutes"] = number(r.targetMinutes)
                        this["currentMinutes"] = number(r.currentMinutes)
                        this["minSessionMinutes"] = r.minSessionMinutes
                        this["sessionMinutes"] = r.sessionMinutes
                    }
                }
            )
    }

    private fun link(l: QuestLink): JsonValue = obj {
        this["questId"] = string(l.questId)
        this["reqId"] = string(l.reqId)
        this["questTitle"] = string(l.questTitle)
        this["deadlineEpoch"] = l.deadlineEpoch
    }

    fun task(t: CandidateTask, prepared: PreparedTask? = null): JsonValue = obj {
        this["kind"] = string(t.kind.name)
        this["linkedGoalIds"] = strings(t.linkedGoalIds)
        this["linkedQuestRequirementIds"] = array(t.linkedQuestRequirementIds.map(::link))
        this["dependsOn"] = strings(t.dependsOn)
        this["minLagMin"] = number(t.minLagMin)
        this["exclusive"] = bool(t.exclusive)
        this["requiresPrior"] =
            t.requiresPrior?.let { p ->
                obj {
                    this["templateIds"] = strings(p.templateIds)
                    this["count"] = p.count
                }
            } ?: JsonNull
        this["blockedReason"] = nullable(t.blockedReason)
        this["origStartEpoch"] = t.origStartEpoch?.let(::number) ?: JsonNull
        this["isPlaced"] = bool(t.isPlaced)
        this["priority"] = number(t.priority)
        this["questWeight"] = number(t.questWeight)
        this["id"] = string(t.id)
        this["title"] = t.title
        this["templateId"] = string(t.templateId)
        this["tag"] = t.tag
        this["goalId"] = nullable(t.goalId)
        this["goalTitle"] = t.goalTitle?.let(::string)
        this["tier"] = number(t.tier)
        this["tierReason"] = string(t.tierReason)
        this["dOpt"] = number(t.dOpt)
        this["dMin"] = number(t.dMin)
        this["dMax"] = number(t.dMax)
        this["IP"] = t.physical
        this["IC"] = t.cognitive
        this["spacingKey"] = string(t.spacingKey)
        this["spacingMinH"] = t.spacingMinH
        this["spacingIdealH"] = t.spacingIdealH
        this["maxPerBwc"] = t.maxPerBwc
        this["avoidBeforeSleepMin"] = t.avoidBeforeSleepMin
        this["phases"] = t.phases
        this["preferRestDay"] = t.preferRestDay?.let(::bool)
        this["windowStart"] = number(t.windowStart)
        this["windowEnd"] = number(t.windowEnd)
        this["deadlineEpoch"] = t.deadlineEpoch
        this["week"] = number(t.week)
        this["sessionIndex"] = number(t.sessionIndex)
        this["goalStatus"] = t.goalStatus?.let(::string)
        this["questId"] = t.questId?.let(::string)
        if (t.hasCoverable)
            this["coverable"] =
                t.coverable?.let { c ->
                    obj {
                        this["reqTag"] = c.reqTag
                        this["start"] = number(c.start)
                        this["end"] = number(c.end)
                        this["link"] = link(c.link)
                        this["exactOnly"] = bool(c.exactOnly)
                    }
                } ?: JsonNull
        if (prepared != null) prepare(this, prepared)
    }

    private fun prepare(o: Obj, p: PreparedTask) {
        o["dOptSlots"] = number(p.dOptSlots)
        o["dMinSlots"] = number(p.dMinSlots)
        o["_prio"] = number(p.priority)
        o["_lag"] = p.lag?.let(::number)
        o["_urgBase"] = number(p.urgencyBase)
        o["_high"] = bool(p.high)
    }

    fun prepared(p: PreparedTask): JsonValue = Obj().also { prepare(it, p) }.done()

    private fun candidateParts(o: Obj, c: CandidateSet, prepared: List<PreparedTask>?) {
        o["tasks"] = array(c.tasks.mapIndexed { i, t -> task(t, prepared?.get(i)) })
        o["passiveReminders"] =
            array(
                c.passiveReminders.map {
                    obj {
                        this["questId"] = string(it.questId)
                        this["title"] = string(it.title)
                        this["anchor"] = string(it.anchor)
                    }
                }
            )
        o["rejected"] =
            array(
                c.rejected.map {
                    obj {
                        this["taskId"] = string(it.questId)
                        this["title"] = JsonNull
                        this["tier"] = JsonNull
                        this["reasonCode"] = string(it.reasonCode)
                        this["humanExplanation"] = string(it.explanation)
                        this["linkedQuestIds"] = JsonArray.EMPTY
                        this["goalId"] = JsonNull
                    }
                }
            )
        o["questStatus"] = JsonObject(c.questStatus)
        o["quests"] = array(c.quests.map(::quest))
    }

    fun candidates(c: CandidateSet): JsonValue = obj { candidateParts(this, c, null) }

    fun coverage(rows: List<QuestCoverageRow>): JsonValue =
        array(
            rows.map { r ->
                obj {
                    this["questId"] = string(r.quest.id)
                    this["title"] = string(r.quest.title)
                    this["tier"] = string(r.quest.tier)
                    this["status"] = r.status
                    this["deadlineEpoch"] = r.quest.deadlineEpoch
                    this["scheduledSessions"] = number(r.scheduledSessions)
                    this["remainingCount"] = number(r.remainingCount)
                    this["recurrence"] = r.quest.recurrence
                    this["covered"] = r.covered?.let(::bool) ?: JsonNull
                }
            }
        )

    fun weeks(weeks: List<PlanWeek>): JsonValue =
        array(
            weeks.map {
                obj {
                    this["index"] = number(it.index)
                    this["key"] = string(it.key)
                    this["startEpoch"] = number(it.startEpoch)
                    this["endEpoch"] = number(it.endEpoch)
                    this["days"] = number(it.days)
                }
            }
        )

    fun drain(g: EnergyGrid): JsonValue = obj {
        this["drainP"] = array(g.drainPhysical.map { number(it.toDouble()) })
        this["drainC"] = array(g.drainCognitive.map { number(it.toDouble()) })
        this["runLen"] = array(g.runAhead.map { number(it.toInt()) })
        this["runBack"] = array(g.runBack.map { number(it.toInt()) })
    }

    fun demand(d: PlanDemandResult): JsonValue = obj {
        this["plans"] = array(d.expansion.plans.map(::planSummary))
        this["clarificationPrompts"] = prompts(d.expansion)
        this["trajectoryWarnings"] = warnings(d.expansion)
        candidateParts(this, d.candidates, d.prepared)
        this["goalInfo"] = info(d.goalInfo)
        this["share"] = JsonObject(d.shares.mapValues { number(it.value) })
    }
}
