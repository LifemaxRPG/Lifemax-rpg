package com.lifemax.rpg.feature.pet.art

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coats native does not ship come from the [CoatSource] (the page, later); here a fake plays the
 * page.
 */
class CoatSourceTest {
    private val shippedAdult =
        PetArt::class.java.getResourceAsStream("/pet/sheets/adult-ginger.png")!!.use {
            it.readBytes()
        }

    // a composed coat: the same index art as ginger, its own colours (like the web's DNA coats)
    private val dnaPalette = CatPalette.of(mapOf(10 to 0x112233, 20 to 0xAA0000))
    private val dna = "dna_red_n_n_0_0_n_0_3"

    private fun fake() =
        FakeCoatSource(mapOf((dna to SheetKind.Adult) to shippedAdult), mapOf(dna to dnaPalette))

    @Test
    fun aComposedCoatIsRecolouredWithItsOwnPalette() {
        val source = fake()
        val art = PetArtResources.load(source)
        val frame = art.frame(dna, Growth.ADULT, "idle_sit", 0)!!
        // same silhouette, other colours: each pixel is the composed palette's colour for the index
        // the sheet holds
        val sheet = art.sheet("ginger", SheetKind.Adult)!!
        val rect = art.anims.frameRect(art.anims["idle_sit"]!!, 0)
        var outline = 0
        for (i in frame.pixels.indices) {
            val index =
                sheet.indices[(rect.y + i / rect.width) * sheet.width + rect.x + i % rect.width]
            assertEquals(dnaPalette.argb(index), frame.pixels[i])
            if (index == 10) outline++
        }
        assertTrue(outline > 0)
    }

    @Test
    fun aCoatNobodyKnowsIsNullAndAsksTheSourceOnce() {
        val source = FakeCoatSource()
        val art = PetArtResources.load(source)
        assertNull(art.palette("dna_unknown"))
        assertNull(art.sheet("dna_unknown", SheetKind.Adult))
        assertNull(art.frame("dna_unknown", Growth.ADULT, "walk", 0))
        assertTrue(source.sheetAsks.all { it == ("dna_unknown" to SheetKind.Adult) })
    }

    @Test
    fun aComposedSheetIsDecodedOnceAndKept() {
        val source = fake()
        val art = PetArtResources.load(source)
        val first = art.sheet(dna, SheetKind.Adult)
        assertNotNull(first)
        assertSame(first, art.sheet(dna, SheetKind.Adult))
        assertEquals(1, source.sheetAsks.size)
    }

    @Test
    fun theLeastRecentlyUsedComposedSheetsAreDropped() {
        val keys = (1..14).map { "dna_$it" }
        val source =
            FakeCoatSource(
                keys.associate { (it to SheetKind.Adult) to shippedAdult },
                keys.associateWith { dnaPalette },
            )
        val art = PetArtResources.load(source)
        for (k in keys) art.sheet(k, SheetKind.Adult)
        assertEquals(14, source.sheetAsks.size)
        art.sheet(keys.last(), SheetKind.Adult) // still cached
        assertEquals(14, source.sheetAsks.size)
        art.sheet(keys.first(), SheetKind.Adult) // dropped long ago: asked again
        assertEquals(15, source.sheetAsks.size)
    }

    @Test
    fun legacyCoatsNeverAskTheSource() {
        val source = FakeCoatSource()
        val art = PetArtResources.load(source)
        assertNotNull(art.frame("tuxedo", 2, "walk", 1))
        assertNotNull(art.frame("black", Growth.ADULT, "bonk", 1))
        assertTrue(source.sheetAsks.isEmpty())
    }

    @Test
    fun sheetKindsNameThemselvesLikeTheWebsPseudoUrls() {
        assertEquals("adult", SheetKind.of(Growth.ADULT, depth = false).key)
        assertEquals("depth", SheetKind.of(Growth.ADULT, depth = true).key)
        assertEquals("st3", SheetKind.of(3, depth = false).key)
        assertEquals("dst3", SheetKind.of(3, depth = true).key)
    }
}
