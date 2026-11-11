package com.lifemax.rpg.core.lifedirector.timer

import com.lifemax.rpg.core.lifedirector.host.HostReplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Replays every case of `ld-timer-golden.json.gz` (WP-LD10, recorded from logic.js in the page in
 * Chromium) through the native host commands with the timer ([TimerCommands]) and requires the
 * page's outcome at every step: the reply, every host and store call in order with its arguments,
 * the page's volatile state and `ST` member for member (WP-LD9's [HostReplay]). Each family's case
 * count is fixed, and every rule a case cites is a TM rule.
 */
class TimerGoldenTest {
    @Test
    fun everyStepGivesThePagesOutcome() {
        val wrong = ArrayList<String>()
        val counts = LinkedHashMap<String, Int>()
        val failing = LinkedHashMap<String, Int>()
        var rules = true
        HostReplay.forEachCase(FILE) { c ->
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
        assertTrue("a case cites a rule outside TM-n", rules)
    }

    companion object {
        const val FILE = "ld-timer-golden.json.gz"
        private val SHOWN = System.getProperty("ld.shown")?.toInt() ?: 30
        private val RULE = Regex("""TM-\d+""")

        /** Cases per family, as tools/ld_timer_cases.js defines them. */
        val FAMILY_COUNTS =
            mapOf(
                "start" to 64,
                "pause" to 40,
                "reset" to 24,
                "replace" to 28,
                "finish" to 72,
                "cancel" to 20,
                "auto" to 68,
                "tick" to 40,
                "clock" to 48,
                "wake" to 40,
                "fuzz" to 120,
            )
    }
}
