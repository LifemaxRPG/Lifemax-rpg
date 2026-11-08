package com.lifemax.rpg.core.shadow

import com.lifemax.rpg.core.economy.Dealt
import com.lifemax.rpg.core.economy.EconomyGoldenTest
import com.lifemax.rpg.core.economy.HandCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `save/compose-*` golden family (ECONOMY_SHADOW_PLAN WP6c, recorded by `gen_economy_golden.js`
 * from the real page with the Daily Hand on): the order of what one action does, the page's save,
 * the Daily Hand's check in the redraw inside it (deal, then verify, each card's toggle saving),
 * then the check of the save's wrapper, and the redraw of a take-back. Each case is replayed
 * through native's composition ([ShadowRunner] with [PageHand]) and must give the recorded trace
 * (every write and every check, in order) as well as the recorded state, toasts and return values
 * (those through the transport, [ShadowGoldenCompareTest]). A composition put together wrongly is
 * caught.
 */
class SaveComposeGoldenTest {
    private val built = GoldenActions.build()

    /** The family's cases, and the one verify case recorded before it. */
    private val composed =
        EconomyGoldenTest.cases.filter {
            EconomyGoldenTest.composed(it) || (it["input"] as Map<*, *>)["verify"] == true
        }

    @Test
    fun everyComposedCaseGivesTheRecordedOrder() {
        val wrong = ArrayList<String>()
        for (case in composed) {
            val id = case["id"] as String
            val expected = economic((case["output"] as Map<*, *>)["trace"] as List<*>)
            val trace = trace(id, QuestNative.PACKAGED)
            if (trace != expected) wrong += "$id: expected $expected, got $trace"
        }
        assertEquals(wrong.joinToString("\n"), emptyList<String>(), wrong)
    }

    @Test
    fun theFamilyIsPinned() {
        val ids = composed.map { it["id"] as String }
        assertEquals(EXPECTED_CASES, ids.size)
        assertTrue(ids.all { id -> built.any { it.caseId == id && it.recorded } })
        // the family covers each place a check runs: inside a save, around it, in a refused
        // toggle's redraw, in a take-back's redraw, alone, and a deal inside a save
        val traces = composed.map { ((it["output"] as Map<*, *>)["trace"] as List<*>) }
        assertTrue(traces.any { it.firstOrNull() == "check" })
        assertTrue(traces.any { t -> t.count { it == "check" } >= 3 })
        assertTrue(traces.any { t -> t.contains("write:false") })
        // the page's own writes cross the transport for every composed case of one action
        val single = built.filter { b -> b.recorded && composed.any { it["id"] == b.caseId } }
        assertTrue(single.count { "journal" in it.action.env } >= JOURNAL_CASES)
    }

    @Test
    fun aCompositionPutTogetherWronglyIsCaught() {
        val wrong =
            mapOf(
                "no wrapper check" to skipEvery(2, 2),
                "no redraw check" to skipEvery(2, 1),
                "no deal before a check" to
                    { real: HandCheck ->
                        HandCheck { save -> Dealt(save, real.deal(save).verify) }
                    },
                "deals but never verifies" to
                    { real: HandCheck ->
                        HandCheck { save -> real.deal(save).copy(verify = false) }
                    },
                "checks once more" to
                    { real: HandCheck ->
                        var n = 0
                        HandCheck { save ->
                            if (++n == 1) real.deal(real.deal(save).save) else real.deal(save)
                        }
                    },
            )
        val missed = ArrayList<String>()
        for ((name, wrap) in wrong) {
            val quests = QuestNative(QuestNative.PACKAGED.content, check = wrap)
            val traced =
                composed.count { case ->
                    val id = case["id"] as String
                    val expected = economic((case["output"] as Map<*, *>)["trace"] as List<*>)
                    trace(id, quests) != expected
                }
            val differed =
                ShadowGoldenCompareTest.session(quests = quests).let { session ->
                    val page = FakeShadowPage()
                    val mine = built.filter { b -> composed.any { it["id"] == b.caseId } }
                    for (part in page.base(mine.first().action.before)) session.base(part, 0)
                    for (b in mine) {
                        GoldenActions.CONTENT.setup = b.setup
                        session.observe(page.send(b.action), FakeShadowPage.T0)
                    }
                    session.ledger.different
                }
            if (traced == 0 && differed == 0) missed += "$name: nothing differed"
        }
        assertEquals(missed.joinToString("\n"), emptyList<String>(), missed)
    }

    /** Native's trace for the case [id]: its envelopes' traces, in order. */
    private fun trace(id: String, quests: QuestNative): List<String> =
        built
            .filter { it.caseId == id }
            .flatMap { b ->
                GoldenActions.CONTENT.setup = b.setup
                val envelope =
                    (ShadowEnvelopeJson.envelope(FakeShadowPage().send(b.action))
                            as ShadowDecode.Ok)
                        .value
                val ran =
                    ShadowRunner(GoldenActions.CONTENT, quests = quests)
                        .run(
                            envelope,
                            FakeShadowPage.shadow(b.action.before),
                            FakeShadowPage.shadow(b.action.after),
                        )
                (ran as? RunOutcome.Ran)?.prediction?.trace ?: listOf("skipped: $ran")
            }

    /**
     * The trace without the journal coach's own plain writes (`journal:`): page state outside the
     * economy (A-06), sent apart as `env.journal`.
     */
    private fun economic(trace: List<*>): List<String> =
        trace.map { it as String }.filterNot { it.startsWith("journal:") }

    /** A check that does nothing on the [which]th of every [every] checks. */
    private fun skipEvery(every: Int, which: Int): (HandCheck) -> HandCheck = { real ->
        var n = 0
        HandCheck { save ->
            if ((n++ % every) + 1 == which) Dealt(save, false) else real.deal(save)
        }
    }

    private companion object {
        /** Thirteen `save/compose-*` cases and `save/daily-hand-pays-inside-save`. */
        const val EXPECTED_CASES = 14

        /** Composed cases whose action made the journal coach write its day memory. */
        const val JOURNAL_CASES = 5
    }
}
