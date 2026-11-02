package com.lifemax.rpg.feature.food

import com.lifemax.rpg.feature.food.data.decodeAnswer
import com.lifemax.rpg.feature.food.data.decodeSheet
import com.lifemax.rpg.feature.food.domain.SheetView
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 1c and 2 of the add-food sheet's decoder, on projections the web app itself produced. */
class FoodSheetFormsJsonTest {
    private fun fixture(name: String) =
        JSONObject(javaClass.getResource("/food-sheet-$name.json")!!.readText())

    private fun malformed(name: String, change: (JSONObject) -> Unit) {
        val d = fixture(name)
        change(d.getJSONObject("sheet"))
        assertThrows(IllegalArgumentException::class.java) { decodeSheet(d) }
    }

    @Test
    fun theSearchSaysThePagesWords() {
        val s = decodeSheet(fixture("search"))!!
        assertEquals("Add food", s.title)
        assertFalse(s.back)
        assertEquals(listOf("Recent", "Favorites", "My foods"), s.tabs.map { it.label })
        assertEquals("Search chicken, rice, yogurt…", s.words.search)
        assertNull(s.quick)
        assertNull(s.create)
        assertNull(s.goal)
    }

    @Test
    fun aPortionStepsBackAndHasNoEntry() {
        val s = decodeSheet(fixture("portion"))!!
        assertEquals("Add food", s.title)
        assertTrue(s.back)
        assertNull(s.portion!!.edit)
    }

    @Test
    fun quickCaloriesHasThePagesBoxes() {
        val q = decodeSheet(fixture("quick"))!!.quick!!
        assertEquals("Calories", q.kcalLabel)
        assertEquals("e.g. 450", q.kcalHint)
        assertEquals(60, q.nameMax)
        assertEquals("Add to Lunch", q.save)
    }

    @Test
    fun createHasItsBoxesInThePagesOrder() {
        val s = decodeSheet(fixture("create"))!!
        assertEquals(SheetView.Create, s.view)
        val c = s.create!!
        assertEquals(
            listOf("name", "brand", "serving", "kcal", "p", "c", "f"),
            c.fields.map { it.key },
        )
        val serving = c.fields.first { it.key == "serving" }
        assertTrue(serving.decimal)
        assertEquals("100", serving.value)
        assertEquals("grams", serving.unit)
        assertEquals(80, c.fields.first().maxLength)
        assertEquals(4, c.fields.count { it.group == 1 })
        assertEquals("Save and continue", c.save)
    }

    @Test
    fun theTargetsHaveThePagesBoxesAndLine() {
        val s = decodeSheet(fixture("goal"))!!
        val g = s.goal!!
        assertEquals("Daily targets", s.title)
        assertFalse(s.back)
        assertEquals(50, g.calStep)
        assertEquals(0, g.calMin)
        assertEquals(listOf("bal", "hi", "low"), g.presets.map { it.id })
        assertEquals(listOf("p", "c", "f"), g.fields.map { it.key })
        assertEquals(0xFFFF7A8AL, g.fields[0].color)
        assertEquals("Your macros add up to ", g.sum.lead)
        assertEquals("ml", g.waterUnit)
    }

    @Test
    fun anEntryOfAnotherDayCanBeLoggedToday() {
        val s = decodeSheet(fixture("edit"))!!
        assertEquals(SheetView.Edit, s.view)
        val e = s.portion!!.edit!!
        assertEquals("f9", e.id)
        assertEquals("Remove", e.remove)
        assertEquals("Log today", e.again)
        assertTrue(s.portion!!.logLabel.startsWith("Save changes"))
    }

    @Test
    fun todaysEntryHasNoLogToday() {
        val d = fixture("edit")
        d.getJSONObject("sheet")
            .getJSONObject("portion")
            .getJSONObject("edit")
            .put("again", JSONObject.NULL)
        assertNull(decodeSheet(d)!!.portion!!.edit!!.again)
    }

    @Test
    fun anEntryKeepsAMealThePillsDoNotOffer() {
        val d = fixture("edit")
        d.getJSONObject("sheet").put("slot", "snack")
        assertEquals("snack", decodeSheet(d)!!.slot)
    }

    @Test
    fun theAnswerOfAWriteDecodes() {
        val a = decodeAnswer(fixture("answer"))!!
        assertEquals("op-1", a.op)
        assertTrue(a.ok)
        assertTrue(a.changed)
        assertTrue(a.message.startsWith("Removed "))
        assertNull(decodeAnswer(fixture("search")))
    }

    @Test
    fun aNumberSentAsTextIsMalformed() =
        malformed("goal") { it.getJSONObject("goal").getJSONObject("cal").put("step", "50") }

    @Test
    fun aCountThatIsNotWholeIsMalformed() =
        malformed("results") { it.getJSONArray("rows").getJSONObject(0).put("kcal", 12.5) }

    @Test
    fun aGramsAsTextIsMalformed() =
        malformed("portion") { it.getJSONObject("portion").put("grams", "175") }

    @Test
    fun anUnknownBoxKindIsMalformed() =
        malformed("create") {
            it.getJSONObject("create").getJSONArray("fields").getJSONObject(0).put("kind", "date")
        }

    @Test
    fun twoBoxesWithOneKeyAreMalformed() =
        malformed("goal") {
            it.getJSONObject("goal").getJSONArray("fields").getJSONObject(1).put("key", "p")
        }

    @Test
    fun anEditViewWithoutItsEntryIsMalformed() =
        malformed("edit") { it.getJSONObject("portion").put("edit", JSONObject.NULL) }

    @Test fun aPortionWithAnEntryIsMalformed() = malformed("edit") { it.put("view", "portion") }

    @Test
    fun createWithoutItsFormIsMalformed() =
        malformed("create") { it.put("create", JSONObject.NULL) }

    @Test
    fun aFormOnAnotherViewIsMalformed() =
        malformed("quick") { s ->
            s.put("goal", fixture("goal").getJSONObject("sheet").get("goal"))
        }

    @Test
    fun missingWordsAreMalformed() =
        malformed("search") { it.getJSONObject("words").remove("close") }

    @Test fun aMissingFormFieldIsMalformed() = malformed("search") { it.remove("goal") }

    @Test fun aTabThatIsNotOfferedIsMalformed() = malformed("search") { it.put("tab", "all") }

    @Test
    fun aWrongColourIsMalformed() =
        malformed("goal") {
            it.getJSONObject("goal").getJSONArray("fields").getJSONObject(0).put("color", "red")
        }
}
