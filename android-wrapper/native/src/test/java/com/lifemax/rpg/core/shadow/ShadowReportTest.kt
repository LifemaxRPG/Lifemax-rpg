package com.lifemax.rpg.core.shadow

import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The copied report (ECONOMY_SHADOW_PLAN 3.4, WP6e): what each line says, from a ledger whose every
 * count is known; its bounds (the latest twenty differences, newest first, at most
 * [ShadowLimits.REPORT_MAX_CHARS]); and that it is built only from redacted parts (the owner text
 * checks with real saves are [ShadowRedactionTest] and the ring test's).
 */
class ShadowReportTest {
    private val t = ShadowSessionTest.T

    private fun session(): ShadowSession =
        ShadowSessionTest.session().also {
            it.configure(
                ShadowSessionTest.configure(ShadowSessionTest.FP).replace("\"b1\"", "\"b7c19e0\""),
                false,
            )
        }

    private fun differed(
        paths: List<String>,
        allowed: Set<String> = emptySet(),
        page: EconomyChange? = EconomyChange(15.0, 15.0, -7.0),
        native: EconomyChange? = EconomyChange(15.0, 15.0, 0.0),
    ): Verdict.Differed {
        val found = paths.map { Difference(it, it) }
        return Verdict.Differed(
            found,
            if (allowed.isEmpty()) found else emptyList(),
            allowed,
            page,
            native,
        )
    }

    private fun report(s: ShadowSession) = s.report(t + 12 * DAY, ZoneOffset.UTC)

    @Test
    fun eachLineSaysWhatTheLedgerHolds() {
        val s = session()
        val l = s.ledger
        repeat(3) {
            l.kind("log.record").apply {
                seen++
                applied++
            }
            l.compared("log.record", Verdict.Same, "b7c19e0", t)
        }
        l.kind("quest.toggle").apply {
            seen += 2
            applied += 2
        }
        l.compared(
            "quest.toggle",
            differed(listOf("restedXP", "activities[+1].xp")),
            "b7c19e0",
            t + HOUR,
        )
        l.compared(
            "quest.toggle",
            differed(listOf("journalCoach.days.*#3f09aa"), setOf("A-01")),
            "b7c19e0",
            t + HOUR,
        )
        l.kind("health.sync").apply {
            seen++
            applied++
        }
        l.compared(
            "health.sync",
            differed(listOf("reads.steps", "reads.steps.range"), page = null, native = null),
            "b7c19e0",
            t + 2 * HOUR,
        )
        l.skip("hand.ensure", "interleaved")
        l.skip(ShadowSession.ANY, "battery")
        l.note("d13Kept.log.record")
        l.note("d13Kept.log.record")
        l.note("zoneLag")
        l.note("clockJump")
        l.codec["strict"] = 1
        l.refuse(ShadowRefusal.TOO_LARGE)
        l.error("IllegalStateException", t)
        l.disabled("slow", t + 3 * HOUR)
        s.timed(40, t)
        val r = report(s)
        val lines = r.lines()
        assertTrue(
            r,
            lines[0].startsWith(
                "LifeMax economy check v1 · app 1 (api 106) · page b7c19e0 · since 2026-11-03 (12 days)"
            ),
        )
        assertEquals("state on · written 2026-11-15 10:00", lines[1])
        assertEquals("content app fp-test · page fp-test (same)", lines[2])
        assertTrue(
            r,
            "actions 6 · compared 6 · same 3 · allowed 1 · different 2 · skipped 2 (battery 1 · interleaved 1)" in
                r,
        )
        assertTrue(r, "  log.record 3/3/0/0\n" in r)
        assertTrue(r, "  quest.toggle 2/0/1/1\n" in r)
        assertTrue(r, "  health.sync 1/0/0/1\n" in r)
        assertTrue(r, "  hand.ensure 0/0/0/0 · skipped interleaved 1\n" in r)
        assertTrue(r, "info: clockJump 1 · d13Kept 2 · zoneLag 1 (log.record 2)" in r)
        assertTrue(r, "reads: reads.steps (health.sync) 1 · reads.steps.range (health.sync) 1" in r)
        assertTrue(r, "codec: encode 0 · nonFinite 0 · strict 1" in r)
        assertTrue(r, "refused: tooLarge 1" in r)
        assertTrue(r, "errors: native 1 · page 0 (IllegalStateException 1)" in r)
        assertTrue(r, "cost: page not measured yet · native p50 40 ms p95 40 ms (last 1)" in r)
        assertTrue(r, "stopped itself: 2026-11-03 13:00 slow" in r)
        assertTrue(r, "differences: 3 kept (* = not explained by the allowlist)" in r)
        // newest first: the health sync's, then the allowed one, then the unexplained toggle
        val m3 = r.indexOf("m-3 x1 health.sync* ")
        val m2 = r.indexOf("m-2 x1 quest.toggle ")
        val m1 = r.indexOf("m-1 x1 quest.toggle* ")
        assertTrue(r, m3 in 0 until m2 && m2 < m1)
        assertTrue(r, "     paths activities[+1].xp; restedXP\n" in r)
        assertTrue(r, "     allowlist A-01\n" in r)
        assertTrue(
            r,
            "     page xp +15 sparks +15 rested -7 · native xp +15 sparks +15 rested 0\n" in r,
        )
        assertTrue(r, "     page not run · native not run\n" in r)
        assertTrue(r, "2026-11-03 11:00 -> 2026-11-03 11:00 (page b7c19e0)" in r)
    }

    @Test
    fun anEmptyCheckSaysSoAndAlwaysNamesTheThreeInfoCounts() {
        val r = report(session())
        assertTrue(r, "  none yet" in r)
        assertTrue(r, "info: clockJump 0 · d13Kept 0 · zoneLag 0" in r)
        assertTrue(r, "reads: no difference" in r)
        assertTrue(r, "stopped itself: never" in r)
        assertTrue(r, "differences: none" in r)
        assertTrue(r, r.length < 1_000)
    }

    @Test
    fun aPageUpdatedWithoutTheAppIsNamed() {
        val s = ShadowSessionTest.session()
        s.configure(ShadowSessionTest.configure("ota-page-fingerprint"), false)
        val r = report(s)
        assertTrue(r, "state stale (fingerprint)" in r)
        assertTrue(
            r,
            "content app fp-test · page ota-page-fin (different: the page was updated without the app)" in
                r,
        )
    }

    @Test
    fun onlyTheLatestTwentyDifferencesAreListedAndTheReportStaysShort() {
        val s = session()
        repeat(ShadowLimits.RING_MAX_ENTRIES) { i ->
            s.ledger.compared(
                "kind.k${i % 60}",
                differed((0 until ShadowLimits.MAX_PATHS_PER_ENTRY).map { "attrXP.path$i.$it" }),
                "b7c19e0",
                t + i * MINUTE,
            )
        }
        val r = report(s)
        assertTrue(r.length <= ShadowLimits.REPORT_MAX_CHARS)
        assertTrue(r, "differences: the latest" in r)
        val listed =
            Regex("""^m-(\d+) """, RegexOption.MULTILINE)
                .findAll(r)
                .map { it.groupValues[1].toInt() }
                .toList()
        assertTrue(listed.size in 1..ShadowLimits.REPORT_DIFFERENCES)
        assertEquals((200 downTo 1).take(listed.size), listed)
        assertFalse("m-180 " in r)
    }

    @Test
    fun aPageBuildIsShownOnlyAsALabel() {
        val s = ShadowSessionTest.session()
        s.configure(
            ShadowSessionTest.configure(ShadowSessionTest.FP)
                .replace("\"b1\"", "\"b1 <script>Ann's\""),
            false,
        )
        val r = report(s)
        assertTrue(r, "page b1scriptAnns · since" in r)
    }

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR
    }
}
