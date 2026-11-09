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
 * Placement's results written as `tools/gen_ld_placement_golden.js` writes the page's: member
 * names, the members the page leaves out, and the projection of a session it records. Only the
 * replay uses these.
 */
object PlacementJson {
    private class Obj {
        val m = linkedMapOf<String, JsonValue>()

        operator fun set(key: String, value: JsonValue?) {
            if (value != null) m[key] = value
        }

        fun done() = JsonObject(m)
    }

    private fun obj(fill: Obj.() -> Unit): JsonObject = Obj().apply(fill).done()

    private fun orNull(v: JsonValue?): JsonValue = v ?: JsonNull

    private fun numbers(values: DoubleArray) = array(values.map(::number))

    private fun ints(values: IntArray) = array(values.map(::number))

    /** The members of a session the recorder keeps (its INSTANCE list), in that order. */
    private val INSTANCE =
        listOf(
            "instanceId",
            "taskId",
            "templateId",
            "goalId",
            "linkedGoalIds",
            "startEpoch",
            "endEpoch",
            "durationSlots",
            "minDurationSlots",
            "optDurationSlots",
            "tier",
            "lifecycleState",
            "isUserLocked",
            "bwcId",
            "IP",
            "IC",
            "spacingKey",
            "compressed",
            "origStartEpoch",
            "kept",
        )

    private fun linkText(x: JsonValue): JsonValue =
        if (x is JsonObject || x is JsonArray)
            string(
                PageValues.text(PageValues.member(x, "questId")) +
                    "|" +
                    PageValues.text(PageValues.member(x, "reqId"))
            )
        else x

    fun session(p: PlanSession): JsonValue {
        val stored = p.stored
        if (stored != null) {
            val o = linkedMapOf<String, JsonValue>()
            for (k in INSTANCE) when (k) {
                "bwcId" -> o[k] = string(p.cycleId)
                "kept" -> o[k] = bool(true)
                else -> stored[k]?.let { o[k] = it }
            }
            when (val links = stored["linkedQuestRequirementIds"]) {
                null -> {}
                is JsonArray -> o["links"] = array(links.items.map(::linkText))
                else -> o["links"] = links
            }
            return JsonObject(o)
        }
        val c = p.commitment!!
        return obj {
            this["instanceId"] = p.instanceId
            this["taskId"] = p.taskId
            this["templateId"] = p.templateId
            this["goalId"] = c.goalId?.let(::string) ?: JsonNull
            this["linkedGoalIds"] = array(p.goalLinks)
            this["startEpoch"] = number(p.startEpoch)
            this["endEpoch"] = number(p.endEpoch)
            this["durationSlots"] = number(p.durationSlots)
            this["minDurationSlots"] = number(c.minSlots)
            this["optDurationSlots"] = number(c.optSlots)
            this["tier"] = number(c.tier)
            this["lifecycleState"] = string("PLANNED")
            this["isUserLocked"] = bool(false)
            this["bwcId"] = string(p.cycleId)
            this["IP"] = p.physical
            this["IC"] = p.cognitive
            this["spacingKey"] = p.spacingKey
            this["compressed"] = bool(c.compressed)
            this["origStartEpoch"] = orNull(c.origStartEpoch)
            this["links"] = array(p.questLinks.map(::linkText))
        }
    }

    private fun ids(list: List<JsonValue>) =
        array(list.map { orNull(PageValues.member(it, "instanceId")) })

    private fun cycles(s: PlanState) =
        array(
            s.cycles.map { c ->
                obj {
                    this["id"] = string(c.cycle.id)
                    this["perKey"] = JsonObject(c.perKey.mapValues { number(it.value) })
                    this["goalMin"] = number(c.goalMinutes)
                    this["highCount"] = number(c.highCount)
                    this["instances"] = array(c.sessions.map { orNull(it.instanceId) })
                    this["freeSlots"] = number(c.freeSlots)
                    this["recoverySlots"] = number(c.recoverySlots)
                    this["recoveryShortfall"] = bool(c.recoveryShortfall)
                }
            }
        )

    private fun common(o: Obj, p: PreparedPlan) {
        val s = p.state
        o["nowSlot"] = number(s.nowSlot)
        o["history"] = ids(p.history)
        o["kept"] = ids(p.kept)
        o["displaced"] =
            array(
                p.displaced.map {
                    array(
                        listOf(
                            orNull(PageValues.member(it.session, "instanceId")),
                            number(it.slotCode),
                        )
                    )
                }
            )
        o["replannable"] = array(p.replannable.keys.map(::orNull))
        o["bw"] = cycles(s)
        o["instances"] = array(s.sessions.map(::session))
        o["spacing"] =
            array(s.spacing.map { (k, v) -> array(listOf(orNull(k), array(v.map(::number)))) })
        o["fair"] = obj {
            this["goalMin"] = JsonObject(s.fair.goalMinutes.mapValues { number(it.value) })
            this["total"] = number(s.fair.total)
            this["share"] = JsonObject(s.fair.share.mapValues { number(it.value) })
        }
        o["type"] = string(s.energy.type.joinToString(""))
    }

    /** The prepare case's answer. */
    fun prepared(p: PreparedPlan): JsonValue = obj {
        val s = p.state
        common(this, p)
        this["sleepTarget"] = s.sleepTarget
        this["checkin"] =
            s.checkIn?.let {
                obj {
                    this["epoch"] = it.stored
                    this["value"] = number(it.value)
                }
            } ?: JsonNull
        this["sleepFactors"] = array(s.cycles.map { number(it.sleepFactor) })
        this["taskOwner"] = ints(s.owner)
        this["taskIP"] = ints(s.energy.taskPhysical)
        this["taskIC"] = ints(s.energy.taskCognitive)
        this["baseP"] = numbers(DoubleArray(s.size) { s.baseP[it].toDouble() })
        this["baseC"] = numbers(DoubleArray(s.size) { s.baseC[it].toDouble() })
        this["drainP"] = numbers(DoubleArray(s.size) { s.energy.drainPhysical[it].toDouble() })
        this["drainC"] = numbers(DoubleArray(s.size) { s.energy.drainCognitive[it].toDouble() })
        this["runLen"] = array(s.energy.runAhead.map { number(it.toInt()) })
        this["runBack"] = array(s.energy.runBack.map { number(it.toInt()) })
        this["energyP"] = numbers(DoubleArray(s.size) { EnergyModel.at(s, it, true) })
        this["energyC"] = numbers(DoubleArray(s.size) { EnergyModel.at(s, it, false) })
    }

    private fun window(w: TaskWindow): JsonValue = obj {
        if (w.blocked != null) this["blocked"] = number(w.blocked.code)
        else {
            this["earliestSlot"] = number(w.earliestSlot)
            this["latestSlot"] = number(w.latestSlot)
        }
    }

    private fun fit(f: PlanFit): JsonValue = obj {
        this["feasible"] = bool(f.feasible)
        if (!f.feasible) this["reason"] = number(f.reason!!.code)
        else {
            this["bwc"] = number(f.cycle)
            this["slot"] = number(f.slot)
            this["n"] = number(f.n)
            this["score"] = number(f.score)
            this["second"] = number(f.second)
            this["count"] = number(f.count)
        }
    }

    fun detail(d: ScoreDetail): JsonValue = obj {
        this["U"] = number(d.u)
        this["F"] = number(d.fairness)
        this["wsum"] = number(d.weightedSum)
        this["factors"] =
            JsonObject(linkedMapOf(*d.factors.map { it.first to number(it.second) }.toTypedArray()))
        this["penalties"] = obj {
            this["fragmentation"] = number(d.fragmentation)
            this["movement"] = number(d.movement)
            this["compression"] = number(d.compression)
        }
        this["phase"] = string(d.phase.name)
        this["energyP"] = number(d.energyP)
        this["energyC"] = number(d.energyC)
        this["restDay"] = bool(d.restDay)
    }

    private fun probe(s: PlanState, t: PlanTask): JsonValue {
        val w = Feasibility.window(s, t)
        val across = Placer.bestAcross(s, t)
        return obj {
            this["id"] = string(t.id)
            this["el"] = window(w)
            this["across"] = fit(across)
            this["days"] =
                if (w.blocked != null) JsonNull
                else
                    array(
                        s.cycles.map { c ->
                            val tally = IntArray(Placer.TALLY_SIZE)
                            val r = Placer.bestInDay(s, t, c, w, tally)
                            val counted = linkedMapOf<String, JsonValue>()
                            for (i in tally.indices) if (tally[i] > 0)
                                counted[i.toString()] = number(tally[i])
                            array(
                                listOf(
                                    number(r.score),
                                    number(r.slot),
                                    number(r.n),
                                    JsonObject(counted),
                                )
                            )
                        }
                    )
            this["detail"] =
                if (across.feasible)
                    detail(
                        PlacementScore.detail(s, t, s.cycles[across.cycle], across.slot, across.n)
                    )
                else JsonNull
        }
    }

    fun rejection(r: Rejection): JsonValue = obj {
        this["taskId"] = string(r.taskId)
        this["title"] = r.title
        this["tier"] = number(r.tier)
        this["reasonCode"] = string(r.reasonCode)
        this["humanExplanation"] = string(r.explanation)
        this["linkedQuestIds"] = array(r.linkedQuestIds.map(::string))
        this["goalId"] = r.goalId?.let(::string) ?: JsonNull
        this["perDay"] =
            r.perDay?.let { m ->
                JsonObject(m.entries.associate { it.key.toString() to number(it.value) })
            }
    }

    /**
     * The place case's answer; [probes] `all` probes every active task, `one` the first, `few` the
     * first two and the first four others left unplaced.
     */
    fun placed(c: Construction, probes: String): JsonValue = obj {
        val s = c.state
        common(this, c.prepared)
        val days = s.cycles.size
        this["active"] = array(c.active.map { string(it.id) })
        this["cells"] =
            array(
                c.active.indices.map { k ->
                    array(
                        (0 until days).map { b ->
                            val d = c.firstScores[k * days + b]
                            array(listOf(number(d.score), number(d.slot), number(d.n)))
                        }
                    )
                }
            )
        this["placements"] =
            array(
                c.placements.map { p ->
                    val x = p.selection
                    obj {
                        this["id"] = p.session.instanceId
                        this["phase"] = number(1)
                        this["k"] = number(x.k)
                        this["b"] = number(x.cycle)
                        this["slot"] = number(x.slot)
                        this["n"] = number(x.n)
                        this["score"] = number(x.score)
                        this["regret"] = number(x.regret)
                        this["count"] = number(x.count)
                        this["key"] = number(x.key)
                        this["F"] = number(x.fairness)
                        if (x.capRelaxed) this["capRelaxed"] = bool(true)
                    }
                }
            )
        this["tasks"] =
            array(
                c.tasks.map {
                    array(
                        listOf(
                            string(it.id),
                            bool(it.isPlaced),
                            orNull(it.coveredBy),
                            orNull(it.origStartEpoch),
                        )
                    )
                }
            )
        this["anchors"] = array(s.sessions.map { bool(PlanConstruction.anchorsDependents(s, it)) })
        this["rejections"] =
            array(c.tasks.filter { !it.isPlaced }.map { rejection(Rejections.of(s, it)) })
        val chosen =
            when (probes) {
                "all" -> c.active
                "one" -> c.active.take(1)
                else -> c.active.take(2) + c.active.drop(2).filter { !it.isPlaced }.take(4)
            }
        this["probes"] = array(chosen.map { probe(s, it) })
    }
}
