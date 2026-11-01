package com.lifemax.rpg.feature.lifedirector

import com.lifemax.rpg.feature.lifedirector.data.WebLifeDirectorRepository
import com.lifemax.rpg.feature.lifedirector.domain.CommitmentDot
import com.lifemax.rpg.feature.lifedirector.domain.DirectorReply
import com.lifemax.rpg.feature.lifedirector.domain.FirstRunKind
import com.lifemax.rpg.feature.lifedirector.domain.OpenSheet
import com.lifemax.rpg.feature.lifedirector.domain.Sections
import com.lifemax.rpg.feature.lifedirector.domain.SetupStepState
import com.lifemax.rpg.feature.lifedirector.domain.Tap
import com.lifemax.rpg.feature.lifedirector.presentation.DirectorActions
import com.lifemax.rpg.feature.lifedirector.presentation.DirectorUi
import com.lifemax.rpg.feature.lifedirector.presentation.Later
import com.lifemax.rpg.feature.lifedirector.presentation.LifeDirectorViewModel
import com.lifemax.rpg.feature.lifedirector.presentation.SheetController
import com.lifemax.rpg.feature.lifedirector.presentation.SleepDraft
import com.lifemax.rpg.feature.lifedirector.presentation.TimeController
import com.lifemax.rpg.feature.lifedirector.presentation.timeItemIndex
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** My time and the first-run card (phase 4B), from the page's own projections. */
class LifeDirectorTimeTest {
    private fun fixture(name: String): JSONObject =
        JSONObject(javaClass.getResource("/fixtures/director-$name.json")!!.readText())

    private fun projection(j: JSONObject) =
        WebLifeDirectorRepository().apply { accept(j, true) }.state.value!!

    private fun rejects(j: JSONObject, why: String) {
        try {
            WebLifeDirectorRepository().accept(j, true)
        } catch (_: IllegalArgumentException) {
            return
        } catch (_: org.json.JSONException) {
            return
        }
        throw AssertionError("accepted $why")
    }

    private fun JSONObject.time() = getJSONObject("time")

    private fun JSONObject.sleep() = time().getJSONObject("sleep")

    // The decoders

    @Test
    fun myTimeOnceSetUpDecodesInThePagesWords() {
        val p = projection(fixture("time"))
        val t = p.time!!
        assertEquals(Sections.SETUP, p.tab)
        assertTrue(Sections.SETUP in p.covers && Sections.FIRST_RUN in p.covers)
        assertFalse(t.first)
        assertTrue(t.steps.isEmpty())
        assertEquals("Change anything here and your plan rebuilds around it automatically.", t.lede)
        with(t.sleep) {
            assertEquals(1380 to 420, bed to wake)
            assertEquals(listOf("11:00 PM", "7:00 AM", "8h"), listOf(bedText, wakeText, length))
            assertFalse(weekend.on)
            assertEquals("Sleep window", labels.group)
            assertEquals(15, step)
            assertEquals(96, clock.size)
            assertEquals("24h", lengths[0])
            assertEquals("Save sleep", save.label)
            assertFalse(save.primary)
            assertEquals(130f, face.radii.outer)
            assertEquals(8, face.hourWords.size)
        }
        assertEquals(listOf("work"), t.commitments.rows.map { it.id })
        assertEquals(CommitmentDot.WORK, t.commitments.rows[0].dot)
        assertEquals(
            listOf("morningRoutineMinutes", "windDownMinutes"),
            t.routines.rows.map { it.key },
        )
        assertNull(t.routines.rows[0].on)
        with(t.routines.meals) {
            assertEquals("mealMinutes", key)
            assertEquals(true, on)
            assertEquals("Turn off", toggle)
            assertEquals("45m", textAt(45))
        }
        assertEquals("Off", t.routines.rows[0].textAt(0))
        assertEquals(3, t.goals.chips.size)
        assertEquals("Manage goals", t.goals.manage)
        assertNull(t.build)
        assertEquals("Re-plan now", t.replan)
        assertNull(p.firstRun)
        assertTrue(p.clock.h12)
        assertEquals("en-US", p.clock.locale)
    }

    @Test
    fun myTimeDuringSetUpHasItsStepsAndBuildMyWeek() {
        val t = projection(fixture("time-first")).time!!
        assertTrue(t.first)
        assertEquals(
            listOf(SetupStepState.OK, SetupStepState.OK, SetupStepState.CUR, SetupStepState.TODO),
            t.steps.map { it.state },
        )
        assertTrue(t.sleep.weekend.on)
        assertEquals(30 to 570, t.sleep.weekend.bed to t.sleep.weekend.wake)
        // a night shift's arc runs past midnight
        assertEquals(1860, t.sleep.context[0].to)
        assertTrue(t.build!!.enabled)
        assertNull(t.replan)
        assertNull(t.goals.manage)
        assertTrue(t.goals.add.primary)
    }

    @Test
    fun theFirstRunCardDecodesItsDemoArcs() {
        val p = projection(fixture("firstrun"))
        val f = p.firstRun!!
        assertNull(p.time)
        assertNull(p.today)
        assertEquals(
            listOf(
                FirstRunKind.SLEEP,
                FirstRunKind.WORK,
                FirstRunKind.SESSION,
                FirstRunKind.SESSION,
            ),
            f.arcs.map { it.kind },
        )
        assertEquals(listOf(true, true, false, false), f.arcs.map { it.outer })
        assertEquals(1380 to 1800, f.arcs[0].from to f.arcs[0].to)
        assertEquals("Set up my time", f.button)
        assertEquals(Sections.SETUP, f.section)
        assertEquals(101f, f.face.radii.inner)
    }

    @Test
    fun malformedMyTimeIsRefused() {
        rejects(fixture("time").apply { sleep().remove("face") }, "a sleep card without its face")
        rejects(fixture("time").apply { sleep().put("step", "15") }, "a step that is text")
        rejects(fixture("time").apply { sleep().put("bed", 1440) }, "a bedtime off the day")
        rejects(fixture("time").apply { sleep().put("bed", 22.5) }, "a bedtime that is not whole")
        rejects(
            fixture("time").apply { sleep().getJSONArray("clock").remove(0) },
            "a dial without words for every stop",
        )
        rejects(
            fixture("time-first").apply {
                time().getJSONArray("steps").getJSONObject(0).put("state", "done")
            },
            "an unknown step state",
        )
        rejects(
            fixture("time").apply {
                time().put("build", fixture("time-first").time().get("build"))
            },
            "Build my week after set-up",
        )
        rejects(
            fixture("time").apply {
                time()
                    .getJSONObject("routines")
                    .getJSONArray("rows")
                    .getJSONObject(0)
                    .getJSONArray("stops")
                    .remove(0)
            },
            "a stepper without words for every stop",
        )
        rejects(
            fixture("time").apply {
                time()
                    .getJSONObject("commitments")
                    .getJSONArray("rows")
                    .getJSONObject(0)
                    .put("dot", "red")
            },
            "an unknown commitment dot",
        )
        rejects(
            fixture("time").apply {
                time().getJSONObject("routines").getJSONObject("meals").remove("on")
            },
            "meals without their switch",
        )
        rejects(
            fixture("firstrun").apply {
                getJSONObject("firstRun").getJSONArray("arcs").getJSONObject(2).put("ring", "outer")
            },
            "a session on the outer ring",
        )
        rejects(
            fixture("time").apply { getJSONObject("clock").put("h12", "yes") },
            "a clock style that is text",
        )
    }

    @Test
    fun aPageFromBefore4BSendsNoMyTime() {
        val p =
            projection(
                fixture("today").apply {
                    remove("time")
                    remove("firstRun")
                    getJSONObject("clock").remove("h12")
                    getJSONObject("clock").remove("locale")
                }
            )
        assertNull(p.time)
        assertNull(p.firstRun)
        // its clock's own words say which style it writes ("3:10 PM")
        assertTrue(p.clock.h12)
        assertEquals("", p.clock.locale)
    }

    @Test
    fun guideAnchorsNameTheirListItems() {
        val first = projection(fixture("time-first")).time!!
        val done = projection(fixture("time")).time!!
        assertEquals(1, timeItemIndex(first, "time.steps"))
        assertEquals(3, timeItemIndex(first, "time.sleep"))
        assertEquals(-1, timeItemIndex(first, "time.mini"))
        assertEquals(7, timeItemIndex(first, "time.build"))
        assertEquals(-1, timeItemIndex(done, "time.steps"))
        assertEquals(2, timeItemIndex(done, "time.sleep"))
        assertEquals(6, timeItemIndex(done, "time.build"))
        assertEquals(-1, timeItemIndex(done, "week.rows"))
    }

    // The screen's writes and sheets, through the view model

    private class Screen(val vm: LifeDirectorViewModel, val commands: DirectorRecorder) {
        val actions = DirectorActions(vm) { _, _ -> }
        val toast
            get() = vm.ui.value.toast?.text

        fun answered(ok: Boolean, message: String = "", changed: Boolean = ok) =
            vm.onReply(DirectorReply("change-${commands.changes.size}", "do", ok, message, changed))
    }

    private fun screen(j: JSONObject): Screen {
        val commands = DirectorRecorder()
        val repo = WebLifeDirectorRepository().apply { accept(j, true) }
        return Screen(LifeDirectorViewModel(repo, commands).apply { opened() }, commands)
    }

    @Test
    fun saveSleepSendsTheTimesAndTheWeekendOnlyWhenItDiffers() {
        val s = screen(fixture("time"))
        s.actions.time.saveSleep()
        assertEquals(
            "sleepSave" to mapOf<String, Any>("bed" to 1380, "wake" to 420),
            s.commands.changes.last(),
        )
        val f = screen(fixture("time-first"))
        f.actions.time.saveSleep()
        assertEquals(
            mapOf("bed" to 1365, "wake" to 420, "weekend" to mapOf("bed" to 30, "wake" to 570)),
            f.commands.changes.last().second,
        )
    }

    @Test
    fun theSleepDraftFollowsTheHandlesUntilSaved() {
        val s = screen(fixture("time"))
        s.actions.time.sleep(1350, 390)
        s.actions.time.weekend(true)
        s.actions.time.weekendTimes(1425, 1470) // a time past midnight is taken round the clock
        assertEquals(SleepDraft(1350, 390, true, 1425, 30), s.vm.ui.value.timeDrafts.sleep)
        s.actions.time.saveSleep()
        assertEquals(
            mapOf("bed" to 1350, "wake" to 390, "weekend" to mapOf("bed" to 1425, "wake" to 30)),
            s.commands.changes.last().second,
        )
        // one at a time
        s.actions.time.saveSleep()
        assertEquals(1, s.commands.changes.size)
        // refused: the page's words, and the draft stays to be fixed
        s.answered(false, "That is under 5 hours of sleep. Please check the times.")
        assertEquals("That is under 5 hours of sleep. Please check the times.", s.toast)
        assertNotNull(s.vm.ui.value.timeDrafts.sleep)
        s.actions.time.saveSleep()
        s.answered(true)
        assertNull(s.vm.ui.value.timeDrafts.sleep)
    }

    @Test
    fun aDraftThePageNowHasIsDoneWith() {
        val commands = DirectorRecorder()
        val repo = WebLifeDirectorRepository().apply { accept(fixture("time"), true) }
        val vm = LifeDirectorViewModel(repo, commands).apply { opened() }
        vm.time.setSleep(1350, 420)
        repo.accept(fixture("time").put("sequence", 99).apply { sleep().put("bed", 1350) })
        vm.projected()
        assertNull(vm.ui.value.timeDrafts.sleep)
    }

    @Test
    fun mealsAndBuildAreThePagesWrites() {
        val s = screen(fixture("time-first"))
        s.actions.time.meals(false)
        assertEquals("meals" to mapOf<String, Any>("on" to false), s.commands.changes.last())
        s.answered(true)
        s.actions.time.build()
        assertEquals("finishSetup" to emptyMap<String, Any>(), s.commands.changes.last())
        s.answered(false, "Save your sleep times first")
        assertEquals("Save your sleep times first", s.toast)
    }

    @Test
    fun commitmentsAndGoalsOpenThePagesSheets() {
        val s = screen(fixture("time"))
        s.actions.time.rule("work")
        assertEquals("rule" to "work", s.commands.sheets.last())
        assertEquals(OpenSheet("rule", "work"), s.vm.ui.value.sheet)
        // one sheet at a time
        s.actions.time.addGoal()
        assertEquals(1, s.commands.sheets.size)
        s.vm.closeSheet()
        s.actions.time.rule("")
        assertEquals("rule" to "", s.commands.sheets.last())
        s.vm.closeSheet()
        s.actions.time.addGoal()
        assertEquals("goalType" to "", s.commands.sheets.last())
    }

    @Test
    fun withoutMyTimeNothingIsSent() {
        val s = screen(fixture("today"))
        s.actions.time.saveSleep()
        s.actions.time.meals(true)
        s.actions.time.build()
        s.actions.time.rule("work")
        assertTrue(s.commands.changes.isEmpty())
        assertTrue(s.commands.sheets.isEmpty())
    }

    @Test
    fun theFirstRunButtonAndTheTabOpenMyTime() {
        val s = screen(fixture("firstrun"))
        assertTrue(s.vm.draws(Sections.SETUP))
        s.actions.tap(Tap("tab", Sections.SETUP))
        assertEquals(listOf(Sections.SETUP), s.commands.sections)
    }

    // A routine's stepper: quick taps add up, then one absolute write after the quiet

    private class Clock : Later {
        val due = mutableListOf<() -> Unit>()
        var cancelled = 0

        override fun after(ms: Long, run: () -> Unit): () -> Unit {
            assertEquals(TimeController.QUIET_MS, ms)
            due += run
            return {
                cancelled++
                due.remove(run)
            }
        }

        fun pass() = due.toList().also { due.clear() }.forEach { it() }
    }

    private class Steppers(j: JSONObject) {
        val commands = DirectorRecorder()
        val repo = WebLifeDirectorRepository().apply { accept(j, true) }
        val ui = MutableStateFlow(DirectorUi())
        val sent = mutableListOf<Pair<String, Map<String, Any>>>()
        var free = true
        val clock = Clock()
        val time =
            TimeController(
                SheetController(commands, repo.state, ui, {}),
                repo.state,
                ui,
                { n, a -> sent += n to a },
                { free },
                clock,
            )
    }

    @Test
    fun quickTapsBecomeOneWrite() {
        val s = Steppers(fixture("time"))
        repeat(3) { s.time.stepRoutine("morningRoutineMinutes", 1) }
        assertEquals(45, s.ui.value.timeDrafts.routines["morningRoutineMinutes"])
        assertEquals(1, s.clock.due.size)
        assertEquals(2, s.clock.cancelled)
        assertTrue(s.sent.isEmpty())
        s.clock.pass()
        assertEquals(
            "routine" to mapOf<String, Any>("key" to "morningRoutineMinutes", "value" to 45),
            s.sent.single(),
        )
        // the page answered: the projection shows its value again
        s.time.settled("routine", true)
        assertTrue(s.ui.value.timeDrafts.routines.isEmpty())
    }

    @Test
    fun aStepperKeepsThePagesLimitsAndABackAndForthSendsNothing() {
        val s = Steppers(fixture("time"))
        repeat(9) { s.time.stepRoutine("windDownMinutes", -1) }
        assertEquals(0, s.ui.value.timeDrafts.routines["windDownMinutes"])
        repeat(6) { s.time.stepRoutine("windDownMinutes", 1) }
        s.clock.pass()
        assertTrue(s.sent.isEmpty())
        assertTrue(s.ui.value.timeDrafts.routines.isEmpty())
    }

    @Test
    fun aStepWaitsForTheChangeOutAndMealsOffHaveNone() {
        val s = Steppers(fixture("time"))
        s.time.stepRoutine("mealMinutes", 1)
        s.free = false
        s.clock.pass()
        assertTrue(s.sent.isEmpty())
        assertEquals(1, s.clock.due.size)
        s.free = true
        s.clock.pass()
        assertEquals(
            mapOf<String, Any>("key" to "mealMinutes", "value" to 35),
            s.sent.single().second,
        )
        val off =
            Steppers(
                fixture("time").apply {
                    time().getJSONObject("routines").getJSONObject("meals").put("on", false)
                }
            )
        off.time.stepRoutine("mealMinutes", 1)
        assertTrue(off.clock.due.isEmpty())
    }
}
