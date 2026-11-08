package com.lifemax.rpg.core.adapters

import com.lifemax.rpg.core.adapters.DeviceContextReplay.cases
import com.lifemax.rpg.core.adapters.DeviceContextReplay.differences
import com.lifemax.rpg.core.adapters.DeviceContextReplay.differingValues
import com.lifemax.rpg.core.adapters.DeviceContextReplay.family
import com.lifemax.rpg.core.adapters.DeviceContextReplay.id
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The adapters hand the pure code the inputs the page computes: every case of
 * `device-context-golden.json` (clock and time zone matrix, manual clock and zone changes, and the
 * Daily Hand's water, cat, plan, luck and rest-day facts) replays through the adapters. The
 * families `exotic` and `factsodd` hold the few places where the economy reads a value differently
 * from the page; they are replayed too and must differ in exactly the pinned places, so a change
 * either way (a difference fixed, or a new one) fails here.
 */
class DeviceContextGoldenTest {
    @Test
    fun everyFaithfulCaseReplaysExactly() {
        val failures =
            cases
                .filter { family(it) !in KNOWN }
                .mapNotNull { case ->
                    differences(case).takeIf { it.isNotEmpty() }?.let { "${id(case)}: $it" }
                }
        assertTrue(
            "${failures.size} cases differ from the page:\n" +
                failures.take(SHOWN).joinToString("\n"),
            failures.isEmpty(),
        )
    }

    @Test
    fun theKnownDifferencesAreExactlyThePinnedOnes() {
        val found =
            cases
                .filter { family(it) in KNOWN }
                .associate { id(it) to differingValues(it) }
                .filterValues { it.isNotEmpty() }
        assertEquals(PINNED, found)
    }

    /** TIME-8's `weekMonday` is the page's `weekStart()` in every recorded clock and zone. */
    @Test
    fun theEconomyWeekMondayIsThePagesWeekStart() {
        val wrong = mutableListOf<String>()
        for (case in cases.filter { family(it) in CLOCKS }) {
            val input = case["input"] as JsonObject
            val device = FixedDeviceClock(text(input["now"]), text(input["zone"]))
            val outs = ((case["output"] as JsonObject)["calls"] as JsonArray).items
            for ((i, call) in (input["calls"] as JsonArray).items.withIndex()) {
                call as JsonObject
                (call["at"] as? JsonString)?.let {
                    device.now = Instant.parse(it.value).toEpochMilli()
                }
                (call["zone"] as? JsonString)?.let { device.zone = ZoneId.of(it.value) }
                val page = text((outs[i] as JsonObject)["weekStart"])
                if (device.reading().weekMonday != page) wrong += "${id(case)} call $i"
            }
        }
        assertTrue("weekMonday differs from weekStart in: $wrong", wrong.isEmpty())
    }

    @Test
    fun theFileHoldsEveryFamily() {
        val counts = cases.groupingBy(::family).eachCount()
        assertEquals(FAMILIES, counts)
        assertEquals("case ids are unique", cases.size, cases.map(::id).toSet().size)
        assertTrue("every family replayed, none skipped", cases.size == FAMILIES.values.sum())
    }

    private fun text(value: com.lifemax.rpg.core.save.JsonValue?) = (value as JsonString).value

    private companion object {
        const val SHOWN = 12
        val KNOWN = setOf("exotic", "factsodd")
        val CLOCKS = setOf("clock", "clockmove", "zonemove")
        val FAMILIES =
            mapOf(
                "clock" to 21,
                "clockmove" to 6,
                "zonemove" to 3,
                "exotic" to 4,
                "facts" to 61,
                "factsodd" to 3,
            )
        private val ODD_DATES = listOf("2026-02-30T10:00:00.000Z", "2026-10-28 10:00", "10/28/2026")

        /**
         * Where the economy and the page differ, pinned. Dates: the page's legacy parser rolls a 30
         * February over to 2 March and reads a space-separated or US-style text as a local time;
         * the economy reads ISO forms only and calls these `Invalid Date` (no record the app writes
         * has such a date). Checks: the economy's `water:` with no number passes at 0% where the
         * page needs 100%, an unknown `pet:` care name never passes where the page reads it as
         * play, and a `null` water entry is skipped where the page throws.
         */
        val PINNED =
            mapOf(
                "exotic/utc" to ODD_DATES,
                "exotic/america-new-york" to ODD_DATES,
                "exotic/pacific-kiritimati" to ODD_DATES,
                "exotic/pacific-pago-pago" to ODD_DATES,
                "factsodd/water-without-percent" to listOf("water:", "water:0", "water:abc"),
                "factsodd/pet-unknown-care" to listOf("pet:groom", "pet:", "pet"),
                "factsodd/water-null-entry" to listOf("water:100"),
            )
    }
}
