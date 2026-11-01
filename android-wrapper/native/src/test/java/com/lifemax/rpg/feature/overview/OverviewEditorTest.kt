package com.lifemax.rpg.feature.overview

import androidx.compose.runtime.MonotonicFrameClock
import com.lifemax.rpg.feature.overview.domain.EditorBuzz
import com.lifemax.rpg.feature.overview.domain.EditorRow
import com.lifemax.rpg.feature.overview.presentation.editor.EditorMove
import com.lifemax.rpg.feature.overview.presentation.editor.ReorderState
import com.lifemax.rpg.feature.overview.presentation.editor.autoScrollStep
import com.lifemax.rpg.feature.overview.presentation.editor.moved
import com.lifemax.rpg.feature.overview.presentation.editor.shiftFor
import com.lifemax.rpg.feature.overview.presentation.editor.slotFor
import com.lifemax.rpg.feature.overview.presentation.editor.talkBackActions
import com.lifemax.rpg.feature.overview.presentation.editor.tops
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The native editor's reorder maths and TalkBack moves, the page's rules (31-overview-edit.js). */
class OverviewEditorTest {
    private val order = listOf("plan", "next", "hero", "attrs", "week")

    /** Rows 100 px tall, 8 px apart: centres at 50, 158, 266, 374, 482. */
    private val mids = listOf(50f, 158f, 266f, 374f, 482f)

    @Test
    fun aRowStaysUntilItsCentrePassesANeighboursCentre() {
        assertEquals(1, slotFor(mids, 1, 158f + 107f))
        assertEquals(2, slotFor(mids, 1, 158f + 109f))
        assertEquals(1, slotFor(mids, 1, 158f - 107f))
        assertEquals(0, slotFor(mids, 1, 158f - 109f))
    }

    @Test
    fun downwardsItTakesTheLastCentrePassedAndUpwardsTheFirst() {
        assertEquals(3, slotFor(mids, 0, 400f))
        assertEquals(4, slotFor(mids, 0, 9_999f))
        assertEquals(1, slotFor(mids, 4, 100f))
        assertEquals(0, slotFor(mids, 4, -9_999f))
    }

    @Test
    fun thePassedRowsStandAsideOneStepTowardsWhereItCameFrom() {
        // row 1 over slot 3: rows 2 and 3 move up a step, the others stay
        assertEquals(listOf(0, 0, -1, -1, 0), order.indices.map { shiftFor(it, 1, 3) })
        // row 3 over slot 1: rows 1 and 2 move down a step
        assertEquals(listOf(0, 1, 1, 0, 0), order.indices.map { shiftFor(it, 3, 1) })
        assertEquals(listOf(0, 0, 0, 0, 0), order.indices.map { shiftFor(it, 2, 2) })
    }

    @Test
    fun aDropMovesTheRowToItsSlot() {
        assertEquals(listOf("plan", "hero", "attrs", "next", "week"), moved(order, 1, 3))
        assertEquals(listOf("attrs", "plan", "next", "hero", "week"), moved(order, 3, 0))
        assertEquals(order, moved(order, 2, 2))
        assertEquals(order, moved(order, 2, 9))
    }

    @Test
    fun theStepIsARowAndTheGap() {
        val top = tops(listOf("a", "b", "c"), { if (it == "b") 120f else 100f }, 8f)
        assertEquals(mapOf("a" to 0f, "b" to 108f, "c" to 236f), top)
    }

    @Test
    fun theListScrollsNearTheWindowsEdges() {
        val d = 2.625f
        val h = 2_200f
        assertEquals(-8 * d, autoScrollStep(89 * d, h, d))
        assertEquals(0f, autoScrollStep(91 * d, h, d))
        assertEquals(0f, autoScrollStep(h - 111 * d, h, d))
        assertEquals(8 * d, autoScrollStep(h - 109 * d, h, d))
    }

    @Test
    fun talkBackMovesGiveTheNewOrder() {
        assertEquals(
            listOf("next", "plan", "hero", "attrs", "week"),
            EditorMove.Up.apply(order, "next"),
        )
        assertEquals(
            listOf("plan", "hero", "next", "attrs", "week"),
            EditorMove.Down.apply(order, "next"),
        )
        assertEquals(
            listOf("attrs", "plan", "next", "hero", "week"),
            EditorMove.Top.apply(order, "attrs"),
        )
        assertEquals(
            listOf("plan", "hero", "attrs", "week", "next"),
            EditorMove.Bottom.apply(order, "next"),
        )
        assertNull(EditorMove.Up.apply(order, "plan"))
        assertNull(EditorMove.Top.apply(order, "plan"))
        assertNull(EditorMove.Down.apply(order, "week"))
        assertNull(EditorMove.Bottom.apply(order, "week"))
        assertNull(EditorMove.Up.apply(order, "cat"))
    }

    @Test
    fun aRowOffersOnlyTheMovesItCanMake() {
        val words = pageEditorWords()
        fun labels(id: String) = talkBackActions(row(id), order, words) {}.map { it.label }
        assertEquals(listOf("Move down", "Move to bottom"), labels("plan"))
        assertEquals(
            listOf("Move up", "Move down", "Move to top", "Move to bottom"),
            labels("hero"),
        )
        assertEquals(listOf("Move up", "Move to top"), labels("week"))
    }

    @Test
    fun aTalkBackActionHandsOverTheMovedOrder() {
        var got: List<String>? = null
        val actions = talkBackActions(row("hero"), order, pageEditorWords()) { got = it }
        actions.first { it.label == "Move to top" }.action()
        assertEquals(listOf("hero", "plan", "next", "attrs", "week"), got)
    }

    @Test
    fun aDragLiftsAndCrossesWithThePagesBuzzesAndLeavesTheDropsToTheSave() {
        val buzzes = mutableListOf<EditorBuzz>()
        val state =
            ReorderState(order, immediate, reduced = true, gap = 8f, buzz = { buzzes += it })
        order.forEach { state.measured(it, 100f) }
        var dropped: List<String>? = null
        state.start("next")
        state.drag(60f) // centre at 218: not past hero's 266
        state.drag(60f) // 278: past it
        state.drag(20f) // 298: still slot 2, no second buzz
        state.drop { dropped = it }
        assertEquals(listOf("plan", "hero", "next", "attrs", "week"), dropped)
        assertEquals(dropped, state.order)
        // the drop's buzz comes once the page has stored the order (OverviewEditController)
        assertEquals(listOf(EditorBuzz.Lift, EditorBuzz.Cross), buzzes)
        assertNull(state.dragging)
    }

    @Test
    fun aDropWhereItStartedChangesNothing() {
        val buzzes = mutableListOf<EditorBuzz>()
        val state =
            ReorderState(order, immediate, reduced = true, gap = 8f, buzz = { buzzes += it })
        order.forEach { state.measured(it, 100f) }
        var dropped: List<String>? = null
        state.start("hero")
        state.drag(30f)
        state.drop { dropped = it }
        assertNull(dropped)
        assertEquals(order, state.order)
        assertEquals(listOf(EditorBuzz.Lift), buzzes)
    }

    @Test
    fun theOrderFromOutsideIsShownButNotDuringADrag() {
        val state = ReorderState(order, immediate, reduced = true, gap = 8f, buzz = {})
        order.forEach { state.measured(it, 100f) }
        val reset = order.reversed()
        state.start("plan")
        state.show(reset)
        assertEquals(order, state.order)
        state.drop {}
        state.show(reset)
        assertEquals(reset, state.order)
    }

    @Test
    fun thePagesBuzzLengths() {
        assertEquals(listOf(8L), EditorBuzz.Open.timings.toList())
        assertEquals(listOf(10L), EditorBuzz.Lift.timings.toList())
        assertEquals(listOf(4L), EditorBuzz.Cross.timings.toList())
        assertEquals(listOf(8L), EditorBuzz.Drop.timings.toList())
        assertEquals(listOf(6L), EditorBuzz.Toggle.timings.toList())
        assertEquals(listOf(8L, 30L, 8L), EditorBuzz.Reset.timings.toList())
        assertEquals(listOf(5L), EditorBuzz.Move.timings.toList())
    }

    private fun row(id: String) =
        EditorRow(id, id.replaceFirstChar { it.uppercase() }, "", "star", 0xFF7C9CFF)

    /** Runs every animation at once: reduced motion makes each a single frame. */
    private val immediate =
        CoroutineScope(
            Dispatchers.Unconfined +
                object : MonotonicFrameClock {
                    override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R =
                        onFrame(System.nanoTime())
                }
        )
}
