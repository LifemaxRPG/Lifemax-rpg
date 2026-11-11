package com.lifemax.rpg.core.lifedirector.hostadapters

import com.lifemax.rpg.core.lifedirector.CalendarMath
import com.lifemax.rpg.core.lifedirector.WallClock
import com.lifemax.rpg.core.lifedirector.ZoneNames
import com.lifemax.rpg.core.quests.HandTemplate

/**
 * The workout plan as Life Director reads it (HA-17) and the instructions shown for a planned
 * session (HA-21). The plan's days come from [PlanDays]; the dates are the plan zone's.
 */
internal object PlanReading {
    private const val MIN_MINUTES = 30.0
    private const val MAX_MINUTES = 90.0
    private const val MINUTES_PER_ITEM = 8.0
    private val REST = Regex("rest")

    /** HA-17 `readPlanSessions(nowEpoch, tz, days)` once the host has a plan. */
    fun sessions(
        plans: PlanDays,
        nowEpoch: Double,
        zoneName: String,
        days: Double,
    ): HostAnswer<List<PlanSession>> {
        val zone =
            ZoneNames.resolve(zoneName)
                ?: return HostAnswer.Refused(HostRefusal.UNKNOWN_ZONE, "zone")
        val p =
            QuestReading.parts(nowEpoch, zone)
                ?: return HostAnswer.Refused(HostRefusal.INVALID_TIME, "now")
        val out = ArrayList<PlanSession>()
        var i = 0
        while (i < days) {
            val ymd = CalendarMath.plusDays(p.date, i)
            val next = ymd?.let { CalendarMath.plusDays(it, 1) }
            if (ymd == null || next == null)
                return HostAnswer.Refused(HostRefusal.INVALID_TIME, "day")
            val key = CalendarMath.key(ymd)
            i++
            val d = plans.day(key) ?: continue
            val items = d.items
            if (items.isNullOrEmpty() || isRest(d.kind)) continue
            val done = plans.isDone(key)
            val tag =
                d.kind?.let { HostTexts.KIND_TAG[it] }
                    ?: d.primary?.let { HostTexts.ATTR_TAG[it] }
                    ?: "fitness"
            val hint = d.minutesHint ?: (items.size * MINUTES_PER_ITEM)
            out +=
                PlanSession(
                    key = key,
                    title = "Plan: " + (d.label?.takeIf { it.isNotEmpty() } ?: "Session"),
                    tag = tag,
                    done = done,
                    sessionMinutes = maxOf(MIN_MINUTES, minOf(MAX_MINUTES, toInt32(hint))),
                    windowStartEpoch = WallClock.instantOf(ymd, 0, zone),
                    deadlineEpoch = WallClock.instantOf(next, 0, zone),
                )
        }
        return HostAnswer.Answer(out)
    }

    /** `/rest/i.test(String(d.kind || ''))`. */
    private fun isRest(kind: String?): Boolean =
        REST.containsMatchIn(HostTexts.asciiLower(kind.orEmpty()))

    /** ECMAScript ToInt32 (`x | 0`), as a double. */
    fun toInt32(value: Double): Double {
        if (value.isNaN() || value.isInfinite()) return 0.0
        val truncated = if (value < 0) Math.ceil(value) else Math.floor(value)
        val modulo = truncated.rem(TWO_32).let { if (it < 0) it + TWO_32 else it }
        return if (modulo >= TWO_31) modulo - TWO_32 else modulo
    }

    private const val TWO_32 = 4294967296.0
    private const val TWO_31 = 2147483648.0

    /** HA-21 `activityDescription(inst)` once connected. */
    fun describe(
        plans: PlanDays,
        board: Board,
        library: List<HandTemplate>?,
        session: DescribedSession,
    ): String {
        for (ref in session.links) {
            val id = ref.questId.removeSuffix("@next")
            if (id.startsWith("plan:")) {
                val day = plans.day(id.substring(PLAN_PREFIX))
                if (day != null) {
                    day.description
                        ?.let(HostTexts::trim)
                        ?.takeIf { it.isNotEmpty() }
                        ?.let {
                            return it
                        }
                    val names = day.items.orEmpty().filter { it.isNotEmpty() }
                    if (names.isNotEmpty()) return names.joinToString(" · ")
                }
            }
            val quest = board.quests.firstOrNull { it.idText == id }
            quest
                ?.desc
                ?.let(HostTexts::trim)
                ?.takeIf { it.isNotEmpty() }
                ?.let {
                    return it
                }
            if (id.startsWith("dh_") && library != null) {
                val title = ref.questTitle?.takeIf { it.isNotEmpty() } ?: session.title
                val matches =
                    library.filter { it.name == title && HostTexts.trim(it.desc).isNotEmpty() }
                if (matches.size == 1) return HostTexts.trim(matches[0].desc)
            }
        }
        return ""
    }

    private const val PLAN_PREFIX = 5
}
