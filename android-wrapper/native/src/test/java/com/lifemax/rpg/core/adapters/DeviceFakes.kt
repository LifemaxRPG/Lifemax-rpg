package com.lifemax.rpg.core.adapters

import com.lifemax.rpg.core.economy.HealthConnect
import java.time.Instant
import java.time.ZoneId

/** A device clock the test sets: both the instant and the zone can be moved between readings. */
class FixedDeviceClock(var now: Long, var zone: ZoneId) : DeviceClock {
    constructor(
        iso: String,
        zone: String,
    ) : this(Instant.parse(iso).toEpochMilli(), ZoneId.of(zone))

    override fun nowMillis(): Long = now

    override fun zone(): ZoneId = zone
}

/** A save source the test sets. */
class FakeSaveSource(var view: SaveView? = null) : SaveSource {
    override fun view(): SaveView? = view
}

/** A plan the test describes: the days that are done and the days that are rest days. */
class FakePlanFacts(
    private val done: Set<String> = emptySet(),
    private val rest: (String) -> Boolean = { false },
) : PlanFacts {
    override fun isDone(day: String) = day in done

    override fun isRestDay(day: String) = rest(day)
}

/**
 * Health Connect as a test fakes it: fixed answers, a record of the ranges asked, and a failure on
 * the read named [failing].
 */
class FakeHealthReads(
    var status: HealthConnect.Status = HealthConnect.Status("available", emptyList()),
    var workouts: List<HealthConnect.Workout> = emptyList(),
    var steps: List<HealthConnect.StepDay> = emptyList(),
    var sleeps: List<HealthConnect.SleepSession> = emptyList(),
    var weight: HealthConnect.Weight? = null,
    var failing: String? = null,
) : HealthReads {
    /** Every read made, as `name from to`. */
    val asked = mutableListOf<String>()

    private fun read(name: String, from: Long = 0, to: Long = 0) {
        asked += "$name $from $to"
        if (failing == name) throw IllegalStateException(FAIL)
    }

    override fun status(): HealthConnect.Status {
        read("status")
        return status
    }

    override fun exercise(from: Long, to: Long): List<HealthConnect.Workout> {
        read("exercise", from, to)
        return workouts
    }

    override fun steps(from: Long, to: Long): List<HealthConnect.StepDay> {
        read("steps", from, to)
        return steps
    }

    override fun sleep(from: Long, to: Long): List<HealthConnect.SleepSession> {
        read("sleep", from, to)
        return sleeps
    }

    override fun weight(from: Long, to: Long): HealthConnect.Weight? {
        read("weight", from, to)
        return weight
    }

    companion object {
        const val FAIL = "Health Connect stopped answering"
    }
}
