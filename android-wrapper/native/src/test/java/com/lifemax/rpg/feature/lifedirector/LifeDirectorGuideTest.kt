package com.lifemax.rpg.feature.lifedirector

import com.lifemax.rpg.feature.lifedirector.data.WebLifeDirectorRepository
import com.lifemax.rpg.feature.lifedirector.domain.DialTour
import com.lifemax.rpg.feature.lifedirector.presentation.GuideRun
import com.lifemax.rpg.feature.lifedirector.presentation.LifeDirectorViewModel
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorPalette
import com.lifemax.rpg.feature.lifedirector.presentation.dial.dialTour
import com.lifemax.rpg.feature.lifedirector.presentation.dial.tagColor
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Today's guide (phase 4A) as the page projects it for a screen that draws it
 * (director-guide-today.json, by tools/gen_native_fixtures.js): its steps, the dial's look on each,
 * the page's name tags, the strict decoder, and the guide run over Today.
 */
class LifeDirectorGuideTest {
    private fun fixture(name: String = "guide-today"): JSONObject =
        JSONObject(javaClass.getResource("/fixtures/director-$name.json")!!.readText())

    private fun repository(name: String = "guide-today") =
        WebLifeDirectorRepository().apply { accept(fixture(name), true) }

    private fun rejects(j: JSONObject) {
        val repo = WebLifeDirectorRepository()
        try {
            repo.accept(j, true)
        } catch (_: IllegalArgumentException) {
            return
        } catch (_: org.json.JSONException) {
            return
        }
        throw AssertionError("accepted a malformed guide")
    }

    private fun step(j: JSONObject, k: Int) =
        j.getJSONObject("guide").getJSONArray("steps").getJSONObject(k)

    @Test
    fun decodesTodaysGuideAsThePageProjectedIt() {
        val g = repository().state.value!!.guide!!
        assertEquals("today", g.key)
        assertTrue(g.auto)
        assertEquals(
            listOf("dial", "dial", "dial", "dial", "dial.centre", "upNext", "footer"),
            g.steps.map { it.anchor },
        )
        assertEquals(
            listOf("clock", "outer", "inner", "handles", "center", "", ""),
            g.steps.map { it.mode },
        )
        assertEquals("This is your day as a clock", g.steps[0].title)
        assertTrue(g.steps[0].hint)
        assertEquals("Got it", g.labels.last)
        // the spotlight: round with 2 around the dial, 14 around its middle, the page's 8 elsewhere
        assertEquals(listOf(true, true, true, true, true, false, false), g.steps.map { it.round })
        assertEquals(listOf(2f, 2f, 2f, 2f, 14f, 8f, 8f), g.steps.map { it.pad })
        // Start and Done in bold, as the page writes them
        assertEquals(listOf("Start", "Done"), g.steps[5].text.filter { it.b }.map { it.t })
    }

    @Test
    fun theDialStepsWearThePagesNameTags() {
        val g = repository().state.value!!.guide!!
        val clock = g.steps[0].tags
        assertEquals(listOf("Now", "Already passed"), clock.map { it.text })
        assertEquals(listOf("now", ""), clock.map { it.kind })
        assertEquals(45.6f, clock[0].x, .001f)
        assertEquals(287.3f, clock[0].y, .001f)
        assertTrue(g.steps[1].tags.map { it.kind }.containsAll(listOf("commute", "maint")))
        assertTrue(g.steps[2].tags.any { it.text == "Session" && it.kind == "task" })
        assertEquals(
            listOf("Wake up", "Bedtime", "Work starts", "Work ends"),
            g.steps[3].tags.map { it.text },
        )
        assertTrue(g.steps.drop(4).all { it.tags.isEmpty() })
        assertTrue(g.steps.flatMap { it.tags }.all { it.x in 0f..360f && it.y in 0f..360f })
    }

    @Test
    fun weeksGuideStepsCarryNoDialLook() {
        val g = repository("week").state.value!!.guide!!
        assertTrue(g.steps.all { it.mode == "" && !it.round && it.pad == 8f && it.tags.isEmpty() })
    }

    @Test
    fun aGuideStepWithoutItsDialFieldsOrWithBadOnesIsRefused() {
        rejects(fixture().also { step(it, 0).remove("mode") })
        rejects(fixture().also { step(it, 0).put("mode", "spin") })
        rejects(fixture().also { step(it, 0).put("round", "yes") })
        rejects(fixture().also { step(it, 0).remove("pad") })
        rejects(fixture().also { step(it, 0).put("pad", "2") })
        rejects(fixture().also { step(it, 0).remove("tags") })
        rejects(fixture().also { step(it, 0).getJSONArray("tags").getJSONObject(0).put("x", "53") })
        rejects(fixture().also { step(it, 0).getJSONArray("tags").getJSONObject(0).put("y", 400) })
        rejects(
            fixture().also { step(it, 0).getJSONArray("tags").getJSONObject(0).put("text", "") }
        )
        rejects(fixture().also { step(it, 0).getJSONArray("tags").getJSONObject(0).remove("kind") })
        rejects(fixture("week").also { step(it, 0).remove("tags") })
    }

    @Test
    fun eachModeDimsTheLayersThePageDims() {
        assertEquals(DialTour.NONE, DialTour.of(""))
        assertEquals(DialTour.NONE, DialTour.of("clock")) // the clock step only pins its tags
        DialTour.of("outer").let {
            assertEquals(1f, it.outer)
            assertEquals(1f, it.words)
            assertEquals(.12f, it.inner)
            assertEquals(.12f, it.centre)
            assertEquals(.12f, it.handles)
            assertFalse(it.pulse)
        }
        DialTour.of("inner").let {
            assertEquals(.12f, it.outer)
            assertEquals(.12f, it.words)
            assertEquals(1f, it.inner)
            assertEquals(.12f, it.centre)
        }
        DialTour.of("center").let {
            assertEquals(.15f, it.outer)
            assertEquals(.15f, it.inner)
            assertEquals(.12f, it.words)
            assertEquals(1f, it.centre)
            assertEquals(.15f, it.handles)
        }
        DialTour.of("handles").let {
            assertEquals(.2f, it.outer)
            assertEquals(.2f, it.inner)
            assertEquals(.2f, it.centre)
            assertEquals(1f, it.handles)
            assertTrue(it.pulse)
        }
    }

    @Test
    fun tagsTakeThePagesColours() {
        assertEquals(DirectorPalette.nowBlue, tagColor("now"))
        assertEquals(DirectorPalette.sky, tagColor(""))
        assertEquals(DirectorPalette.sleep, tagColor("sleep"))
        assertEquals(DirectorPalette.work, tagColor("work"))
        assertEquals(DirectorPalette.task, tagColor("task"))
        assertEquals(DirectorPalette.meal, tagColor("meal"))
        assertEquals(DirectorPalette.sky, tagColor("unheard-of"))
    }

    @Test
    fun theDialFollowsTodaysGuideStepOnly() {
        val p = repository().state.value!!
        assertNull(dialTour(p, null))
        val run = dialTour(p, GuideRun("today", 3))
        assertNotNull(run)
        assertEquals("handles", run!!.step.mode)
        assertEquals(p.reducedMotion, run.still)
        assertNull(dialTour(p, GuideRun("week", 0)))
        assertNull(dialTour(repository("week").state.value!!, GuideRun("week", 0)))
    }

    @Test
    fun todaysGuideRunsOverTodayAndEndsWithTheSeenCommand() {
        val repo = repository()
        val commands = DirectorRecorder()
        val vm = LifeDirectorViewModel(repo, commands).apply { opened() }
        vm.startGuide(auto = true)
        assertEquals(GuideRun("today", 0), vm.ui.value.guide)
        vm.showDay(1) // the rest of the screen waits while the guide shows
        assertNull(vm.ui.value.pendingDay)
        repeat(6) { vm.guideNext() }
        assertEquals(6, vm.ui.value.guide!!.step)
        vm.guideNext() // Got it
        assertNull(vm.ui.value.guide)
        assertEquals(listOf("today" to true), commands.seen)
        vm.startGuide(auto = true) // once only by itself
        assertNull(vm.ui.value.guide)
    }

    @Test
    fun aBuildStillToPlayIsDueUntilItIsTaken() {
        val repo = repository()
        val vm = LifeDirectorViewModel(repo, DirectorRecorder()).apply { opened() }
        val p = repo.state.value!!
        val id = p.today!!.dial.build!!.id
        assertTrue(vm.buildDue(p.session, id))
        assertTrue(vm.buildDue(p.session, id)) // asking takes nothing
        assertTrue(vm.takeBuild(p.session, id))
        assertFalse(vm.buildDue(p.session, id))
    }
}
