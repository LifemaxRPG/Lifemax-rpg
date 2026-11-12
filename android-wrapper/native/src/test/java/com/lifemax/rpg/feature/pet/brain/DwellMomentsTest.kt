package com.lifemax.rpg.feature.pet.brain

import com.lifemax.rpg.feature.pet.world.DwellReport
import com.lifemax.rpg.feature.pet.world.MomentReport
import com.lifemax.rpg.feature.pet.world.WorldCommand
import com.lifemax.rpg.feature.pet.world.WorldCommandJson
import com.lifemax.rpg.feature.pet.world.WorldContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Work package C1: what the brain tells the page for the album and the likes. Time spent at places
 * and with toys comes out as `dwell` reports (whole seconds, per local day, never more than the
 * time that passed, names the page knows, a day's report when the day ends), and the first zoomies
 * the owner watches come out as one `moment`, once per cat for good, carried in the saved state so
 * a restart does not report it again.
 */
class DwellMomentsTest {
    private val day = 24 * CatDays.HOUR
    private val owner = listOf(OwnerAct(0, "open"))

    private fun run(
        hours: Double,
        acts: List<OwnerAct>,
        cats: List<CatProfile> = listOf(CatDays.cat("Bean", CatDays.PLAYFUL, stage = 2)),
        setup: (CatWorld) -> Unit = {},
        start: Long = CatDays.MIDNIGHT,
    ): Pair<CatWorld, List<WorldCommand>> {
        val out = ArrayList<WorldCommand>()
        val world =
            CatDays.run(
                cats,
                acts,
                hours = hours,
                start = start,
                fidelity = CatWorld.Fidelity.FAST,
                setup = setup,
                probe = { w, _, think -> if (think) out += w.takeReports() },
            )
        world.flush()
        out += world.takeReports()
        return world to out
    }

    @Test
    fun dwellIsReportedPerDayInWholeSecondsWithinTheTimeThatPassed() {
        val (_, reports) = run(hours = 30.0, acts = emptyList())
        val dwell = reports.filterIsInstance<DwellReport>()
        assertTrue("some dwell", dwell.isNotEmpty())
        val days = dwell.map { it.day }.toSet()
        assertEquals(setOf("2026-10-30", "2026-10-31"), days)
        for (d in dwell) {
            assertTrue(d.spots.keys.all { it in WorldContract.SPOTS })
            assertTrue(d.toys.keys.all { it in WorldContract.TOYS })
            assertTrue(d.spots.values.all { it in 1..DwellReport.DAY_SECONDS })
            // a report is checked as the page checks it
            WorldCommandJson.decode("dwell", WorldCommandJson.encode(d))
        }
        for (key in days) {
            val spent = dwell.filter { it.day == key }.sumOf { it.spots.values.sum() }
            val limit = if (key == "2026-10-30") 24 * 3600 else 6 * 3600
            assertTrue("$key spent $spent of at most $limit s", spent in 1..limit)
        }
        assertEquals("unique ops", dwell.size, dwell.map { it.op }.toSet().size)
    }

    @Test
    fun aDayEndingIsReportedAtOnceAndNothingIsLostWhenTheViewCloses() {
        val (world, reports) = run(hours = 25.0, acts = emptyList())
        // the first day was handed over when the second began, not held back for an hour
        val first = reports.filterIsInstance<DwellReport>().filter { it.day == "2026-10-30" }
        assertTrue(first.isNotEmpty())
        // flush leaves nothing behind
        world.flush()
        assertTrue(world.takeReports().filterIsInstance<DwellReport>().isEmpty())
    }

    @Test
    fun theFirstZoomiesTheOwnerWatchesAreReportedOnceForGood() {
        val (world, reports) = run(hours = 48.0, acts = owner)
        val zoom = reports.filterIsInstance<MomentReport>().filter { it.kind == "first_zoomies" }
        assertEquals("once", 1, zoom.size)
        assertEquals("cbean", zoom[0].cat)
        assertEquals("cbean:first_zoomies", zoom[0].op)
        WorldCommandJson.decode("moment", WorldCommandJson.encode(zoom[0]))
        // it is kept with the saved cat
        val saved = world.export()
        assertTrue("first_zoomies" in saved.cats.getValue("cbean").fx)
        // a restart restores it and does not tell the page again
        val (_, again) =
            run(
                hours = 48.0,
                acts = owner,
                setup = { it.restore(saved) },
                start = CatDays.MIDNIGHT + 2 * day,
            )
        assertTrue(again.filterIsInstance<MomentReport>().none { it.kind == "first_zoomies" })
    }

    @Test
    fun zoomiesNobodyWatchesAreNotAFirst() {
        val (world, reports) = run(hours = 48.0, acts = emptyList())
        assertTrue(reports.filterIsInstance<MomentReport>().isEmpty())
        assertTrue(world.export().cats.getValue("cbean").fx.isEmpty())
    }
}
