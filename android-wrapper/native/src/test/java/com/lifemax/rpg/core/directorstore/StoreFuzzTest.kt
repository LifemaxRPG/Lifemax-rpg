package com.lifemax.rpg.core.directorstore

import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonRead
import com.lifemax.rpg.core.save.JsonReader
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import com.lifemax.rpg.core.save.sameStoredValue
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Seeded random trees and mangled texts through every entry point: nothing throws, the page form
 * reads back as itself, an encoded state decodes to the same values with the same number texts, and
 * a store saves and loads what it was given, whatever the storage does.
 */
class StoreFuzzTest {
    private val random = Random(SEED)

    private val lexemes =
        listOf(
            "0",
            "-0",
            "1",
            "1.0",
            "1e0",
            "1E+2",
            "0.1",
            "1e21",
            "1e-7",
            "5e-324",
            "1e400",
            "-1e400",
            "12345678901234567890",
            "480",
            "1793169000",
        )
    private val keys =
        listOf(
            "a",
            "b",
            "goals",
            "rules",
            "sleep",
            "work",
            "bed",
            "wake",
            "schemaVersion",
            "prefs",
            "0",
            "1",
            "10",
            "4294967295",
            "__proto__",
            "",
            "\u00E9",
            "\uD83D\uDE00",
            "\ud800",
        )
    private val strings =
        listOf(
            "",
            "a",
            "\u00E9",
            "x\ny",
            "\uD83D\uDE00",
            "\ud800",
            "\"\\/",
            "\u2028\u0007",
            "07:00",
            " 7 ",
            "0x1f",
        )

    private fun tree(depth: Int): JsonValue =
        when (random.nextInt(if (depth > 4) 4 else 7)) {
            0 -> listOf(JsonNull, JsonBool.TRUE, JsonBool.FALSE).random(random)
            1 -> lexemes.random(random).let { JsonNumber(it.toDouble(), it) }
            2 -> JsonString(strings.random(random))
            3 -> JsonNumber(random.nextDouble() * 1e6 - 5e5)
            4,
            5 ->
                JsonObject(
                    LinkedHashMap<String, JsonValue>().also { m ->
                        repeat(random.nextInt(5)) { m[keys.random(random)] = tree(depth + 1) }
                    }
                )
            else -> JsonArray(List(random.nextInt(4)) { tree(depth + 1) })
        }

    /** A state the page accepts, with random owned and unowned members. */
    private fun state(): JsonObject {
        val m = LinkedHashMap(StoreSchema.defaultState(StoreOptions.of("p", "UTC", 1.0)).members)
        repeat(1 + random.nextInt(4)) { m[keys.random(random) + random.nextInt(3)] = tree(0) }
        if (random.nextInt(4) == 0) m["goals"] = JsonArray(List(random.nextInt(3)) { tree(1) })
        return JsonObject(m)
    }

    private fun mangle(text: String): String {
        if (text.isEmpty()) return "{"
        val at = random.nextInt(text.length)
        return when (random.nextInt(6)) {
            0 -> text.substring(0, at)
            1 -> text.removeRange(at, at + 1)
            2 ->
                text.substring(0, at) +
                    listOf("\u0000", "\"", "}", ",", "\\", "NaN", "\uFEFF").random(random) +
                    text.substring(at)
            3 -> text.replaceFirst('"', '\'')
            4 -> "[$text"
            else -> text + listOf(",", "}", "x", " ").random(random)
        }
    }

    @Test
    fun threeThousandTreesThroughEveryEntryPoint() {
        var encoded = 0
        repeat(TREES) {
            val value = if (random.nextInt(3) == 0) tree(0) else state()
            val page = PageJson.text(value)
            val read = (JsonReader.read(page) as JsonRead.Parsed).value
            assertEquals("the page form reads back as itself", page, PageJson.text(read))
            assertTrue(read.sameStoredValue(value))
            StoreSchema.validate(value)
            StoreSchema.migrate(value)
            BackupText.read(BackupText.write(value))
            BackupText.read(page)
            val e = DirectorStoreCodec.encode(value)
            if (e is StoreEncode.Encoded) {
                encoded++
                val d = DirectorStoreCodec.decode(e.text) as StoreDecode.Decoded
                assertEquals(e.text, PageJson.preserving(d.state.document))
                assertTrue(d.state.document.sameStoredValue(value))
            }
            DirectorStoreCodec.decode(page, StoreReading.AS_PAGE)
        }
        assertTrue("$encoded of $TREES encoded", encoded > TREES / 3)
    }

    @Test
    fun mangledTextsAreRefusedOrReadNeverThrown() {
        var refused = 0
        repeat(TREES) {
            val text = mangle(PageJson.text(state()))
            when (val d = DirectorStoreCodec.decode(text)) {
                is StoreDecode.Refused -> refused++
                is StoreDecode.Decoded ->
                    assertEquals(
                        text,
                        (DirectorStoreCodec.encode(d.state.document, d.state)
                                as StoreEncode.Encoded)
                            .text,
                    )
            }
            BackupText.read(text)
            val storage = MemoryStorage()
            storage.write(StoreKeys.MAIN, text)
            val loaded = DirectorStore(storage).load(null)
            assertTrue(loaded is LoadOutcome.Loaded || loaded is LoadOutcome.Threw)
        }
        assertTrue("$refused of $TREES refused", refused > TREES / 2)
    }

    @Test
    fun aSavedStateLoadsBackAsTheSameValues() {
        repeat(TREES / 3) {
            val s = state()
            val quota = if (random.nextInt(5) == 0) random.nextLong(100, 3000) else Long.MAX_VALUE
            val storage = MemoryStorage(quota)
            val store = DirectorStore(storage)
            val saved = store.save(s)
            val loaded = DirectorStore(storage).load(null) as LoadOutcome.Loaded
            val room =
                quota - storage.contents.entries.sumOf { (k, v) -> (k.length + v.length).toLong() }
            if (saved is SaveOutcome.Saved && room < StoreKeys.PROBE.length + 1) {
                // LS10: a storage too full for the probe makes the next store memory-only
                assertEquals(LoadSource.MEMORY_ONLY, loaded.report.source)
            } else if (saved is SaveOutcome.Saved) {
                assertEquals(LoadSource.MAIN, loaded.report.source)
                assertTrue(loaded.state.sameStoredValue(s))
                assertEquals(PageJson.text(s).length, saved.units)
            } else assertEquals(SaveRefusal.STORAGE_FULL, (saved as SaveOutcome.NotSaved).reason)
        }
    }

    private companion object {
        const val SEED = 20261111
        const val TREES = 3000
    }
}
