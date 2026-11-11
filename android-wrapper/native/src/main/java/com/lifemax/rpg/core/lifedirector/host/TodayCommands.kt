package com.lifemax.rpg.core.lifedirector.host

import com.lifemax.rpg.core.directorstore.PageJson
import com.lifemax.rpg.core.lifedirector.MissConsequences
import com.lifemax.rpg.core.lifedirector.timer.TimerCommands
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
import com.lifemax.rpg.core.lifedirector.host.Replies.applied
import com.lifemax.rpg.core.lifedirector.host.Replies.recorded
import com.lifemax.rpg.core.lifedirector.host.Replies.saved
import com.lifemax.rpg.core.save.JsonNull

/**
 * Today's writes from a native screen (`logic.js:3401-3605`, `Today`): done, log for today, skip
 * and its take-back, pin, move, Something came up, energy, re-plan, regroup and the banner's
 * dismissal (HC-10 to HC-35). The timer (`Today.timer`) is WP-LD10's: [TimerCommands] (TM-n).
 */
object TodayCommands {
    private const val MINUTES_MAX = 480.0
    private const val TAKE_BACK_MS = 120_000.0
    private const val SLOT_MINUTES = 15.0
    private const val HOUR = 3600.0
    private const val MINUTES_PER_DAY = 1440.0
    private const val PAUSE_HOURS = 48
    private const val LIGHT_DAYS = 7

    /** The commands this port has, by the page's names. */
    val NAMES =
        listOf(
            "done",
            "logToday",
            "skip",
            "unskip",
            "pin",
            "moveTo",
            "disrupt",
            "energy",
            "replan",
            "regroup",
            "dismissBanner",
            "timer",
        )

    /** HC-10 `nativeTodayRun(name, payload)`: the command of that name with its payload. */
    fun run(p: HostPage, name: String, payload: JsObj): CommandReply {
        if (p.st == null) p.boot()
        return when (name) {
            "done" -> done(p, payload)
            "logToday" -> logToday(p, payload)
            "skip" -> skip(p, payload)
            "unskip" -> unskip(p, payload)
            "pin" -> pin(p, payload)
            "moveTo" -> moveTo(p, payload)
            "disrupt" -> disrupt(p, payload)
            "energy" -> energy(p, payload)
            "replan" -> replan(p)
            "regroup" -> regroup(p, payload)
            "dismissBanner" -> dismissBanner(p, payload)
            "timer" -> TimerCommands.timer(p, payload)
            else -> CommandReply.CANNOT
        }
    }

    /** `plannedInst(id)`: the session of that id (as text) still on the plan. */
    private fun planned(p: HostPage, id: Any?): JsObj? =
        p.schedule?.let { s ->
            o(list(s["scheduledInstances"]).firstOrNull { same(at(it, "instanceId"), text(id)) })
        }

    /** `resolved(id)`: its history entry (the id compared as given). */
    private fun resolved(p: HostPage, id: Any?): JsObj? =
        p.schedule?.let { s ->
            o(list(s["historyInstances"]).firstOrNull { same(at(it, "instanceId"), id) })
        }

    /**
     * HC-11 `minutesOf`: the minutes given, rounded and kept to 1..480; 0 or none is the plan's.
     */
    private fun minutesOf(inst: JsObj, m: Any?): Double {
        val r = Js.round(num(m))
        val v = if (r == 0.0 || r.isNaN()) num(inst["durationSlots"]) * SLOT_MINUTES else r
        return maxOf(1.0, minOf(MINUTES_MAX, v))
    }

    private fun refusedFinish(p: HostPage): CommandReply =
        if (p.lastFinishRefusal == "DUPLICATE_EVENT_ID") recorded(p, "Already recorded.")
        else
            CommandReply.no("Cannot complete this block (" + (p.lastFinishRefusal ?: "null") + ").")

    /** HC-10 to HC-14 `done({id, minutes})`. */
    private fun done(p: HostPage, x: JsObj): CommandReply {
        val inst = planned(p, x["id"])
        if (inst == null) {
            val h = resolved(p, x["id"])
            return if (h != null && text(h["lifecycleState"]).contains("COMPLETED"))
                recorded(p, "Already recorded.")
            else CommandReply.GONE
        }
        if (inst["lifecycleState"] != "PLANNED") return CommandReply.GONE
        if (!HostCompletion.doable(p, inst, p.now))
            return CommandReply.no("You can mark it done on the day, or from 6 hours before.")
        val r =
            HostCompletion.finish(
                p,
                inst,
                minutesOf(inst, x["minutes"]),
                HostCompletion.FinishOptions(quietRefusal = true),
            )
        if (r.recorded == null) return refusedFinish(p)
        val focus = get(get(p.s, "ui"), "focus")
        if (truthy(focus) && same(at(focus, "id"), inst["instanceId"])) {
            o(p.s["ui"])!!["focus"] = JsonNull
            p.persist()
        }
        return saved(p, "")
    }

    /** HC-15 `logToday({id, minutes, op})`. */
    private fun logToday(p: HostPage, x: JsObj): CommandReply {
        val inst = planned(p, x["id"]) ?: return CommandReply.GONE
        if (!truthy(x["op"])) return CommandReply.no("Nothing was logged.")
        val r = HostCompletion.logToday(p, inst, minutesOf(inst, x["minutes"]), text(x["op"]))
        if (r.duplicate) return recorded(p, "Already recorded.")
        if (r.recorded == null) return CommandReply.no("Nothing was logged.")
        return saved(p, "")
    }

    /** HC-16 `skip({id})`: the miss consequence of a skip, the repair, the take-back. */
    private fun skip(p: HostPage, x: JsObj): CommandReply {
        val id = x["id"]
        val inst = planned(p, id)
        if (inst == null) {
            val h = resolved(p, id)
            if (!(h != null && text(h["lifecycleState"]).contains("SKIPPED")))
                return CommandReply.GONE
            val r = recorded(p, "Already skipped.")
            val t = p.takeBack
            return if (t != null && t.id == text(id) && r.ok) r.copy(undo = t.text) else r
        }
        if (inst["lifecycleState"] != "PLANNED") return CommandReply.GONE
        val focus = get(get(p.s, "ui"), "focus")
        if (truthy(focus) && same(at(focus, "id"), inst["instanceId"]))
            return CommandReply.no(HostWords.REMOVE_WHY.getValue("running"))
        val before = obj("schedule" to Js.copy(p.s["schedule"]), "ledger" to Js.copy(p.s["ledger"]))
        val c =
            MissConsequences.apply(
                Js.json(p.s["ledger"]),
                Js.value(inst),
                Js.json(p.s["prefs"]),
                Js.value(p.now),
                Js.value("SKIP"),
            )
        p.s["ledger"] = Js.of(c.ledger)
        val r = p.reschedule(obj("type" to "SKIPPED", "instanceId" to id))
        p.persist()
        val token = at(Js.of(c.consequence), "type") == "REST_TOKEN_SPENT"
        val text =
            "Skipped" +
                (if (token) " · used a rest token" else "") +
                (if (r != null) " · " + p.summarize(r) else "")
        p.say(text)
        p.takeBack = TakeBack(text(id), text, p.nowMs, before, planJson(p))
        return saved(p, "").copy(changed = true, undo = text)
    }

    private fun planJson(p: HostPage): String = PageJson.text(Js.value(p.s["schedule"]))

    /**
     * HC-17 `unskip({id, op})`: the plan and ledger before the Skip, while nothing changed since.
     */
    private fun unskip(p: HostPage, x: JsObj): CommandReply {
        val op = x["op"]
        if (truthy(op) && same(op, p.takenBackOp)) return recorded(p, "Already back on the plan.")
        val t = p.takeBack
        if (t == null || t.id != text(x["id"]))
            return if (planned(p, x["id"]) != null) recorded(p, "Already back on the plan.")
            else CommandReply.no("That can no longer be taken back.")
        if (p.nowMs - t.at > TAKE_BACK_MS || planJson(p) != t.left)
            return CommandReply.no("The plan has changed since, so that cannot be taken back.")
        p.s["schedule"] = t.before["schedule"]
        p.s["ledger"] = t.before["ledger"]
        p.takeBack = null
        p.takenBackOp = if (truthy(op)) op else JsonNull
        p.changeLog().add(obj("at" to p.now, "label" to "Skip taken back"))
        p.persist()
        p.say("Back on the plan")
        return applied(p)
    }

    /** HC-18 `pin({id, on})`. */
    private fun pin(p: HostPage, x: JsObj): CommandReply {
        val inst = planned(p, x["id"]) ?: return CommandReply.GONE
        val want = truthy(x["on"])
        if (inst["isUserLocked"] == want) return recorded(p, "")
        inst["isUserLocked"] = want
        val explanation = inst["explanation"]
        if (truthy(explanation)) {
            val summary =
                if (want) "You pinned this time, so Life Director never moves it automatically."
                else at(explanation, "summary")
            inst["explanation"] = assign(o(explanation), obj("summary" to summary))
        }
        p.persist()
        p.say(if (want) "Pinned" else "Unpinned")
        return saved(p, "")
    }

    /** HC-19 `moveTo({id, start})`: a move, pinned, by the engine's repair. */
    private fun moveTo(p: HostPage, x: JsObj): CommandReply {
        val inst = planned(p, x["id"])
        val at = num(x["start"])
        if (inst == null) return CommandReply.GONE
        if (!Js.isInteger(at)) return CommandReply.no("That time is not on the plan.")
        if (
            num(inst["startEpoch"]) == at &&
                inst["startEpoch"] is Double &&
                truthy(inst["isUserLocked"])
        )
            return recorded(p, "")
        val r =
            p.reschedule(obj("type" to "MOVE", "instanceId" to x["id"], "newStartEpoch" to at))
                ?: return CommandReply.no("It does not fit there. Nothing was moved.")
        p.persist()
        p.say("Moved and pinned · " + p.summarize(r))
        return saved(p, "")
    }

    /** `dayMinute(v)` (`logic.js:3413`): a whole minute of the day, else null. */
    fun dayMinute(v: Any?): Double? =
        if (v is Double && Js.isInteger(v) && v >= 0 && v < MINUTES_PER_DAY) v else null

    /** `offered(part, v)`: one of the part's options, compared as text. */
    private fun offered(kind: String, v: Any?): Boolean =
        HostWords.DISRUPT_OPTIONS[kind]?.any { o ->
            (if (o is Double) Js.numText(o) else o.toString()) == text(v)
        } == true

    private val DATE = Regex("""^\d{4}-\d{2}-\d{2}$""")

    /** HC-30, HC-31 `disrupt({kind, op, date, from, until, commute, hours, days})`. */
    private fun disrupt(p: HostPage, x: JsObj): CommandReply {
        val kind = x["kind"] as? String
        val now = p.now
        if (kind == null || kind !in HostWords.DISRUPT_KINDS) return CommandReply.CANNOT
        HostWords.dayKey(now, p.tz()) // disruptParts reads the day (an unknown zone throws there)
        if (!truthy(x["op"])) return CommandReply.no("Nothing was added.")
        val id = HostWords.DISRUPT_ID.getValue(kind) + "-" + text(x["op"])
        val taken =
            list(p.s["fixedCommitments"].takeIf(::truthy)) +
                list(get(p.s["availability"], "blackouts").takeIf(::truthy))
        if (taken.any { text(at(it, "id")) == id }) return recorded(p, "Already added")
        if (
            kind in HostWords.DISRUPT_OPTIONS &&
                !offered(kind, if (kind == "block") x["hours"] else x["days"])
        )
            return CommandReply.no("That is not one of the choices.")
        val z = p.tz()
        val commitment: JsObj
        val type: String
        when (kind) {
            "work" -> {
                val date = x["date"]
                val pieces = text(date.takeIf(::truthy) ?: "").split("-").map { Js.num(it) }
                val s0 = dayMinute(x["from"])
                val e0 = dayMinute(x["until"])
                if (!DATE.matches(text(date)) || s0 == null || e0 == null)
                    return CommandReply.no("Enter a date, start and end")
                val (y, mo, d) = Triple(pieces[0], pieces[1], pieces[2])
                type = "NEW_COMMITMENT"
                commitment =
                    obj(
                        "id" to id,
                        "title" to "Extra shift",
                        "startEpoch" to HostWords.zoned(y, mo, d, s0, z),
                        "endEpoch" to
                            HostWords.zoned(
                                y,
                                mo,
                                d,
                                if (e0 <= s0) e0 + MINUTES_PER_DAY else e0,
                                z,
                            ),
                        "commuteMin" to
                            maxOf(
                                0.0,
                                num(x["commute"]).takeUnless { it == 0.0 || it.isNaN() } ?: 0.0,
                            ),
                        "kind" to "WORK",
                    )
            }
            "block" -> {
                type = "BLOCK_HOURS"
                commitment =
                    obj(
                        "id" to id,
                        "title" to "Unavailable",
                        "startEpoch" to now,
                        "endEpoch" to now + num(x["hours"]) * HOUR,
                    )
            }
            "day" -> {
                val nx = HostWords.plusDays(HostWords.parts(now, z).date, 1)
                type = "BLACKOUT_DAY"
                commitment =
                    obj(
                        "id" to id,
                        "title" to "Day off the plan",
                        "startEpoch" to now,
                        "endEpoch" to
                            HostWords.zoned(
                                nx.year.toDouble(),
                                nx.month.toDouble(),
                                nx.day.toDouble(),
                                0.0,
                                z,
                            ),
                    )
            }
            else -> {
                type = "SICK"
                commitment =
                    obj(
                        "id" to id,
                        "title" to "Sick",
                        "startEpoch" to now,
                        "endEpoch" to now + num(x["days"]) * HostPage.DAY,
                    )
            }
        }
        val told = p.notice?.id
        val r = p.reschedule(obj("type" to type, "commitment" to commitment))
        if (r == null) {
            val n = p.notice
            val message =
                if (n != null && n.id != told) n.text
                else p.banner?.get("text")?.takeIf(::truthy)?.let(::text) ?: "Nothing was changed."
            return CommandReply(false, message, said = true)
        }
        p.persist()
        p.say((if (kind == "sick") "Rest up · " else "") + p.summarize(r))
        return applied(p)
    }

    /** HC-32 `energy({level})`: the check-in, then a re-plan. */
    private fun energy(p: HostPage, x: JsObj): CommandReply {
        if (!offered("energy", x["level"]))
            return CommandReply.no("That is not one of the choices.")
        p.s["energyCheckin"] = obj("level" to text(x["level"]), "epoch" to p.now)
        p.regenerate("energy check-in", true)
        p.say("Energy noted")
        return applied(p)
    }

    /** HC-33 `replan()`: a red banner goes; a refused plan answers with its banner. */
    private fun replan(p: HostPage): CommandReply {
        if (p.banner?.get("kind") == "bad") p.banner = null
        val ok = p.regenerate("requested", false)
        return if (ok) applied(p)
        else
            CommandReply.no(
                p.banner?.get("text")?.takeIf(::truthy)?.let(::text) ?: "Nothing was changed."
            )
    }

    /** HC-34 `regroup({v, op})`: pause 48 hours, light mode for 7 days, or carry on. */
    private fun regroup(p: HostPage, x: JsObj): CommandReply {
        val now = p.now
        val v = x["v"]
        val id = "p-" + text(x["op"])
        if (v !in listOf("pause", "light", "go")) return CommandReply.CANNOT
        if (v == "pause" && !truthy(x["op"])) return CommandReply.no("Nothing was changed.")
        if (!(p.banner != null && truthy(p.banner!!["regroup"]))) {
            val landed =
                when (v) {
                    "pause" ->
                        list(get(p.s["availability"], "blackouts").takeIf(::truthy)).any {
                            same(at(it, "id"), id)
                        }
                    "light" ->
                        p.prefs()["intensityLevel"] == "LIGHT" && num(p.s["prefsLightUntil"]) > now
                    else -> !truthy(get(p.ledger, "consecutiveMisses"))
                }
            return if (landed) recorded(p, if (v == "pause") "Already added" else "")
            else CommandReply.no("That question is no longer showing.")
        }
        if (v == "pause")
            p.reschedule(
                obj(
                    "type" to "BLACKOUT_DAY",
                    "commitment" to
                        obj(
                            "id" to id,
                            "title" to "Paused",
                            "startEpoch" to now,
                            "endEpoch" to now + PAUSE_HOURS * HOUR,
                        ),
                )
            )
        if (v == "light") {
            p.s["prefsLightRestore"] = p.prefs()["intensityLevel"]
            val prefs =
                o(get(p.s, "prefs"))
                    ?: throw JsThrow(
                        "TypeError",
                        "Cannot set properties of undefined (setting 'intensityLevel')",
                    )
            prefs["intensityLevel"] = "LIGHT"
            p.s["prefsLightUntil"] = now + LIGHT_DAYS * HostPage.DAY
            p.regenerate("light mode", true)
        }
        p.s["ledger"] = assign(o(p.s["ledger"]), obj("consecutiveMisses" to 0.0))
        p.banner = null
        p.persist()
        return applied(p)
    }

    /**
     * HC-35 `dismissBanner({text})`: only the banner the screen showed; the regroup question stays.
     */
    private fun dismissBanner(p: HostPage, x: JsObj): CommandReply {
        val b = p.banner
        val shown = text(x["text"].takeIf(::truthy) ?: "")
        if (b == null || !same(b["text"], shown)) return CommandReply(true, "", changed = false)
        if (truthy(b["regroup"])) return CommandReply.no("Choose one of the answers.")
        p.banner = null
        return CommandReply(true, "", changed = true)
    }
}
