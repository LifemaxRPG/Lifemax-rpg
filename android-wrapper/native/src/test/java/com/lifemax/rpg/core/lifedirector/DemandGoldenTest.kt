package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.lifedirector.DemandReplay.cases
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Replays every case of `ld-demand-golden.json.gz` (WP-LD3) and requires the page's answer exactly:
 * strings and booleans equal, numbers equal as doubles bit for bit, arrays in order, objects member
 * for member (a member the page leaves out must be absent), and a throw of the same error name. The
 * case count of each family is fixed, so a lost or added case fails here as well as in the web test
 * that regenerates the file.
 */
class DemandGoldenTest {
    @Test
    fun everyCaseGivesThePagesAnswer() {
        val wrong =
            cases.mapNotNull { c -> DemandReplay.difference(c)?.let { it + "\n    at " + path(c) } }
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
        val missing = cases.map { it.fn }.toSet() - DemandReplay.ADAPTERS.keys
        assertTrue("no adapter for $missing", missing.isEmpty())
    }

    @Test
    fun everyCaseCitesASpecRule() {
        val odd = cases.filter { c -> c.rule.split(' ').any { !RULE.matches(it) } }.map { it.id }
        assertTrue("cases cite unknown rule prefixes: ${odd.take(SHOWN)}", odd.isEmpty())
    }

    /**
     * LDF-1, LDF-2: the recording browser's `Math.exp` and `Math.pow` are correctly rounded, which
     * neither the JVM's `Math` (intrinsics) nor `StrictMath` (fdlibm, as older JavaScript engines)
     * is. The replay above requires [PageMath] to give every recorded value; this counts, for the
     * record, how many of them `StrictMath` and `Math` would get wrong in the last place, and fails
     * if `PageMath` misses any.
     */
    @Test
    fun theExponentialsAreCorrectlyRoundedNotFdlibmOrIntrinsics() {
        val floats = cases.filter { it.family == "float" && it.fn != "square" }
        var fdlibm = 0
        var intrinsic = 0
        for (c in floats) {
            val a = c.args.map { (it as JsonNumber).value }
            val want = (c.out as JsonNumber).value.toBits()
            val page = if (c.fn == "exp") PageMath.exp(a[0]) else PageMath.pow(a[0], a[1])
            assertEquals(c.id, want, page.toBits())
            val strict = if (c.fn == "exp") StrictMath.exp(a[0]) else StrictMath.pow(a[0], a[1])
            val fast = if (c.fn == "exp") Math.exp(a[0]) else Math.pow(a[0], a[1])
            if (strict.toBits() != want) fdlibm++
            if (fast.toBits() != want) intrinsic++
        }
        println(
            "float: ${floats.size} recorded exp and pow values; StrictMath differs on $fdlibm, Math on $intrinsic"
        )
    }

    private fun path(case: KernelGolden.Case): String =
        firstDifference(case.out, DemandReplay.run(case), "out")

    private fun firstDifference(a: JsonValue, b: JsonValue, at: String): String =
        when {
            a is JsonObject && b is JsonObject ->
                (a.members.keys + b.members.keys).firstNotNullOfOrNull { k ->
                    val x = a[k]
                    val y = b[k]
                    if (x == y) null
                    else if (x == null || y == null) "$at.$k (${x == null} ${y == null})"
                    else firstDifference(x, y, "$at.$k")
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
        private val RULE = Regex("""LD[DQCEF]-\d+""")

        /** Cases per family, as tools/ld_demand_cases.js defines them. */
        val FAMILY_COUNTS =
            mapOf(
                "tag" to 3008,
                "goal" to 739,
                "template" to 310,
                "expand" to 288,
                "goalinfo" to 23,
                "quest" to 1246,
                "cycle" to 236,
                "ids" to 173,
                "candidates" to 121,
                "coverage" to 24,
                "prep" to 366,
                "drain" to 271,
                "float" to 3241,
                "scenario" to 110,
                "fuzz" to 400,
            )
    }
}
