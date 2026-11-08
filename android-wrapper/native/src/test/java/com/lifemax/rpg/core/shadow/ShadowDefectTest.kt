package com.lifemax.rpg.core.shadow

import com.lifemax.rpg.core.economy.EconomyGoldenTest
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ECONOMY_SHADOW_PLAN 5.1 point 3 (WP6f): every page defect reachable through a shadow point, as
 * envelopes the page recorded, reproduced by native identically. The sources are the golden cases
 * that name the defect in their rule (the page's own recorded after-state, sent through the fake
 * page's transport, one fresh session a case) and, for the defects whose golden cases call only
 * pure rules, the real page's defect corpus (`tools/gen_shadow_sim_corpus.js --defects`, each run's
 * page check proving the page showed it). Each defect must have an envelope, every one compared and
 * matched (the same, or allowed only by the allowlist ids [ALLOWED] names), never an unexplained
 * difference: native that "fixed" a defect would show as different (the kill tests,
 * [ShadowMutationTest] D05 and [ShadowDayMutationTest] D28, prove that for two). D13 is also
 * counted `info.d13Kept`, the plan's evidence for its ruling.
 */
class ShadowDefectTest {
    /** What one defect's envelopes came to. */
    data class Outcome(
        val sources: Int,
        val envelopes: Int,
        val compared: Int,
        val same: Int,
        val allowed: Set<String>,
        val unexplained: List<String>,
        val d13Kept: Int,
    )

    @Test
    fun everyReachableDefectIsReproducedThroughTheTransport() {
        val outcomes = outcomes()
        val problems = ArrayList<String>()
        for (d in DEFECTS) {
            val o = outcomes[d]
            when {
                o == null || o.envelopes == 0 -> problems += "$d: no envelope shows it"
                o.compared != o.envelopes ->
                    problems += "$d: ${o.envelopes} sent, ${o.compared} compared"
                o.unexplained.isNotEmpty() -> problems += "$d: unexplained ${o.unexplained}"
                !(ALLOWED[d].orEmpty().containsAll(o.allowed)) ->
                    problems += "$d: allowed by ${o.allowed}"
                d == "D13" && o.d13Kept == 0 -> problems += "D13: not counted info.d13Kept"
            }
        }
        assertEquals(problems.joinToString("\n"), emptyList<String>(), problems)
        println("defects through the transport (5.1 point 3), each the same as the page:")
        for (d in DEFECTS) {
            val o = outcomes.getValue(d)
            println(
                "  $d: ${o.sources} source(s), ${o.envelopes} envelopes, ${o.same} same" +
                    (if (o.allowed.isEmpty()) "" else ", allowed ${o.allowed}") +
                    (if (d == "D13") ", info.d13Kept ${o.d13Kept}" else "")
            )
        }
    }

    private fun outcomes(): Map<String, Outcome> {
        val parts = HashMap<String, MutableList<Outcome>>()
        val economy = GoldenActions.build().groupBy { it.caseId }
        for (c in EconomyGoldenTest.cases) {
            val id = c["id"] as String
            val built = economy[id] ?: continue
            for (d in defectsOf(id + " " + c["rule"])) parts.getOrPut(d) { ArrayList() } +=
                economyCase(built)
        }
        val hand = HandGoldenActions.build().groupBy { it.file + ":" + it.caseId }
        for (file in HandGoldenActions.FILES) for (c in HandGoldenActions.cases(file)) {
            val id = (c["id"] as JsonString).value
            val built = hand["$file:$id"] ?: continue
            for (d in defectsOf(id + " " + c["rule"])) parts.getOrPut(d) { ArrayList() } +=
                handCase(built)
        }
        for (run in SimReplay.runs(CORPUS, override = null)) {
            val d = (run["defect"] as JsonString).value
            parts.getOrPut(d) { ArrayList() } += corpusRun(run)
        }
        return parts.mapValues { (_, l) ->
            Outcome(
                l.size,
                l.sumOf { it.envelopes },
                l.sumOf { it.compared },
                l.sumOf { it.same },
                l.flatMap { it.allowed }.toSet(),
                l.flatMap { it.unexplained },
                l.sumOf { it.d13Kept },
            )
        }
    }

    private fun economyCase(built: List<GoldenActions.Built>): Outcome {
        val s = ShadowGoldenCompareTest.session()
        val page = FakeShadowPage()
        page.base(built.first().action.before).forEach { assertTrue(s.base(it, 0).ok) }
        for (b in built) {
            GoldenActions.CONTENT.setup = b.setup
            s.observe(page.send(b.action), FakeShadowPage.T0)
        }
        return outcome(s.ledger, built.size)
    }

    private fun handCase(built: List<HandGoldenActions.Built>): Outcome {
        GoldenActions.CONTENT.setup = HandGoldenActions.SETUP
        val s = ShadowGoldenCompareTest.session()
        val page = FakeShadowPage()
        page.base(built.first().action.before).forEach { assertTrue(s.base(it, 0).ok) }
        for (b in built) s.observe(page.send(b.action), FakeShadowPage.T0)
        return outcome(s.ledger, built.size)
    }

    private fun corpusRun(run: JsonObject): Outcome {
        val r = SimReplay.replay(run)
        assertTrue(r.name, SimReplay.problems(r).isEmpty())
        // the boot's envelopes come with every run; the defect is in the actions after them
        val sessions = (run["sessions"] as JsonArray).items
        assertTrue(r.name, sessions.isNotEmpty())
        return outcome(r.ledger, r.envelopes)
    }

    private fun outcome(l: ShadowLedger, envelopes: Int) =
        Outcome(
            1,
            envelopes,
            l.compared,
            l.same,
            l.mismatches().filter { !it.unexplained }.flatMap { it.allow }.toSet(),
            l.mismatches().filter { it.unexplained }.map { "${it.kind} ${it.paths}" },
            l.info.filterKeys { it.startsWith("d13Kept") }.values.sum(),
        )

    private fun defectsOf(text: String): Set<String> =
        DEFECT.findAll(text).map { it.value }.filter { it in DEFECTS }.toSet()

    companion object {
        const val CORPUS = "shadow-defect-corpus.json.gz"
        private val DEFECT = Regex("\\bQ?D\\d\\d\\b")

        /** ECONOMY_SHADOW_PLAN 5.1 point 3, in its order. */
        val DEFECTS =
            listOf(
                "D01",
                "D03",
                "D04",
                "D05",
                "D06",
                "D07",
                "D08",
                "D09",
                "D10",
                "D11",
                "D13",
                "D14",
                "D16",
                "D17",
                "D18",
                "D20",
                "D21",
                "D22",
                "D28",
                "D29",
                "D33",
                "D34",
                "D35",
                "D37",
                "QD02",
                "QD15",
                "QD16",
                "QD19",
                "QD20",
                "QD21",
                "QD22",
                "QD23",
            )

        /**
         * The allowlist ids a defect's envelopes may come out allowed by, because the page's action
         * also touched a field outside the economy (5.2), never the defect itself: A-06, the
         * journal coach's own plain write inside a refused save (the compose case of D13, the
         * refused Log after D29's boot).
         */
        val ALLOWED: Map<String, Set<String>> =
            mapOf("D13" to setOf("A-06"), "D29" to setOf("A-06"))
    }
}
