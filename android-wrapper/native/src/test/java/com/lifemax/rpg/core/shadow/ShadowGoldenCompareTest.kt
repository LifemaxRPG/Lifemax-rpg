package com.lifemax.rpg.core.shadow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP6b's and WP6c's evidence on the recorded page (ECONOMY_SHADOW_PLAN WP6b): every economy golden
 * case whose calls are entry points of the kinds compared here goes through the real transport (the
 * fake page's base and envelopes, the strict decoder, the mirror) and the comparison, as one page
 * session, and every one of them must be compared and come out matched: the same as the page, or
 * different only where an allowlist entry says the page's state is outside the economy. None is
 * unexplained and none is skipped.
 */
class ShadowGoldenCompareTest {
    @Test
    fun everyInScopeGoldenCaseIsComparedAndMatches() {
        val built = GoldenActions.build()
        val session = session()
        val page = FakeShadowPage()
        for (part in page.base(built.first().action.before)) assertTrue(session.base(part, 0).ok)
        val problems = ArrayList<String>()
        for (b in built) {
            val ledger = session.ledger
            val before =
                Triple(
                    ledger.compared,
                    ledger.different,
                    ledger.kinds.values.sumOf { it.skipped.values.sum() },
                )
            val text = page.send(b.action)
            GoldenActions.CONTENT.setup = b.setup
            assertTrue("${b.caseId} accepted", session.accept(text.length, false).accepted)
            session.observe(text, FakeShadowPage.T0)
            val skipped = ledger.kinds.values.sumOf { it.skipped.values.sum() } - before.third
            when {
                skipped > 0 ->
                    problems +=
                        "${b.caseId} (${b.action.kind}) skipped: ${ledger.kind(b.action.kind).skipped}"
                ledger.compared != before.first + 1 -> problems += "${b.caseId} not compared"
                ledger.different != before.second ->
                    problems +=
                        "${b.caseId} (${b.action.kind}) differs: ${ledger.mismatches().last().paths}"
            }
        }
        assertEquals(problems.joinToString("\n"), emptyList<String>(), problems)
        val l = session.ledger
        assertEquals(built.size, l.compared)
        assertEquals(0, l.different)
        assertEquals(built.size, l.same + l.allowed)
        assertTrue(l.refused.isEmpty())
        println(
            "golden comparison: ${GoldenActions.inScope().size} cases, ${built.size} envelopes " +
                "(${built.count { it.recorded }} with the page's own after-state): ${l.same} same, " +
                "${l.allowed} allowed, ${l.different} unexplained; by kind " +
                l.kinds.entries.joinToString(" · ") { (k, c) ->
                    "$k ${c.same + c.allowed}/${c.compared}"
                } +
                "; allowlist " +
                l.mismatches().joinToString(" ") { "${it.allow}x${it.count}" }
        )
    }

    @Test
    fun theCountOfComparedCasesIsPinned() {
        // the golden cases that are entry points of WP6b's kinds; a new golden case of one of them
        // raises this on purpose, a lost one fails here before it silently stops being compared
        val cases = GoldenActions.inScope()
        val envelopes = GoldenActions.build()
        assertEquals(EXPECTED_CASES, cases.size)
        assertEquals(EXPECTED_ENVELOPES, envelopes.size)
        assertEquals(cases.size, envelopes.count { it.recorded })
        val kinds = envelopes.map { it.action.kind }.toSet()
        assertEquals(ShadowEntries.COMPARED - UNRECORDED_KINDS, kinds)
    }

    companion object {
        const val EXPECTED_CASES = 354
        const val EXPECTED_ENVELOPES = 406

        /**
         * Compared kinds no economy golden case calls as an entry point: the class pack is the
         * board golden's ([ShadowQuestsGoldenTest]) and the real-page corpus's; the boot's decay is
         * the day corpus's ([ShadowDayCorpusTest]) and [ShadowDayGoldenTest]'s.
         */
        val UNRECORDED_KINDS = setOf("quest.pack", "boot.decay")

        fun session(
            mutation: EntryMutation = EntryMutation.NONE,
            quests: QuestNative = QuestNative.PACKAGED,
        ) =
            ShadowSession(
                    ShadowSessionTest.FP,
                    ShadowLedger(
                        ShadowSessionTest.T,
                        ByteArray(ShadowLimits.SALT_BYTES) { it.toByte() },
                    ),
                    1,
                    109,
                    GoldenActions.CONTENT,
                    mutation = mutation,
                    quests = quests,
                )
                .also { it.configure(ShadowSessionTest.configure(ShadowSessionTest.FP), false) }
    }
}
