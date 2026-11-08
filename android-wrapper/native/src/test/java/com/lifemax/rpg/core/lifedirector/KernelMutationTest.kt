package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.lifedirector.KernelGolden.bool
import com.lifemax.rpg.core.lifedirector.KernelGolden.cases
import com.lifemax.rpg.core.lifedirector.KernelGolden.number
import com.lifemax.rpg.core.lifedirector.KernelGolden.string
import com.lifemax.rpg.core.lifedirector.KernelReplay.Args
import com.lifemax.rpg.core.lifedirector.KernelReplay.Mutation
import com.lifemax.rpg.core.save.JsonValue
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mutation spot checks: each plausible porting mistake, put in place of one adapter, must make the
 * golden replay fail in the families it should, so the corpus is known to notice it. Several are
 * what an unguided `java.time` port would do, and so also show where `java.time`'s defaults and the
 * page differ.
 */
class KernelMutationTest {
    private fun caught(
        families: Set<String>,
        fn: String,
        wrong: (Args) -> JsonValue,
    ): List<String> {
        val mutation = Mutation(mapOf(fn to wrong))
        return cases
            .filter { it.family in families && it.fn == fn }
            .filter { KernelReplay.difference(it, mutation) != null }
            .map { it.id }
    }

    private fun assertCaught(
        families: Set<String>,
        fn: String,
        least: Int,
        wrong: (Args) -> JsonValue,
    ) {
        val found = caught(families, fn, wrong)
        assertTrue(
            "only ${found.size} cases noticed the mutation of $fn, wanted $least: ${found.take(SHOWN)}",
            found.size >= least,
        )
    }

    /**
     * `java.time`'s own choice for a wall clock time: the earlier offset in a fold, the gap skipped
     * forward.
     */
    private fun javaDefault(a: Args): JsonValue {
        val zone = ZoneId.of(a.text(4))
        val day = LocalDate.of(a.int(0), 1, 1).plusMonths(a.int(1) - 1L).plusDays(a.int(2) - 1L)
        val local = LocalDateTime.of(day, LocalTime.MIDNIGHT).plusMinutes(a.int(3).toLong())
        return number(ZonedDateTime.ofLocal(local, zone, null).toEpochSecond().toDouble())
    }

    @Test
    fun javaTimesFoldRuleIsNoticed() {
        // East of Greenwich the page takes the second occurrence; java.time always the first.
        val real = KernelReplay.ADAPTERS.getValue("zonedToEpoch")
        val found =
            caught(setOf("wall", "fuzz"), "zonedToEpoch") { a ->
                if (a.text(4) in PLAIN_NAMES && a.number(0) >= MODERN_YEAR) javaDefault(a)
                else real(a)
            }
        assertTrue(
            "the fold rule was noticed by ${found.size} cases",
            found.count { it.endsWith("-fold") } >= FOLD_LEAST,
        )
        assertTrue(
            "every wall time java.time misplaces is a fold one",
            found.all { it.endsWith("-fold") || it.startsWith("fuzz/") },
        )
    }

    @Test
    fun aGapResolvedToTheGapsStartIsNoticed() =
        assertCaught(setOf("wall"), "zonedToEpoch", GAP_LEAST) { a ->
            val zone = ZoneNames.resolve(a.text(4))!!
            val t = WallClock.instantOf(a.number(0), a.number(1), a.number(2), a.number(3), zone)
            val inGap =
                WallClock.localParts(t, zone).minuteOfDay !=
                    Math.floorMod(a.int(3), MINUTES_PER_DAY)
            number(if (inGap) t - (t % HOUR) else t)
        }

    @Test
    fun caseSensitiveZoneNamesAreNoticed() =
        assertCaught(setOf("zone"), "isValidTimeZone", ZONE_LEAST) { a ->
            bool(runCatching { ZoneId.of(a.text(0)) }.isSuccess)
        }

    @Test
    fun weeksStartingOnSundayAreNoticed() =
        assertCaught(setOf("week"), "weekKey", WEEK_LEAST) { a ->
            val date = WallClock.localParts(a.number(0), ZoneNames.resolve(a.text(1))!!).date
            string(CalendarMath.key(CalendarMath.plusDays(date, -date.weekday)!!))
        }

    @Test
    fun tenMinuteSlotsAreNoticed() =
        assertCaught(setOf("slot"), "slotOf", SLOT_LEAST) { a ->
            number(Math.floor((a.number(1) - a.number(0)) / TEN_MINUTES))
        }

    @Test
    fun unpaddedStableIdsAreNoticed() =
        assertCaught(setOf("stable", "fuzz"), "stableId", STABLE_LEAST) { a ->
            val text = a.text(1)
            string(
                a.text(0) +
                    "_" +
                    EngineHash.hash53(text, 1).toString(RADIX) +
                    EngineHash.hash53(text, SEVEN).toString(RADIX)
            )
        }

    @Test
    fun hashingCodePointsInsteadOfCodeUnitsIsNoticed() =
        assertCaught(setOf("hash", "fuzz"), "cyrb53", HASH_LEAST) { a ->
            val folded =
                a.text(0).codePoints().toArray().joinToString("") {
                    String(Character.toChars(it % BMP))
                }
            number(EngineHash.hash53(folded, EngineHash.seedOf(a.number(1))).toDouble())
        }

    @Test
    fun calendarDaysWithoutTheTwoDigitYearRuleAreNoticed() =
        assertCaught(setOf("cal"), "daysBetween", 1) { a ->
            val from = a.date(0)
            val to = a.date(1)
            fun day(d: DateFields) =
                LocalDate.of(d.year.toInt(), 1, 1)
                    .plusMonths(d.month.toLong() - 1)
                    .plusDays(d.day.toLong() - 1)
            number(
                runCatching { ChronoUnit.DAYS.between(day(from), day(to)).toDouble() }
                    .getOrDefault(Double.NaN)
            )
        }

    @Test
    fun anOffsetRoundedToTheMinuteIsNoticed() =
        assertCaught(setOf("offset", "parts"), "tzOffsetSec", 1) { a ->
            number(
                Math.round(
                    WallClock.offsetSeconds(a.number(0), ZoneNames.resolve(a.text(1))!!) / MINUTE
                ) * MINUTE
            )
        }

    private companion object {
        const val SHOWN = 10
        const val MODERN_YEAR = 1000.0
        const val FOLD_LEAST = 100
        const val GAP_LEAST = 100
        const val ZONE_LEAST = 20
        const val WEEK_LEAST = 100
        const val SLOT_LEAST = 15
        const val STABLE_LEAST = 100
        const val HASH_LEAST = 10
        const val MINUTES_PER_DAY = 1440
        const val HOUR = 3600.0
        const val MINUTE = 60.0
        const val TEN_MINUTES = 600.0
        const val RADIX = 36
        const val SEVEN = 7
        const val BMP = 0x10000

        /**
         * Zone names `ZoneId.of` reads as the page does (no case folding or legacy names needed).
         */
        val PLAIN_NAMES: Set<String> = ZoneId.getAvailableZoneIds()
    }
}
