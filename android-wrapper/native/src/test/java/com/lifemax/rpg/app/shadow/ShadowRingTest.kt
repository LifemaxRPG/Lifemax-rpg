package com.lifemax.rpg.app.shadow

import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import com.lifemax.rpg.core.shadow.FakeShadowPage
import com.lifemax.rpg.core.shadow.ShadowKeys
import com.lifemax.rpg.core.shadow.ShadowLedger
import com.lifemax.rpg.core.shadow.ShadowLedgerJson
import com.lifemax.rpg.core.shadow.ShadowLimits
import com.lifemax.rpg.core.shadow.ShadowSessionTest
import com.lifemax.rpg.core.shadow.ShadowState
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ring (ECONOMY_SHADOW_PLAN 3.1, 3.2): at most 200 entries and 128 KB, written atomically and
 * read back, a damaged file replaced, and never a word of the owner's: every real fixture save is
 * replayed through the shadow with actions that touch owner-keyed fields, and the ring file is
 * searched for every string those saves hold.
 */
class ShadowRingTest {
    private val dir: File = Files.createTempDirectory("ring").toFile()

    @After
    fun removeTheFolder() {
        dir.deleteRecursively()
    }

    @Test
    fun theRingKeepsTwoHundredEntriesAndFitsItsSize() {
        val ledger = ShadowLedger(0, ByteArray(ShadowLimits.SALT_BYTES))
        repeat(450) {
            ledger.touched(
                "save.other",
                List(12) { p -> "skillXP.*#%06x".format(it * 13 + p) },
                "b",
                it * 60_000L,
            )
        }
        assertEquals(ShadowLimits.RING_MAX_ENTRIES, ledger.entries().size)
        assertEquals("t-251", ledger.entries().first().id)
        val text = ledger.fit()
        assertTrue(text.toByteArray().size <= ShadowLimits.RING_MAX_BYTES)
        val ring = ShadowRing(dir)
        assertTrue(ring.store(text))
        val back = ring.load()!!
        assertEquals(ShadowLedgerJson.write(back), text)
        assertFalse(File(ring.file.parentFile, "ring.json.tmp").exists())
    }

    @Test
    fun aDamagedOrForeignFileReadsAsNoRing() {
        val ring = ShadowRing(dir)
        ring.file.parentFile.mkdirs()
        for (t in
            listOf(
                "",
                "{",
                "{\"v\":2}",
                "[]",
                ShadowLedgerJson.write(ShadowLedger(0, ByteArray(16)))
                    .replace("\"salt\":\"", "\"salt\":\"zz"),
            )) {
            ring.file.writeText(t)
            assertNull(t, ring.load())
        }
        assertFalse(ring.store("x".repeat(ShadowLimits.RING_MAX_BYTES + 1)))
    }

    @Test
    fun aCrashBetweenTheTemporaryFileAndTheRenameLeavesTheLastRing() {
        val ring = ShadowRing(dir)
        val ledger = ring.fresh(ShadowSessionTest.T).apply { note("base") }
        assertTrue(ring.store(ShadowLedgerJson.write(ledger)))
        // the app died after writing the temporary file and before the rename: half a new ring
        val temp = File(ring.file.parentFile, ring.file.name + ".tmp")
        temp.writeText(ShadowLedgerJson.write(ledger.apply { note("base") }).take(40))
        assertEquals(1, ring.load()!!.info["base"])
        // the next store replaces the leftover and the ring
        assertTrue(ring.store(ShadowLedgerJson.write(ledger)))
        assertEquals(2, ring.load()!!.info["base"])
        assertFalse(temp.exists())
    }

    @Test
    fun clearLeavesAnEmptyRingOnDiskWithTheSaltAndState() {
        val ring = ShadowRing(dir)
        val session = ShadowSessionTest.session()
        session.configure(ShadowSessionTest.configure(ShadowSessionTest.FP), false)
        val page = FakeShadowPage()
        val start = mapOf("sparks" to ShadowSessionTest.num(1))
        page.base(start).forEach { session.base(it, 0) }
        session.observe(
            page.envelope("save.other", start, start + ("sparks" to ShadowSessionTest.num(2))),
            ShadowSessionTest.T,
        )
        session.ledger.disabled("slow", ShadowSessionTest.T)
        assertTrue(ring.store(session.ringText()))
        assertTrue(ring.load()!!.seen > 0)
        session.clear()
        assertTrue(ring.store(session.ringText()))
        val stored = ring.load()!!
        assertEquals(0, stored.seen)
        assertTrue(stored.kinds.isEmpty() && stored.info.isEmpty() && stored.codec.isEmpty())
        assertTrue(stored.entries().isEmpty() && stored.mismatches().isEmpty())
        assertTrue(stored.disables().isEmpty())
        assertEquals(ShadowState.ON, stored.state)
        assertTrue(session.ledger.salt.contentEquals(stored.salt))
    }

    @Test
    fun noStringFromAnyFixtureSaveReachesTheRingFile() {
        for (name in listOf("established", "extras", "legacy", "new")) {
            val save =
                FakeShadowPage.members(
                    FakeShadowPage.members(FakeShadowPage.fixture("saves/$name.json"))["state"]
                )
            // the app's own words may show (attribute ids; a field name such as customRules holds
            // "custom")
            val words =
                personal(JsonObject(save), owner = false)
                    .filter { it.length >= MIN_WORD && it !in ShadowKeys.FIXED_MEMBERS }
                    .filterNot { w -> ShadowKeys.SHADOW.any { it.contains(w) } }
                    .toSet()
            val session = ShadowSessionTest.session()
            session.configure(ShadowSessionTest.configure(ShadowSessionTest.FP), false)
            val page = FakeShadowPage()
            page.base(save).forEach { assertTrue(session.base(it, 0).ok) }
            var now = save
            // actions that touch owner-keyed fields: every skill, every reflection day, every
            // record's name
            for ((i, key) in
                listOf("skillXP", "dayReflections", "questDone", "customRules", "actXP")
                    .withIndex()) {
                val o = now[key] as? JsonObject ?: continue
                val next =
                    now +
                        (key to
                            JsonObject(
                                o.members.mapValues { (_, v) ->
                                    if (v is JsonString) JsonString(v.value + "!")
                                    else ShadowSessionTest.num(i + 1)
                                }
                            ))
                session.observe(page.envelope("save.other", now, next), ShadowSessionTest.T)
                now = next
            }
            val acts = now["activities"] as? JsonArray
            if (acts != null && acts.items.isNotEmpty()) {
                val renamed =
                    acts.items.map { a ->
                        if (a is JsonObject)
                            JsonObject(a.members + ("name" to JsonString("renamed")))
                        else a
                    }
                val next = now + ("activities" to JsonArray(renamed + acts.items.first()))
                session.observe(page.envelope("log.record", now, next), ShadowSessionTest.T)
            }
            assertTrue(name, session.ledger.applied > 0)
            val ring = ShadowRing(File(dir, name))
            assertTrue(ring.store(session.ringText()))
            val file = ring.file.readText()
            val leaked = words.filter { file.contains(it) }
            assertTrue("$name leaks $leaked", leaked.isEmpty())
            // the copied report is built from the same redacted ledger (WP6e)
            val report = session.report(ShadowSessionTest.T, java.time.ZoneOffset.UTC)
            val told = words.filter { report.contains(it) }
            assertTrue("$name's report leaks $told", told.isEmpty())
            assertTrue(name, words.size > 3)
        }
    }

    /**
     * The owner's words in a save: what they named or wrote (names, notes, titles, reflections,
     * answers) and the keys of the maps they keyed (skills, rules, reflection days).
     */
    private fun personal(v: JsonValue, owner: Boolean, ownedKeys: Boolean = false): List<String> =
        when (v) {
            is JsonString -> if (owner) listOf(v.value) else emptyList()
            is JsonArray -> v.items.flatMap { personal(it, owner) }
            is JsonObject ->
                v.members.flatMap { (k, x) ->
                    (if (owner || ownedKeys) listOf(k) else emptyList()) +
                        personal(x, owner || k in OWNER_TEXT, k in OWNER_KEYS)
                }
            else -> emptyList()
        }

    private companion object {
        /**
         * Short strings (ids like "q1", "on") are the app's own words or too short to be personal.
         */
        const val MIN_WORD = 5

        /** Keys whose values are the owner's text. */
        val OWNER_TEXT =
            setOf(
                "name",
                "notes",
                "note",
                "title",
                "desc",
                "text",
                "answer",
                "reflection",
                "label",
                "dayReflections",
            )

        /** Maps keyed by the owner's own names. */
        val OWNER_KEYS = setOf("skillXP", "customRules", "actXP")
    }
}
