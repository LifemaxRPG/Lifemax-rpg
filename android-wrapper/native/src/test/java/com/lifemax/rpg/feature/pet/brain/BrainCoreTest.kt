package com.lifemax.rpg.feature.pet.brain

import com.lifemax.rpg.feature.pet.body.Clips
import com.lifemax.rpg.feature.pet.body.Rng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The brain's parts on their own: drives are bounded and move monotonically (property test over
 * random targets and time constants), the schedule follows the cat's id and the date and never its
 * name, the persona maps DNA to weights, the place book keeps two cats off one perch, the explain
 * ring is bounded, every row a script plays exists, and the registry takes a later phase's activity
 * without touching the core.
 */
class BrainCoreTest {
    @Test
    fun drivesStayBoundedAndMoveMonotonicallyTowardTheirTarget() {
        val rng = Rng(42)
        repeat(200) { trial ->
            val d = Drives()
            val drive = Drives.ALL[trial % Drives.COUNT]
            d[drive] = rng.nextFloat().toDouble()
            val target = if (trial % 2 == 0) 0.0 else 1.0
            val tau = 1.0 + rng.nextFloat() * 20_000.0
            var last = d[drive]
            repeat(500) {
                d.relax(drive, target, tau)
                val v = d[drive]
                assertTrue("bounded $v", v in 0.0..1.0 && !v.isNaN())
                if (target == 0.0) assertTrue("decays $last -> $v", v <= last)
                else assertTrue("recovers $last -> $v", v >= last)
                last = v
            }
            // nudges are bounded and monotone too
            d.nudge(drive, 1 - target, rng.nextFloat().toDouble())
            assertTrue(d[drive] in 0.0..1.0)
        }
    }

    @Test
    fun relaxIsClosedFormSoTheQuantumCountAloneDecides() {
        val a = Drives()
        val b = Drives()
        a[Drive.PLAY] = 0.2
        b[Drive.PLAY] = 0.2
        repeat(240) { a.relax(Drive.PLAY, 1.0, 600.0) }
        // the same 60 s in one go: e^(-60/600)
        val expected = 1.0 - 0.8 * StrictMath.exp(-60.0 / 600.0)
        assertEquals(expected, a[Drive.PLAY], 1e-9)
        repeat(240) { b.relax(Drive.PLAY, 1.0, 600.0) }
        assertEquals(a[Drive.PLAY], b[Drive.PLAY], 0.0)
    }

    @Test
    fun theScheduleFollowsTheWorldIdAndTheDateNeverTheName() {
        fun day(id: String, key: String) =
            Schedule()
                .also { it.plan(id, key, 0.4, false) }
                .let { listOf(it.wake, it.bed, it.dusk, it.napCount.toDouble()) }
        assertEquals(day("cmu9rkw00", "2026-10-30"), day("cmu9rkw00", "2026-10-30"))
        assertNotEquals(day("cmu9rkw00", "2026-10-30"), day("cmu9rkw00", "2026-10-31"))
        assertNotEquals(day("cmu9rkw00", "2026-10-30"), day("cmv2cdxc0", "2026-10-30"))
        val s = Schedule().also { it.plan("c1", "2026-10-30", 1.0, false) }
        assertTrue(s.wake in 6.6..8.0 && s.bed in 22.0..23.5)
        assertTrue(s.inBed(2.0) && !s.inBed(12.0))
        // a renamed cat keeps its days: the name is not part of anything that seeds it
        val ted = CatDays.cat("Ted", CatDays.LAZY, id = "cmu9rkw00")
        val renamed = ted.copy(name = "Teddy")
        val a = CatDays.run(listOf(ted), hours = 2.0, fidelity = CatWorld.Fidelity.FAST)
        val b = CatDays.run(listOf(renamed), hours = 2.0, fidelity = CatWorld.Fidelity.FAST)
        assertEquals(
            a.minds[0].runner.explain.entries().map { it.toString() },
            b.minds[0].runner.explain.entries().map { it.toString() },
        )
    }

    @Test
    fun personaScoresBecomeWeights() {
        val lazy = Persona(CatDays.cat("A", CatDays.LAZY))
        val playful = Persona(CatDays.cat("B", CatDays.PLAYFUL))
        val shy = Persona(CatDays.cat("C", CatDays.SHY))
        assertTrue(lazy.lazy > playful.lazy && lazy.energyDrain > playful.energyDrain)
        assertTrue(playful.temperature > lazy.temperature && playful.durationMul < lazy.durationMul)
        assertTrue(shy.hiddenLike > playful.hiddenLike && shy.highLike < playful.highLike)
        assertTrue(playful.curiosityGain > shy.curiosityGain)
        val kitten = Persona(CatDays.cat("K", CatDays.PLAYFUL, stage = 1))
        assertTrue(kitten.kitten && kitten.growthScale < 1f && kitten.playGain > playful.playGain)
        val hungry = Persona(CatDays.cat("H", CatDays.AVERAGE, hunger = 3))
        assertTrue(hungry.weak && hungry.begs && !hungry.atBowlOnly)
        hungry.vacation = true
        assertFalse(hungry.weak || hungry.begs)
    }

    @Test
    fun theBookKeepsTwoCatsOffOnePerch() {
        val book = SpotBook(2)
        assertTrue(book.free(1, Spots.FRIDGE, Spots.FRIDGE.x, 0))
        book.claim(0, Spots.FRIDGE, Spots.FRIDGE.x, SpotBook.bit(Spots.COUNTER.surface))
        assertFalse("taken", book.free(1, Spots.FRIDGE, Spots.FRIDGE.x, 0))
        assertFalse("its route lands there", book.free(1, Spots.COUNTER, Spots.COUNTER.x, 0))
        book.arrived(0)
        assertTrue("the way up is behind it", book.free(1, Spots.COUNTER, Spots.COUNTER.x, 0))
        assertFalse(
            "my route lands on its perch",
            book.free(1, Spots.KSILL, Spots.KSILL.x, SpotBook.bit(Spots.FRIDGE.surface)),
        )
        // the two sofa ends are two places; floor places keep the gap, the bowls excepted
        book.place(0, Spots.COUCH_L, Spots.COUCH_L.x)
        assertTrue(book.free(1, Spots.COUCH_R, Spots.COUCH_R.x, 0))
        book.place(0, Spots.BOWL_0, Spots.BOWL_0.x)
        assertTrue(book.free(1, Spots.BOWL_1, Spots.BOWL_1.x, 0))
        assertFalse(book.free(1, Spots.RUG, Spots.RUG.x, 0))
        // a stay is kept until the cat has left it
        book.place(0, Spots.BED, Spots.BED.x)
        book.claim(0, Spots.RUG, Spots.RUG.x, 0)
        assertFalse(book.free(1, Spots.BED, Spots.BED.x, 0))
        book.standing(0, Spots.RUG.surface, Spots.RUG.x)
        assertTrue(book.free(1, Spots.BED, Spots.BED.x, 0))
    }

    @Test
    fun theExplainRingIsBounded() {
        val ring = ExplainRing()
        repeat(100) {
            ring.add(Why(it.toLong(), "loaf", "bed", "rests", listOf("loaf@bed" to 1.0)))
        }
        assertEquals(ExplainRing.CAPACITY, ring.entries().size)
        assertEquals(100L, ring.total)
        assertEquals(68L, ring.entries().first().atMs)
        assertEquals("loaf @ bed (rests) [loaf@bed 1.00]", ring.last.toString())
    }

    @Test
    fun everyRowAScriptPlaysExists() {
        for (r in Rows.ALL) assertNotNull(r, Clips.find(r))
    }

    @Test
    fun aLaterPhasePlugsAnActivityIn() {
        val chase =
            object : Activity {
                override val id = "chase the light"
                override val mainDrive = Drive.PLAY
                override val cooldownMin = 30.0

                override fun eligible(m: MindView) = true

                override fun appeal(m: MindView) = 50.0

                override fun reason(m: MindView) = "a later phase"

                override fun duration(m: MindView, rng: Rng) = 20.0

                override fun script(
                    m: MindView,
                    at: Spot?,
                    seconds: Double,
                    rng: Rng,
                    out: Script,
                ) = out.act("play", seconds)
            }
        val registry = Catalogue.core().add(chase)
        val failed = runCatching { Catalogue.core().add(Sleep) }
        assertTrue(failed.isFailure)
        val clock =
            com.lifemax.rpg.feature.pet.world.WorldClock(
                com.lifemax.rpg.feature.pet.world.FakeTimeSource(
                    0,
                    CatDays.MIDNIGHT + 10 * CatDays.HOUR,
                )
            )
        val world =
            CatWorld(
                clock,
                listOf(CatDays.cat("Pip", CatDays.PLAYFUL)),
                CatWorld.Fidelity.FAST,
                registry,
            )
        assertEquals("chase the light", world.now(0).activity)
    }
}
