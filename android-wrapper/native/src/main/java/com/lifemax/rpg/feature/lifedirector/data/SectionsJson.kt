package com.lifemax.rpg.feature.lifedirector.data

import com.lifemax.rpg.core.data.objects
import com.lifemax.rpg.feature.lifedirector.domain.BlockKind
import com.lifemax.rpg.feature.lifedirector.domain.DirectorQuests
import com.lifemax.rpg.feature.lifedirector.domain.DirectorWeek
import com.lifemax.rpg.feature.lifedirector.domain.LegendEntry
import com.lifemax.rpg.feature.lifedirector.domain.MilestoneRow
import com.lifemax.rpg.feature.lifedirector.domain.Milestones
import com.lifemax.rpg.feature.lifedirector.domain.QuestHead
import com.lifemax.rpg.feature.lifedirector.domain.QuestRow
import com.lifemax.rpg.feature.lifedirector.domain.QuestTone
import com.lifemax.rpg.feature.lifedirector.domain.SessionState
import com.lifemax.rpg.feature.lifedirector.domain.UnfitGroup
import com.lifemax.rpg.feature.lifedirector.domain.Unscheduled
import com.lifemax.rpg.feature.lifedirector.domain.WeekDay
import com.lifemax.rpg.feature.lifedirector.domain.WeekFit
import com.lifemax.rpg.feature.lifedirector.domain.WeekSeg
import com.lifemax.rpg.feature.lifedirector.domain.WeekStat
import com.lifemax.rpg.feature.lifedirector.domain.XpYield
import com.lifemax.rpg.feature.lifedirector.domain.YieldRow
import org.json.JSONObject

/*
 * Week and Quests of the projection (phase 3A). Strict like the rest: a missing or wrong-kind field, an unknown kind or tone, a
 * segment off the day or a share over the bar throws, and the projection is refused.
 */

/**
 * The sections the page hands this screen; a page from before phase 3A sends none, which means
 * Today only.
 */
internal fun JSONObject.covers(): List<String> {
    if (!has("covers")) return listOf("today")
    val list = getJSONArray("covers").let { a -> List(a.length()) { a.getString(it) } }
    require("today" in list) { "covers without today" }
    return list
}

internal fun JSONObject.week(): DirectorWeek? {
    if (!has("week") || isNull("week")) return null
    val w = getJSONObject("week")
    val days =
        w.getJSONArray("days").objects().mapIndexed { i, d ->
            require(d.getInt("index") == i) { "Week's days out of order" }
            WeekDay(
                index = i,
                label = d.text("label"),
                date = d.getInt("date"),
                today = d.getBoolean("today"),
                aria = d.text("aria"),
                count = d.text("count"),
                done = d.text("done"),
                minutes = d.text("minutes"),
                segs =
                    d.getJSONArray("segs").objects().map { g ->
                        val left = g.getDouble("left").toFloat()
                        val width = g.getDouble("width").toFloat()
                        require(left >= 0f && width > 0f && left + width <= 100.01f) {
                            "A segment off the day"
                        }
                        WeekSeg(
                            blockKind(g.text("kind")),
                            sessionState(g.text("state")),
                            left,
                            width,
                        )
                    },
            )
        }
    require(days.isNotEmpty()) { "Week without days" }
    val fit = w.getJSONObject("fit")
    return DirectorWeek(
        stats =
            w.getJSONArray("stats").objects().map { WeekStat(it.text("value"), it.text("label")) },
        ticks = w.getJSONArray("ticks").let { a -> List(a.length()) { a.getString(it) } },
        days = days,
        legend =
            w.getJSONArray("legend").objects().map {
                LegendEntry(blockKind(it.text("kind")), it.text("label"))
            },
        yield =
            if (w.isNull("yield")) null
            else
                w.getJSONObject("yield").let { y ->
                    XpYield(
                        y.text("title"),
                        y.text("note"),
                        y.getJSONArray("rows").objects().map { r ->
                            val done = r.getDouble("done").toFloat()
                            val plan = r.getDouble("plan").toFloat()
                            require(done >= 0f && plan >= 0f && done + plan <= 1.001f) {
                                "A yield bar over its length"
                            }
                            YieldRow(
                                r.text("label"),
                                r.getLong("color") and 0xFFFFFFFFL,
                                done,
                                plan,
                                r.text("total"),
                            )
                        },
                    )
                },
        fit =
            WeekFit(
                fit.text("title"),
                fit.text("text"),
                fit.text("allFit"),
                if (fit.isNull("unscheduled")) null
                else
                    fit.getJSONObject("unscheduled").let { u ->
                        Unscheduled(
                            u.text("summary"),
                            u.getJSONArray("groups").objects().map {
                                UnfitGroup(it.text("title"), it.text("tier"), it.text("text"))
                            },
                        )
                    },
            ),
    )
}

internal fun JSONObject.quests(): DirectorQuests? {
    if (!has("quests") || isNull("quests")) return null
    val q = getJSONObject("quests")
    val head = q.getJSONObject("head")
    return DirectorQuests(
        head = QuestHead(head.text("title"), head.text("text"), head.text("open")),
        rows = q.getJSONArray("rows").objects().map { it.questRow(tone(it.text("tone"))) },
        passive = q.getJSONArray("passive").objects().map { it.questRow(QuestTone.PAS) },
        empty = q.text("empty"),
        milestones =
            if (q.isNull("milestones")) null
            else
                q.getJSONObject("milestones").let { m ->
                    Milestones(
                        m.text("summary"),
                        m.text("note"),
                        m.getJSONArray("rows").objects().map {
                            MilestoneRow(
                                it.text("title"),
                                it.text("desc"),
                                it.getBoolean("earned"),
                                it.text("status"),
                            )
                        },
                    )
                },
    )
}

private fun JSONObject.questRow(tone: QuestTone) =
    QuestRow(text("title"), text("when"), text("status"), tone, if (has("why")) text("why") else "")

private fun tone(s: String) =
    when (s) {
        "ok" -> QuestTone.OK
        "pas" -> QuestTone.PAS
        "no" -> QuestTone.NO
        else -> throw IllegalArgumentException("Unknown quest tone $s")
    }

private fun blockKind(s: String) =
    BlockKind.entries.firstOrNull { it.name.equals(s, ignoreCase = true) }
        ?: throw IllegalArgumentException("Unknown block kind $s")

private fun sessionState(s: String) =
    if (s.isEmpty()) SessionState.NONE
    else
        SessionState.entries.firstOrNull {
            it.name.equals(s, ignoreCase = true) && it != SessionState.NONE
        } ?: throw IllegalArgumentException("Unknown session state $s")

/** text, never a number or null taken as text */
private fun JSONObject.text(key: String): String =
    get(key) as? String ?: throw IllegalArgumentException("$key is not text")

internal fun JSONObject.guide(): com.lifemax.rpg.feature.lifedirector.domain.DirectorGuide? {
    if (!has("guide") || isNull("guide")) return null
    val g = getJSONObject("guide")
    val l = g.getJSONObject("labels")
    val steps =
        g.getJSONArray("steps").objects().map { s ->
            val dial = s.guideDial() // phase 4A: the dial's mode, the spotlight and the name tags
            com.lifemax.rpg.feature.lifedirector.domain.GuideStep(
                anchor =
                    s.text("anchor").also {
                        require(it.isNotEmpty()) { "A guide step points at nothing" }
                    },
                title = s.text("title"),
                text =
                    s.getJSONArray("text").objects().map {
                        com.lifemax.rpg.feature.lifedirector.domain.GuideSpan(
                            it.text("t"),
                            it.getBoolean("b"),
                        )
                    },
                hint = s.getBoolean("hint"),
                mode = dial.mode,
                round = dial.round,
                pad = dial.pad,
                tags = dial.tags,
            )
        }
    require(steps.isNotEmpty()) { "A guide without steps" }
    return com.lifemax.rpg.feature.lifedirector.domain.DirectorGuide(
        key = g.text("key"),
        auto = g.getBoolean("auto"),
        steps = steps,
        labels =
            com.lifemax.rpg.feature.lifedirector.domain.GuideLabels(
                l.text("skip"),
                l.text("back"),
                l.text("next"),
                l.text("last"),
                l.text("tap"),
            ),
    )
}
