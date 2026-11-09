package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.save.JsonValue

/**
 * LDC-6: a quest requirement a task counts toward. [deadlineEpoch] is the quest's deadline as
 * stored, or the end of the day for a daily quest.
 */
data class QuestLink(
    val questId: String,
    val reqId: String,
    val questTitle: String,
    val deadlineEpoch: JsonValue,
)

/**
 * LDC-8: a daily quest's day that any compatible goal session placed inside [start]..[end] covers
 * ([exactOnly]: only a session of exactly [reqTag], for an exclusive quest).
 */
data class DailyCover(
    val reqTag: JsonValue,
    val start: Double,
    val end: Double,
    val link: QuestLink,
    val exactOnly: Boolean,
)

/** LDC-3: a template's prerequisite volume: [count] earlier sessions of [templateIds]. */
data class PriorVolume(val templateIds: List<String>, val count: JsonValue?)

/** The three kinds of candidate task (LDC-3, LDC-6, LDC-7). */
enum class TaskKind {
    GOAL,
    MERGED,
    QUEST,
}

/**
 * LDC-3 to LDC-10: one session the planner will try to place. Members the page carries as stored
 * are [JsonValue]s (the title, tag, intensities, spacing, phases, the quest deadline); a member the
 * page's task object does not have is null here and marked so: [goalTitle], [preferRestDay] and
 * [goalStatus] exist on goal sessions and the merged sessions copied from them, [questId] on quest
 * sessions only, and [coverable] is set ([hasCoverable]) on the merged and quest sessions of LDC-6
 * to LDC-8 (null, or a [DailyCover]). The lists are this task's own.
 */
data class CandidateTask(
    val id: String,
    var kind: TaskKind,
    val title: JsonValue,
    val templateId: String,
    val tag: JsonValue,
    val goalId: String?,
    val goalTitle: String?,
    val linkedGoalIds: MutableList<String>,
    val linkedQuestRequirementIds: MutableList<QuestLink>,
    val dependsOn: MutableList<String>,
    var minLagMin: Double,
    val exclusive: Boolean,
    val requiresPrior: PriorVolume?,
    var blockedReason: String?,
    val priority: Double,
    var questWeight: Double,
    var tier: Int,
    var tierReason: String,
    var dOpt: Double,
    var dMin: Double,
    var dMax: Double,
    val physical: JsonValue,
    val cognitive: JsonValue,
    val spacingKey: String,
    val spacingMinH: JsonValue?,
    val spacingIdealH: JsonValue?,
    val maxPerBwc: JsonValue,
    val avoidBeforeSleepMin: JsonValue,
    val phases: JsonValue,
    val preferRestDay: Boolean?,
    var windowStart: Double,
    var windowEnd: Double,
    var deadlineEpoch: JsonValue,
    val week: Double,
    val sessionIndex: Double,
    val goalStatus: String?,
    val questId: String?,
    val hasCoverable: Boolean = false,
    val coverable: DailyCover? = null,
) {
    /**
     * Where a re-plan found the session before, as the candidate leaves it: none (placement keeps
     * its own on [PlanTask.origStartEpoch], LDR-3).
     */
    val origStartEpoch: Double?
        get() = null

    /**
     * Whether it is placed, as the candidate leaves it: never (placement's is [PlanTask.isPlaced]).
     */
    val isPlaced: Boolean
        get() = false

    /** A copy whose lists are its own (the page's `makeTask` copies them). */
    fun copied(): CandidateTask =
        copy(
            linkedGoalIds = linkedGoalIds.toMutableList(),
            linkedQuestRequirementIds = linkedQuestRequirementIds.toMutableList(),
            dependsOn = dependsOn.toMutableList(),
        )

    /** Whether it carries a requirement of quest [id]. */
    fun counts(id: String): Boolean = linkedQuestRequirementIds.any { it.questId == id }
}

/** LDC-4: why a quest makes no sessions (the page's `diag` with no task). */
data class QuestRejection(val questId: String, val reasonCode: String, val explanation: String)

/** LDC-4: a quest with scheduling mode `NONE`: a reminder, not a session. */
data class PassiveReminder(val questId: String, val title: String, val anchor: String)

/** What [Candidates.compile] gives: the tasks in id order and what it found about each quest. */
class CandidateSet(
    val tasks: List<CandidateTask>,
    val passiveReminders: List<PassiveReminder>,
    val rejected: List<QuestRejection>,
    val questStatus: Map<String, JsonValue>,
    val quests: List<PlayerQuest>,
)

/** LDC-1: a week of the plan: up to seven of its days, from the first. */
data class PlanWeek(
    val index: Int,
    val key: String,
    val startEpoch: Double,
    val endEpoch: Double,
    val days: Int,
)

/** Task identities (LDC-9) and the plan's weeks (LDC-1). */
object CandidateIds {
    private const val SKIP_LIMIT = 200
    private const val DAYS_IN_WEEK = 7

    /**
     * LDC-9 `freshIds`: up to [count] ids `stableId("t", base + k)` for k = 0, 1, 2 ..., leaving
     * out every id whose instance id (`i_` and the same digits) is in [used], and trying no k at or
     * past `count + 200`. A fractional count gives its ceiling; NaN or 0 or less none.
     */
    fun fresh(used: Set<String>?, base: String, count: Double): List<String> {
        val out = ArrayList<String>()
        var k = 0
        while (out.size.toDouble() < count && k.toDouble() < count + SKIP_LIMIT) {
            val id = EngineHash.stableId("t", base + k)
            if (used == null || ("i" + id.substring(1)) !in used) out += id
            k++
        }
        return out
    }

    /** LDC-1 `weeksOf`: [days] cut into weeks of seven from the first day. */
    fun weeks(days: List<PlanDay>): List<PlanWeek> =
        days.chunked(DAYS_IN_WEEK).mapIndexed { i, chunk ->
            PlanWeek(
                i,
                chunk.first().key,
                chunk.first().startEpoch,
                chunk.last().endEpoch,
                chunk.size,
            )
        }
}
