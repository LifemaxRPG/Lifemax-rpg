package com.lifemax.rpg.feature.shopping

import com.lifemax.rpg.feature.shopping.domain.CartIssue
import com.lifemax.rpg.feature.shopping.domain.CartRequest
import com.lifemax.rpg.feature.shopping.domain.WalmartCartFormat
import com.lifemax.rpg.feature.shopping.domain.WalmartCartLinkBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The cart link format the owner verified on the phone, and the rules around it. */
class WalmartCartLinkBuilderTest {
    private val builder = WalmartCartLinkBuilder()

    private fun req(id: String, qty: Int = 1, label: String = "Item $id") =
        CartRequest(id, qty, label)

    @Test
    fun goldenTwoProductLinkMatchesTheOwnersPrototype() {
        val plan = builder.build(listOf(req("145051970"), req("165233284")))
        assertEquals(1, plan.links.size)
        assertEquals(
            "https://affil.walmart.com/cart/addToCart?items=145051970%7C1%2C165233284%7C1",
            plan.links[0].url,
        )
        assertTrue(plan.issues.isEmpty())
    }

    @Test
    fun quantitiesAreWrittenAfterTheBarAndTheWholeTextIsEncoded() {
        val plan = builder.build(listOf(req("145051970", 3), req("165233284", 12)))
        assertEquals(
            "https://affil.walmart.com/cart/addToCart?items=145051970%7C3%2C165233284%7C12",
            plan.links[0].url,
        )
    }

    @Test
    fun encodingFollowsEncodeUriComponent() {
        assertEquals("a%7Cb%2Cc", WalmartCartFormat.encodeUriComponent("a|b,c"))
        assertEquals("AZaz09-_.!~*'()", WalmartCartFormat.encodeUriComponent("AZaz09-_.!~*'()"))
        assertEquals("%20%26%3D%3F%23%2F%25", WalmartCartFormat.encodeUriComponent(" &=?#/%"))
        assertEquals("%C3%A9", WalmartCartFormat.encodeUriComponent("é"))
        assertEquals(
            "https://www.walmart.com/search?q=greek%20yogurt%20%26%20honey",
            WalmartCartFormat.searchUrl("  greek yogurt & honey "),
        )
    }

    @Test
    fun emptyCartMakesNoLink() {
        val plan = builder.build(emptyList())
        assertTrue(plan.isEmpty)
        assertTrue(plan.links.isEmpty())
        assertEquals(0, plan.lineCount)
    }

    @Test
    fun invalidIdsAreLeftOutAndReported() {
        val plan =
            builder.build(
                listOf(req("1234"), req("abc12345"), req("1234567890123456"), req("12345"))
            )
        assertEquals(1, plan.lineCount)
        assertEquals(3, plan.issues.count { it is CartIssue.InvalidId })
        assertTrue(plan.links[0].url.endsWith("12345%7C1"))
    }

    @Test
    fun quantityBelowOneIsLeftOutAndReported() {
        val plan = builder.build(listOf(req("11111", 0), req("22222", -3), req("33333", 2)))
        assertEquals(2, plan.issues.count { it is CartIssue.InvalidQuantity })
        assertEquals(listOf("33333"), plan.links.single().lines.map { it.productId })
    }

    @Test
    fun anOnlyInvalidRequestMakesNoLink() {
        val plan = builder.build(listOf(req("nope", 1)))
        assertTrue(plan.links.isEmpty())
        assertEquals(1, plan.issues.size)
    }

    @Test
    fun theSameProductTwiceBecomesOneLineWithTheQuantitiesAdded() {
        val plan = builder.build(listOf(req("11111", 2), req("22222", 1), req("11111", 3)))
        assertEquals(2, plan.lineCount)
        assertEquals(5, plan.links[0].lines.first { it.productId == "11111" }.quantity)
        assertEquals(listOf("11111", "22222"), plan.links[0].lines.map { it.productId })
    }

    @Test
    fun aSumAboveTheCapIsCutToTwelveWithAVisibleNote() {
        val plan = builder.build(listOf(req("11111", 8, "Oats"), req("11111", 9, "Oats")))
        val line = plan.links.single().lines.single()
        assertEquals(WalmartCartFormat.MAX_QUANTITY, line.quantity)
        val note = plan.issues.filterIsInstance<CartIssue.QuantityCapped>().single()
        assertTrue(note.message.contains("17 requested"))
        assertTrue(note.message.contains("Oats"))
    }

    @Test
    fun hugeQuantitiesDoNotWrapAround() {
        val plan = builder.build(listOf(req("11111", Int.MAX_VALUE), req("11111", Int.MAX_VALUE)))
        assertEquals(WalmartCartFormat.MAX_QUANTITY, plan.links.single().lines.single().quantity)
    }

    @Test
    fun moreLinesThanTheLimitSplitIntoSeveralLinksAndSaySo() {
        val small = WalmartCartLinkBuilder(maxLines = 2)
        val plan = small.build((1..5).map { req("1000$it") })
        assertEquals(3, plan.links.size)
        assertEquals(listOf(2, 2, 1), plan.links.map { it.lines.size })
        assertEquals(listOf(1, 2, 3), plan.links.map { it.index })
        assertTrue(plan.links.all { it.count == 3 })
        assertTrue(plan.issues.any { it is CartIssue.Split && it.message.contains("3 links") })
    }

    @Test
    fun theDefaultLimitIsTwentyLinesAndNoLinkIsTooLong() {
        val plan = builder.build((1..45).map { req((10000 + it).toString(), 12) })
        assertEquals(listOf(20, 20, 5), plan.links.map { it.lines.size })
        assertTrue(plan.links.all { it.url.length <= WalmartCartFormat.MAX_URL_LENGTH })
    }

    @Test
    fun aLinkLongerThanTheUrlLimitIsSplit() {
        val short = WalmartCartLinkBuilder(maxLines = 100, maxUrlLength = 90)
        val plan = short.build((1..6).map { req("9000000000$it", 12) })
        assertTrue(plan.links.size > 1)
        assertTrue(plan.links.all { it.url.length <= 90 })
        assertEquals(6, plan.lineCount)
    }

    @Test
    fun aSingleLinkCarriesNoSplitNote() {
        val plan = builder.build(listOf(req("11111"), req("22222")))
        assertTrue(plan.issues.none { it is CartIssue.Split })
        assertEquals(1, plan.links.single().count)
    }
}
