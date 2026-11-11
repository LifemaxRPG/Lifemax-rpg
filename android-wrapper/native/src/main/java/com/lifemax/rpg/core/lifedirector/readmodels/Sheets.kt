package com.lifemax.rpg.core.lifedirector.readmodels

import com.lifemax.rpg.core.lifedirector.PageValues
import com.lifemax.rpg.core.lifedirector.PageValues.number
import com.lifemax.rpg.core.lifedirector.PageValues.text
import com.lifemax.rpg.core.lifedirector.readmodels.Js.get
import com.lifemax.rpg.core.lifedirector.readmodels.Js.n
import com.lifemax.rpg.core.lifedirector.readmodels.Js.obj
import com.lifemax.rpg.core.lifedirector.readmodels.Js.s
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import kotlin.math.ceil

/** A field of Something came up's work part: minutes after midnight for a time. */
data class DisruptField(
    val key: String,
    val kind: String,
    val label: String,
    val value: JsonValue,
    val group: Int,
    val text: String? = null,
    val min: Double? = null,
    val step: Double? = null,
)

/** One part of Something came up (`disruptParts`, RM-34). */
data class DisruptPart(
    val key: String,
    val title: String,
    val command: String,
    val open: Boolean = false,
    val button: String? = null,
    val param: String? = null,
    val options: List<FieldOption>? = null,
    val note: String? = null,
    val form: List<DisruptField>? = null,
)

/** `nativeSheet('disrupt')` (RM-34). */
data class DisruptSheet(val title: String, val note: String, val sections: List<DisruptPart>) {
    fun json(): JsonValue =
        obj(
            "kind" to s("disrupt"),
            "title" to s(title),
            "note" to s(note),
            "sections" to Js.list(sections.map(::part)),
        )

    private fun part(d: DisruptPart): JsonValue =
        obj(
            "key" to s(d.key),
            "title" to s(d.title),
            "open" to if (d.open) JsonBool.TRUE else null,
            "command" to s(d.command),
            "button" to d.button?.let(::s),
            "param" to d.param?.let(::s),
            "options" to
                d.options?.let { o ->
                    Js.list(o.map { obj("value" to it.value, "label" to s(it.label)) })
                },
            "note" to d.note?.let(::s),
            "form" to
                d.form?.let { f ->
                    Js.list(
                        f.map {
                            obj(
                                "key" to s(it.key),
                                "kind" to s(it.kind),
                                "label" to s(it.label),
                                "value" to it.value,
                                "text" to it.text?.let(::s),
                                "min" to it.min?.let(::n),
                                "step" to it.step?.let(::n),
                                "group" to n(it.group),
                            )
                        }
                    )
                },
        )
}

/** A time a session can move to: the page's label and the start (epoch seconds). */
data class MoveTime(val label: String, val start: Double)

/** `nativeSheet('move', id)` (RM-35). */
sealed interface MoveSheet {
    fun json(): JsonValue

    /** The Move sheet of a planned session: [id] as stored. */
    data class Move(
        val id: JsonValue?,
        val title: String,
        val times: List<MoveTime>,
        val none: String,
        val note: String,
    ) : MoveSheet {
        override fun json(): JsonValue =
            obj(
                "kind" to s("move"),
                "id" to id,
                "title" to s(title),
                "times" to
                    Js.list(times.map { obj("label" to s(it.label), "start" to n(it.start)) }),
                "none" to s(none),
                "note" to s(note),
            )
    }

    /** No planned session has that id (or it is no longer planned). */
    data object Gone : MoveSheet {
        override fun json(): JsonValue = obj("kind" to s("move"), "gone" to JsonBool.TRUE)
    }
}

/** RM-34, RM-35: `disruptParts`, `moveTimes` and `moveModel` (logic.js:2205-2241, 3392-3397). */
internal object Sheets {
    private val PLANNED = JsonString("PLANNED")
    private val FREE = JsonString("FREE")
    private const val QUARTER = 900.0
    private const val HALF_HOUR = 1800.0
    private const val MOST_TIMES = 48
    private const val MINUTE = 60.0
    private const val FROM = 17 * 60.0
    private const val UNTIL = 21 * 60.0
    private const val STEP = 5.0
    private val HOURS = listOf(1, 2, 3, 4, 6)
    private val SICK_DAYS = listOf(1, 2, 3)
    private val ENERGY = listOf("LOW" to "low", "NORMAL" to "normal", "HIGH" to "high")

    fun disrupt(p: DirectorPanels): DisruptSheet {
        fun ph(k: String) = p.words.phrase("disruptParts", k)
        val today = p.clock.dateKey(p.models.nowEpoch, p.zone())
        fun time(key: String, label: String, m: Double) =
            DisruptField(key, "time", label, n(m), 1, text = p.clock.hm(m))
        val parts =
            listOf(
                DisruptPart(
                    "work",
                    ph("workTitle"),
                    "disrupt",
                    open = true,
                    button = ph("workButton"),
                    form =
                        listOf(
                            DisruptField("date", "date", ph("date"), s(today), 0),
                            DisruptField("commute", "number", ph("travel"), n(0.0), 0, min = 0.0, step = STEP),
                            time("from", ph("from"), FROM),
                            time("until", ph("until"), UNTIL),
                        ),
                ),
                DisruptPart(
                    "block",
                    ph("blockTitle"),
                    "disrupt",
                    param = "hours",
                    options = HOURS.map { FieldOption(n(it), it.toString() + ph("hourMark")) },
                ),
                DisruptPart("day", ph("dayTitle"), "disrupt", button = ph("dayButton")),
                DisruptPart(
                    "sick",
                    ph("sickTitle"),
                    "disrupt",
                    param = "days",
                    options =
                        SICK_DAYS.map {
                            FieldOption(n(it), it.toString() + ph("day") + if (it > 1) ph("plural") else "")
                        },
                    note = ph("sickNote"),
                ),
                DisruptPart(
                    "energy",
                    ph("energyTitle"),
                    "energy",
                    param = "level",
                    options = ENERGY.map { (v, k) -> FieldOption(s(v), ph(k)) },
                    note = ph("energyNote"),
                ),
            )
        return DisruptSheet(p.words.disruptTitle, p.words.disruptNote, parts)
    }

    /** `nativeSheet('move', id)`: the planned session [id]'s Move sheet, else gone. */
    fun move(p: DirectorPanels, id: String): MoveSheet {
        val list = Js.items(get(p.models.schedule, "scheduledInstances"), "scheduledInstances")
        val k = list.indexOfFirst { get(it, "instanceId") == s(id) }
        if (k < 0 || get(list[k], "lifecycleState") != PLANNED) return MoveSheet.Gone
        val inst = list[k]
        fun ph(key: String) = p.words.phrase("moveModel", key)
        val minutes = (number(get(inst, "endEpoch")) - number(get(inst, "startEpoch"))) / MINUTE
        return MoveSheet.Move(
            id = get(inst, "instanceId"),
            title = ph("titleOpen") + text(get(inst, "title")) + ph("titleClose"),
            times = times(p, inst),
            none = ph("none"),
            note = ph("notePrefix") + p.mins(minutes) + ph("noteTail"),
        )
    }

    /**
     * RM-35 `moveTimes(inst)`: every half hour of free time (or of the session's own blocks), from
     * the next quarter hour after the next 15 minutes on, where the session fits, on every shown
     * day; the first 48, each as `Wed 9:30 AM`.
     */
    private fun times(p: DirectorPanels, inst: JsonValue): List<MoveTime> {
        val now = p.models.nowEpoch
        val start = get(inst, "startEpoch")
        val dur = number(get(inst, "endEpoch")) - number(start)
        val id = get(inst, "instanceId")
        val out = ArrayList<Double>()
        val earliest = ceil((now + QUARTER) / QUARTER) * QUARTER
        for (d in p.models.horizonDays()) {
            val blocks =
                p.models
                    .dayBlocks(d)
                    .filter {
                        it.kind == FREE ||
                            (it.session != null && PageValues.same(it.session.instanceId, id))
                    }
                    .sortedWith { a, b -> PageValues.byDifference(a.startEpoch - b.startEpoch) }
            val merged = ArrayList<DoubleArray>()
            for (b in blocks) {
                val l = merged.lastOrNull()
                if (l != null && l[1] >= b.startEpoch) l[1] = Math.max(l[1], b.endEpoch)
                else merged += doubleArrayOf(b.startEpoch, b.endEpoch)
            }
            for (g in merged) {
                var t = Math.max(g[0], earliest)
                while (t + dur <= g[1]) {
                    if (!(start is JsonNumber && start.value == t)) out += t
                    t += HALF_HOUR
                }
            }
        }
        val zone = p.zone()
        val space = p.words.phrase("moveTimes", "space")
        return out.take(MOST_TIMES).map {
            MoveTime(p.clock.day(it, zone).replace(Regex(",.*"), "") + space + p.clock.time(it, zone), it)
        }
    }
}
