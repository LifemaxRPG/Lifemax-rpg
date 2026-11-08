package com.lifemax.rpg.core.adapters

import com.lifemax.rpg.core.economy.Decay
import com.lifemax.rpg.core.economy.EconomyClock
import com.lifemax.rpg.core.economy.EconomyContext
import com.lifemax.rpg.core.economy.HealthConnect
import com.lifemax.rpg.core.economy.IdSource
import com.lifemax.rpg.core.economy.Storage
import com.lifemax.rpg.core.quests.HandContext
import java.util.Locale
import java.util.UUID

/** Random ids as the page makes them: `crypto.randomUUID()` and four base-36 characters. */
class RandomIdSource : IdSource {
    override fun uuid(): String = UUID.randomUUID().toString()

    override fun token(): String =
        (0 until TOKEN_LENGTH)
            .map { BASE36[(Math.random() * BASE36.length).toInt()] }
            .joinToString("")

    private companion object {
        const val TOKEN_LENGTH = 4
        const val BASE36 = "0123456789abcdefghijklmnopqrstuvwxyz"
    }
}

/**
 * Everything one action reads from the device, taken from a single reading of the clock: the
 * economy context, the Health Connect port and, once a save exists, the Daily Hand's outside
 * inputs.
 */
class ActionInputs(
    val economy: EconomyContext,
    val health: HealthConnect,
    /** Null while there is no decoded save. */
    val hand: HandContext?,
)

/**
 * Builds the pure code's inputs from the device, read-only: it reads the clock, the latest decoded
 * save, the plan and Health Connect, and writes nothing anywhere. One call to [begin] is one
 * reading of the clock (see [DeviceClock]).
 *
 * Sources of truth, one per fact:
 * - now, zone, today, week: [DeviceClock].
 * - water percent, cat counts, luck, rest days taken, records, coach memory: the decoded save
 *   ([SaveSource]).
 * - plan done and plan rest days: the plan's projection ([PlanFacts]), because the plan's content
 *   is page code.
 * - workouts, steps, sleep, weight: Health Connect ([HealthReads]).
 */
class ContextFactory(
    private val device: DeviceClock,
    private val save: SaveSource,
    private val plan: PlanFacts,
    private val health: HealthReads = NoHealthReads,
    private val ids: IdSource = RandomIdSource(),
    private val locale: Locale = Locale.getDefault(),
) {
    private val facts = HandFactsAdapter(save, plan)

    /** Takes a reading of the clock and builds one action's inputs from it. */
    fun begin(storage: Storage = Storage.Writable): ActionInputs {
        val clock = device.reading()
        val economy =
            EconomyContext(
                clock = clock,
                ids = ids,
                storage = storage,
                handFacts = facts.facts(clock),
                locale = locale,
                isPlanRestDay = plan::isRestDay,
            )
        return ActionInputs(economy, health.at(clock), hand(clock, economy))
    }

    /**
     * What the Daily Hand draw reads outside the save: today, the luck roll, `ecoRestDay` (a rest
     * day taken, in the plan, or a recovery session of 20+ minutes) and the day key of a record's
     * date.
     */
    private fun hand(clock: EconomyClock, context: EconomyContext): HandContext? {
        val view = save.view() ?: return null
        return HandContext(
            today = clock.today,
            luckScore = SaveFacts.luckScore(view.stored, clock.today),
            isRestDay = { day -> Decay.isRestDay(view.economy, day, context) },
            dayOf = { date -> PageDays.dayOf(date, clock) },
        )
    }
}
