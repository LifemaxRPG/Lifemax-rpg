package com.lifemax.rpg.core.lifedirector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Replays every case of `ld-ledger-golden.json.gz` (WP-LD6) and requires the page's answer exactly:
 * strings and booleans equal, numbers equal as doubles bit for bit, arrays in order, objects member
 * for member, and a throw of the same error name. The only cases allowed to differ are the
 * malformed shapes the strict decoder refuses ([DEVIATIONS], LIFE_DIRECTOR_LEDGER_SPEC.md section
 * 6): each of them must be refused, and no other case may be. Each family's case count is fixed.
 */
class LedgerGoldenTest {
    @Test
    fun everyCaseGivesThePagesAnswer() {
        val wrong = ArrayList<String>()
        val refused = ArrayList<String>()
        val counts = LinkedHashMap<String, Int>()
        val ids = HashSet<String>()
        var rules = true
        LedgerReplay.forEachCase { c ->
            counts[c.family] = (counts[c.family] ?: 0) + 1
            assertTrue("repeated id ${c.id}", ids.add(c.id))
            assertTrue("no adapter for ${c.fn}", c.fn in LedgerReplay.ADAPTERS)
            rules = rules && c.rule.split(' ').all { RULE.matches(it) }
            val d = LedgerReplay.difference(c) ?: return@forEachCase
            if (c.id in DEVIATIONS && d.endsWith(")") && d.contains(": refused (")) refused += c.id
            else wrong += d
        }
        assertTrue(
            "${wrong.size} of ${counts.values.sum()} cases differ:\n" +
                wrong.take(SHOWN).joinToString("\n"),
            wrong.isEmpty(),
        )
        assertEquals("the refused cases", DEVIATIONS.sorted(), refused.sorted())
        assertEquals(FAMILY_COUNTS, counts)
        assertTrue("a case cites a rule outside LED-n", rules)
    }

    companion object {
        private const val SHOWN = 25
        private val RULE = Regex("""LED-\d+""")

        /** Cases per family, as tools/ld_ledger_cases.js defines them. */
        val FAMILY_COUNTS =
            mapOf(
                "empty" to 19,
                "event" to 313,
                "transition" to 257,
                "record" to 274,
                "revoke" to 49,
                "has" to 25,
                "compact" to 15,
                "reconcile" to 98,
                "misses" to 51,
                "miss" to 394,
                "stream" to 135,
                "dst" to 86,
                "fuzz" to 400,
                "malformed" to 101,
            )

        /**
         * The malformed cases native refuses where the page carries on (DV-L1 to DV-L3 of the
         * spec): a list where the page keeps an object, a quest or goal that is not an object, a
         * quest list given as text.
         */
        val DEVIATIONS: Set<String> =
            setOf(
                "malformed/recordCompletion/record:ledger.events=[]",
                "malformed/recordCompletion/record:ledger.streaks=[]",
                "malformed/revokeEvent/revoke:revoked-list",
                "malformed/ledgerHas/has:events-list",
                "malformed/reconcileQuestProgress/reconcile:quests-text",
                "malformed/reconcileQuestProgress/reconcile:quest-text",
                "malformed/reconcileQuestProgress/reconcile:quest-list",
                "malformed/reconcileQuestProgress/reconcile:goal-text",
            )
    }
}
