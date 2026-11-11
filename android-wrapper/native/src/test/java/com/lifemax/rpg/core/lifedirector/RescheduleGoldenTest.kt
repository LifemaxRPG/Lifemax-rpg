package com.lifemax.rpg.core.lifedirector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Replays every case of `ld-reschedule-golden.json.gz` (WP-LD5b) and requires the page's answer
 * exactly, step after step: strings and booleans equal, numbers equal as doubles bit for bit,
 * arrays in order, objects member for member and in the page's member order (a member the page
 * leaves out must be absent), and a throw of the same error name. Each family's case count is
 * fixed, so a lost or added case fails here as well as in the web test that regenerates the file.
 */
class RescheduleGoldenTest {
    @Test
    fun everyCaseGivesThePagesAnswer() {
        val wrong = ArrayList<String>()
        val counts = LinkedHashMap<String, Int>()
        val ids = HashSet<String>()
        var rules = true
        var steps = 0
        RescheduleReplay.forEachCase { c ->
            counts[c.family] = (counts[c.family] ?: 0) + 1
            assertTrue("repeated id ${c.id}", ids.add(c.id))
            assertEquals("reschedule", c.fn)
            steps += (c.args[1] as com.lifemax.rpg.core.save.JsonArray).items.size
            rules = rules && c.rule.split(' ').all { RULE.matches(it) }
            RescheduleReplay.difference(c)?.let { wrong += it }
        }
        println(
            "reschedule golden: ${counts.values.sum()} cases, $steps steps, ${wrong.size} differ"
        )
        assertTrue(
            "${wrong.size} of ${counts.values.sum()} cases differ:\n" +
                wrong.take(SHOWN).joinToString("\n"),
            wrong.isEmpty(),
        )
        assertEquals(FAMILY_COUNTS, counts)
        assertTrue("a case cites a rule outside the prefixes part 6 uses", rules)
    }

    companion object {
        private val SHOWN = (System.getenv("LD_SHOW") ?: "20").toInt()
        private val RULE = Regex("""LD[MBYZ]-\d+""")

        /** Cases per family, as tools/ld_reschedule_cases.js defines them. */
        val FAMILY_COUNTS =
            mapOf(
                "scenario" to 110,
                "seeds" to 60,
                "dst" to 120,
                "miss" to 400,
                "move" to 458,
                "residual" to 159,
                "disrupt" to 406,
                "quest" to 208,
                "time" to 153,
                "sequence" to 160,
                "refuse" to 16,
                "hold" to 13,
                "radius" to 35,
            )
    }
}
