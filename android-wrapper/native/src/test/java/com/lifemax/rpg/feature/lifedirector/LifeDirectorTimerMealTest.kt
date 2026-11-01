package com.lifemax.rpg.feature.lifedirector

import com.lifemax.rpg.feature.lifedirector.data.WebLifeDirectorRepository
import com.lifemax.rpg.feature.lifedirector.domain.DirectorReply
import com.lifemax.rpg.feature.lifedirector.domain.GoneSheet
import com.lifemax.rpg.feature.lifedirector.domain.GuideSpan
import com.lifemax.rpg.feature.lifedirector.domain.MealChip
import com.lifemax.rpg.feature.lifedirector.domain.MealChipKind
import com.lifemax.rpg.feature.lifedirector.domain.MealSheetModel
import com.lifemax.rpg.feature.lifedirector.domain.OpenSheet
import com.lifemax.rpg.feature.lifedirector.domain.SessionSheetModel
import com.lifemax.rpg.feature.lifedirector.domain.Tap
import com.lifemax.rpg.feature.lifedirector.domain.TimerSheetModel
import com.lifemax.rpg.feature.lifedirector.domain.answers
import com.lifemax.rpg.feature.lifedirector.presentation.DirectorActions
import com.lifemax.rpg.feature.lifedirector.presentation.LifeDirectorViewModel
import com.lifemax.rpg.feature.lifedirector.presentation.SheetController
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Today's timer and meal sheets (phase 4A), from the page's own projections. */
class LifeDirectorTimerMealTest {
    private fun fixture(name: String): JSONObject =
        JSONObject(javaClass.getResource("/fixtures/director-$name.json")!!.readText())

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

        /** The page's answer to the latest `sheet` command. */
        fun sheetAnswered(ok: Boolean = true) =
            vm.onReply(DirectorReply("sheet-${commands.sheets.size}", "sheet", ok))

        /** The page's answer to the latest `today` command. */
        fun todayAnswered(ok: Boolean = true, message: String = "") =
            vm.onReply(DirectorReply("today-${commands.todays.size}", "today", ok, message))

        val lastToday
            get() = commands.todays.last().let { it.first to (it.second - "op") }

        val lastOp
            get() = commands.todays.last().second["op"]
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

    /** The timer sheet opened from the dial's centre, and the page's model of it. */
    private fun Screen.openTimer(): TimerSheetModel {
        actions.tap(Tap("focusOpen"))
        sheetAnswered()
        return vm.timerMeal.timer!!
    }

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

    private fun JSONObject.sheetPart() = getJSONObject("sheet")

    private val running
        get() = fixture("sheet-timer")

    private val paused
        get() = fixture("sheet-timer-paused")

    private val meal
        get() = fixture("sheet-meal")

    // The decoders

    @Test
    fun decodesTheRunningAndThePausedTimerAsThePageProjectedThem() {
        val t = repository(running).state.value!!.sheet as TimerSheetModel
        assertEquals("i_1ap3yp1xvo91m2q4qdhxpu", t.id)
        assertEquals("Resistance Training", t.title)
        assertEquals("In progress", t.label)
        assertFalse(t.paused)
        assertEquals(1793200201500, t.startedAt)
        assertEquals(0L, t.pausedMs)
        assertNull(t.pausedAt)
        assertEquals(3_600_000L, t.plannedMs)
        assertEquals("of 1h planned", t.planned)
        assertEquals(
            listOf(
                GuideSpan("Logging now earns about ", false),
                GuideSpan("+12 XP", true),
                GuideSpan(" in Physique.", false),
            ),
            t.xp,
        )
        assertEquals("Finish and log", t.finish)
        assertEquals("Pause", t.pause)
        assertEquals("pause", t.pauseAct)
        assertEquals("Stop without logging", t.stop)
        assertEquals("You can leave this screen. The timer keeps running.", t.note)

        val p = repository(paused).state.value!!.sheet as TimerSheetModel
        assertTrue(p.paused)
        assertEquals("Paused", p.label)
        assertEquals(1793200955500, p.pausedAt)
        assertEquals("Resume", p.pause)
        assertEquals("resume", p.pauseAct)
    }

    @Test
    fun theTimeIsDerivedFromThePagesTimesNeverCounted() {
        val t = repository(running).state.value!!.sheet as TimerSheetModel
        val now = 1793200955000 // the projection's own minute
        assertEquals(753_500L, t.timer.elapsedMs(now))
        assertEquals(753_500f / 3_600_000f, t.progress(now), 1e-6f)
        assertEquals(1f, t.progress(now + 4 * 3_600_000L)) // full past the planned hour
        // paused: frozen at the pause, whatever the wall clock says
        val p = repository(paused).state.value!!.sheet as TimerSheetModel
        assertEquals(754_000L, p.timer.elapsedMs(now + 3_600_000L))
        // time paused before is left out
        assertEquals(653_500L, t.copy(pausedMs = 100_000).timer.elapsedMs(now))
    }

    @Test
    fun decodesTheMealAsThePageProjectedIt() {
        val m = repository(meal).state.value!!.sheet as MealSheetModel
        assertEquals(1793210400L, m.start)
        assertEquals("1793210400", m.id)
        assertEquals("meal", m.kind)
        assertEquals("Turkey & Sweet Potato Hash", m.title)
        assertEquals("Wed, Oct 28 · 6:00 PM–6:30 PM · 30m", m.`when`)
        assertEquals(
            listOf(
                MealChip(MealChipKind.MEAL, "Dinner"),
                MealChip(MealChipKind.KCAL, "770 kcal"),
                MealChip(MealChipKind.STATE, "On your plan"),
            ),
            m.chips,
        )
        assertEquals("Open meal menu", m.open)
    }

    @Test
    fun aTimerOrMealThePageNoLongerFindsIsGoneWithoutAnId() {
        for (kind in listOf("timer", "meal", "move")) {
            val p =
                repository(
                        fixture("today")
                            .put("sheet", JSONObject().put("kind", kind).put("gone", true))
                    )
                    .state
                    .value!!
            assertEquals(GoneSheet(kind, ""), p.sheet)
            assertTrue(p.sheet!!.answers(OpenSheet(kind, "1793210400")))
            assertFalse(p.sheet!!.answers(OpenSheet("session", "1793210400")))
        }
        // a timer opened without an id is whichever runs; one opened for a session is that one's
        val t = repository(running).state.value!!.sheet!!
        assertTrue(t.answers(OpenSheet("timer", "")))
        assertTrue(t.answers(OpenSheet("timer", "i_1ap3yp1xvo91m2q4qdhxpu")))
        assertFalse(t.answers(OpenSheet("timer", "i-other")))
    }

    @Test
    fun malformedTimersAndMealsAreRefused() {
        val timerEdits: List<JSONObject.() -> Unit> =
            listOf(
                { remove("startedAt") },
                { put("startedAt", "1793200201500") },
                { put("startedAt", 1793200201500.5) },
                { put("plannedMs", 0) },
                { put("paused", true) }, // paused without its time
                { put("pausedAt", 1793200955500) }, // a time without being paused
                {
                    put("paused", true)
                    put("pausedAt", 5)
                }, // paused before it started
                { put("id", "") },
                { remove("note") },
                { put("label", 3) },
                { getJSONArray("xp").getJSONObject(1).remove("b") },
                { put("xp", "+12 XP") },
            )
        for (edit in timerEdits) rejects(running.apply { sheetPart().edit() })
        val mealEdits: List<JSONObject.() -> Unit> =
            listOf(
                { put("start", "1793210400") },
                { remove("open") },
                { getJSONArray("chips").getJSONObject(1).put("kind", "badge") },
                { put("chips", org.json.JSONArray()) },
                { getJSONArray("chips").getJSONObject(0).put("kind", "kcal") },
                { put("title", JSONObject.NULL) },
            )
        for (edit in mealEdits) rejects(meal.apply { sheetPart().edit() })
        rejects(running.put("sheet", JSONObject().put("kind", "timer").put("gone", false)))
    }

    // The screen

    @Test
    fun theCentreAndUpNextOpenTheTimerHereAndCloseTellsThePage() {
        val s = screen(running)
        assertNull(s.vm.timerMeal.timer) // shown only once the screen asks for it
        val t = s.openTimer()
        assertEquals("timer" to "", s.commands.sheets.single())
        assertEquals(OpenSheet("timer", ""), s.vm.ui.value.sheet)
        assertEquals("i_1ap3yp1xvo91m2q4qdhxpu", t.id)
        assertTrue(s.emitted.isEmpty())
        s.actions.timerMeal.close()
        assertNull(s.vm.ui.value.sheet)
        assertEquals("" to "", s.commands.sheets.last())
        // up next's Open timer is the same tap
        s.actions.upNext(s.repo.state.value!!.today!!.upNext!!.actions.single { it.primary })
        assertEquals("timer" to "", s.commands.sheets.last())
    }

    @Test
    fun theSessionSheetsOpenTimerOpensThatSessionsTimer() {
        val s =
            screen(
                fixture("sheet-session-now").apply {
                    sheetPart()
                        .put("primary", JSONObject("""{"label":"Open timer","sheet":"timer"}"""))
                }
            )
        val id = (s.repo.state.value!!.sheet as SessionSheetModel).id
        s.actions.tap(Tap("inst", id))
        s.sheetAnswered()
        s.actions.session.primary()
        assertEquals("timer" to id, s.commands.sheets.last())
        s.push(running)
        s.sheetAnswered()
        assertEquals(id, s.vm.timerMeal.timer!!.id)
        assertTrue(s.emitted.isEmpty())
    }

    @Test
    fun pauseAndResumeKeepTheSheetOpenAndRunOneAtATime() {
        val s = screen(running)
        val t = s.openTimer()
        s.actions.timerMeal.pause()
        assertEquals("timer" to mapOf("act" to "pause", "id" to t.id), s.lastToday)
        assertEquals("timer.pause", s.vm.ui.value.pendingToday)
        assertEquals(s.lastOp, s.vm.operation("timer.pause", t.id))
        s.actions.timerMeal.pause() // a second tap while the first is in flight
        s.actions.timerMeal.stop()
        assertEquals(1, s.commands.todays.size)
        s.push(paused)
        s.todayAnswered()
        assertEquals(OpenSheet("timer", ""), s.vm.ui.value.sheet) // still open, now paused
        assertTrue(s.vm.timerMeal.timer!!.paused)
        assertNull(s.vm.operation("timer.pause", t.id)) // landed: the next tap is a new operation
        s.actions.timerMeal.pause()
        assertEquals("timer" to mapOf("act" to "resume", "id" to t.id), s.lastToday)
    }

    @Test
    fun finishAndLogNamesItsStartAndClosesOnceItLanded() {
        val s = screen(running)
        val t = s.openTimer()
        s.actions.timerMeal.finish()
        assertEquals(
            "timer" to mapOf("act" to "finish", "start" to 1793200201500, "id" to t.id),
            s.lastToday,
        )
        // the page's projection comes before its reply: the timer it ended is gone, which is no
        // news to the screen that ended it
        s.push(running) { put("sheet", JSONObject().put("kind", "timer").put("gone", true)) }
        assertNull(s.toast)
        assertEquals(OpenSheet("timer", ""), s.vm.ui.value.sheet)
        s.todayAnswered()
        assertNull(s.vm.ui.value.sheet)
        assertEquals("" to "", s.commands.sheets.last())
        assertNull(s.toast)
    }

    @Test
    fun aFinishWhoseReplyWasLostIsSentAgainWithTheSameOperation() {
        val s = screen(running)
        val t = s.openTimer()
        s.actions.timerMeal.finish()
        val op = s.lastOp
        s.vm.hidden() // the page stepped in (a level-up) before the reply reached the screen
        assertNull(s.vm.ui.value.sheet)
        s.vm.opened()
        s.openTimer()
        s.actions.timerMeal.finish()
        assertEquals(op, s.lastOp)
        assertEquals(op, s.vm.operation("timer.finish", t.id))
        s.todayAnswered(message = "Already recorded.")
        assertEquals("Already recorded.", s.toast)
        assertNull(s.vm.operation("timer.finish", t.id))
    }

    @Test
    fun stopWithoutLoggingCancelsThatTimerAndARefusalKeepsTheSheet() {
        val s = screen(paused)
        val t = s.openTimer()
        s.actions.timerMeal.stop()
        assertEquals(
            "timer" to mapOf("act" to "cancel", "start" to 1793200201500, "id" to t.id),
            s.lastToday,
        )
        s.todayAnswered(ok = false, message = "That timer is not running.")
        assertEquals("That timer is not running.", s.toast)
        assertEquals(OpenSheet("timer", ""), s.vm.ui.value.sheet)
        s.actions.timerMeal.stop()
        s.todayAnswered()
        assertNull(s.vm.ui.value.sheet)
    }

    @Test
    fun aTimerThatEndedElsewhereClosesItsSheetAndSaysSo() {
        val s = screen(running)
        s.openTimer()
        s.push(running) { put("sheet", JSONObject().put("kind", "timer").put("gone", true)) }
        assertNull(s.vm.ui.value.sheet)
        assertEquals(SheetController.GONE, s.toast)
    }

    @Test
    fun upNextsFinishAndLogFinishesTheRunningTimerByItsStart() {
        val s = screen(fixture("timer"))
        val finish = s.repo.state.value!!.today!!.upNext!!.actions.single { !it.primary }
        assertEquals(Tap("focusFinish"), finish.tap)
        s.actions.upNext(finish)
        assertEquals(
            "timer" to mapOf("act" to "finish", "start" to 1793200201500, "id" to ""),
            s.lastToday,
        )
        assertTrue(s.emitted.isEmpty())
        assertNull(s.vm.ui.value.sheet)
        s.todayAnswered()
        assertNull(s.vm.ui.value.pendingToday)
    }

    @Test
    fun aMealTapOpensItsSheetHereAndItsButtonHandsTheMealToThePage() {
        val s = screen(meal)
        val dinner = s.repo.state.value!!.today!!.dial.arcs.single { it.title == "Dinner" }
        s.actions.arc(dinner)
        assertEquals("meal" to "1793210400", s.commands.sheets.single())
        s.sheetAnswered()
        val m = s.vm.timerMeal.meal!!
        assertEquals("Turkey & Sweet Potato Hash", m.title)
        assertNull(s.vm.timerMeal.timer)
        s.actions.timerMeal.openMeal()
        assertEquals(listOf(Tap("meal", "1793210400")), s.emitted)
        assertNull(s.vm.ui.value.sheet)
        assertEquals("" to "", s.commands.sheets.last())
    }

    @Test
    fun aPageWithoutSheetsKeepsTheTimerAndMealsTapsItsOwn() {
        val s = screen(running.apply { remove("sheet") })
        s.actions.tap(Tap("focusOpen"))
        assertEquals(listOf(Tap("focusOpen")), s.emitted)
        assertTrue(s.commands.sheets.isEmpty())
        val t = screen(running.apply { remove("sheet") })
        t.actions.tap(Tap("meal", "1793210400"))
        assertEquals(listOf(Tap("meal", "1793210400")), t.emitted)
        val u = screen(fixture("timer").apply { remove("sheet") })
        u.actions.tap(Tap("focusFinish"))
        assertEquals(listOf(Tap("focusFinish")), u.emitted)
        assertTrue(u.commands.todays.isEmpty())
    }

    @Test
    fun anotherSheetOrAGuideHoldsTheTimersTaps() {
        val s = screen(running)
        s.actions.tap(Tap("meal", "1793210400"))
        s.actions.tap(Tap("focusOpen")) // a sheet is open: nothing else opens over it
        s.actions.tap(Tap("focusFinish"))
        assertEquals(listOf("meal" to "1793210400"), s.commands.sheets)
        assertTrue(s.commands.todays.isEmpty())
        assertNotNull(s.vm.ui.value.sheet)
    }
}
