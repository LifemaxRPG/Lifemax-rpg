package com.lifemax.rpg.core.lifedirector.host

import com.lifemax.rpg.core.directorstore.JsValues
import com.lifemax.rpg.core.lifedirector.DemandTables
import com.lifemax.rpg.core.lifedirector.PageValues
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
import com.lifemax.rpg.core.save.JsonNull

/**
 * Do's commands from a native screen (`logic.js:2325-2525`, `Do`): goals (HC-50 to HC-55), settings
 * (HC-56 to HC-59), the backup and reset (HC-65, HC-66); My time's are in [TimeCommands].
 */
object DoCommands {
    /** `NO_OP` (`logic.js:2329`). */
    const val NO_OP = "Nothing was added."
    private val PRIORITIES = listOf(1.0, 2.0, 3.0, 4.0, 5.0)
    private val DATE = Regex("""^\d{4}-\d{2}-\d{2}$""")
    private const val MAX_OP = 12
    private const val LAST_MINUTE = 1439.0

    /** The commands this port has, by the page's names. */
    val NAMES =
        listOf(
            "goalPriority",
            "goalPause",
            "goalRemove",
            "clarify",
            "pref",
            "prefRange",
            "restDay",
            "labToggle",
            "tourReset",
            "exportBackup",
            "importBackup",
            "reset",
            "sleepSave",
            "ruleSave",
            "ruleRemove",
            "routine",
            "meals",
            "activityAtAdd",
            "goalAdd",
            "finishSetup",
        )

    /** HC-50 `nativeRun(name, payload)`. */
    fun run(p: HostPage, name: String, x: JsObj): CommandReply {
        if (p.st == null) p.boot()
        return when (name) {
            "goalPriority" -> goalPriority(p, x)
            "goalPause" -> goalPause(p, x)
            "goalRemove" -> goalRemove(p, x)
            "clarify" -> clarify(p, x)
            "pref" -> pref(p, x)
            "prefRange" -> prefRange(p, x)
            "restDay" -> restDay(p, x)
            "labToggle" -> labToggle(p, x)
            "tourReset" -> tourReset(p)
            "exportBackup" -> CommandReply(true, "", text = p.host.exportBackup(Js.value(p.s)))
            "importBackup" -> importBackup(p, x)
            "reset" -> reset(p)
            "goalAdd" -> goalAdd(p, x)
            else -> TimeCommands.run(p, name, x) ?: CommandReply.CANNOT
        }
    }

    /** JavaScript's `String.prototype.trim`. */
    fun trim(s: String): String = s.trim { JsValues.isJsSpace(it) }

    private fun goals(p: HostPage): JsArr =
        a(get(p.s, "goals"))
            ?: throw JsThrow("TypeError", "Cannot read properties of undefined (reading 'find')")

    /** `goalById(id)`: the goal of that id, compared as text. */
    private fun goalById(p: HostPage, id: Any?): JsObj? =
        o(goals(p).firstOrNull { same(at(it, "id"), text(id)) })

    private val GONE = CommandReply.no("That goal is gone.")

    /** `replanOrSave` (`logic.js:2327`). */
    private fun replanOrSave(p: HostPage, reason: String) {
        if (truthy(p.s["setupComplete"])) p.regenerate(reason, true) else p.persist()
    }

    private fun goalPriority(p: HostPage, x: JsObj): CommandReply {
        val g = goalById(p, x["id"])
        val v = num(x["value"])
        if (g == null) return GONE
        if (v !in PRIORITIES) return CommandReply.no("That is not a priority.")
        g["priority"] = v
        p.regenerate("priority", true)
        return saved(p, "")
    }

    private fun goalPause(p: HostPage, x: JsObj): CommandReply {
        val g = goalById(p, x["id"]) ?: return GONE
        val paused = g["status"] == "PAUSED"
        val want = x["paused"] as? Boolean ?: !paused
        if (want == paused) return CommandReply(true, "")
        g["status"] = if (want) "PAUSED" else "ACTIVE"
        p.regenerate("goal changed", true)
        return saved(p, "")
    }

    private fun goalRemove(p: HostPage, x: JsObj): CommandReply {
        if (goalById(p, x["id"]) == null) return GONE
        p.s["goals"] = Js.arrOf(goals(p).filter { !same(at(it, "id"), text(x["id"])) })
        p.regenerate("goal removed", true)
        return saved(p, "")
    }

    private fun clarify(p: HostPage, x: JsObj): CommandReply {
        val g = goalById(p, x["id"]) ?: return GONE
        val arch = x["arch"]
        if (arch !is String || arch !in DemandTables.archetypes)
            return CommandReply.no("That is not a kind of goal.")
        g["archetypeId"] = arch
        p.regenerate("goal clarified", true)
        return saved(p, "")
    }

    private fun prefsObj(p: HostPage): JsObj =
        o(get(p.s, "prefs")) ?: throw JsThrow("TypeError", "Cannot set properties of undefined")

    private fun pref(p: HostPage, x: JsObj): CommandReply {
        val key = x["key"]
        val value = x["value"]
        val allowed = (key as? String)?.let { HostWords.PREF_OPTIONS[it] }
        if (allowed == null || allowed.none { it == text(value) })
            return CommandReply.no("That is not a setting.")
        val n = num(value)
        prefsObj(p)[key] = if (n.isNaN()) value else n
        if (key == "intensityLevel") p.s["prefsLightUntil"] = JsonNull
        replanOrSave(p, "settings")
        return saved(p, "")
    }

    private fun prefRange(p: HostPage, x: JsObj): CommandReply {
        val r = HostWords.PREF_RANGES.firstOrNull { it.key == x["key"] }
        val v = num(x["value"])
        if (
            r == null ||
                !v.isFinite() ||
                v < r.low ||
                v > r.high ||
                PageValues.truthy((v - r.low) % r.step)
        )
            return CommandReply.no("That is not a setting.")
        prefsObj(p)[r.key] = v
        replanOrSave(p, "settings")
        return saved(p, "")
    }

    private fun restDay(p: HostPage, x: JsObj): CommandReply {
        val k = num(x["day"])
        if (!Js.isInteger(k) || k < 0 || k > 6) return CommandReply.no("That is not a day.")
        val days = list(p.prefs()["preferredRestDays"])
        val has = days.any { same(it, k) }
        val want = x["on"] as? Boolean ?: !has
        if (want == has) return CommandReply(true, "")
        prefsObj(p)["preferredRestDays"] =
            if (has) Js.arrOf(days.filter { !same(it, k) }) else Js.arrOf(days + k)
        replanOrSave(p, "settings")
        return saved(p, "")
    }

    private fun ui(p: HostPage): JsObj =
        o(get(p.s, "ui")) ?: throw JsThrow("TypeError", "Cannot set properties of undefined")

    private fun labToggle(p: HostPage, x: JsObj): CommandReply {
        ui(p)["lab"] = truthy(x["on"])
        p.persist()
        return saved(p, "")
    }

    private fun tourReset(p: HostPage): CommandReply {
        ui(p)["tours"] = JsObj()
        p.persist()
        if (truthy(p.s["setupComplete"]) && truthy(p.s["schedule"])) {
            p.tab = "today"
            ui(p)["tab"] = "today"
            p.dayIdx = 0.0
        } else p.tab = "setup"
        return saved(p, "")
    }

    /** HC-65 `importBackup({text})`: the backup's state replaces `ST` only when it is saved. */
    private fun importBackup(p: HostPage, x: JsObj): CommandReply {
        val r = p.host.importBackup(text(x["text"].takeIf(::truthy) ?: ""))
        val next = o(Js.of(r.state)) ?: return CommandReply.no(r.error ?: "")
        val was = p.st
        p.st = next
        if (!p.persist()) {
            p.st = was
            return CommandReply.no(
                "Not saved: " + (p.lastSave.error ?: "undefined") + " Nothing was restored."
            )
        }
        p.say("Backup restored")
        return CommandReply(true, "Backup restored")
    }

    /** HC-66 `reset()`: a fresh state saved in its place, then a new boot. */
    private fun reset(p: HostPage): CommandReply {
        val result = p.host.clearState(p.host.playerId(), p.host.deviceZone(), p.now)
        p.lastSave = result
        if (!result.ok) {
            val message = "Life Director was not reset: " + (result.error ?: "undefined")
            p.banner = obj("kind" to "bad", "text" to message)
            return CommandReply.no(message)
        }
        p.banner = null
        p.notice = null
        p.dayIdx = 0.0
        p.boot()
        return CommandReply(true, "Life Director reset")
    }

    /** `goalFormModel(arch).fold`: whether the kind of goal has a measurable target. */
    private fun hasTarget(arch: Any?): Boolean =
        DemandTables.archetypes[arch as? String]?.templates?.any {
            PageValues.truthy(it["volumeDriver"])
        } == true

    /** HC-53 `goalAdd(form)`: a new goal named from op (its first 12 characters). */
    private fun goalAdd(p: HostPage, x: JsObj): CommandReply {
        val arch = x["arch"]
        val known = arch is String && arch in DemandTables.archetypes
        val name = trim(text(x["title"].takeIf(::truthy) ?: ""))
        val prio = num(x["priority"]).takeUnless { it == 0.0 || it.isNaN() } ?: 3.0
        val lvl = x["level"].takeIf(::truthy) ?: "NOVICE"
        if (!known && arch != "CUSTOM") return CommandReply.refuse("That is not a kind of goal.")
        if (name.isEmpty()) return CommandReply.refuse("Give the goal a name")
        if (
            prio !in PRIORITIES ||
                lvl !in HostWords.GOAL_LEVELS ||
                (!known && x["tag"] !in HostWords.CUSTOM_TAGS)
        )
            return CommandReply.refuse("That is not one of the choices.")
        if (!truthy(x["op"])) return CommandReply.refuse(NO_OP)
        val id = "g-" + text(x["op"]).take(MAX_OP)
        if (goalById(p, id) != null) return recorded(p, "Already added")
        val g =
            obj(
                "id" to id,
                "title" to name,
                "priority" to prio,
                "level" to lvl,
                "status" to "ACTIVE",
                "createdEpoch" to p.now,
            )
        if (known) g["archetypeId"] = arch
        else
            g["customTemplates"] =
                Js.arr(
                    obj(
                        "title" to name,
                        "tag" to x["tag"],
                        "minutes" to or(num(x["minutes"]), 30.0),
                        "perWeek" to or(num(x["perWeek"]), 3.0),
                        "IP" to or(num(x["physical"]), 2.0),
                        "IC" to or(num(x["mental"]), 2.0),
                    )
                )
        if (arch == "SKILL_LANGUAGE")
            g["subject"] =
                trim(text(x["subject"].takeIf(::truthy) ?: "")).ifEmpty { null } ?: JsonNull
        val u = num(x["units"])
        val per = num(x["rate"])
        val deadline = text(x["deadline"].takeIf(::truthy) ?: "")
        if (hasTarget(arch) && u > 0 && per > 0 && DATE.matches(deadline)) {
            val (y, mo, d) = deadline.split("-").map { Js.num(it) }
            g["target"] =
                obj(
                    "units" to u,
                    "completed" to 0.0,
                    "unit" to (x["unit"].takeIf(::truthy) ?: "units"),
                    "unitsPerHour" to per,
                    "deadlineEpoch" to HostWords.zoned(y, mo, d, LAST_MINUTE, p.tz()),
                )
        }
        goals(p).add(g)
        if (truthy(p.s["setupComplete"])) p.regenerate("goal added", true) else p.persist()
        p.say("Goal added")
        return applied(p)
    }

    private fun or(v: Double, fallback: Double): Double = if (v == 0.0 || v.isNaN()) fallback else v
}
