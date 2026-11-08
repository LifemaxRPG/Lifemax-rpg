package com.lifemax.rpg.core.quests

import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The native quest board shows, files and creates quests exactly as the page: every case recorded
 * from the page (`quests-board-golden.json`, families weekday to fuzz) replays through
 * [QuestRules], [QuestBoard], [ClassPacks] and [CustomQuests] with the same return values, the same
 * changes to `quests`, `questDone`, `classId` and `classContent` (as JSON text, key order
 * included), the same messages and the same save requests.
 */
class BoardGoldenTest {
    private val content = (QuestContentLoader.packaged as QuestContentRead.Loaded).content
    private val cases = BoardGoldenReplay.cases

    @Test
    fun everyCaseReplaysExactly() {
        var replayed = 0
        val failures = mutableListOf<String>()
        for (case in cases) {
            BoardGoldenReplay.replay(case, content)?.let { failures += "${id(case)}: $it" }
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
        assertTrue("several hundred cases", cases.size >= 900)
        val ids = cases.map(::id)
        assertEquals("case ids are unique", ids.size, ids.toSet().size)
    }

    /**
     * Every clock the cases cover is a different day or week, so the key rules are really tried.
     */
    @Test
    fun theCasesCoverEveryWeekdayAndPeriodBoundary() {
        val inputs = cases.map { it["input"] as JsonObject }
        val zones = inputs.map { (it["zone"] as JsonString).value }.toSet()
        assertTrue("at least 10 zones: $zones", zones.size >= MIN_ZONES)
        val weekdays = mutableSetOf<Int>()
        val weekKeys = mutableSetOf<String>()
        for (input in inputs) {
            val ctx =
                ((input["calls"] as com.lifemax.rpg.core.save.JsonArray).items.first()
                    as JsonObject)["ctx"]
                    as JsonObject
            weekdays += (ctx["day"] as com.lifemax.rpg.core.save.JsonNumber).value.toInt()
            weekKeys += (ctx["week"] as JsonString).value
        }
        assertEquals("every weekday", (0..6).toSet(), weekdays)
        assertTrue("at least 15 different weeks: ${weekKeys.size}", weekKeys.size >= MIN_WEEKS)
    }

    private fun id(case: JsonObject) = (case["id"] as JsonString).value

    private companion object {
        const val MAX_SHOWN = 15
        const val MIN_ZONES = 10
        const val MIN_WEEKS = 15
        val FAMILIES =
            mapOf(
                "weekday" to 130,
                "key" to 90,
                "stage" to 70,
                "pack" to 160,
                "create" to 80,
                "board" to 185,
                "ledger" to 28,
                "odd" to 24,
                "fuzz" to 215,
            )
    }
}
