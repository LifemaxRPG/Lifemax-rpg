package com.lifemax.rpg.feature.overview

import com.lifemax.rpg.feature.overview.data.WebOverviewRepository
import com.lifemax.rpg.feature.overview.domain.EditorBuzz
import com.lifemax.rpg.feature.overview.domain.OverviewLayout
import com.lifemax.rpg.feature.overview.domain.OverviewReply
import com.lifemax.rpg.feature.overview.domain.editorRows
import com.lifemax.rpg.feature.overview.presentation.OverviewActions
import com.lifemax.rpg.feature.overview.presentation.OverviewViewModel
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The native editor's state (phase 2, OverviewEditController through the view model): a change
 * shows at once and goes to the page whole; the page's answer settles it, a refusal snapping back
 * to the page's layout with the page's words; the drop, eye, Reset and move buzzes only once
 * stored; the screen going behind the page ends edit mode (plan §5, OverviewViewModelTest's phase 2
 * list).
 */
class OverviewEditControllerTest {
    private fun fixture(name: String = "established") =
        JSONObject(javaClass.getResource("/fixtures/overview-cards-$name.json")!!.readText())

    private inner class Setup {
        val repo = WebOverviewRepository().apply { accept(fixture(), true) }
        val sent = OverviewRecorder()
        val buzzes = mutableListOf<EditorBuzz>()
        val vm =
            OverviewViewModel(repo, sent).apply {
                onBuzz = { buzzes += it }
                opened()
            }
        val actions = OverviewActions(vm)
        val editor = vm.editor
        val start = vm.cards!!.layout
        var sequence = 1L

        /** The page's projection after it stored [layout] (or kept the one it had). */
        fun project(layout: OverviewLayout) {
            val j = fixture()
            j.put(
                "layout",
                JSONObject()
                    .put("order", JSONArray(layout.order))
                    .put("hidden", JSONArray(layout.hidden)),
            )
            repo.accept(j.put("sequence", ++sequence))
            vm.projected()
        }

        /**
         * The page's answer to request [id], with the projection it comes with (accepted first).
         */
        fun answer(id: String, ok: Boolean, stored: OverviewLayout, message: String = "") {
            project(stored)
            val command = id.substringBefore('-')
            vm.onReply(OverviewReply(id, command, ok, ok, message))
        }

        val order
            get() = editor.rows.map { it.id }

        val hidden
            get() = editor.rows.filter { it.hidden }.map { it.id }
    }

    @Test
    fun editOpensTheEditorAndEditOrDoneClosesIt() {
        val s = Setup()
        assertFalse(s.editor.editing)
        s.actions.edit()
        assertTrue(s.editor.editing)
        s.actions.edit() // the page's ovEditOpen closes an open editor
        assertFalse(s.editor.editing)
        s.actions.edit()
        s.editor.close()
        assertFalse(s.editor.editing)
        assertTrue("Edit asks the page for nothing", s.sent.sent.isEmpty())
    }

    @Test
    fun theRowsAreThePagesTilesInItsOrderWithItsWords() {
        val s = Setup()
        val rows = s.editor.rows
        assertEquals(s.start.order, rows.map { it.id })
        val cat = rows.first { it.id == "cat" }
        assertEquals("Appears once you adopt a cat", cat.sub) // the page's note before its sub
        assertEquals("Cat", cat.name)
        assertEquals("Life Director’s live day dial", rows.first().sub)
        val words = s.vm.cards!!.editor
        assertEquals("Edit Overview", words.title)
        assertEquals("Hide ", words.hide)
        assertEquals("Move to bottom", words.moveBottom)
        // a hidden tile, and an id the page does not name, as the rows show them
        val info = s.vm.cards!!.tileInfo
        val r = editorRows(OverviewLayout(listOf("luck", "nope", "plan"), listOf("plan")), info)
        assertEquals(listOf("luck" to false, "plan" to true), r.map { it.id to it.hidden })
    }

    @Test
    fun aDropShowsAtOnceAndBuzzesOnceStored() {
        val s = Setup()
        s.actions.edit()
        val moved = listOf("next", "plan") + s.start.order.drop(2)
        s.editor.reorder(moved, EditorBuzz.Drop)
        assertEquals(moved, s.order) // before the page answers
        assertEquals(listOf("layout" to listOf(moved.joinToString(","), "")), s.sent.sent)
        assertTrue(s.buzzes.isEmpty())
        s.answer("layout-1", ok = true, stored = OverviewLayout(moved, emptyList()))
        assertEquals(listOf(EditorBuzz.Drop), s.buzzes)
        assertNull(s.editor.pending)
        assertEquals(moved, s.order) // now the page's own
        assertNull(s.vm.notice)
    }

    @Test
    fun aRefusedLayoutSnapsBackWithThePagesWords() {
        val s = Setup()
        s.actions.edit()
        s.editor.reorder(s.start.order.reversed(), EditorBuzz.Drop)
        assertEquals(s.start.order.reversed(), s.order)
        s.answer(
            "layout-1",
            ok = false,
            stored = s.start,
            message = "Could not save to this browser (storage full?)",
        )
        assertEquals(s.start.order, s.order)
        assertEquals("Could not save to this browser (storage full?)", s.vm.notice!!.text)
        assertTrue("no buzz for a change not stored", s.buzzes.isEmpty())
        assertTrue(s.editor.editing) // the editor stays open, showing the page's layout
    }

    @Test
    fun aRefusalWithNoWordsStillSaysSo() {
        val s = Setup()
        s.actions.edit()
        s.editor.toggle("luck")
        s.answer("layout-1", ok = false, stored = s.start)
        assertEquals("Nothing was changed.", s.vm.notice!!.text)
        assertTrue(s.hidden.isEmpty())
    }

    @Test
    fun theEyeHidesAndShowsATileAndBuzzesOnceStored() {
        val s = Setup()
        s.actions.edit()
        s.editor.toggle("luck")
        assertEquals(listOf("luck"), s.hidden)
        assertEquals(
            "layout" to listOf(s.start.order.joinToString(","), "luck"),
            s.sent.sent.last(),
        )
        val hiddenLuck = OverviewLayout(s.start.order, listOf("luck"))
        s.answer("layout-1", ok = true, stored = hiddenLuck)
        assertEquals(listOf(EditorBuzz.Toggle), s.buzzes)
        s.editor.toggle("luck")
        assertTrue(s.hidden.isEmpty())
        assertEquals("layout" to listOf(s.start.order.joinToString(","), ""), s.sent.sent.last())
        s.editor.toggle("nope") // not a tile of the layout: nothing
        assertEquals(2, s.sent.sent.size)
    }

    @Test
    fun resetShowsTheDefaultAtOnceAndSendsLayoutReset() {
        val s = Setup()
        val custom = OverviewLayout(s.start.order.reversed(), listOf("cat", "week"))
        s.project(custom)
        s.actions.edit()
        assertEquals(custom.order, s.order)
        s.editor.reset()
        assertEquals(s.vm.cards!!.tileInfo.map { it.id }, s.order)
        assertTrue(s.hidden.isEmpty())
        assertEquals("layoutReset" to emptyList<String>(), s.sent.sent.last())
        s.answer("layoutReset-1", ok = true, stored = s.start)
        assertEquals(listOf(EditorBuzz.Reset), s.buzzes)
        // a refused Reset keeps the page's layout, hidden tiles and all
        s.project(custom)
        s.editor.reset()
        s.answer("layoutReset-2", ok = false, stored = custom, message = "Not saved")
        assertEquals(custom.order, s.order)
        assertEquals(listOf("cat", "week"), s.hidden.sorted())
        assertEquals("Not saved", s.vm.notice!!.text)
        assertEquals(listOf(EditorBuzz.Reset), s.buzzes)
    }

    @Test
    fun aTalkBackMoveBuzzesItsOwnBuzzOnceStored() {
        val s = Setup()
        s.actions.edit()
        val moved = s.start.order.drop(1) + s.start.order.first()
        s.editor.reorder(moved, EditorBuzz.Move)
        s.answer("layout-1", ok = true, stored = OverviewLayout(moved, emptyList()))
        assertEquals(listOf(EditorBuzz.Move), s.buzzes)
        s.editor.reorder(moved, EditorBuzz.Move) // the order it has: nothing to send
        assertEquals(1, s.sent.sent.size)
    }

    @Test
    fun onlyTheLatestChangeSettlesTheLayoutShown() {
        val s = Setup()
        s.actions.edit()
        val first = listOf("next", "plan") + s.start.order.drop(2)
        s.editor.reorder(first, EditorBuzz.Drop)
        s.editor.toggle("luck") // sent before the first is answered: it carries the first's order
        assertEquals("layout" to listOf(first.joinToString(","), "luck"), s.sent.sent.last())
        // the first is stored; the second, shown ahead, stays shown until it is answered
        s.answer("layout-1", ok = true, stored = OverviewLayout(first, emptyList()))
        assertEquals(listOf(EditorBuzz.Drop), s.buzzes)
        assertEquals(listOf("luck"), s.hidden)
        assertEquals(first, s.order)
        // the second is refused: the page's layout (the first's) shows, with its words, once
        s.answer("layout-2", ok = false, stored = OverviewLayout(first, emptyList()), "Full")
        assertTrue(s.hidden.isEmpty())
        assertEquals(first, s.order)
        assertEquals("Full", s.vm.notice!!.text)
        assertEquals(listOf(EditorBuzz.Drop), s.buzzes)
    }

    @Test
    fun anOlderRefusalLeavesTheLatestChangeShown() {
        val s = Setup()
        s.actions.edit()
        s.editor.toggle("luck")
        s.editor.toggle("cat")
        s.answer("layout-1", ok = false, stored = s.start, message = "Full")
        assertEquals(listOf("cat", "luck"), s.hidden) // in the rows' order
        assertNull("the latest answers for the layout shown", s.vm.notice)
        val both = OverviewLayout(s.start.order, listOf("luck", "cat"))
        s.answer("layout-2", ok = true, stored = both)
        assertEquals(listOf(EditorBuzz.Toggle), s.buzzes)
        assertEquals(listOf("cat", "luck"), s.hidden) // in the rows' order
    }

    @Test
    fun theEditorEndsWhenTheScreenHidesAndAnAnswerNeverComes() {
        val s = Setup()
        s.actions.edit()
        s.editor.reorder(s.start.order.reversed(), EditorBuzz.Drop)
        s.vm.hidden()
        assertFalse(s.editor.editing)
        assertNull(s.editor.pending)
        assertEquals(s.start.order, s.order)
        s.vm.opened()
        assertFalse(s.editor.editing) // reopened on the cards, not the editor
        // the answer of before, should it come late, is waited for no more
        s.answer("layout-1", ok = false, stored = s.start, message = "Full")
        assertTrue(s.buzzes.isEmpty())
        assertNull(s.vm.notice)
    }

    @Test
    fun otherRepliesAreNotTheEditors() {
        val s = Setup()
        s.actions.edit()
        s.editor.toggle("luck")
        s.vm.onReply(OverviewReply("tip-9", "tip", false, false, "No"))
        assertEquals(listOf("luck"), s.hidden) // still waiting for its own answer
        assertTrue(s.buzzes.isEmpty())
    }
}
