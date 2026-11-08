package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.lifedirector.KernelGolden.cases
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Replays every case of `ld-kernel-golden.json` outside the `tzfragile` family (zones whose offsets
 * depend on the tz database release; [KernelTzFragileTest] checks those) and requires the page's
 * answer exactly: strings and booleans equal, numbers equal as doubles bit for bit (no tolerance),
 * objects member for member. The case count of each family is fixed, so a lost or added case fails
 * here as well as in the web test that regenerates the file.
 */
class KernelGoldenTest {
    @Test
    fun everyCaseGivesThePagesAnswer() {
        val strict = cases.filter { it.family != "tzfragile" }
        val wrong = strict.mapNotNull { KernelReplay.difference(it) }
        assertTrue(
            "${wrong.size} of ${strict.size} strict cases differ (${zoneData()}):\n" +
                wrong.take(SHOWN).joinToString("\n"),
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
        val missing = cases.map { it.fn }.toSet() - KernelReplay.ADAPTERS.keys
        assertTrue("no adapter for $missing", missing.isEmpty())
    }

    @Test
    fun everyCaseCitesAKernelRule() {
        val odd = cases.filter { c -> c.rule.split(' ').any { !RULE.matches(it) } }.map { it.id }
        assertTrue("cases cite rules outside LDT, LDH and LDN: ${odd.take(SHOWN)}", odd.isEmpty())
    }

    @Test
    fun theZoneMatrixAndItsClockChangesAreAllRecorded() {
        val zones =
            cases
                .filter { it.family == "parts" || it.family == "wall" }
                .map { KernelGolden.text(it.args.last()) }
                .toSet()
        assertTrue("zones missing: ${MATRIX - zones}", zones.containsAll(MATRIX))
        val gaps = cases.count { it.family == "wall" && it.id.endsWith("-gap") }
        val folds = cases.count { it.family == "wall" && it.id.endsWith("-fold") }
        assertTrue("$gaps gap and $folds fold wall times", gaps >= GAP_CASES && folds >= FOLD_CASES)
    }

    /**
     * What the zone data is, for the failure message, from APIs android.jar also has (the tz
     * database version itself is only reachable through JDK-only classes).
     */
    private fun zoneData(): String =
        "${ZoneId.getAvailableZoneIds().size} zone ids, Java ${System.getProperty("java.version")}"

    companion object {
        private const val SHOWN = 25
        private const val GAP_CASES = 300
        private const val FOLD_CASES = 300
        private val RULE = Regex("""LD[THN]-\d+""")

        /** The zone matrix of WP-LD1. */
        val MATRIX =
            setOf(
                "UTC",
                "America/New_York",
                "America/Phoenix",
                "America/Los_Angeles",
                "America/Santiago",
                "Europe/London",
                "Asia/Kolkata",
                "Australia/Sydney",
                "Pacific/Apia",
                "Pacific/Kiritimati",
                "Etc/GMT+12",
            )

        /** Cases per family, as tools/ld_kernel_cases.js defines them. */
        val FAMILY_COUNTS =
            mapOf(
                "hash" to 200,
                "stable" to 96,
                "event" to 74,
                "rng" to 22,
                "num" to 41,
                "zone" to 682,
                "alias" to 1364,
                "parts" to 2248,
                "offset" to 2023,
                "wall" to 4900,
                "cal" to 334,
                "horizon" to 119,
                "grid" to 22,
                "slot" to 102,
                "week" to 1071,
                "fuzz" to 1854,
                "tzfragile" to 79,
            )
    }
}
