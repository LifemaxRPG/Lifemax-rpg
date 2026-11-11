package com.lifemax.rpg.core.directorstore

import com.lifemax.rpg.core.save.JsonKind
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The native-only parts of the store contract (LIFE_DIRECTOR_STORE_SPEC.md LST-27, LST-28, LST-29):
 * the strict reading of owned fields, every refusal, the faithful-write check, and the store over a
 * storage that fails in ways the page's cases cannot show.
 */
class StoreCodecTest {
    private val fresh =
        PageJson.text(StoreSchema.defaultState(StoreOptions.of("p", "UTC", 1793169000.0)))

    private fun with(field: String, raw: String): String = fresh.dropLast(1) + ",\"$field\":$raw}"

    private fun refusal(text: String, reading: StoreReading = StoreReading.STRICT): StoreRefusal? =
        (DirectorStoreCodec.decode(text, reading) as? StoreDecode.Refused)?.refusal

    @Test
    fun theOwnedFieldsAreListed() {
        assertEquals(24, OwnedFields.TOP.size)
        assertEquals(
            listOf("timezone", "sleepRequirementMinutes", "rules", "blackouts"),
            OwnedFields.AVAILABILITY.keys.toList(),
        )
        val defaults = StoreSchema.defaultState(null).members.keys
        assertTrue("every default field is owned", OwnedFields.TOP.keys.containsAll(defaults))
    }

    @Test
    fun strictReadingRefusesAKindThePageNeverWrites() {
        assertEquals(
            StoreRefusal.WrongType("prefs", JsonKind.STRING),
            refusal(with("prefs", "\"x\"")),
        )
        assertEquals(StoreRefusal.WrongType("ui", JsonKind.ARRAY), refusal(with("ui", "[]")))
        assertEquals(
            StoreRefusal.WrongType("updatedEpoch", JsonKind.STRING),
            refusal(with("updatedEpoch", "\"1\"")),
        )
        assertEquals(
            StoreRefusal.WrongType("availability.timezone", JsonKind.NUMBER),
            refusal(fresh.replace("\"timezone\":\"UTC\"", "\"timezone\":5")),
        )
        assertNull("null is where a number may be", refusal(with("updatedEpoch", "null")))
        assertNull(
            "the page's reading accepts it",
            refusal(with("prefs", "\"x\""), StoreReading.AS_PAGE),
        )
        assertNull("unknown fields are anyone's", refusal(with("zUnknown", "[1,{\"a\":\"b\"}]")))
    }

    @Test
    fun everyDecodeRefusalHasItsType() {
        assertTrue(refusal("{") is StoreRefusal.NotJson)
        assertEquals(StoreRefusal.TooDeep, refusal("[".repeat(600) + "]".repeat(600)))
        assertEquals(StoreRefusal.PageWouldThrow("TypeError"), refusal("{\"work\":{}}"))
        assertEquals(
            StoreRefusal.Invalid("wrong schemaVersion 2"),
            refusal(with("schemaVersion", "2")),
        )
        assertEquals(StoreRefusal.Invalid("not an object"), refusal("null"))
        val v0 =
            DirectorStoreCodec.decode("{\"sleep\":{\"bed\":1380,\"wake\":420}}")
                as StoreDecode.Decoded
        assertEquals(0.0, v0.state.migratedFrom!!, 0.0)
        assertEquals("player", v0.state.playerId)
        assertTrue(v0.state.setupComplete)
    }

    @Test
    fun encodingRefusesWhatThePageWouldWriteDifferently() {
        val doc = (DirectorStoreCodec.decode(fresh) as StoreDecode.Decoded).state.document
        fun set(k: String, v: JsonValue) = JsonObject(LinkedHashMap(doc.members).also { it[k] = v })
        assertEquals(
            StoreRefusal.NonFinite("updatedEpoch"),
            (DirectorStoreCodec.encode(set("updatedEpoch", JsonNumber(Double.NaN)))
                    as StoreEncode.Refused)
                .refusal,
        )
        assertEquals(
            StoreRefusal.Invalid("missing goals"),
            (DirectorStoreCodec.encode(set("goals", JsonObject.EMPTY)) as StoreEncode.Refused)
                .refusal,
        )
        assertEquals(
            StoreRefusal.WrongType("playerId", JsonKind.NUMBER),
            (DirectorStoreCodec.encode(set("playerId", JsonNumber(1.0))) as StoreEncode.Refused)
                .refusal,
        )
        assertEquals(
            "a number whose text is not its value is caught on read-back",
            StoreRefusal.NotFaithful("values"),
            (DirectorStoreCodec.encode(set("updatedEpoch", JsonNumber(2.0, "1")))
                    as StoreEncode.Refused)
                .refusal,
        )
        val ok =
            DirectorStoreCodec.encode(set("updatedEpoch", JsonNumber(1.5))) as StoreEncode.Encoded
        assertTrue(ok.text.contains("\"updatedEpoch\":1.5"))
    }

    @Test
    fun anUnchangedStateIsWrittenAsItsOwnTextEvenOutOfThePagesOrder() {
        val text = with("z", "{\"b\":1.0,\"1\":2}")
        val d = (DirectorStoreCodec.decode(text) as StoreDecode.Decoded).state
        assertEquals(text, (DirectorStoreCodec.encode(d.document, d) as StoreEncode.Encoded).text)
        val reordered =
            (DirectorStoreCodec.encode(JsonObject(LinkedHashMap(d.document.members)))
                    as StoreEncode.Encoded)
                .text
        assertTrue(
            "index keys first, number texts kept",
            reordered.endsWith("\"z\":{\"1\":2,\"b\":1.0}}"),
        )
    }

    /** A storage that hands back something else than it was given. */
    private class Forgetful : KeyValueStorage {
        val inner = MemoryStorage()

        override fun read(key: String): StorageRead =
            if (key == StoreKeys.MAIN) StorageRead.Found("{}") else inner.read(key)

        override fun write(key: String, text: String): StorageError? = inner.write(key, text)

        override fun remove(key: String): StorageError? = inner.remove(key)
    }

    @Test
    fun theStoreOverStoragesThatFail() {
        val state = StoreSchema.defaultState(null)
        assertEquals(
            SaveOutcome.NotSaved(SaveRefusal.NOT_VERIFIED, StoreWords.NOT_VERIFIED),
            DirectorStore(Forgetful()).save(state),
        )
        val tiny = MemoryStorage(10)
        val store = DirectorStore(tiny)
        assertFalse(store.available)
        assertEquals(SaveRefusal.UNAVAILABLE, (store.save(state) as SaveOutcome.NotSaved).reason)
        val report = (store.load(null) as LoadOutcome.Loaded).report
        assertEquals(LoadSource.MEMORY_ONLY, report.source)
        assertEquals(StorageError.quotaExceeded(StoreKeys.PROBE).toString(), report.error)
        val memory = MemoryStorage()
        val s = DirectorStore(memory)
        assertEquals(SaveOutcome.Saved(PageJson.text(state).length), s.save(state))
        val changed =
            JsonObject(LinkedHashMap(state.members).also { it["updatedEpoch"] = JsonNumber(5.0) })
        s.save(changed)
        assertEquals(PageJson.text(state), memory.contents[StoreKeys.BACKUP])
        assertEquals(PageJson.text(changed), memory.contents[StoreKeys.MAIN])
        val deep =
            MemoryStorage().also { it.write(StoreKeys.MAIN, "[".repeat(600) + "]".repeat(600)) }
        assertEquals(LoadOutcome.BeyondNative(StoreKeys.MAIN), DirectorStore(deep).load(null))
        assertEquals("nothing quarantined", setOf(StoreKeys.MAIN), deep.contents.keys)
    }

    @Test
    fun javascriptCoercionsAndKeys() {
        val cases =
            mapOf(
                " 7 " to 7.0,
                "0x1f" to 31.0,
                "0b11" to 3.0,
                "0o17" to 15.0,
                "-0x5" to Double.NaN,
                ".5" to 0.5,
                "5." to 5.0,
                "1_000" to Double.NaN,
                "\u00A07\u2028" to 7.0,
                "\uFEFF8" to 8.0,
                "7\u180E" to Double.NaN,
                "" to 0.0,
                "Infinity" to Double.POSITIVE_INFINITY,
                "infinity" to Double.NaN,
                "1e3" to 1000.0,
            )
        for ((s, n) in cases) assertEquals(s, n, JsValues.stringNumber(s), 0.0)
        assertEquals("1,,2", JsValues.text(StoreGolden.parse("[1,null,2]")))
        assertEquals("1e+21", JsValues.text(JsonNumber(1e21)))
        assertEquals("[object Object]", JsValues.text(JsonObject.EMPTY))
        assertTrue(JsValues.isArrayIndex("4294967294"))
        assertFalse(JsValues.isArrayIndex("4294967295"))
        assertFalse(JsValues.isArrayIndex("01"))
        assertEquals(
            "lifemax_director_quarantine_lifemax_director_state_v1_bak",
            StoreKeys.quarantineKey(StoreKeys.BACKUP),
        )
        assertEquals(
            "x",
            (StoreSchema.migrate(StoreGolden.parse("{\"work\":[\"x\"]}")) as Migration.Done)
                .value
                .let {
                    val rules =
                        ((it as JsonObject)["availability"] as JsonObject)["rules"].toString()
                    (StoreGolden.parse(rules) as com.lifemax.rpg.core.save.JsonArray)
                        .items
                        .first()
                        .let { r -> ((r as JsonObject)["0"] as JsonString).value }
                },
        )
    }
}
