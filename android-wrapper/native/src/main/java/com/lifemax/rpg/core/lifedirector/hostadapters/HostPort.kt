package com.lifemax.rpg.core.lifedirector.hostadapters

import com.lifemax.rpg.core.economy.LifeDirector

/**
 * What Life Director asks of the rest of LifeMax: the page's `LifeMaxHost` (engine.js:2355-2620)
 * without its screen calls (icon, toast, navigation). Every answer is the page's, for the same
 * save, clock, device zone and arguments (LIFE_DIRECTOR_ADAPTERS_SPEC, rules HA-1 to HA-25). No
 * method throws: where the page would throw, the answer is a [HostAnswer.Refused] naming why.
 *
 * Three methods change the economy (the save the port holds): [xpFor] with `commit`,
 * [awardActivity] and [completeQuest]. The others only read. Nothing here writes storage: the
 * caller decides what to do with the save the port leaves (while the page is the only writer,
 * nothing does).
 */
interface HostPort {
    /** HA-2 `connected()`: the save has an activity list and a quest list. */
    val connected: Boolean

    /** HA-2 `playerId()`: `lm_` and twelve characters of a hash of the save's birth and name. */
    fun playerId(): String

    /** HA-3 `typeForTag`. */
    fun typeForTag(tag: String): String

    /** HA-3 `tagForType`. */
    fun tagForType(type: String): String

    /**
     * HA-10 `xpFor(type, minutes, quality, dateKey, commit)`: what a session of [minutes] would pay
     * on [dateKey] (the device's today when null or empty, HA-11). With [commit] the rested XP it
     * uses is spent from the held save.
     */
    fun xpFor(
        type: String,
        minutes: Double,
        quality: Double,
        dateKey: String?,
        commit: Boolean,
    ): LifeDirector.SessionXp

    /** HA-12 to HA-14 `awardActivity(event, info)`: one LifeMax activity per completion event. */
    fun awardActivity(event: AwardEvent, infoTitle: String?): LifeDirector.AwardResult

    /** HA-15 `completeQuest(hostRef)`: a quest bounty through the quest board's own toggle. */
    fun completeQuest(ref: LifeDirector.QuestRef?): LifeDirector.BountyResult

    /** HA-16 `readQuests(nowEpoch, tz, overlay, revoked)`. */
    fun readQuests(
        nowEpoch: Double,
        zone: String,
        overlay: QuestOverlay,
        revoked: Set<String>,
    ): HostAnswer<List<HostQuest>>

    /** HA-17 `readPlanSessions(nowEpoch, tz, days)`. */
    fun readPlanSessions(
        nowEpoch: Double,
        zone: String,
        days: Double,
    ): HostAnswer<List<PlanSession>>

    /** HA-17 `isPlanDayDone(key)`. */
    fun isPlanDayDone(key: String): Boolean

    /** HA-18 `milestones()`. */
    fun milestones(): HostAnswer<List<Milestone>>

    /** HA-19 `retiredUntimedQuest(ref)`: a Daily Hand card gone from the board, by its template. */
    fun retiredUntimedQuest(ref: QuestLink?): HostAnswer<RetiredQuest?>

    /** HA-20 `activitiesSince(sinceIso)`: activities logged elsewhere in LifeMax. */
    fun activitiesSince(sinceIso: String?): List<HostActivity>

    /** HA-20 `activityExists(id)`. */
    fun activityExists(id: String): Boolean

    /** HA-21 `activityDescription(inst)`: the instructions of the source of a planned session. */
    fun activityDescription(session: DescribedSession?): HostAnswer<String>

    /** HA-22 `LifeMaxMeals.resolveScheduled(ref)`: the Smart Diet meal a meal block stands for. */
    fun scheduledMeal(ref: MealRef?): ScheduledMeal?

    /**
     * HA-23 the reference logic.js:1076-1078 hands Smart Diet for a meal block, then
     * [scheduledMeal] of it ([zone] is the plan's zone).
     */
    fun mealOfBlock(block: MealBlock, zone: String): HostAnswer<ScheduledMeal?>
}

/** A completion event as the ledger hands it to the award (engine.js:1933-1976). */
data class AwardEvent(
    val eventId: String,
    val activityTypeTag: String,
    val actualStartEpoch: Double,
    val actualDurationMinutes: Double,
    val title: String,
    val lifecycleState: String,
) {
    /** The economy's session (LD-3); `partial` is `PARTIALLY_COMPLETED`. */
    fun session(): LifeDirector.Session =
        LifeDirector.Session(
            eventId = eventId,
            tag = activityTypeTag,
            startEpochSeconds = actualStartEpoch,
            minutes = actualDurationMinutes,
            title = title,
            partial = lifecycleState == "PARTIALLY_COMPLETED",
        )
}

/**
 * Life Director's session counts per quest period (`ST.questOverlay`): the period key to the
 * completion event ids counted toward it.
 */
data class QuestOverlay(val eventIds: Map<String, List<String>> = emptyMap())

/**
 * A planned session's link to a quest (`linkedQuestRequirementIds[i]`): [questId] as the page reads
 * it, `String(ref.questId || '')`, and the quest's title when the link carries one.
 */
data class QuestLink(val questId: String, val questTitle: String? = null)

/** A planned session as [HostPort.activityDescription] reads it: its title and quest links. */
data class DescribedSession(val title: String?, val links: List<QuestLink>)

/**
 * What Smart Diet is asked for (`sdDirectorMeal`'s `ref`): a calendar [day] (used when non-empty),
 * else the device's date at [startEpoch] (seconds; NaN when absent); the meal slot from [slot],
 * else from [title].
 */
data class MealRef(
    val title: String? = null,
    val slot: String? = null,
    val day: String? = null,
    val startEpoch: Double = Double.NaN,
)

/** A planned meal block: its title, first minute and the meal's own start when it differs. */
data class MealBlock(
    val title: String?,
    val startEpoch: Double,
    val mealStartEpoch: Double? = null,
)

/**
 * The workout plan's days (`planDay`, `dayDone`): page content that is not native yet, given to the
 * adapter. Where the page's function throws, [day] answers null and [isDone] false, which is what
 * every caller in the host makes of a throw.
 */
interface PlanDays {
    fun day(key: String): PlanDay?

    fun isDone(key: String): Boolean
}

/**
 * One plan day as the host reads it: [kind] and [primary] as text, [label], [minutesHint] the first
 * truthy of the day's `min` and `minutes` as a number (null when neither is), [items] the name of
 * each item (`item.name || item.title || ''`, the item itself when it is text; null when the day's
 * `items` is not a list), [description] when it is text.
 */
data class PlanDay(
    val kind: String?,
    val label: String?,
    val primary: String?,
    val minutesHint: Double?,
    val items: List<String>?,
    val description: String?,
)

/** Smart Diet's days (`sdDay`), page content that is not native yet. */
fun interface MealDays {
    fun day(key: String): MealDay?
}

/** A Smart Diet day as `LifeMaxMeals` reads it: its meals in order. */
data class MealDay(val items: List<MealItem>)

/** One planned meal: its slot, recipe name, calories and whether it was logged or skipped. */
data class MealItem(
    val slot: String?,
    val name: String?,
    val kcal: Double,
    val logged: Boolean,
    val skipped: Boolean,
)

/** No plan: every day is empty. */
object NoPlanDays : PlanDays {
    override fun day(key: String): PlanDay? = null

    override fun isDone(key: String): Boolean = false
}

/** No Smart Diet. */
val NoMealDays = MealDays { null }
