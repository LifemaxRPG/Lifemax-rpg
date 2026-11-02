package com.lifemax.rpg.feature.food

import com.lifemax.rpg.core.designsystem.form.decimalOnly
import com.lifemax.rpg.feature.food.domain.CreateField
import com.lifemax.rpg.feature.food.domain.FoodAnswer
import com.lifemax.rpg.feature.food.presentation.sheet.FoodSheetController
import com.lifemax.rpg.feature.food.presentation.sheet.createRows
import com.lifemax.rpg.feature.food.presentation.sheet.sameNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The add-food sheet's writes: one operation id per (command, target), kept until answered. */
class FoodSheetControllerTest {
    private val sent = mutableListOf<Pair<String, Map<String, Any>>>()
    private var clock = 0L
    private var next = 0
    private val actions =
        FoodSheetController({ c, p -> sent += c to p }, { "op-${++next}" }, { clock })

    private fun lastOp() = sent.last().second["op"] as String

    @Test
    fun aMoveGoesAsItIs() {
        actions.send("sheetStep", mapOf("dir" to 1))
        assertEquals("sheetStep" to mapOf<String, Any>("dir" to 1), sent.single())
    }

    @Test
    fun aWriteCarriesAnOperationAndASecondTapSendsNothing() {
        actions.write("sheetLog", "Rice", mapOf("x" to 1))
        assertEquals("op-1", lastOp())
        assertEquals(1, sent.last().second["x"])
        assertTrue(actions.waiting)
        actions.write("sheetLog", "Rice")
        actions.write("sheetFav", "Rice")
        assertEquals(1, sent.size)
    }

    @Test
    fun anAnswerEndsTheOperationAndTheNextTapIsANewOne() {
        actions.write("sheetEditSave", "e1")
        actions.answered(FoodAnswer("op-1", ok = true, changed = true, message = ""))
        assertFalse(actions.waiting)
        assertNull(actions.operation("sheetEditSave", "e1"))
        actions.write("sheetEditSave", "e1")
        assertEquals("op-2", lastOp())
    }

    @Test
    fun aWriteWhoseAnswerWasDroppedDoesNotLendItsIdToALaterSheet() {
        // Log lands and closes the sheet, but Food closed before the answer went out
        actions.write("sheetLog", "Banana")
        actions.sheetClosed()
        // a later sheet: the same food logged again is a new operation, not "Already recorded."
        clock += 10_000
        actions.write("sheetLog", "Banana")
        assertEquals("op-2", lastOp())
    }

    @Test
    fun anAnswerEndsItsOperationEvenAfterAnotherWriteTookOver() {
        actions.write("sheetLog", "Banana")
        clock += 5_000 // past WAIT_MS: another write may go
        actions.write("sheetFav", "Rice")
        actions.answered(FoodAnswer("op-1", ok = true, changed = true, message = ""))
        assertNull(actions.operation("sheetLog", "Banana"))
        assertTrue(actions.waiting) // the favourite still waits for its own answer
    }

    @Test
    fun aRefusalIsFinalToo() {
        actions.write("sheetCreateSave", "create")
        actions.answered(
            FoodAnswer("op-1", ok = false, changed = false, message = "Give it a name")
        )
        actions.write("sheetCreateSave", "create")
        assertEquals("op-2", lastOp())
    }

    @Test
    fun anAnswerThatNeverCameLetsTheSameOperationGoAgain() {
        actions.write("sheetGoalSave", "goal")
        clock += 5000
        assertFalse(actions.waiting)
        actions.write("sheetGoalSave", "goal")
        assertEquals(2, sent.size)
        assertEquals("op-1", lastOp())
    }

    @Test
    fun anotherOperationsAnswerIsIgnored() {
        actions.write("sheetLog", "Rice")
        actions.answered(FoodAnswer("op-9", ok = true, changed = true, message = ""))
        actions.answered(null)
        assertTrue(actions.waiting)
        assertEquals("op-1", actions.operation("sheetLog", "Rice"))
    }

    @Test
    fun eachTargetHasItsOwnOperation() {
        actions.write("noticeAction", "1", mapOf("id" to 1))
        clock += 5000
        actions.write("noticeAction", "2", mapOf("id" to 2))
        assertNotEquals(sent[0].second["op"], sent[1].second["op"])
        assertEquals("op-1", actions.operation("noticeAction", "1"))
    }

    @Test
    fun anAmountTypedWithAPointIsTheSameNumber() {
        assertTrue(sameNumber("1.", "1"))
        assertTrue(sameNumber("175", "175.0"))
        assertFalse(sameNumber("17", "175"))
        assertTrue(sameNumber("", ""))
        assertFalse(sameNumber("", "0"))
    }

    @Test
    fun aDecimalBoxKeepsDigitsAndOnePoint() {
        assertEquals("12.5", decimalOnly("12.5"))
        assertEquals("12.55", decimalOnly("1a2.5.5"))
        assertEquals("", decimalOnly("abc"))
    }

    @Test
    fun boxesOfOneGroupShareAGrid() {
        fun f(key: String, group: Int?) =
            CreateField(key, false, key, null, null, null, "", null, group)
        val rows =
            createRows(
                listOf(f("name", null), f("brand", null), f("kcal", 1), f("p", 1), f("c", 1))
            )
        assertEquals(listOf(1, 1, 3), rows.map { it.size })
    }
}
