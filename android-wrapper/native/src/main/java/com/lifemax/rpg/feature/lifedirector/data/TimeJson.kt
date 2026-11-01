package com.lifemax.rpg.feature.lifedirector.data

import com.lifemax.rpg.core.data.objects
import com.lifemax.rpg.core.designsystem.form.ClockText
import com.lifemax.rpg.feature.lifedirector.domain.BuildCard
import com.lifemax.rpg.feature.lifedirector.domain.CommitmentDot
import com.lifemax.rpg.feature.lifedirector.domain.CommitmentRow
import com.lifemax.rpg.feature.lifedirector.domain.Commitments
import com.lifemax.rpg.feature.lifedirector.domain.DialFaceSpec
import com.lifemax.rpg.feature.lifedirector.domain.DirectorClock
import com.lifemax.rpg.feature.lifedirector.domain.DirectorTime
import com.lifemax.rpg.feature.lifedirector.domain.FirstRun
import com.lifemax.rpg.feature.lifedirector.domain.FirstRunArc
import com.lifemax.rpg.feature.lifedirector.domain.FirstRunKind
import com.lifemax.rpg.feature.lifedirector.domain.MinuteRange
import com.lifemax.rpg.feature.lifedirector.domain.RoutineRow
import com.lifemax.rpg.feature.lifedirector.domain.Routines
import com.lifemax.rpg.feature.lifedirector.domain.SetupStep
import com.lifemax.rpg.feature.lifedirector.domain.SetupStepState
import com.lifemax.rpg.feature.lifedirector.domain.SleepLabels
import com.lifemax.rpg.feature.lifedirector.domain.TimeButton
import com.lifemax.rpg.feature.lifedirector.domain.TimeGoals
import com.lifemax.rpg.feature.lifedirector.domain.TimeSleep
import com.lifemax.rpg.feature.lifedirector.domain.WeekendSleep
import org.json.JSONArray
import org.json.JSONObject

/*
 * My time, the first-run card and the frame's clock style (phase 4B). Strict like the rest of the projection: a missing or
 * wrong-kind field, a time off the day, a stepper whose words do not cover its stops, throws and the projection is refused. A
 * page from before 4B sends no `time` and no `firstRun` (nothing to draw) and no clock style (read from its own words).
 */

/**
 * The frame's clock; [DirectorClock.h12] and [DirectorClock.locale] are what a picked time is
 * written with. A page from before 4B sends neither: its clock's own words say which style it
 * writes.
 */
internal fun JSONObject.directorClock(): DirectorClock {
    val c = getJSONObject("clock")
    val time = c.text("time")
    return DirectorClock(
        time = time,
        day = c.text("day"),
        h12 = if (c.has("h12")) c.bool("h12") else ClockText.is12h(time),
        locale = if (c.has("locale")) c.text("locale") else "",
    )
}

internal fun JSONObject.time(): DirectorTime? {
    if (!has("time") || isNull("time")) return null
    val t = getJSONObject("time")
    val first = t.bool("first")
    val steps =
        t.getJSONArray("steps").objects().map {
            SetupStep(
                it.text("label"),
                when (val s = it.text("state")) {
                    "ok" -> SetupStepState.OK
                    "cur" -> SetupStepState.CUR
                    "" -> SetupStepState.TODO
                    else -> throw IllegalArgumentException("Unknown step state $s")
                },
            )
        }
    require(first || steps.isEmpty()) { "Set-up's steps after set-up" }
    val build = t.optObject("build")
    val replan = t.optObject("replan")
    require((build != null) == first && (replan != null) == !first) {
        "Build my week before set-up is done, Re-plan after"
    }
    return DirectorTime(
        first = first,
        steps = steps,
        lede = t.text("lede"),
        sleep = sleep(t.getJSONObject("sleep")),
        commitments = commitments(t.getJSONObject("commitments")),
        routines = routines(t.getJSONObject("routines")),
        goals =
            t.getJSONObject("goals").let { g ->
                TimeGoals(
                    title = g.text("title"),
                    chips = g.getJSONArray("chips").texts(),
                    empty = g.text("empty"),
                    add = g.getJSONObject("add").button(),
                    manage = g.optText("manage"),
                )
            },
        build =
            build?.let {
                BuildCard(it.text("title"), it.text("text"), it.text("label"), it.bool("enabled"))
            },
        replan = replan?.text("label"),
    )
}

internal fun JSONObject.firstRun(): FirstRun? {
    if (!has("firstRun") || isNull("firstRun")) return null
    val f = getJSONObject("firstRun")
    return FirstRun(
        arcs =
            f.getJSONArray("arcs").objects().map {
                val outer =
                    when (val r = it.text("ring")) {
                        "outer" -> true
                        "inner" -> false
                        else -> throw IllegalArgumentException("Unknown ring $r")
                    }
                val kind =
                    when (val k = it.text("kind")) {
                        "sleep" -> FirstRunKind.SLEEP
                        "work" -> FirstRunKind.WORK
                        "session" -> FirstRunKind.SESSION
                        else -> throw IllegalArgumentException("Unknown demo arc $k")
                    }
                require(outer == (kind != FirstRunKind.SESSION)) {
                    "Sleep and work are the outer ring, sessions the inner"
                }
                FirstRunArc(outer, kind, it.int("from"), it.int("to")).also { a ->
                    requireRange(a.from, a.to)
                }
            },
        title = f.text("title"),
        text = f.text("text"),
        button = f.text("button"),
        section = f.text("section"),
        face = f.getJSONObject("face").face(),
    )
}

private fun sleep(s: JSONObject): TimeSleep {
    val step = s.int("step")
    require(step > 0 && DAY % step == 0) { "The mini dial's step must divide the day" }
    val clock = s.getJSONArray("clock").texts()
    val lengths = s.getJSONArray("lengths").texts()
    require(clock.size == DAY / step && lengths.size == DAY / step) {
        "The mini dial needs words for every stop"
    }
    val w = s.getJSONObject("weekend")
    val l = s.getJSONObject("labels")
    return TimeSleep(
        title = s.text("title"),
        note = s.text("note"),
        saved = s.bool("saved"),
        bed = s.minute("bed"),
        wake = s.minute("wake"),
        bedText = s.text("bedText"),
        wakeText = s.text("wakeText"),
        length = s.text("length"),
        weekend = WeekendSleep(w.bool("on"), w.minute("bed"), w.minute("wake")),
        labels =
            SleepLabels(
                centre = l.text("centre"),
                bed = l.text("bed"),
                wake = l.text("wake"),
                weekend = l.text("weekend"),
                weekendBed = l.text("weekendBed"),
                weekendWake = l.text("weekendWake"),
                bedHandle = l.text("bedHandle"),
                wakeHandle = l.text("wakeHandle"),
                group = l.text("group"),
            ),
        save = s.getJSONObject("save").button(),
        step = step,
        context =
            s.getJSONArray("context").objects().map {
                MinuteRange(it.int("from"), it.int("to")).also { c -> requireRange(c.from, c.to) }
            },
        clock = clock,
        lengths = lengths,
        face = s.getJSONObject("face").face(),
    )
}

private fun commitments(c: JSONObject) =
    Commitments(
        title = c.text("title"),
        rows =
            c.getJSONArray("rows").objects().map {
                CommitmentRow(
                    id =
                        it.text("id").also { id ->
                            require(id.isNotEmpty()) { "A commitment without an id" }
                        },
                    title = it.text("title"),
                    dot =
                        when (val d = it.text("dot")) {
                            "work" -> CommitmentDot.WORK
                            "block" -> CommitmentDot.BLOCK
                            else -> throw IllegalArgumentException("Unknown commitment dot $d")
                        },
                    line = it.text("line"),
                )
            },
        empty = c.text("empty"),
        add = c.text("add"),
    )

private fun routines(r: JSONObject): Routines {
    val meals = r.getJSONObject("meals")
    return Routines(
        title = r.text("title"),
        text = r.text("text"),
        rows = r.getJSONArray("rows").objects().map { it.routine(meals = false) },
        meals = meals.routine(meals = true),
        note = r.text("note"),
    )
}

private fun JSONObject.routine(meals: Boolean): RoutineRow {
    val min = int("min")
    val max = int("max")
    val step = int("step")
    require(step > 0 && min <= max && (max - min) % step == 0) { "A stepper off its steps" }
    val stops = getJSONArray("stops").texts()
    require(stops.size == (max - min) / step + 1) { "A stepper needs words for every stop" }
    return RoutineRow(
        key = text("key"),
        label = text("label"),
        hint = text("hint"),
        min = min,
        max = max,
        step = step,
        value = int("value"),
        text = text("text"),
        stops = stops,
        less = text("less"),
        more = text("more"),
        on = if (meals) bool("on") else null,
        toggle = if (meals) text("toggle") else null,
    )
}

private fun JSONObject.face() =
    DialFaceSpec(dialRadii(getJSONObject("radii")), hourWords(getJSONArray("hourWords")))

private fun JSONObject.button() = TimeButton(text("label"), bool("primary"))

private fun requireRange(from: Int, to: Int) =
    require(from in 0 until DAY && to > from && to <= from + DAY) { "A span off the day" }

private fun JSONObject.minute(key: String): Int =
    int(key).also { require(it in 0 until DAY) { "$key is not a time of day" } }

private fun JSONObject.text(key: String): String =
    get(key) as? String ?: throw IllegalArgumentException("$key is not text")

private fun JSONObject.optText(key: String): String? =
    if (has(key) && !isNull(key)) text(key) else null

private fun JSONObject.optObject(key: String): JSONObject? =
    if (has(key) && !isNull(key))
        get(key) as? JSONObject ?: throw IllegalArgumentException("$key is not an object")
    else null

private fun JSONObject.bool(key: String): Boolean =
    get(key) as? Boolean ?: throw IllegalArgumentException("$key is not true or false")

/** A whole number: minutes and limits on the wire are whole (A18); 3.5 or "3" is malformed. */
private fun JSONObject.int(key: String): Int =
    when (val v = get(key)) {
        is Int -> v
        is Long -> v.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
        is Number ->
            v.toDouble().takeIf { it == Math.rint(it) && kotlin.math.abs(it) < 1e9 }?.toInt()
        else -> null
    } ?: throw IllegalArgumentException("$key is not a whole number")

private fun JSONArray.texts(): List<String> =
    (0 until length()).map { get(it) as? String ?: throw IllegalArgumentException("not text") }

private const val DAY = 1440
