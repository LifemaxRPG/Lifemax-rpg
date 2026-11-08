package com.lifemax.rpg.core.economy

import com.lifemax.rpg.core.adapters.ActionInputs
import com.lifemax.rpg.core.adapters.ContextFactory
import com.lifemax.rpg.core.adapters.DeviceClock
import com.lifemax.rpg.core.adapters.FakePlanFacts
import com.lifemax.rpg.core.adapters.FixedDeviceClock
import com.lifemax.rpg.core.adapters.HealthReads
import com.lifemax.rpg.core.adapters.NoHealthReads
import com.lifemax.rpg.core.adapters.SaveSource
import com.lifemax.rpg.core.adapters.SaveView
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * One golden case being replayed: the save, the clock, the id source and the toasts so far. [call]
 * maps a recorded page call onto this module's API and its answer back onto the page's JSON.
 */
internal class GoldenRun(
    private val fresh: Map<String, Any?>,
    start: Map<String, Any?>,
    now: Long,
    val zone: ZoneId,
) {
    /** The device clock the adapters read; the cases move it with `at`. */
    val device = FixedDeviceClock(now, zone)

    var now: Long
        get() = device.now
        set(value) {
            device.now = value
        }

    var raw: Map<String, Any?> = start
    var document = SaveCodec.document(start)
    var save: EconomySave = SaveCodec.decode(start)
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

    fun encoded(): Map<String, Any?> = SaveCodec.encode(save, document)

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
        document = SaveCodec.document(raw)
        save =
            save.copy(
                money = save.money ?: restored.money,
                calorieGoal = restored.calorieGoal,
                foodLog = restored.foodLog,
            )
    }

    /**
     * Every case runs through the adapter layer: the clock, the Daily Hand facts, the plan's rest
     * days and Health Connect reach the pure code only as the production adapters build them from
     * the recorded save, clock and zone.
     */
    /** Mutation checks wrap the adapters' sources here; a replay must then notice. */
    var wrapReads: (HealthReads) -> HealthReads = { it }
    var wrapDevice: (DeviceClock) -> DeviceClock = { it }

    fun inputs(env: Map<String, Any?> = emptyMap()): ActionInputs {
        val storage =
            when {
                readOnly || env["failSave"] == "readonly" -> Storage.ReadOnly
                env["failSave"] != null -> Storage.Full
                else -> Storage.Writable
            }
        val reads = env["health"]?.obj()?.let { GoldenPaths.GoldenHealthReads(it) } ?: NoHealthReads
        val clock = wrapDevice(device)
        val source = SaveSource { SaveView(save, document.stored) }
        val plan = FakePlanFacts(rest = ::isPlanRestDay)
        return ContextFactory(clock, source, plan, wrapReads(reads), ids, Locale.US).begin(storage)
    }

    fun context(env: Map<String, Any?> = emptyMap()) = inputs(env).economy

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
