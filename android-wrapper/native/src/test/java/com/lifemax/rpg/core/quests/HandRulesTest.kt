package com.lifemax.rpg.core.quests

import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The constants the page keeps in code (tier odds, luck, slot order, migration pattern, hashing),
 * pinned; and mutation spot checks: change one of them, or one piece of the content, and the golden
 * cases must notice.
 */
class HandRulesTest {
    private val content = (QuestContentLoader.packaged as QuestContentRead.Loaded).content

    @Test
    fun theOddsAreThePagesNumbers() {
        val o = HandOdds.PAGE
        assertEquals(
            listOf(5500.0, 1000.0, 8999.0),
            listOf(o.luckFallback, o.luckFloor, o.luckSpan),
        )
        assertEquals(
            listOf(1.2, 0.4, 1.0, 0.5, 1.0, 0.3),
            listOf(o.tier1, o.tier1Luck, o.tier2, o.tier3, o.tier3Luck, o.maintenanceTier3),
        )
        assertEquals(3, HandOdds.TOP_TIER)
        // luck 1 (score 9999): tier 1 at 0.8, tier 3 at 1.5 (0.45 on the Maintenance card)
        val luck = o.luck(9999.0)
        assertEquals(1.0, luck, 0.0)
        assertEquals(1.2 - 1.0 * 0.4, o.weight(1, luck, HandSlot.WILDCARD), 0.0)
        assertEquals(1.5 * 0.3, o.weight(3, luck, HandSlot.MAINTENANCE), 0.0)
        assertEquals(1.5, o.weight(3, luck, null), 0.0)
    }

    @Test
    fun slotsAndIdsAreThePagesOrderAndShape() {
        assertEquals(
            listOf("maintenance", "class", "wildcard"),
            HandSlot.entries.map { it.pageName },
        )
        assertEquals(null, HandSlot.at(3))
        assertEquals("dh_2026-11-08_2_7", HandIds.card("2026-11-08", 2, "7"))
        assertEquals("2026-11-08|5500|0|1", HandIds.seedText("2026-11-08", "5500", "0", 1))
    }

    @Test
    fun theMigrationPatternIsJavaScripts() {
        for (id in listOf("q1", "q2", "q3", "cc_", "cc_a", "cc_\u0085", "cc_a b")) assertTrue(
            id,
            HandMigration.matchesPattern(id),
        )
        for (id in
            listOf(
                "q4",
                "q10",
                "Q1",
                "q1 ",
                " cc_",
                "cc_a\n",
                "cc_\r",
                "cc_\u2028",
                "cc_\u2029",
                "xcc_",
                "undefined",
            )) assertFalse(id, HandMigration.matchesPattern(id))
    }

    @Test
    fun hashingAndTheGeneratorMatchKnownVectors() {
        assertEquals(2166136261L, HandRandom.seed(""))
        assertEquals(0xE40C292CL, HandRandom.seed("a")) // FNV-1a 32 of "a"
        assertEquals(4294967295L, HandRandom.toUint32(-1.0))
        assertEquals(0L, HandRandom.toUint32(4294967296.0))
        assertEquals(3L, HandRandom.toUint32(3.7))
        assertEquals(0L, HandRandom.toUint32(Double.NaN))
        val rng = HandRng(42)
        assertEquals(0.6011037519201636, rng.next(), 0.0) // also golden case rng/r3
        assertEquals(0.44829055899754167, rng.next(), 0.0)
    }

    @Test
    fun pageSemanticsAtTheEdges() {
        assertEquals("undefined", Js.string(null))
        assertEquals("null", Js.string(JsonNull))
        assertEquals("1e+21", Js.string(JsonNumber(1e21)))
        assertEquals(
            "a,,b",
            Js.string(JsonArray(listOf(JsonString("a"), JsonNull, JsonString("b")))),
        )
        assertTrue(Js.number(JsonArray(listOf(JsonBool.TRUE))).isNaN()) // Number([true]) is NaN
        assertEquals(5.0, Js.number(JsonArray(listOf(JsonArray(listOf(JsonNumber(5.0)))))), 0.0)
        assertTrue(Js.strictEquals(null, null))
        assertFalse(Js.strictEquals(JsonNull, null))
        assertFalse(Js.strictEquals(JsonNumber(Double.NaN), JsonNumber(Double.NaN)))
        assertTrue(Js.strictEquals(JsonNumber(-0.0), JsonNumber(0.0)))
        assertFalse(Js.strictEquals(JsonObject.EMPTY, JsonObject.EMPTY))
        val up =
            with(Js) {
                JsonObject(linkedMapOf("1" to JsonBool.TRUE, "a" to JsonBool.TRUE))
                    .withIndexKey(0, JsonBool.FALSE)
            }
        assertEquals("""{"0":false,"1":true,"a":true}""", JsonWriter.write(up))
    }

    /**
     * Each mutation of a page constant or of the content makes some golden case differ. A change of
     * a weight by one part in ten thousand moves no recorded pick (r would have to land that close
     * to a boundary), so the luck mutations are a few percent.
     */
    @Test
    fun everyMutationIsCaught() {
        val o = HandOdds.PAGE
        val mutants =
            mapOf(
                "luckFallback" to HandDealer(content, o.copy(luckFallback = 5501.0)),
                "luckFloor" to HandDealer(content, o.copy(luckFloor = 1500.0)),
                "luckSpan" to HandDealer(content, o.copy(luckSpan = 6000.0)),
                "tier1" to HandDealer(content, o.copy(tier1 = 1.3)),
                "tier1Luck" to HandDealer(content, o.copy(tier1Luck = 0.5)),
                "tier2" to HandDealer(content, o.copy(tier2 = 1.1)),
                "tier3" to HandDealer(content, o.copy(tier3 = 0.6)),
                "tier3Luck" to HandDealer(content, o.copy(tier3Luck = 0.9)),
                "maintenanceTier3" to HandDealer(content, o.copy(maintenanceTier3 = 0.4)),
                "freeRerolls" to
                    HandDealer(content.copy(hand = content.hand.copy(freeRerolls = 2))),
                "rerollCost" to HandDealer(content.copy(hand = content.hand.copy(rerollCost = 49))),
                "attributeOrder" to
                    HandDealer(content.copy(attributes = content.attributes.reversed())),
                "templateOrder" to
                    HandDealer(
                        content.copy(
                            hand = content.hand.copy(templates = content.hand.templates.reversed())
                        )
                    ),
                "classAttrs" to
                    HandDealer(
                        content.copy(
                            hand =
                                content.hand.copy(
                                    classAttrs =
                                        content.hand.classAttrs +
                                            ("athlete" to listOf("physique", "endurance"))
                                )
                        )
                    ),
            )
        val missed =
            mutants.filter { (_, dealer) ->
                HandGoldenReplay.cases.none { HandGoldenReplay.replay(it, dealer, content) != null }
            }
        assertTrue("mutations no golden case notices: ${missed.keys}", missed.isEmpty())
    }
}
