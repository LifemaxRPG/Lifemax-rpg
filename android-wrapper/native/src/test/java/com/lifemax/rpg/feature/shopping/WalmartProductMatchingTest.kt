package com.lifemax.rpg.feature.shopping

import com.lifemax.rpg.feature.shopping.domain.MatchSource
import com.lifemax.rpg.feature.shopping.domain.PastedMatch
import com.lifemax.rpg.feature.shopping.domain.PreferredProduct
import com.lifemax.rpg.feature.shopping.domain.ProductMatch
import com.lifemax.rpg.feature.shopping.domain.ProductParse
import com.lifemax.rpg.feature.shopping.domain.WalmartProductLink
import com.lifemax.rpg.feature.shopping.domain.WalmartProductMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WalmartProductMatchingTest {
    private fun id(text: String) =
        (WalmartProductLink.parse(text) as? ProductParse.Valid)?.productId

    @Test
    fun acceptsTheProductLinkForms() {
        assertEquals("145051970", id("https://www.walmart.com/ip/145051970"))
        assertEquals("165233284", id("https://www.walmart.com/ip/Great-Value-Oats/165233284"))
        assertEquals(
            "27935840",
            id("https://www.walmart.com/ip/27935840?sid=edfc41aa-c731-47b8-9fec-6d99adbba129"),
        )
        assertEquals(
            "165233284",
            id("  https://www.walmart.com/ip/Some-Slug/165233284?athbdg=L1600#top  "),
        )
        assertEquals("165233284", id("https://walmart.com/ip/165233284"))
        assertEquals("165233284", id("http://m.walmart.com/ip/slug/165233284/"))
        assertEquals("165233284", id("HTTPS://WWW.WALMART.COM/IP/slug/165233284"))
    }

    @Test
    fun acceptsAPlainItemNumber() {
        assertEquals("12345", id("12345"))
        assertEquals("123456789012345", id(" 123456789012345 "))
    }

    @Test
    fun rejectsWhatIsNotAWalmartProduct() {
        listOf(
                "",
                "   ",
                "1234",
                "1234567890123456",
                "https://www.evil.com/ip/165233284",
                "https://walmart.com.evil.com/ip/165233284",
                "https://notwalmart.com/ip/165233284",
                "https://www.walmart.com/ip/slug/abc",
                "https://www.walmart.com/ip/slug/1234",
                "https://www.walmart.com/search?q=oats",
                "https://www.walmart.com/",
                "ftp://www.walmart.com/ip/165233284",
                "javascript:alert(1)",
                "walmart.com/ip/165233284",
                "oats",
                "https://www.walmart.com/ip/ spaced /165233284",
            )
            .forEach {
                assertTrue(
                    "should reject: $it",
                    WalmartProductLink.parse(it) is ProductParse.Invalid,
                )
            }
    }

    @Test
    fun rejectsAnOverlongPaste() {
        val long = "https://www.walmart.com/ip/" + "a".repeat(700) + "/165233284"
        assertTrue(WalmartProductLink.parse(long) is ProductParse.Invalid)
    }

    @Test
    fun anIngredientWithNoChoiceIsUnmatchedNeverGuessed() {
        val matcher =
            WalmartProductMatcher(saved = listOf(PreferredProduct("ing:rolled oat|g", "12345")))
        assertEquals(ProductMatch.Unmatched, matcher.matchFor("ing:rolled oats|g"))
        assertEquals(ProductMatch.Unmatched, matcher.matchFor("sd:oats"))
    }

    @Test
    fun savedMatchesAreFoundByKeyAndASessionChoiceWins() {
        val saved = PreferredProduct("sd:oats", "11111", "Old brand")
        val chosen = PreferredProduct("sd:oats", "22222", "New brand")
        val matcher = WalmartProductMatcher(listOf(saved), listOf(chosen))
        val match = matcher.matchFor("sd:oats") as ProductMatch.Matched
        assertEquals("22222", match.product.productId)
        assertEquals(MatchSource.SESSION, match.source)
        val onlySaved =
            WalmartProductMatcher(listOf(saved)).matchFor("sd:oats") as ProductMatch.Matched
        assertEquals(MatchSource.SAVED, onlySaved.source)
    }

    @Test
    fun aPastedLinkBecomesAMatchWithATrimmedBoundedNote() {
        val matcher = WalmartProductMatcher()
        val ok =
            matcher.fromPasted(
                "sd:oats",
                "https://www.walmart.com/ip/Oats/165233284",
                "  Quaker  ",
                "x".repeat(300),
            ) as PastedMatch.Accepted
        assertEquals("165233284", ok.product.productId)
        assertEquals("Quaker", ok.product.label)
        assertEquals(WalmartProductMatcher.MAX_NOTE, ok.product.note.length)
    }

    @Test
    fun aBadPasteIsRefusedWithAReason() {
        val bad = WalmartProductMatcher().fromPasted("sd:oats", "https://example.com/ip/165233284")
        assertTrue(bad is PastedMatch.Rejected)
        assertTrue((bad as PastedMatch.Rejected).reason.contains("walmart.com"))
    }

    @Test
    fun searchLinkIsAnEncodedWalmartSearch() {
        assertEquals(
            "https://www.walmart.com/search?q=rolled%20oats",
            WalmartProductMatcher().searchUrl("rolled oats"),
        )
    }
}
