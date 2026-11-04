package com.lifemax.rpg.feature.pet.brain

import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.world.BlobPos
import com.lifemax.rpg.feature.pet.world.FakeTimeSource
import com.lifemax.rpg.feature.pet.world.WorldClock
import com.lifemax.rpg.feature.pet.world.WorldFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two cats never lie layered on one spot (the owner's report: both cats "in the exact same spot,
 * exactly layered over each other, for the past 30 minutes"). The brain alone, without the import:
 * a world restored with both cats saved asleep on the bed (what the import used to write for two
 * cats at night) parts them at once and keeps them apart through hours, a restart and a catch-up;
 * two cats put down on one point of the floor are parted within seconds; no activity outlives its
 * longest ([Activity.maxMinutes], the runner's watchdog); and both cats get about by day.
 */
class CatsApartBrainTest {
    private val cats =
        listOf(
            CatDays.cat("Ted", CatDays.AVERAGE, id = WorldFixtures.TED),
            CatDays.cat("Lily", CatDays.AVERAGE, id = WorldFixtures.LILY, slot = 1),
        )

    private fun world(at: Long): Pair<CatWorld, WorldClock> {
        val clock = WorldClock(FakeTimeSource(0L, at))
        return CatWorld(clock, cats, pickFirst = false) to clock
    }

    /** Both cats saved at [pos] doing [act]: the state the import wrote for two cats at night. */
    private fun bothAt(pos: BlobPos, act: String) =
        BrainState(
            mapOf(
                WorldFixtures.TED to WorldFixtures.cat().copy(pos = pos, act = act),
                WorldFixtures.LILY to WorldFixtures.cat().copy(pos = pos, act = act),
            )
        )

    /** Runs [minutes], calling [each] after every think quantum and [minute] once a minute. */
    private fun run(
        w: CatWorld,
        c: WorldClock,
        minutes: Int,
        each: () -> Unit = {},
        minute: (Int) -> Unit = {},
    ) {
        val from = c.subSteps
        var quanta = 0L
        val target = (from + minutes * STEPS_PER_MIN) * NANOS_PER_S / STEPS_PER_S
        c.advanceTo(target + 1, Int.MAX_VALUE) { s, t ->
            w.step(s, t)
            if (!t) return@advanceTo
            each()
            if (++quanta % QUANTA_PER_MIN == 0L) minute((quanta / QUANTA_PER_MIN).toInt())
        }
    }

    /** Minutes at which the cats stood on top of each other, as one minute's samples see it. */
    private fun layeredMinutes(w: CatWorld, c: WorldClock, minutes: Int): List<Int> {
        val out = ArrayList<Int>()
        run(w, c, minutes, minute = { if (w.crowded(0)) out += it })
        return out
    }

    private fun assertApart(what: String, layered: List<Int>) {
        // a cat walking past the other can be caught in one sample; never two in a row
        val runs = layered.zipWithNext().count { (a, b) -> b == a + 1 }
        assertEquals("$what: layered in consecutive minutes $layered", 0, runs)
        assertTrue("$what: layered too often $layered", layered.size <= MAX_PASSING_SAMPLES)
    }

    @Test
    fun twoCatsSavedAsleepOnTheBedAreNeverLeftLayered() {
        val night = WorldFixtures.NOW + 11 * HOUR_MS // 23:00
        val (w, c) = world(night)
        w.restore(bothAt(BlobPos(SurfaceId.BED.key, 36.0, 35.0, 1), "sleep"))
        // the first takes up its sleep on the bed; the second does not take the same bed
        assertEquals("sleep", w.now(0).activity)
        assertEquals(Spots.BED, w.book.stayOf(0))
        assertNotEquals(Spots.BED, w.book.stayOf(1))
        assertNotEquals(Spots.BED, w.book.claimOf(1))
        assertApart("three hours of the night", layeredMinutes(w, c, 180))

        // a restart from the saved world, then a long closed gap caught up
        val saved = w.export()
        val (back, c2) = world(c.epochMs())
        back.restore(saved)
        assertApart("an hour after a restart", layeredMinutes(back, c2, 60))
        back.catchUp(5 * HOUR_MS)
        assertTrue("apart after the catch-up", !back.crowded(0))
        assertApart("an hour after the catch-up", layeredMinutes(back, c2, 60))
    }

    @Test
    fun twoCatsPutDownOnOnePointOfTheFloorArePartedWithinSeconds() {
        val (w, c) = world(WorldFixtures.NOW)
        w.restore(bothAt(BlobPos(SurfaceId.FLOOR.key, 180.0, 50.0, 1), "groom"))
        var parted = -1
        var quanta = 0
        run(w, c, 2, each = { if (parted < 0 && !w.crowded(0)) parted = quanta else quanta++ })
        assertTrue("parted after $quanta quanta", parted in 0..PART_WITHIN_QUANTA)
        assertTrue(
            w.minds.any {
                it.runner.explain.entries().any { r -> r.reason.startsWith("makes way") }
            }
        )
    }

    @Test
    fun noActivityOutlivesItsLongestAndTheWatchdogEndsOneThatWould() {
        val (w, c) = world(WorldFixtures.NOW - 6 * HOUR_MS) // 06:00
        w.restore(bothAt(BlobPos(SurfaceId.FLOOR.key, 180.0, 50.0, 1), "idle"))
        // a sleep forced to last ten hours: the watchdog ends it at the longest sleep there is
        w.minds[0].force("sleep", Spots.BED, 10 * 3600.0)
        val longest = HashMap<String, Double>()
        run(
            w,
            c,
            24 * 60,
            each = {
                for (m in w.minds) {
                    val r = m.runner
                    longest.merge(r.activity.id, r.elapsedS, ::maxOf)
                }
            },
        )
        val registry = Catalogue.core()
        for ((id, s) in longest) {
            val max = registry.find(id)!!.maxMinutes * 60 + ROUTE_MAX_S + QUANTUM_SLACK_S
            assertTrue("$id lasted ${s / 60} min, at most ${max / 60}", s <= max)
        }
        assertTrue("the forced sleep was ended", w.minds[0].runner.watchdogs >= 1)
        assertEquals("a healthy cat never needs the watchdog", 0, w.minds[1].runner.watchdogs)
    }

    @Test
    fun bothCatsGetAboutOverThreeAwakeHours() {
        val (w, c) = world(WorldFixtures.NOW - 3 * HOUR_MS) // 09:00
        w.restore(
            BrainState(
                mapOf(
                    WorldFixtures.TED to
                        WorldFixtures.cat()
                            .copy(pos = BlobPos("floor", 87.0, 40.0, 1), act = "idle"),
                    WorldFixtures.LILY to
                        WorldFixtures.cat()
                            .copy(pos = BlobPos("floor", 121.0, 40.0, 1), act = "idle"),
                )
            )
        )
        val places = List(2) { HashSet<String>() }
        val path = FloatArray(2)
        val last = w.minds.map { it.exec.x }.toFloatArray()
        run(
            w,
            c,
            180,
            each = {
                for (m in w.minds) {
                    path[m.slot] += Math.abs(m.exec.x - last[m.slot])
                    last[m.slot] = m.exec.x
                }
            },
            minute = {
                for (m in w.minds) places[m.slot] +=
                    "${m.exec.surface.key}:${(m.exec.x / 20).toInt()}"
            },
        )
        for (slot in 0..1) {
            assertTrue("cat $slot was in ${places[slot]}", places[slot].size >= MIN_PLACES)
            assertTrue("cat $slot walked ${path[slot]} px", path[slot] >= MIN_PATH_PX)
        }
    }

    private companion object {
        const val HOUR_MS = 3_600_000L
        const val STEPS_PER_MIN = 120L * 60
        const val NANOS_PER_S = 1_000_000_000L
        const val STEPS_PER_S = 120L
        const val QUANTA_PER_MIN = 240L
        const val MAX_PASSING_SAMPLES = 3
        const val PART_WITHIN_QUANTA = 40
        const val ROUTE_MAX_S = 120.0
        const val QUANTUM_SLACK_S = 1.0
        const val MIN_PLACES = 4
        const val MIN_PATH_PX = 300f
    }
}
