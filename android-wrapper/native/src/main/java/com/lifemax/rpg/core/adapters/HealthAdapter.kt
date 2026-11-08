package com.lifemax.rpg.core.adapters

import com.lifemax.rpg.core.economy.EconomyClock
import com.lifemax.rpg.core.economy.HealthConnect

/**
 * Health Connect reads in the plugin's own terms (`HealthSyncPlugin`: `readExercise`, `readSteps`,
 * `readSleep`, `readWeight` with an explicit time range), blocking and read-only: there is no
 * request or write here. The production implementation is `HealthConnectReads` (`bridge/health`);
 * tests use a fake. A failed read throws, with the plugin's message (`readExercise failed: ...`),
 * which the sync shows after "Sync failed: ".
 */
interface HealthReads {
    /** `status()`: the SDK state (`available`, `update`, `unavailable`) and the granted types. */
    fun status(): HealthConnect.Status

    /** Exercise sessions between two instants (epoch milliseconds). */
    fun exercise(from: Long, to: Long): List<HealthConnect.Workout>

    /** One total per local calendar day from the day of [from] to [to]. */
    fun steps(from: Long, to: Long): List<HealthConnect.StepDay>

    /** Sleep sessions between two instants. */
    fun sleep(from: Long, to: Long): List<HealthConnect.SleepSession>

    /** The latest weight reading between two instants, or null. */
    fun weight(from: Long, to: Long): HealthConnect.Weight?
}

/**
 * The economy's [HealthConnect] port over [HealthReads]: it turns the sync's "last N days" into the
 * explicit ranges the plugin takes, counting back from the action's own reading of the clock
 * ([now], so the whole sync uses one instant) exactly as the page does (`now - N * 86400e3`; steps
 * 30 days, weight 60, sleep 8, exercise 7, the last two given by the sync itself).
 */
class HealthAdapter(private val reads: HealthReads, private val now: Long) : HealthConnect {
    override fun status() = reads.status()

    override fun exercise(from: Long, to: Long) = reads.exercise(from, to)

    override fun steps(days: Int): List<HealthConnect.StepDay> =
        reads.steps(now - days * DAY_MILLIS, now)

    override fun sleep(from: Long, to: Long) = reads.sleep(from, to)

    override fun weight(days: Int): HealthConnect.Weight? =
        reads.weight(now - days * DAY_MILLIS, now)

    private companion object {
        const val DAY_MILLIS = 86_400_000L
    }
}

/** The port for one action: [now] is the action's [EconomyClock] instant. */
fun HealthReads.at(clock: EconomyClock): HealthConnect = HealthAdapter(this, clock.now)

/** A phone without Health Connect (or outside Android): the sync treats it as unavailable. */
object NoHealthReads : HealthReads {
    override fun status() = HealthConnect.Status("unavailable", emptyList())

    override fun exercise(from: Long, to: Long) = emptyList<HealthConnect.Workout>()

    override fun steps(from: Long, to: Long) = emptyList<HealthConnect.StepDay>()

    override fun sleep(from: Long, to: Long) = emptyList<HealthConnect.SleepSession>()

    override fun weight(from: Long, to: Long): HealthConnect.Weight? = null
}
