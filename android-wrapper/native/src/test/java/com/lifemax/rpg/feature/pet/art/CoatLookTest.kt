package com.lifemax.rpg.feature.pet.art

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The palette health report, and the lit path off neutral: it moves only shades of healthy ramps,
 * never fixed colours, never clear pixels, and a flagged ramp stays flat.
 */
class CoatLookTest {
    private val art = ShadeTestSupport.art

    @Test
    fun theHealthReportOfEveryShippedAndFixturePalette() {
        for ((label, palette) in ShadeTestSupport.palettes) {
            val look = CoatLook.of(palette)
            println(
                "PALETTE $label coat=%s spread=%.1f downGain=%d stripe=%s white=%s pink=%s"
                    .format(
                        look.healthy(ShadeFamily.COAT),
                        look.spread(ShadeFamily.COAT),
                        look.downGain,
                        look.healthy(ShadeFamily.STRIPE),
                        look.healthy(ShadeFamily.WHITE),
                        look.healthy(ShadeFamily.PINK),
                    )
            )
            assertTrue("$label coat ramp strictly brightens", look.healthy(ShadeFamily.COAT))
        }
    }

    @Test
    fun theBlackCoatMayGoDarkerOneStepAndTheOthersTwo() {
        assertEquals(1, CoatLook.of(art.palette("black")!!).downGain)
        assertEquals(2, CoatLook.of(art.palette("ginger")!!).downGain)
        assertEquals(2, CoatLook.of(art.palette("gray")!!).downGain)
    }

    @Test
    fun aBrokenRampIsFlaggedAndStaysFlat() {
        // coat tones out of order: 30 darker than 20
        val broken =
            CatPalette.of(
                mapOf(10 to 0, 20 to 0x808080, 30 to 0x303030, 40 to 0xA0A0A0, 50 to 0xC0C0C0)
            )
        val look = CoatLook.of(broken)
        assertFalse(look.healthy(ShadeFamily.COAT))
        val sheet = IndexSheet(4, 1, intArrayOf(20, 30, 40, 50))
        val split = ShadeSplit.split(sheet)
        val rect = FrameRect(0, 0, 4, 1)
        val lit = LitRecolour.region(split, broken, rect, CatLight(shadeOffset = 1))
        assertEquals(Recolour.sheet(sheet, broken).pixels.toList(), lit.pixels.toList())
    }

    @Test
    fun aShadeOffsetMovesCoatTonesAndNothingElse() {
        val palette = art.palette("ginger")!!
        val sheet = IndexSheet(8, 1, intArrayOf(0, 10, 20, 30, 40, 50, 150, 200))
        val split = ShadeSplit.split(sheet)
        val rect = FrameRect(0, 0, 8, 1)
        val up = LitRecolour.region(split, palette, rect, CatLight(shadeOffset = 1)).pixels
        assertEquals(0, up[0])
        assertEquals(palette.argb(10), up[1])
        assertEquals(palette.argb(30), up[2])
        assertEquals(palette.argb(40), up[3])
        assertEquals(palette.argb(50), up[4])
        assertEquals(palette.argb(50), up[5]) // the top tone stays at the top
        assertEquals(palette.argb(150), up[6])
        assertEquals(palette.argb(200), up[7])
        val down = LitRecolour.region(split, palette, rect, CatLight(shadeOffset = -2)).pixels
        assertEquals(palette.argb(20), down[2])
        assertEquals(palette.argb(20), down[3])
        assertEquals(palette.argb(20), down[4])
        assertEquals(palette.argb(30), down[5])
    }

    @Test
    fun theBlackCoatCannotBePushedTwoStepsDown() {
        val palette = art.palette("black")!!
        val split = ShadeSplit.split(IndexSheet(1, 1, intArrayOf(50)))
        val out =
            LitRecolour.region(split, palette, FrameRect(0, 0, 1, 1), CatLight(shadeOffset = -2))
        assertEquals(palette.argb(40), out.pixels[0])
    }

    @Test
    fun theSmallerRampsMoveByHalfTheOffset() {
        val palette = art.palette("tuxedo")!!
        val split = ShadeSplit.split(IndexSheet(2, 1, intArrayOf(100, 130)))
        val rect = FrameRect(0, 0, 2, 1)
        val up2 = LitRecolour.region(split, palette, rect, CatLight(shadeOffset = 1)).pixels
        assertEquals(palette.argb(110), up2[0])
        assertEquals(palette.argb(140), up2[1])
    }

    @Test
    fun aTintMultipliesOpaquePixelsAndLeavesClearOnesClear() {
        val palette = art.palette("gray")!!
        val split = ShadeSplit.split(IndexSheet(2, 1, intArrayOf(0, 40)))
        val out =
            LitRecolour.region(split, palette, FrameRect(0, 0, 2, 1), CatLight(tint = 0x808080))
                .pixels
        assertEquals(0, out[0])
        val src = palette.argb(40)
        assertNotEquals(src, out[1])
        assertEquals(0xFF, out[1] ushr 24)
        assertEquals((((src shr 16) and 255) * 128 + 127) / 255, (out[1] shr 16) and 255)
    }
}
