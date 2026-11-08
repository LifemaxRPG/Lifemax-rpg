package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.lifedirector.KernelGolden.cases
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `tzfragile` cases: zones whose offsets depend on the tz database release (spec 6.2(8),
 * `TZ_FRAGILE` in tools/ld_kernel_cases.js). The page's recorded instants come from Chromium's tz
 * data, and this JVM's may differ (the CI runner's JDK does for Morocco), so the recorded answers
 * are only reported here, matches and mismatches counted. What must hold whatever the data is that
 * the kernel applies the page's rules to the data it has: each answer is checked against this JVM's
 * own transitions (the wall clock it lands on, the gap and fold rules LDT-7 to LDT-9, local day
 * starts, Mondays). Only a broken rule fails.
 */
class KernelTzFragileTest {
    private val fragile = cases.filter { it.family == "tzfragile" }

    @Test
    fun theRecordedAnswersAreReported() {
        val differ = fragile.mapNotNull { KernelReplay.difference(it) }
        println(
            "tzfragile: ${fragile.size - differ.size} of ${fragile.size} cases match the page's tz data on this JVM" +
                differ.take(SHOWN).joinToString("") { "\n  differs (tz data): $it" }
        )
        assertTrue("the file has no tzfragile cases", fragile.isNotEmpty())
    }

    @Test
    fun theKernelsRulesHoldOnThisJvmsData() {
        val broken =
            fragile.mapNotNull { c ->
                runCatching { violation(c) }.getOrElse { "${c.id}: ${it.message}" }
            }
        assertTrue(
            "${broken.size} rule violations:\n" + broken.take(SHOWN).joinToString("\n"),
            broken.isEmpty(),
        )
    }

    /** What rule [c]'s kernel answer breaks on this JVM's data, or null. */
    private fun violation(c: KernelGolden.Case): String? {
        val got = KernelReplay.run(c)
        if (got is JsonObject && "threw" in got)
            return null // a zone this JVM lacks: reported above
        val a = KernelReplay.Args(c.args)
        return when (c.fn) {
            "zonedToEpoch" -> wallRule(c, a, (got as JsonNumber).value)
            "tzOffsetSec" -> {
                val zone = a.zone(1)
                val want = zone.offsetAt(Instant.ofEpochSecond(a.number(0).toLong())).toDouble()
                if ((got as JsonNumber).value == want) null
                else "${c.id}: offset ${got.value}, rules say $want"
            }
            "dayEndAfter" -> dayStartRule(c, a.number(0), (got as JsonNumber).value, a.zone(1))
            "weekKey" -> mondayRule(c, (got as JsonString).value)
            "weekBounds" -> mondayRule(c, ((got as JsonObject)["monKey"] as JsonString).value)
            "buildHorizon" ->
                horizonRule(c, (got as JsonArray).items.map { it as JsonObject }, a.zone(2))
            else -> null
        }
    }

    /** LDT-7 to LDT-9 against the JVM's offsets for the wall clock asked for. */
    private fun wallRule(c: KernelGolden.Case, a: KernelReplay.Args, got: Double): String? {
        if (got.isNaN()) return null
        val zone = a.zone(4)
        val wall = EcmaTime.utc(a.number(0), a.number(1) - 1, a.number(2), 0.0, a.number(3)) / MS
        val local = LocalDateTime.ofEpochSecond(wall.toLong(), 0, ZoneOffset.UTC)
        val offsets = zone.offsetsOf(local)
        val want =
            when (offsets.size) {
                1 -> wall - offsets.single()
                0 -> wall - zone.offsetBeforeGap(local)
                else -> wall - zone.offsetAt(Instant.ofEpochSecond(wall.toLong()))
            }
        val shown = WallClock.localParts(got, zone)
        val landsOnWall =
            offsets.isEmpty() || shown.minuteOfDay == local.hour * MINUTES + local.minute
        return if (got == want && landsOnWall) null
        else "${c.id}: got $got, the rules on this JVM's data give $want"
    }

    /** LDT-18: the answer is the first instant of the local date after [epoch]'s. */
    private fun dayStartRule(
        c: KernelGolden.Case,
        epoch: Double,
        got: Double,
        zone: PageZone,
    ): String? {
        val today = WallClock.localParts(epoch, zone).date
        val next = CalendarMath.plusDays(today, 1)
        val ok =
            got > epoch &&
                WallClock.localParts(got, zone).date == next &&
                WallClock.localParts(got - 1, zone).date == today
        return if (ok) null else "${c.id}: $got is not the start of the day after"
    }

    /** LDT-19: a week key is a Monday's. */
    private fun mondayRule(c: KernelGolden.Case, key: String): String? =
        if (CalendarMath.parse(key)?.weekday == MONDAY) null else "${c.id}: $key is not a Monday"

    /**
     * LDT-15: consecutive dates, each day starting where the last ended, at its first local
     * instant.
     */
    private fun horizonRule(c: KernelGolden.Case, days: List<JsonObject>, zone: PageZone): String? {
        fun n(o: JsonObject, k: String) = (o[k] as JsonNumber).value
        for ((i, d) in days.withIndex()) {
            val start = n(d, "startEpoch")
            val end = n(d, "endEpoch")
            val date = WallClock.localParts(start, zone).date
            if (WallClock.localParts(start - 1, zone).date == date)
                return "${c.id}: day $i starts late"
            if (n(d, "slots") != (end - start) / SlotGrid.SLOT_SECONDS)
                return "${c.id}: day $i slots"
            if (i > 0 && n(days[i - 1], "endEpoch") != start)
                return "${c.id}: day $i does not follow"
        }
        return null
    }

    private companion object {
        const val SHOWN = 20
        const val MS = 1000.0
        const val MINUTES = 60
        const val MONDAY = 1
    }
}
