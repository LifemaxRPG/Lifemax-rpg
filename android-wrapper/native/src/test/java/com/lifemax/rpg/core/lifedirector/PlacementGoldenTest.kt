package com.lifemax.rpg.core.lifedirector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Replays every case of `ld-placement-golden.json.gz` (WP-LD4) and requires the page's answer
 * exactly: strings and booleans equal, numbers equal as doubles bit for bit, arrays in order,
 * objects member for member (a member the page leaves out must be absent), and a throw of the same
 * error name. Each family's case count is fixed, so a lost or added case fails here as well as in
 * the web test that regenerates the file.
 */
class PlacementGoldenTest {
    @Test
    fun everyCaseGivesThePagesAnswer() {
        val wrong = ArrayList<String>()
        val counts = LinkedHashMap<String, Int>()
        val ids = HashSet<String>()
        var rules = true
        PlacementReplay.forEachCase { c ->
            counts[c.family] = (counts[c.family] ?: 0) + 1
            assertTrue("repeated id ${c.id}", ids.add(c.id))
            assertTrue("no adapter for ${c.fn}", c.fn in PlacementReplay.ADAPTERS)
            rules = rules && c.rule.split(' ').all { RULE.matches(it) }
            PlacementReplay.difference(c)?.let { wrong += it }
        }
        assertTrue(
            "${wrong.size} of ${counts.values.sum()} cases differ:\n" +
                wrong.take(SHOWN).joinToString("\n"),
            wrong.isEmpty(),
        )
        assertEquals(FAMILY_COUNTS, counts)
        assertTrue("a case cites a rule outside part 4's prefixes", rules)
    }

    companion object {
        private const val SHOWN = 20
        private val RULE = Regex("""LD[RKUWJ]-\d+""")

        /** Cases per family, as tools/ld_placement_cases.js defines them. */
        val FAMILY_COUNTS =
            mapOf(
                "prepare" to 178,
                "anchors" to 15,
                "scenario" to 110,
                "seeds" to 220,
                "dst" to 120,
                "rules" to 55,
                "fuzz" to 700,
                "small" to 1600,
            )
    }
}
