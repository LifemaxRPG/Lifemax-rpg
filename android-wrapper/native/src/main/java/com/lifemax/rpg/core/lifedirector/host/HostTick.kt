package com.lifemax.rpg.core.lifedirector.host

import com.lifemax.rpg.core.lifedirector.ActivityTags
import com.lifemax.rpg.core.lifedirector.CompletionLedger
import com.lifemax.rpg.core.lifedirector.MissConsequences
import com.lifemax.rpg.core.lifedirector.host.Js.a
import com.lifemax.rpg.core.lifedirector.host.Js.assign
import com.lifemax.rpg.core.lifedirector.host.Js.at
import com.lifemax.rpg.core.lifedirector.host.Js.get
import com.lifemax.rpg.core.lifedirector.host.Js.list
import com.lifemax.rpg.core.lifedirector.host.Js.num
import com.lifemax.rpg.core.lifedirector.host.Js.o
import com.lifemax.rpg.core.lifedirector.host.Js.obj
import com.lifemax.rpg.core.lifedirector.host.Js.same
import com.lifemax.rpg.core.lifedirector.host.Js.text
import com.lifemax.rpg.core.lifedirector.host.Js.truthy
import com.lifemax.rpg.core.save.JsonNull
import java.time.Instant
import kotlin.math.floor

/**
 * The tick (`logic.js:171-275, 2900-2907`): `syncHost` (HC-70 to HC-74), `processMisses` (HC-75 to
 * HC-77), `maybeRollover` (HC-78) and the tick that runs them in that order (HC-79).
 */
object HostTick {
    private const val MISSES_PER_TICK = 12
    private const val SLOT_MINUTES = 15.0
    private const val MS_PER_SECOND = 1000.0
    private const val ROLLOVER_DAYS = 2
    private const val REGROUP =
        "Looks like life got hectic. Want to pause for 48 hours, switch to light mode for this week, or just carry on? Missed-task consequences are paused either way."

    /**
     * HC-70 to HC-74 `syncHost` (`logic.js:171-240`): (1) LifeMax activities not seen yet finish a
     * matching planned session (no XP: LifeMax paid) or become ad-hoc ledger events, and the cursor
     * moves to the latest; (2) completions undone in the LifeMax log are revoked, and a session
     * still ahead goes back on the plan; (3) automatic sessions made only of untimed quest cards
     * are retired to passive reminders, and links to completed quests are dropped. True when
     * anything changed.
     */
    fun syncHost(p: HostPage): Boolean {
        if (!p.host.connected() || !truthy(p.st?.get("schedule"))) return false
        var changed = false
        val now = p.now
        val st = p.s
        val sync = get(st, "hostSync")
        val cursor = at(sync, "cursor").let { if (it == null || it == JsonNull) null else text(it) }
        for (a in p.host.activitiesSince(cursor)) {
            val seen =
                o(get(sync, "seen"))
                    ?: throw JsThrow(
                        "TypeError",
                        "Cannot read properties of undefined (reading '${a.id}')",
                    )
            if (truthy(seen[a.id])) continue
            seen[a.id] = 1.0
            changed = true
            val ep = floor(parse(a.iso) / MS_PER_SECOND)
            val sched = p.schedule!!
            val match =
                list(sched["scheduledInstances"]).firstOrNull { i ->
                    at(i, "lifecycleState") == "PLANNED" &&
                        HostWords.dayKey(num(at(i, "startEpoch")), p.tz()) ==
                            HostWords.dayKey(ep, p.tz()) &&
                        (ActivityTags.matches(Js.json(a.tag), Js.json(at(i, "tag"))) ||
                            ActivityTags.matches(Js.json(at(i, "tag")), Js.json(a.tag)) ||
                            (!a.planDate.isNullOrEmpty() &&
                                list(get(i, "linkedQuestRequirementIds")).any {
                                    same(at(it, "questId"), "plan:" + a.planDate)
                                }))
                }
            if (match != null) {
                val minutes =
                    if (a.minutes != 0.0 && !a.minutes.isNaN()) a.minutes
                    else num(at(match, "durationSlots")) * SLOT_MINUTES
                HostCompletion.finish(
                    p,
                    o(match)!!,
                    minutes,
                    HostCompletion.FinishOptions(
                        hostActivityId = a.id,
                        awardXp = false,
                        startEpoch = ep,
                        silent = true,
                    ),
                )
            } else {
                val minutes =
                    if (a.minutes != 0.0 && !a.minutes.isNaN()) a.minutes else SLOT_MINUTES
                val r =
                    CompletionLedger.record(
                        Js.value(
                            obj(
                                "ledger" to st["ledger"],
                                "playerId" to st["playerId"],
                                "adhoc" to obj("tag" to a.tag, "title" to a.name),
                                "clientEventId" to "host:" + a.id,
                                "actualStartEpoch" to ep,
                                "actualDurationMinutes" to maxOf(1.0, minutes),
                                "nowEpoch" to now,
                            )
                        )
                    )
                if (r.applied) {
                    st["ledger"] = Js.of(r.ledger)
                    HostCompletion.afterEvent(p, o(Js.of(r.event))!!, true)
                }
            }
        }
        val all = p.host.activitiesSince(null)
        if (all.isNotEmpty()) {
            var m = if (cursorTruthy(sync)) text(at(sync, "cursor")) else ""
            for (a in all) if (a.iso > m) m = a.iso
            o(sync)!!["cursor"] = m
        }
        changed = undone(p, now) || changed
        changed = retire(p, now) || changed
        return changed
    }

    private fun cursorTruthy(sync: Any?) = truthy(at(sync, "cursor"))

    /** `Date.parse` of an ISO instant (NaN when it is not one). */
    private fun parse(iso: String): Double =
        try {
            Instant.parse(iso).toEpochMilli().toDouble()
        } catch (e: java.time.format.DateTimeParseException) {
            Double.NaN
        }

    /** HC-71 step 2 (`logic.js:188-202`). */
    private fun undone(p: HostPage, now: Double): Boolean {
        var changed = false
        val sched = p.schedule!!
        for (hv in list(sched["historyInstances"]).toList()) {
            val h = o(hv) ?: continue
            val revoked = get(p.ledger, "revoked").takeIf(::truthy) ?: JsObj()
            if (
                !truthy(h["hostActivityId"]) ||
                    !truthy(h["completionEventId"]) ||
                    truthy(at(revoked, text(h["completionEventId"])))
            )
                continue
            if (p.host.activityExists(text(h["hostActivityId"]))) continue
            val (ledger, done) =
                CompletionLedger.revoke(
                    Js.json(p.ledger),
                    Js.json(h["completionEventId"]),
                    Js.value("UNDONE_IN_LIFEMAX_LOG"),
                    Js.value(now),
                )
            if (!done) continue
            p.s["ledger"] = Js.of(ledger)
            changed = true
            h["undone"] = true
            if (num(h["endEpoch"]) > now) {
                val s = p.schedule!!
                s["historyInstances"] = Js.arrOf(list(s["historyInstances"]).filter { it !== h })
                val planned =
                    a(get(s, "scheduledInstances"))
                        ?: throw JsThrow(
                            "TypeError",
                            "Cannot read properties of undefined (reading 'push')",
                        )
                planned.add(
                    assign(
                        h,
                        obj(
                            "lifecycleState" to "PLANNED",
                            "revision" to num(h["revision"].takeIf(::truthy) ?: 0.0) + 1,
                            "completionEventId" to JsonNull,
                            "hostActivityId" to JsonNull,
                            "undone" to false,
                        ),
                    )
                )
            }
            p.say(
                "“" +
                    text(h["title"]) +
                    "” was undone in your log; Life Director updated its records."
            )
        }
        return changed
    }

    /** HC-72, HC-73 step 3 (`logic.js:203-239`). */
    private fun retire(p: HostPage, now: Double): Boolean {
        var changed = false
        val qs = p.hostQuests(now)
        val done = LinkedHashSet<Any?>()
        for (q in qs) if (at(q, "status") == "COMPLETED") done += at(q, "id")
        for (m in p.host.milestones()) done +=
            Js.of(com.lifemax.rpg.core.lifedirector.PageValues.member(m, "id"))
        val untimed = LinkedHashMap<Any?, Any?>()
        for (q in qs) if (at(q, "schedulingMode") == "NONE") untimed[at(q, "id")] = q
        val sched = p.schedule!!
        if (p.host.resolvesRetiredQuests) {
            for (inst in list(sched["scheduledInstances"])) {
                for (ref in list(at(inst, "linkedQuestRequirementIds"))) {
                    if (at(ref, "questId") in untimed) continue
                    val title = at(ref, "questTitle").takeIf(::truthy) ?: at(inst, "title")
                    val q =
                        Js.of(
                            p.host.retiredUntimedQuest(
                                Js.value(assign(o(ref), obj("questTitle" to title)))
                            )
                        )
                    if (truthy(q)) untimed[at(q, "id")] = q
                }
            }
        }
        val focus = get(get(p.s, "ui"), "focus")
        val retired =
            list(sched["scheduledInstances"]).filter { i ->
                val refs = list(at(i, "linkedQuestRequirementIds"))
                at(i, "lifecycleState") == "PLANNED" &&
                    !truthy(at(i, "isUserLocked")) &&
                    !(truthy(focus) && same(at(focus, "id"), at(i, "instanceId"))) &&
                    list(at(i, "linkedGoalIds")).isEmpty() &&
                    refs.isNotEmpty() &&
                    refs.all { at(it, "questId") in untimed }
            }
        if (retired.isNotEmpty()) {
            val ids = retired.map { at(it, "instanceId") }
            sched["scheduledInstances"] =
                Js.arrOf(
                    list(sched["scheduledInstances"]).filter { i ->
                        ids.none { same(it, at(i, "instanceId")) }
                    }
                )
            val reminders =
                a(sched["passiveReminders"]).takeIf { truthy(sched["passiveReminders"]) }
                    ?: JsArr().also { sched["passiveReminders"] = it }
            for (inst in retired) for (ref in list(at(inst, "linkedQuestRequirementIds"))) {
                val q = untimed[at(ref, "questId")]
                if (reminders.none { same(at(it, "questId"), at(q, "id")) })
                    reminders.add(
                        obj(
                            "questId" to at(q, "id"),
                            "title" to at(q, "title"),
                            "anchor" to (at(q, "anchor").takeIf(::truthy) ?: "WAKE"),
                        )
                    )
            }
            changed = true
        }
        for (iv in list(sched["scheduledInstances"]).toList()) {
            val i = o(iv) ?: continue
            val all = list(get(i, "linkedQuestRequirementIds"))
            val links = Js.arrOf(all.filter { at(it, "questId") !in done })
            if (links.size == all.size || i["lifecycleState"] != "PLANNED") continue
            changed = true
            if (
                links.isEmpty() &&
                    list(get(i, "linkedGoalIds")).isEmpty() &&
                    !truthy(i["isUserLocked"])
            )
                sched["scheduledInstances"] =
                    Js.arrOf(list(sched["scheduledInstances"]).filter { it !== i })
            else {
                i["linkedQuestRequirementIds"] = links
                if (links.isEmpty() && list(get(i, "linkedGoalIds")).isEmpty())
                    i["orphanWarning"] = true
            }
        }
        return changed
    }

    /**
     * HC-75 to HC-77 `processMisses` (`logic.js:242-268`): planned sessions past their end and the
     * grace, at most 12 a tick, each repaired locally (a failed repair records it as missed in
     * place) and then given its bounded consequence; the regroup banner when one asks for it.
     */
    fun processMisses(p: HostPage): Boolean {
        val sched0 = p.schedule ?: return false
        val prefs = p.prefs()
        val misses =
            MissConsequences.detect(
                Js.json(sched0["scheduledInstances"]),
                Js.value(p.now),
                Js.json(prefs["missGraceMinutes"]),
            )
        var n = 0
        var regroup = false
        for (miss in misses.take(MISSES_PER_TICK)) {
            val missId =
                Js.of(com.lifemax.rpg.core.lifedirector.PageValues.member(miss, "instanceId"))
            val inst =
                o(
                    list(p.schedule!!["scheduledInstances"]).firstOrNull {
                        same(at(it, "instanceId"), missId) && at(it, "lifecycleState") == "PLANNED"
                    }
                ) ?: continue
            var repaired =
                try {
                    p.reschedule(obj("type" to "MISSED", "instanceId" to inst["instanceId"])) !=
                        null
                } catch (e: RuntimeException) {
                    val s = p.schedule!!
                    s["scheduledInstances"] =
                        Js.arrOf(list(s["scheduledInstances"]).filter { it !== inst })
                    val history =
                        a(s["historyInstances"]) ?: JsArr().also { s["historyInstances"] = it }
                    history.add(
                        assign(
                            inst,
                            obj(
                                "lifecycleState" to "MISSED_UNEXPLAINED",
                                "resolvedAtEpoch" to p.now,
                            ),
                        )
                    )
                    true
                }
            val c =
                MissConsequences.apply(
                    Js.json(p.ledger),
                    Js.value(inst),
                    Js.json(p.s["prefs"]),
                    Js.value(p.now),
                    Js.value("MISS"),
                )
            p.s["ledger"] = Js.of(c.ledger)
            if (c.regroupPrompt) regroup = true
            if (repaired) {
                val h =
                    o(
                        list(get(p.schedule, "historyInstances")).firstOrNull {
                            same(at(it, "instanceId"), inst["instanceId"])
                        }
                    )
                if (h != null) {
                    h["lifecycleState"] = c.state
                    h["consequence"] = Js.of(c.consequence ?: JsonNull)
                }
            }
            repaired = false
            n++
        }
        if (regroup) p.banner = obj("kind" to "warn", "regroup" to true, "text" to REGROUP)
        if (n > 0) p.persist()
        return n > 0
    }

    /**
     * HC-78 `maybeRollover` (`logic.js:269-275`): light mode ends when its week is over; a full
     * re-plan when there is no plan, the week changed, under 2 days of plan remain, or the zone
     * changed.
     */
    fun maybeRollover(p: HostPage): Boolean {
        val now = p.now
        val st = p.s
        if (!truthy(st["setupComplete"])) return false
        if (truthy(st["prefsLightUntil"]) && now > num(st["prefsLightUntil"])) {
            val prefs =
                o(get(st, "prefs"))
                    ?: throw JsThrow(
                        "TypeError",
                        "Cannot set properties of undefined (setting 'intensityLevel')",
                    )
            prefs["intensityLevel"] = st["prefsLightRestore"].takeIf(::truthy) ?: "BALANCED"
            st["prefsLightUntil"] = JsonNull
        }
        val sched = p.schedule
        if (
            sched == null ||
                !same(st["lastGeneratedWeekKey"], HostWords.weekKey(now, p.tz())) ||
                num(sched["horizonEndEpoch"]) - now < ROLLOVER_DAYS * HostPage.DAY ||
                !same(sched["timezone"], p.tz())
        )
            return p.regenerate("weekly rollover", true)
        return false
    }

    /**
     * HC-79 the tick's body (`logic.js:2904-2906`): sync, misses, rollover; an error stops it with
     * the red banner; anything changed is saved.
     */
    fun tick(p: HostPage) {
        var changed = false
        try {
            changed = syncHost(p) || changed
            changed = processMisses(p) || changed
            changed = maybeRollover(p) || changed
        } catch (e: RuntimeException) {
            p.banner =
                obj("kind" to "bad", "text" to "Life Director hit an error: " + (e.message ?: ""))
            changed = true
        }
        if (changed) p.persist()
    }
}
