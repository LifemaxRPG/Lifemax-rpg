package com.lifemax.rpg.feature.pet.brain

import com.lifemax.rpg.feature.pet.world.FakeTimeSource
import com.lifemax.rpg.feature.pet.world.WorldClock
import com.lifemax.rpg.feature.pet.world.WorldFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The developer parkour demo ([DemoParkour], the Cat tab's "Demo parkour" button): which cat it
 * sends, who it refuses and why, alternation, no overlap between the cats, repeat-safety, no data
 * effects, and a headless run (real bodies, the real planner) that proves a tap leads to a parkour
 * tour of at least two jumps in bounded time and ends cleanly.
 */
class DemoParkourTest {
    private val cats =
        listOf(
            CatDays.cat("Ted", CatDays.AVERAGE, id = WorldFixtures.TED),
            CatDays.cat("Lily", CatDays.AVERAGE, id = WorldFixtures.LILY, slot = 1),
        )

    private fun world(): Pair<CatWorld, WorldClock> {
        val clock = WorldClock(FakeTimeSource(0L, WorldFixtures.NOW)) // 12:00
        val w = CatWorld(clock, cats)
        for (m in w.minds) m.drives[Drive.ENERGY] = RESTED
        return w to clock
    }

    /** Both cats loafing in their own place in the open, rested. */
    private fun loafing(): Pair<CatWorld, WorldClock> {
        val (w, c) = world()
        w.minds[0].force("loaf", Spots.RUG, LONG_S)
        w.minds[1].force("loaf", Spots.LIVING_RUG, LONG_S)
        return w to c
    }

    /** Runs [minutes], calling [each] after every think quantum. */
    private fun run(w: CatWorld, c: WorldClock, minutes: Int, each: () -> Unit = {}) {
        val target = (c.subSteps + minutes * STEPS_PER_MIN) * NANOS_PER_S / STEPS_PER_S
        c.advanceTo(target + 1, Int.MAX_VALUE) { s, t ->
            w.step(s, t)
            if (t) each()
        }
    }

    @Test
    fun anAwakeCatIsSentAndTheAnswerNamesIt() {
        val (w, _) = loafing()
        val a = w.demoParkour()
        assertTrue(a.message, a.played)
        assertEquals("", a.message)
        val m = w.minds[a.slot]
        assertTrue(m.touring)
        assertEquals("parkour visit", w.now(a.slot).activity)
        assertEquals(DemoParkour.REASON, w.now(a.slot).why)
    }

    @Test
    fun aCatThatCannotIsRefusedWithAOneLineReason() {
        fun refused(set: (CatWorld) -> Unit, expect: String) {
            val (w, _) = loafing()
            set(w)
            val before = w.minds.map { it.runner.activity.id + "/" + it.refusesDemo() }
            val a = w.demoParkour()
            assertFalse("$expect: sent (slot ${a.slot}) although $before", a.played)
            assertTrue(a.message, a.message.contains(expect))
            assertTrue(a.message, a.message.startsWith("Ted"))
            assertFalse(a.message.contains(PlayWords.NAME))
            assertTrue("nobody went touring", w.minds.none { it.touring })
        }
        fun both(f: (CatMind) -> Unit): (CatWorld) -> Unit = { w -> w.minds.forEach(f) }
        refused(both { it.force("sleep", null, LONG_S) }, "asleep")
        refused(both { it.force(Catalogue.EAT, Spots.bowl(it.slot), LONG_S) }, "eating")
        refused(both { it.hunger(HUNGRY_STAGE, false) }, "hungry")
        refused(both { it.drives[Drive.ENERGY] = 0.05 }, "sleepy")
    }

    @Test
    fun aGameAndOtherBusyStatesAreRefusedByTheRules() {
        val (w, _) = loafing()
        val m = w.minds[0]
        assertTrue(DemoParkour.refusal(m, OwnerPlay)!!.contains("game"))
        assertTrue(DemoParkour.refusal(m, MakeWay)!!.contains("busy"))
        assertTrue(DemoParkour.refusal(m, Beg)!!.contains("busy"))
        assertNull(DemoParkour.refusal(m, Loaf))
        assertNull(DemoParkour.refusal(m, Wander))
    }

    @Test
    fun theCatDoingLeastIsSentNotOneBusyOrAsleep() {
        val (w, _) = loafing()
        w.minds[0].force("scratch", Spots.SCRATCH_TREE, LONG_S)
        assertEquals("the loafing cat, not the scratching one", 1, w.demoParkour().slot)
        val (w2, _) = loafing()
        w2.minds[1].force("sleep", Spots.BED, LONG_S)
        assertEquals("the awake cat, not the sleeping one", 0, w2.demoParkour().slot)
    }

    @Test
    fun repeatedTapsAlternateTheCatsAndNeverOverlap() {
        val (w, c) = loafing()
        val first = w.demoParkour()
        assertTrue(first.played)
        // a second tap while the tour runs: refused, nothing else disturbed (repeat-safe)
        val other = 1 - first.slot
        val before = w.now(other).activity
        val again = w.demoParkour()
        assertFalse(again.played)
        assertEquals(DemoParkour.RUNNING, again.message)
        assertEquals(before, w.now(other).activity)
        assertTrue(w.minds[first.slot].touring)
        assertFalse(w.minds[other].touring)
        // the tour ends; the next tap sends the other cat
        var crowded = 0
        var worst = 0
        run(w, c, TOUR_MINUTES) {
            if (w.crowded(0)) worst = maxOf(worst, ++crowded) else crowded = 0
        }
        assertFalse(w.minds[first.slot].touring)
        // the cat's loaf is over; make both cats idle again, rested
        w.minds.forEach {
            it.force("loaf", if (it.slot == 0) Spots.RUG else Spots.LIVING_RUG, LONG_S)
            it.drives[Drive.ENERGY] = RESTED
        }
        run(w, c, 1)
        val second = w.demoParkour()
        assertTrue(second.message, second.played)
        assertEquals("the other cat", other, second.slot)
        run(w, c, TOUR_MINUTES) {
            if (w.crowded(0)) worst = maxOf(worst, ++crowded) else crowded = 0
        }
        assertTrue("the cats stayed apart (longest overlap $worst quanta)", worst < OVERLAP_QUANTA)
    }

    @Test
    fun aTapReportsNothingToThePage() {
        val (w, c) = loafing()
        val a = w.demoParkour()
        assertTrue(a.played)
        run(w, c, TOUR_MINUTES)
        assertTrue(
            "nothing is reported to the page (no play, no XP, no counters)",
            w.takeReports().isEmpty(),
        )
    }

    @Test
    fun aTapLeadsToAParkourTourOfAtLeastTwoJumpsInBoundedTimeAndEndsCleanly() {
        for (slot in 0..1) {
            val (w, c) = loafing()
            // the cat is made to be the preferred one by the other one being busy
            w.minds[1 - slot].force("scratch", Spots.SCRATCH_TREE, LONG_S)
            val answer = w.demoParkour()
            assertEquals(slot, answer.slot)
            val m = w.minds[slot]
            var surface = m.exec.surface
            var jumps = 0
            var visited = 0
            var endedAtQuantum = -1
            var quanta = 0
            var last = ""
            run(w, c, TOUR_MINUTES) {
                quanta++
                if (m.exec.surface != surface) {
                    jumps++
                    surface = m.exec.surface
                }
                val d = w.now(slot)
                if (
                    d.why == DemoParkour.REASON && d.activity == "parkour visit" && d.place != last
                ) {
                    visited++
                    last = d.place
                }
                if (endedAtQuantum < 0 && !m.touring) endedAtQuantum = quanta
            }
            val story =
                "slot $slot: jumps $jumps, stops $visited, ended at quantum $endedAtQuantum; " +
                    m.runner.explain.entries().takeLast(6).joinToString(" | ")
            assertTrue("at least two jumps: $story", jumps >= 2)
            assertTrue("more than one perch: $story", visited >= 2)
            assertTrue("the tour ended in bounded time: $story", endedAtQuantum in 1..MAX_QUANTA)
            assertEquals("no watchdog had to step in", 0, m.runner.watchdogs)
            assertFalse("the tour is over", m.touring)
            assertNotEquals(
                "the cat went on to something else",
                DemoParkour.REASON,
                w.now(slot).why,
            )
            assertTrue(w.takeReports().isEmpty())
        }
    }

    private companion object {
        const val RESTED = 0.9
        const val HUNGRY_STAGE = 4
        const val LONG_S = 3600.0
        const val TOUR_MINUTES = 8
        const val OVERLAP_QUANTA = 20
        const val MAX_QUANTA = 6 * 240 // six minutes of think quanta (240 a minute)
        const val STEPS_PER_MIN = 120L * 60
        const val NANOS_PER_S = 1_000_000_000L
        const val STEPS_PER_S = 120L
    }
}
