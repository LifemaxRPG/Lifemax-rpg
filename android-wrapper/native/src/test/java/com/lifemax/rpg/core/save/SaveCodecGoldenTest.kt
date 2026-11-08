package com.lifemax.rpg.core.save

import com.lifemax.rpg.core.economy.EconomyGoldenTest
import com.lifemax.rpg.core.save.SaveTestData.at
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

/**
 * The codec on the starting state of every economy golden case (the fresh save with the case's
 * fields over it), and on every save a case hands `load()`: decode then encode is the identity,
 * decoding is repeatable, and a change to every kind of tracked field keeps everything else.
 *
 * The page's own reading accepts every case. The strict reading accepts every case except the ones
 * that store a value of a type the page never writes (to test how the page reads it), and refuses
 * those for exactly that field.
 */
@RunWith(Parameterized::class)
class SaveCodecGoldenTest(private val id: String, private val case: JsonObject) {
    @Test
    fun roundTripsAsThePageReadsIt() {
        val saves = listOf(SaveTestData.startOf(case)) + SaveTestData.storedOf(case)
        saves.forEach { SaveTestData.checkRoundTrip(it, SaveReading.AS_PAGE) }
    }

    @Test
    fun strictReadingRefusesOnlyMalformedFields() {
        val start = SaveTestData.startOf(case)
        val expected = MALFORMED[id]
        when (val d = EconomySaveCodec.decode(JsonWriter.write(start), SaveReading.STRICT)) {
            is SaveDecoding.Decoded -> {
                assertEquals("$id is accepted only when it has no malformed field", null, expected)
                SaveTestData.checkRoundTrip(start, SaveReading.STRICT)
                val loose = SaveTestData.decoded(start, SaveReading.AS_PAGE)
                assertEquals("both readings agree on a well-typed save", loose.save, d.save)
            }
            is SaveDecoding.Refused -> {
                assertEquals(SaveRefusalReason.WRONG_TYPE, d.refusal.reason)
                assertEquals(expected, d.refusal.problems.map { "${it.field}${it.at}" }.toSet())
            }
        }
    }

    companion object {
        /**
         * The cases whose starting state holds a value of a type the page never writes there, and
         * where (golden case ids; the generator stores them to record NUM-3 and similar).
         */
        val MALFORMED: Map<String, Set<String>> =
            mapOf(
                "cap/day-total-negative-and-text" to
                    setOf("activities[2].xp", "activities[2].xpSplit[0].xp"),
                "award/time-limit-text-durations" to
                    setOf("activities[0].duration", "activities[1].duration"),
                "quest/text-xp" to setOf("quests[0].xp"),
                "catalog/status-text-base" to setOf("actXP.*"),
                "sparks/sync" to setOf("activities[3].xp", "activities[3].xpSplit[0].xp"),
                "sparks/sync-from-text" to setOf("sparks"),
                "sparks/balance-text" to setOf("sparks"),
                "save/credits-sparks-and-rebuilds" to
                    setOf("activities[3].xpSplit[2]", "activities[3].xpSplit[3].xp"),
                "derive/rebuild-attributes" to
                    setOf(
                        "activities[2].xp",
                        "activities[4].xpSplit[1]",
                        "activities[4].xpSplit[2].xp",
                        "activities[4].xpSplit[3].xp",
                    ),
                "derive/rebuild-skills" to setOf("activities[3].xp", "activities[3].xpSplit[0].xp"),
            )

        @JvmStatic
        @Parameters(name = "{0}")
        fun parameters(): List<Array<Any>> =
            SaveTestData.goldenCases.map { arrayOf((it["id"] as JsonString).value, it) }
    }
}

/** Every golden case is covered. */
class SaveCodecGoldenCountTest {
    @Test
    fun everyCaseRuns() {
        val ids = SaveCodecGoldenTest.parameters().map { it[0] }
        assertEquals(EconomyGoldenTest.cases.map { it["id"] }, ids)
        assertTrue(SaveTestData.goldenCases.all { it.at("input") is JsonObject })
    }
}
