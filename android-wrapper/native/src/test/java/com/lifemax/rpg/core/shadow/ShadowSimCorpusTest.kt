package com.lifemax.rpg.core.shadow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP6f's proof on the real page (ECONOMY_SHADOW_PLAN WP6f, section 5.1 points 2 to 4): the seeded
 * runs of `tools/gen_shadow_sim_corpus.js` (`tests/fixtures/shadow-sim-corpus.json.gz`, 40 seeds)
 * and every fixture save's scripted day (`shadow-fixture-days.json.gz`), replayed as the app
 * receives them. Every envelope must be compared, or skipped for a reason the plan names
 * ([SimReplay.EXPLAINED_SKIPS]); none may differ without an allowlist id; nothing is refused and no
 * internal error is counted. Together the runs must reach every kind a tap reaches, the day rolling
 * over with the app open and at boot, a refused save and its take-back, a zone change and a clock
 * moved back.
 *
 * One seed again: `node tools/gen_shadow_sim_corpus.js --seed N --out FILE`, then this test with
 * `-Dshadow.sim.file=FILE` (or `-Dshadow.sim.seed=N` on the committed corpus).
 */
class ShadowSimCorpusTest {
    @Test
    fun everySeedOfTheRealPageMatches() {
        val runs = SimReplay.runs(SIM).map { SimReplay.replay(it) }
        val problems = runs.flatMap { SimReplay.problems(it) }
        assertEquals(problems.joinToString("\n"), emptyList<String>(), problems)
        if (
            System.getProperty(SimReplay.FILE_PROPERTY).isNullOrEmpty() &&
                System.getProperty(SimReplay.SEED_PROPERTY) == null
        ) {
            assertEquals(SEEDS, runs.size)
            val kinds = runs.flatMap { r -> r.ledger.kinds.filterValues { it.compared > 0 }.keys }
            assertTrue(
                "every kind a tap reaches: missing ${SIM_KINDS - kinds.toSet()}",
                kinds.containsAll(SIM_KINDS),
            )
            assertTrue("a save the storage refused", runs.sumOf { it.refusedSaves } > 0)
            assertTrue("a clock moved back", runs.any { (it.ledger.info["clockJump"] ?: 0) > 0 })
        }
        print("sim corpus", runs)
    }

    @Test
    fun everyFixtureSaveThroughAScriptedDayMatches() {
        val runs = SimReplay.runs(FIXTURES, override = null).map { SimReplay.replay(it) }
        val problems = runs.flatMap { SimReplay.problems(it) }
        assertEquals(problems.joinToString("\n"), emptyList<String>(), problems)
        assertEquals(FIXTURE_SAVES, runs.size)
        for (r in runs) {
            val compared = r.ledger.kinds.filterValues { it.compared > 0 }.keys
            assertTrue(
                "${r.name}: ${FIXTURE_KINDS - compared}",
                compared.containsAll(FIXTURE_KINDS),
            )
        }
        print("fixture days", runs)
    }

    private fun print(what: String, runs: List<SimReplay.Run>) {
        val kinds = sortedMapOf<String, Int>()
        val skips = sortedMapOf<String, Int>()
        val allow = sortedMapOf<String, Int>()
        for (r in runs) {
            for ((k, c) in r.ledger.kinds) kinds.merge(k, c.compared, Int::plus)
            for ((k, n) in r.skipped) skips.merge(k, n, Int::plus)
            for (m in r.ledger.mismatches()) for (a in m.allow) allow.merge(a, m.count, Int::plus)
        }
        val costs = runs.flatMap { it.pageCosts }.sorted()
        val info = sortedMapOf<String, Int>()
        for (r in runs) for ((k, n) in r.ledger.info) info.merge(k, n, Int::plus)
        println(
            "$what: ${runs.size} runs, ${runs.sumOf { it.envelopes }} envelopes, " +
                "${runs.sumOf { it.ledger.compared }} compared, ${runs.sumOf { it.ledger.same }} same, " +
                "${runs.sumOf { it.ledger.allowed }} allowed $allow, " +
                "${runs.sumOf { it.ledger.different }} unexplained; skipped $skips; " +
                "refused saves ${runs.sumOf { it.refusedSaves }}, info $info; " +
                "page cost p50 ${costs.getOrNull(costs.size / 2)} ms p95 " +
                "${costs.getOrNull(costs.size * 95 / 100)} ms; compared by kind $kinds"
        )
        for (r in runs) println(
            "  ${r.name}: ${r.envelopes} sent, ${r.ledger.compared} compared, " +
                "${r.ledger.same} same, ${r.ledger.allowed} allowed, skipped ${r.skipped}"
        )
    }

    companion object {
        const val SIM = "shadow-sim-corpus.json.gz"
        const val FIXTURES = "shadow-fixture-days.json.gz"
        private const val SEEDS = 40
        private const val FIXTURE_SAVES = 4

        /** The kinds the seeds' random taps reach, every one compared somewhere. */
        val SIM_KINDS =
            setOf(
                "boot.load",
                "boot.decay",
                "log.record",
                "log.delete",
                "voice.log",
                "quest.toggle",
                "quest.create",
                "quest.pack",
                "hand.reroll",
                "hand.verify",
                "rest.take",
                "spark.buy",
                "ascend",
                "bonus.care",
                "bonus.nutrition",
                "money.record",
                "health.sync",
                "decay.apply",
                "save.other",
                "director.award",
                "rules.decay",
                "rules.reset",
            )

        /** The kinds every fixture save's scripted day reaches. */
        val FIXTURE_KINDS =
            setOf(
                "boot.load",
                "log.record",
                "log.delete",
                "quest.toggle",
                "spark.buy",
                "bonus.care",
                "money.record",
                "health.sync",
                "decay.apply",
                "save.other",
                "director.award",
            )
    }
}
