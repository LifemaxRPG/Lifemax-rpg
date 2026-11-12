package com.lifemax.rpg.feature.pet.art

import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shade split loses nothing: joining a split sheet gives the sheet back, for every value a
 * sheet can hold, for every shipped and fixture sheet and for random sheets. The table says what
 * the palette header says, and the report finds no value in the shipped art that it does not know.
 */
class ShadeSplitTest {
    private fun sheetOf(values: IntArray, width: Int = values.size) =
        IndexSheet(width, values.size / width, values)

    @Test
    fun everyPossibleValueRoundTrips() {
        val sheet = sheetOf(IntArray(256) { it }, 16)
        val back = ShadeSplit.join(ShadeSplit.split(sheet))
        assertEquals(sheet.indices.toList(), back.indices.toList())
    }

    @Test
    fun randomSheetsRoundTrip() {
        val random = Random(7)
        repeat(200) {
            val sheet = sheetOf(IntArray(72 * 48) { random.nextInt(256) }, 72)
            val back = ShadeSplit.join(ShadeSplit.split(sheet))
            assertEquals(sheet.indices.toList(), back.indices.toList())
        }
    }

    @Test
    fun theTableIsTheOneInThePlan() {
        fun check(index: Int, family: ShadeFamily, base: Int, shade: Int) {
            val s = ShadeSplit.split(sheetOf(intArrayOf(index), 1))
            assertEquals("family of $index", family, ShadeSplit.family(index))
            assertEquals("base of $index", base, s.baseAt(0))
            assertEquals("shade of $index", shade, s.shadeAt(0))
        }
        for (i in 0..3) check(20 + 10 * i, ShadeFamily.COAT, 20, i)
        for (i in 0..2) check(60 + 10 * i, ShadeFamily.STRIPE, 60, i)
        for (i in 0..2) check(90 + 10 * i, ShadeFamily.WHITE, 90, i)
        for (i in 0..2) check(120 + 10 * i, ShadeFamily.PINK, 120, i)
        for (i in listOf(0, 10, 150, 160, 170, 180, 190, 200, 210, 25, 55, 255)) {
            check(i, ShadeFamily.FIXED, i, 0)
        }
    }

    @Test
    fun indexOfClampsIntoTheRamp() {
        assertEquals(20, ShadeSplit.indexOf(ShadeFamily.COAT, -3))
        assertEquals(50, ShadeSplit.indexOf(ShadeFamily.COAT, 9))
        assertEquals(110, ShadeSplit.indexOf(ShadeFamily.WHITE, 3))
    }

    @Test
    fun aValueOutsideAnEightBitSheetIsRefused() {
        var refused = false
        try {
            ShadeSplit.split(sheetOf(intArrayOf(256), 1))
        } catch (e: IllegalArgumentException) {
            refused = true
        }
        assertTrue(refused)
    }

    @Test
    fun everyShippedAndFixtureSheetRoundTripsByteForByte() {
        val cases = ShadeTestSupport.all
        assertEquals(4 * 14 + 4 * 3, cases.size)
        for (c in cases) {
            val back = ShadeSplit.join(ShadeSplit.split(c.sheet))
            assertEquals(c.label, c.sheet.indices.toList(), back.indices.toList())
        }
    }

    @Test
    fun theShippedArtUsesOnlyTheDocumentedIndices() {
        val report = ShadeSplitReport.build(ShadeTestSupport.shipped)
        println(report.text)
        assertEquals("undocumented indices in the shipped art", emptySet<Int>(), report.surprises)
    }

    @Test
    fun theGeneticFixturesAddOnlyTheKnownExtras() {
        // The page's genetic palettes carry colours outside the shipped scheme: 75 and 85 (calico's
        // lighter orange tones) and 220-252 (the colour-point's three-tone points). The split
        // leaves them FIXED, i.e. never shaded by a light. A new extra turning up must be looked
        // at, not silently left unlit, so this list is the contract.
        val report = ShadeSplitReport.build(ShadeTestSupport.genetic)
        println(report.text)
        val expected =
            setOf(75, 85) + listOf(220, 221, 222, 230, 231, 232, 240, 241, 242, 250, 251, 252)
        assertTrue(
            "new extras: ${report.surprises - expected}",
            expected.containsAll(report.surprises),
        )
        for (i in report.surprises) assertEquals(ShadeFamily.FIXED, ShadeSplit.family(i))
    }
}
