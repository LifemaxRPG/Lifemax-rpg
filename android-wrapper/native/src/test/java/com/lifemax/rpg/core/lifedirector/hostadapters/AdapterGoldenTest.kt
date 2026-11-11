package com.lifemax.rpg.core.lifedirector.hostadapters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Replays every case of `ld-adapters-golden.json.gz` (WP-LD11) through [EconomyHost] and requires
 * the page's answer to every call, the economy the calls leave (every recorded save field) and the
 * messages shown, exactly. The only cases allowed to differ are the shapes the strict readers
 * refuse ([DEVIATIONS], LIFE_DIRECTOR_ADAPTERS_SPEC DV-A1): each must be refused, and no other case
 * may be. Each family's case count is fixed.
 */
class AdapterGoldenTest {
    @Test
    fun everyCaseGivesThePagesAnswer() {
        val wrong = ArrayList<String>()
        val refused = ArrayList<String>()
        val counts = LinkedHashMap<String, Int>()
        var rules = true
        for (c in AdapterGolden.cases) {
            counts[c.family] = (counts[c.family] ?: 0) + 1
            rules = rules && c.rule.split(' ').all { RULE.matches(it) }
            val d = AdapterReplay.difference(c) ?: continue
            if (d.startsWith("${c.id}: ${AdapterReplay.REFUSED} (")) refused += c.id else wrong += d
        }
        assertTrue(
            "${wrong.size} of ${counts.values.sum()} cases differ:\n" +
                wrong.take(SHOWN).joinToString("\n"),
            wrong.isEmpty(),
        )
        assertEquals("the refused cases", DEVIATIONS.sorted(), refused.sorted())
        assertEquals(FAMILY_COUNTS, counts)
        assertTrue("a case cites a rule outside HA-n", rules)
    }

    companion object {
        private const val SHOWN = 40
        private val RULE = Regex("""HA-\d+""")

        /** Cases per family, as tools/ld_adapters_cases.js defines them. */
        val FAMILY_COUNTS: Map<String, Int> =
            linkedMapOf(
                "tags" to 571,
                "week" to 68,
                "player" to 11,
                "xp" to 39,
                "award" to 50,
                "bounty" to 37,
                "quests" to 219,
                "plan" to 71,
                "milestones" to 23,
                "retired" to 64,
                "since" to 40,
                "describe" to 38,
                "meals" to 84,
                "flow" to 12,
                "verify" to 24,
                "fuzz" to 300,
            )

        /** The cases native refuses (DV-A1): quest values the page itself never writes. */
        val DEVIATIONS: Set<String> =
            setOf(
                "describe/quest-number-desc",
                "quests/number-id",
                "retired/board-has-number-id",
                "tags/mapQuestTag/name-null",
            )
    }
}
