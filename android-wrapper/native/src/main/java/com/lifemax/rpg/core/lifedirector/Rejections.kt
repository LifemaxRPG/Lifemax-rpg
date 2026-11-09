package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.lifedirector.PageValues.text
import com.lifemax.rpg.core.lifedirector.PageValues.truthy
import com.lifemax.rpg.core.save.JsonValue

/**
 * LDJ-1: why a task is not on the plan, as the page's `diag` reports it: the task, its title as
 * stored and tier, the reason code and the sentence the player reads, the quests it counts toward,
 * its goal, and for a task that fit nowhere the number of waking days that failed for each reason
 * ([perDay], by reason code, in code order; null when not given).
 */
class Rejection(
    val taskId: String,
    val title: JsonValue,
    val tier: Int,
    val reasonCode: String,
    val explanation: String,
    val linkedQuestIds: List<String>,
    val goalId: String?,
    val perDay: Map<Int, Int>?,
)

/** LDJ-1 `buildRejection` (`engine.js:1385-1428`). */
object Rejections {
    private val SPECIFIC =
        listOf(
            Refusal.CAP,
            Refusal.SPACING,
            Refusal.FATIGUE,
            Refusal.POST_SHIFT,
            Refusal.PER_DAY,
            Refusal.HIGH_COUNT,
            Refusal.BEFORE_SLEEP,
            Refusal.CONSECUTIVE,
            Refusal.DOWNSTREAM,
            Refusal.PRIOR_VOLUME,
        )
    private val NAMES =
        mapOf(
            Refusal.BUSY to "NO_FREE_WINDOW",
            Refusal.CAP to "DAILY_EFFORT_LIMIT",
            Refusal.PER_DAY to "ONE_PER_DAY_RULE",
            Refusal.SPACING to "SPACING_RULE",
            Refusal.BEFORE_SLEEP to "TOO_CLOSE_TO_SLEEP",
            Refusal.FATIGUE to "ESTIMATED_FATIGUE",
            Refusal.POST_SHIFT to "POST_SHIFT_PROTECTION",
            Refusal.CONSECUTIVE to "CONSECUTIVE_LOAD_CAP",
            Refusal.DOWNSTREAM to "DOWNSTREAM_FATIGUE",
            Refusal.PREREQUISITE to "PREREQUISITE_NOT_SCHEDULED",
            Refusal.HIGH_COUNT to "HIGH_INTENSITY_DAILY_CAP",
            Refusal.PRIOR_VOLUME to "NEEDS_EARLIER_SESSIONS",
            Refusal.RECOVERY to "RECOVERY_PROTECTED",
        )

    /**
     * LDJ-1, in this order: a task with a blocked reason (LDC-10) waits on a prerequisite quest; a
     * blocked window (LDK-1) gives its reason; a window shorter than the task's shortest length has
     * no time left; otherwise each waking day the window touches is tried with a tally (LDW-1) and
     * given one reason, the most counted of the specific ones (the first of them in the page's list
     * on a tie), or, when none was counted, protected recovery if a free or recovery run of the
     * shortest length exists in the day's part of the window, else no free window; the days are
     * counted per reason, and the reason of most days (the lower code on a tie) answers, the
     * sentence naming the top two when there are several.
     */
    fun of(s: PlanState, t: PlanTask): Rejection {
        val task = t.task
        val title = "\"" + text(task.title) + "\""
        if (task.blockedReason != null)
            return reject(t, task.blockedReason!!, "$title waits on a prerequisite quest.", null)
        val w = Feasibility.window(s, t)
        if (w.blocked != null) {
            val code =
                if (w.blocked == Refusal.PREREQUISITE) "PREREQUISITE_NOT_SCHEDULED"
                else "NEEDS_EARLIER_SESSIONS"
            return reject(t, code, "$title could not be placed because ${w.blocked.words}.", null)
        }
        if (w.latestSlot - w.earliestSlot < t.prepared.dMinSlots) {
            val where =
                if (truthy(task.deadlineEpoch)) " before the quest deadline" else " this week"
            return reject(
                t,
                "DEADLINE_TOO_CLOSE",
                "$title has no time left in its window$where.",
                null,
            )
        }
        val perReason = sortedMapOf<Int, Int>()
        for (c in s.cycles) {
            if (c.cycle.endSlot <= w.earliestSlot || c.cycle.startSlot >= w.latestSlot) continue
            val tally = IntArray(Placer.TALLY_SIZE)
            Placer.bestInDay(s, t, c, w, tally)
            var code: Refusal? = null
            var most = 0
            for (r in SPECIFIC) if (tally[r.code] > most) {
                most = tally[r.code]
                code = r
            }
            val reason = code ?: if (recoveryFits(s, t, c, w)) Refusal.RECOVERY else Refusal.BUSY
            perReason[reason.code] = (perReason[reason.code] ?: 0) + 1
        }
        val ranked =
            perReason.keys.sortedWith { a, b -> perReason.getValue(b) - perReason.getValue(a) }
        val top = ranked.firstOrNull()?.let(::byCode) ?: Refusal.BUSY
        val linked =
            if (task.linkedQuestRequirementIds.isEmpty()) ""
            else
                " (quest: " +
                    task.linkedQuestRequirementIds.joinToString(", ") { it.questTitle } +
                    ")"
        fun days(code: Int): String {
            val n = perReason.getValue(code)
            return "$n day" + (if (n == 1) "" else "s") + ": " + byCode(code).words
        }
        val why = if (ranked.size > 1) days(ranked[0]) + "; " + days(ranked[1]) else top.words
        val sentence = "$title$linked could not be scheduled — $why." + hint(s, t, top)
        return reject(t, NAMES.getValue(top), sentence, perReason)
    }

    private fun byCode(code: Int): Refusal = Refusal.entries.first { it.code == code }

    /**
     * Whether a run of free or recovery slots as long as the shortest length fits in the window.
     */
    private fun recoveryFits(s: PlanState, t: PlanTask, c: CycleState, w: TaskWindow): Boolean {
        var run = 0
        var i = maxOf(c.cycle.startSlot, w.earliestSlot)
        val until = minOf(c.cycle.endSlot, w.latestSlot)
        while (i < until) {
            val code = s.code(i.toInt())
            run = if (code == SlotCode.FREE.code || code == SlotCode.RECOVERY.code) run + 1 else 0
            if (run >= t.prepared.dMinSlots) return true
            i += 1
        }
        return false
    }

    private fun hint(s: PlanState, t: PlanTask, top: Refusal): String =
        when (top) {
            Refusal.CAP -> " Raise “Max daily effort” in Settings, or choose a lighter intensity."
            Refusal.SPACING ->
                " Sessions are kept at least " + text(t.task.spacingMinH) + "h apart."
            Refusal.FATIGUE ->
                " Energy estimates are heuristics; an energy check-in can override them."
            Refusal.POST_SHIFT -> " Heavy lifting is not scheduled after a long physical shift."
            Refusal.RECOVERY ->
                " Life Director keeps " +
                    text(s.prefs["minDailyRecoveryMinutes"]) +
                    " minutes of downtime per day and will not trade it for tasks."
            else -> ""
        }

    private fun reject(t: PlanTask, code: String, sentence: String, perDay: Map<Int, Int>?) =
        Rejection(
            t.id,
            t.task.title,
            t.task.tier,
            code,
            sentence,
            t.task.linkedQuestRequirementIds.map { it.questId },
            t.task.goalId,
            perDay,
        )
}
