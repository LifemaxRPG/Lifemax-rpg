package com.lifemax.rpg.core.directorstore

import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonRead
import com.lifemax.rpg.core.save.JsonReader
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import com.lifemax.rpg.core.save.sameStoredValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round trips on every text in the store golden file (the seeds the page wrote, every stored item,
 * every state argument, every state the page gave back): the page's own texts are in the page's
 * normal form, decode then encode gives the stored text back byte for byte, and a change to one
 * owned field changes nothing else, number texts included.
 */
class StoreRoundTripTest {
    private fun fixtureTexts(): Pair<Map<String, String>, Set<String>> {
        val texts = LinkedHashSet<String>()
        var seeds: Map<String, String> = emptyMap()
        StoreGolden.read { refs, c ->
            seeds = refs.seeds
            if (c.family == "browser" || c.family == "depth") return@read
            collect(c.args, refs, texts)
            collect(c.out, refs, texts)
        }
        return seeds to texts
    }

    /** Every string that is a text argument or a recorded text, resolved. */
    private fun collect(v: JsonValue, refs: StoreGolden.Refs, into: MutableSet<String>) {
        when (v) {
            is JsonString -> into += v.value
            is JsonArray -> v.items.forEach { collect(it, refs, into) }
            is JsonObject ->
                if ("seed" in v || "grow" in v) into += refs.text(v)
                else v.members.values.forEach { collect(it, refs, into) }
            else -> Unit
        }
    }

    @Test
    fun thePagesTextsAreInThePagesNormalForm() {
        val (seeds, _) = fixtureTexts()
        assertEquals(8, seeds.size)
        for ((name, text) in seeds) {
            val value = (JsonReader.read(text) as JsonRead.Parsed).value
            if (name == "lived-export") assertEquals(name, text, PageJson.indented(value))
            else {
                assertEquals(name, text, PageJson.text(value))
                assertEquals(name, text, PageJson.preserving(value))
            }
        }
    }

    @Test
    fun decodeThenEncodeIsTheIdentityOnEveryFixtureText() {
        val (_, texts) = fixtureTexts()
        var decoded = 0
        var normal = 0
        for (text in texts) {
            val read = JsonReader.read(text)
            if (read is JsonRead.Parsed && PageJson.text(read.value) == text) {
                normal++
                assertEquals(
                    "a page-normal text keeps its number texts",
                    text,
                    PageJson.preserving(read.value),
                )
            }
            val d =
                DirectorStoreCodec.decode(text, StoreReading.AS_PAGE) as? StoreDecode.Decoded
                    ?: continue
            decoded++
            val again = DirectorStoreCodec.encode(d.state.document, d.state)
            assertTrue(text.take(80), again is StoreEncode.Encoded && again.text == text)
            val fresh = DirectorStoreCodec.encode(d.state.document)
            if (fresh is StoreEncode.Encoded) {
                assertTrue(fresh.document.sameStoredValue(d.state.document))
                assertEquals(PageJson.preserving(d.state.document), fresh.text)
            } else {
                val refusal = (fresh as StoreEncode.Refused).refusal
                assertTrue(
                    "$refusal",
                    refusal is StoreRefusal.NonFinite || refusal is StoreRefusal.WrongType,
                )
            }
        }
        assertTrue("$decoded states decoded", decoded > 1500)
        assertTrue("$normal page-normal texts", normal > 1500)
    }

    @Test
    fun aChangedFieldChangesNothingElse() {
        val (_, texts) = fixtureTexts()
        var changed = 0
        for (text in texts) {
            val d = DirectorStoreCodec.decode(text) as? StoreDecode.Decoded ?: continue
            val doc = d.state.document
            val members = LinkedHashMap(doc.members)
            members["updatedEpoch"] = JsonNumber(1_800_000_000.0)
            val out = DirectorStoreCodec.encode(JsonObject(members), d.state)
            if (out !is StoreEncode.Encoded) continue
            changed++
            val back = (DirectorStoreCodec.decode(out.text) as StoreDecode.Decoded).state.document
            for ((k, v) in doc.members) if (k != "updatedEpoch")
                assertEquals(k, PageJson.preserving(v), PageJson.preserving(back[k]!!))
            assertEquals(1_800_000_000.0, (back["updatedEpoch"] as JsonNumber).value, 0.0)
        }
        assertTrue("$changed states changed", changed > 1000)
    }
}
