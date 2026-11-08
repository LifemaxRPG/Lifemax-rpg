package com.lifemax.rpg.core.save

import com.lifemax.rpg.core.economy.Activity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** How the codec refuses: wrong types, text that is not JSON, and changes it cannot write. */
class SaveCodecStrictnessTest {
    private fun saveWith(extra: String): String =
        JsonWriter.write(SaveTestData.fresh).let { it.substring(0, it.length - 1) + ",$extra}" }

    private fun refusal(text: String, reading: SaveReading = SaveReading.STRICT): SaveRefusal =
        (EconomySaveCodec.decode(text, reading) as SaveDecoding.Refused).refusal

    @Test
    fun aTrackedValueOfTheWrongTypeRefusesTheSave() {
        val cases =
            mapOf(
                "\"sparks\":\"5\"" to "sparks",
                "\"sparks\":true" to "sparks",
                "\"perk\":5" to "perk",
                "\"xpSchemaV2\":\"yes\"" to "xpSchemaV2",
                "\"activities\":{}" to "activities",
                "\"activities\":[1]" to "activities[0]",
                "\"activities\":[{\"id\":7,\"type\":\"reading\"}]" to "activities[0].id",
                "\"activities\":[{\"id\":\"a\",\"type\":\"r\",\"xpSplit\":[null]}]" to
                    "activities[0].xpSplit[0]",
                "\"restDays\":{\"2026-01-01\":\"yes\"}" to "restDays.*",
                "\"customRules\":{\"reading\":{\"base\":\"20\"}}" to "customRules.*.base",
                "\"mealsMeta\":{\"calGoal\":\"2000\"}" to "mealsMeta.calGoal",
                "\"money\":{\"tx\":[{\"id\":\"t\",\"amount\":\"5\"}]}" to "money.tx[0].amount",
                "\"health\":{\"on\":\"on\"}" to "health.on",
                "\"plan\":{\"checks\":{\"d\":{\"s\":{\"k\":[\"x\"]}}}}" to "plan.checks.*.s.*[0]",
                "\"dailyDraw\":{\"rerollsUsed\":\"1\"}" to "dailyDraw.rerollsUsed",
                "\"journalCoach\":{\"days\":{\"d\":{\"xpAwarded\":\"y\"}}}" to
                    "journalCoach.days.*.xpAwarded",
                "\"journalCoach\":{\"welcome\":{\"d\":{\"gap\":3,\"tier\":\"2\"}}}" to
                    "journalCoach.welcome.*.tier",
            )
        for ((field, at) in cases) {
            val r = refusal(saveWith(field))
            assertEquals(field, SaveRefusalReason.WRONG_TYPE, r.reason)
            assertEquals(field, listOf(at), r.problems.map { it.field + it.at })
            // The page's own reading accepts it, reading the value as the page does.
            assertTrue(
                EconomySaveCodec.decode(saveWith(field), SaveReading.AS_PAGE)
                    is SaveDecoding.Decoded
            )
        }
    }

    @Test
    fun nullAndMissingAreAcceptedAndUntrackedTypesAreFree() {
        val text =
            saveWith(
                "\"sparks\":null,\"perk\":null,\"quests\":null,\"health\":null,\"decayLog\":null," +
                    "\"name\":5,\"pet\":\"x\",\"activitiesX\":{}"
            )
        val d = SaveTestData.decoded(text, SaveReading.STRICT)
        assertEquals(0.0, d.save.sparks, 0.0) // Number(null)
        assertEquals(null, d.save.decayLog)
        assertEquals(text, SaveTestData.encoded(d.document, d.save).text)
    }

    @Test
    fun aRefusalNeverRepeatsWhatTheSaveHolds() {
        val r = refusal(saveWith("\"skillXP\":{\"SECRET skill\":\"SECRET value\"}"))
        assertEquals(listOf(SaveProblem("skillXP", ".*", "number", "STRING")), r.problems)
        assertFalse(r.toString().contains("SECRET"))
        val bad = refusal("{\"SECRET\":")
        assertEquals(SaveRefusalReason.NOT_JSON, bad.reason)
        assertFalse(bad.toString().contains("SECRET"))
    }

    @Test
    fun textThatIsNotJsonOrNotAnObjectIsRefused() {
        val notJson =
            listOf(
                "",
                " ",
                "{",
                "{\"a\":1,}",
                "[1,]",
                "{'a':1}",
                "{\"a\":NaN}",
                "{\"a\":Infinity}",
                "{\"a\":01}",
                "{\"a\":1.}",
                "{\"a\":.5}",
                "{\"a\":+1}",
                "{\"a\":0x10}",
                "{\"a\":\"\u0001\"}",
                "{\"a\":\"\\x\"}",
                "{\"a\":\"\\u12\"}",
                "{\"a\":\"\\u١٢٣٤\"}",
                "\ufeff{}",
                "{}/**/",
                "{} x",
                "{\"a\" 1}",
                "{\"a\":tru}",
            )
        for (t in notJson) assertEquals(t, SaveRefusalReason.NOT_JSON, refusal(t).reason)
        for (t in listOf("[]", "\"save\"", "1", "null", "true")) assertEquals(
            t,
            SaveRefusalReason.NOT_AN_OBJECT,
            refusal(t).reason,
        )
    }

    @Test
    fun nestingIsBoundedWithoutThrowing() {
        fun nested(n: Int) = "{\"x\":" + "[".repeat(n) + "]".repeat(n) + "}"
        assertTrue(
            EconomySaveCodec.decode(nested(JsonReader.MAX_DEPTH - 1)) is SaveDecoding.Decoded
        )
        assertEquals(SaveRefusalReason.NOT_JSON, refusal(nested(JsonReader.MAX_DEPTH + 1)).reason)
        assertEquals(SaveRefusalReason.NOT_JSON, refusal(nested(100_000)).reason)
    }

    @Test
    fun aRepeatedKeyKeepsItsFirstPlaceAndLastValue() {
        val v = SaveTestData.parse("{\"a\":1,\"b\":2,\"a\":3}") as JsonObject
        assertEquals(listOf("a", "b"), v.members.keys.toList())
        assertEquals(JsonNumber(3.0), v["a"])
        assertEquals("{\"a\":3,\"b\":2}", JsonWriter.write(v))
    }

    @Test
    fun aNumberTheEconomyMadeNaNIsRefusedStrictlyAndNullAsThePage() {
        val text = saveWith("\"restedXP\":5")
        val strict = SaveTestData.decoded(text, SaveReading.STRICT)
        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY)) {
            val r = EconomySaveCodec.encode(strict.document, strict.save.copy(restedXp = bad))
            r as SaveEncoding.Refused
            assertEquals(SaveRefusalReason.NON_FINITE_NUMBER, r.refusal.reason)
            assertEquals("restedXP", r.refusal.problems.single().field)
        }
        val loose = SaveTestData.decoded(text, SaveReading.AS_PAGE)
        val out = SaveTestData.encoded(loose.document, loose.save.copy(restedXp = Double.NaN))
        assertTrue(out.text.contains("\"restedXP\":null"))
        // A NaN that stands for "not set" is not written: the save keeps lacking the field.
        val unset = SaveTestData.decoded(JsonWriter.write(SaveTestData.fresh), SaveReading.STRICT)
        val more = SaveTestData.encoded(unset.document, unset.save.copy(sparks = 3.0))
        assertFalse(more.text.contains("\"restedXP\""))
        // A record's NaN quality is a value, not "not set" (missing quality reads as none).
        val nanQuality =
            unset.save.copy(activities = listOf(Activity("a", "reading", quality = Double.NaN)))
        assertTrue(EconomySaveCodec.encode(unset.document, nanQuality) is SaveEncoding.Refused)
        val page = SaveTestData.decoded(JsonWriter.write(SaveTestData.fresh), SaveReading.AS_PAGE)
        assertTrue(
            SaveTestData.encoded(page.document, nanQuality).text.contains("\"quality\":null")
        )
    }

    @Test
    fun untouchedTextKeepsItsExactNumbersAndEscapesThroughAChange() {
        val odd =
            "\"probe\":{\"negZero\":-0,\"huge\":1e400,\"one\":1.0,\"e\":1E2,\"big\":12345678901234567890," +
                "\"s\":\"\\u00e9\\/\\ud800\"},\"activities\":[{\"id\":\"a1\",\"type\":\"reading\",\"xp\":1.0," +
                "\"duration\":30,\"mine\":{\"z\":-0}}]"
        val text = saveWith(odd)
        val d = SaveTestData.decoded(text, SaveReading.STRICT)
        assertEquals(text, SaveTestData.encoded(d.document, d.save).text)
        val changed =
            d.save.copy(
                sparks = 9.0,
                activities =
                    d.save.activities.map { it.copy(sparked = true) } + Activity("a2", "task"),
            )
        val out = SaveTestData.encoded(d.document, changed).text
        // Numbers keep their text; a string keeps its value (escapes are written as the page
        // would).
        assertTrue(
            out,
            out.contains(
                "\"negZero\":-0,\"huge\":1e400,\"one\":1.0,\"e\":1E2,\"big\":12345678901234567890"
            ),
        )
        val probe = { t: String -> (SaveTestData.parse(t) as JsonObject)["probe"] }
        assertEquals(probe(text), probe(out))
        assertEquals(JsonString("é/\ud800"), (probe(out) as JsonObject)["s"])
        assertTrue(out, out.contains("\"xp\":1.0,\"duration\":30,\"mine\":{\"z\":-0},\"sp\":1}"))
        assertTrue(out.contains("\"sparks\":9"))
    }

    @Test
    fun aFieldTheEconomyDeletesIsRemovedAndRecordsAreFollowedById() {
        val text =
            saveWith(
                "\"decay\":{\"on\":true,\"grace\":3,\"rate\":2,\"x\":1},\"activities\":[" +
                    "{\"id\":\"a\",\"type\":\"t\",\"keep\":1},{\"id\":\"b\",\"type\":\"t\",\"keep\":2}," +
                    "{\"id\":\"a\",\"type\":\"t\",\"keep\":3}]"
            )
        val d = SaveTestData.decoded(text, SaveReading.STRICT)
        val (a1, _, a2) = d.save.activities
        val changed = d.save.copy(decay = null, activities = listOf(a2.copy(xp = 5.0), a1))
        val out = SaveTestData.parse(SaveTestData.encoded(d.document, changed).text) as JsonObject
        assertFalse("decay" in out)
        val records = (out["activities"] as JsonArray).items.map { it as JsonObject }
        // The first "a" is the first "a" before: each occurrence of an id continues the same one.
        assertEquals(listOf(JsonNumber(1.0), JsonNumber(3.0)), records.map { it["keep"] })
        assertEquals(JsonNumber(5.0), records[0]["xp"])
    }

    @Test
    fun alignmentFollowsIdsThenValuesThenPositions() {
        fun o(id: String, x: Int) =
            JsonObject(mapOf("id" to JsonString(id), "x" to JsonNumber(x.toDouble())))
        val s = { v: String -> JsonString(v) }
        assertEquals(
            listOf(1, null, 0),
            JsonMerge.alignment(
                listOf(o("a", 1), o("b", 2)),
                listOf(o("b", 9), o("c", 1), o("a", 1)),
            ),
        )
        assertEquals(
            listOf(1, 2, null),
            JsonMerge.alignment(listOf(s("x"), s("y"), s("z")), listOf(s("y"), s("z"), s("w"))),
        )
        assertEquals(
            listOf(0, 1),
            JsonMerge.alignment(listOf(s("x"), s("y")), listOf(s("p"), s("q"))),
        )
        assertEquals(listOf(null), JsonMerge.alignment(listOf(s("x"), s("y")), listOf(s("p"))))
    }

    @Test
    fun encodingIsRepeatSafe() {
        val d =
            SaveTestData.decoded(
                saveWith("\"keep\":{\"a\":[1,2]},\"sparks\":4"),
                SaveReading.STRICT,
            )
        val changed = d.save.copy(sparks = 1.0)
        val once = SaveTestData.encoded(d.document, changed)
        assertEquals(once.text, SaveTestData.encoded(d.document, changed).text)
        assertEquals(once.text, SaveTestData.encoded(once.document, changed).text)
        assertTrue(
            EconomySaveCodec.encode(d.document, changed.copy(sparks = Double.NaN))
                is SaveEncoding.Refused
        )
        assertEquals(
            once.text,
            SaveTestData.encoded(d.document, changed).text,
        ) // a refusal changed nothing
    }
}
