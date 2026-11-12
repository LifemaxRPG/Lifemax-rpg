package com.lifemax.rpg.feature.pet.brain

import com.lifemax.rpg.feature.pet.body.CatBody
import com.lifemax.rpg.feature.pet.world.FakeTimeSource
import com.lifemax.rpg.feature.pet.world.WorldBlobJson
import com.lifemax.rpg.feature.pet.world.WorldClock
import com.lifemax.rpg.feature.pet.world.WorldFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The brain as the world host uses it (plan 2.3, 2.6, 2.7): its state goes through the blob and
 * comes back the same cat in the same place doing the same thing; the catch-up is a pure function
 * of the state and the gap and tells a short story; [CatWorld.bind] hands the house's bodies over
 * without moving a cat.
 */
class BrainStateTest {
    private val cats =
        listOf(
            CatDays.cat("Ted", CatDays.PLAYFUL, id = WorldFixtures.TED),
            CatDays.cat("Lily", CatDays.SHY, id = WorldFixtures.LILY, slot = 1),
        )

    private fun world(at: Long = WorldFixtures.NOW): Pair<CatWorld, WorldClock> {
        val clock = WorldClock(FakeTimeSource(0L, at))
        return CatWorld(clock, cats) to clock
    }

    private fun run(w: CatWorld, c: WorldClock, minutes: Int) {
        c.advanceTo(minutes * 60_000_000_000L + 1, Int.MAX_VALUE) { s, t -> w.step(s, t) }
    }

    @Test
    fun aSavedMindComesBackInPlaceDoingTheSame() {
        val (w, c) = world()
        run(w, c, 45)
        val saved = w.export()
        // the state survives the blob's strict codec
        for (cat in saved.cats.values) {
            val blob = WorldBlobJson.encode(blobOf(saved))
            assertTrue(blob.toString().length < 8192)
            assertTrue(cat.act.matches(Regex("[a-z][a-z0-9_]{0,31}")))
        }
        val (back, _) = world(c.epochMs())
        back.restore(saved)
        for (m in back.minds) {
            val was = w.minds[m.slot]
            assertEquals(was.exec.surface, m.exec.surface)
            assertEquals(was.exec.x, m.exec.x, 0.01f)
            // an activity of seconds (a look round, a dash) is not taken up again after a
            // restart; one with at least a minute left is
            if (was.runner.seconds - was.runner.atPlaceS >= LONG_ENOUGH_S)
                assertEquals(was.runner.activity.id, m.runner.activity.id)
            assertEquals(was.mood.valence, m.mood.valence, 0.001)
        }
        // what a later phase owns rides through untouched
        assertEquals(
            saved.cats.mapValues { it.value.fx },
            back.export().cats.mapValues { it.value.fx },
        )
    }

    @Test
    fun theCatchUpIsAPureFunctionOfTheStateAndTheGap() {
        fun story(): Pair<CatchUp, Long> {
            val (w, c) = world()
            run(w, c, 10)
            val t0 = System.nanoTime()
            val up = w.catchUp(6 * 3_600_000L)
            return up to (System.nanoTime() - t0)
        }
        val (a, ns) = story()
        val (b, warm) = story()
        assertEquals(a, b)
        assertTrue(a.lines.size in 1..3)
        assertEquals(2, a.places.size)
        println(
            "a six hour gap (its last hour played) caught up in ${ns / 1_000_000} ms cold, ${warm / 1_000_000} ms warm: ${a.lines}"
        )
    }

    @Test
    fun bindingTheHousesBodiesKeepsEveryCatWhereItIs() {
        val (w, c) = world()
        run(w, c, 5)
        val where = w.minds.map { it.exec.surface to it.exec.x }
        val bodies = listOf(CatBody(1), CatBody(2))
        w.bind(bodies)
        for (m in w.minds) {
            assertSame(bodies[m.slot], (m.exec as BodyExecutor).body)
            assertEquals(where[m.slot].first, bodies[m.slot].pose.surface)
            assertEquals(where[m.slot].second, bodies[m.slot].pose.x, 0.01f)
        }
        run(w, c, 10) // and the brain goes on playing them
        assertTrue(bodies.all { it.planId > 0 })
    }

    private companion object {
        const val LONG_ENOUGH_S = 60.0
    }

    private fun blobOf(s: BrainState) =
        com.lifemax.rpg.feature.pet.world.WorldBlob(
            v = 1,
            imported = "fresh",
            cats = s.cats,
            pair = s.pair,
            toys = s.toys,
            gift = s.gift,
            outbox = emptyList(),
            away = null,
        )
}
