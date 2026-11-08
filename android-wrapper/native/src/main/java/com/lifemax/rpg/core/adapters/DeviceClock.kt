package com.lifemax.rpg.core.adapters

import com.lifemax.rpg.core.economy.EconomyClock
import com.lifemax.rpg.core.quests.BoardClock
import java.time.ZoneId

/**
 * The phone's wall clock and time zone, as a port: production reads the system, tests set both.
 * Nothing in the adapter layer reads `System` or `ZoneId.systemDefault()` directly.
 *
 * Behaviour, pinned by the clock golden family (`device-context-golden.json`) and
 * `DeviceClockTest`:
 * - **Nothing is cached.** Every action takes one [reading] and uses it from start to end, so one
 *   action never sees two different days. The page reads `new Date()` afresh at each call; the two
 *   agree except for an action that runs across midnight, which the page could split and native
 *   cannot.
 * - **The day key is a pure function of the instant and the zone** (`toLocaleDateString('en-CA')`):
 *   daylight saving, a midnight that does not exist (Santiago) and offsets of +14 or -11 need no
 *   special case.
 * - **A manual clock change is followed, not repaired.** Moving the clock back gives an earlier day
 *   key at once, forward a later one, exactly as the page does; whatever the economy stored under
 *   the later day stays stored (it never rewrites history), and rules that compare day keys as text
 *   (TIME-5) see the change.
 * - **A zone change takes effect at the next reading**, not before.
 */
interface DeviceClock {
    /** `Date.now()`: milliseconds since 1970-01-01T00:00:00Z. */
    fun nowMillis(): Long

    /** The device's current time zone. */
    fun zone(): ZoneId
}

/** The real device clock. */
object SystemDeviceClock : DeviceClock {
    override fun nowMillis(): Long = System.currentTimeMillis()

    override fun zone(): ZoneId = ZoneId.systemDefault()
}

/** One reading of the clock for the economy: the instant first, then the zone. */
fun DeviceClock.reading(): EconomyClock = EconomyClock(nowMillis(), zone())

/** One reading of the clock for the quest board (day, weekday, week key and `Date.now()`). */
fun DeviceClock.boardReading(): BoardClock = BoardClock.of(reading())
