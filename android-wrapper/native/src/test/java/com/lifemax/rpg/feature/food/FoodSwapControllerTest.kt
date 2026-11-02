package com.lifemax.rpg.feature.food

import com.lifemax.rpg.feature.food.domain.FoodAnswer
import com.lifemax.rpg.feature.food.presentation.sheet.FoodSheetController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The swap sheet's writes go through the add-food sheet's controller: one operation id per
 * (command, meal), kept until the page answers it, so a repeat is "Already recorded." on the page
 * and never a second swap.
 */
class FoodSwapControllerTest {
    private val sent = mutableListOf<Pair<String, Map<String, Any>>>()
    private var clock = 0L
    private var next = 0
    private val actions =
        FoodSheetController({ c, p -> sent += c to p }, { "op-${++next}" }, { clock })

    private fun pick(index: Int, id: String) =
        actions.write("swapPick", "$index:$id", mapOf("index" to index, "id" to id))

    @Test
    fun closingIsAMoveWithoutAnOperation() {
        actions.send("swapClose")
        assertEquals("swapClose" to emptyMap<String, Any>(), sent.single())
    }

    @Test
    fun aSwapCarriesItsOperationAndAnotherTapBeforeTheAnswerSendsNothing() {
        pick(1, "std_l02")
        assertEquals("op-1", sent.single().second["op"])
        assertEquals("std_l02", sent.single().second["id"])
        assertTrue(actions.waiting)
        pick(1, "std_l03")
        actions.write("swapReset", "1:reset", mapOf("index" to 1))
        assertEquals(1, sent.size)
    }

    @Test
    fun aLostAnswerResendsTheSameOperationAfterTheWait() {
        pick(1, "std_l02")
        clock += 5_000
        pick(1, "std_l02")
        assertEquals(2, sent.size)
        assertEquals(sent[0].second["op"], sent[1].second["op"])
    }

    @Test
    fun anAnswerEndsTheOperationSoTheNextSwapIsANewOne() {
        pick(1, "std_l02")
        actions.answered(FoodAnswer("op-1", ok = true, changed = true, message = "Swapped"))
        assertFalse(actions.waiting)
        pick(1, "std_l02")
        assertNotEquals(sent[0].second["op"], sent[1].second["op"])
    }

    @Test
    fun differentMealsAndTheWayBackAreOperationsOfTheirOwn() {
        pick(1, "a")
        actions.answered(FoodAnswer("op-1", ok = false, changed = false, message = ""))
        pick(1, "b")
        actions.answered(FoodAnswer("op-2", ok = false, changed = false, message = ""))
        actions.write("swapReset", "1:reset", mapOf("index" to 1))
        assertEquals(listOf("op-1", "op-2", "op-3"), sent.map { it.second["op"] })
    }

    @Test
    fun aClosedSheetDoesNotLendItsOperationToTheNext() {
        pick(1, "a")
        actions.sheetClosed()
        clock += 10_000
        pick(1, "a")
        assertEquals(listOf("op-1", "op-2"), sent.map { it.second["op"] })
    }
}
