package com.lifemax.rpg.core.lifedirector.host

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Replays every case of `ld-host-golden.json.gz` (WP-LD9) through [HostCommands] and requires the
 * page's outcome at every step: the reply, every host and store call in order with its arguments,
 * the page's volatile state and `ST` member for member ([HostReplay]). Each family's case count is
 * fixed, and every rule a case cites is an HC rule.
 */
class HostGoldenTest {
    @Test
    fun everyStepGivesThePagesOutcome() {
        val wrong = ArrayList<String>()
        val counts = LinkedHashMap<String, Int>()
        val failing = LinkedHashMap<String, Int>()
        var rules = true
        HostReplay.forEachCase { c ->
            counts[c.family] = (counts[c.family] ?: 0) + 1
            rules = rules && c.rule.split(' ').all { RULE.matches(it) }
            val d = HostReplay.replay(c) ?: return@forEachCase
            failing[c.family] = (failing[c.family] ?: 0) + 1
            wrong += d
        }
        assertTrue(
            "${wrong.size} of ${counts.values.sum()} cases differ (by family $failing):\n" +
                wrong.take(SHOWN).joinToString("\n"),
            wrong.isEmpty(),
        )
        assertEquals(FAMILY_COUNTS, counts)
        assertTrue("a case cites a rule outside HC-n", rules)
    }

    companion object {
        private val SHOWN = System.getProperty("ld.shown")?.toInt() ?: 30
        private val RULE = Regex("""HC-\d+""")

        /** Cases per family, as tools/ld_host_cases.js defines them. */
        val FAMILY_COUNTS =
            mapOf(
                "done" to 175,
                "log" to 40,
                "skip" to 59,
                "pin" to 24,
                "disrupt" to 53,
                "replan" to 25,
                "regroup" to 41,
                "goals" to 26,
                "prefs" to 21,
                "time" to 28,
                "activity" to 23,
                "backup" to 7,
                "sync" to 59,
                "misses" to 34,
                "rollover" to 36,
                "dial" to 27,
                "output" to 4,
                "early" to 10,
                "boot" to 7,
                "fuzz" to 160,
            )
    }
}
