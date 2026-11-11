package com.lifemax.rpg.core.lifedirector.hostadapters

import com.lifemax.rpg.core.economy.EconomyContext
import com.lifemax.rpg.core.economy.EconomySave
import com.lifemax.rpg.core.economy.LifeDirector
import com.lifemax.rpg.core.economy.Outcome
import com.lifemax.rpg.core.lifedirector.EngineHash
import com.lifemax.rpg.core.quests.HandTemplate
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.PageReads

/**
 * [HostPort] on the native economy and quest board: the page's `LifeMaxHost` for one save.
 * - [stored] is the save as stored (every member, for what the economy does not model: the quest
 *   list's own members, `plan`, `created`, `name`); the board is read from it once.
 * - [save] is the economy's view of the same save; awards and bounties replace it, as the page's
 *   `state` changes, and [toasts] collects what they showed.
 * - [context] gives each call its clock, device zone and storage (one reading per call: the page
 *   reads `Date.now()` inside every function).
 * - [library] is the Daily Hand's templates (`QUEST_LIBRARY`, from `QuestContent`); null where the
 *   page has none.
 * - [plans] and [meals] are the workout plan's and Smart Diet's days, page content not native yet.
 *
 * Construct with [of], which refuses a quest list the strict reader does not take (DV-A1).
 */
class EconomyHost
private constructor(
    private val stored: JsonObject,
    save: EconomySave,
    private val board: Board?,
    private val context: () -> EconomyContext,
    private val library: List<HandTemplate>?,
    private val plans: PlanDays,
    private val meals: MealDays,
) : HostPort {
    /** The economy as the calls so far left it. */
    var save: EconomySave = save
        private set

    private val shown = ArrayList<String>()

    /** Every message the calls so far showed, in order. */
    val toasts: List<String>
        get() = shown.toList()

    override val connected: Boolean =
        stored["activities"] is JsonArray && stored["quests"] is JsonArray

    private fun <T> apply(outcome: Outcome<T>): T {
        save = outcome.save
        shown += outcome.toasts
        return outcome.value
    }

    override fun playerId(): String {
        if (!connected) return "player"
        val created =
            stored["created"].let { if (PageReads.truthy(it)) PageReads.pageText(it) else "" }
        val name = stored["name"].let { if (PageReads.truthy(it)) PageReads.pageText(it) else "" }
        val id = EngineHash.stableId("p", "$created|$name")
        return "lm_" + id.substring(minOf(2, id.length), minOf(PLAYER_ID_END, id.length))
    }

    override fun typeForTag(tag: String): String = HostTexts.typeForTag(tag)

    override fun tagForType(type: String): String = HostTexts.tagForType(type)

    override fun xpFor(
        type: String,
        minutes: Double,
        quality: Double,
        dateKey: String?,
        commit: Boolean,
    ): LifeDirector.SessionXp =
        apply(LifeDirector.xpFor(save, type, minutes, quality, dateKey, commit, context()))

    override fun awardActivity(event: AwardEvent, infoTitle: String?): LifeDirector.AwardResult =
        apply(LifeDirector.awardActivity(save, event.session(), infoTitle, context(), connected))

    override fun completeQuest(ref: LifeDirector.QuestRef?): LifeDirector.BountyResult =
        apply(LifeDirector.completeQuest(save, ref, context(), connected))

    override fun readQuests(
        nowEpoch: Double,
        zone: String,
        overlay: QuestOverlay,
        revoked: Set<String>,
    ): HostAnswer<List<HostQuest>> {
        if (!connected || board == null) return HostAnswer.Answer(emptyList())
        return QuestReading.readQuests(
            board,
            save,
            context().clock,
            nowEpoch,
            zone,
            overlay,
            revoked,
        )
    }

    override fun readPlanSessions(
        nowEpoch: Double,
        zone: String,
        days: Double,
    ): HostAnswer<List<PlanSession>> {
        if (!connected || !PageReads.truthy(stored["plan"])) return HostAnswer.Answer(emptyList())
        return PlanReading.sessions(plans, nowEpoch, zone, days)
    }

    override fun isPlanDayDone(key: String): Boolean = plans.isDone(key)

    override fun milestones(): HostAnswer<List<Milestone>> =
        HostAnswer.Answer(
            if (!connected || board == null) emptyList()
            else QuestReading.milestones(board, save, context().clock)
        )

    override fun retiredUntimedQuest(ref: QuestLink?): HostAnswer<RetiredQuest?> =
        QuestReading.retired(board, library, ref)

    override fun activitiesSince(sinceIso: String?): List<HostActivity> {
        if (!connected) return emptyList()
        return save.activities
            .filter {
                it.ldEventId.isNullOrEmpty() &&
                    it.type != "quest" &&
                    (sinceIso.isNullOrEmpty() || (it.date ?: "undefined") > sinceIso)
            }
            .map {
                HostActivity(
                    id = it.id,
                    type = it.type,
                    name = it.name,
                    minutes = it.duration.let { d -> if (d.isNaN()) 0.0 else d + 0.0 },
                    iso = it.date,
                    tag =
                        if (it.type == "plan")
                            it.kind?.let { k -> HostTexts.KIND_TAG[k] } ?: "fitness"
                        else HostTexts.tagForType(it.type),
                    planDate = it.planDate?.takeIf { d -> d.isNotEmpty() },
                )
            }
    }

    override fun activityExists(id: String): Boolean =
        connected && save.activities.any { it.id == id }

    override fun activityDescription(session: DescribedSession?): HostAnswer<String> {
        if (!connected || session == null || board == null) return HostAnswer.Answer("")
        return HostAnswer.Answer(PlanReading.describe(plans, board, library, session))
    }

    override fun scheduledMeal(ref: MealRef?): ScheduledMeal? =
        HostMeals.meal(ref, context().clock.zone, meals)

    override fun mealOfBlock(block: MealBlock, zone: String): HostAnswer<ScheduledMeal?> =
        when (val ref = HostMeals.refOf(block, zone)) {
            is HostAnswer.Answer -> HostAnswer.Answer(scheduledMeal(ref.value))
            is HostAnswer.Refused -> ref
        }

    companion object {
        private const val PLAYER_ID_END = 14

        /**
         * The host for [stored] (the save as stored) and [save] (the economy's decoding of it), or
         * the refusal of its quest list.
         */
        fun of(
            stored: JsonObject,
            save: EconomySave,
            context: () -> EconomyContext,
            library: List<HandTemplate>?,
            plans: PlanDays = NoPlanDays,
            meals: MealDays = NoMealDays,
        ): HostAnswer<EconomyHost> =
            when (val read = BoardQuests.read(stored["quests"])) {
                is HostAnswer.Refused -> read
                is HostAnswer.Answer ->
                    HostAnswer.Answer(
                        EconomyHost(stored, save, read.value, context, library, plans, meals)
                    )
            }
    }
}
