package com.lifemax.rpg.feature.lifedirector.data

import com.lifemax.rpg.core.data.objects
import com.lifemax.rpg.core.designsystem.feedback.XpAward
import com.lifemax.rpg.core.designsystem.feedback.XpGain
import com.lifemax.rpg.feature.lifedirector.domain.DirectorPoint
import com.lifemax.rpg.feature.lifedirector.domain.GlanceIntent
import org.json.JSONArray
import org.json.JSONObject

/*
 * Phase 4A's feedback on the wire, strict like the rest: a missing or wrong-kind field throws (a
 * number is never taken as text, nor text as a number). The projection's celebrationTarget and
 * intent are optional objects (a page from before 4A sends neither); once sent they must be whole.
 */

/** The projection's `celebrationTarget {x, y}`, or null from a page that sends none. */
internal fun JSONObject.celebrationTarget(): DirectorPoint? {
    if (!has("celebrationTarget") || isNull("celebrationTarget")) return null
    val t = getJSONObject("celebrationTarget")
    return DirectorPoint(t.number("x"), t.number("y"))
}

/** The projection's `intent {id, sheet, target}`, or null when the glance asked for nothing. */
internal fun JSONObject.intent(): GlanceIntent? {
    if (!has("intent") || isNull("intent")) return null
    val i = getJSONObject("intent")
    val id = i.whole("id").also { require(it > 0) { "An intent's id counts from 1" } }
    val sheet = i.text("sheet").also { require(it.isNotEmpty()) { "An intent without a sheet" } }
    return GlanceIntent(id, sheet, i.text("target"))
}

/**
 * A reply's `celebrations [{xp, gains: [{attribute, short, xp, color}]}]`, as 50-native-tabkit.js
 * celebrations() sends them (the same shape as Workout plan's reply).
 */
fun xpAwards(a: JSONArray): List<XpAward> =
    a.objects().map { c ->
        XpAward(
            c.whole("xp").toInt(),
            c.getJSONArray("gains").objects().map {
                val color = it.whole("color")
                require(color in 0..0xFFFFFFFFL) { "A colour is 0xAARRGGBB" }
                XpGain(it.text("attribute"), it.text("short"), it.whole("xp").toInt(), color)
            },
        )
    }

/**
 * The celebrations of a reply, none when it carries none. A list that is malformed is not drawn
 * rather than thrown: the reply must still settle the write it answers (its pending button, its
 * operation id), where a throw would leave the screen waiting for an answer that has come.
 */
internal fun JSONObject.celebrations(): List<XpAward> {
    if (!has("celebrations")) return emptyList()
    return runCatching { xpAwards(getJSONArray("celebrations")) }.getOrDefault(emptyList())
}

private fun JSONObject.text(key: String): String =
    get(key) as? String ?: throw IllegalArgumentException("$key is not text")

private fun JSONObject.number(key: String): Float {
    val v = get(key) as? Number ?: throw IllegalArgumentException("$key is not a number")
    return v.toFloat().also { require(it.isFinite()) { "$key is not finite" } }
}

/** a whole number, never text or a fraction */
private fun JSONObject.whole(key: String): Long =
    when (val v = get(key)) {
        is Int -> v.toLong()
        is Long -> v
        else -> throw IllegalArgumentException("$key is not a whole number")
    }
