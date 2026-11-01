package com.lifemax.rpg.core.economy

import kotlin.math.max
import kotlin.math.min

/** Section 11.5: what Life Director sessions and quest bounties pay. */
object LifeDirector {
    private val TAG_TYPES =
        listOf(
            "fitness.strength" to "strength",
            "fitness.cardio" to "running",
            "fitness" to "strength",
            "language" to "language",
            "reading" to "reading",
            "study" to "reading",
            "business" to "business",
            "admin.finance" to "business",
            "admin" to "task",
            "wellbeing" to "recovery",
            "nutrition" to "practical",
            "practical" to "practical",
            "creative" to "practical",
            "chores" to "task",
            "social" to "custom",
        )

    /** LD-1: the first prefix that matches a tag (exactly or as `prefix.`), else `custom`. */
    fun typeForTag(tag: String): String =
        TAG_TYPES.firstOrNull { (prefix, _) -> tag == prefix || tag.startsWith("$prefix.") }?.second
            ?: "custom"

    /** LD-2's answer: the starred XP, the rule it was scored by, and the split. */
    data class SessionXp(val xp: Double, val rule: Rule, val split: List<XpShare>)

    /**
     * LD-2: a session scored like a Log of [quality] (not a manual one, so no active-time limit) on
     * [day], split by the rule and starred. Without [commit] it is a preview.
     */
    fun xpFor(
        save: EconomySave,
        type: String,
        minutes: Double,
        quality: Double,
        day: String?,
        commit: Boolean,
        ctx: EconomyContext,
    ): Outcome<SessionXp> {
        val rule = save.rules.of(type)
        val duration = max(0.0, minutes.orZero)
        val q = if (duration < 20) min(1.0, quality.or(1.0)) else quality.or(1.0)
        val total = max(0.0, jsRound(rule.base * durationFactor(duration) * q))
        val onDay = day?.takeIf { it.isNotEmpty() } ?: ctx.today
        val awarded =
            if (total > 0) save.award(type, total, duration, ctx, onDay, peek = !commit)
            else save.result(Award(0.0, ""))
        val parts = awarded.save.starred(splitXp(awarded.value.xp, rule.primary, rule.secondary))
        return awarded.map { SessionXp(parts.sumOf { it.xp }, rule, parts) }
    }

    /** A finished Life Director session (the engine's event). */
    data class Session(
        val eventId: String,
        val tag: String,
        val startEpochSeconds: Double,
        val minutes: Double,
        val title: String,
        val partial: Boolean = false,
    )

    /** LD-3's answer. [xp] is the session's XP without the execution bonus [D20]. */
    data class AwardResult(
        val awarded: Boolean,
        val reason: String? = null,
        val activityId: String? = null,
        val xp: Double? = null,
    )

    /**
     * LD-3: records the session once per event (even when it pays nothing [D18]) and adds the
     * execution bonus, 10% (20% with Executive Edge) of the starred XP, 2 to 5 per session and 15 a
     * day, outside the cap [D07]. A failed save is taken back whole. [connected] is whether the
     * page host is ready.
     */
    fun awardActivity(
        save: EconomySave,
        session: Session,
        title: String?,
        ctx: EconomyContext,
        connected: Boolean = true,
    ): Outcome<AwardResult> {
        if (!connected) return save.result(AwardResult(false, "HOST_NOT_CONNECTED"))
        save.activities
            .firstOrNull { it.ldEventId == session.eventId }
            ?.let {
                return save.result(AwardResult(false, "ALREADY_IN_LIFEMAX", it.id, it.xp))
            }
        val type = typeForTag(session.tag)
        val start = (session.startEpochSeconds * 1000).toLong()
        val day = ctx.clock.localDate(start)
        val scored = xpFor(save, type, session.minutes, 1.0, day, commit = true, ctx)
        val (xp, rule, split) = scored.value
        val bonus =
            if (xp > 0) {
                val used =
                    save.activities
                        .filter { ctx.clock.dayOf(it.date) == day }
                        .sumOf { it.ldBonus.orNaN.orZero }
                val share = jsRound(xp * if (save.hasPerk("exec")) 0.2 else 0.1)
                min(max(0.0, 15 - used), max(2.0, min(5.0, share)))
            } else 0.0
        val record =
            Activity(
                id = "ld_${session.eventId}",
                ldEventId = session.eventId,
                type = type,
                name = title?.takeIf { it.isNotEmpty() } ?: session.title,
                icon = rule.icon,
                duration = session.minutes,
                quality = 1.0,
                skill = rule.skill,
                primary = rule.primary,
                secondary = rule.secondary,
                xp = xp + bonus,
                date = EconomyClock.iso(start),
                notes = "Logged from Life Director" + if (session.partial) " (partial)" else "",
                split = if (bonus > 0) split + XpShare("discipline", bonus) else split,
                ldBonus = bonus.takeIf { it > 0 },
            )
        val saved =
            SavePipeline.save(scored.save.copy(activities = scored.save.activities + record), ctx)
        if (!saved.value) {
            val reason =
                if (ctx.storage == Storage.ReadOnly) "HOST_READ_ONLY" else "HOST_SAVE_FAILED"
            return saved.then { SavePipeline.takeBack(save, AwardResult(false, reason), ctx) }
        }
        return saved.map { AwardResult(true, activityId = record.id, xp = xp) }
    }

    /** The engine's reference to the quest a block can complete (LD-4). */
    data class QuestRef(val questId: String?, val key: String?, val future: Boolean = false)

    /** LD-4's answer: the quest's listed XP, not what was paid [D20]. */
    data class BountyResult(
        val completed: Boolean,
        val reason: String? = null,
        val xp: Double? = null,
        val name: String? = null,
    )

    /** LD-4: pays a quest bounty through the quest board, if the period still matches. */
    fun completeQuest(
        save: EconomySave,
        ref: QuestRef?,
        ctx: EconomyContext,
        connected: Boolean = true,
    ): Outcome<BountyResult> {
        if (!connected || ref == null || ref.questId.isNullOrEmpty() || ref.future)
            return save.result(BountyResult(false))
        val quest =
            save.quests.firstOrNull { it.id == ref.questId }
                ?: return save.result(BountyResult(false, "QUEST_GONE"))
        if (Quests.key(quest, ctx.clock) != ref.key)
            return save.result(BountyResult(false, "PERIOD_CHANGED"))
        if (Quests.isDone(save, quest, ctx.clock))
            return save.result(BountyResult(false, "ALREADY_DONE"))
        val toggled = Quests.toggle(save, quest.id, true, ctx)
        return toggled.map {
            BountyResult(
                Quests.isDone(toggled.save, quest, ctx.clock),
                xp = quest.xp.orZero,
                name = quest.name,
            )
        }
    }
}
