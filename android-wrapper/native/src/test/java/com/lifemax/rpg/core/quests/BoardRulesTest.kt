package com.lifemax.rpg.core.quests

import com.lifemax.rpg.core.economy.EconomyClock
import com.lifemax.rpg.core.economy.jsNumber
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonWriter
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the board's constants and a few hand-checked values, and proves the golden cases would
 * notice a wrong rule: each mutation below (one number, one name, one piece of content, one clock
 * reading) must make at least one recorded case fail.
 */
class BoardRulesTest {
    private val content = (QuestContentLoader.packaged as QuestContentRead.Loaded).content
    private val cases = BoardGoldenReplay.cases

    @Test
    fun theTuningIsThePagesAndAgreesWithTheContent() {
        assertEquals(
            content.stageLevels,
            BoardTuning.PAGE.stageLevels.map { if (it == 0) 1 else it },
        )
        assertEquals(1.0, BoardTuning.PAGE.minXp, 0.0)
        assertEquals(500.0, BoardTuning.PAGE.maxXp, 0.0)
        assertEquals(25.0, BoardTuning.PAGE.defaultXp, 0.0)
        assertEquals(
            listOf("sun", "mon", "tue", "wed", "thu", "fri", "sat"),
            BoardTuning.PAGE.weekdays,
        )
    }

    @Test
    fun theCharacterLevelComesFromTheSaveAsThePageComputesIt() {
        fun level(xp: Double) =
            CharacterLevel.of(JsonReaderHelper.save("""{"attrXP":{"physique":$xp,"social":0}}"""))
        assertEquals(1, level(0.0))
        assertEquals(1, level(199.0))
        assertEquals(2, level(200.0))
        assertEquals(2, level(474.0))
        assertEquals(3, level(475.0))
        assertEquals(12, level(Levels12.CUMULATIVE))
        assertEquals(null, CharacterLevel.of(JsonReaderHelper.save("""{"attrXP":{"a":"5"}}""")))
        assertEquals(null, CharacterLevel.of(JsonReaderHelper.save("""{}""")))
    }

    @Test
    fun localeNumbersAreFormattedAsTheEnglishPageDoes() {
        val vectors =
            mapOf(
                0.0 to "0",
                40.0 to "40",
                999.0 to "999",
                1000.0 to "1,000",
                1234.5 to "1,234.5",
                1234.5675 to "1,234.568",
                0.0005 to "0.001",
                0.0004 to "0",
                0.30000000000000004 to "0.3",
                1e21 to "1,000,000,000,000,000,000,000",
                123456789.0 to "123,456,789",
                Double.POSITIVE_INFINITY to "∞",
            )
        for ((value, text) in vectors) assertEquals("$value", text, QuestBoard.localeNumber(value))
    }

    @Test
    fun javaScriptTextRulesAreKept() {
        assertEquals("a b", Js.trim(" ﻿ a b  　"))
        assertEquals("\u001ca\u001c", Js.trim("\u001ca\u001c"))
        assertEquals(7.0, jsNumber(" 7﻿"), 0.0)
        assertTrue(jsNumber("\u001c7").isNaN())
        assertTrue(jsNumber("0b12").isNaN())
        assertTrue(jsNumber("0o9").isNaN())
        assertEquals(5.0, jsNumber("0b101"), 0.0)
        assertEquals(31.0, jsNumber("0x1F"), 0.0)
    }

    @Test
    fun theStageTableIsReadAsAnArrayIsIndexed() {
        val rules = QuestRules()
        fun lvl(json: String) = rules.stageLvl(JsonReaderHelper.value(json))
        assertEquals(listOf(1, 1, 3, 5, 8, 12), (0..5).map { lvl("$it") })
        assertEquals(1, lvl("6"))
        assertEquals(5, lvl("\"3\""))
        assertEquals(5, lvl("[3]"))
        assertEquals(6, lvl("\"length\""))
        assertEquals(1, lvl("\"03\""))
        assertEquals(1, lvl("2.5"))
        assertEquals(1, lvl("null"))
        assertEquals(1, rules.stageLvl(null))
    }

    // ---- mutations: the golden cases must notice each one ----

    private fun failures(
        tuning: BoardTuning = BoardTuning.PAGE,
        content: QuestContent = this.content,
        clockFor: (EconomyClock) -> BoardClock = BoardClock::of,
    ): Int =
        cases.count {
            BoardGoldenReplay.replay(it, content, tuning, clockFor, checkClock = false) != null
        }

    @Test
    fun theUnchangedRulesFailNoCase() {
        assertEquals(0, failures())
    }

    /** The clock the board derives from the device clock is the page's, for every recorded call. */
    @Test
    fun theClockAdapterGivesThePagesDayWeekdayAndWeek() {
        val bad =
            cases.mapNotNull { BoardGoldenReplay.replay(it, content)?.takeIf { m -> "clock" in m } }
        assertTrue(bad.take(3).toString(), bad.isEmpty())
    }

    @Test
    fun everyMutationIsNoticed() {
        val page = BoardTuning.PAGE
        val levels = page.stageLevels
        val classes = content.classQuests
        val athlete = classes.getValue("athlete")
        // the pack keeps only the quests that are not dailies (QST-9), so mutate those
        val kept =
            athlete.indices.filter {
                athlete[it].cadence != "daily" ||
                    athlete[it].kind in setOf("nutrition", "hydration")
            }
        val mutations: Map<String, () -> Int> =
            mapOf(
                "last stage unlocks at 13" to
                    {
                        failures(page.copy(stageLevels = levels.dropLast(1) + 13))
                    },
                "stage 3 unlocks at 4" to
                    {
                        failures(page.copy(stageLevels = levels.toMutableList().also { it[2] = 4 }))
                    },
                "stage 1 unlocks at 2" to
                    {
                        failures(page.copy(stageLevels = levels.toMutableList().also { it[1] = 2 }))
                    },
                "least XP 0" to { failures(page.copy(minXp = 0.0)) },
                "most XP 499" to { failures(page.copy(maxXp = 499.0)) },
                "default XP 24" to { failures(page.copy(defaultXp = 24.0)) },
                "Monday and Sunday swapped" to
                    {
                        failures(
                            page.copy(
                                weekdays =
                                    page.weekdays.toMutableList().also {
                                        it[0] = "mon"
                                        it[1] = "sun"
                                    }
                            )
                        )
                    },
                "a class quest's XP changed" to
                    {
                        val changed = athlete.toMutableList()
                        changed[kept[0]] = changed[kept[0]].copy(xp = changed[kept[0]].xp + 1)
                        failures(
                            content = content.copy(classQuests = classes + ("athlete" to changed))
                        )
                    },
                "two class quests swapped" to
                    {
                        val swapped = athlete.toMutableList()
                        swapped[kept[0]] = athlete[kept[1]]
                        swapped[kept[1]] = athlete[kept[0]]
                        failures(
                            content = content.copy(classQuests = classes + ("athlete" to swapped))
                        )
                    },
                "a default quest renamed" to
                    {
                        val renamed = content.defaultQuests.toMutableList()
                        renamed[3] = renamed[3].copy(name = renamed[3].name + "!")
                        failures(content = content.copy(defaultQuests = renamed))
                    },
                "a default quest's cadence changed" to
                    {
                        val changed = content.defaultQuests.toMutableList()
                        changed[4] = changed[4].copy(cadence = "epic")
                        failures(content = content.copy(defaultQuests = changed))
                    },
                "the week starts on Sunday" to
                    {
                        failures(
                            clockFor = { c ->
                                BoardClock.of(c).let { b ->
                                    b.copy(
                                        weekKey =
                                            java.time.LocalDate.parse(b.today)
                                                .minusDays(b.weekday.toLong())
                                                .toString()
                                    )
                                }
                            }
                        )
                    },
                "the week key is today" to
                    {
                        failures(
                            clockFor = { c -> BoardClock.of(c).let { it.copy(weekKey = it.today) } }
                        )
                    },
                "the day is the UTC day" to
                    {
                        failures(
                            clockFor = { c -> BoardClock.of(EconomyClock(c.now, ZoneOffset.UTC)) }
                        )
                    },
                "the weekday is a day late" to
                    {
                        failures(
                            clockFor = { c ->
                                BoardClock.of(c).let { it.copy(weekday = (it.weekday + 1) % 7) }
                            }
                        )
                    },
            )
        val missed = mutations.filter { it.value() == 0 }.keys
        assertTrue("no case notices: $missed", missed.isEmpty())
    }

    /** The recorded outputs hold what the mutation tests lean on: both answers to each question. */
    @Test
    fun theCasesHoldBothAnswersToEveryQuestion() {
        val answers = mutableMapOf<String, MutableSet<String>>()
        for (case in cases) {
            val input = case["input"] as JsonObject
            val calls = (input["calls"] as com.lifemax.rpg.core.save.JsonArray).items
            val outs =
                ((case["output"] as JsonObject)["calls"] as com.lifemax.rpg.core.save.JsonArray)
                    .items
            calls.zip(outs).forEach { (call, out) ->
                val fn = ((call as JsonObject)["fn"] as JsonString).value
                val returns = (out as JsonObject)["returns"]
                if (fn in setOf("questToday", "isQuestDone", "questLocked"))
                    answers.getOrPut(fn) { mutableSetOf() }.add(JsonWriter.write(returns!!))
                if (fn == "stageLvl")
                    answers
                        .getOrPut(fn) { mutableSetOf() }
                        .add(((returns as? JsonNumber)?.value ?: -1.0).toString())
            }
        }
        for (fn in listOf("questToday", "isQuestDone", "questLocked")) assertTrue(
            "$fn answers both ways: ${answers[fn]}",
            answers.getValue(fn).containsAll(listOf("true", "false")),
        )
        assertTrue(
            "stageLvl gives 1, 3, 5, 8, 12 and 6: ${answers["stageLvl"]}",
            answers
                .getValue("stageLvl")
                .containsAll(listOf("1.0", "3.0", "5.0", "8.0", "12.0", "6.0")),
        )
    }
}

private object Levels12 {
    /** The XP that reaches character level 12 (CURVE-4). */
    const val CUMULATIVE = 37.5 * 12 * 12 + 87.5 * 12 - 125
}

private object JsonReaderHelper {
    fun value(text: String) =
        (com.lifemax.rpg.core.save.JsonReader.read(text)
                as com.lifemax.rpg.core.save.JsonRead.Parsed)
            .value

    fun save(text: String) = value(text) as JsonObject
}
