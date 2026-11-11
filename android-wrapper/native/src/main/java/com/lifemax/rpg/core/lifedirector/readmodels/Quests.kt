package com.lifemax.rpg.core.lifedirector.readmodels

import com.lifemax.rpg.core.lifedirector.PageValues
import com.lifemax.rpg.core.lifedirector.PageValues.number
import com.lifemax.rpg.core.lifedirector.PageValues.text
import com.lifemax.rpg.core.lifedirector.PageValues.truthy
import com.lifemax.rpg.core.lifedirector.readmodels.Js.get
import com.lifemax.rpg.core.lifedirector.readmodels.Js.obj
import com.lifemax.rpg.core.lifedirector.readmodels.Js.s
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/** One quest row (RM-25). [title] and [why] as stored (null: absent). */
data class QuestRow(
    val title: JsonValue?,
    val whenText: String,
    val status: String,
    val tone: String?,
    val why: JsonValue?,
)

/** The milestones LifeMax reports, unlocked ones only (RM-25). */
data class MilestoneRows(val summary: String, val note: String, val rows: List<Milestone>)

/** `nativeQuests()` (RM-25). [head] is the page's `QUEST_HEAD`, or the not-connected head. */
data class QuestsPanel(
    val head: JsonValue,
    val rows: List<QuestRow>,
    val passive: List<QuestRow>,
    val empty: String,
    val milestones: MilestoneRows?,
    private val earned: String,
    private val inProgress: String,
) {
    fun json(): JsonValue =
        obj(
            "head" to head,
            "rows" to Js.list(rows.map(::row)),
            "passive" to Js.list(passive.map(::row)),
            "empty" to s(empty),
            "milestones" to
                (milestones?.let { m ->
                    obj(
                        "summary" to s(m.summary),
                        "note" to s(m.note),
                        "rows" to
                            Js.list(
                                m.rows.map {
                                    obj(
                                        "title" to it.title?.let(::s),
                                        "desc" to it.desc?.let(::s),
                                        "earned" to Js.b(it.done),
                                        "status" to s(if (it.done) earned else inProgress),
                                    )
                                }
                            ),
                    )
                } ?: JsonNull),
        )

    private fun row(r: QuestRow): JsonValue =
        obj(
            "title" to r.title,
            "when" to s(r.whenText),
            "status" to s(r.status),
            "tone" to r.tone?.let(::s),
            "why" to r.why,
        )
}

/** RM-25: `questsModel` and `nativeQuests` (logic.js:1434-1456, 3297-3303). */
internal object Quests {
    private val PASSIVE = JsonString("PASSIVE")
    private val COMPLETED = JsonString("COMPLETED")
    private val LOCKED = JsonString("LOCKED")
    private val DAILY = JsonString("DAILY")

    fun panel(p: DirectorPanels): QuestsPanel {
        fun ph(k: String) = p.words.phrase("questsModel", k)
        val cap = p.host.capabilities()
        val head =
            if (cap.connected) p.words.questHead
            else
                obj(
                    "title" to s(ph("notDetected")),
                    "text" to
                        s(ph("missing") + cap.missing.joinToString(ph("comma")) + ph("period")),
                    "open" to s(""),
                )
        val sched = get(p.state, "schedule")
        val done = ph("earned")
        val going = ph("inProgress")
        if (!truthy(sched))
            return QuestsPanel(head, emptyList(), emptyList(), "", null, done, going)
        val diagnostics = Js.itemsOrNone(get(sched, "unscheduledDiagnostics"), "diagnostics")
        val rows =
            Js.items(get(sched, "questCoverage"), "questCoverage")
                .filter { !text(get(it, "questId")).endsWith(ph("nextSuffix")) }
                .map { q -> row(p, q, diagnostics) }
        val passive =
            Js.itemsOrNone(get(sched, "passiveReminders"), "passiveReminders").map {
                QuestRow(get(it, "title"), ph("tracked"), ph("allDay"), null, null)
            }
        val ms = p.host.milestones().filter { !it.locked }
        val milestones =
            if (ms.isEmpty()) null
            else
                MilestoneRows(
                    ms.count { it.done }.toString() + ph("of") + ms.size + ph("earnedTail"),
                    p.words.milestoneNote,
                    ms,
                )
        return QuestsPanel(head, rows, passive, ph("empty"), milestones, done, going)
    }

    private fun row(p: DirectorPanels, q: JsonValue, diagnostics: List<JsonValue>): QuestRow {
        fun ph(k: String) = p.words.phrase("questsModel", k)
        val id = get(q, "questId")
        val diag =
            diagnostics.filter {
                Js.includes(Js.itemsOrNone(get(it, "linkedQuestIds"), "linkedQuestIds"), id) ||
                    PageValues.same(get(it, "taskId"), id)
            }
        val status = get(q, "status")
        val covered = truthy(get(q, "covered"))
        val sessions = get(q, "scheduledSessions")
        val statusText =
            when {
                status == PASSIVE -> ph("allDayReminder")
                status == COMPLETED -> ph("donePeriod")
                covered ->
                    text(sessions) +
                        ph("session") +
                        (if (sessions is JsonNumber && sessions.value == 1.0) ""
                        else ph("plural")) +
                        ph("scheduled")
                diag.isNotEmpty() -> ph("couldNotFit")
                status == LOCKED -> ph("locked")
                else -> text(sessions) + ph("scheduled")
            }
        val deadline = get(q, "deadlineEpoch")
        val whenText =
            when {
                get(q, "recurrence") == DAILY -> ph("daily")
                truthy(deadline) -> ph("due") + p.clock.day(number(deadline), p.zone())
                else -> ph("noDeadline")
            }
        val tone =
            when {
                covered || status == COMPLETED -> ph("toneOk")
                status == PASSIVE -> ph("tonePassive")
                else -> ph("toneNo")
            }
        val why: JsonValue? =
            if (diag.isNotEmpty() && !covered) get(diag[0], "humanExplanation") else s("")
        return QuestRow(get(q, "title"), whenText, statusText, tone, why)
    }
}
