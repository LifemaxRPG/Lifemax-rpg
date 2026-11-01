package com.lifemax.rpg.feature.lifedirector.data

import com.lifemax.rpg.core.data.objects
import com.lifemax.rpg.feature.lifedirector.domain.Choice
import com.lifemax.rpg.feature.lifedirector.domain.ChoiceSetting
import com.lifemax.rpg.feature.lifedirector.domain.DirectorGoals
import com.lifemax.rpg.feature.lifedirector.domain.DirectorSettings
import com.lifemax.rpg.feature.lifedirector.domain.GoalCard
import com.lifemax.rpg.feature.lifedirector.domain.GoalClarify
import com.lifemax.rpg.feature.lifedirector.domain.GoalShare
import com.lifemax.rpg.feature.lifedirector.domain.GuideSpan
import com.lifemax.rpg.feature.lifedirector.domain.RestDay
import com.lifemax.rpg.feature.lifedirector.domain.SettingRange
import com.lifemax.rpg.feature.lifedirector.domain.SettingsData
import com.lifemax.rpg.feature.lifedirector.domain.TitledText
import org.json.JSONObject

/*
 * Goals and Settings of the projection (phase 3B), strict like the rest: a missing or wrong-kind field, a share outside 0..1, a
 * priority not offered, or a range whose labels do not match its stops throws, and the projection is refused.
 */

internal fun JSONObject.goals(): DirectorGoals? {
    if (!has("goals") || isNull("goals")) return null
    val g = getJSONObject("goals")
    val priorities =
        g.getJSONArray("priorities").objects().map {
            Choice(it.getInt("value").toString(), it.str("label"))
        }
    val cards =
        g.getJSONArray("cards").objects().map { c ->
            val prio = c.getInt("priority")
            require(priorities.any { it.value == prio.toString() }) {
                "A goal's priority is not offered"
            }
            GoalCard(
                id = c.str("id"),
                title = c.str("title"),
                sub = c.str("sub"),
                paused = c.getBoolean("paused"),
                priority = prio,
                share =
                    if (c.isNull("share")) null
                    else
                        c.getJSONObject("share").let {
                            GoalShare(
                                it.share("target"),
                                it.str("targetText"),
                                it.share("scheduled"),
                                it.str("scheduledText"),
                                it.str("line"),
                            )
                        },
                warning = if (c.isNull("warning")) null else c.str("warning"),
                clarify =
                    if (c.isNull("clarify")) null
                    else
                        c.getJSONObject("clarify").let { k ->
                            GoalClarify(
                                k.str("text"),
                                k.getJSONArray("options").objects().map {
                                    Choice(it.str("arch"), it.str("label"))
                                },
                            )
                        },
                pause = c.str("pause"),
                remove = c.str("remove"),
            )
        }
    return DirectorGoals(
        explain = g.getJSONObject("explain").titled(),
        priorities = priorities,
        cards = cards,
        empty = if (g.isNull("empty")) null else g.getJSONObject("empty").titled(),
        add = g.str("add"),
        removeConfirm = g.str("removeConfirm"),
    )
}

internal fun JSONObject.settings(): DirectorSettings? {
    if (!has("settings") || isNull("settings")) return null
    val s = getJSONObject("settings")
    val intensity = s.getJSONObject("intensity")
    val rest = s.getJSONObject("restDays")
    val misses = s.getJSONObject("misses")
    val guide = s.getJSONObject("guide")
    val d = s.getJSONObject("data")
    val diag = s.getJSONObject("diagnostics")
    val lab = s.getJSONObject("lab")
    return DirectorSettings(
        intensity = intensity.choices(),
        intensityNote = intensity.str("note"),
        ranges = s.getJSONArray("ranges").objects().map { it.range() },
        restDaysTitle = rest.str("title"),
        restDaysNote = rest.str("note"),
        restDays =
            rest
                .getJSONArray("days")
                .objects()
                .map { RestDay(it.getInt("day"), it.str("label"), it.getBoolean("on")) }
                .also {
                    require(it.map { d -> d.day } == (0..6).toList()) {
                        "Rest days are not the seven days"
                    }
                },
        horizon = s.getJSONObject("horizon").choices(),
        misses = misses.choices(),
        missesText =
            misses.getJSONArray("text").objects().map {
                GuideSpan(it.str("t"), it.getBoolean("b"))
            },
        grace = misses.getJSONObject("grace").range(),
        guide = TitledText(guide.str("title"), guide.str("text")),
        guideButton = guide.str("button"),
        data =
            SettingsData(
                d.str("title"),
                d.str("text"),
                d.str("storageWarning"),
                d.getBoolean("storageUnavailable"),
                d.str("copy"),
                d.str("restore"),
                d.str("reset"),
                d.str("resetConfirm"),
                d.str("restoreTitle"),
                d.str("restoreText"),
                d.str("restoreButton"),
            ),
        diagnosticsTitle = diag.str("title"),
        diagnostics = diag.str("line"),
        labLabel = lab.str("label"),
        labOn = lab.getBoolean("on"),
    )
}

private fun JSONObject.choices(): ChoiceSetting {
    val options = getJSONArray("options").objects().map { Choice(it.str("value"), it.str("label")) }
    val value = str("value")
    require(options.any { it.value == value }) { "A setting's value is not one of its choices" }
    return ChoiceSetting(if (has("title")) str("title") else "", value, options)
}

private fun JSONObject.range(): SettingRange {
    val r =
        SettingRange(
            key = str("key"),
            label = str("label"),
            hint = str("hint"),
            min = getInt("min"),
            max = getInt("max"),
            step = getInt("step"),
            value = getInt("value"),
            stops = getJSONArray("stops").let { a -> List(a.length()) { a.getString(it) } },
        )
    require(r.step > 0 && r.max > r.min && (r.max - r.min) % r.step == 0) {
        "A range off its steps"
    }
    require(r.stops.size == r.stopCount) { "A range without a label for every stop" }
    require(r.value in r.min..r.max && (r.value - r.min) % r.step == 0) {
        "A range's value off its steps"
    }
    return r
}

private fun JSONObject.titled() = TitledText(str("title"), str("text"))

private fun JSONObject.share(key: String): Float =
    getDouble(key).toFloat().also { require(it in 0f..1f) { "$key is not a share" } }

private fun JSONObject.str(key: String): String =
    get(key) as? String ?: throw IllegalArgumentException("$key is not text")
