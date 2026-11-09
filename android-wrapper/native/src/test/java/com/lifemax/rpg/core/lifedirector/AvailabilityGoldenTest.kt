package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.lifedirector.AvailabilityReplay.cases
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Replays every case of `ld-availability-golden.json` (WP-LD2) and requires the page's answer
 * exactly: strings and booleans equal, numbers equal as doubles bit for bit, arrays in order,
 * objects member for member, and a throw of the same error name. Every zone is a strict zone of the
 * WP-LD1 matrix, so no case depends on the tz database release. The case count of each family is
 * fixed, so a lost or added case fails here as well as in the web test that regenerates the file.
 */
class AvailabilityGoldenTest {
    @Test
    fun everyCaseGivesThePagesAnswer() {
        val wrong =
            cases.mapNotNull { c ->
                AvailabilityReplay.difference(c)?.let { it + "\n    at " + path(c) }
            }
        assertTrue(
            "${wrong.size} of ${cases.size} cases differ:\n" + wrong.take(SHOWN).joinToString("\n"),
            wrong.isEmpty(),
        )
    }

    @Test
    fun theFileHoldsEveryFamilyWithItsCount() {
        val counts = cases.groupingBy { it.family }.eachCount()
        assertEquals(FAMILY_COUNTS, counts)
        assertEquals(FAMILY_COUNTS.values.sum(), cases.size)
        assertEquals(cases.size, cases.map { it.id }.toSet().size)
    }

    @Test
    fun everyPageFunctionHasAnAdapter() {
        val missing = cases.map { it.fn }.toSet() - AvailabilityReplay.ADAPTERS.keys
        assertTrue("no adapter for $missing", missing.isEmpty())
    }

    @Test
    fun everyCaseCitesASpecRule() {
        val odd = cases.filter { c -> c.rule.split(' ').any { !RULE.matches(it) } }.map { it.id }
        assertTrue("cases cite unknown rule prefixes: ${odd.take(SHOWN)}", odd.isEmpty())
    }

    /**
     * Plan defect D3, measured: the page's own copy of the rule question ([PageRuleCheck]) and the
     * engine's ([AvailabilityRule.appliesOn]) answer differently for exactly these stored rules
     * (spec LDA-9). A change on either side shows up here first.
     */
    @Test
    fun thePagesCopyOfTheRuleQuestionDiffersOnlyWhereTheSpecSays() {
        val engine =
            cases
                .filter { it.family == "applies" }
                .associateBy { it.id.substringAfter("ruleAppliesOn/") }
        val differ =
            cases
                .filter { it.family == "ruleon" }
                .filter { engine.getValue(it.id.substringAfter("ruleOn/")).out != it.out }
                .map { it.id.substringAfter("ruleOn/").substringBefore(':') }
                .toSet()
        assertEquals(D3_DIFFERENCES, differ)
    }

    /** Where [case]'s answer first differs, as a path into the output. */
    private fun path(case: KernelGolden.Case): String =
        firstDifference(case.out, AvailabilityReplay.run(case), "out")

    private fun firstDifference(a: JsonValue, b: JsonValue, at: String): String =
        when {
            a is JsonObject && b is JsonObject ->
                (a.members.keys + b.members.keys).firstNotNullOfOrNull { k ->
                    val x = a[k]
                    val y = b[k]
                    if (x == y) null
                    else if (x == null || y == null) "$at.$k" else firstDifference(x, y, "$at.$k")
                } ?: at
            a is JsonArray && b is JsonArray ->
                (0 until maxOf(a.items.size, b.items.size)).firstNotNullOfOrNull { i ->
                    val x = a.items.getOrNull(i)
                    val y = b.items.getOrNull(i)
                    if (x == y) null
                    else if (x == null || y == null) "$at[$i]" else firstDifference(x, y, "$at[$i]")
                } ?: at
            else -> "$at: ${KernelGolden.show(a)} vs ${KernelGolden.show(b)}"
        }

    companion object {
        private const val SHOWN = 25
        private val RULE = Regex("""LD[ATNHPSG]-\d+""")

        /** Cases per family, as tools/ld_availability_cases.js defines them. */
        val FAMILY_COUNTS =
            mapOf(
                "helper" to 66,
                "rule" to 557,
                "applies" to 390,
                "ruleon" to 390,
                "materialize" to 588,
                "prefs" to 110,
                "sleep" to 510,
                "paint" to 161,
                "avail" to 169,
                "scenario" to 96,
                "dst" to 160,
                "fuzz" to 600,
            )

        /** The `applies` cases (tag before `:`) whose answer the page's copy gives differently. */
        val D3_DIFFERENCES =
            setOf(
                "oddrotation0",
                "oddrotation1",
                "oddrotation2",
                "oddrotation3",
                "oddrotation4",
                "oddrotation5",
                "oddrotation7",
                "oddrotation20",
                "oddrotation21",
                "oddrotation22",
                "legacy0",
                "legacy1",
                "legacy2",
                "legacy3",
            )
    }
}
