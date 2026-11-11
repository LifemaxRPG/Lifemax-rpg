package com.lifemax.rpg.core.lifedirector.host

import com.lifemax.rpg.core.lifedirector.CompletionLedger
import com.lifemax.rpg.core.lifedirector.PlanPreferences
import com.lifemax.rpg.core.lifedirector.PlanSchedule
import com.lifemax.rpg.core.lifedirector.Reschedule
import com.lifemax.rpg.core.lifedirector.host.Js.a
import com.lifemax.rpg.core.lifedirector.host.Js.assign
import com.lifemax.rpg.core.lifedirector.host.Js.at
import com.lifemax.rpg.core.lifedirector.host.Js.get
import com.lifemax.rpg.core.lifedirector.host.Js.list
import com.lifemax.rpg.core.lifedirector.host.Js.num
import com.lifemax.rpg.core.lifedirector.host.Js.o
import com.lifemax.rpg.core.lifedirector.host.Js.obj
import com.lifemax.rpg.core.lifedirector.host.Js.text
import com.lifemax.rpg.core.lifedirector.host.Js.truthy
import com.lifemax.rpg.core.save.JsonNull

/** The page's notice (`ui.notice`, `logic.js:28`): the last message said, its number and time. */
data class Notice(val id: Double, val text: String, val at: Double)

/**
 * The Skip's take-back (`logic.js:3402`): the session, the words, when (milliseconds), the plan and
 * ledger before the Skip, and the plan as the Skip left it (its `JSON.stringify` text).
 */
class TakeBack(
    val id: String,
    val text: String,
    val at: Double,
    val before: JsObj,
    val left: String,
)

/**
 * A repair `reschedule` applied (`logic.js:149-161`): the engine's input after it and the record.
 */
class Repair(val engineInput: JsObj?, val changeRecord: JsObj?)

/**
 * The page's memory while commands run (HC-2): `ST` and the controller's own variables that a
 * command reads or leaves (`ui.banner`, `ui.notice`, `ui.tab`, `ui.dayIdx`, `ui.pendingDial`, the
 * Skip's take-back, the last save, the last refused completion), over the [host] at the clock
 * [nowMs] (milliseconds, as `Date.now()`). Commands change it in place, as the page does.
 */
class HostPage(val host: HostEffects, var nowMs: Double) {
    var st: JsObj? = null
    var banner: JsObj? = null
    var notice: Notice? = null
    var tab: String = "today"
    var dayIdx: Double = 0.0
    var pendingDial: JsObj? = null
    var takeBack: TakeBack? = null
    var takenBackOp: Any? = JsonNull
    var lastSave: SaveOutcome = SaveOutcome.OK
    var lastFinishRefusal: String? = null

    /** `ST`, which every command but boot needs. */
    val s: JsObj
        get() = st ?: throw JsThrow("TypeError", "Cannot read properties of null")

    /** `nowEpoch()`: whole seconds of the clock. */
    val now: Double
        get() = HostWords.seconds(nowMs)

    /** `tz()` (`logic.js:14`): the plan's zone, else the device's. */
    fun tz(): String {
        val z = st?.let { at(get(it, "availability"), "timezone") }
        return if (truthy(z)) text(z) else host.deviceZone()
    }

    /** `ST.schedule` when there is one. */
    val schedule: JsObj?
        get() = o(st?.get("schedule"))

    /** `ST.ledger` (null when the page would throw reading it). */
    val ledger: Any?
        get() = s["ledger"]

    /** `E.normalizePreferences(ST.prefs)` as an object. */
    fun prefs(): JsObj =
        JsObj().also { o ->
            for ((k, v) in PlanPreferences.of(Js.json(s["prefs"])).members) o[k] = Js.of(v)
        }

    /** HC-6 `say` (`logic.js:28`): the notice, then the host's toast. */
    fun say(message: String) {
        notice = Notice((notice?.id ?: 0.0) + 1, message, nowMs)
        host.toast(message)
    }

    /**
     * HC-7 `persist` (`logic.js:71-83`): history older than 21 days by its end is dropped, the
     * change log keeps its last 60, `updatedEpoch` is stamped, and the store saves; a refused save
     * shows the red banner, a good one clears a `Not saved` banner.
     */
    fun persist(): Boolean {
        val st = s
        val sched = o(st["schedule"])
        if (sched != null) {
            val cut = now - HISTORY_DAYS * DAY
            sched["historyInstances"] =
                Js.arrOf(list(sched["historyInstances"]).filter { num(at(it, "endEpoch")) > cut })
        }
        val log = list(st["changeLog"])
        st["changeLog"] = Js.arrOf(log.drop(maxOf(0, log.size - CHANGE_LOG)))
        st["updatedEpoch"] = now
        val r = host.saveState(Js.value(st))
        lastSave = r
        val b = banner
        if (!r.ok) banner = obj("kind" to "bad", "text" to "Not saved: " + (r.error ?: "undefined"))
        else if (b != null && b["kind"] == "bad" && text(b["text"]).startsWith("Not saved"))
            banner = null
        return r.ok
    }

    /**
     * HC-1 `boot` (`logic.js:84-99`): the store's state, a ledger, quest overlay and sync cursor
     * where missing, the one-time early-done repair, the storage banners, the Lab when LifeMax is
     * not there, and the tab.
     */
    fun boot() {
        val res = host.loadState(host.playerId(), host.deviceZone(), now)
        val st = o(Js.of(res.state)) ?: JsObj()
        this.st = st
        if (!truthy(st["ledger"]))
            st["ledger"] = Js.of(CompletionLedger.empty(Js.json(st["playerId"])))
        st["questOverlay"] = st["questOverlay"].takeIf(::truthy) ?: JsObj()
        st["hostSync"] =
            st["hostSync"].takeIf(::truthy) ?: obj("cursor" to JsonNull, "seen" to JsObj())
        if (HostCompletion.repairEarlyDone(this)) persist()
        when {
            res.source == "MEMORY_ONLY" -> banner = obj("kind" to "bad", "text" to MEMORY_ONLY)
            res.recovered ->
                banner =
                    obj(
                        "kind" to "warn",
                        "text" to
                            "Your last save was unreadable, so Life Director restored the previous good copy. The damaged copy was kept as " +
                                (res.quarantined ?: "a backup") +
                                ".",
                    )
            res.migratedFrom != null ->
                banner =
                    obj(
                        "kind" to "warn",
                        "text" to
                            "Life Director data upgraded from version " +
                                Js.numText(res.migratedFrom) +
                                ".",
                    )
        }
        val ui = o(st["ui"])
        if (!host.connected()) {
            if (ui == null)
                throw JsThrow("TypeError", "Cannot read properties of undefined (reading 'lab')")
            if (ui["lab"] == null) ui["lab"] = true
        }
        tab =
            if (!truthy(st["setupComplete"])) "setup"
            else (ui?.get("tab")?.takeIf(::truthy)?.let(::text) ?: "today")
    }

    /** `hostQuests(now)` (`logic.js:102-105`): LifeMax's quests and the class plan's days. */
    fun hostQuests(now: Double): JsArr {
        val days = prefs()["planningHorizonDays"]
        val zone = tz()
        val overlay = s["questOverlay"]
        val revoked = get(ledger, "revoked")
        val quests = host.readQuests(now, zone, Js.value(overlay), Js.json(revoked))
        val plan = host.readPlanSessions(now, tz(), num(days))
        return Js.arrOf((quests + plan).map(Js::of))
    }

    /** `completionHistory()` (`logic.js:106-110`): satisfied, unrevoked completions in order. */
    fun completionHistory(): JsArr {
        val l = ledger
        val rev = get(l, "revoked").takeIf(::truthy) ?: JsObj()
        val events = get(l, "events")
        val out = JsArr()
        for (id in list(get(l, "order"))) {
            val e = at(events, text(id))
            if (!truthy(e) || truthy(at(rev, text(at(e, "eventId"))))) continue
            if (!(num(at(e, "completionRatio")) >= SATISFIED)) continue
            out.add(
                obj(
                    "templateId" to at(e, "templateId"),
                    "spacingKey" to at(e, "spacingKey"),
                    "startEpoch" to at(e, "actualStartEpoch"),
                )
            )
        }
        return out
    }

    /** HC-8 `buildInput(now)` (`logic.js:111-119`): the engine's input from `ST`. */
    fun buildInput(now: Double): JsObj {
        val st = s
        val sched = o(st["schedule"])
        val zone = tz()
        val horizon = prefs()["planningHorizonDays"]
        val availability = assign(o(st["availability"]), obj("timezone" to tz()))
        val fixed = st["fixedCommitments"].takeIf(::truthy) ?: JsArr()
        val goals = st["goals"]
        val quests = hostQuests(now)
        val existing =
            if (sched != null)
                Js.arrOf(list(sched["scheduledInstances"]) + list(sched["historyInstances"]))
            else JsArr()
        return obj(
            "timezone" to zone,
            "currentTimeEpoch" to now,
            "horizonStartEpoch" to now,
            "horizonDays" to horizon,
            "playerAvailability" to availability,
            "fixedCommitments" to fixed,
            "goals" to goals,
            "activeQuests" to quests,
            "existingSchedule" to existing,
            "completionHistory" to completionHistory(),
            "playerPreferences" to st["prefs"],
            "energyProfile" to obj("checkin" to st["energyCheckin"]),
        )
    }

    /**
     * HC-40, HC-41 `applyOutput` (`logic.js:120-131`): a plan that broke an invariant is refused
     * (the red banner, `lastRejected` with the first 10 violations, the old plan kept); any other
     * becomes `ST.schedule`, with a change-log entry when labelled.
     */
    fun applyOutput(out: JsObj, label: Any?): Boolean {
        val st = s
        if (out["status"] == "INVARIANT_VIOLATION") {
            val violated = list(get(get(out, "validation"), "violatedInvariants"))
            val codes =
                violated.joinToString(", ") {
                    at(it, "code").let { c -> if (c == null || c == JsonNull) "" else text(c) }
                }
            banner =
                obj(
                    "kind" to "bad",
                    "text" to
                        "Life Director refused to publish a plan that broke a safety rule (" +
                            codes +
                            "). Your previous plan is kept. Please report this from Settings → Diagnostics.",
                )
            st["lastRejected"] =
                obj("at" to now, "violations" to Js.arrOf(violated.take(REJECTED_KEPT)))
            return false
        }
        st["schedule"] = out
        if (truthy(label)) changeLog().add(obj("at" to now, "label" to label))
        return true
    }

    /** `ST.changeLog`, which `push` needs. */
    fun changeLog(): JsArr =
        a(get(s, "changeLog"))
            ?: throw JsThrow("TypeError", "Cannot read properties of undefined (reading 'push')")

    /**
     * HC-9 `regenerate` (`logic.js:132-139`): a new plan for the whole horizon; the week key when
     * taken; saved; said unless quiet.
     */
    fun regenerate(reason: String, quiet: Boolean): Boolean {
        val now = now
        val out = o(Js.of(PlanSchedule.generate(Js.value(buildInput(now))))) ?: JsObj()
        val ok = applyOutput(out, "Re-planned ($reason)")
        if (ok) s["lastGeneratedWeekKey"] = HostWords.weekKey(now, tz())
        persist()
        if (!quiet && ok) {
            val n = list(out["scheduledInstances"]).count { at(it, "lifecycleState") == "PLANNED" }
            say("Plan updated · $n activities")
        }
        return ok
    }

    /** `autoReplan` (`logic.js:141`): re-plan quietly once set up, else only save. */
    fun autoReplan(reason: String) {
        if (truthy(s["setupComplete"])) regenerate(reason, true) else persist()
    }

    /**
     * HC-9 `reschedule(trigger)` (`logic.js:149-161`): the engine's local repair; a refused move is
     * said; a refused plan saves and stops; otherwise the commitments and blackouts the repair
     * kept, and the change log.
     */
    fun reschedule(trigger: JsObj): Repair? {
        val now = now
        val r =
            Reschedule.run(
                Js.value(
                    obj(
                        "engineInput" to buildInput(now),
                        "currentOutput" to s["schedule"],
                        "trigger" to trigger,
                    )
                )
            )
        if (r.rejectedMove != null && r.rejectedMove != JsonNull) {
            say(text(at(Js.of(r.rejectedMove), "message")))
            return null
        }
        val record = o(Js.of(r.changeRecord))
        val updated = o(Js.of(r.updatedSchedule)) ?: JsObj()
        if (!applyOutput(updated, get(record, "triggerReason"))) {
            persist()
            return null
        }
        val input = o(Js.of(r.engineInput))
        s["fixedCommitments"] = at(input, "fixedCommitments").takeIf(::truthy) ?: JsArr()
        val availability = o(get(s, "availability"))!!
        val kept = at(at(input, "playerAvailability"), "blackouts")
        if (truthy(kept)) availability["blackouts"] = kept
        changeLog()
            .add(
                obj(
                    "at" to now,
                    "label" to get(record, "triggerReason"),
                    "churn" to get(record, "churn"),
                    "actions" to num(get(get(record, "actions"), "length")),
                )
            )
        return Repair(input, record)
    }

    /** `summarize(r)` (`logic.js:162-168`): what a repair did, in words. */
    fun summarize(r: Repair?): String {
        if (r == null) return ""
        val actions = list(at(r.changeRecord, "actions"))
        fun n(t: String) = actions.count { at(it, "action") == t }
        val parts = ArrayList<String>()
        if (n("EXCUSE") > 0) parts += "${n("EXCUSE")} excused (no penalty)"
        if (n("INSERT") > 0) parts += "${n("INSERT")} re-placed"
        if (n("SHED") > 0) parts += "${n("SHED")} dropped for this week"
        if (n("COMPRESS") > 0) parts += "${n("COMPRESS")} shortened"
        return parts.joinToString(" · ").ifEmpty { "Nothing else needed to move" }
    }

    companion object {
        const val DAY = 86400.0
        private const val HISTORY_DAYS = 21
        private const val CHANGE_LOG = 60
        private const val REJECTED_KEPT = 10
        private const val SATISFIED = 0.75
        private const val MEMORY_ONLY =
            "This browser blocks storage here, so Life Director cannot save. Changes last until you close the app."
    }
}
