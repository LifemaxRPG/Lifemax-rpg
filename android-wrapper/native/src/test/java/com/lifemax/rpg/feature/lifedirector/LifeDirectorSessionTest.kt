package com.lifemax.rpg.feature.lifedirector

import com.lifemax.rpg.feature.lifedirector.data.WebLifeDirectorRepository
import com.lifemax.rpg.feature.lifedirector.domain.ChipKind
import com.lifemax.rpg.feature.lifedirector.domain.DirectorReply
import com.lifemax.rpg.feature.lifedirector.domain.GoneSheet
import com.lifemax.rpg.feature.lifedirector.domain.GuideSpan
import com.lifemax.rpg.feature.lifedirector.domain.OpenSheet
import com.lifemax.rpg.feature.lifedirector.domain.SessionMode
import com.lifemax.rpg.feature.lifedirector.domain.SessionSheetModel
import com.lifemax.rpg.feature.lifedirector.domain.SessionState
import com.lifemax.rpg.feature.lifedirector.domain.SheetPrimary
import com.lifemax.rpg.feature.lifedirector.domain.SheetRowButton
import com.lifemax.rpg.feature.lifedirector.domain.Tap
import com.lifemax.rpg.feature.lifedirector.presentation.DirectorActions
import com.lifemax.rpg.feature.lifedirector.presentation.LifeDirectorViewModel
import com.lifemax.rpg.feature.lifedirector.presentation.SheetController
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Today's session sheet and Today's writes (phase 4A), from the page's own projections. */
class LifeDirectorSessionTest {
    private fun fixture(name: String): JSONObject =
        JSONObject(javaClass.getResource("/fixtures/director-$name.json")!!.readText())

    private fun sheet(mode: String) = fixture("sheet-session-$mode")

    private fun repository(j: JSONObject) = WebLifeDirectorRepository().apply { accept(j, true) }

    private class Screen(
        val repo: WebLifeDirectorRepository,
        val vm: LifeDirectorViewModel,
        val commands: DirectorRecorder,
        val emitted: MutableList<Tap>,
        val actions: DirectorActions,
    ) {
        private var sequence = 1000L

        /** The page's next projection, changed by [edit]. */
        fun push(j: JSONObject, edit: JSONObject.() -> Unit = {}) {
            repo.accept(JSONObject(j.toString()).put("sequence", ++sequence).apply(edit))
            vm.projected()
        }

        val toast
            get() = vm.ui.value.toast?.text
    }

    private fun screen(j: JSONObject): Screen {
        val repo = repository(j)
        val commands = DirectorRecorder()
        val vm = LifeDirectorViewModel(repo, commands).apply { opened() }
        val emitted = mutableListOf<Tap>()
        return Screen(
            repo,
            vm,
            commands,
            emitted,
            DirectorActions(vm) { a, id -> emitted += Tap(a, id) },
        )
    }

    private fun Screen.openNow(): SessionSheetModel {
        val id = (repo.state.value!!.sheet as SessionSheetModel).id
        actions.tap(Tap("inst", id))
        vm.onReply(DirectorReply("sheet-${commands.sheets.size}", "sheet", true))
        return vm.session!!
    }

    private fun rejects(j: JSONObject) {
        val repo = WebLifeDirectorRepository()
        try {
            repo.accept(j, true)
        } catch (_: IllegalArgumentException) {
            return
        } catch (_: org.json.JSONException) {
            return
        }
        throw AssertionError("accepted a malformed sheet")
    }

    private fun JSONObject.sheetPart() = getJSONObject("sheet")

    // The decoder

    @Test
    fun decodesTheSessionSheetOnNowAsThePageProjectedIt() {
        val p = repository(sheet("now")).state.value!!
        assertTrue(p.sheets)
        val s = p.sheet as SessionSheetModel
        assertEquals("i_1ap3yp1xvo91m2q4qdhxpu", s.id)
        assertEquals("Resistance Training", s.title)
        assertEquals("dumbbell", s.icon)
        assertEquals(SessionState.PLAN, s.state)
        assertEquals(SessionMode.NOW, s.mode)
        assertEquals("Wed, Oct 28 · 7:15 PM–8:15 PM · 1h", s.`when`)
        assertEquals(listOf(ChipKind.QUEST, ChipKind.GOAL, ChipKind.XP), s.chips.map { it.kind })
        assertEquals(SheetPrimary("Start timer", command = "timer", act = "start"), s.primary)
        assertEquals(60, s.minutes!!.value)
        assertEquals(1..480, s.minutes!!.min..s.minutes!!.max)
        assertEquals("done", s.complete!!.command)
        assertEquals(
            listOf(
                SheetRowButton("Move", sheet = "move"),
                SheetRowButton("Pin here", command = "pin", on = true),
                SheetRowButton("Skip", command = "skip"),
            ),
            s.row,
        )
        assertEquals(listOf("17.1", "15.9", "13.9", "7.2"), s.why.factors.map { it.points })
        assertEquals(.7125f, s.why.factors[0].frac, .0001f)
        assertEquals("It could have fitted on 1 day.", s.why.feasible)
        assertEquals("", s.orphan)
    }

    @Test
    fun decodesTheLaterAndPastSheets() {
        val later = repository(sheet("later")).state.value!!.sheet as SessionSheetModel
        assertEquals(SessionMode.LATER, later.mode)
        assertNull(later.primary)
        assertEquals("logToday", later.complete!!.command)
        assertEquals(
            "You get the XP now, and this session stays on Thu.",
            later.complete!!.smallPrint,
        )
        assertEquals(GuideSpan("Thu, Oct 29 at 7:30 AM", true), later.note[1])
        assertEquals("12", later.why.factors[1].points)
        val past = repository(sheet("past")).state.value!!.sheet as SessionSheetModel
        assertEquals(SessionMode.PAST, past.mode)
        assertEquals(SessionState.DONE, past.state)
        assertEquals("Done · 30 min", past.past)
        assertNull(past.minutes)
        assertTrue(past.row.isEmpty())
    }

    @Test
    fun aPageBeforeThisPhaseSendsNoSheetAndAGoneSessionSaysSo() {
        val old = repository(fixture("today").apply { remove("sheet") }).state.value!!
        assertFalse(old.sheets)
        assertNull(old.sheet)
        val none = repository(fixture("today")).state.value!!
        assertTrue(none.sheets)
        assertNull(none.sheet)
        val gone =
            repository(
                    sheet("now")
                        .put("sheet", JSONObject("""{"kind":"session","id":"i-1","gone":true}"""))
                )
                .state
                .value!!
        assertEquals(GoneSheet("session", "i-1"), gone.sheet)
    }

    @Test
    fun aMalformedSheetIsRefused() {
        val edits: List<JSONObject.() -> Unit> =
            listOf(
                { remove("title") },
                { put("mode", "soon") },
                { put("state", "maybe") },
                { put("title", 7) },
                { put("primary", JSONObject.NULL) },
                { getJSONObject("minutes").put("value", 600) },
                { getJSONObject("complete").put("command", "logToday") },
                { getJSONArray("row").getJSONObject(0).put("sheet", "elsewhere") },
                { getJSONArray("row").getJSONObject(1).remove("on") },
                { getJSONArray("row").getJSONObject(2).put("command", "delete") },
                { getJSONObject("why").getJSONArray("factors").getJSONObject(0).put("frac", 1.5) },
                {
                    getJSONObject("why")
                        .getJSONArray("factors")
                        .getJSONObject(0)
                        .put("points", "17")
                },
                { getJSONArray("chips").getJSONObject(0).put("kind", "badge") },
                { put("kind", "meal") },
                { put("gone", false) },
            )
        for (edit in edits) rejects(sheet("now").apply { edit(sheetPart()) })
        // a past session with actions
        rejects(
            sheet("past").apply {
                sheetPart().put("row", sheet("now").sheetPart().getJSONArray("row"))
            }
        )
    }

    // The screen

    @Test
    fun aSessionTapOpensTheSheetHereAndCloseTellsThePage() {
        val s = screen(sheet("now"))
        assertNull(s.vm.session) // the page's model shows only once the screen asks for it
        val model = s.openNow()
        assertEquals(listOf("session" to model.id), s.commands.sheets)
        assertTrue(s.emitted.isEmpty())
        assertTrue(s.vm.busy)
        s.actions.tap(Tap("inst", "another")) // the list waits under the sheet
        assertEquals(1, s.commands.sheets.size)
        s.vm.closeSheet()
        assertEquals("" to "", s.commands.sheets.last())
        assertNull(s.vm.ui.value.sheet)
    }

    @Test
    fun aPageBeforeThisPhaseStillAnswersEveryTap() {
        val s = screen(fixture("today").apply { remove("sheet") })
        s.actions.tap(Tap("inst", "i-1"))
        assertEquals(listOf(Tap("inst", "i-1")), s.emitted)
        s.vm.opened()
        s.actions.tap(Tap("doneQuick", "i-1"))
        s.vm.opened()
        s.actions.tap(Tap("focusStart", "i-1"))
        assertEquals(3, s.emitted.size)
        assertTrue(s.commands.sheets.isEmpty() && s.commands.todays.isEmpty())
    }

    @Test
    fun upNextsStartAndDoneRunHere() {
        val s = screen(fixture("today"))
        val up = s.repo.state.value!!.today!!.upNext!!
        s.actions.upNext(up.actions[0]) // Start
        val (name, args) = s.commands.todays.single()
        assertEquals("timer", name)
        assertEquals("start", args["act"])
        assertEquals(up.actions[0].tap!!.id, args["id"])
        assertNotNull(args["op"])
        s.actions.upNext(up.actions[1]) // Done, while Start is in flight: dropped
        assertEquals(1, s.commands.todays.size)
        s.vm.onReply(DirectorReply("today-1", "today", true))
        s.actions.upNext(up.actions[1])
        assertEquals("done", s.commands.todays.last().first)
        assertFalse(s.commands.todays.last().second.containsKey("minutes")) // the planned length
        s.actions.upNext(up.actions[2]) // Details: the sheet
        assertTrue(s.emitted.isEmpty())
    }

    @Test
    fun markDoneSendsTheMinutesAndALandedWriteClosesTheSheet() {
        val s = screen(sheet("now"))
        val model = s.openNow()
        s.vm.sessionMinutes("9999") // three digits at most, then the page's limits
        s.vm.sessionComplete()
        val (name, args) = s.commands.todays.single()
        assertEquals("done", name)
        assertEquals(480, args["minutes"])
        assertEquals(model.id, args["id"])
        assertEquals("done", s.vm.ui.value.pendingToday)
        s.vm.onReply(DirectorReply("today-1", "today", true, "Already recorded."))
        assertNull(s.vm.ui.value.pendingToday)
        assertNull(s.vm.ui.value.sheet)
        assertEquals("" to "", s.commands.sheets.last())
        assertEquals("Already recorded.", s.toast)
        assertNull(s.vm.operation("done", model.id)) // the next tap is a new operation
    }

    @Test
    fun theStepperKeepsThePagesLimits() {
        val s = screen(sheet("now"))
        s.openNow()
        s.vm.sessionStep(5)
        assertEquals("65", s.vm.ui.value.minutesDraft!!.text)
        s.vm.sessionMinutes("3")
        s.vm.sessionStep(-5)
        assertEquals("1", s.vm.ui.value.minutesDraft!!.text)
        s.vm.sessionMinutes("")
        s.vm.sessionComplete()
        assertEquals(60, s.commands.todays.single().second["minutes"]) // empty: the planned length
    }

    @Test
    fun aWriteWhoseReplyWasLostIsSentAgainWithTheSameOperation() {
        val s = screen(sheet("later"))
        val model = s.openNow()
        s.vm.sessionComplete()
        val first = s.commands.todays.single().second
        assertEquals("logToday", s.commands.todays.single().first)
        s.vm.hidden() // the screen went behind the page before the reply
        assertNull(s.vm.ui.value.sheet)
        assertNull(s.vm.ui.value.pendingToday)
        s.vm.opened()
        s.vm.onReply(DirectorReply("today-1", "today", true)) // a reply for the hidden screen
        assertEquals(first["op"], s.vm.operation("logToday", model.id))
        s.actions.tap(Tap("inst", model.id))
        s.vm.onReply(DirectorReply("sheet-2", "sheet", true))
        s.vm.sessionComplete()
        assertEquals(first["op"], s.commands.todays.last().second["op"])
        s.vm.onReply(DirectorReply("today-2", "today", true, "Already recorded."))
        s.vm.openSession(model.id)
        s.vm.onReply(DirectorReply("sheet-4", "sheet", true))
        s.vm.sessionComplete()
        assertNotEquals(
            first["op"],
            s.commands.todays.last().second["op"],
        ) // logged again on purpose
    }

    @Test
    fun aRefusalShowsThePagesWordsAndOnlyANotSavedOneKeepsItsOperation() {
        val s = screen(sheet("now"))
        val model = s.openNow()
        s.vm.sessionRow(model.row.single { it.command == "skip" })
        val op = s.commands.todays.last().second["op"]
        s.vm.onReply(
            DirectorReply("today-1", "today", false, "Not saved: the quota has been exceeded.")
        )
        assertEquals("Not saved: the quota has been exceeded.", s.toast)
        assertNotNull(s.vm.ui.value.sheet) // the sheet stays
        s.vm.sessionRow(model.row.single { it.command == "skip" })
        assertEquals(op, s.commands.todays.last().second["op"])
        s.vm.onReply(
            DirectorReply(
                "today-2",
                "today",
                false,
                "You can start it on the day, or from 6 hours before.",
            )
        )
        assertEquals("You can start it on the day, or from 6 hours before.", s.toast)
        assertNull(s.vm.operation("skip", model.id))
    }

    @Test
    fun aSessionThatLeftThePlanClosesItsSheet() {
        val s = screen(sheet("now"))
        val model = s.openNow()
        s.push(sheet("now")) {
            put("sheet", JSONObject().put("kind", "session").put("id", model.id).put("gone", true))
        }
        assertNull(s.vm.ui.value.sheet)
        assertEquals(SheetController.GONE, s.toast)
        assertEquals("" to "", s.commands.sheets.last())
    }

    @Test
    fun aSheetThePageNoLongerSendsOrRefusesCloses() {
        val s = screen(sheet("now"))
        s.openNow()
        s.push(fixture("today")) // the page dropped it (it reopened)
        assertNull(s.vm.ui.value.sheet)
        s.vm.openSession("i-x")
        assertEquals(OpenSheet("session", "i-x"), s.vm.ui.value.sheet)
        s.vm.onReply(DirectorReply("sheet-2", "sheet", false, "Life Director cannot do that."))
        assertNull(s.vm.ui.value.sheet)
        assertEquals("Life Director cannot do that.", s.toast)
    }

    @Test
    fun pinStartsAndMoveAndTheTimerOpenHereAndPlanIsThePages() {
        val s = screen(sheet("now"))
        val model = s.openNow()
        s.actions.session.row(model.row.single { it.command == "pin" })
        assertEquals("pin" to true, s.commands.todays.last().let { it.first to it.second["on"] })
        s.vm.onReply(DirectorReply("today-1", "today", true))
        s.openNow()
        s.actions.session.primary()
        assertEquals(
            mapOf("act" to "start", "id" to model.id),
            s.commands.todays.last().second - "op",
        )
        s.vm.onReply(DirectorReply("today-2", "today", true))
        s.openNow()
        s.actions.session.row(model.row.single { it.sheet == "move" })
        assertTrue(s.emitted.isEmpty()) // Move is the screen's own sheet now
        assertEquals(OpenSheet("move", model.id), s.vm.ui.value.sheet)

        val running =
            screen(
                sheet("now").apply {
                    sheetPart()
                        .put("primary", JSONObject("""{"label":"Open timer","sheet":"timer"}"""))
                }
            )
        running.openNow()
        running.actions.session.primary()
        // Open timer opens the timer's own sheet here (LifeDirectorTimerMealTest)
        assertEquals(OpenSheet("timer", model.id), running.vm.ui.value.sheet)
        assertTrue(running.emitted.isEmpty())
        assertTrue(running.commands.todays.isEmpty())
    }
}
