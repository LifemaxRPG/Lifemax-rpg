package com.lifemax.rpg.core.save

import com.lifemax.rpg.core.economy.EconomySave
import com.lifemax.rpg.core.golden.GoldenFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.fail

/** Saves to test the codec on, read in place from lifemax_modular_v73/tests/fixtures. */
internal object SaveTestData {
    /** The economy golden cases, each parsed by the production reader from its own line. */
    val goldenCases: List<JsonObject> by lazy {
        GoldenFiles.text("economy-golden.json")
            .lines()
            .map { it.trimStart('[').trimEnd(',') }
            .filter { it.startsWith("{\"id\"") }
            .map { parse(it) as JsonObject }
    }

    /** The fresh save every golden case's state is written against (the first case's). */
    val fresh: JsonObject by lazy { goldenCases.first().at("input", "state") as JsonObject }

    /**
     * The state a golden case starts from: its fields laid over the fresh save, as the test does.
     */
    fun startOf(case: JsonObject): JsonObject {
        val state = case.at("input", "state") as? JsonObject ?: return fresh
        return JsonObject(LinkedHashMap(fresh.members).apply { putAll(state.members) })
    }

    /** The saves a golden case hands load() (`env.stored`), if any. */
    fun storedOf(case: JsonObject): List<JsonObject> =
        (case.at("input", "calls") as? JsonArray)?.items.orEmpty().mapNotNull {
            (it as JsonObject).at("env", "stored") as? JsonObject
        }

    /** The golden saves of tests/fixtures/saves (exports: the save is their `state`). */
    val fixtureSaves: Map<String, JsonObject> by lazy {
        listOf("new", "established", "legacy", "extras").associateWith {
            parse(GoldenFiles.text("saves/$it.json")) as JsonObject
        }
    }

    fun parse(text: String): JsonValue =
        when (val r = JsonReader.read(text)) {
            is JsonRead.Parsed -> r.value
            is JsonRead.Malformed -> error("not JSON: ${r.reason} at ${r.offset}")
        }

    fun JsonValue?.at(vararg keys: String): JsonValue? =
        keys.fold(this) { v, k -> (v as? JsonObject)?.get(k) }

    fun decoded(text: String, reading: SaveReading): SaveDecoding.Decoded =
        when (val d = EconomySaveCodec.decode(text, reading)) {
            is SaveDecoding.Decoded -> d
            is SaveDecoding.Refused -> fail("refused: ${d.refusal}") as Nothing
        }

    fun decoded(json: JsonValue, reading: SaveReading): SaveDecoding.Decoded =
        when (val d = EconomySaveCodec.decode(json, reading)) {
            is SaveDecoding.Decoded -> d
            is SaveDecoding.Refused -> fail("refused: ${d.refusal}") as Nothing
        }

    fun encoded(document: SaveDocument, save: EconomySave): SaveEncoding.Encoded =
        when (val e = EconomySaveCodec.encode(document, save)) {
            is SaveEncoding.Encoded -> e
            is SaveEncoding.Refused -> fail("refused: ${e.refusal}") as Nothing
        }

    /**
     * The full round-trip contract on one stored save: unchanged it comes back byte for byte (from
     * text and from JSON), decoding is repeatable, and a change to every kind of tracked field
     * keeps every untracked value exactly.
     */
    fun checkRoundTrip(stored: JsonObject, reading: SaveReading) {
        val text = JsonWriter.write(stored)
        val first = decoded(text, reading)
        assertEquals(
            "unchanged, the stored text comes back",
            text,
            encoded(first.document, first.save).text,
        )
        val fromJson = decoded(stored, reading)
        val rewritten = encoded(fromJson.document, fromJson.save)
        assertEquals("unchanged, written from JSON", text, rewritten.text)
        assertEquals("decoding is repeatable", first.save, decoded(text, reading).save)
        assertEquals(first.save, fromJson.save)
        assertEquals(first.document.written, decoded(text, reading).document.written)

        val changed = SaveChanges.everything(first.save)
        val out = encoded(first.document, changed)
        val back = decoded(out.text, reading)
        assertEquals(
            "the economy reads back as written",
            EconomySaveWriter.write(changed).toString(),
            back.document.written.toString(),
        )
        UntouchedValues.check(
            stored,
            first.document.written,
            EconomySaveWriter.write(changed),
            back.document.stored,
        )
        assertEquals("encoding is repeatable", out.text, encoded(first.document, changed).text)
        assertEquals(
            "a second encode on its own output changes nothing",
            out.text,
            encoded(back.document, back.save).text,
        )
    }
}
