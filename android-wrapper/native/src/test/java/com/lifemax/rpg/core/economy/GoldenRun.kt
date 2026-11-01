package com.lifemax.rpg.core.economy

import java.time.Instant
import java.time.ZoneId

/**
 * One golden case being replayed: the save, the clock, the id source and the toasts so far. [call]
 * maps a recorded page call onto this module's API and its answer back onto the page's JSON.
 */
internal class GoldenRun(
    private val fresh: Map<String, Any?>,
    start: Map<String, Any?>,
    var now: Long,
    val zone: ZoneId,
) {
    var raw: Map<String, Any?> = start
    var baseline: EconomySave = SaveCodec.decode(start)
    var save: EconomySave = baseline
    var readOnly = false
    val toasts = mutableListOf<String>()
    private var uuids = 0

    private companion object {
        val REDRAW_DEFAULTS = listOf("mealsMeta", "foodLog", "money")
    }

    private val ids =
        object : IdSource {
            override fun uuid() = "00000000-0000-4000-8000-%012d".format(++uuids)

            override fun token() = "000d"
        }

    fun encoded(): Map<String, Any?> = SaveCodec.encode(save, baseline, raw)

    /**
     * After a save the page redraws, and the food and money screens put back their empty defaults
     * when a loaded save lacks them (outside the economy).
     */
    fun redrawn() {
        val missing = REDRAW_DEFAULTS.filter { it !in raw }
        if (missing.isEmpty()) return
        val defaults = missing.associateWith { fresh[it] }
        raw = raw + defaults
        val restored = SaveCodec.decode(raw)
        baseline =
            baseline.copy(
                money = restored.money,
                calorieGoal = restored.calorieGoal,
                foodLog = restored.foodLog,
            )
        save =
            save.copy(
                money = save.money ?: restored.money,
                calorieGoal = restored.calorieGoal,
                foodLog = restored.foodLog,
            )
    }

    fun context(env: Map<String, Any?> = emptyMap()) =
        EconomyContext(
            clock = EconomyClock(now, zone),
            ids = ids,
            storage =
                when {
                    readOnly || env["failSave"] == "readonly" -> Storage.ReadOnly
                    env["failSave"] != null -> Storage.Full
                    else -> Storage.Writable
                },
            isPlanRestDay = ::isPlanRestDay,
        )

    /**
     * The default plan rests on Sundays from the day it starts (`planDay`, outside the economy).
     */
    private fun isPlanRestDay(day: String): Boolean {
        val start = raw["plan"].obj().str("start") ?: return false
        val date = runCatching { java.time.LocalDate.parse(day) }.getOrNull() ?: return false
        return day >= start && date.dayOfWeek == java.time.DayOfWeek.SUNDAY
    }

    /** Applies an action's outcome to the run and returns its value. */
    fun <T> apply(outcome: Outcome<T>): T {
        save = outcome.save
        toasts += outcome.toasts
        return outcome.value
    }

    fun call(call: Map<String, Any?>): Any? {
        (call["at"] as? String)?.let { now = Instant.parse(it).toEpochMilli() }
        val env = call["env"].obj()
        val args = call["args"].list()
        return GoldenCalls.dispatch(this, call["fn"] as String, args, env)
    }
}
