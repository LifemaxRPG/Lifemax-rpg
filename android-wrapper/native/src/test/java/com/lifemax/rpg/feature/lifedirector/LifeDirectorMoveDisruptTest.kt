package com.lifemax.rpg.feature.lifedirector

import com.lifemax.rpg.core.designsystem.form.ClockText
import com.lifemax.rpg.core.designsystem.form.DateText
import com.lifemax.rpg.feature.lifedirector.data.WebLifeDirectorRepository
import com.lifemax.rpg.feature.lifedirector.data.directorReply
import com.lifemax.rpg.feature.lifedirector.domain.DisruptDate
import com.lifemax.rpg.feature.lifedirector.domain.DisruptNumber
import com.lifemax.rpg.feature.lifedirector.domain.DisruptOption
import com.lifemax.rpg.feature.lifedirector.domain.DisruptSheetModel
import com.lifemax.rpg.feature.lifedirector.domain.DisruptTime
import com.lifemax.rpg.feature.lifedirector.domain.GoneSheet
import com.lifemax.rpg.feature.lifedirector.domain.MoveSheetModel
import com.lifemax.rpg.feature.lifedirector.domain.OpenSheet
import com.lifemax.rpg.feature.lifedirector.domain.SessionSheetModel
import com.lifemax.rpg.feature.lifedirector.domain.Tap
import com.lifemax.rpg.feature.lifedirector.presentation.DirectorActions
import com.lifemax.rpg.feature.lifedirector.presentation.LifeDirectorViewModel
import com.lifemax.rpg.feature.lifedirector.presentation.SheetController
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Move, "Something came up" and the banner's answers (phase 4A), from the page's own projections
 * and replies (director-sheet-move.json, director-sheet-disrupt.json, director-replies-today.json).
 */
class LifeDirectorMoveDisruptTest {
    private fun fixture(name: String): JSONObject =
        JSONObject(javaClass.getResource("/fixtures/director-$name.json")!!.readText())

    private val replies = fixture("replies-today")

    private fun repository(j: JSONObject) = WebLifeDirectorRepository().apply { accept(j, true) }

    private class Screen(
        val repo: WebLifeDirectorRepository,
        val vm: LifeDirectorViewModel,
        val commands: DirectorRecorder,
        val emitted: MutableList<Tap>,
        val actions: DirectorActions,
    ) {
        private var sequence = 1000L

        fun push(j: JSONObject) {
            repo.accept(JSONObject(j.toString()).put("sequence", ++sequence))
            vm.projected()
        }

        val toast
            get() = vm.ui.value.toast?.text

        val last
            get() = commands.todays.last()
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

    /** The page's reply [name] from director-replies-today.json, to the screen's latest write. */
    private fun Screen.reply(name: String) =
        vm.onReply(
            directorReply(replies.getJSONObject(name))
                .copy(requestId = "today-${commands.todays.size}")
        )

    private fun Screen.answerSheet() =
        vm.onReply(
            com.lifemax.rpg.feature.lifedirector.domain.DirectorReply(
                "sheet-${commands.sheets.size}",
                "sheet",
                true,
            )
        )

    private fun rejects(j: JSONObject) {
        try {
            WebLifeDirectorRepository().accept(j, true)
        } catch (_: IllegalArgumentException) {
            return
        } catch (_: org.json.JSONException) {
            return
        }
        throw AssertionError("accepted a malformed sheet")
    }

    private fun move() = fixture("sheet-move")

    private fun disrupt() = fixture("sheet-disrupt")

    private fun JSONObject.sheetPart() = getJSONObject("sheet")

    private fun JSONObject.section(i: Int) = sheetPart().getJSONArray("sections").getJSONObject(i)

    /** The disrupt sheet open on the screen, its model sent. */
    private fun disrupting(): Screen {
        val s = screen(disrupt())
        s.actions.tap(Tap("disrupt"))
        s.answerSheet()
        return s
    }

    // The decoders

    @Test
    fun decodesMoveAsThePageProjectedIt() {
        val m = repository(move()).state.value!!.sheet as MoveSheetModel
        assertEquals("i_1o9khh740bh2dpbco8cef4", m.id)
        assertEquals("Move “Vocab Review”", m.title)
        assertEquals(
            "Open times that fit 15m. The moved session is pinned so automatic re-planning leaves it alone.",
            m.note,
        )
        assertEquals("Fri 8:45 PM", m.times[0].label)
        assertEquals(1793393100L, m.times[0].start)
        assertEquals("No open time fits this week.", m.none)
        assertTrue(m.times.size in 1..48)
    }

    @Test
    fun decodesSomethingCameUpInThePagesOrder() {
        val d = repository(disrupt()).state.value!!.sheet as DisruptSheetModel
        assertEquals("Something came up", d.title)
        assertEquals("", d.id)
        assertEquals(listOf("work", "block", "day", "sick", "energy"), d.sections.map { it.key })
        val work = d.sections[0]
        assertTrue(work.open)
        assertEquals("Add and re-plan", work.button)
        assertEquals(
            listOf(
                DisruptDate("date", "Date", "2026-10-28", 0),
                DisruptNumber("commute", "Travel each way (min)", 0, 0, 5, 0),
                DisruptTime("from", "From", 1020, "5:00 PM", 1),
                DisruptTime("until", "Until", 1260, "9:00 PM", 1),
            ),
            work.form,
        )
        val block = d.sections[1]
        assertFalse(block.open)
        assertEquals("hours", block.param)
        assertEquals(listOf(1, 2, 3, 4, 6), block.options.map { it.value })
        assertEquals("Clear the rest of today", d.sections[2].button)
        assertEquals(
            "Demanding sessions are cleared and only must-do work is moved to after you recover.",
            d.sections[3].note,
        )
        val energy = d.sections[4]
        assertEquals("energy", energy.command)
        assertEquals(DisruptOption("LOW", "Running low"), energy.options[0])
    }

    @Test
    fun aSectionsPayloadIsWhatTheCommandTakes() {
        val d = repository(disrupt()).state.value!!.sheet as DisruptSheetModel
        val (work, block, day, _, energy) = d.sections
        assertEquals(
            mapOf(
                "kind" to "work",
                "date" to "2026-10-29",
                "commute" to 15,
                "from" to 600,
                "until" to 700,
            ),
            work.payload(
                mapOf(
                    "date" to "2026-10-29",
                    "commute" to 15,
                    "from" to 600,
                    "until" to 700,
                    "stray" to 1,
                )
            ),
        )
        assertEquals(
            mapOf("kind" to "block", "hours" to 2),
            block.payload(option = block.options[1]),
        )
        assertEquals(mapOf("kind" to "day"), day.payload())
        assertEquals(mapOf("level" to "HIGH"), energy.payload(option = energy.options[2]))
    }

    @Test
    fun aMalformedMoveOrDisruptIsRefused() {
        val moves: List<JSONObject.() -> Unit> =
            listOf(
                { remove("times") },
                { put("id", "") },
                { remove("none") },
                { getJSONArray("times").getJSONObject(0).put("start", "soon") },
                { getJSONArray("times").getJSONObject(0).put("start", 1.5) },
                {
                    val t = getJSONArray("times")
                    while (t.length() <= 48) t.put(JSONObject(t.getJSONObject(0).toString()))
                },
            )
        for (edit in moves) rejects(move().apply { edit(sheetPart()) })
        val sections: List<Pair<Int, JSONObject.() -> Unit>> =
            listOf(
                0 to { put("command", "nap") },
                0 to { remove("button") },
                0 to { getJSONArray("form").getJSONObject(0).put("kind", "color") },
                0 to { getJSONArray("form").getJSONObject(0).put("value", "28/10/2026") },
                0 to { getJSONArray("form").getJSONObject(1).put("value", "0") },
                0 to { getJSONArray("form").getJSONObject(2).put("value", 1500) },
                0 to { getJSONArray("form").getJSONObject(2).remove("text") },
                0 to { getJSONArray("form").getJSONObject(3).put("group", 2) },
                1 to { remove("param") },
                1 to { getJSONArray("options").getJSONObject(0).put("value", 1.5) },
                1 to { remove("options") },
                2 to { remove("button") },
                4 to { put("open", "yes") },
                4 to { put("title", JSONObject.NULL) },
            )
        for ((i, edit) in sections) rejects(disrupt().apply { edit(section(i)) })
        rejects(
            disrupt().apply {
                sheetPart().put("sections", JSONArray().put(section(1)).put(section(1)))
            }
        )
    }

    @Test
    fun theClockStyleFollowsThePagesWords() {
        assertTrue(ClockText.is12h("5:00 PM"))
        assertFalse(ClockText.is12h("17:00"))
        assertEquals("5:00 PM", ClockText.format(1020, true))
        assertEquals("12:05 AM", ClockText.format(5, true))
        assertEquals("12:30 PM", ClockText.format(750, true))
        assertEquals("09:15", ClockText.format(555, false))
        assertEquals("2026-10-28", DateText.ymd(DateText.millis("2026-10-28")!!))
    }

    // Move

    @Test
    fun moveFromTheSessionSheetOpensItHereAndATimeMovesTheSession() {
        val s = screen(fixture("sheet-session-now"))
        val id = (s.repo.state.value!!.sheet as SessionSheetModel).id
        s.actions.tap(Tap("inst", id))
        s.answerSheet()
        s.actions.session.row(s.vm.session!!.row.single { it.sheet == "move" })
        assertTrue(s.emitted.isEmpty()) // nothing handed over
        assertEquals("move" to id, s.commands.sheets.last())
        assertEquals(OpenSheet("move", id), s.vm.ui.value.sheet)

        val m = screen(move())
        m.vm.moves.openMove("i_1o9khh740bh2dpbco8cef4")
        m.answerSheet()
        val model = m.vm.moves.move!!
        m.actions.moves.moveTo(model.times[2])
        assertEquals("moveTo", m.last.first)
        assertEquals(mapOf("id" to model.id, "start" to model.times[2].start), m.last.second - "op")
        assertEquals("moveTo", m.vm.ui.value.pendingToday)
        m.actions.moves.moveTo(model.times[3]) // one write at a time
        assertEquals(1, m.commands.todays.size)
        m.vm.onReply(
            com.lifemax.rpg.feature.lifedirector.domain.DirectorReply("today-1", "today", true)
        )
        assertNull(m.vm.ui.value.sheet) // landed: the sheet closes, the page's notice says so
        assertEquals("" to "", m.commands.sheets.last())
        assertNull(m.vm.operation("moveTo", model.id))
    }

    @Test
    fun aMoveWhoseReplyWasLostIsSentAgainWithTheSameOperation() {
        val s = screen(move())
        s.vm.moves.openMove("i_1o9khh740bh2dpbco8cef4")
        s.answerSheet()
        val model = s.vm.moves.move!!
        s.actions.moves.moveTo(model.times[0])
        val op = s.last.second["op"]
        s.vm.hidden()
        s.vm.opened()
        s.vm.moves.openMove(model.id)
        s.answerSheet()
        s.actions.moves.moveTo(model.times[0])
        assertEquals(op, s.last.second["op"])
    }

    @Test
    fun aMovedSessionThatLeftThePlanClosesTheSheet() {
        val s = screen(move())
        s.vm.moves.openMove("i_1o9khh740bh2dpbco8cef4")
        s.answerSheet()
        s.push(
            move()
                .put(
                    "sheet",
                    JSONObject()
                        .put("kind", "move")
                        .put("id", "i_1o9khh740bh2dpbco8cef4")
                        .put("gone", true),
                )
        )
        assertEquals(GoneSheet("move", "i_1o9khh740bh2dpbco8cef4"), s.repo.state.value!!.sheet)
        assertNull(s.vm.ui.value.sheet)
        assertEquals(SheetController.GONE, s.toast)
    }

    // Something came up

    @Test
    fun somethingCameUpOpensHereAndTheWorkFormIsSentWithItsValues() {
        val s = disrupting()
        assertEquals("disrupt" to "", s.commands.sheets.last())
        val d = s.vm.moves.disrupt!!
        val values = mapOf("date" to "2026-10-28", "commute" to 15, "from" to 1200, "until" to 1260)
        s.actions.moves.answer(d.sections[0], values, null)
        assertEquals("disrupt", s.last.first)
        assertEquals(mapOf("kind" to "work") + values, s.last.second - "op")
        assertNotNull(s.last.second["op"])
        s.reply("disrupt") // changed: the sheet closes, the page's notice says what moved
        assertNull(s.vm.ui.value.sheet)
        assertNull(s.toast)
    }

    @Test
    fun aRepeatSaysAlreadyAddedAndLeavesTheSheetOpenAsThePagesDoes() {
        val s = disrupting()
        val d = s.vm.moves.disrupt!!
        s.actions.moves.answer(d.sections[2], emptyMap(), null)
        val op = s.last.second["op"]
        assertEquals(mapOf("kind" to "day"), s.last.second - "op")
        s.reply("disruptAgain")
        assertEquals("Already added", s.toast)
        assertNotNull(s.vm.ui.value.sheet)
        s.actions.moves.answer(d.sections[2], emptyMap(), null)
        assertNotEquals(op, s.last.second["op"]) // the next tap is a new request
    }

    @Test
    fun refusalsShowThePagesWordsUnlessThePageHasSaidThem() {
        val s = disrupting()
        val d = s.vm.moves.disrupt!!
        s.actions.moves.answer(d.sections[0], mapOf("date" to ""), null)
        s.reply("disruptInvalid")
        assertEquals("Enter a date, start and end", s.toast)
        assertNotNull(s.vm.ui.value.sheet)
        assertNull(s.vm.operation("disrupt", "work")) // a definite refusal: a new request next
        s.actions.moves.answer(d.sections[1], emptyMap(), d.sections[1].options[0])
        s.vm.onReply(
            com.lifemax.rpg.feature.lifedirector.domain.DirectorReply(
                "today-2",
                "today",
                false,
                "No sessions needed to move.",
                said = true,
            )
        )
        assertEquals("Enter a date, start and end", s.toast) // nothing said twice
        assertNotNull(s.vm.ui.value.sheet)
    }

    @Test
    fun aSaveTheStoreRefusedIsRetriedWithItsOperation() {
        val s = disrupting()
        val block = s.vm.moves.disrupt!!.sections[1]
        s.actions.moves.answer(block, emptyMap(), block.options[0])
        assertEquals(mapOf("kind" to "block", "hours" to 1), s.last.second - "op")
        val op = s.last.second["op"]
        s.reply("disruptNotSaved")
        assertTrue(s.toast!!.startsWith("Not saved"))
        s.actions.moves.answer(block, emptyMap(), block.options[0])
        assertEquals(op, s.last.second["op"])
        s.reply("disruptAfterFault")
        assertEquals("Already added", s.toast)
    }

    @Test
    fun anEnergyCheckInClosesTheSheetOnceItHasLanded() {
        val s = disrupting()
        val energy = s.vm.moves.disrupt!!.sections[4]
        s.actions.moves.answer(energy, emptyMap(), energy.options[0])
        assertEquals("energy" to mapOf("level" to "LOW"), s.last.first to (s.last.second - "op"))
        s.reply("energy")
        assertNull(s.vm.ui.value.sheet)
    }

    // Re-plan and the banner

    @Test
    fun replanAndTheBannersAnswersRunHere() {
        val s = screen(fixture("banner"))
        val banner = s.repo.state.value!!.banner!!
        s.actions.tap(Tap("replan"))
        assertEquals("replan" to emptyMap<String, Any>(), s.last.first to (s.last.second - "op"))
        s.reply("replan")
        s.actions.tap(Tap("dismissBanner"))
        assertEquals(mapOf("text" to banner.text), s.last.second - "op")
        s.reply("dismissBannerNotShowing")
        assertNull(s.toast)
        s.actions.tap(Tap("regroup", "pause"))
        assertEquals(mapOf("v" to "pause"), s.last.second - "op")
        val pause = s.last.second["op"]
        s.vm.hidden() // the reply is lost
        s.vm.opened()
        s.actions.tap(Tap("regroup", "pause"))
        assertEquals(pause, s.last.second["op"]) // the same pause again
        s.reply("regroupNotShowing")
        assertEquals("That question is no longer showing.", s.toast)
        s.actions.tap(Tap("regroup", "pause"))
        assertNotEquals(pause, s.last.second["op"]) // a new tap after a terminal reply
        assertTrue(s.emitted.isEmpty())
    }

    @Test
    fun aPageBeforeThisPhaseStillAnswersTheseTaps() {
        val s = screen(fixture("banner").apply { remove("sheet") })
        for (t in
            listOf(Tap("disrupt"), Tap("replan"), Tap("regroup", "go"), Tap("dismissBanner"))) {
            s.vm.opened()
            s.actions.tap(t)
        }
        assertEquals(4, s.emitted.size)
        assertTrue(s.commands.todays.isEmpty() && s.commands.sheets.isEmpty())
    }
}
