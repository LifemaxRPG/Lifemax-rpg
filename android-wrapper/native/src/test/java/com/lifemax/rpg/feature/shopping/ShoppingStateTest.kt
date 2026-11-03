package com.lifemax.rpg.feature.shopping

import com.lifemax.rpg.feature.shopping.ShoppingFixtures.item
import com.lifemax.rpg.feature.shopping.ShoppingFixtures.pref
import com.lifemax.rpg.feature.shopping.ShoppingFixtures.projection
import com.lifemax.rpg.feature.shopping.data.WalmartCartLauncher
import com.lifemax.rpg.feature.shopping.domain.ProductMatch
import com.lifemax.rpg.feature.shopping.presentation.CartNotice
import com.lifemax.rpg.feature.shopping.presentation.ShoppingListText
import com.lifemax.rpg.feature.shopping.presentation.ShoppingState
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingStateTest {
    private val opener = FakeOpener()
    private var now = 5_000_000L
    private val sent = mutableListOf<Pair<String, Map<String, Any>>>()
    private val launcher = WalmartCartLauncher(opener, { now }, 60_000)

    private fun state(json: JSONObject? = projection()): ShoppingState =
        ShoppingState(launcher, { c, p -> sent += c to p }, clock = { now }).also {
            it.accept(json)
        }

    private fun matchedProjection() =
        projection(
            items =
                listOf(
                    item("oats", grams = 550.0),
                    item("rice", grams = 500.0),
                    item("salt", home = true),
                ),
            prefs = listOf(pref("sd:oats", "145051970"), pref("sd:rice", "165233284")),
        )

    @Test
    fun nothingIsDrawnUntilAGoodProjectionArrives() {
        val s = state(null)
        assertFalse(s.ready)
        s.accept(JSONObject().put("version", 7))
        assertFalse(s.ready)
        s.accept(projection())
        assertTrue(s.ready)
    }

    @Test
    fun aBadUpdateKeepsTheLastGoodList() {
        val s = state()
        s.accept(projection(revision = 2).put("version", 3))
        assertEquals(3, s.rows.size)
    }

    @Test
    fun theSummaryCountsAddedUnmatchedAndOwned() {
        val s =
            state(
                projection(
                    items = listOf(item("oats"), item("rice"), item("salt", home = true)),
                    prefs = listOf(pref("sd:oats", "145051970")),
                )
            )
        assertEquals("1 items added, 1 need a product, 1 already owned", s.summary.text)
    }

    @Test
    fun theAddAllButtonNeedsAMatchedItem() {
        assertFalse(state().canAddAll)
        assertTrue(state(matchedProjection()).canAddAll)
    }

    @Test
    fun pantryItemsAreOwnedUntilTheOwnerSaysOtherwise() {
        val s = state()
        assertTrue(s.rows.single { it.key == "sd:salt" }.owned)
        s.setOwned("sd:salt", false)
        assertFalse(s.rows.single { it.key == "sd:salt" }.owned)
        s.setOwned("sd:oats", true)
        assertEquals(1, s.summary.owned) // salt now needed, oats owned
    }

    @Test
    fun anOwnedItemIsNotInTheCart() {
        val s = state(matchedProjection())
        s.setOwned("sd:rice", true)
        assertEquals(listOf("145051970"), s.plan.links.single().lines.map { it.productId })
    }

    @Test
    fun removedItemsLeaveTheListAndComeBack() {
        val s = state(matchedProjection())
        s.remove("sd:oats")
        assertEquals(2, s.rows.size)
        assertEquals(1, s.removedCount)
        s.restoreRemoved()
        assertEquals(3, s.rows.size)
    }

    @Test
    fun theQuantityDefaultsToThePackagesNeededAndCanBeEdited() {
        val s = state(matchedProjection())
        assertEquals(2, s.rows.single { it.key == "sd:oats" }.quantity) // 550 g over 500 g packs
        s.setQuantity("sd:oats", 5)
        assertEquals(
            "https://affil.walmart.com/cart/addToCart?items=145051970%7C5%2C165233284%7C1",
            s.plan.links.single().url,
        )
        s.setQuantity("sd:oats", 500)
        assertEquals(99, s.rows.single { it.key == "sd:oats" }.quantity)
        s.setQuantity("sd:oats", -2)
        assertEquals(1, s.rows.single { it.key == "sd:oats" }.quantity)
    }

    @Test
    fun theCartLinkUsesTheMatchedProducts() {
        val s = state(matchedProjection())
        assertEquals(
            "https://affil.walmart.com/cart/addToCart?items=145051970%7C2%2C165233284%7C1",
            s.plan.links.single().url,
        )
    }

    @Test
    fun aValidPastedLinkIsSentToThePageToSave() {
        val s = state()
        assertNull(
            s.setProduct("sd:oats", "https://www.walmart.com/ip/Oats/165233284", "Quaker", "42 oz")
        )
        val (command, payload) = sent.single()
        assertEquals("shopPrefSet", command)
        assertEquals("sd:oats", payload["key"])
        assertEquals("165233284", payload["productId"])
        assertEquals("Quaker", payload["label"])
        assertTrue((payload["op"] as String).isNotBlank())
        assertTrue(s.rows.single { it.key == "sd:oats" }.match is ProductMatch.Matched)
    }

    @Test
    fun anInvalidPasteIsRefusedAndSendsNothing() {
        val s = state()
        val reason = s.setProduct("sd:oats", "https://example.com/ip/165233284")
        assertNotNull(reason)
        assertTrue(sent.isEmpty())
        assertTrue(s.rows.single { it.key == "sd:oats" }.match is ProductMatch.Unmatched)
    }

    @Test
    fun aRetryOfTheSameChangeKeepsItsOperationId() {
        val s = state()
        s.setProduct("sd:oats", "11111")
        s.setProduct("sd:oats", "11111")
        assertEquals(sent[0].second["op"], sent[1].second["op"])
        s.setProduct("sd:rice", "22222")
        assertTrue(sent[2].second["op"] != sent[0].second["op"])
    }

    @Test
    fun theOperationIdIsFreedWhenThePageAnswers() {
        val s = state()
        s.setProduct("sd:oats", "11111")
        val op = sent[0].second["op"] as String
        s.accept(
            projection(
                revision = 2,
                prefs = listOf(pref("sd:oats", "11111")),
                answer = ShoppingFixtures.answer(op, ok = true),
            )
        )
        s.setProduct("sd:oats", "33333")
        assertTrue(sent[1].second["op"] != op)
    }

    @Test
    fun aFailedSaveIsSaidInPlainWords() {
        val s = state()
        s.setProduct("sd:oats", "11111")
        val op = sent[0].second["op"] as String
        s.accept(
            projection(
                revision = 2,
                answer =
                    ShoppingFixtures.answer(
                        op,
                        ok = false,
                        message = "Could not save to this browser (storage full?)",
                    ),
            )
        )
        assertEquals("Could not save to this browser (storage full?)", s.message)
    }

    @Test
    fun clearingAProductMakesTheRowUnmatchedAtOnceAndAsksThePageToForgetIt() {
        val s = state(matchedProjection())
        s.clearProduct("sd:oats")
        assertEquals("shopPrefClear", sent.single().first)
        assertTrue(s.rows.single { it.key == "sd:oats" }.match is ProductMatch.Unmatched)
        s.accept(projection(revision = 2, prefs = listOf(pref("sd:rice", "165233284"))))
        assertTrue(s.rows.single { it.key == "sd:oats" }.match is ProductMatch.Unmatched)
    }

    @Test
    fun recipesAddScaledIngredientRowsThatNeedAProduct() {
        val recipe =
            ShoppingFixtures.recipe(
                "r1",
                "Oat bowl",
                1,
                "1/2 cup rolled oats (40g)",
                "Pinch of salt",
            )
        val s = state(projection(items = emptyList(), recipes = listOf(recipe)))
        assertTrue(s.rows.isEmpty())
        s.pickRecipe("r1", 3.0)
        val row = s.rows.single()
        assertEquals(120.0, row.item.amount.value, 1e-9)
        assertEquals("ing:rolled oat|g", row.key)
        assertTrue(row.needsProduct)
        assertEquals("Pinch of salt", s.review.single().text)
        s.pickRecipe("r1", null)
        assertTrue(s.rows.isEmpty())
    }

    @Test
    fun theCartOpensOnceAndNeverClaimsSuccess() {
        val s = state(matchedProjection())
        s.openCart()
        assertTrue(s.notice is CartNotice.Handed)
        assertEquals(1, opener.calls.size)
    }

    @Test
    fun aRepeatOfTheSameCartAsksFirstAndOpensAgainOnlyWhenTold() {
        val s = state(matchedProjection())
        s.openCart()
        now += 3_000
        s.openCart()
        assertTrue(s.notice is CartNotice.ConfirmRepeat)
        assertEquals(1, opener.calls.size)
        s.openCart(0, openAgainAnyway = true)
        assertEquals(2, opener.calls.size)
        assertTrue(s.notice is CartNotice.Handed)
    }

    @Test
    fun aLargeCartIsOpenedOneLinkAtATime() {
        val items = (1..25).map { item("i$it") }
        val prefs = (1..25).map { pref("sd:i$it", (10000 + it).toString()) }
        val s = state(projection(items = items, prefs = prefs))
        assertEquals(2, s.plan.links.size)
        s.openCart()
        assertEquals(1, s.cartIndex)
        s.openCart()
        assertEquals(2, opener.calls.size)
        assertTrue(opener.calls[0] != opener.calls[1])
    }

    @Test
    fun aFailedLaunchIsReported() {
        opener.system = { throw com.lifemax.rpg.feature.shopping.data.NoAppToOpen("x") }
        opener.browser = { throw com.lifemax.rpg.feature.shopping.data.NoAppToOpen("y") }
        val s = state(matchedProjection())
        s.openCart()
        assertTrue(s.notice is CartNotice.Failed)
    }

    @Test
    fun searchOpensAWalmartSearchAndNeverTheCart() {
        val s = state()
        s.search("rolled oats")
        assertEquals(listOf("system:https://www.walmart.com/search?q=rolled%20oats"), opener.calls)
        s.search(
            "rolled oats"
        ) // a search can repeat without a confirmation: it cannot add anything
        assertEquals(2, opener.calls.size)
    }

    @Test
    fun theCopiedListLeavesOutOwnedItemsAndNamesTheChosenProducts() {
        val s = state(matchedProjection())
        val text = ShoppingListText.build(s.weekStart, s.rows)
        assertTrue(text.startsWith("Shopping list, week of 2026-11-02"))
        assertTrue(text.contains("[ ] Oats - 2 x pack (Walmart item 145051970)"))
        assertFalse(text.contains("Salt"))
    }
}
