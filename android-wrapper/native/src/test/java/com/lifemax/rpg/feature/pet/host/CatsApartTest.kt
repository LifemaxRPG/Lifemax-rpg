package com.lifemax.rpg.feature.pet.host

import com.lifemax.rpg.feature.pet.brain.Catalogue
import com.lifemax.rpg.feature.pet.world.FakePage
import com.lifemax.rpg.feature.pet.world.FakeTimeSource
import com.lifemax.rpg.feature.pet.world.WorldFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The owner's report end to end: two cats from the legacy import, the real brain in the real host
 * against the page, watched minute by minute for two hours, then the app closed for five hours
 * (caught up on the next open), an hour, a cold start from the stored world and two more hours.
 * Before the fix the night import put both cats on the bed at one point and both took up their
 * sleep there, layered for over an hour (65 of the first minutes); two cats imported at one saved
 * place sat on each other for 26 minutes. Now no minute finds them on top of each other twice in a
 * row, and no activity runs past its longest. `CAT_TIMELINE=<dir>` writes the timelines.
 */
class CatsApartTest {
    private fun page(a: String?, b: String?): FakePage =
        FakePage(WorldFixtures.NOW).also { p ->
            p.adopt(
                FakePage.Cat(
                    born = WorldFixtures.NOW - 40 * DAY,
                    name = "Ted",
                    lastFed = WorldFixtures.NOW - HOUR,
                    legacy = a?.let { WorldFixtures.legacy(it) },
                )
            )
            p.adopt(
                FakePage.Cat(
                    born = WorldFixtures.NOW - 35 * DAY,
                    name = "Lily",
                    lastFed = WorldFixtures.NOW - HOUR,
                    legacy = b?.let { WorldFixtures.legacy(it) },
                )
            )
        }

    /** The day as the owner lives it, from [startH] local, with the web brains [a] and [b]. */
    private fun day(name: String, startH: Int, a: String?, b: String?): HouseWatch {
        val time = FakeTimeSource(0L, WorldFixtures.NOW + (startH - NOON) * HOUR)
        var w = HouseWatch(page(a, b), time)
        w.open()
        w.log()
        w.minutes(OPEN_MIN)
        w.close()
        w.host.onStop()
        w.away(AWAY_MIN)
        w.open()
        w.log()
        w.minutes(AFTER_GAP_MIN)
        w = w.restart()
        w.open()
        w.log()
        w.minutes(OPEN_MIN)
        w.dump(name, "$name, from $startH:00, web brains $a and $b")
        return w
    }

    /** Minutes at which the two cats stood on top of each other. */
    private fun layered(w: HouseWatch): List<Int> =
        w.rows
            .groupBy { it.minute }
            .filterValues { r ->
                r.size == 2 &&
                    r[0].surface == r[1].surface &&
                    Math.abs(r[0].x - r[1].x) < OVERLAP_PX &&
                    Math.abs(r[0].z - r[1].z) < OVERLAP_Z
            }
            .keys
            .sorted()

    private fun assertApart(w: HouseWatch, what: String) {
        val l = layered(w)
        val runs = l.zipWithNext().count { (x, y) -> y == x + 1 }
        assertEquals("$what: layered in consecutive minutes $l", 0, runs)
        assertTrue("$what: layered too often $l", l.size <= MAX_PASSING_SAMPLES)
    }

    /** No cat does one thing (same activity, place and reason) for longer than its longest. */
    private fun assertNothingOutlives(w: HouseWatch) {
        val registry = Catalogue.core()
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
                assertTrue("cat $slot ${r.activity} for $run minutes", run <= max + SLACK_MIN)
                last = r
            }
        }
    }

    @Test
    fun twoCatsImportedAtNightDoNotShareTheBed() {
        val w = day("night", NIGHT_H, "damaged", "v77")
        assertApart(w, "two imported cats at night")
        assertNothingOutlives(w)
        val first = w.rows.filter { it.minute == 0 }
        assertTrue("they start apart: $first", first[0].surface != first[1].surface)
    }

    @Test
    fun twoCatsImportedAtOneSavedPlaceAreParted() {
        val w = day("one-place", NIGHT_H, "rich", "rich")
        assertApart(w, "two cats saved at one place")
        assertNothingOutlives(w)
    }

    @Test
    fun twoCatsImportedByDayKeepApart() {
        val w = day("day", DAY_H, "damaged", null)
        assertApart(w, "two imported cats by day")
        assertNothingOutlives(w)
        // both get about while awake: each is seen in several places
        for (slot in 0..1) {
            val places =
                w.rows.filter { it.slot == slot }.map { "${it.surface}:${(it.x / 20).toInt()}" }
            assertTrue("cat $slot was at ${places.toSet()}", places.toSet().size >= MIN_PLACES)
        }
    }

    private companion object {
        const val HOUR = 3_600_000L
        const val DAY = 86_400_000L
        const val NOON = 12
        const val NIGHT_H = 23
        const val DAY_H = 13
        const val OPEN_MIN = 120
        const val AWAY_MIN = 300
        const val AFTER_GAP_MIN = 60
        const val OVERLAP_PX = 20f
        const val OVERLAP_Z = 14f
        const val MAX_PASSING_SAMPLES = 3
        const val SLACK_MIN = 3
        const val MIN_PLACES = 4
    }
}
