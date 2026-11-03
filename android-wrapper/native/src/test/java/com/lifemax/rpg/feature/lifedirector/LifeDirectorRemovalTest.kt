package com.lifemax.rpg.feature.lifedirector

import com.lifemax.rpg.feature.lifedirector.data.WebLifeDirectorRepository
import com.lifemax.rpg.feature.lifedirector.data.directorReply
import com.lifemax.rpg.feature.lifedirector.domain.DirectorNotice
import com.lifemax.rpg.feature.lifedirector.domain.DragPoint
import com.lifemax.rpg.feature.lifedirector.domain.OrderRow
import com.lifemax.rpg.feature.lifedirector.domain.Removal
import com.lifemax.rpg.feature.lifedirector.presentation.LifeDirectorViewModel
import com.lifemax.rpg.feature.lifedirector.presentation.drag.DragPhase
import com.lifemax.rpg.feature.lifedirector.presentation.drag.Press
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Taking a session off the day (director.remove.v1): what the page says about each row, the lift
 * and the drop with a fake command channel, the removal and Undo, a refusal, a save that fails and
 * a request sent again with its operation id.
 */
class LifeDirectorRemovalTest {
    private fun fixture(name: String) =
        JSONObject(javaClass.getResource("/fixtures/director-$name.json")!!.readText())

    private val replies = fixture("replies-remove")

    private fun reply(name: String, command: String = "today", requestId: String) =
        directorReply(
            JSONObject(replies.getJSONObject(name).toString())
                .put("command", command)
                .put("requestId", requestId)
        )

    private class Screen(val repo: WebLifeDirectorRepository, val commands: DirectorRecorder) {
        val vm = LifeDirectorViewModel(repo, commands).apply { opened() }
        val drag = vm.removal

        init {
            // a 1000 by 2000 px screen with 200 px of bars, 2.5 px to the dp
            drag.layout(1000f, 2000f, 150f, 200f, 2.5f)
        }

        val rows: List<OrderRow>
            get() = repo.state.value!!.today!!.let { it.ahead + it.earlier?.rows.orEmpty() }

        fun session(title: String) = rows.first { it.title == title }

        val toast
            get() = vm.ui.value.toast?.text

        val undo
            get() = vm.ui.value.undo

        /** The finger on the trash: the circle's centre. */
        val onTrash
            get() = drag.target.value!!.centre

        /** Presses, lifts and drops [title] on the trash; the page has been asked to remove it. */
        fun dropOnTrash(title: String): OrderRow {
            val row = session(title)
            drag.lift(row, DragPoint(300f, 900f))
            drag.move(onTrash)
            drag.release()
            return row
        }

        val sent
            get() = commands.todays
    }

    private fun screen(fixture: String = "remove") =
        Screen(
            WebLifeDirectorRepository().apply { accept(fixture(fixture), true) },
            DirectorRecorder(),
        )

    private fun Screen.id(title: String) = session(title).tap!!.id

    // What the page says about a row

    @Test
    fun everySessionRowSaysWhetherItCanBeRemovedAndWhy() {
        val s = screen()
        assertEquals(Removal(true, ""), s.session("Resistance Training").removal)
        assertEquals(
            Removal(false, "It is already off the plan."),
            s.session("Full care day").removal,
        )
        // blocks that are not sessions say nothing
        assertNull(s.session("Work").removal)
        assertNull(s.session("Dinner").removal)
    }

    @Test
    fun aPageThatDoesNotOfferRemovalMarksNothingAndNothingLifts() {
        val s = screen("today")
        assertTrue(s.rows.all { it.removal == null })
        assertEquals(Press.Ignore, s.drag.press(s.session("Resistance Training").key))
    }

    private fun rejects(edit: JSONObject.() -> Unit) {
        val j = fixture("remove")
        val row =
            j.getJSONObject("today").getJSONObject("rows").getJSONArray("ahead").getJSONObject(5)
        row.edit()
        try {
            WebLifeDirectorRepository().accept(j, true)
        } catch (_: IllegalArgumentException) {
            return
        } catch (_: org.json.JSONException) {
            return
        }
        throw AssertionError("accepted a malformed removal")
    }

    @Test
    fun theDecoderIsStrictAboutTheRemovalField() {
        rejects { put("remove", "yes") }
        rejects { put("remove", JSONObject().put("ok", true)) }
        rejects { put("remove", JSONObject().put("ok", 1).put("reason", "")) }
        rejects { put("remove", JSONObject().put("ok", false).put("reason", " ")) }
    }

    @Test
    fun theUndoWordsComeWithTheReply() {
        assertEquals("Skipped · 1 re-placed", reply("skip", requestId = "x").undo)
        assertEquals("", reply("unskip", requestId = "x").undo)
    }

    // What a finger that stays on a row means

    @Test
    fun aRemovableSessionLiftsAndOneThatCannotRefusesWithItsReason() {
        val s = screen()
        val lift = s.drag.press(s.session("Resistance Training").key)
        assertTrue(lift is Press.Lift)
        assertEquals(
            Press.Refuse("It is already off the plan."),
            s.drag.press(s.session("Full care day").key),
        )
        assertEquals(Press.Ignore, s.drag.press(s.session("Work").key))
        assertEquals(Press.Ignore, s.drag.press("not a row"))
        s.drag.refuse("It is already off the plan.")
        assertEquals("It is already off the plan.", s.toast)
    }

    @Test
    fun nothingLiftsWhileAnotherThingHoldsTheScreen() {
        val s = screen()
        val key = s.session("Resistance Training").key
        // an open session sheet holds the screen
        s.vm.openSession(s.id("Resistance Training"))
        assertEquals(Press.Ignore, s.drag.press(key))
    }

    @Test
    fun onlyOneSessionIsLiftedAtATime() {
        val s = screen()
        s.drag.lift(s.session("Resistance Training"), DragPoint(300f, 900f))
        assertEquals(Press.Ignore, s.drag.press(s.session("Focused Reading").key))
    }

    // The drag

    @Test
    fun liftingHoldsTheCardAndMovingOverTheTrashArmsIt() {
        val s = screen()
        val row = s.session("Resistance Training")
        s.drag.lift(row, DragPoint(300f, 900f))
        assertEquals(DragPhase.HELD, s.drag.held.value!!.phase)
        assertEquals(row, s.drag.held.value!!.row)
        assertFalse(s.drag.armed.value)
        assertFalse(s.drag.move(DragPoint(310f, 950f)))
        assertEquals(DragPoint(310f, 950f), s.drag.finger.value)
        assertTrue("arming is reported once", s.drag.move(s.onTrash))
        assertTrue(s.drag.armed.value)
        assertFalse(s.drag.move(s.onTrash))
        assertTrue("leaving is reported once", s.drag.move(DragPoint(100f, 300f)))
        assertFalse(s.drag.armed.value)
    }

    @Test
    fun letGoAwayFromTheTrashPutsItBackAndSendsNothing() {
        val s = screen()
        s.drag.lift(s.session("Resistance Training"), DragPoint(300f, 900f))
        s.drag.move(DragPoint(200f, 700f))
        s.drag.release()
        assertEquals(DragPhase.RETURNING, s.drag.held.value!!.phase)
        assertTrue(s.sent.isEmpty())
        s.drag.settled()
        assertNull(s.drag.held.value)
        assertEquals(Press.Lift::class, s.drag.press(s.session("Resistance Training").key)::class)
    }

    @Test
    fun cancellingOverTheTrashStillPutsItBack() {
        val s = screen()
        s.drag.lift(s.session("Resistance Training"), DragPoint(300f, 900f))
        s.drag.move(s.onTrash)
        s.drag.cancel()
        assertEquals(DragPhase.RETURNING, s.drag.held.value!!.phase)
        assertFalse(s.drag.armed.value)
        assertTrue(s.sent.isEmpty())
        s.drag.release() // a finger that lets go afterwards removes nothing
        assertTrue(s.sent.isEmpty())
    }

    @Test
    fun theScrollSpeedFollowsTheFingerAndStopsOverTheTrash() {
        val s = screen()
        s.drag.lift(s.session("Resistance Training"), DragPoint(300f, 900f))
        assertEquals(0f, s.drag.scrollSpeed(), 0f)
        s.drag.move(DragPoint(300f, 160f))
        assertTrue(s.drag.scrollSpeed() < 0f)
        s.drag.move(DragPoint(300f, 1400f))
        assertTrue(s.drag.scrollSpeed() > 0f)
        s.drag.move(s.onTrash)
        assertEquals(0f, s.drag.scrollSpeed(), 0f)
    }

    // Removing

    @Test
    fun droppingOnTheTrashAsksThePageToSkipThatSessionWithAnOperationId() {
        val s = screen()
        val row = s.dropOnTrash("Resistance Training")
        assertEquals(DragPhase.DROPPING, s.drag.held.value!!.phase)
        assertEquals(1, s.sent.size)
        val (name, args) = s.sent.single()
        assertEquals("skip", name)
        assertEquals(row.tap!!.id, args["id"])
        assertTrue((args["op"] as String).isNotEmpty())
    }

    @Test
    fun theRemovalLandingOffersUndoInThePagesWordsAndHidesTheToast() {
        val s = screen()
        s.dropOnTrash("Resistance Training")
        s.vm.notice(
            "director-fixture",
            DirectorNotice(7, "Skipped · 1 re-placed"),
        ) // the notice, first
        assertEquals("Skipped · 1 re-placed", s.toast)
        s.vm.onReply(reply("skip", requestId = "today-1"))
        assertNull(s.drag.held.value)
        val offer = s.undo!!
        assertEquals("Skipped · 1 re-placed", offer.text)
        assertEquals(s.id("Resistance Training"), offer.id)
        assertNull("Undo stands in for the toast", s.toast)
    }

    @Test
    fun aNoticeThatArrivesAfterTheOfferIsNotAlsoToasted() {
        val s = screen()
        s.dropOnTrash("Resistance Training")
        s.vm.onReply(reply("skip", requestId = "today-1"))
        s.vm.notice("director-fixture", DirectorNotice(7, "Skipped · 1 re-placed"))
        assertNull(s.toast)
        s.vm.notice("director-fixture", DirectorNotice(8, "Something else"))
        assertEquals("Something else", s.toast)
    }

    @Test
    fun theOfferExpires() {
        val s = screen()
        s.dropOnTrash("Resistance Training")
        s.vm.onReply(reply("skip", requestId = "today-1"))
        val offer = s.undo!!
        s.drag.dismissUndo(offer.serial + 1) // an offer that is not this one
        assertNotNull(s.undo)
        s.drag.dismissUndo(offer.serial)
        assertNull(s.undo)
    }

    @Test
    fun aRefusedRemovalSpringsBackAndSaysWhy() {
        val s = screen()
        s.dropOnTrash("Resistance Training")
        s.vm.onReply(reply("skipRunning", requestId = "today-1"))
        assertEquals(DragPhase.RETURNING, s.drag.held.value!!.phase)
        assertEquals("Its timer is running. Stop the timer first.", s.toast)
        assertNull(s.undo)
    }

    @Test
    fun aRefusalIsATerminalReplyAndTheNextAttemptIsANewOperation() {
        val s = screen()
        s.dropOnTrash("Resistance Training")
        val first = s.sent.single().second["op"]
        s.vm.onReply(reply("skipRunning", requestId = "today-1"))
        s.drag.settled()
        s.dropOnTrash("Resistance Training")
        assertEquals(2, s.sent.size)
        assertNotEquals(first, s.sent[1].second["op"])
    }

    @Test
    fun aSaveThatFailedKeepsTheOperationSoTheSameRequestLandsIt() {
        val s = screen()
        s.dropOnTrash("Resistance Training")
        val first = s.sent.single().second["op"]
        val unsaved =
            JSONObject()
                .put("command", "today")
                .put("requestId", "today-1")
                .put("ok", false)
                .put("message", "Not saved: The quota has been exceeded.")
        s.vm.onReply(directorReply(unsaved))
        assertEquals("Not saved: The quota has been exceeded.", s.toast)
        assertNull(s.drag.held.value)
        s.drag.remove(s.session("Resistance Training"))
        assertEquals("the same operation again", first, s.sent[1].second["op"])
        // and it lands: now the id is free for a new operation
        s.vm.onReply(reply("skipAgain", requestId = "today-2"))
        assertEquals("Skipped · 1 re-placed", s.undo!!.text)
        s.drag.dismissUndo(s.undo!!.serial)
        s.drag.remove(s.session("Resistance Training"))
        assertNotEquals(first, s.sent[2].second["op"])
    }

    @Test
    fun aRequestWhoseReplyWasLostGoesAgainWithTheSameOperation() {
        val s = screen()
        s.dropOnTrash("Resistance Training")
        val first = s.sent.single().second["op"]
        s.vm.hidden() // the screen hid before the reply came
        assertNull(s.drag.held.value)
        s.vm.opened()
        s.drag.remove(s.session("Resistance Training"))
        assertEquals(first, s.sent[1].second["op"])
        s.vm.onReply(reply("skipAgain", requestId = "today-2"))
        assertEquals("a repeat still offers Undo", "Skipped · 1 re-placed", s.undo!!.text)
    }

    @Test
    fun aReplyThatIsNotForTheRequestIsNotTheRemovals() {
        val s = screen()
        s.dropOnTrash("Resistance Training")
        assertFalse(s.drag.onReply(reply("skip", requestId = "today-99")))
        assertFalse(s.drag.onReply(reply("skip", command = "sheet", requestId = "today-1")))
        assertEquals(DragPhase.DROPPING, s.drag.held.value!!.phase)
    }

    @Test
    fun onlyOneRequestIsInFlightAtATime() {
        val s = screen()
        s.dropOnTrash("Resistance Training")
        s.drag.remove(s.session("Focused Reading"))
        assertEquals(1, s.sent.size)
        assertEquals(Press.Ignore, s.drag.press(s.session("Focused Reading").key))
    }

    @Test
    fun aSessionThatCannotBeRemovedIsNotRemovedByTheTalkBackActionEither() {
        val s = screen()
        s.drag.remove(s.session("Full care day"))
        s.drag.remove(s.session("Work"))
        assertTrue(s.sent.isEmpty())
    }

    @Test
    fun talkBackRemovesWithNoDragAndTheSameMessage() {
        val s = screen()
        s.drag.remove(s.session("Resistance Training"))
        assertEquals("skip", s.sent.single().first)
        assertNull("there is no card to fly", s.drag.held.value)
        s.vm.onReply(reply("skip", requestId = "today-1"))
        assertEquals("Skipped · 1 re-placed", s.undo!!.text)
    }

    // Undo

    private fun Screen.removed(title: String = "Resistance Training"): String {
        dropOnTrash(title)
        vm.onReply(reply("skip", requestId = "today-${sent.size}"))
        return id(title)
    }

    @Test
    fun undoAsksThePageToTakeTheSkipBackAndClosesTheOffer() {
        val s = screen()
        val id = s.removed()
        s.drag.undo()
        assertNull(s.undo)
        val (name, args) = s.sent[1]
        assertEquals("unskip", name)
        assertEquals(id, args["id"])
        assertTrue((args["op"] as String).isNotEmpty())
        s.vm.onReply(reply("unskip", requestId = "today-2"))
        assertNull(s.toast)
        assertNull(s.undo)
    }

    @Test
    fun undoIsRefusedOrAlreadyDoneAndTheScreenSaysSo() {
        val s = screen()
        s.removed()
        s.drag.undo()
        s.vm.onReply(reply("unskipNothing", requestId = "today-2"))
        assertEquals("That can no longer be taken back.", s.toast)
        assertNull("a refusal is final: no offer comes back", s.undo)
        val t = screen()
        t.removed()
        t.drag.undo()
        t.vm.onReply(reply("unskipAgain", requestId = "today-2"))
        assertEquals("Already back on the plan.", t.toast)
    }

    @Test
    fun undoWhoseSaveFailedStaysOnOfferWithTheSameOperation() {
        val s = screen()
        s.removed()
        val offer = s.undo!!
        s.drag.undo()
        val first = s.sent[1].second["op"]
        val unsaved =
            JSONObject()
                .put("command", "today")
                .put("requestId", "today-2")
                .put("ok", false)
                .put("message", "Not saved: The quota has been exceeded.")
        s.vm.onReply(directorReply(unsaved))
        assertEquals(offer, s.undo)
        s.drag.undo()
        assertEquals(first, s.sent[2].second["op"])
    }

    @Test
    fun nothingToUndoSendsNothing() {
        val s = screen()
        s.drag.undo()
        assertTrue(s.sent.isEmpty())
    }

    // When the page changes under a lifted row

    @Test
    fun aLiftedSessionWhoseRowHasGoneFromThePageGoesBack() {
        val s = screen()
        s.drag.lift(s.session("Resistance Training"), DragPoint(300f, 900f))
        val gone = fixture("remove")
        val ahead = gone.getJSONObject("today").getJSONObject("rows").getJSONArray("ahead")
        val kept = org.json.JSONArray()
        for (i in 0 until ahead.length()) if (
            ahead.getJSONObject(i).getString("title") != "Resistance Training"
        )
            kept.put(ahead.getJSONObject(i))
        gone.getJSONObject("today").getJSONObject("rows").put("ahead", kept)
        gone.put("sequence", gone.getLong("sequence") + 1)
        s.repo.accept(gone)
        s.vm.projected()
        assertEquals(DragPhase.RETURNING, s.drag.held.value!!.phase)
    }

    @Test
    fun theScreenHidingDropsALiftAndKeepsNothingInFlight() {
        val s = screen()
        s.drag.lift(s.session("Resistance Training"), DragPoint(300f, 900f))
        s.vm.hidden()
        assertNull(s.drag.held.value)
        assertFalse(s.drag.isHeld)
        assertTrue(s.sent.isEmpty())
    }
}
