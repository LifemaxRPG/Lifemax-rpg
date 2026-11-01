package com.lifemax.rpg.feature.lifedirector.data

import com.lifemax.rpg.core.data.objects
import com.lifemax.rpg.feature.lifedirector.domain.Chip
import com.lifemax.rpg.feature.lifedirector.domain.ChipKind
import com.lifemax.rpg.feature.lifedirector.domain.GoneSheet
import com.lifemax.rpg.feature.lifedirector.domain.GuideSpan
import com.lifemax.rpg.feature.lifedirector.domain.MEAL_SHEET
import com.lifemax.rpg.feature.lifedirector.domain.SessionMode
import com.lifemax.rpg.feature.lifedirector.domain.SessionSheetModel
import com.lifemax.rpg.feature.lifedirector.domain.SessionState
import com.lifemax.rpg.feature.lifedirector.domain.SheetComplete
import com.lifemax.rpg.feature.lifedirector.domain.SheetKinds
import com.lifemax.rpg.feature.lifedirector.domain.SheetMinutes
import com.lifemax.rpg.feature.lifedirector.domain.SheetModel
import com.lifemax.rpg.feature.lifedirector.domain.SheetPrimary
import com.lifemax.rpg.feature.lifedirector.domain.SheetRowButton
import com.lifemax.rpg.feature.lifedirector.domain.SheetWhy
import com.lifemax.rpg.feature.lifedirector.domain.WhyFactor
import org.json.JSONObject

/*
 * Today's sheets of the projection (phase 4A), strict like the rest: a missing or wrong-kind field, an unknown kind, state, mode
 * or command, or a body that does not belong to its mode throws, and the projection is refused.
 */

/** Whether the page knows Today's sheets: a page from before phase 4A sends no `sheet` field. */
internal fun JSONObject.knowsSheets(): Boolean = has("sheet")

internal fun JSONObject.sheet(): SheetModel? {
    if (!has("sheet") || isNull("sheet")) return null
    val s = getJSONObject("sheet")
    val kind = s.str("kind")
    if (s.has("gone")) {
        require(s.getBoolean("gone")) { "gone is only ever true" }
        // a session names itself; a timer, meal or move the page no longer finds does not
        return GoneSheet(kind, if (s.has("id")) s.str("id") else "")
    }
    return when (kind) {
        SheetKinds.SESSION -> session(s)
        SheetKinds.TIMER -> timerSheet(s)
        MEAL_SHEET -> mealSheet(s)
        SheetKinds.MOVE -> moveSheet(s)
        com.lifemax.rpg.feature.lifedirector.domain.DISRUPT_SHEET -> disruptSheet(s)
        com.lifemax.rpg.feature.lifedirector.domain.FormSheetKinds.RULE -> ruleSheet(s)
        com.lifemax.rpg.feature.lifedirector.domain.FormSheetKinds.GOAL_TYPE -> goalTypeSheet(s)
        com.lifemax.rpg.feature.lifedirector.domain.FormSheetKinds.GOAL_FORM -> goalFormSheet(s)
        else -> throw IllegalArgumentException("Unknown sheet $kind")
    }
}

private fun session(s: JSONObject): SessionSheetModel {
    val mode =
        when (val m = s.str("mode")) {
            "now" -> SessionMode.NOW
            "later" -> SessionMode.LATER
            "past" -> SessionMode.PAST
            else -> throw IllegalArgumentException("Unknown session mode $m")
        }
    val why = s.getJSONObject("why")
    val sheet =
        SessionSheetModel(
            id = s.str("id").also { require(it.isNotEmpty()) { "A session without an id" } },
            state =
                when (val st = s.str("state")) {
                    "plan" -> SessionState.PLAN
                    "done" -> SessionState.DONE
                    "part" -> SessionState.PART
                    "off" -> SessionState.OFF
                    else -> throw IllegalArgumentException("Unknown session state $st")
                },
            icon = s.str("icon"),
            title = s.str("title"),
            `when` = s.str("when"),
            chips =
                s.getJSONArray("chips").objects().map {
                    Chip(
                        when (val k = it.str("kind")) {
                            "quest" -> ChipKind.QUEST
                            "goal" -> ChipKind.GOAL
                            "xp" -> ChipKind.XP
                            else -> throw IllegalArgumentException("Unknown chip $k")
                        },
                        it.str("text"),
                    )
                },
            mode = mode,
            note =
                s.getJSONArray("note").objects().map { GuideSpan(it.str("t"), it.getBoolean("b")) },
            minutes = if (s.isNull("minutes")) null else s.getJSONObject("minutes").minutes(),
            primary = if (s.isNull("primary")) null else s.getJSONObject("primary").primary(),
            complete =
                if (s.isNull("complete")) null
                else
                    s.getJSONObject("complete").let {
                        SheetComplete(
                            it.str("label"),
                            it.str("command").also { c ->
                                require(c == "done" || c == "logToday") { "Unknown completion $c" }
                            },
                            it.str("smallPrint"),
                        )
                    },
            row = s.getJSONArray("row").objects().map { it.rowButton() },
            past = s.str("past"),
            why =
                SheetWhy(
                    why.str("title"),
                    why.str("summary"),
                    why.getJSONArray("factors").objects().map {
                        WhyFactor(
                            it.str("name"),
                            points(it.get("points")),
                            it.getDouble("frac").toFloat().also { f ->
                                require(f in 0f..1f) { "A factor's bar is not a share" }
                            },
                        )
                    },
                    why.str("feasible"),
                ),
            orphan = s.str("orphan"),
        )
    sheet.requireFitsMode()
    return sheet
}

/** Each mode has its own body, as instSheet's branches do. */
private fun SessionSheetModel.requireFitsMode() {
    when (mode) {
        SessionMode.NOW ->
            require(primary != null && minutes != null && complete?.command == "done") {
                "A session on now without its timer, minutes or Mark done"
            }
        SessionMode.LATER ->
            require(primary == null && minutes != null && complete?.command == "logToday") {
                "A later session without its minutes or Log it for today"
            }
        SessionMode.PAST ->
            require(primary == null && minutes == null && complete == null && row.isEmpty()) {
                "A past session with actions"
            }
    }
}

private fun JSONObject.minutes(): SheetMinutes {
    val m = SheetMinutes(str("label"), getInt("value"), getInt("min"), getInt("max"))
    require(m.min in 1..m.max && m.value in m.min..m.max) { "Minutes outside their limits" }
    return m
}

private fun JSONObject.primary(): SheetPrimary {
    val label = str("label")
    return when {
        has("sheet") -> {
            require(!has("command")) { "A button is a command or a sheet" }
            SheetPrimary(label, sheet = str("sheet").also { requireSheet(it) })
        }
        else -> {
            val command = str("command")
            require(command == "timer") { "Unknown main button $command" }
            SheetPrimary(label, command = command, act = str("act"))
        }
    }
}

private fun JSONObject.rowButton(): SheetRowButton {
    val label = str("label")
    if (has("sheet")) {
        require(!has("command")) { "A button is a command or a sheet" }
        return SheetRowButton(label, sheet = str("sheet").also { requireSheet(it) })
    }
    return when (val command = str("command")) {
        "pin" -> SheetRowButton(label, command = command, on = getBoolean("on"))
        "skip" -> SheetRowButton(label, command = command)
        "openPlan" -> SheetRowButton(label, command = command, key = str("key"))
        else -> throw IllegalArgumentException("Unknown session button $command")
    }
}

private fun requireSheet(kind: String) =
    require(kind == SheetKinds.TIMER || kind == SheetKinds.MOVE) { "Unknown sheet $kind" }

/** A factor's points as the page writes them: whole numbers without a decimal point. */
private fun points(v: Any): String {
    val d = (v as? Number)?.toDouble() ?: throw IllegalArgumentException("points is not a number")
    require(d.isFinite()) { "points is not a number" }
    return if (d == Math.rint(d) && kotlin.math.abs(d) < 1e15) d.toLong().toString()
    else d.toString()
}

private fun JSONObject.str(key: String): String =
    get(key) as? String ?: throw IllegalArgumentException("$key is not text")
