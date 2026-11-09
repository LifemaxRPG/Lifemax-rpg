package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.lifedirector.AvailabilityReplay.Args
import com.lifemax.rpg.core.lifedirector.AvailabilityReplay.Mutation
import com.lifemax.rpg.core.lifedirector.AvailabilityReplay.cases
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mutation spot checks for the availability layer: each plausible porting mistake, put in place of
 * one adapter, must make the golden replay fail in the families it should. (Source mutations of the
 * native code itself were also run by hand for WP-LD2: spec section 8.2.)
 */
class AvailabilityMutationTest {
    private fun assertCaught(
        families: Set<String>,
        fn: String,
        least: Int,
        wrong: (Args) -> JsonValue,
    ) {
        val mutation = Mutation(mapOf(fn to wrong))
        val found =
            cases
                .filter { it.family in families && it.fn == fn }
                .filter { AvailabilityReplay.difference(it, mutation) != null }
        assertTrue(
            "only ${found.size} cases noticed the mutation of $fn, wanted $least",
            found.size >= least,
        )
    }

    /**
     * One stored rule question for both copies (plan D3 "one function") is not what the page does
     * today.
     */
    @Test
    fun answeringTheEnginesQuestionWithThePagesCopyIsNoticed() =
        assertCaught(setOf("applies"), "ruleAppliesOn", 10) { a ->
            val start = a.date(1)
            JsonString(
                (0 until a.int(2)).joinToString("") {
                    val d = requireNotNull(CalendarMath.plusDays(start, it))
                    if (runCatching { PageRuleCheck.appliesOn(a[0], d) }.getOrDefault(false)) "1"
                    else "0"
                }
            )
        }

    /** Reading a stored decompression as a number (the page keeps `"30"` as text, LDA-1). */
    @Test
    fun readingTheDecompressionAsANumberIsNoticed() =
        assertCaught(setOf("rule"), "normalizeRule", 3) { a ->
            val out = AvailabilityReplay.ADAPTERS.getValue("normalizeRule")(a) as JsonObject
            val d = out["decompressMin"]
            if (d is JsonString)
                JsonObject(out.members + ("decompressMin" to JsonNumber(PageValues.number(d))))
            else out
        }

    /**
     * `java.time`'s own wall clock choice (the earlier offset in a fold) for a rule's intervals.
     */
    @Test
    fun javaTimesFoldChoiceForRuleIntervalsIsNoticed() =
        assertCaught(setOf("materialize"), "materializeRule", 20) { a ->
            val out = AvailabilityReplay.ADAPTERS.getValue("materializeRule")(a) as JsonArray
            val zone = ZoneId.of((a[3] as JsonString).value)
            val rule = AvailabilityRule.of(a[0], 0)
            JsonArray(
                out.items.map {
                    val o = it as JsonObject
                    val ymd = o["ymd"] as JsonObject
                    fun f(k: String) = (ymd[k] as JsonNumber).value.toInt()
                    val day = LocalDate.of(f("y"), f("mo"), f("d")).atStartOfDay()
                    fun at(minutes: Double) =
                        JsonNumber(
                            ZonedDateTime.ofLocal(day.plusMinutes(minutes.toLong()), zone, null)
                                .toEpochSecond()
                                .toDouble()
                        )
                    JsonObject(
                        o.members +
                            ("startEpoch" to at(rule.startMin)) +
                            ("endEpoch" to at(rule.startMin + rule.durationMin))
                    )
                }
            )
        }
}
