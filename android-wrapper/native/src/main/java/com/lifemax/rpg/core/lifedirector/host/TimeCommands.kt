package com.lifemax.rpg.core.lifedirector.host

import com.lifemax.rpg.core.directorstore.PageJson
import com.lifemax.rpg.core.lifedirector.Availability
import com.lifemax.rpg.core.lifedirector.CalendarMath
import com.lifemax.rpg.core.lifedirector.Reschedule
import com.lifemax.rpg.core.lifedirector.host.Js.a
import com.lifemax.rpg.core.lifedirector.host.Js.at
import com.lifemax.rpg.core.lifedirector.host.Js.get
import com.lifemax.rpg.core.lifedirector.host.Js.list
import com.lifemax.rpg.core.lifedirector.host.Js.num
import com.lifemax.rpg.core.lifedirector.host.Js.o
import com.lifemax.rpg.core.lifedirector.host.Js.obj
import com.lifemax.rpg.core.lifedirector.host.Js.same
import com.lifemax.rpg.core.lifedirector.host.Js.text
import com.lifemax.rpg.core.lifedirector.host.Js.truthy
import com.lifemax.rpg.core.lifedirector.host.Replies.applied
import com.lifemax.rpg.core.lifedirector.host.Replies.recorded
import com.lifemax.rpg.core.lifedirector.host.Replies.saved
import com.lifemax.rpg.core.lifedirector.host.TodayCommands.dayMinute
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonString

/**
 * My time's commands (`logic.js:2411-2523`): sleep (HC-60), commitments (HC-61), routines and meals
 * (HC-58), a pinned activity at a time (HC-63) and the end of set-up (HC-62); and the dial's
 * `applyDial` (`logic.js:1883-1900`, HC-80 to HC-82).
 */
object TimeCommands {
    private const val DAY_MINUTES = 1440.0
    private const val SHORTEST_SLEEP = 300.0
    private const val DEFAULT_SLEEP = 480.0
    private const val MAX_COMMUTE = 240.0
    private const val MAX_UNWIND = 120.0
    private const val DEFAULT_ROTATION = 4.0
    private const val MAX_OP = 128
    private const val MAX_TITLE = 80
    private const val SLOT = 15.0
    private const val LONGEST = 180.0
    private const val MINUTE = 60.0
    private val DATE = Regex("""^\d{4}-\d{2}-\d{2}$""")
    private val BLOCKED =
        setOf(
            "SLEEP",
            "WORK",
            "CLASS",
            "APPOINTMENT",
            "COMMUTE",
            "DECOMPRESS",
            "BLACKOUT",
            "SICK",
            "EMERGENCY",
            "UNAVAILABLE",
        )
    private val WEEK = listOf(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0)

    /** The command of that name, or null when it is not one of these. */
    fun run(p: HostPage, name: String, x: JsObj): CommandReply? =
        when (name) {
            "sleepSave" -> sleepSave(p, x)
            "ruleSave" -> ruleSave(p, x)
            "ruleRemove" -> ruleRemove(p, x)
            "routine" -> routine(p, x)
            "meals" -> meals(p, x)
            "activityAtAdd" -> activityAtAdd(p, x)
            "finishSetup" -> finishSetup(p)
            else -> null
        }

    private fun availability(p: HostPage): JsObj =
        o(get(p.s, "availability"))
            ?: throw JsThrow("TypeError", "Cannot read properties of undefined (reading 'rules')")

    private fun rules(p: HostPage): JsArr =
        a(get(availability(p), "rules"))
            ?: throw JsThrow("TypeError", "Cannot read properties of undefined (reading 'filter')")

    private fun wrap(span: Double, fallback: Double): Double =
        ((span + DAY_MINUTES) % DAY_MINUTES).let { if (it == 0.0 || it.isNaN()) fallback else it }

    private fun sleep(id: String, days: List<Double>, start: Double, duration: Double): JsObj =
        obj(
            "id" to id,
            "kind" to "SLEEP",
            "title" to "Sleep",
            "days" to Js.arrOf(days),
            "startMin" to start,
            "durationMin" to duration,
        )

    /**
     * HC-60 `sleepSave({bed, wake, weekend})`: the SLEEP rules and the sleep need, then a re-plan.
     */
    private fun sleepSave(p: HostPage, x: JsObj): CommandReply {
        val b = dayMinute(x["bed"])
        val w = dayMinute(x["wake"])
        val weekend = x["weekend"]
        val we =
            if (truthy(weekend))
                listOf(dayMinute(at(weekend, "bed")), dayMinute(at(weekend, "wake")))
            else null
        if (b == null || w == null || (we != null && (we[0] == null || we[1] == null)))
            return CommandReply.refuse("Enter both times")
        val dur = wrap(w - b, DEFAULT_SLEEP)
        if (dur < SHORTEST_SLEEP)
            return CommandReply.refuse("That is under 5 hours of sleep. Please check the times.")
        val rules =
            if (we == null) Js.arr(sleep("sleep-wk", WEEK, b, dur))
            else
                Js.arr(
                    sleep("sleep-wk", WEEK.take(5), b, dur),
                    sleep("sleep-we", WEEK.drop(5), we[0]!!, wrap(we[1]!! - we[0]!!, DEFAULT_SLEEP)),
                )
        val av = availability(p)
        val current = Js.arrOf(rules(p).filter { at(it, "kind") == "SLEEP" })
        if (
            PageJson.text(Js.value(rules)) == PageJson.text(Js.value(current)) &&
                same(av["sleepRequirementMinutes"], dur)
        )
            return recorded(p, "")
        av["rules"] = Js.arrOf(rules(p).filter { at(it, "kind") != "SLEEP" } + rules)
        av["sleepRequirementMinutes"] = dur
        p.autoReplan("sleep changed")
        p.say(if (truthy(p.s["setupComplete"])) "Sleep saved · plan rebuilt" else "Sleep saved")
        return applied(p)
    }

    /**
     * HC-61 `ruleSave(form)`: a commitment added (named from op) or changed (by id), then a
     * re-plan.
     */
    private fun ruleSave(p: HostPage, x: JsObj): CommandReply {
        val s0 = dayMinute(x["start"])
        val e0 = dayMinute(x["end"])
        val rot = x["rotating"] == true
        if (s0 == null || e0 == null) return CommandReply.refuse("Enter start and end times")
        val picked =
            a(x["days"])?.filter { Js.isInteger(it) && num(it) >= 0 && num(it) <= 6 } ?: emptyList()
        if (!rot && picked.isEmpty()) return CommandReply.refuse("Pick at least one day")
        val anchor = x["anchor"]
        if (rot && !DATE.matches(text(anchor.takeIf(::truthy) ?: "")))
            return CommandReply.refuse("Pick the first day on")
        val kind = x["kind"]
        val load = x["load"]
        if (kind !in HostWords.RULE_KINDS || load !in HostWords.RULE_LOADS)
            return CommandReply.refuse("That is not one of the choices.")
        val id = x["id"]
        val was =
            if (truthy(id))
                o(
                    rules(p).firstOrNull {
                        same(at(it, "id"), text(id)) &&
                            at(it, "kind") != "SLEEP" &&
                            at(it, "kind") != "PREFERRED"
                    }
                )
            else null
        if (truthy(id) && was == null) return CommandReply.refuse("That commitment was removed.")
        if (!truthy(id) && !truthy(x["op"])) return CommandReply.refuse(DoCommands.NO_OP)
        val rid = if (truthy(id)) text(id) else "r-" + text(x["op"])
        if (!truthy(id) && rules(p).any { same(at(it, "id"), rid) })
            return recorded(p, "Already added")
        val n = maxOf(1.0, or(num(x["on"]), DEFAULT_ROTATION))
        val f = maxOf(1.0, or(num(x["off"]), DEFAULT_ROTATION))
        val rule =
            obj(
                "id" to rid,
                "kind" to kind,
                "title" to
                    DoCommands.trim(text(x["title"].takeIf(::truthy) ?: "")).ifEmpty {
                        HostWords.KIND_LABEL.getValue(kind as String)
                    },
                "days" to if (rot) Js.arrOf(WEEK) else Js.arrOf(picked),
                "startMin" to s0,
                "durationMin" to wrap(e0 - s0, DAY_MINUTES),
                "commuteMin" to maxOf(0.0, minOf(MAX_COMMUTE, or(num(x["commute"]), 0.0))),
                "decompressMin" to maxOf(0.0, minOf(MAX_UNWIND, or(num(x["unwind"]), 0.0))),
                "load" to load,
                "rotation" to
                    if (rot)
                        obj(
                            "anchorDate" to text(anchor),
                            "cycleLength" to n + f,
                            "activeDays" to Js.arrOf((0 until n.toInt()).map { it.toDouble() }),
                        )
                    else JsonNull,
            )
        if (was != null && PageJson.text(Js.value(was)) == PageJson.text(Js.value(rule)))
            return recorded(p, "")
        val list = rules(p)
        val at = list.indexOfFirst { same(at(it, "id"), rid) }
        if (at >= 0) list[at] = rule else list.add(rule)
        p.autoReplan(if (at >= 0) "commitment changed" else "commitment added")
        val rebuilt = if (truthy(p.s["setupComplete"])) " · plan rebuilt" else ""
        p.say(if (at >= 0) "Saved$rebuilt" else "Commitment added$rebuilt")
        return applied(p)
    }

    private fun or(v: Double, fallback: Double): Double = if (v == 0.0 || v.isNaN()) fallback else v

    /** HC-61 `ruleRemove({id})`: a commitment removed; one already gone changes nothing. */
    private fun ruleRemove(p: HostPage, x: JsObj): CommandReply {
        val r =
            o(rules(p).firstOrNull { same(at(it, "id"), text(x["id"])) }) ?: return recorded(p, "")
        if (r["kind"] == "SLEEP" || r["kind"] == "PREFERRED")
            return CommandReply.refuse("That is not a commitment.")
        availability(p)["rules"] = Js.arrOf(rules(p).filter { !same(at(it, "id"), r["id"]) })
        p.autoReplan("commitment removed")
        p.say("Removed")
        return applied(p)
    }

    private fun prefsObj(p: HostPage): JsObj =
        o(get(p.s, "prefs")) ?: throw JsThrow("TypeError", "Cannot set properties of undefined")

    /** HC-58 `routine({key, value})`: a routine's minutes, kept within its stepper. */
    private fun routine(p: HostPage, x: JsObj): CommandReply {
        val r = HostWords.ROUTINES.firstOrNull { it.key == x["key"] }
        val v = num(x["value"])
        if (r == null || !v.isFinite()) return CommandReply.refuse("That is not a setting.")
        val want = maxOf(r.low, minOf(r.high, Js.round(v)))
        if (same(p.prefs()[r.key], want)) return recorded(p, "")
        prefsObj(p)[r.key] = want
        p.autoReplan("routines changed")
        return applied(p)
    }

    /** HC-58 `meals({on})`. */
    private fun meals(p: HostPage, x: JsObj): CommandReply {
        val has = truthy(p.prefs()["mealsEnabled"])
        val want = x["on"] as? Boolean ?: !has
        if (want == has) return recorded(p, "")
        prefsObj(p)["mealsEnabled"] = want
        p.autoReplan("routines changed")
        return applied(p)
    }

    /** HC-62 `finishSetup()`: needs a SLEEP rule; the first plan. */
    private fun finishSetup(p: HostPage): CommandReply {
        if (rules(p).none { at(it, "kind") == "SLEEP" })
            return CommandReply.refuse("Save your sleep times first")
        p.s["setupComplete"] = true
        if (p.regenerate("setup", false)) {
            p.tab = "today"
            (o(get(p.s, "ui"))
                ?: throw JsThrow(
                    "TypeError",
                    "Cannot set properties of undefined (setting 'tab')",
                ))["tab"] = "today"
            p.dayIdx = 0.0
        }
        p.persist()
        return applied(p)
    }

    /**
     * HC-63 `activityAtAdd({op, title, tag, date, start, minutes})`: a session pinned at a local
     * time of the planned horizon, clear of sleep, work and other sessions; named from op.
     */
    private fun activityAtAdd(p: HostPage, x: JsObj): CommandReply {
        val op = x["op"]
        if (op !is String || op.isEmpty() || op.length > MAX_OP)
            return CommandReply.refuse(DoCommands.NO_OP)
        val id = "manual-$op"
        val s = p.schedule ?: return CommandReply.refuse("Make a plan for this day first.")
        val all =
            list(s["scheduledInstances"].takeIf(::truthy)) +
                list(s["historyInstances"].takeIf(::truthy))
        if (all.any { same(at(it, "instanceId"), id) }) return recorded(p, "Already added")
        val name = DoCommands.trim(text(x["title"].takeIf(::truthy) ?: ""))
        val minute = dayMinute(x["start"])
        val duration = num(x["minutes"])
        if (name.isEmpty() || name.length > MAX_TITLE)
            return CommandReply.refuse("Give the activity a name (up to 80 characters).")
        val tag = x["tag"]
        if (tag !in HostWords.CUSTOM_TAGS) return CommandReply.refuse("Choose an activity type.")
        val date = text(x["date"])
        if (
            !DATE.matches(date) ||
                minute == null ||
                minute % SLOT != 0.0 ||
                !Js.isInteger(duration) ||
                duration < SLOT ||
                duration > LONGEST ||
                duration % SLOT != 0.0
        )
            return CommandReply.refuse(
                "Choose a date, a 15-minute start time and a duration from 15 to 180 minutes."
            )
        val (y, mo, d) = date.split("-").map { Js.num(it) }
        val z = p.tz()
        val epoch = HostWords.zoned(y, mo, d, minute, z)
        val parts = HostWords.parts(epoch, z)
        val end = epoch + duration * MINUTE
        if (
            CalendarMath.key(parts.date.fields) != date ||
                (parts.hour * 60 + parts.minute).toDouble() != minute
        )
            return CommandReply.refuse("That local time does not exist. Choose another time.")
        if (epoch < p.now)
            return CommandReply.refuse(
                "Choose an upcoming time. Use Log activity for something already done."
            )
        if (epoch < num(s["horizonStartEpoch"]) || end > num(s["horizonEndEpoch"]))
            return CommandReply.refuse(
                "This day has not been planned yet. Make a plan for it before adding activities."
            )
        val grid =
            Availability.derive(
                    Js.value(
                        obj(
                            "timezone" to z,
                            "horizonStartEpoch" to epoch,
                            "horizonDays" to 2.0,
                            "playerAvailability" to p.s["availability"],
                            "fixedCommitments" to
                                (p.s["fixedCommitments"].takeIf(::truthy) ?: JsArr()),
                            "playerPreferences" to p.s["prefs"],
                        )
                    )
                )
                .grid
        val clash =
            grid.blocks.any { b ->
                (b.meta?.kind as? JsonString)?.value in BLOCKED &&
                    b.startEpoch < end &&
                    b.endEpoch > epoch
            } ||
                list(s["scheduledInstances"].takeIf(::truthy)).any {
                    num(at(it, "startEpoch")) < end &&
                        num(at(it, "endEpoch")) > epoch &&
                        !Regex("CANCELLED|EXCUSED").containsMatchIn(text(at(it, "lifecycleState")))
                }
        if (clash)
            return CommandReply.refuse(
                "That time overlaps sleep, work or another activity. Choose a free time."
            )
        val was = p.st
        val next = o(Js.copy(p.st))!!
        val sched = o(next["schedule"])!!
        val planned =
            a(sched["scheduledInstances"])
                ?: throw JsThrow(
                    "TypeError",
                    "Cannot read properties of undefined (reading 'push')",
                )
        val slots = duration / SLOT
        planned.add(
            obj(
                "instanceId" to id,
                "taskId" to id,
                "templateId" to id,
                "title" to name,
                "tag" to tag,
                "kind" to "CUSTOM",
                "goalId" to JsonNull,
                "goalTitle" to JsonNull,
                "linkedGoalIds" to JsArr(),
                "linkedQuestRequirementIds" to JsArr(),
                "startEpoch" to epoch,
                "endEpoch" to end,
                "durationSlots" to slots,
                "minDurationSlots" to slots,
                "optDurationSlots" to slots,
                "tier" to 2.0,
                "lifecycleState" to "PLANNED",
                "isUserLocked" to true,
                "bwcId" to "manual",
                "IP" to 2.0,
                "IC" to 2.0,
                "spacingKey" to id,
                "compressed" to false,
                "origStartEpoch" to epoch,
                "explanation" to
                    obj("summary" to "Scheduled by you at this time.", "locked" to true),
                "moves" to 0.0,
                "source" to JsonNull,
            )
        )
        val sorted =
            planned.sortedWith { l, r ->
                (num(at(l, "startEpoch")) - num(at(r, "startEpoch"))).let {
                    if (it.isNaN()) 0 else it.compareTo(0.0)
                }
            }
        planned.clear()
        planned.addAll(sorted)
        p.st = next
        if (!p.persist()) {
            p.st = was
            return CommandReply(
                false,
                "Activity could not be saved. Nothing was added.",
                changed = false,
            )
        }
        p.say("Activity added")
        return applied(p)
    }

    /**
     * HC-80 to HC-82 `applyDial(now)` with the change [pending] (`ui.pendingDial`): the rule takes
     * the new start and length; before set-up only saved; after it the plan is repaired around it
     * (`TIME_CHANGED`), and a refused plan puts the rule back.
     */
    fun applyDial(p: HostPage, pending: JsObj?, now: Double): CommandReply {
        val r =
            if (pending != null) o(rules(p).firstOrNull { same(at(it, "id"), pending["ruleId"]) })
            else null
        p.pendingDial = null
        if (r == null || pending == null)
            return CommandReply(
                false,
                "There is no change to make. Nothing was changed.",
                changed = false,
                moved = false,
            )
        val before = obj("startMin" to r["startMin"], "durationMin" to r["durationMin"])
        r["startMin"] = pending["startMin"]
        r["durationMin"] = pending["durationMin"]
        if (!truthy(p.s["setupComplete"])) {
            val saved = p.persist()
            return CommandReply(
                saved,
                if (saved) "" else bannerText(p),
                changed = true,
                moved = false,
            )
        }
        val old = p.schedule
        val out =
            o(
                Js.of(
                    Reschedule.run(
                            Js.value(
                                obj(
                                    "engineInput" to p.buildInput(now),
                                    "currentOutput" to p.s["schedule"],
                                    "trigger" to obj("type" to "TIME_CHANGED"),
                                )
                            )
                        )
                        .updatedSchedule
                )
            ) ?: JsObj()
        if (!p.applyOutput(out, "Time adjusted")) {
            r.putAll(before)
            p.persist()
            return CommandReply(false, bannerText(p), changed = false, moved = false)
        }
        val positions = LinkedHashMap<Any?, String>()
        for (i in list(old?.get("scheduledInstances"))) positions[at(i, "instanceId")] = spot(i)
        val after = list(out["scheduledInstances"])
        val moved =
            after.any { positions[at(it, "instanceId")] != spot(it) } ||
                positions.size != after.size
        val saved = p.persist()
        val message =
            HostWords.handleLabel(r["kind"], r["title"], text(pending["end"])) +
                " now " +
                HostWords.hm(num(pending["to"])) +
                if (moved) " · schedule adjusted" else " · sessions kept in place"
        if (saved) p.say(message)
        return CommandReply(
            saved,
            if (saved) message else bannerText(p),
            changed = true,
            moved = moved,
        )
    }

    private fun spot(i: Any?): String = text(at(i, "startEpoch")) + ":" + text(at(i, "endEpoch"))

    private fun bannerText(p: HostPage): String =
        text(
            get(
                p.banner
                    ?: throw JsThrow(
                        "TypeError",
                        "Cannot read properties of null (reading 'text')",
                    ),
                "text",
            )
        )
}
