package com.lifemax.rpg.feature.food

import com.lifemax.rpg.feature.food.data.decodeNotice
import com.lifemax.rpg.feature.food.data.decodeSheet
import com.lifemax.rpg.feature.food.domain.SheetView
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodSheetJsonTest {
    private fun fixture(name: String) =
        JSONObject(javaClass.getResource("/food-sheet-$name.json")!!.readText())

    @Test
    fun closedSheetDecodesToNull() {
        assertNull(decodeSheet(fixture("search").put("sheet", JSONObject.NULL)))
    }

    @Test
    fun searchHasItsRows() {
        val s = decodeSheet(fixture("results"))!!
        assertEquals(SheetView.Search, s.view)
        assertTrue(s.rows.isNotEmpty())
        assertNull(s.portion)
    }

    @Test
    fun portionHasItsBarsAsShares() {
        val p = decodeSheet(fixture("portion"))!!.portion!!
        assertEquals(3, p.impact.size)
        p.impact.forEach { assertTrue(it.before + it.add <= 1.0001f) }
        assertTrue(p.servings.isNotEmpty())
    }

    @Test
    fun quickHasNoPortion() {
        assertEquals(SheetView.Quick, decodeSheet(fixture("quick"))!!.view)
    }

    @Test
    fun noticeDecodes() {
        val n = decodeNotice(fixture("notice"))
        assertNotNull(n)
        assertEquals("Undo", fixture("quick").getJSONObject("notice").getString("action"))
    }

    @Test
    fun aPortionViewWithoutItsPortionIsMalformed() {
        val d = fixture("portion")
        d.getJSONObject("sheet").put("portion", JSONObject.NULL)
        assertThrows(IllegalArgumentException::class.java) { decodeSheet(d) }
    }

    @Test
    fun aMissingFieldIsMalformed() {
        val d = fixture("results")
        d.getJSONObject("sheet").remove("rows")
        assertThrows(Exception::class.java) { decodeSheet(d) }
    }

    @Test
    fun anUnknownViewIsMalformed() {
        val d = fixture("results")
        d.getJSONObject("sheet").put("view", "create")
        assertThrows(IllegalArgumentException::class.java) { decodeSheet(d) }
    }
}
