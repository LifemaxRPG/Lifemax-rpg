package com.lifemax.rpg.core.quests

import com.lifemax.rpg.core.economy.EconomyClock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZonedDateTime

/**
 * The things the quest board reads from the clock, as the page computes them at call time (QST-11,
 * QST-12): `getToday()`, `new Date().getDay()`, the weekly period and `Date.now()`. Built from the
 * device clock ([EconomyClock]) so the board and the economy agree on today.
 */
data class BoardClock(
    /** `getToday()`: the local day key (`YYYY-MM-DD`). */
    val today: String,
    /** `new Date().getDay()`: 0 is Sunday. */
    val weekday: Int,
    /** The weekly quests' period: the day key of "this week's Monday" as the page finds it. */
    val weekKey: String,
    /** `Date.now()`, the number a new custom quest's id is made from. */
    val nowMillis: Long,
) {
    companion object {
        fun of(clock: EconomyClock): BoardClock {
            val local = ZonedDateTime.ofInstant(Instant.ofEpochMilli(clock.now), clock.zone)
            val weekday = local.dayOfWeek.toJsDay()
            // `first.setDate(d.getDate() - ((d.getDay() + 6) % 7))`: the same wall-clock time some
            // days earlier. A wall time that does not exist on that day moves forward by the gap,
            // as `Date` does.
            val back = (weekday + 6) % 7
            val wall: LocalDateTime = local.toLocalDateTime().minusDays(back.toLong())
            val monday = ZonedDateTime.ofLocal(wall, clock.zone, null).toLocalDate().toString()
            return BoardClock(clock.today, weekday, monday, clock.now)
        }

        private fun DayOfWeek.toJsDay(): Int = value % 7
    }
}
