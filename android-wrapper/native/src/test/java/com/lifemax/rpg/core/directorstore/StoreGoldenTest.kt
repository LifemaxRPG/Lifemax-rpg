package com.lifemax.rpg.core.directorstore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Replays every case of `ld-store-golden.json.gz` (WP-LD7) and requires the page's answer exactly:
 * the state text, the report, the save result and message, and every storage call in its order with
 * the storage left as the page left it. The only cases allowed to differ are those whose input
 * nests deeper than native reads (DV-1), and they must be exactly the known ones. Each family's
 * case count is fixed, so a lost or added case fails here as well as in the web test.
 */
class StoreGoldenTest {
    @Test
    fun everyCaseGivesThePagesAnswer() {
        val wrong = ArrayList<String>()
        val beyond = ArrayList<String>()
        val counts = LinkedHashMap<String, Int>()
        val ids = HashSet<String>()
        var rules = true
        StoreGolden.read { refs, c ->
            counts[c.family] = (counts[c.family] ?: 0) + 1
            assertTrue("repeated id ${c.id}", ids.add(c.id))
            assertTrue("no adapter for ${c.fn}", c.fn in StoreReplay.ADAPTERS)
            rules = rules && c.rule.split(' ').all { RULE.matches(it) }
            try {
                val got = StoreReplay.run(c, refs)
                if (got != c.out)
                    wrong +=
                        "${c.id}\n  page:   ${StoreGolden.show(c.out)}\n  native: ${StoreGolden.show(got)}"
            } catch (e: StoreGolden.BeyondNative) {
                beyond += c.id
            }
        }
        assertTrue(
            "${wrong.size} of ${counts.values.sum()} cases differ:\n" +
                wrong.take(SHOWN).joinToString("\n"),
            wrong.isEmpty(),
        )
        assertEquals(
            "only the known deviations (DV-1) are beyond native",
            DEEPER_THAN_NATIVE,
            beyond.toSet(),
        )
        assertEquals(FAMILY_COUNTS, counts)
        assertTrue("a case cites a rule outside LST-n", rules)
    }

    companion object {
        private const val SHOWN = 12
        private val RULE = Regex("""LST-\d+""")

        /** The depth cases nesting past [com.lifemax.rpg.core.save.JsonReader.MAX_DEPTH] (DV-1). */
        val DEEPER_THAN_NATIVE =
            setOf(512, 513, 600, 2000)
                .flatMap { listOf("depth/load/$it", "depth/save/$it") }
                .toSet()

        /** Cases per family, as tools/ld_store_cases.js defines them. */
        val FAMILY_COUNTS =
            mapOf(
                "seeds" to 74,
                "validate" to 421,
                "migrate" to 476,
                "defaults" to 37,
                "load" to 1000,
                "save" to 700,
                "sequence" to 400,
                "backup" to 61,
                "numbers" to 175,
                "unknown" to 44,
                "older" to 13,
                "depth" to 14,
                "fuzz" to 500,
                "browser" to 10,
            )
    }
}
