package com.lifemax.rpg.feature.pet.brain

import com.lifemax.rpg.feature.pet.brain.BehaviourMeter.Needs
import com.lifemax.rpg.feature.pet.brain.BehaviourMeter.Posture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Roaming (docs/specs/CAT_TAB_PLAN.md "Roaming"): the two cats of the house (Ted, a curious playful
 * kitten; Lily, a calm grown cat) get about instead of lying about. Whole days on live bodies,
 * measured by [BehaviourMeter]. The thresholds were set from the numbers before and after the
 * retune (each test says them); the old brain failed every one of the movement tests.
 *
 * Before (two days, awake 08:00 to 21:00, Ted / Lily): moving share of awake time 3% / 2%, asleep
 * 53% / 44% of the daytime, 2.5 / 1.8 places per awake 10 minutes, whole 20 minute windows lying or
 * sitting still (1200 s), no play. After: 32% / 21% moving, asleep 29% / 22%, 12.3 / 11.0 places,
 * longest stretch still 178 s / 187 s, an hour or more of zoomies and play a day for Ted.
 */
class RoamingTest {
    private companion object {
        const val FROM = 8.0
        const val TO = 21.0
        const val TED = 0
        const val LILY = 1

        /** Two days from 07:00 to 21:00 (the fed, owner-watching days of the harness). */
        val days: List<BehaviourMeter.Trace> by lazy {
            (0..1).map { BehaviourMeter.record(day = it, startHour = 7.0, hours = TO - 7.0) }
        }

        /** 10 minute windows in which the cat is awake nearly all the time. */
        fun awakeWindows(t: BehaviourMeter.Trace, cat: Int, minutes: Int) =
            generateSequence(FROM) { it + minutes / 60.0 }
                .takeWhile { it + minutes / 60.0 <= TO }
                .map { t.stats(cat, it, it + minutes / 60.0) }
                .filter { it.awake >= it.total * AWAKE_WINDOW }
                .toList()

        const val AWAKE_WINDOW = 0.9
    }

    private fun active(cat: Int): Double =
        days.map { it.stats(cat, FROM, TO) }.let { s -> s.sumOf { it.activeShare } / s.size }

    @Test
    fun awakeCatsMoveAboutDuringTheDay() {
        // before: 0.03 and 0.02; after: about 0.32 and 0.21
        assertTrue("Ted ${active(TED)}", active(TED) >= 0.25)
        assertTrue("Lily ${active(LILY)}", active(LILY) >= 0.15)
    }

    @Test
    fun theKittenMovesMoreThanTheCalmGrownCat() {
        assertTrue(active(TED) > active(LILY) * 1.2)
    }

    @Test
    fun catsVisitManyPlacesInTenMinutes() {
        // before: 2.5 and 1.8 places per awake 10 minutes; after: 12.3 and 11.0
        for (cat in listOf(TED, LILY)) {
            val w = days.flatMap { awakeWindows(it, cat, 10) }
            val mean = w.sumOf { it.places.size } / w.size.toDouble()
            assertTrue("cat $cat: $mean places in 10 minutes over ${w.size} windows", mean >= 8.0)
        }
    }

    @Test
    fun noPostureIsHeldForLongWhileAwake() {
        // before: whole 20 minute windows (1200 s) lying or sitting; after: at most 187 s
        for (cat in listOf(TED, LILY)) {
            for (t in days) for (w in awakeWindows(t, cat, 20)) {
                assertTrue("cat $cat still ${w.longestStillS} s", w.longestStillS <= 300.0)
            }
        }
    }

    @Test
    fun theWholeHouseIsVisited() {
        // every place of the table that cats choose (not hidden behind furniture) over two days
        val seen = HashSet<String>()
        for (t in days) for (c in 0..1) seen += t.stats(c, FROM, TO).places
        val wanted =
            Spots.all
                .filter { it.has(Spots.VISIT) && !it.hidden && !it.has(Spots.HIDDEN) }
                .map { it.id }
                .toSet()
        val missed = wanted - seen
        assertTrue("never visited: $missed of $wanted", missed.size <= wanted.size / 4)
    }

    @Test
    fun kittensPlayAndRunAndTheCatsKeepEachOtherCompany() {
        val ted = days.sumOf { it.seconds(TED, "zoomies") + it.seconds(TED, "solo play") }
        val lily = days.sumOf { it.seconds(LILY, "zoomies") + it.seconds(LILY, "solo play") }
        assertTrue("Ted plays $ted s in two days", ted >= 1800.0)
        assertTrue("Ted $ted s against Lily $lily s", ted > lily)
        val together = days.sumOf { t -> (0..1).sumOf { t.pair[it].count { p -> p } } }
        assertTrue("moments together: $together samples", together > 0)
    }

    @Test
    fun sleepStillComesAtNightWhenTiredAndAfterMeals() {
        // night: both cats are asleep nearly all of 01:00 to 01:20
        for (c in BehaviourMeter.window(1.0, Needs(), warmMin = 4.0)) {
            assertTrue("${c.name} at night", c.share(Posture.SLEEP) >= 0.9)
        }
        // tired by day: asleep for most of the afternoon window
        for (c in BehaviourMeter.window(14.0, Needs(energy = 0.2, name = "tired"))) {
            assertTrue("${c.name} tired", c.share(Posture.SLEEP) >= 0.7)
        }
        // rested by day: awake and about
        for (c in BehaviourMeter.window(10.0, Needs(energy = 0.9, name = "rested"))) {
            assertTrue("${c.name} rested", c.share(Posture.SLEEP) <= 0.1)
        }
    }

    @Test
    fun aDayIsRepeatableToTheSample() {
        val a = BehaviourMeter.record(startHour = 9.0, hours = 0.5)
        val b = BehaviourMeter.record(startHour = 9.0, hours = 0.5)
        for (c in 0..1) {
            assertEquals(a.size, b.size)
            assertTrue(a.x[c].contentEquals(b.x[c]))
            assertTrue(a.posture[c].contentEquals(b.posture[c]))
        }
    }
}
