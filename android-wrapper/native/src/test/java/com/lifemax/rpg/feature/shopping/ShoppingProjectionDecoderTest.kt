package com.lifemax.rpg.feature.shopping

import com.lifemax.rpg.feature.shopping.ShoppingFixtures.item
import com.lifemax.rpg.feature.shopping.ShoppingFixtures.pref
import com.lifemax.rpg.feature.shopping.ShoppingFixtures.projection
import com.lifemax.rpg.feature.shopping.data.ShoppingFeed
import com.lifemax.rpg.feature.shopping.data.ShoppingProjectionDecoder
import com.lifemax.rpg.feature.shopping.domain.ShoppingProjection
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ShoppingProjectionDecoderTest {
    private fun rejects(j: JSONObject, why: String) {
        try {
            ShoppingProjectionDecoder.decode(j)
            fail("should reject: $why")
        } catch (e: Exception) {
            // rejected
        }
    }

    @Test
    fun decodesTheWebProjection() {
        val p =
            ShoppingProjectionDecoder.decode(
                projection(
                    items = listOf(item("oats", grams = 550.0, packGrams = 500.0, unit = "bag")),
                    prefs = listOf(pref("sd:oats", "165233284", "Quaker", "42 oz")),
                    recipes =
                        listOf(ShoppingFixtures.recipe("r1", "Oat bowl", 2, "1/2 cup oats (40g)")),
                )
            )
        assertEquals("2026-11-02", p.weekStart)
        assertEquals("sd:oats", p.weekItems.single().key)
        assertEquals(500.0, p.weekItems.single().packGrams!!, 0.0)
        assertEquals("165233284", p.prefs.single().productId)
        assertEquals(2, p.recipes.single().servings)
        assertNull(p.answer)
    }

    @Test
    fun aMissingPackSizeIsNullNotZero() {
        val p =
            ShoppingProjectionDecoder.decode(
                projection(items = listOf(item("oats", packGrams = 0.0)))
            )
        assertNull(p.weekItems.single().packGrams)
    }

    @Test
    fun theAnswerIsDecoded() {
        val p =
            ShoppingProjectionDecoder.decode(
                projection(
                    answer = ShoppingFixtures.answer("op1", ok = false, message = "Not saved")
                )
            )
        assertEquals("op1", p.answer!!.op)
        assertFalse(p.answer!!.ok)
    }

    @Test
    fun rejectsMalformedProjections() {
        rejects(projection().put("version", 2), "version")
        rejects(projection().apply { remove("items") }, "missing items")
        rejects(projection().apply { remove("session") }, "missing session")
        rejects(projection().put("revision", 0), "revision")
        rejects(projection().put("weekStart", "Monday"), "date")
        rejects(projection().put("items", "nope"), "items type")
        rejects(projection(items = listOf(item("oats"), item("oats"))), "duplicate id")
        rejects(projection(items = listOf(item("oats", grams = -1.0))), "negative grams")
        rejects(projection(items = listOf(item("oats", name = ""))), "blank name")
        rejects(projection(prefs = listOf(pref("sd:oats", "1234"))), "short product id")
        rejects(projection(prefs = listOf(pref("sd:oats", "12345abc"))), "non numeric product id")
        rejects(
            projection(prefs = listOf(pref("sd:oats", "11111"), pref("sd:oats", "22222"))),
            "duplicate pref",
        )
        rejects(projection(prefs = listOf(pref("sd:oats", "11111", "x".repeat(81)))), "long label")
        rejects(
            projection(recipes = listOf(ShoppingFixtures.recipe("r", "R", 0, "1 egg"))),
            "servings 0",
        )
        rejects(projection(answer = JSONObject().put("op", "x")), "partial answer")
        rejects(
            projection().put("items", JSONArray((1..301).map { item("i$it") })),
            "too many items",
        )
        rejects(
            projection().put("prefs", JSONArray((1..201).map { pref("sd:k$it", "11111") })),
            "too many prefs",
        )
    }

    @Test
    fun theFeedKeepsTheLastGoodProjectionWhenOneIsMalformed() {
        val feed = ShoppingFeed()
        assertTrue(feed.accept(projection(revision = 1)))
        val good = feed.current
        assertFalse(feed.accept(projection(revision = 2).put("version", 9)))
        assertEquals(good, feed.current)
        assertTrue(feed.lastRejection!!.isNotBlank())
        assertFalse(feed.accept(null))
        assertEquals(good, feed.current)
    }

    @Test
    fun theFeedIgnoresAnOlderRevisionOfTheSamePage() {
        val feed = ShoppingFeed()
        feed.accept(projection(revision = 5, items = listOf(item("a"))))
        assertFalse(feed.accept(projection(revision = 4, items = listOf(item("b")))))
        assertEquals("a", feed.current!!.weekItems.single().id)
        assertTrue(feed.accept(projection(revision = 5, items = listOf(item("c")))))
        assertEquals("c", feed.current!!.weekItems.single().id)
    }

    @Test
    fun theFeedTakesALowerRevisionFromANewPageLoad() {
        val feed = ShoppingFeed()
        feed.accept(projection(revision = 50, session = "old"))
        assertTrue(feed.accept(projection(revision = 1, session = "new")))
        assertEquals("new", feed.current!!.session)
    }

    @Test
    fun theCapabilityIsTheDocumentedString() {
        assertEquals("food.shopping.v1", ShoppingProjection.CAPABILITY)
    }
}
