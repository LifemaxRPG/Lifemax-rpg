package com.lifemax.rpg.feature.pet.art

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The coat palettes against the page's `petAssets.pal`, and the transparency rules of `petInit`.
 */
class CatPaletteTest {
    private val art = PetArtResources.load()

    @Test
    fun everyIndexOfEveryLegacyCoatIsTheWebsColour() {
        val pal = PetArtFixtures.web.getJSONObject("pal")
        assertEquals(PetArtFixtures.coats.sorted(), art.legacyCoats)
        for (coat in PetArtFixtures.coats) {
            val web = pal.getJSONObject(coat)
            val mine = art.palette(coat)!!
            assertEquals(coat, web.length(), mine.indices.size)
            for (key in web.keys()) {
                val rgb = web.getJSONArray(key)
                val want =
                    (0xFF shl 24) or
                        (rgb.getInt(0) shl 16) or
                        (rgb.getInt(1) shl 8) or
                        rgb.getInt(2)
                assertEquals("$coat index $key", want, mine.argb(key.toInt()))
            }
        }
    }

    @Test
    fun indexZeroAndAnyIndexWithoutAColourAreClear() {
        val ginger = art.palette("ginger")!!
        assertEquals(0, ginger.argb(0))
        assertEquals(0, ginger.argb(15))
        assertEquals(0, ginger.argb(255))
        assertEquals(0, ginger.argb(-1))
        assertEquals(0, ginger.argb(300))
        assertEquals(21, ginger.indices.size)
        assertTrue(ginger.argb(10) ushr 24 == 255)
    }

    @Test
    fun anEntryForIndexZeroIsIgnored() {
        val palette = CatPalette.of(mapOf(0 to 0xFFFFFF, 7 to 0x102030))
        assertEquals(0, palette.argb(0))
        assertEquals(0xFF102030.toInt(), palette.argb(7))
        assertEquals(listOf(7), palette.indices)
    }

    @Test(expected = IllegalArgumentException::class)
    fun anIndexOutsideTheByteRangeIsRefused() {
        CatPalette.of(mapOf(256 to 0))
    }
}
