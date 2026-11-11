package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.lifedirector.LedgerValues.field
import com.lifemax.rpg.core.lifedirector.PageValues.number
import com.lifemax.rpg.core.lifedirector.PageValues.or
import com.lifemax.rpg.core.lifedirector.PageValues.same
import com.lifemax.rpg.core.lifedirector.PageValues.text
import com.lifemax.rpg.core.lifedirector.PageValues.truthy
import com.lifemax.rpg.core.lifedirector.ScheduleJson.obj
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/**
 * The recorder's event stream (`runStream` in `tools/gen_ld_ledger_golden.js`) natively: the host's
 * flows reduced to the ledger calls, on one ledger, its quests, goals, plan and history. Each
 * step's answer is written exactly as the recorder writes it.
 */
object LedgerStream {
    private const val MISSES_PER_TICK = 12

    private class State(spec: JsonObject) {
        val zone = ZoneNames.resolve(text(spec["tz"])) ?: error("zone ${spec["tz"]}")
        val prefsIn: JsonValue? = spec["prefs"]
        val prefs = PlanPreferences.of(prefsIn)
        var ledger: JsonValue? =
            or(spec["ledger"], null) ?: CompletionLedger.empty(spec["playerId"])
        var planned: List<JsonValue> = (spec["planned"] as JsonArray).items
        val history = ArrayList<JsonValue>()
        var quests: JsonValue? = spec["quests"]
        var goals: JsonValue? = spec["goals"]
        val refs = ArrayList<String>()
        val playerId: JsonValue? = spec["playerId"]
    }

    private fun brief(i: JsonValue): JsonValue =
        JsonString(
            text(field(i, "instanceId")) +
                ":" +
                text(field(i, "lifecycleState")) +
                ":" +
                text(or(field(i, "revision"), JsonNumber(0.0)))
        )

    private fun view(s: State): Map<String, JsonValue> {
        val l = s.ledger as JsonObject
        return linkedMapOf(
            "ledger" to JsonObject(l.members.filterKeys { it != "events" }),
            "events" to JsonNumber((l["events"] as JsonObject).members.size.toDouble()),
            "planned" to JsonArray(s.planned.map(::brief)),
            "history" to JsonArray(s.history.map(::brief)),
        )
    }

    /**
     * logic.js:287 afterEvent: the event to quests and goals, with its day in the stream's zone.
     */
    private fun after(s: State, event: JsonObject): JsonValue {
        val parts = WallClock.localParts(number(event["actualStartEpoch"]), s.zone)
        val dayKey = CalendarMath.key(parts.date)
        val opts = obj { this["dayKey"] = JsonString(dayKey) }
        val rq = QuestProgress.reconcile(event, s.quests, s.goals, JsonArray(s.planned), opts)
        val oldQuests = (s.quests as JsonArray).items
        val oldGoals = (s.goals as JsonArray).items
        val out = obj {
            this["dayKey"] = JsonString(dayKey)
            this["questsChanged"] =
                JsonArray(rq.quests.mapIndexed { i, q -> if (q == oldQuests[i]) JsonNull else q })
            this["goalsChanged"] =
                JsonArray(rq.goals.mapIndexed { i, g -> if (g == oldGoals[i]) JsonNull else g })
            this["sessionsChanged"] =
                JsonArray(rq.pruned.filter { p -> s.planned.none { it === p } })
            this["questBountyRewards"] = JsonArray(rq.bounties)
            this["removedInstanceIds"] = JsonArray(rq.removedIds)
            this["questDeltas"] = JsonArray(rq.deltas)
        }
        s.quests = JsonArray(rq.quests)
        s.goals = JsonArray(rq.goals)
        val removed = JsonArray(rq.removedIds)
        s.planned = rq.pruned.filter { !LedgerValues.includes(removed, field(it, "instanceId")) }
        return out
    }

    private fun lastKept(s: State, id: JsonValue?): JsonValue? =
        s.history.lastOrNull { same(field(it, "instanceId"), id) }

    private fun settle(s: State, inst: JsonValue, state: String, key: String, value: JsonValue) {
        s.planned = s.planned.filter { !same(field(it, "instanceId"), field(inst, "instanceId")) }
        val m = LedgerValues.ownMembers(inst)
        m["lifecycleState"] = JsonString(state)
        m[key] = value
        s.history += JsonObject(m)
    }

    private fun miss(c: MissOutcome): JsonObject = obj {
        this["penaltyId"] = JsonString(c.penaltyId)
        this["state"] = JsonString(c.state)
        this["consequence"] = c.consequence ?: JsonNull
        this["regroupPrompt"] = JsonBool.of(c.regroupPrompt)
        this["note"] = c.note?.let(::JsonString) ?: JsonNull
    }

    private fun complete(s: State, st: JsonObject, out: PageObject) {
        val adhoc = text(st["op"]) == "adhoc"
        val inst =
            if (adhoc) null
            else
                s.planned.firstOrNull { same(field(it, "instanceId"), st["id"]) }
                    ?: lastKept(s, st["id"])
        if (!adhoc && inst == null) {
            out["found"] = JsonBool.FALSE
            return
        }
        val ci = obj {
            this["ledger"] = s.ledger
            this["playerId"] = s.playerId
            if (inst != null) this["instance"] = inst
            else {
                this["adhoc"] = obj {
                    this["tag"] = st["tag"]
                    this["title"] = st["title"]
                }
                this["clientEventId"] = st["client"]
            }
            this["actualStartEpoch"] = st["start"]
            this["actualDurationMinutes"] = st["minutes"]
            this["nowEpoch"] = st["now"]
            if (inst != null) this["satisfiedRatio"] = s.prefs["partialSatisfiedRatio"]
        }
        val r = CompletionLedger.record(ci)
        s.refs += r.eventId
        out["applied"] = JsonBool.of(r.applied)
        out["reason"] = r.reason?.let(::JsonString)
        out["eventId"] = JsonString(r.eventId)
        if (inst != null)
            out["transition"] =
                JsonBool.of(
                    MissConsequences.canTransition(
                        field(inst, "lifecycleState"),
                        JsonString(r.lifecycleState ?: "COMPLETED_ON_TIME"),
                    )
                )
        if (!r.applied) return
        s.ledger = r.ledger
        out["event"] = r.event
        out["lifecycleState"] = JsonString(r.lifecycleState!!)
        out["tokenEarned"] = JsonBool.of(r.tokenEarned)
        out["residualTask"] = r.residualTask ?: JsonNull
        if (inst != null)
            settle(s, inst, r.lifecycleState, "completionEventId", JsonString(r.eventId))
        out["progress"] = after(s, r.event!!)
    }

    private fun step(s: State, st: JsonObject): JsonObject {
        val out = PageObject()
        val op = text(st["op"])
        out["op"] = JsonString(op)
        fun ref(): String? = s.refs.getOrNull(number(st["ref"]).toInt())
        when (op) {
            "complete",
            "adhoc" -> complete(s, st, out)
            "revoke" -> {
                val id = ref()
                val (ledger, revoked) =
                    CompletionLedger.revoke(
                        s.ledger,
                        id?.let(::JsonString),
                        st["reason"],
                        st["now"],
                    )
                s.ledger = ledger
                out["revoked"] = JsonBool.of(revoked)
                out["eventId"] = id?.let(::JsonString)
            }
            "reopen" -> {
                val h = lastKept(s, st["id"])
                val done = h?.let { field(it, "completionEventId") }
                val revoked = or(field(s.ledger, "revoked"), JsonObject.EMPTY)
                val ok =
                    h != null &&
                        truthy(done) &&
                        truthy(LedgerValues.lookup(revoked, text(done))) &&
                        s.planned.none { same(field(it, "instanceId"), st["id"]) }
                if (ok) {
                    val m = LedgerValues.ownMembers(h)
                    m["lifecycleState"] = JsonString("PLANNED")
                    m["revision"] = LedgerValues.add(or(field(h, "revision"), JsonNumber(0.0)), 1.0)
                    m["completionEventId"] = JsonNull
                    s.planned = s.planned + JsonObject(m)
                }
                out["reopened"] = JsonBool.of(ok)
            }
            "skip" -> {
                val inst = s.planned.firstOrNull { same(field(it, "instanceId"), st["id"]) }
                if (inst == null) out["found"] = JsonBool.FALSE
                else {
                    val c =
                        MissConsequences.apply(
                            s.ledger,
                            inst,
                            s.prefsIn,
                            st["now"],
                            JsonString("SKIP"),
                        )
                    s.ledger = c.ledger
                    settle(s, inst, c.state, "consequence", c.consequence ?: JsonNull)
                    miss(c).members.forEach { (k, v) -> out[k] = v }
                }
            }
            "tick" -> {
                val misses =
                    MissConsequences.detect(
                        JsonArray(s.planned),
                        st["now"],
                        s.prefs["missGraceMinutes"],
                    )
                out["misses"] = JsonArray(misses.map { field(it, "instanceId") ?: JsonNull })
                val done = ArrayList<JsonValue>()
                for (inst in misses.take(MISSES_PER_TICK)) {
                    val c =
                        MissConsequences.apply(
                            s.ledger,
                            inst,
                            s.prefsIn,
                            st["now"],
                            JsonString("MISS"),
                        )
                    s.ledger = c.ledger
                    settle(s, inst, c.state, "consequence", c.consequence ?: JsonNull)
                    done += miss(c)
                }
                out["consequences"] = JsonArray(done)
            }
            "has" -> {
                val id = s.refs.getOrNull(number(st["ref"]).toInt()) ?: "ev_unknown"
                out["has"] = JsonBool.of(CompletionLedger.has(s.ledger, JsonString(id)))
            }
            else -> error("unknown step $op")
        }
        view(s).forEach { (k, v) -> out[k] = v }
        return out.done()
    }

    /** The stream [spec]'s answer. */
    fun run(spec: JsonObject): JsonValue {
        val s = State(spec)
        val steps = (spec["steps"] as JsonArray).items.map { step(s, it as JsonObject) }
        return obj {
            this["steps"] = JsonArray(steps)
            this["ledger"] = s.ledger
            this["quests"] = s.quests
            this["goals"] = s.goals
            this["planned"] = JsonArray(s.planned)
            this["history"] = JsonArray(s.history)
        }
    }
}
