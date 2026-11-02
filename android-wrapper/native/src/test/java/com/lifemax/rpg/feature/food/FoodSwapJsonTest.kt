package com.lifemax.rpg.feature.food

import com.lifemax.rpg.feature.food.data.decodeAnswer
import com.lifemax.rpg.feature.food.data.decodeNotice
import com.lifemax.rpg.feature.food.data.decodeSheet
import com.lifemax.rpg.feature.food.data.decodeSwap
import com.lifemax.rpg.feature.food.data.hasSwap
import com.lifemax.rpg.feature.food.domain.SheetView
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The swap sheet's decoder against projections the page itself produced (gen_food_swap_fixtures).
 */
class FoodSwapJsonTest {
    private fun fixture(name: String) =
        JSONObject(javaClass.getResource("/food-swap-$name.json")!!.readText())

    @Test
    fun openSwapHasThePagesWordsAndMeals() {
        val s = decodeSwap(fixture("open"))!!
        assertEquals(1, s.index)
        assertEquals("Swap lunch", s.title)
        assertEquals("Instead of ", s.lead)
        assertEquals(". The rest of the day rescales to keep your target.", s.tail)
        assertEquals("Close", s.close)
        assertTrue(s.rows.size > 2)
        assertTrue(
            s.rows.all { it.unit == "kcal" && it.kcal.isNotEmpty() && it.detail.isNotEmpty() }
        )
        assertNull(s.none)
        assertNull(s.reset)
    }

    @Test
    fun aSwappedMealOffersTheWayBackToThePlan() {
        assertEquals("Back to the plan", decodeSwap(fixture("reset"))!!.reset)
    }

    @Test
    fun nothingToOfferSaysSo() {
        val s = decodeSwap(fixture("none"))!!
        assertTrue(s.rows.isEmpty())
        assertEquals("No other options", s.none)
    }

    @Test
    fun aClosedSheetDecodesToNullButTheFieldIsThere() {
        val d = fixture("notice")
        assertTrue(d.hasSwap())
        assertNull(decodeSwap(d))
    }

    @Test
    fun anOlderPageHasNoSwapField() {
        assertFalse(fixture("open").apply { remove("swap") }.hasSwap())
    }

    @Test
    fun theMessageAfterASwapOffersUndo() {
        val n = decodeNotice(fixture("notice"))!!
        assertTrue(n.text.startsWith("Swapped to "))
        assertEquals("Undo", n.action)
        val a = decodeAnswer(fixture("notice"))!!
        assertTrue(a.ok && a.changed)
    }

    @Test
    fun aRefusedSwapIsAnsweredNotOk() {
        val a = decodeAnswer(fixture("answer"))!!
        assertFalse(a.ok)
        assertFalse(a.changed)
        assertNotNull(decodeSwap(fixture("answer")))
    }

    @Test
    fun aMissingFieldIsMalformed() {
        val d = fixture("open")
        d.getJSONObject("swap").remove("lead")
        assertThrows(IllegalArgumentException::class.java) { decodeSwap(d) }
    }

    @Test
    fun aWrongKindIsMalformed() {
        val d = fixture("open")
        d.getJSONObject("swap").put("index", "1")
        assertThrows(IllegalArgumentException::class.java) { decodeSwap(d) }
        val e = fixture("open")
        e.getJSONObject("swap").put("index", 1.5)
        assertThrows(IllegalArgumentException::class.java) { decodeSwap(e) }
        val f = fixture("open")
        f.getJSONObject("swap").getJSONArray("rows").getJSONObject(0).put("kcal", 784)
        assertThrows(IllegalArgumentException::class.java) { decodeSwap(f) }
    }

    @Test
    fun anEmptyListNeedsItsWordsAndAFullOneMustNotHaveThem() {
        val d = fixture("none")
        d.getJSONObject("swap").put("none", JSONObject.NULL)
        assertThrows(IllegalArgumentException::class.java) { decodeSwap(d) }
        val e = fixture("open")
        e.getJSONObject("swap").put("none", "No other options")
        assertThrows(IllegalArgumentException::class.java) { decodeSwap(e) }
    }

    @Test
    fun twoMealsWithOneIdAreMalformed() {
        val d = fixture("open")
        val rows = d.getJSONObject("swap").getJSONArray("rows")
        rows.put(JSONObject(rows.getJSONObject(0).toString()))
        assertThrows(IllegalArgumentException::class.java) { decodeSwap(d) }
    }

    @Test
    fun theSeededRecipeIsTheAddFoodSheetsPortionView() {
        val seed = JSONObject(javaClass.getResource("/food-recipe-seed.json")!!.readText())
        val s = decodeSheet(seed)!!
        assertEquals(SheetView.Portion, s.view)
        assertTrue(s.back)
        assertTrue(s.portion!!.grams > 0)
        assertNull(s.portion!!.edit)
        assertNull(decodeSwap(seed))
    }
}
