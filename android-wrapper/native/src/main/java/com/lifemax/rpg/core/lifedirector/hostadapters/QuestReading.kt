package com.lifemax.rpg.core.lifedirector.hostadapters

import com.lifemax.rpg.core.economy.EconomyClock
import com.lifemax.rpg.core.economy.EconomySave
import com.lifemax.rpg.core.economy.Quest
import com.lifemax.rpg.core.economy.Quests
import com.lifemax.rpg.core.lifedirector.CalendarMath
import com.lifemax.rpg.core.lifedirector.EcmaTime
import com.lifemax.rpg.core.lifedirector.LocalParts
import com.lifemax.rpg.core.lifedirector.PageZone
import com.lifemax.rpg.core.lifedirector.WallClock
import com.lifemax.rpg.core.lifedirector.ZoneNames
import com.lifemax.rpg.core.quests.HandTemplate

/**
 * The board as Life Director reads it (HA-9, HA-16, HA-18, HA-19): quest records to plan, the
 * milestones, and retired Daily Hand cards. The period keys and done marks are the device's
 * (`questKey`, the economy's QUEST-1); the week's bounds are the plan zone's (HA-11).
 */
internal object QuestReading {
    private const val HOUR = 3600.0
    private const val MINUTE = 60.0
    private const val DAYS_IN_WEEK = 7

    /** HA-9 `weekBounds(nowEpoch, tz)`: Monday 00:00 of this week, next week, the week after. */
    fun weekBounds(nowEpoch: Double, zoneName: String): HostAnswer<WeekBounds> {
        val zone =
            ZoneNames.resolve(zoneName)
                ?: return HostAnswer.Refused(HostRefusal.UNKNOWN_ZONE, "zone")
        val p = parts(nowEpoch, zone) ?: return HostAnswer.Refused(HostRefusal.INVALID_TIME, "now")
        val mon = CalendarMath.plusDays(p.date, -((p.weekday + 6) % DAYS_IN_WEEK))
        val next = mon?.let { CalendarMath.plusDays(it, DAYS_IN_WEEK) }
        val after = mon?.let { CalendarMath.plusDays(it, 2 * DAYS_IN_WEEK) }
        if (mon == null || next == null || after == null)
            return HostAnswer.Refused(HostRefusal.INVALID_TIME, "week")
        return HostAnswer.Answer(
            WeekBounds(
                WallClock.instantOf(mon, 0, zone),
                WallClock.instantOf(next, 0, zone),
                WallClock.instantOf(after, 0, zone),
                CalendarMath.key(mon),
            )
        )
    }

    /** `localParts`, or null where `new Date(epoch * 1000)` is an Invalid Date. */
    fun parts(epoch: Double, zone: PageZone): LocalParts? =
        if (EcmaTime.timeClip(epoch * 1000.0).isNaN()) null else WallClock.localParts(epoch, zone)

    /** QUEST-1 `questKey(q)` on the device's clock. */
    fun key(q: BoardQuest, clock: EconomyClock): String = Quests.key(economyQuest(q), clock)

    /** QUEST-1 `isQuestDone(q)`. */
    fun isDone(q: BoardQuest, save: EconomySave, clock: EconomyClock): Boolean =
        !save.questDone[key(q, clock)].isNullOrEmpty()

    /** CURVE-9 `questLocked(q)`. */
    fun isLocked(q: BoardQuest, save: EconomySave): Boolean = save.isLocked(economyQuest(q))

    private fun economyQuest(q: BoardQuest) =
        Quest(
            id = q.idText,
            name = q.name.orEmpty(),
            cadence = q.cadence,
            xp = q.xp,
            stage = q.stage,
        )

    /** HA-16 `readQuests`. */
    fun readQuests(
        board: Board,
        save: EconomySave,
        clock: EconomyClock,
        nowEpoch: Double,
        zoneName: String,
        overlay: QuestOverlay,
        revoked: Set<String>,
    ): HostAnswer<List<HostQuest>> {
        val wb =
            when (val w = weekBounds(nowEpoch, zoneName)) {
                is HostAnswer.Answer -> w.value
                is HostAnswer.Refused -> return w
            }
        val zone = ZoneNames.resolve(zoneName)!!
        val nextMonday =
            parts(wb.end + HOUR, zone)?.let { CalendarMath.key(it.date) }
                ?: return HostAnswer.Refused(HostRefusal.INVALID_TIME, "next week")
        val out = ArrayList<HostQuest>()
        for (q in board.quests) {
            val id = q.id
            if (id.isNullOrEmpty() || HostTexts.isMilestone(q)) continue
            val locked = isLocked(q, save)
            val done = isDone(q, save, clock)
            val key = key(q, clock)
            val passive =
                (q.kind != null && q.kind in HostTexts.PASSIVE_KINDS) || HostTexts.isUntimedQuest(q)
            val mins = HostTexts.parseMinutes(q.words)
            val target = if (q.cadence == "daily") 1.0 else HostTexts.parseCount(q.words)
            val count = overlay.eventIds[key].orEmpty().count { it !in revoked }.toDouble()
            val base =
                HostQuest(
                    id = id,
                    status = "ACTIVE",
                    requiredCount = target,
                    title = q.name,
                    tier = if (q.kind == "milestone") "OPTIONAL" else "CORE",
                    tag = HostTexts.mapQuestTag(q),
                    schedulingMode = if (passive) "NONE" else "SESSIONS",
                    sessionMinutes = mins,
                    bountyXp = q.xp,
                )
            when {
                locked -> out += base.copy(status = "LOCKED")
                q.cadence == "daily" -> {
                    val only =
                        q.day
                            ?.takeIf { it.isNotEmpty() && it != "any" }
                            ?.let { day -> listOf(HostTexts.DAYS.indexOf(day)).filter { it >= 0 } }
                    out +=
                        base.copy(
                            daily = true,
                            onlyDays = only,
                            requiredCount = 1.0,
                            progressCount = if (done) 1.0 else minOf(1.0, count),
                            hostRef = QuestHostRef(id, key, "daily", done),
                        )
                }
                q.cadence == "weekly" -> {
                    out +=
                        base.copy(
                            status = if (done) "COMPLETED" else "ACTIVE",
                            progressCount = if (done) target else minOf(target, count),
                            deadlineEpoch = wb.end - MINUTE,
                            hostRef = QuestHostRef(id, key, "weekly", done),
                        )
                    out +=
                        base.copy(
                            id = "$id@next",
                            progressCount = 0.0,
                            windowStartEpoch = wb.end,
                            deadlineEpoch = wb.nextEnd - MINUTE,
                            hostRef =
                                QuestHostRef(
                                    id,
                                    id + "_" + nextMonday,
                                    "weekly",
                                    null,
                                    future = true,
                                ),
                        )
                }
                else -> {
                    val once = if (HostTexts.isProjectMilestone(q)) 1.0 else target
                    out +=
                        base.copy(
                            status = if (done) "COMPLETED" else "ACTIVE",
                            requiredCount = once,
                            progressCount = if (done) once else minOf(once, count),
                            hostRef =
                                QuestHostRef(
                                    id,
                                    key,
                                    q.cadence?.takeIf { it.isNotEmpty() } ?: "once",
                                    done,
                                ),
                            tier = "OPTIONAL",
                        )
                }
            }
        }
        return HostAnswer.Answer(out)
    }

    /** HA-18 `milestones()`: every milestone earned through other activity, with its state. */
    fun milestones(board: Board, save: EconomySave, clock: EconomyClock): List<Milestone> =
        board.quests.filter(HostTexts::isMilestone).map { q ->
            Milestone(
                id = q.id,
                title = q.name,
                desc = q.desc.orEmpty(),
                done = isDone(q, save, clock),
                locked = isLocked(q, save),
            )
        }

    /** HA-19 `retiredUntimedQuest(ref)`. */
    fun retired(
        board: Board?,
        library: List<HandTemplate>?,
        ref: QuestLink?,
    ): HostAnswer<RetiredQuest?> {
        val id = ref?.questId.orEmpty()
        if (!id.startsWith("dh_") || library == null) return HostAnswer.Answer(null)
        if (board == null) return HostAnswer.Refused(HostRefusal.NO_BOARD, "quests")
        if (board.quests.any { it.idText == id }) return HostAnswer.Answer(null)
        val matches = library.filter { it.name == ref?.questTitle }
        val t = matches.singleOrNull() ?: return HostAnswer.Answer(null)
        if (!HostTexts.isUntimedQuest(BoardQuest.ofTemplate(t.id, t.name, t.desc, t.verify)))
            return HostAnswer.Answer(null)
        return HostAnswer.Answer(RetiredQuest(id, t.name))
    }
}
