package com.lifemax.rpg.core.lifedirector.readmodels

import com.lifemax.rpg.core.lifedirector.DemandTables
import com.lifemax.rpg.core.lifedirector.PageValues
import com.lifemax.rpg.core.lifedirector.PageValues.number
import com.lifemax.rpg.core.lifedirector.PageValues.text
import com.lifemax.rpg.core.lifedirector.PageValues.truthy
import com.lifemax.rpg.core.lifedirector.readmodels.Js.get
import com.lifemax.rpg.core.lifedirector.readmodels.Js.items
import com.lifemax.rpg.core.lifedirector.readmodels.Js.n
import com.lifemax.rpg.core.lifedirector.readmodels.Js.obj
import com.lifemax.rpg.core.lifedirector.readmodels.Js.s
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/** A goal card's share bars (`nativeGoals` share): 0..1 with the page's rounded percentages. */
data class GoalShare(
    val target: Double,
    val targetText: String,
    val scheduled: Double,
    val scheduledText: String,
    val line: String,
)

/** One of a clarification's choices: an archetype (as the plan names it) and its title. */
data class ClarifyOption(val arch: JsonValue, val label: String)

/** A goal's card (RM-24). [id], [title] and [priority] are the stored goal's (null: absent). */
data class GoalCard(
    val id: JsonValue?,
    val title: JsonValue?,
    val sub: String,
    val paused: Boolean,
    val priority: JsonValue?,
    val share: GoalShare?,
    val warning: String?,
    val clarify: List<ClarifyOption>?,
    val pause: String,
    val remove: String,
)

/** `nativeGoals()` (RM-24): the explanation, the priorities, the cards and the empty state. */
data class GoalsPanel(
    val explain: JsonValue,
    val priorities: List<Choice>,
    val cards: List<GoalCard>,
    val empty: JsonValue?,
    val add: String,
    val removeConfirm: String,
    val clarifyText: String,
) {
    fun json(): JsonValue =
        obj(
            "explain" to explain,
            "priorities" to
                Js.list(priorities.map { obj("value" to it.value, "label" to s(it.label)) }),
            "cards" to Js.list(cards.map(::card)),
            "empty" to (empty ?: JsonNull),
            "add" to s(add),
            "removeConfirm" to s(removeConfirm),
        )

    private fun card(c: GoalCard): JsonValue =
        obj(
            "id" to c.id,
            "title" to c.title,
            "sub" to s(c.sub),
            "paused" to Js.b(c.paused),
            "priority" to c.priority,
            "share" to
                (c.share?.let {
                    obj(
                        "target" to n(it.target),
                        "targetText" to s(it.targetText),
                        "scheduled" to n(it.scheduled),
                        "scheduledText" to s(it.scheduledText),
                        "line" to s(it.line),
                    )
                } ?: JsonNull),
            "warning" to (c.warning?.let(::s) ?: JsonNull),
            "clarify" to
                (c.clarify?.let { o ->
                    obj(
                        "text" to s(clarifyText),
                        "options" to
                            Js.list(o.map { obj("arch" to it.arch, "label" to s(it.label)) }),
                    )
                } ?: JsonNull),
            "pause" to s(c.pause),
            "remove" to s(c.remove),
        )
}

/** RM-23, RM-24: `goalShare`, `goalCards` and `nativeGoals` (logic.js:1395-1420, 3622-3634). */
internal object Goals {
    private val PAUSED = JsonString("PAUSED")
    private const val DEFAULT_PRIORITY = 3.0
    private val PRIORITY_ORDER = listOf(5, 4, 3, 2, 1)

    /** A goal's allocation: the stored one, and the share `goalShare` puts in its place. */
    private class Allocation(val stored: JsonValue, val targetShare: JsonValue?) {
        fun share(): JsonValue? = targetShare ?: get(stored, "targetShare")
    }

    fun panel(p: DirectorPanels): GoalsPanel {
        val w = p.words
        fun ph(k: String) = w.phrase("nativeGoals", k)
        val cards = cards(p)
        return GoalsPanel(
            explain = w.goalsExplain,
            priorities = PRIORITY_ORDER.map { Choice(n(it), w.priorities[it]) },
            cards = cards,
            empty = if (cards.isEmpty()) w.goalsEmpty else null,
            add = ph("add"),
            removeConfirm = w.goalRemoveConfirm,
            clarifyText = w.clarifyText,
        )
    }

    /** `Math.round((v || 0) * 100)` as text, with the page's `%`. */
    private fun pct(p: DirectorPanels, v: JsonValue?): String =
        PageValues.numberText(PageValues.round(orZero(v) * HUNDRED)) +
            p.words.phrase("nativeGoals", "percent")

    private fun orZero(v: JsonValue?): Double = if (truthy(v)) number(v) else 0.0

    private fun unit(v: JsonValue?): Double = Math.min(1.0, Math.max(0.0, orZero(v)))

    private fun cards(p: DirectorPanels): List<GoalCard> {
        val st = p.state
        val s = get(st, "schedule")
        val sched = truthy(s)
        val alloc = if (sched) get(s, "goalAllocation") else JsonArray.EMPTY
        val warn = if (sched) get(s, "trajectoryWarnings") else JsonArray.EMPTY
        val clar = if (sched) get(s, "clarificationPrompts") else JsonArray.EMPTY
        val goals = items(get(st, "goals"), "goals")
        val w = p.words
        fun ph(k: String) = w.phrase("goalCards", k)
        return goals.map { g ->
            val a = share(st, g, alloc)
            val tw = items(warn, "trajectoryWarnings").firstOrNull { sameId(get(it, "goalId"), g) }
            val cp =
                items(clar, "clarificationPrompts").firstOrNull { sameId(get(it, "goalId"), g) }
            val archId = get(g, "archetypeId")
            val arch = if (truthy(archId)) DemandTables.archetypes[text(archId)] else null
            val paused = get(g, "status") == PAUSED
            val level = get(g, "level").let { if (truthy(it)) it else s(ph("level")) }
            val sub =
                (arch?.title ?: ph("custom")) +
                    ph("sep") +
                    Js.lower(level) +
                    if (paused) ph("pausedTail") else ""
            val line = a?.let { line(p, it) } ?: ""
            GoalCard(
                id = get(g, "id"),
                title = get(g, "title"),
                sub = sub,
                paused = paused,
                priority = get(g, "priority"),
                share =
                    a?.let {
                        GoalShare(
                            unit(it.share()),
                            pct(p, it.share()),
                            unit(get(it.stored, "actualShare")),
                            pct(p, get(it.stored, "actualShare")),
                            line,
                        )
                    },
                warning =
                    tw?.let {
                        text(get(it, "message")) +
                            ph("projected") +
                            p.clock.day(number(get(it, "projectedCompletionEpoch")), p.zone()) +
                            ph("period")
                    },
                clarify = cp?.let { clarify(it) },
                pause = if (paused) ph("resume") else ph("pause"),
                remove = w.phrase("nativeGoals", "remove"),
            )
        }
    }

    private fun sameId(id: JsonValue?, g: JsonValue): Boolean = PageValues.same(id, get(g, "id"))

    /**
     * RM-23 `goalShare(g, alloc)`: the goal's allocation; where its target share is not truthy (a
     * local repair left it at 0), the engine's own rule in its place: the goal's priority (3 when
     * not truthy) over the sum of every allocated goal's (3 for each without one), at least 1.
     */
    private fun share(st: JsonValue?, g: JsonValue, alloc: JsonValue?): Allocation? {
        val list = items(alloc, "goalAllocation")
        val a0 = list.firstOrNull { PageValues.same(get(it, "goalId"), get(g, "id")) } ?: return null
        if (truthy(get(a0, "targetShare"))) return Allocation(a0, null)
        val goals = items(get(st, "goals"), "goals")
        var sum: JsonValue = n(0.0)
        for (x in list) {
            val gg = goals.firstOrNull { PageValues.same(get(it, "id"), get(x, "goalId")) }
            val pr = gg?.let { get(it, "priority") }
            sum = Js.add(sum, if (truthy(pr)) pr else n(DEFAULT_PRIORITY))
        }
        val own = get(g, "priority").let { if (truthy(it)) number(it) else DEFAULT_PRIORITY }
        return Allocation(a0, n(own / Math.max(1.0, number(sum))))
    }

    /** The share's line: sessions, minutes this week and each template's count. */
    private fun line(p: DirectorPanels, a: Allocation): String {
        fun ph(k: String) = p.words.phrase("goalCards", k)
        val stored = a.stored
        val templates =
            items(get(stored, "templates"), "templates").map { t ->
                val med = get(t, "med")
                val base = get(t, "base")
                text(get(t, "title")) +
                    ph("space") +
                    text(med) +
                    (if (PageValues.less(med, base)) ph("dash") + text(base) else "") +
                    ph("times")
            }
        return text(get(stored, "plannedSessions")) +
            ph("sessions") +
            p.mins(get(stored, "scheduledMin")) +
            ph("week") +
            templates.joinToString(ph("comma"))
    }

    /** A clarification's options: the plan's suggestions, or the first four archetypes. */
    private fun clarify(cp: JsonValue): List<ClarifyOption> {
        val ids = get(cp, "suggestedTemplateIds")
        val chosen =
            if (truthy(Js.get(ids, "length"))) items(ids, "suggestedTemplateIds")
            else DemandTables.archetypes.keys.take(FIRST_FOUR).map { s(it) }
        return chosen.map { t ->
            val a =
                DemandTables.archetypes[text(t)]
                    ?: throw com.lifemax.rpg.core.lifedirector.PageTypeError(
                        "Cannot read properties of undefined (reading 'title')"
                    )
            ClarifyOption(t, a.title)
        }
    }

    private const val HUNDRED = 100.0
    private const val FIRST_FOUR = 4
}
