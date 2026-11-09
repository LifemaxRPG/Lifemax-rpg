package com.lifemax.rpg.core.lifedirector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Replays every case of `ld-schedule-golden.json.gz` (WP-LD5) and requires the page's answer
 * exactly: strings and booleans equal, numbers equal as doubles bit for bit, arrays in order,
 * objects member for member (a member the page leaves out must be absent), and a throw of the same
 * error name. Each family's case count is fixed, so a lost or added case fails here as well as in
 * the web test that regenerates the file.
 */
class ScheduleGoldenTest {
    @Test
    fun everyCaseGivesThePagesAnswer() {
        val wrong = ArrayList<String>()
        val counts = LinkedHashMap<String, Int>()
        val ids = HashSet<String>()
        var rules = true
        ScheduleReplay.forEachCase { c ->
            counts[c.family] = (counts[c.family] ?: 0) + 1
            assertTrue("repeated id ${c.id}", ids.add(c.id))
            assertTrue("no adapter for ${c.fn}", c.fn in ScheduleReplay.ADAPTERS)
            rules = rules && c.rule.split(' ').all { RULE.matches(it) }
            ScheduleReplay.difference(c)?.let { wrong += it }
        }
        assertTrue(
            "${wrong.size} of ${counts.values.sum()} cases differ:\n" +
                wrong.take(SHOWN).joinToString("\n"),
            wrong.isEmpty(),
        )
        assertEquals(FAMILY_COUNTS, counts)
        assertTrue("a case cites a rule outside the prefixes part 5 uses", rules)
    }

    companion object {
        private const val SHOWN = 20
        private val RULE = Regex("""LD[XOVREW]-\d+""")

        /** Cases per family, as tools/ld_schedule_cases.js defines them. */
        val FAMILY_COUNTS =
            mapOf(
                "scenario" to 110,
                "seeds" to 140,
                "dst" to 120,
                "rules" to 100,
                "fuzz" to 500,
                "small" to 900,
                "tight" to 430,
                "replan" to 250,
                "repair" to 273,
                "validate" to 55,
                "hash" to 37,
            )
    }
}
