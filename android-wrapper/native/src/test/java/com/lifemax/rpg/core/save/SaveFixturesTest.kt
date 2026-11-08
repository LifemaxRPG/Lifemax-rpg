package com.lifemax.rpg.core.save

import com.lifemax.rpg.core.golden.GoldenFiles
import com.lifemax.rpg.core.save.SaveTestData.at
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The codec on real saves: the golden saves of tests/fixtures/saves, and what the page itself
 * stores (tests/fixtures/codec-roundtrip.json, recorded from the page's own `JSON.stringify`).
 */
class SaveFixturesTest {
    private val roundTrip by lazy {
        SaveTestData.parse(GoldenFiles.text("codec-roundtrip.json")) as JsonObject
    }

    @Test
    fun goldenSavesRoundTripStrictly() {
        assertEquals(4, SaveTestData.fixtureSaves.size)
        for ((name, export) in SaveTestData.fixtureSaves) {
            val state = export.at("state") as JsonObject
            assertTrue("$name has a ledger", state["activities"] is JsonArray)
            SaveTestData.checkRoundTrip(state, SaveReading.STRICT)
            SaveTestData.checkRoundTrip(state, SaveReading.AS_PAGE)
            // The whole export as a document: its own keys (app, version, rules, extras) are kept.
            SaveTestData.checkRoundTrip(export, SaveReading.STRICT)
        }
    }

    @Test
    fun theFixtureFileItselfReadsAndWritesBackExactly() {
        for (name in SaveTestData.fixtureSaves.keys) {
            val text = GoldenFiles.text("saves/$name.json")
            val decoded = SaveTestData.decoded(text, SaveReading.STRICT)
            assertEquals(text, SaveTestData.encoded(decoded.document, decoded.save).text)
        }
    }

    /** Every number the page wrote, rewritten from its value alone, is the page's text. */
    @Test
    fun numbersAreWrittenAsThePageWritesThem() {
        val numbers = (roundTrip["numbers"] as JsonArray).items
        assertTrue(numbers.size > 400)
        for (entry in numbers) {
            val (bits, text) = (entry as JsonArray).items.map { (it as JsonString).value }
            val value =
                java.lang.Long.parseUnsignedLong(bits, 16).let(java.lang.Double::longBitsToDouble)
            assertEquals("bits $bits", text, jsNumberText(value))
            if (value.isFinite()) {
                val read = (SaveTestData.parse(text) as JsonNumber).value
                assertEquals(
                    "bits $bits read back",
                    (if (value == 0.0) 0.0 else value).toBits(),
                    read.toBits(),
                )
            }
        }
    }

    /** Every string, written by the codec, is the page's text, and reads back unit for unit. */
    @Test
    fun stringsAreWrittenAsThePageWritesThem() {
        val strings = (roundTrip["strings"] as JsonArray).items
        assertTrue(strings.size > 100)
        for (entry in strings) {
            val (units, text) = (entry as JsonArray).items
            val value =
                String(
                    (units as JsonArray)
                        .items
                        .map { (it as JsonNumber).value.toInt().toChar() }
                        .toCharArray()
                )
            val expected = (text as JsonString).value
            assertEquals(expected, JsonWriter.write(JsonString(value)))
            assertEquals(JsonString(value), SaveTestData.parse(expected))
        }
    }

    /**
     * What the page stores is read and written back byte for byte, numbers both from their text and
     * from their value alone; and the full round trip holds on it.
     */
    @Test
    fun pageStoredSavesRoundTripByteForByte() {
        val saves = (roundTrip["saves"] as JsonArray).items.map { it as JsonObject }
        assertEquals(
            listOf("new", "extras", "probe"),
            saves.map { (it["name"] as JsonString).value },
        )
        for (entry in saves) {
            val stored = (entry["stored"] as JsonString).value
            val parsed = SaveTestData.parse(stored) as JsonObject
            assertEquals(stored, JsonWriter.write(parsed))
            assertEquals(stored, JsonWriter.write(withoutLexemes(parsed)))
            SaveTestData.checkRoundTrip(parsed, SaveReading.STRICT)
        }
    }

    private fun withoutLexemes(v: JsonValue): JsonValue =
        when (v) {
            is JsonNumber -> JsonNumber(v.value)
            is JsonObject ->
                JsonObject(v.members.mapValuesTo(LinkedHashMap()) { withoutLexemes(it.value) })
            is JsonArray -> JsonArray(v.items.map(::withoutLexemes))
            else -> v
        }
}
