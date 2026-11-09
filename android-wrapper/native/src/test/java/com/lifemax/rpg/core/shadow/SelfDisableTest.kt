package com.lifemax.rpg.core.shadow

import com.lifemax.rpg.app.shadow.ShadowRing
import com.lifemax.rpg.app.shadow.ShadowWorker
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import java.io.File
import java.nio.file.Files
import java.time.ZoneOffset
import java.util.concurrent.Executor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The check's self-disable rules and its pause (ECONOMY_SHADOW_PLAN section 4, budgets and
 * self-disable; decision O-5), each at its threshold and just under it: five internal errors within
 * 24 hours, more than four resynchronisations within an hour, a worker whose 95th percentile over
 * 50 actions is past 2 s, a save over 4 MB, and Battery Saver (a pause, counted, not a disable). A
 * stop is kept in the ring with its reason and in the history the report lists; the page is told at
 * every handshake and configure; the owner's off and on, or a new app version, try again.
 */
class SelfDisableTest {
    private val dir: File = Files.createTempDirectory("selfdisable").toFile()
    private val t = ShadowSessionTest.T
    private val start: Map<String, JsonValue> = mapOf("sparks" to JsonNumber(10.0))

    @After
    fun removeTheFolder() {
        dir.deleteRecursively()
    }

    private fun on(): Pair<ShadowSession, FakeShadowPage> {
        val s = ShadowSessionTest.session()
        assertEquals(
            ShadowState.ON,
            s.configure(ShadowSessionTest.configure(ShadowSessionTest.FP), false).state,
        )
        val page = FakeShadowPage()
        page.base(start).forEach { assertTrue(s.base(it, t).ok) }
        return s to page
    }

    /** The state the page is told: in the handshake, a configure and an envelope's quick answer. */
    private fun told(s: ShadowSession): List<String?> =
        listOf(
            s.handshake().state.word,
            s.configure(ShadowSessionTest.configure(ShadowSessionTest.FP), false).state.word,
            s.accept(10, false).reason,
        )

    private fun stoppedFor(s: ShadowSession, reason: String) {
        assertEquals(ShadowState.DISABLED, s.ledger.state)
        assertEquals(reason, s.ledger.reason)
        assertEquals(reason, s.ledger.disables().last().reason)
        assertEquals(listOf("disabled", "disabled", "disabled"), told(s))
        val report = s.report(t + HOUR, ZoneOffset.UTC)
        assertTrue(report, "state disabled ($reason)" in report)
        assertTrue(report, Regex("stopped itself: .*$reason").containsMatchIn(report))
    }

    @Test
    fun fiveInternalErrorsWithinADayStopTheCheck() {
        val (s, _) = on()
        repeat(ShadowLimits.ERRORS_BEFORE_DISABLE - 1) {
            s.fault(IllegalStateException(), t + it * HOUR)
        }
        assertEquals(ShadowState.ON, s.ledger.state)
        assertTrue(s.ledger.disables().isEmpty())
        s.fault(IllegalStateException(), t + 23 * HOUR)
        stoppedFor(s, "errors")
        assertEquals(5, s.ledger.errors["IllegalStateException"])
    }

    @Test
    fun errorsFurtherApartThanADayNeverStopIt() {
        val (s, _) = on()
        repeat(12) {
            s.fault(IllegalStateException(), t + it * (ShadowLimits.ERROR_WINDOW_MS / 4 + 1))
        }
        assertEquals(ShadowState.ON, s.ledger.state)
        assertEquals(12, s.ledger.nativeFaults)
    }

    @Test
    fun moreThanFourResyncsWithinAnHourStopTheCheck() {
        val s = ShadowSessionTest.session()
        s.configure(ShadowSessionTest.configure(ShadowSessionTest.FP), false)
        val page = FakeShadowPage()
        // the session's first base is not a resync; four more within the hour are allowed
        repeat(1 + ShadowLimits.RESYNCS_PER_HOUR) { i ->
            page.base(start).forEach { s.base(it, t + i * MINUTE) }
        }
        assertEquals(ShadowState.ON, s.ledger.state)
        page.base(start).forEach { s.base(it, t + 10 * MINUTE) }
        stoppedFor(s, "resyncLoop")
    }

    @Test
    fun theResyncWindowIsExactlyAnHour() {
        for ((late, stops) in listOf(HOUR to false, HOUR - 1 to true)) {
            val s = ShadowSessionTest.session()
            s.configure(ShadowSessionTest.configure(ShadowSessionTest.FP), false)
            val page = FakeShadowPage()
            // the first base, then four resyncs a minute apart: the most an hour allows
            repeat(1 + ShadowLimits.RESYNCS_PER_HOUR) { i ->
                page.base(start).forEach { s.base(it, t + i * MINUTE) }
            }
            // a fifth resync exactly an hour after the first has left that one behind
            page.base(start).forEach { s.base(it, t + MINUTE + late) }
            if (stops) stoppedFor(s, "resyncLoop")
            else assertEquals("$late", ShadowState.ON, s.ledger.state)
        }
    }

    @Test
    fun theSlowRuleReadsThe95thPercentile() {
        val (s, _) = on()
        // 3 slow of 50 are above the 95th percentile: still on
        repeat(ShadowLimits.SLOW_WINDOW - 3) { s.timed(40) }
        repeat(3) { s.timed(SLOW_MS) }
        assertEquals(ShadowState.ON, s.ledger.state)
        // a fourth reaches it
        s.timed(SLOW_MS)
        stoppedFor(s, "slow")
    }

    @Test
    fun resyncsSpreadOverHoursNeverStopIt() {
        val s = ShadowSessionTest.session()
        s.configure(ShadowSessionTest.configure(ShadowSessionTest.FP), false)
        val page = FakeShadowPage()
        repeat(20) { i -> page.base(start).forEach { s.base(it, t + i * 16 * MINUTE) } }
        assertEquals(ShadowState.ON, s.ledger.state)
    }

    @Test
    fun aSlowWorkerStopsTheCheckThroughTheRealWorker() {
        var clock = t
        val held = Held()
        val ring = ShadowRing(dir)
        val w =
            ShadowWorker(
                held,
                ring,
                {
                    clock += SLOW_MS / 2
                    clock
                },
                { false },
            ) { r, now ->
                ShadowSession(ShadowSessionTest.FP, r.load() ?: r.fresh(now), 1, 112)
            }
        w.configure(ShadowSessionTest.configure(ShadowSessionTest.FP)) { _, _ -> }
        val page = FakeShadowPage()
        page.base(start).forEach { w.base(it) {} }
        held.drain()
        // every envelope takes SLOW_MS on the worker's clock: judged only once 50 are timed
        var state = ""
        for (i in 1..ShadowLimits.SLOW_WINDOW) {
            assertTrue("envelope $i", w.observe(page.envelope("probe.idle", start, start)).accepted)
            held.drain()
            w.summary { state = it.state.word }
            held.drain()
            if (i < ShadowLimits.SLOW_WINDOW) assertEquals("on at $i", "on", state)
        }
        assertEquals("disabled", state)
        w.flush() // the app stopping writes the ring at once
        held.drain()
        val stored = ring.load()!!
        assertEquals(ShadowState.DISABLED, stored.state)
        assertEquals("slow", stored.reason)
        assertEquals("slow", stored.disables().single().reason)
        assertEquals("disabled", w.observe(page.envelope("probe.idle", start, start)).reason)
    }

    @Test
    fun aQuickWorkerWithAFewSlowActionsKeepsGoing() {
        val (s, _) = on()
        repeat(ShadowLimits.SLOW_WINDOW * 3) { s.timed(if (it % 25 == 0) 5_000 else 40) }
        assertEquals(ShadowState.ON, s.ledger.state)
    }

    @Test
    fun aSaveOverFourMegabytesStopsTheCheckAndIsNotHeld() {
        val s = ShadowSessionTest.session()
        s.configure(ShadowSessionTest.configure(ShadowSessionTest.FP), false)
        val big = mapOf("dayReflections" to JsonString("x".repeat(ShadowLimits.MAX_HELD_CHARS)))
        FakeShadowPage().base(big).forEach { s.base(it, t) }
        stoppedFor(s, "tooLarge")
        assertTrue(s.mirrorSnapshot().isEmpty())
    }

    @Test
    fun aSaveJustUnderFourMegabytesIsHeld() {
        val s = ShadowSessionTest.session()
        s.configure(ShadowSessionTest.configure(ShadowSessionTest.FP), false)
        val under =
            mapOf("dayReflections" to JsonString("x".repeat(ShadowLimits.MAX_HELD_CHARS - 1_000)))
        val answers = FakeShadowPage().base(under).map { s.base(it, t) }
        assertTrue(answers.all { it.ok })
        assertEquals(ShadowState.ON, s.ledger.state)
        assertFalse(s.mirrorSnapshot().isEmpty())
    }

    @Test
    fun batterySaverPausesWithoutStoppingAndCountsWhatItDropped() {
        val held = Held()
        var battery = false
        val w =
            ShadowWorker(held, ShadowRing(dir), { t }, { battery }) { r, now ->
                ShadowSession(ShadowSessionTest.FP, r.load() ?: r.fresh(now), 1, 112)
            }
        w.configure(ShadowSessionTest.configure(ShadowSessionTest.FP)) { _, _ -> }
        val page = FakeShadowPage()
        page.base(start).forEach { w.base(it) {} }
        held.drain()
        battery = true
        val a = w.observe(page.envelope("probe.idle", start, start))
        assertEquals(ShadowAccept(false, true, "paused"), a)
        held.drain()
        var summary: ShadowSummary? = null
        w.summary { summary = it }
        held.drain()
        assertEquals(ShadowState.PAUSED, summary!!.state)
        assertEquals("battery", summary!!.reason)
        assertEquals(1, summary!!.skipped)
        // a configure while Battery Saver is still on stays paused; off again, the check goes on
        var state = ""
        w.configure(ShadowSessionTest.configure(ShadowSessionTest.FP)) { s, _ ->
            state = s.state.word
        }
        held.drain()
        assertEquals("paused", state)
        battery = false
        w.configure(ShadowSessionTest.configure(ShadowSessionTest.FP)) { s, _ ->
            state = s.state.word
        }
        held.drain()
        assertEquals("on", state)
        var report = ""
        w.report({ ZoneOffset.UTC }) { report = it }
        held.drain()
        assertTrue(report, "stopped itself: never" in report)
        assertTrue(report, "battery 1" in report)
    }

    @Test
    fun theOwnersOffAndOnOrANewAppVersionTryAgainAndTheHistoryStays() {
        val (s, _) = on()
        repeat(ShadowLimits.ERRORS_BEFORE_DISABLE) { s.fault(RuntimeException(), t) }
        assertEquals(
            ShadowState.DISABLED,
            s.configure(ShadowSessionTest.configure(ShadowSessionTest.FP), false).state,
        )
        s.configure(ShadowSessionTest.configure(ShadowSessionTest.FP, enabled = false), false)
        assertEquals(
            ShadowState.ON,
            s.configure(ShadowSessionTest.configure(ShadowSessionTest.FP), false).state,
        )
        assertNull(s.ledger.reason)
        assertEquals(1, s.ledger.disables().size)
        // more errors after a stop add no second entry; a stop after a retry does
        repeat(ShadowLimits.ERRORS_BEFORE_DISABLE + 3) { s.fault(RuntimeException(), t + MINUTE) }
        assertEquals(2, s.ledger.disables().size)
        val next = ShadowSession(ShadowSessionTest.FP, s.ledger, versionCode = 2, nativeApi = 112)
        assertEquals(ShadowState.OFF, next.ledger.state)
        assertEquals(2, next.ledger.disables().size)
    }

    @Test
    fun theHistoryIsBoundedAndSurvivesTheRing() {
        val (s, _) = on()
        repeat(ShadowLimits.DISABLE_HISTORY + 3) { i ->
            repeat(ShadowLimits.ERRORS_BEFORE_DISABLE) {
                s.fault(RuntimeException(), t + i * DAY + it)
            }
            s.configure(ShadowSessionTest.configure(ShadowSessionTest.FP, enabled = false), false)
            s.configure(ShadowSessionTest.configure(ShadowSessionTest.FP), false)
        }
        assertEquals(ShadowLimits.DISABLE_HISTORY, s.ledger.disables().size)
        val reread = ShadowLedgerJson.read(s.ringText())!!
        assertEquals(s.ledger.disables(), reread.disables())
        // a ring written before WP6e (no history) still reads
        val old = s.ringText().replace(Regex(""","disables":\[[^\]]*\]"""), "")
        assertTrue(ShadowLedgerJson.read(old)!!.disables().isEmpty())
    }

    /** Runs jobs only when [drain] is called. */
    private class Held : Executor {
        val jobs = ArrayDeque<Runnable>()

        override fun execute(command: Runnable) {
            jobs.addLast(command)
        }

        fun drain() {
            while (jobs.isNotEmpty()) jobs.removeFirst().run()
        }
    }

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR
        const val SLOW_MS = 2 * ShadowLimits.SLOW_P95_MS + 2
    }
}
