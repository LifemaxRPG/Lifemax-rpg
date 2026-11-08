package com.lifemax.rpg.feature.pet.host

import com.lifemax.rpg.feature.pet.brain.Catalogue
import com.lifemax.rpg.feature.pet.world.FakePage
import com.lifemax.rpg.feature.pet.world.FakeTimeSource
import com.lifemax.rpg.feature.pet.world.WorldFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The owner's "stuck in bed" report: the cats went to bed together and stayed there for hours, and
 * a meal only got them out to eat before they went straight back. Whole days of two imported cats
 * in the real host against the page, with the page's meals served at its meal hours, from an
 * evening start and from a morning start with the app closed for six hours in the middle (caught up
 * on the next open) and a cold start. Over each day every cat is awake a realistic share of the
 * time, nothing outlives its longest, and two sleeping cats are never on one spot.
 * `CAT_TIMELINE=<dir>` writes the timelines.
 */
class WholeDayTest {
    private fun page(): FakePage =
        FakePage(WorldFixtures.NOW).also { p ->
            p.adopt(
                FakePage.Cat(
                    born = WorldFixtures.NOW - 40 * DAY,
                    name = "Ted",
                    lastFed = WorldFixtures.NOW - 5 * HOUR,
                    legacy = WorldFixtures.legacy("damaged"),
                )
            )
            p.adopt(
                FakePage.Cat(
                    born = WorldFixtures.NOW - 35 * DAY,
                    name = "Lily",
                    lastFed = WorldFixtures.NOW - 5 * HOUR,
                    legacy = WorldFixtures.legacy("v77"),
                )
            )
        }

    /** Runs [minutes] open, serving both cats a meal at each of [MEALS_H] it passes. */
    private fun HouseWatch.day(minutes: Int) {
        repeat(minutes) {
            val h = Math.floorMod(time.epoch, DAY) / HOUR.toDouble()
            if (MEALS_H.any { m -> h >= m && h < m + 1.0 / MINUTES_PER_HOUR }) {
                for (slot in 0..1) page.cats[slot].lastFed = time.epoch
                project()
                for (slot in 0..1) host.ownerAct("feed:$slot")
            }
            minutes(1)
        }
    }

    /**
     * The checks over [w]'s rows, which began at local hour [startH]; with [daytime] the awake
     * share is over the rows between waking and bed hours only (a day sampled with its night).
     */
    private fun check(w: HouseWatch, what: String, startH: Int, daytime: Boolean = false) {
        val registry = Catalogue.core()
        for (slot in 0..1) {
            val rows =
                w.rows.filter {
                    val h = (startH + it.minute / MINUTES_PER_HOUR) % HOURS
                    it.slot == slot && (!daytime || (h >= DAY_FROM_H && h < DAY_UNTIL_H))
                }
            val awake = rows.count { !(registry.find(it.activity)?.asleep ?: false) }
            val share = awake.toDouble() / rows.size
            assertTrue("$what: cat $slot awake ${(share * 100).toInt()} %", share >= MIN_AWAKE)
        }
        val layered =
            w.rows
                .groupBy { it.minute }
                .filterValues { r ->
                    r.size == 2 &&
                        r.all { registry.find(it.activity)?.asleep == true } &&
                        r[0].surface == r[1].surface &&
                        Math.abs(r[0].x - r[1].x) < OVERLAP_PX &&
                        Math.abs(r[0].z - r[1].z) < OVERLAP_Z
                }
        assertEquals("$what: both asleep on one spot at ${layered.keys}", 0, layered.size)
        for (slot in 0..1) {
            var run = 0
            var last: HouseWatch.Row? = null
            for (r in w.rows.filter { it.slot == slot }) {
                val same =
                    last != null &&
                        r.minute == last.minute + 1 &&
                        r.activity == last.activity &&
                        r.place == last.place &&
                        r.why == last.why
                run = if (same) run + 1 else 0
                val max = registry.find(r.activity)?.maxMinutes ?: continue
                assertTrue("$what: cat $slot ${r.activity} $run min", run <= max + SLACK_MIN)
                last = r
            }
        }
    }

    @Test
    fun aWholeDayFromTheEvening() {
        val time = FakeTimeSource(0L, WorldFixtures.NOW + (EVENING_H - NOON) * HOUR)
        val w = HouseWatch(page(), time)
        w.open()
        w.day(DAY_MIN)
        w.dump("day-from-evening", "a whole day from 19:00, meals at the page's hours")
        check(w, "from the evening", EVENING_H)
    }

    @Test
    fun aWholeDayWithTheAppClosedSixHoursAndARestart() {
        val time = FakeTimeSource(0L, WorldFixtures.NOW + (MORNING_H - NOON) * HOUR)
        var w = HouseWatch(page(), time)
        w.open()
        w.day(SIX_H_MIN)
        w.close()
        w.host.onStop()
        w.away(SIX_H_MIN)
        w.open()
        w.day(SIX_H_MIN)
        w = w.restart()
        w.open()
        w.day(SIX_H_MIN)
        w.dump("day-with-gap", "a day from 07:00: six hours, six closed, six, a restart, six")
        check(w, "with a gap and a restart", MORNING_H, daytime = true)
    }

    private companion object {
        const val HOUR = 3_600_000L
        const val DAY = 86_400_000L
        const val NOON = 12
        const val EVENING_H = 19
        const val MORNING_H = 7
        const val DAY_MIN = 24 * 60
        const val SIX_H_MIN = 6 * 60
        const val MINUTES_PER_HOUR = 60.0
        val MEALS_H = doubleArrayOf(8.0, 18.25)
        const val MIN_AWAKE = 0.35
        const val OVERLAP_PX = 20f
        const val OVERLAP_Z = 14f
        const val SLACK_MIN = 3
        const val HOURS = 24
        const val DAY_FROM_H = 7.5
        const val DAY_UNTIL_H = 22.0
    }
}
