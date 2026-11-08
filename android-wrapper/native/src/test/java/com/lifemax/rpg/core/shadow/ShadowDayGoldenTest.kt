package com.lifemax.rpg.core.shadow

import com.lifemax.rpg.core.economy.EconomyGoldenTest
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP6d's evidence on the recorded page: every golden case of the health, decay, shield, streak,
 * rested, zone and time families whose calls are page entry points goes through the real transport
 * and the comparison, and each is compared and matched (none skipped, none unexplained); every
 * decay pass is also sent as the boot's (`boot.decay`: the same pass, then a plain write when it
 * lost XP). The other cases of these families call only pure functions, which no shadow point wraps
 * (plan 1.2: their proof is the economy's own golden replay); a new case that calls an entry point
 * and is not compared fails here.
 */
class ShadowDayGoldenTest {
    @Test
    fun everyInScopeCaseOfTheDayFamiliesIsComparedAndMatches() {
        val built = GoldenActions.build()
        val day = built.filter { it.action.kind in DAY_KINDS }
        val session = ShadowGoldenCompareTest.session()
        val page = FakeShadowPage()
        for (part in page.base(built.first().action.before)) assertTrue(session.base(part, 0).ok)
        val problems = ArrayList<String>()
        val sent = built + bootDecays(built)
        for (b in sent) {
            val l = session.ledger
            val was =
                Triple(l.compared, l.different, l.kinds.values.sumOf { it.skipped.values.sum() })
            GoldenActions.CONTENT.setup = b.setup
            session.observe(page.send(b.action), FakeShadowPage.T0)
            when {
                l.kinds.values.sumOf { it.skipped.values.sum() } > was.third ->
                    problems +=
                        "${b.caseId} ${b.action.kind} skipped ${l.kind(b.action.kind).skipped}"
                l.compared != was.first + 1 -> problems += "${b.caseId} not compared"
                l.different != was.second ->
                    problems += "${b.caseId} ${b.action.kind} ${l.mismatches().last().paths}"
            }
        }
        assertEquals(problems.joinToString("\n"), emptyList<String>(), problems)
        val l = session.ledger
        for (kind in DAY_KINDS) {
            val c = l.kind(kind)
            assertEquals(kind, EXPECTED_ENVELOPES.getValue(kind), c.compared)
            assertEquals(kind, c.compared, c.same)
        }
        println(
            "day goldens: ${casesByFamily()} in scope; ${day.size} envelopes and " +
                "${bootDecays(built).size} boot passes: " +
                DAY_KINDS.joinToString(" · ") { "$it ${l.kind(it).same}/${l.kind(it).compared}" }
        )
    }

    @Test
    fun theInScopeCasesAreCountedSoNoneIsLostSilently() {
        assertEquals(EXPECTED_CASES, casesByFamily())
        // every case of these families outside the shadow calls only pure functions
        val inScope = GoldenActions.inScope().map { it["id"] }.toSet()
        val loose =
            EconomyGoldenTest.cases
                .filter { (it["id"] as String).substringBefore('/') in FAMILIES }
                .filter { it["id"] !in inScope }
                .filter { case ->
                    calls(case).any {
                        it in GoldenActions.ENTRIES || it == DayGoldenActions.SHIELD_TICK
                    }
                }
                .map { it["id"] }
        assertEquals("cases with an entry point not compared", emptyList<Any?>(), loose)
        val pure =
            EconomyGoldenTest.cases
                .filter { (it["id"] as String).substringBefore('/') in FAMILIES }
                .filter { it["id"] !in inScope }
                .flatMap(::calls)
                .toSet()
        assertEquals(PURE, pure)
    }

    companion object {
        val DAY_KINDS = listOf("health.sync", "decay.apply", "boot.decay")
        val FAMILIES =
            setOf(
                "health",
                "decay",
                "shield",
                "streak",
                "rested",
                "zone",
                "time",
                "day",
                "rest",
                "passive",
            )

        /** The in-scope cases of each family (the others' entries are WP6b's and WP6c's kinds). */
        val EXPECTED_CASES =
            sortedMapOf(
                "day" to 3,
                "decay" to 33,
                "health" to 46,
                "rest" to 5,
                "shield" to 13,
                "zone" to 4,
            )

        val EXPECTED_ENVELOPES = mapOf("health.sync" to 52, "decay.apply" to 51, "boot.decay" to 51)

        /** What the out-of-scope cases of these families call: reads and pure rules only. */
        val PURE =
            setOf(
                "lmaHealthAward",
                "ecoRestedPeek",
                "ecoRestedTake",
                "streak",
                "questKey",
                "getToday",
                "ecoAward",
                "dayNum",
                "ecoDayKey",
                "activityDate",
                "getDecay",
                "lastTrained",
                "ecoPassive",
                "ecoRestDay",
                "ecoRestUsed",
            )

        fun casesByFamily(): Map<String, Int> =
            GoldenActions.inScope()
                .groupingBy { (it["id"] as String).substringBefore('/') }
                .eachCount()
                .filterKeys { it in FAMILIES }
                .toSortedMap()

        private fun calls(case: Map<String, Any?>): List<String> =
            ((case["input"] as Map<*, *>)["calls"] as List<*>).map {
                (it as Map<*, *>)["fn"] as String
            }

        /**
         * Each golden decay pass as the boot's: the boot calls the same `applyDecay()` (its toasts
         * and state are the pass's), returns nothing, and writes once with `writeSave()` when the
         * pass lost XP (SAVE-6, D29).
         */
        internal fun bootDecays(built: List<GoldenActions.Built>): List<GoldenActions.Built> =
            built
                .filter { it.action.kind == "decay.apply" }
                .map {
                    val changed = it.action.returns == JsonBool.TRUE
                    GoldenActions.Built(
                        it.caseId + "#boot",
                        it.action.copy(
                            kind = "boot.decay",
                            returns = JsonNull,
                            saves = if (changed) listOf(true) else emptyList(),
                        ),
                        it.recorded,
                        it.setup,
                    )
                }
    }
}
