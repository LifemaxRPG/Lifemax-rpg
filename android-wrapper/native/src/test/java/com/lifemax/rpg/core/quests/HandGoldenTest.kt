package com.lifemax.rpg.core.quests

import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The native Daily Hand deals exactly as the page: every case recorded from the page
 * (`quests-golden.json`, families seed to fuzz) replays through [HandDealer] with the same return
 * values, the same changes to `quests`, `dailyDraw`, `dhMig`, `sparks` and `questDone` (as JSON
 * text, key order included), the same messages and the same save requests.
 */
class HandGoldenTest {
    private val content = (QuestContentLoader.packaged as QuestContentRead.Loaded).content
    private val cases = HandGoldenReplay.cases

    @Test
    fun everyCaseReplaysExactly() {
        val dealer = HandDealer(content)
        var replayed = 0
        val failures = mutableListOf<String>()
        for (case in cases) {
            HandGoldenReplay.replay(case, dealer, content)?.let { failures += "${id(case)}: $it" }
            replayed++
        }
        assertEquals("every case is replayed, none skipped", cases.size, replayed)
        assertTrue(
            "${failures.size} of ${cases.size} cases differ from the page:\n" +
                failures.take(MAX_SHOWN).joinToString("\n"),
            failures.isEmpty(),
        )
    }

    /** The file holds every family, in at least the numbers it was written with. */
    @Test
    fun theFileHoldsEveryFamily() {
        val counts = cases.groupingBy { (it["family"] as JsonString).value }.eachCount()
        for ((family, least) in FAMILIES) assertTrue(
            "$family has ${counts[family] ?: 0} cases, fewer than $least",
            (counts[family] ?: 0) >= least,
        )
        assertEquals("no family the test does not know", FAMILIES.keys, counts.keys)
        assertEquals(cases.size, counts.values.sum())
        assertTrue("several hundred cases", cases.size >= 800)
        val ids = cases.map(::id)
        assertEquals("case ids are unique", ids.size, ids.toSet().size)
    }

    private fun id(case: JsonObject) = (case["id"] as JsonString).value

    private companion object {
        const val MAX_SHOWN = 15
        val FAMILIES =
            mapOf(
                "seed" to 25,
                "rng" to 30,
                "quest" to 8,
                "recent" to 70,
                "draw" to 100,
                "ensure" to 45,
                "reroll" to 60,
                "odd" to 40,
                "fuzz" to 400,
            )
    }
}
