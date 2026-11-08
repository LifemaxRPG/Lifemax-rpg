package com.lifemax.rpg.core.shadow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ring's difference entries (ECONOMY_SHADOW_PLAN 3.1): coalesced by kind, paths and allowlist
 * ids; at most 200, the ring at most 128 KB with unexplained entries kept longest; the counts and
 * entries written and read back as they were (and a WP6a ring, without them, still reads).
 */
class ShadowMismatchRingTest {
    private fun ledger() =
        ShadowLedger(ShadowSessionTest.T, ByteArray(ShadowLimits.SALT_BYTES) { it.toByte() })

    private fun differed(paths: List<String>, allowed: Set<String> = emptySet()) =
        Verdict.Differed(
            paths.map { Difference(it, it) },
            if (allowed.isEmpty()) paths.map { Difference(it, it) } else emptyList(),
            allowed,
            EconomyChange(15.0, 15.0, -7.0),
            EconomyChange(15.0, 15.0, 0.0),
        )

    @Test
    fun aRepeatRaisesTheCountAndTheRingStaysBounded() {
        val l = ledger()
        repeat(3) {
            l.compared(
                "quest.toggle",
                differed(listOf("restedXP")),
                "b1",
                ShadowSessionTest.T + it * 60_000L,
            )
        }
        assertEquals(1, l.mismatches().size)
        assertEquals(3, l.mismatches().single().count)
        assertEquals(3, l.different)
        l.compared("log.record", Verdict.Same, "b1", ShadowSessionTest.T)
        l.compared(
            "log.record",
            differed(listOf("journalCoach.days.*#abcdef"), setOf("A-01")),
            "b1",
            ShadowSessionTest.T,
        )
        assertEquals(1, l.allowed)
        assertEquals(1, l.same)
        repeat(400) {
            l.compared(
                "log.record",
                differed(listOf("skillXP.*#%06x".format(it), "activities[+1].notes")),
                "b1",
                ShadowSessionTest.T,
            )
        }
        assertEquals(ShadowLimits.RING_MAX_ENTRIES, l.mismatches().size)
        repeat(3000) {
            l.compared(
                "save.other",
                differed(List(12) { i -> "attrXP.*#%06x".format(it * 12 + i) }),
                "b1",
                ShadowSessionTest.T,
            )
        }
        val text = l.fit()
        assertTrue(text.toByteArray(Charsets.UTF_8).size <= ShadowLimits.RING_MAX_BYTES)
        assertTrue(l.mismatches().all { it.unexplained })
    }

    @Test
    fun theCountsAndEntriesReadBackAsWritten() {
        val l = ledger()
        l.compared("log.record", differed(listOf("sparks", "toasts[0]")), "b1", ShadowSessionTest.T)
        l.compared(
            "money.record",
            differed(listOf("money"), setOf("A-03")),
            "b1",
            ShadowSessionTest.T,
        )
        l.compared("rest.take", Verdict.Skipped("planWindow"), "b1", ShadowSessionTest.T)
        l.codec["strict"] = 2
        val text = ShadowLedgerJson.write(l)
        val back = ShadowLedgerJson.read(text)
        assertNotNull(back)
        back!!
        assertEquals(text, ShadowLedgerJson.write(back))
        assertEquals(1, back.kind("log.record").different)
        assertEquals(1, back.kind("money.record").allowed)
        assertEquals(mapOf("planWindow" to 1), back.kind("rest.take").skipped)
        assertEquals(listOf("A-03"), back.mismatches().last().allow)
        assertEquals(EconomyChange(15.0, 15.0, -7.0), back.mismatches().first().page)
    }

    @Test
    fun aRingWrittenBeforeTheComparisonStillReads() {
        val l = ledger()
        l.kind("log.record").seen = 4
        val wp6a =
            ShadowLedgerJson.write(l)
                .replace(Regex(",\"compared\":0,\"same\":0,\"allowed\":0,\"different\":0"), "")
                .replace(",\"codec\":{}", "")
                .replace(Regex(",\"nextMismatch\":1,\"mismatches\":\\[]"), "")
        assertTrue("compared" !in wp6a && "mismatches" !in wp6a)
        val back = ShadowLedgerJson.read(wp6a)!!
        assertEquals(4, back.kind("log.record").seen)
        assertEquals(0, back.compared)
        assertTrue(back.mismatches().isEmpty())
    }
}
