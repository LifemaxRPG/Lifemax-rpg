package com.lifemax.rpg.core.lifedirector.readmodels

import com.lifemax.rpg.core.lifedirector.PageTypeError
import com.lifemax.rpg.core.lifedirector.PageValues
import com.lifemax.rpg.core.lifedirector.PageValues.number
import com.lifemax.rpg.core.lifedirector.PageValues.text
import com.lifemax.rpg.core.lifedirector.PageValues.truthy
import com.lifemax.rpg.core.lifedirector.PlanPreferences
import com.lifemax.rpg.core.lifedirector.readmodels.Js.get
import com.lifemax.rpg.core.lifedirector.readmodels.Js.n
import com.lifemax.rpg.core.lifedirector.readmodels.Js.obj
import com.lifemax.rpg.core.lifedirector.readmodels.Js.s
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/** A routine's stepper (RM-29): `routine` in `timeModel`, and the meals' with their switch. */
data class RoutineStepper(
    val spec: RangeSpec,
    val value: JsonValue?,
    val text: String,
    val stops: List<String>,
    val less: String,
    val more: String,
    /** Meals only: whether they are on, and the switch's label. */
    val meals: Pair<Boolean, String>? = null,
) {
    fun json(): JsonValue =
        obj(
            "key" to s(spec.key),
            "label" to s(spec.label),
            "hint" to spec.hint?.let(::s),
            "min" to n(spec.low),
            "max" to n(spec.high),
            "step" to n(spec.step),
            "value" to value,
            "text" to s(text),
            "stops" to Js.list(stops.map(::s)),
            "less" to s(less),
            "more" to s(more),
            "on" to meals?.let { Js.b(it.first) },
            "toggle" to meals?.let { s(it.second) },
        )
}

/** A commitment's row in My time: the rule's id and title as stored, its dot and line. */
data class CommitmentRow(val id: JsonValue?, val title: JsonValue?, val dot: String, val line: String)

/**
 * `nativeTime()` (RM-28 to RM-30): `timeModel()` with what the mini sleep dial needs. Members are
 * kept as the page's JSON ([model]) because the page's own object is what a screen reads; the typed
 * view below names what a native screen draws.
 */
data class TimePanel(val model: JsonObject) {
    fun json(): JsonValue = model

    /** Whether set-up is not finished (the steps and the build card are shown). */
    val first: Boolean
        get() = model["first"] == com.lifemax.rpg.core.save.JsonBool.TRUE

    val sleep: JsonObject
        get() = model["sleep"] as JsonObject
}

/** RM-28 to RM-30: `timeModel`, `daysText`, `ruleText`, `sleepContext`, `nativeTime`. */
internal object MyTime {
    private val SLEEP = JsonString("SLEEP")
    private val PREFERRED = JsonString("PREFERRED")
    private val UNAVAILABLE = JsonString("UNAVAILABLE")
    private val WEEK_SLEEP = JsonString("sleep-wk")
    private val WEEKEND_SLEEP = JsonString("sleep-we")
    private const val DAY = 1440.0
    private const val BED = 1380.0
    private const val WAKE = 420.0
    private const val HOUR = 60.0

    fun rules(p: DirectorPanels): List<JsonValue> =
        Js.itemsOrNone(get(get(p.state, "availability"), "rules"), "rules")

    fun panel(p: DirectorPanels): TimePanel {
        val t = model(p)
        val w = p.words
        val sleep = LinkedHashMap((t["sleep"] as JsonObject).members)
        val labels = LinkedHashMap((sleep.getValue("labels") as JsonObject).members)
        val quarters = (0 until (DAY / w.miniSnap).toInt()).map { it * w.miniSnap }
        sleep["step"] = n(w.miniSnap)
        sleep["context"] = Js.list(sleepContext(p))
        sleep["clock"] = Js.list(quarters.map { s(p.clock.hm(it)) })
        sleep["lengths"] = Js.list(quarters.map { s(p.mins(if (it == 0.0) DAY else it)) })
        sleep["face"] = face(p)
        labels["group"] = s(w.miniGroup)
        sleep["labels"] = JsonObject(labels)
        val out = LinkedHashMap(t.members)
        out["sleep"] = JsonObject(sleep)
        return TimePanel(JsonObject(out))
    }

    /** `nativeFace()`: the dial's radii and its hour words every three hours. */
    fun face(p: DirectorPanels): JsonValue =
        obj(
            "radii" to p.words.dial,
            "hourWords" to
                Js.list(
                    (0 until HOURS step THREE).map {
                        obj("h" to n(it), "word" to s(p.clock.hourWord(it)))
                    }
                ),
        )

    /** RM-30 `sleepContext()`: the work arcs on the mini dial, minutes after midnight. */
    private fun sleepContext(p: DirectorPanels): List<JsonValue> =
        rules(p)
            .filter { r ->
                val kind = get(r, "kind")
                kind != SLEEP &&
                    kind != PREFERRED &&
                    (truthy(get(r, "rotation")) ||
                        Js.itemsOrNone(get(r, "days"), "days").any {
                            PageValues.atLeast(it, n(1)) && PageValues.lessOrEqual(it, n(FRIDAY))
                        })
            }
            .map {
                obj(
                    "from" to get(it, "startMin"),
                    "to" to Js.add(get(it, "startMin"), get(it, "durationMin")),
                )
            }

    /** `(a + b) % 1440` of stored values. */
    private fun endOf(r: JsonValue): Double =
        number(Js.add(get(r, "startMin"), get(r, "durationMin"))) % DAY

    /** RM-28, RM-29 `timeModel()`. */
    private fun model(p: DirectorPanels): JsonObject {
        val w = p.words
        fun ph(k: String) = w.phrase("timeModel", k)
        val st = p.state
        val all = rules(p)
        val sleeps = all.filter { get(it, "kind") == SLEEP }
        val wk = sleeps.firstOrNull { get(it, "id") == WEEK_SLEEP } ?: sleeps.firstOrNull()
        val we = sleeps.firstOrNull { get(it, "id") == WEEKEND_SLEEP }
        val bed: JsonValue? = if (wk != null) get(wk, "startMin") else n(BED)
        val wake: JsonValue = if (wk != null) n(endOf(wk)) else n(WAKE)
        val other = all.filter { get(it, "kind") != SLEEP && get(it, "kind") != PREFERRED }
        val prefs = PlanPreferences.of(get(st, "prefs"))
        val first = !truthy(get(st, "setupComplete"))
        val goals = Js.items(get(st, "goals"), "goals")
        val hasWk = wk != null
        val steps =
            if (!first) emptyList()
            else
                listOf(
                    ph("stepSleep") to if (hasWk) ph("stepOk") else ph("stepCurrent"),
                    ph("stepWork") to
                        when {
                            other.isNotEmpty() -> ph("stepOk")
                            hasWk -> ph("stepCurrent")
                            else -> ""
                        },
                    ph("stepGoals") to
                        when {
                            goals.isNotEmpty() -> ph("stepOk")
                            hasWk -> ph("stepCurrent")
                            else -> ""
                        },
                    ph("stepBuild") to "",
                )
        val length = (number(wake) - number(bed) + DAY) % DAY
        val sleep =
            obj(
                "title" to s(ph("sleepTitle")),
                "note" to s(ph("sleepNote")),
                "saved" to Js.b(hasWk),
                "bed" to bed,
                "wake" to wake,
                "bedText" to s(p.clock.hm(number(bed))),
                "wakeText" to s(p.clock.hm(number(wake))),
                "length" to s(p.mins(if (PageValues.truthy(length)) length else DAY)),
                "weekend" to
                    obj(
                        "on" to Js.b(we != null),
                        "bed" to
                            (if (we != null) get(we, "startMin")
                            else n(number(Js.add(bed, n(HOUR))) % DAY)),
                        "wake" to
                            (if (we != null) n(endOf(we))
                            else n(number(Js.add(wake, n(HOUR))) % DAY)),
                    ),
                "labels" to
                    obj(
                        "centre" to s(ph("sleepCentre")),
                        "bed" to s(if (we != null) ph("weeknightBed") else ph("bedtime")),
                        "wake" to s(ph("wakeUp")),
                        "weekend" to s(ph("weekendToggle")),
                        "weekendBed" to s(ph("weekendBed")),
                        "weekendWake" to s(ph("weekendWake")),
                        "bedHandle" to s(ph("bedtime")),
                        "wakeHandle" to s(ph("wakeUp")),
                    ),
                "save" to
                    obj(
                        "label" to s(if (hasWk) ph("saveSleep") else ph("saveSleepFirst")),
                        "primary" to Js.b(!hasWk),
                    ),
            )
        val commitments =
            obj(
                "title" to s(ph("commitTitle")),
                "rows" to Js.list(other.map { commitmentRow(p, it).let(::rowJson) }),
                "empty" to s(ph("commitEmpty")),
                "add" to s(ph("commitAdd")),
            )
        val meals = routine(p, prefs, w.mealRange)
        val mealsOn = truthy(prefs["mealsEnabled"])
        val mealStepper =
            meals.copy(
                text = if (mealsOn) p.mins(prefs["mealMinutes"]) else ph("off"),
                less = ph("shorter"),
                more = ph("longer"),
                meals = mealsOn to if (mealsOn) ph("turnOff") else ph("turnOn"),
            )
        val routines =
            obj(
                "title" to s(ph("routinesTitle")),
                "text" to s(ph("routinesText")),
                "rows" to Js.list(w.routines.map { routine(p, prefs, it).json() }),
                "meals" to mealStepper.json(),
                "note" to s(ph("routinesNote")),
            )
        val goalsCard =
            obj(
                "title" to s(ph("goalsTitle")),
                "chips" to Js.list(goals.map { get(it, "title") ?: JsonNull }),
                "empty" to s(ph("goalsEmpty")),
                "add" to
                    obj(
                        "label" to s(ph("goalsAdd")),
                        "primary" to Js.b(hasWk && goals.isEmpty()),
                    ),
                "manage" to (if (goals.isNotEmpty()) s(ph("goalsManage")) else JsonNull),
            )
        return obj(
            "first" to Js.b(first),
            "steps" to Js.list(steps.map { (l, st2) -> obj("label" to s(l), "state" to s(st2)) }),
            "lede" to s(if (first) ph("ledeFirst") else ph("lede")),
            "sleep" to sleep,
            "commitments" to commitments,
            "routines" to routines,
            "goals" to goalsCard,
            "build" to
                (if (first)
                    obj(
                        "title" to s(ph("buildTitle")),
                        "text" to s(ph("zone") + text(p.models.zoneValue()) + ph("buildTail")),
                        "label" to s(ph("buildTitle")),
                        "enabled" to Js.b(hasWk),
                    )
                else JsonNull),
            "replan" to (if (first) JsonNull else obj("label" to s(ph("replan")))),
        )
    }

    private fun rowJson(r: CommitmentRow): JsonValue =
        obj("id" to r.id, "title" to r.title, "dot" to s(r.dot), "line" to s(r.line))

    private fun commitmentRow(p: DirectorPanels, r: JsonValue): CommitmentRow {
        val kind = get(r, "kind")
        val label = p.words.kindLabel[text(kind)]?.let(::s) ?: kind
        val dot = if (kind == UNAVAILABLE) "dotBlock" else "dotWork"
        return CommitmentRow(
            get(r, "id"),
            get(r, "title"),
            p.words.phrase("timeModel", dot),
            text(label) + p.words.phrase("timeModel", "sep") + ruleText(p, r),
        )
    }

    /** `routine([key, min, max, step, label, hint])`: a stepper with a label for every stop. */
    private fun routine(p: DirectorPanels, prefs: PlanPreferences, r: RangeSpec): RoutineStepper {
        fun ph(k: String) = p.words.phrase("timeModel", k)
        val value = prefs[r.key]
        val stops = ArrayList<String>()
        var v = r.low
        while (v <= r.high) {
            stops += if (PageValues.truthy(v)) p.mins(v) else ph("off")
            v += r.step
        }
        return RoutineStepper(
            r,
            value,
            if (truthy(value)) p.mins(value) else ph("off"),
            stops,
            ph("less"),
            ph("more"),
        )
    }

    /** RM-29 `ruleText(r)`: its days, its times, and its travel. */
    fun ruleText(p: DirectorPanels, r: JsonValue): String {
        fun ph(k: String) = p.words.phrase("ruleText", k)
        val commute = get(r, "commuteMin")
        return daysText(p, r) +
            ph("sep") +
            p.clock.hm(number(get(r, "startMin"))) +
            ph("dash") +
            p.clock.hm(endOf(r)) +
            if (truthy(commute)) ph("sep") + text(commute) + ph("travel") else ""
    }

    /** RM-29 `daysText(r)`: a rotation, a named pattern, or the day names. */
    fun daysText(p: DirectorPanels, r: JsonValue): String {
        fun ph(k: String) = p.words.phrase("daysText", k)
        val rot = get(r, "rotation")
        if (truthy(rot)) {
            val on = Js.length(get(rot, "activeDays"))
            return ph("rotating") +
                PageValues.numberText(on) +
                ph("onOff") +
                PageValues.numberText(number(get(rot, "cycleLength")) - on) +
                ph("offTail")
        }
        val stored = get(r, "days")
        val days: List<JsonValue> =
            when {
                !truthy(stored) -> emptyList()
                stored is JsonArray -> stored.items
                else -> throw PageTypeError("days.slice(...).sort is not a function")
            }
        val d = sorted(days)
        val joined = Js.join(d, ",")
        val sleep = get(r, "kind") == SLEEP
        return when {
            d.size == WEEK -> ph("everyDay")
            joined == WEEKDAYS -> ph("weekdays")
            joined == WEEKENDS -> ph("weekends")
            sleep && joined == SUN_THU -> ph("sunThu")
            sleep && joined == FRI_SAT -> ph("friSat")
            else -> Js.join(d.map { x -> dayName(p, x)?.let(::s) }, ph("space"))
        }
    }

    /** `DOW[x]`: the name of a day index (its text as a list index), else undefined. */
    private fun dayName(p: DirectorPanels, x: JsonValue): String? {
        val k = text(x)
        val i = k.toIntOrNull() ?: return null
        return if (i.toString() == k) p.words.dayNames.getOrNull(i) else null
    }

    /** `Array.prototype.sort()` without a comparator: by text, undefined last, stable. */
    private fun sorted(items: List<JsonValue>): List<JsonValue> =
        items.sortedWith { a, b -> text(a).compareTo(text(b)) }

    private const val WEEK = 7
    private const val HOURS = 24
    private const val THREE = 3
    private const val FRIDAY = 5.0
    private const val WEEKDAYS = "1,2,3,4,5"
    private const val WEEKENDS = "0,6"
    private const val SUN_THU = "0,1,2,3,4"
    private const val FRI_SAT = "5,6"
}
