package com.lifemax.rpg.feature.lifedirector.data

import com.lifemax.rpg.core.data.objects
import com.lifemax.rpg.feature.lifedirector.domain.DISRUPT_SHEET
import com.lifemax.rpg.feature.lifedirector.domain.DisruptDate
import com.lifemax.rpg.feature.lifedirector.domain.DisruptInput
import com.lifemax.rpg.feature.lifedirector.domain.DisruptNumber
import com.lifemax.rpg.feature.lifedirector.domain.DisruptOption
import com.lifemax.rpg.feature.lifedirector.domain.DisruptSection
import com.lifemax.rpg.feature.lifedirector.domain.DisruptSheetModel
import com.lifemax.rpg.feature.lifedirector.domain.DisruptTime
import com.lifemax.rpg.feature.lifedirector.domain.MoveSheetModel
import com.lifemax.rpg.feature.lifedirector.domain.MoveTime
import org.json.JSONObject

/*
 * The Move and "Something came up" sheets of the projection (phase 4A), strict like the session sheet: a missing or
 * wrong-kind field, an unknown field kind or command, or a section that asks for nothing throws, and the projection is
 * refused rather than drawn half right.
 */

/** The most open times the page offers (moveTimes keeps the first 48). */
private const val MOST_TIMES = 48

internal fun moveSheet(s: JSONObject): MoveSheetModel {
    val times =
        s.getJSONArray("times").objects().map {
            MoveTime(it.text("label"), it.whole("start").also { t -> require(t > 0) })
        }
    require(times.size <= MOST_TIMES) { "More open times than the page offers" }
    return MoveSheetModel(
        id = s.text("id").also { require(it.isNotEmpty()) { "A move without its session" } },
        title = s.text("title"),
        note = s.text("note"),
        times = times,
        none = s.text("none"),
    )
}

internal fun disruptSheet(s: JSONObject): DisruptSheetModel {
    val sections = s.getJSONArray("sections").objects().map { section(it) }
    require(sections.map { it.key }.toSet().size == sections.size) { "A section twice" }
    return DisruptSheetModel(s.text("title"), s.text("note"), sections)
}

private fun section(s: JSONObject): DisruptSection {
    val command = s.text("command")
    require(command == DISRUPT_SHEET || command == "energy") { "Unknown disrupt command $command" }
    val form =
        if (s.has("form")) s.getJSONArray("form").objects().map { input(it) } else emptyList()
    val options =
        if (s.has("options"))
            s.getJSONArray("options").objects().map {
                val v = it.get("value")
                require((v is Number && v.toDouble() == Math.rint(v.toDouble())) || v is String) {
                    "A choice's value is a whole number or a word"
                }
                DisruptOption(if (v is Number) v.toInt() else v, it.text("label"))
            }
        else emptyList()
    val param = if (s.has("param")) s.text("param") else null
    val button = if (s.has("button")) s.text("button") else null
    // what each fold offers: choices go under a parameter; a form needs its button; nothing at all
    // is no fold
    require((param != null) == options.isNotEmpty()) { "Choices without their parameter" }
    require(form.isEmpty() || button != null) { "A form without its button" }
    require(options.isNotEmpty() || button != null) { "A section that offers nothing" }
    require(form.map { it.key }.toSet().size == form.size) { "A field twice" }
    return DisruptSection(
        key = s.text("key").also { require(it.isNotEmpty()) },
        title = s.text("title"),
        open = if (s.has("open")) s.getBoolean("open") else false,
        command = command,
        param = param,
        form = form,
        options = options,
        button = button,
        note = if (s.has("note")) s.text("note") else "",
    )
}

private fun input(f: JSONObject): DisruptInput {
    val key = f.text("key")
    val label = f.text("label")
    val group = f.getInt("group").also { require(it == 0 || it == 1) { "A form has two rows" } }
    return when (val kind = f.text("kind")) {
        "date" ->
            DisruptDate(
                key,
                label,
                f.text("value").also {
                    require(Regex("""\d{4}-\d{2}-\d{2}""").matches(it)) { "A date is YYYY-MM-DD" }
                },
                group,
            )
        "number" ->
            DisruptNumber(
                key,
                label,
                f.whole("value").toInt(),
                if (f.has("min")) f.whole("min").toInt() else null,
                if (f.has("step")) f.whole("step").toInt().also { require(it > 0) } else null,
                group,
            )
        "time" ->
            DisruptTime(
                key,
                label,
                f.whole("value").toInt().also {
                    require(it in 0 until DAY_MINUTES) { "A time is minutes after midnight" }
                },
                f.text("text"),
                group,
            )
        else -> throw IllegalArgumentException("Unknown field kind $kind")
    }
}

private const val DAY_MINUTES = 1440

private fun JSONObject.text(key: String): String =
    get(key) as? String ?: throw IllegalArgumentException("$key is not text")

/** A whole number, as the page writes one (no decimal part). */
private fun JSONObject.whole(key: String): Long {
    val v = get(key) as? Number ?: throw IllegalArgumentException("$key is not a number")
    val d = v.toDouble()
    require(d.isFinite() && d == Math.rint(d)) { "$key is not a whole number" }
    return v.toLong()
}
