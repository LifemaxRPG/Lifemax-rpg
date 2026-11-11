package com.lifemax.rpg.core.lifedirector.host

import com.lifemax.rpg.core.lifedirector.CompletionLedger
import com.lifemax.rpg.core.lifedirector.PlayerQuests
import com.lifemax.rpg.core.lifedirector.QuestProgress
import com.lifemax.rpg.core.lifedirector.Recorded
import com.lifemax.rpg.core.lifedirector.host.Js.a
import com.lifemax.rpg.core.lifedirector.host.Js.assign
import com.lifemax.rpg.core.lifedirector.host.Js.at
import com.lifemax.rpg.core.lifedirector.host.Js.get
import com.lifemax.rpg.core.lifedirector.host.Js.list
import com.lifemax.rpg.core.lifedirector.host.Js.num
import com.lifemax.rpg.core.lifedirector.host.Js.numText
import com.lifemax.rpg.core.lifedirector.host.Js.o
import com.lifemax.rpg.core.lifedirector.host.Js.obj
import com.lifemax.rpg.core.lifedirector.host.Js.text
import com.lifemax.rpg.core.lifedirector.host.Js.truthy
import com.lifemax.rpg.core.save.JsonNull
import kotlin.math.ceil

/**
 * Completion as the page records it (`logic.js:277-391`): `afterEvent` (HC-23), `finishInstance`
 * (HC-20 to HC-26), `doable` (HC-12), `repairEarlyDone` (HC-42) and `logToday` (HC-27). Each
 * changes the [HostPage] in place, in the page's order, with the page's host calls.
 */
object HostCompletion {
    /** HC-12: a session can be done from 6 hours before it. */
    const val DOABLE_AHEAD = 6 * 3600.0
    private const val EARLIEST_START = 12 * 3600.0
    private const val MINUTE = 60.0
    private const val SLOT_MINUTES = 15.0

    /**
     * What `finishInstance` and `logToday` give: the ledger's answer, or a repeat (`{duplicate}`).
     */
    class Outcome(val recorded: Recorded?, val duplicate: Boolean = false)

    /**
     * Finishing options (`opt`): who logged it, whether XP is awarded, when it started, quietness.
     */
    class FinishOptions(
        val hostActivityId: String? = null,
        val awardXp: Boolean = true,
        val startEpoch: Double? = null,
        val silent: Boolean = false,
        val quietRefusal: Boolean = false,
    )

    private fun unsaved(a: AwardReply) =
        a.reason == "HOST_SAVE_FAILED" || a.reason == "HOST_READ_ONLY"

    /** `xpWords` (`logic.js:280`). */
    fun xpWords(a: AwardReply): String =
        if (a.awarded) "+" + (a.xp?.let(::numText) ?: "undefined") + " XP"
        else HostWords.XP_WORDS[a.reason] ?: ""

    /**
     * HC-23 `afterEvent` (`logic.js:284-305`): the event counted into quests and goals; each quest
     * it moved remembers the event in the overlay, a quest whose requirements are met is completed
     * in LifeMax (its bounty) when the XP was saved ([bounty]), one still short is reported as
     * `n/m`; the plan keeps the sessions the progress released out.
     */
    fun afterEvent(p: HostPage, event: JsObj, bounty: Boolean): MutableList<String> {
        val qs = p.hostQuests(p.now)
        val st = p.s
        val sched = p.schedule
        val future = if (sched != null) sched["scheduledInstances"] else JsArr()
        val dayKey = HostWords.dayKey(num(event["actualStartEpoch"]), p.tz())
        val rq =
            QuestProgress.reconcile(
                Js.value(event),
                Js.value(qs),
                Js.json(st["goals"]),
                Js.json(future),
                Js.value(obj("dayKey" to dayKey)),
            )
        st["goals"] = Js.of(com.lifemax.rpg.core.save.JsonArray(rq.goals))
        val notes = ArrayList<String>()
        val updated = rq.quests.map(Js::of)
        for (d in rq.deltas.map(Js::of)) {
            val questId = at(d, "questId")
            val q = qs.firstOrNull { Js.same(at(it, "id"), questId) }
            if (!truthy(q) || !truthy(at(q, "hostRef"))) continue
            val hostRef = at(q, "hostRef")
            val key = text(at(hostRef, "key"))
            val overlay = o(get(st, "questOverlay"))!!
            val ov = o(overlay[key]).takeIf(::truthy) ?: obj("eventIds" to JsArr())
            overlay[key] = ov
            val ids =
                a(get(ov, "eventIds"))
                    ?: throw JsThrow(
                        "TypeError",
                        "Cannot read properties of undefined (reading 'includes')",
                    )
            if (ids.none { Js.same(it, event["eventId"]) }) ids.add(event["eventId"])
            val uq = updated.firstOrNull { Js.same(at(it, "id"), questId) }
            val nq = PlayerQuests.of(Js.json(uq))
            val met =
                nq.requirements.all {
                    (!truthy(it.targetCount) || it.currentCount >= it.targetCount) &&
                        (!truthy(it.targetMinutes) || it.currentMinutes >= it.targetMinutes)
                }
            if (met && truthy(at(hostRef, "questId")) && bounty) {
                val c = p.host.completeQuest(Js.value(hostRef))
                if (c.completed)
                    notes +=
                        "Quest complete: " +
                            (c.name ?: "undefined") +
                            " +" +
                            (c.xp?.let(::numText) ?: "undefined") +
                            " XP"
            } else if (!met) {
                val r = nq.requirements[0]
                notes +=
                    text(at(q, "title")) +
                        " " +
                        numText(r.currentCount) +
                        "/" +
                        numText(r.targetCount)
            }
        }
        if (sched != null) {
            val removed = rq.removedIds.map(Js::of)
            sched["scheduledInstances"] =
                Js.arrOf(
                    rq.pruned.map(Js::of).filter { i ->
                        removed.none { Js.same(it, at(i, "instanceId")) }
                    }
                )
        }
        return notes
    }

    private fun truthy(v: Double): Boolean = !(v == 0.0 || v.isNaN())

    /**
     * HC-20 to HC-26 `finishInstance(inst, minutes, opt)` (`logic.js:308-332`): the ledger first
     * (start: the given start, else the earlier of the plan's start and `minutes` before now, at
     * most 12 hours before the plan), then the XP award, the plan and history, quest progress, a
     * re-plan of the rest of a partial session, and the save; then the notice.
     */
    fun finish(p: HostPage, inst: JsObj, minutes: Double, opt: FinishOptions): Outcome {
        val now = p.now
        val instStart = num(inst["startEpoch"])
        val given = opt.startEpoch?.takeIf { truthy(it) }
        val start = given ?: minOf(instStart, now - minutes * MINUTE).takeIf { truthy(it) } ?: now
        val r =
            CompletionLedger.record(
                Js.value(
                    obj(
                        "ledger" to p.s["ledger"],
                        "playerId" to p.s["playerId"],
                        "instance" to inst,
                        "actualStartEpoch" to maxOf(start, instStart - EARLIEST_START),
                        "actualDurationMinutes" to minutes,
                        "nowEpoch" to now,
                        "satisfiedRatio" to p.prefs()["partialSatisfiedRatio"],
                    )
                )
            )
        p.lastFinishRefusal = if (r.applied) null else r.reason
        if (!r.applied) {
            if (!opt.silent && !opt.quietRefusal)
                p.say(
                    if (r.reason == "DUPLICATE_EVENT_ID") "Already recorded."
                    else "Cannot complete this block (${r.reason})."
                )
            return Outcome(null)
        }
        val event = o(Js.of(r.event))!!
        p.s["ledger"] = Js.of(r.ledger)
        var xpNote = ""
        var hostActivityId: Any? = opt.hostActivityId ?: JsonNull
        var bounty = true
        if (opt.awardXp) {
            val a = p.host.awardActivity(Js.value(event), inst["title"] as? String)
            bounty = !unsaved(a)
            xpNote = xpWords(a)
            if (a.awarded) hostActivityId = a.activityId ?: JsonNull
        }
        val sched =
            o(get(p.s, "schedule"))
                ?: throw JsThrow(
                    "TypeError",
                    "Cannot read properties of null (reading 'scheduledInstances')",
                )
        sched["scheduledInstances"] =
            Js.arrOf(
                list(sched["scheduledInstances"]).filter {
                    !Js.same(at(it, "instanceId"), inst["instanceId"])
                }
            )
        sched["historyInstances"] =
            Js.arrOf(
                list(sched["historyInstances"]) +
                    assign(
                        inst,
                        obj(
                            "lifecycleState" to r.lifecycleState,
                            "completionEventId" to r.eventId,
                            "hostActivityId" to hostActivityId,
                            "actualMinutes" to event["actualDurationMinutes"],
                            "resolvedAtEpoch" to now,
                        ),
                    )
            )
        val notes = afterEvent(p, event, bounty)
        if (r.residualTask != null && r.residualTask != JsonNull) {
            val rr =
                p.reschedule(
                    obj(
                        "type" to "RESIDUAL",
                        "task" to Js.of(r.residualTask),
                        "parentInstanceId" to inst["instanceId"],
                    )
                )
            if (rr != null) notes += "Rest of it re-planned"
        }
        if (r.tokenEarned) notes += "Rest token earned"
        p.persist()
        if (!opt.silent) {
            val what =
                text(inst["title"]) +
                    if (r.lifecycleState == "PARTIALLY_COMPLETED") " partly done" else " done"
            p.say((listOf(what, xpNote) + notes).filter { it.isNotEmpty() }.joinToString(" · "))
        }
        return Outcome(r)
    }

    /**
     * HC-12 `doable` (`logic.js:340-344`): from 6 hours before, or any time on its own local day.
     */
    fun doable(p: HostPage, inst: JsObj, now: Double): Boolean {
        val start = num(inst["startEpoch"])
        if (start - now <= DOABLE_AHEAD) return true
        return HostWords.dayKey(start, p.tz()) == HostWords.dayKey(now, p.tz())
    }

    /**
     * `showAsDoneNow` (`logic.js:346-349`): the entry moved to end now, at least 15 minutes long.
     */
    private fun showAsDoneNow(entry: JsObj, minutes: Double, now: Double): JsObj {
        val start =
            now -
                maxOf(SLOT_MINUTES, minutes).let { if (minutes.isNaN()) Double.NaN else it } *
                    MINUTE
        entry["startEpoch"] = start
        entry["endEpoch"] = now
        return entry
    }

    /**
     * HC-42 `repairEarlyDone` (`logic.js:352-371`), once per install: a session completed a day or
     * more before its own day goes back on the plan on that day (when its time is still ahead and
     * free), and its history entry shows when the work was done. True when the flag was set now.
     */
    fun repairEarlyDone(p: HostPage): Boolean {
        val st = p.s
        val fixes = st["fixes"]
        if (truthy(fixes) && truthy(at(fixes, "earlyDone"))) return false
        st["fixes"] = assign(o(fixes), obj("earlyDone" to 1.0))
        val s = p.schedule ?: return true
        val now = p.now
        val zone = p.tz()
        var n = 0
        for (hv in list(s["historyInstances"])) {
            val h = o(hv) ?: continue
            if (
                !text(h["lifecycleState"]).contains("COMPLETED") ||
                    truthy(h["adhoc"]) ||
                    !truthy(h["resolvedAtEpoch"])
            )
                continue
            val resolved = num(h["resolvedAtEpoch"])
            if (
                num(h["startEpoch"]) - resolved <= DOABLE_AHEAD ||
                    HostWords.dayKey(resolved, zone) >= HostWords.dayKey(num(h["startEpoch"]), zone)
            )
                continue
            val plannedStart = h["startEpoch"]
            val plannedEnd = h["endEpoch"]
            val minutes =
                h["actualMinutes"].takeIf(::truthy) ?: (num(h["durationSlots"]) * SLOT_MINUTES)
            showAsDoneNow(h, num(minutes), resolved)
            h["movedFromEpoch"] = plannedStart
            val ps = num(plannedStart)
            val pe = num(plannedEnd)
            val planned =
                Js.a(get(s, "scheduledInstances"))
                    ?: throw JsThrow(
                        "TypeError",
                        "Cannot read properties of undefined (reading 'some')",
                    )
            if (
                ps > now &&
                    planned.none { num(at(it, "startEpoch")) < pe && num(at(it, "endEpoch")) > ps }
            ) {
                planned.add(
                    assign(
                        h,
                        obj(
                            "instanceId" to Js.text(h["instanceId"]) + "-r",
                            "startEpoch" to plannedStart,
                            "endEpoch" to plannedEnd,
                            "lifecycleState" to "PLANNED",
                            "revision" to num(h["revision"].takeIf(::truthy) ?: 0.0) + 1,
                            "completionEventId" to JsonNull,
                            "hostActivityId" to JsonNull,
                            "actualMinutes" to JsonNull,
                            "resolvedAtEpoch" to JsonNull,
                            "movedFromEpoch" to JsonNull,
                            "undone" to false,
                        ),
                    )
                )
            }
            n++
        }
        if (n > 0)
            p.banner =
                obj(
                    "kind" to "warn",
                    "text" to
                        n.toString() +
                            " session" +
                            (if (n == 1) " was" else "s were") +
                            " marked done before " +
                            (if (n == 1) "its" else "their") +
                            " day. They’re back on the plan, and the work you did shows on the day you did it. Your XP is kept.",
                )
        return true
    }

    /**
     * HC-27 `logToday(inst, minutes, op)` (`logic.js:374-390`): an ad-hoc completion for now under
     * `ld-today:` + op (a repeat is a duplicate), the award, a history entry shown when done, quest
     * progress, the save and the notice; the planned session stays.
     */
    fun logToday(p: HostPage, inst: JsObj, minutes: Double, op: String?): Outcome {
        val now = p.now
        val id = op?.takeIf { it.isNotEmpty() } ?: p.host.newId()
        val r =
            CompletionLedger.record(
                Js.value(
                    obj(
                        "ledger" to p.s["ledger"],
                        "playerId" to p.s["playerId"],
                        "adhoc" to obj("tag" to inst["tag"], "title" to inst["title"]),
                        "clientEventId" to "ld-today:$id",
                        "actualStartEpoch" to now - minutes * MINUTE,
                        "actualDurationMinutes" to minutes,
                        "nowEpoch" to now,
                    )
                )
            )
        if (!r.applied) {
            if (!op.isNullOrEmpty() && r.reason == "DUPLICATE_EVENT_ID")
                return Outcome(null, duplicate = true)
            p.say("Could not log it (${r.reason}).")
            return Outcome(null)
        }
        p.s["ledger"] = Js.of(r.ledger)
        val event = o(Js.of(r.event))!!
        val a = p.host.awardActivity(Js.value(event), inst["title"] as? String)
        val sched =
            o(get(p.s, "schedule"))
                ?: throw JsThrow(
                    "TypeError",
                    "Cannot read properties of null (reading 'historyInstances')",
                )
        val history =
            a(sched["historyInstances"]).takeIf(::truthy)
                ?: JsArr().also { sched["historyInstances"] = it }
        if (!truthy(sched["historyInstances"])) sched["historyInstances"] = history
        val entry =
            obj(
                "instanceId" to "adhoc-" + r.eventId,
                "taskId" to "adhoc-" + r.eventId,
                "adhoc" to true,
                "title" to inst["title"],
                "tag" to inst["tag"],
                "goalTitle" to (inst["goalTitle"].takeIf(::truthy) ?: JsonNull),
                "linkedQuestRequirementIds" to JsArr(),
                "linkedGoalIds" to JsArr(),
                "durationSlots" to ceil(minutes / SLOT_MINUTES),
                "lifecycleState" to "COMPLETED_ON_TIME",
                "completionEventId" to r.eventId,
                "hostActivityId" to (a.activityId ?: JsonNull),
                "actualMinutes" to minutes,
                "resolvedAtEpoch" to now,
                "explanation" to
                    obj(
                        "summary" to
                            "Logged for today. The session planned for " +
                                HostWords.day(num(inst["startEpoch"]), p.tz()) +
                                " is still on your plan."
                    ),
            )
        history.add(showAsDoneNow(entry, minutes, now))
        val notes = afterEvent(p, event, !unsaved(a))
        p.persist()
        val words =
            listOf(text(inst["title"]) + " logged for today", xpWords(a)) +
                notes +
                ("still planned for " + HostWords.day(num(inst["startEpoch"]), p.tz()))
        p.say(words.filter { it.isNotEmpty() }.joinToString(" · "))
        return Outcome(r)
    }
}
