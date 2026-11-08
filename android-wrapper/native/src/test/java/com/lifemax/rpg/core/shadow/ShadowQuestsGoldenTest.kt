package com.lifemax.rpg.core.shadow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP6c's evidence on the recorded Daily Hand and quest board (ECONOMY_SHADOW_PLAN WP6c): every case
 * of `quests-golden.json` and `quests-board-golden.json` whose calls are entry points goes through
 * the real transport (the fake page's base and envelopes, the strict decoder, the mirror) and the
 * comparison, as one page session, under the recorders' own setup ([HandGoldenActions.SETUP]).
 * Every one must be compared and come out the same as the page, the page's throws included (native
 * stops where the page does). None is unexplained, none is allowed, none is skipped.
 */
class ShadowQuestsGoldenTest {
    @Test
    fun everyInScopeHandAndBoardCaseIsComparedAndMatches() {
        val built = HandGoldenActions.build()
        val l =
            replay(built, EntryMutation.NONE, QuestNative.PACKAGED) { problems ->
                assertEquals(problems.joinToString("\n"), emptyList<String>(), problems)
            }
        assertEquals(built.size, l.compared)
        assertEquals(built.size, l.same)
        assertEquals(0, l.allowed)
        assertEquals(0, l.different)
        assertTrue(l.refused.isEmpty())
        println(
            "hand and board comparison: ${HandGoldenActions.FILES.joinToString(", ") {
                "$it ${HandGoldenActions.inScope(it).size} cases"
            }}, ${built.size} envelopes (${built.count { it.threw }} the page threw out of): " +
                "${l.same} same, ${l.allowed} allowed, ${l.different} unexplained; by kind " +
                l.kinds.entries.joinToString(" · ") { (k, c) -> "$k ${c.same}/${c.compared}" }
        )
    }

    @Test
    fun theCountOfComparedCasesIsPinned() {
        // a new golden case raises these on purpose; a lost one fails here before it silently stops
        // being compared
        val hand = HandGoldenActions.inScope(HandGoldenActions.FILES[0])
        val board = HandGoldenActions.inScope(HandGoldenActions.FILES[1])
        val built = HandGoldenActions.build()
        assertEquals(EXPECTED_HAND_CASES, hand.size)
        assertEquals(EXPECTED_BOARD_CASES, board.size)
        assertEquals(EXPECTED_ENVELOPES, built.size)
        assertEquals(EXPECTED_THREW, built.count { it.threw })
        val kinds = built.groupingBy { it.action.kind }.eachCount()
        assertEquals(EXPECTED_BY_KIND, kinds)
        // every case of the two files is either in scope or calls no entry point at all
        for (file in HandGoldenActions.FILES) {
            val all = HandGoldenActions.cases(file)
            val outside =
                all.filter { case -> case !in HandGoldenActions.inScope(file) }
                    .filter { case ->
                        HandGoldenReplayCalls.fns(case).any { it in HandGoldenActions.ENTRIES }
                    }
            assertEquals("$file: entry cases left out", emptyList<Any>(), outside)
        }
    }

    companion object {
        const val EXPECTED_HAND_CASES = 559
        const val EXPECTED_BOARD_CASES = 380
        const val EXPECTED_ENVELOPES = 2184
        const val EXPECTED_THREW = 67
        val EXPECTED_BY_KIND =
            mapOf(
                "hand.ensure" to 686,
                "hand.reroll" to 851,
                "quest.pack" to 442,
                "quest.create" to 205,
            )

        /**
         * Sends [built] through one session; [check] gets the problems (a skip, a case not
         * compared, a difference). Answers the ledger.
         */
        internal fun replay(
            built: List<HandGoldenActions.Built>,
            mutation: EntryMutation,
            quests: QuestNative,
            check: (List<String>) -> Unit,
        ): ShadowLedger {
            GoldenActions.CONTENT.setup = HandGoldenActions.SETUP
            val session = ShadowGoldenCompareTest.session(mutation, quests)
            val page = FakeShadowPage()
            for (part in page.base(built.first().action.before)) assertTrue(
                session.base(part, 0).ok
            )
            val problems = ArrayList<String>()
            val ledger = session.ledger
            for (b in built) {
                val skippedBefore = ledger.kinds.values.sumOf { it.skipped.values.sum() }
                val compared = ledger.compared
                val different = ledger.different
                val text = page.send(b.action)
                assertTrue("${b.caseId} accepted", session.accept(text.length, false).accepted)
                session.observe(text, FakeShadowPage.T0)
                val skipped = ledger.kinds.values.sumOf { it.skipped.values.sum() } - skippedBefore
                when {
                    skipped > 0 ->
                        problems +=
                            "${b.caseId} (${b.action.kind}) skipped: ${ledger.kind(b.action.kind).skipped}"
                    ledger.compared != compared + 1 -> problems += "${b.caseId} not compared"
                    ledger.different != different ->
                        problems +=
                            "${b.caseId} (${b.action.kind}) differs: ${ledger.mismatches().last().paths}"
                }
            }
            check(problems)
            return ledger
        }
    }
}

/** The function names of a recorded case's calls. */
internal object HandGoldenReplayCalls {
    fun fns(case: com.lifemax.rpg.core.save.JsonObject): List<String> =
        ((case["input"] as com.lifemax.rpg.core.save.JsonObject)["calls"]
                as com.lifemax.rpg.core.save.JsonArray)
            .items
            .map {
                ((it as com.lifemax.rpg.core.save.JsonObject)["fn"]
                        as com.lifemax.rpg.core.save.JsonString)
                    .value
            }
}
