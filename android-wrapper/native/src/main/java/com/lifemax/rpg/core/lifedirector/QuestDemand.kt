package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.lifedirector.Candidates.Window
import com.lifemax.rpg.core.lifedirector.PageValues.member
import com.lifemax.rpg.core.lifedirector.PageValues.number
import com.lifemax.rpg.core.lifedirector.PageValues.or
import com.lifemax.rpg.core.lifedirector.PageValues.orZero
import com.lifemax.rpg.core.lifedirector.PageValues.round
import com.lifemax.rpg.core.lifedirector.PageValues.truthy
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import kotlin.math.ceil

/**
 * LDC-4 to LDC-8: the sessions one scheduling quest asks for, added to [tasks] (which already holds
 * the goal sessions and earlier quests' sessions, in the page's order). For each requirement and
 * each window: what is still needed, less the kept sessions already carrying it; then compatible
 * goal sessions take it on (LDC-6, or cover it each day, LDC-8), and sessions of the quest's own
 * fill the rest (LDC-7).
 */
internal class QuestDemand(
    private val tasks: MutableList<CandidateTask>,
    private val weeks: List<PlanWeek>,
    private val now: Double,
    private val horizon: List<PlanDay>,
    private val kept: List<JsonValue>,
    private val used: Set<String>?,
    private val weekOf: (JsonValue?) -> Int,
) {
    fun add(q: PlayerQuest) {
        val windows = windows(q)
        for (r in q.requirements) for (win in windows) add(q, r, win)
    }

    /** LDC-4: a daily quest's days (those of `onlyDays` when it has them), else one window. */
    private fun windows(q: PlayerQuest): List<Window> {
        if (q.daily)
            return horizon
                .filter { d ->
                    q.onlyDays == null ||
                        q.onlyDays.any {
                            PageValues.same(it, JsonNumber(d.date.weekday.toDouble()))
                        }
                }
                .map { d ->
                    Window(
                        maxOf(d.startEpoch, now),
                        d.endEpoch,
                        d.key,
                        now >= d.startEpoch && now < d.endEpoch,
                    )
                }
        val last = weeks.lastOrNull() ?: throw PageTypeError("Cannot read properties of undefined")
        val end =
            minOf(number(or(q.deadlineEpoch, JsonNumber(Double.POSITIVE_INFINITY))), last.endEpoch)
        return listOf(Window(maxOf(now, number(orZero(q.windowStartEpoch))), end, ONCE, false))
    }

    private fun add(q: PlayerQuest, r: QuestRequirement, win: Window) {
        if (win.end <= win.start) return
        val fresh = q.daily && !win.isToday
        var count =
            if (fresh) maxOf(0.0, if (truthy(r.targetCount)) r.targetCount else 1.0)
            else maxOf(0.0, r.targetCount - r.currentCount)
        var minutes = if (fresh) r.targetMinutes else maxOf(0.0, r.targetMinutes - r.currentMinutes)
        for (k in kept) {
            if (!PageValues.present(k)) throw PageTypeError("Cannot read properties of null")
            val links = member(k, "linkedQuestRequirementIds")
            val items =
                (links as? JsonArray)?.items
                    ?: if (truthy(links)) throw PageTypeError("some") else emptyList()
            if (!items.any { Candidates.storedLink(it, q.id, r.id) }) continue
            val start = number(member(k, "startEpoch"))
            if (!(start >= win.start && start < win.end)) continue
            if (count > 0) count--
            minutes =
                maxOf(
                    0.0,
                    minutes - round((number(member(k, "endEpoch")) - start) / Candidates.MINUTE),
                )
        }
        val profile = ActivityTags.profile(r.tag)
        val length =
            QuarterHours.roundUp(
                number(or(or(r.sessionMinutes, r.minSessionMinutes), JsonNumber(profile.minutes)))
            )
        if (!truthy(count) && truthy(minutes)) count = ceil(minutes / length)
        if (!truthy(count)) return
        val tier = Candidates.questTier(q, win, now, length)
        val link =
            QuestLink(q.id, r.id, q.title, if (q.daily) JsonNumber(win.end) else q.deadlineEpoch)
        val ask = Ask(q, r, win, link, tier, length, profile)
        val compatible = if (q.exclusive) emptyList() else tasks.filter { compatible(it, q, r) }
        val group = best(compatible, r, win)
        var attached = 0.0
        val idBase = "|" + q.id + "|" + r.id + "|" + win.key + "|"
        if (q.daily) {
            val cover = DailyCover(r.tag, win.start, win.end, link, q.exclusive)
            val best = group?.firstOrNull()
            if (
                best != null &&
                    PageValues.lessOrEqual(
                        orZero(best.spacingMinH),
                        JsonNumber(DAILY_SPACING_HOURS),
                    )
            ) {
                CandidateIds.fresh(used, "qx$idBase", count).forEachIndexed { k, id ->
                    tasks += merged(best, id, k, ask, true, cover)
                }
            } else {
                CandidateIds.fresh(used, "q$idBase", count).forEachIndexed { k, id ->
                    tasks += ownSession(ask, k, id).copy(hasCoverable = true, coverable = cover)
                }
            }
            attached = count
        } else if (group != null) {
            val best = group.first()
            val takers =
                group
                    .filter { inWindow(it, win) && !it.counts(q.id) }
                    .sortedWith { a, b ->
                        PageValues.byDifference(a.week - b.week).takeIf { it != 0 }
                            ?: PageValues.byDifference(a.sessionIndex - b.sessionIndex)
                    }
            for (t in takers) {
                if (attached >= count) break
                takeOn(t, ask)
                attached++
            }
            for ((k, id) in CandidateIds.fresh(used, "qx$idBase", count - attached).withIndex()) {
                tasks += merged(best, id, k, ask, true, null)
                attached++
            }
        }
        CandidateIds.fresh(used, "q$idBase", count - attached).forEachIndexed { k, id ->
            tasks += ownSession(ask, k, id)
        }
    }

    /** What one requirement asks for in one window. */
    private class Ask(
        val q: PlayerQuest,
        val r: QuestRequirement,
        val win: Window,
        val link: QuestLink,
        val tier: Int,
        val sessionMinutes: Double,
        val profile: TagProfile,
    ) {
        val weight: Double
            get() = DemandTables.packaged.questWeights.getValue(q.tier)

        val tierReason: String
            get() = if (tier == CRITICAL_TIER) "CRITICAL_DEADLINE" else "ACTIVE_QUEST"
    }

    /**
     * LDC-6: a goal session that may carry the requirement: a goal's own, not exclusive, not a
     * quest session and not covering a daily quest; of a matching tag, a check-in tag only when the
     * requirement names it exactly; long enough, and intense enough.
     */
    private fun compatible(t: CandidateTask, q: PlayerQuest, r: QuestRequirement): Boolean =
        !t.goalId.isNullOrEmpty() &&
            !t.exclusive &&
            t.kind != TaskKind.QUEST &&
            t.coverable == null &&
            ActivityTags.matches(r.tag, t.tag) &&
            (!Candidates.checkIn(t.tag) || PageValues.same(t.tag, r.tag)) &&
            PageValues.atLeast(JsonNumber(t.dMax), orZero(r.minSessionMinutes)) &&
            PageValues.atLeast(
                JsonNumber(maxOf(number(t.physical), number(t.cognitive))),
                q.minIntensity,
            )

    /**
     * LDC-6: the compatible sessions of the best template: the deepest tag match, then the most
     * sessions inside the window, then the template id.
     */
    private fun best(
        compatible: List<CandidateTask>,
        r: QuestRequirement,
        win: Window,
    ): List<CandidateTask>? {
        if (compatible.isEmpty()) return null
        val required = (r.tag as JsonString).value
        return compatible
            .groupBy { it.templateId }
            .entries
            .map { (id, list) ->
                Triple(
                    id,
                    list,
                    ActivityTags.depth(required, list.first().tag) to
                        list.count { inWindow(it, win) },
                )
            }
            .sortedWith { x, y ->
                PageValues.byDifference(y.third.first - x.third.first).takeIf { it != 0 }
                    ?: (y.third.second - x.third.second).takeIf { it != 0 }
                    ?: x.first.compareTo(y.first)
            }
            .first()
            .second
    }

    private fun inWindow(t: CandidateTask, win: Window): Boolean =
        t.windowStart < win.end && t.windowEnd > win.start

    /** LDC-6: a goal session takes the requirement on in place. */
    private fun takeOn(t: CandidateTask, ask: Ask) {
        t.linkedQuestRequirementIds += ask.link
        t.kind = TaskKind.MERGED
        t.windowStart = maxOf(t.windowStart, ask.win.start)
        t.windowEnd = minOf(t.windowEnd, ask.win.end)
        t.deadlineEpoch = or(ask.link.deadlineEpoch, t.deadlineEpoch)!!
        if (truthy(ask.r.minSessionMinutes)) {
            t.dMin = maxOf(t.dMin, QuarterHours.roundUp(number(ask.r.minSessionMinutes)))
            t.dOpt = maxOf(t.dOpt, t.dMin)
            t.dMax = maxOf(t.dMax, t.dOpt)
        }
        if (ask.tier > t.tier) {
            t.tier = ask.tier
            t.tierReason = ask.tierReason
        }
        t.questWeight = maxOf(t.questWeight, ask.weight)
    }

    /**
     * LDC-6, LDC-8: an extra session of [from]'s kind for the requirement (session index 100 + k).
     */
    private fun merged(
        from: CandidateTask,
        id: String,
        k: Int,
        ask: Ask,
        setCover: Boolean,
        cover: DailyCover?,
    ): CandidateTask {
        val least =
            QuarterHours.roundUp(
                number(or(ask.r.minSessionMinutes, JsonNumber(QuarterHours.MINIMUM_MINUTES)))
            )
        return from
            .copied()
            .copy(
                id = id,
                kind = TaskKind.MERGED,
                sessionIndex = (MERGED_INDEX + k).toDouble(),
                linkedQuestRequirementIds = mutableListOf(ask.link),
                tier = ask.tier,
                tierReason = ask.tierReason,
                questWeight = ask.weight,
                windowStart = ask.win.start,
                windowEnd = ask.win.end,
                deadlineEpoch = ask.link.deadlineEpoch,
                dependsOn = mutableListOf(),
                requiresPrior = null,
                hasCoverable = setCover,
                coverable = cover,
                dOpt = maxOf(from.dOpt, least),
                dMin = maxOf(from.dMin, least),
            )
    }

    /** LDC-7 `standaloneQuestTask`: a session of the quest's own, of its tag's default demand. */
    private fun ownSession(ask: Ask, k: Int, id: String): CandidateTask {
        val q = ask.q
        val r = ask.r
        val length = ask.sessionMinutes
        val dMin =
            QuarterHours.roundUp(
                number(
                    or(
                        r.minSessionMinutes,
                        JsonNumber(maxOf(QuarterHours.MINIMUM_MINUTES, length - SLOT_MINUTES)),
                    )
                )
            )
        val own = "quest:" + q.id + ":" + r.id
        return CandidateTask(
            id = id,
            kind = TaskKind.QUEST,
            title = JsonString(q.title),
            templateId = own,
            tag = r.tag,
            goalId = null,
            goalTitle = null,
            linkedGoalIds = mutableListOf(),
            linkedQuestRequirementIds = mutableListOf(ask.link),
            dependsOn = mutableListOf(),
            minLagMin = 0.0,
            exclusive = q.exclusive,
            requiresPrior = null,
            blockedReason = null,
            priority = DEFAULT_PRIORITY,
            questWeight = ask.weight,
            tier = ask.tier,
            tierReason = OWN_TIER_REASONS.getValue(ask.tier),
            dOpt = maxOf(length, dMin),
            dMin = dMin,
            dMax = maxOf(length, dMin) + SLOT_MINUTES,
            physical = JsonNumber(ask.profile.physical),
            cognitive = JsonNumber(ask.profile.cognitive),
            spacingKey = own,
            spacingMinH = JsonNumber(if (q.exclusive) 0.0 else OWN_SPACING_HOURS),
            spacingIdealH = JsonNumber(IDEAL_SPACING_HOURS),
            maxPerBwc = JsonNumber(if (q.exclusive) 1.0 else 2.0),
            avoidBeforeSleepMin =
                JsonNumber(if (ask.profile.physical >= HEAVY) BEFORE_SLEEP_MINUTES else 0.0),
            phases = Candidates.ANY_PHASE,
            preferRestDay = null,
            windowStart = ask.win.start,
            windowEnd = ask.win.end,
            deadlineEpoch = ask.link.deadlineEpoch,
            week = maxOf(0, weekOf(JsonNumber(ask.win.start))).toDouble(),
            sessionIndex = k.toDouble(),
            goalStatus = null,
            questId = q.id,
        )
    }

    private companion object {
        const val ONCE = "once"
        const val DAILY_SPACING_HOURS = 24.0
        const val CRITICAL_TIER = 4
        const val MERGED_INDEX = 100
        const val SLOT_MINUTES = 15.0
        const val DEFAULT_PRIORITY = 3.0
        const val OWN_SPACING_HOURS = 3.0
        const val IDEAL_SPACING_HOURS = 24.0
        const val HEAVY = 4.0
        const val BEFORE_SLEEP_MINUTES = 120.0
        val OWN_TIER_REASONS =
            mapOf(4 to "CRITICAL_DEADLINE", 3 to "ACTIVE_QUEST", 2 to "SIDE_QUEST")
    }
}
