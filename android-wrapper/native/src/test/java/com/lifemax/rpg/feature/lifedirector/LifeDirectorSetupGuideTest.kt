package com.lifemax.rpg.feature.lifedirector

import com.lifemax.rpg.feature.lifedirector.data.WebLifeDirectorRepository
import com.lifemax.rpg.feature.lifedirector.domain.Tap
import com.lifemax.rpg.feature.lifedirector.presentation.DirectorActions
import com.lifemax.rpg.feature.lifedirector.presentation.GuideRun
import com.lifemax.rpg.feature.lifedirector.presentation.LifeDirectorViewModel
import com.lifemax.rpg.feature.lifedirector.presentation.time.miniGuideTags
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Set-up's guide (phase 4B) as the page projects it for a screen that draws My time
 * (director-guide-setup.json, by tools/gen_native_fixtures.js): its steps over My time's parts, the
 * mini sleep dial's Bedtime and Wake up tags, and the ? from Goals or Settings, which switches to
 * the section of the guide the page names (help) and plays it there, or hands over.
 */
class LifeDirectorSetupGuideTest {
    private fun fixture(name: String = "guide-setup"): JSONObject =
        JSONObject(javaClass.getResource("/fixtures/director-$name.json")!!.readText())

    private fun repository(name: String = "guide-setup") =
        WebLifeDirectorRepository().apply { accept(fixture(name), true) }

    private fun rejects(j: JSONObject) {
        try {
            WebLifeDirectorRepository().accept(j, true)
        } catch (_: IllegalArgumentException) {
            return
        } catch (_: org.json.JSONException) {
            return
        }
        throw AssertionError("accepted a malformed help")
    }

    @Test
    fun decodesSetUpsGuideAsThePageProjectedIt() {
        val p = repository().state.value!!
        val g = p.guide!!
        assertEquals("setup", p.tab)
        assertEquals("setup", g.key)
        assertTrue(g.auto)
        // My time's parts, named as the My time screen registers them
        assertEquals(
            listOf(
                "time.steps",
                "time.sleep",
                "time.commitments",
                "time.routines",
                "time.goals",
                "time.build",
            ),
            g.steps.map { it.anchor },
        )
        assertEquals(listOf("", "mini", "", "", "", ""), g.steps.map { it.mode })
        // the mini dial's spotlight is round with the page's 4 around it; the cards' the page's 8
        assertEquals(listOf(false, true, false, false, false, false), g.steps.map { it.round })
        assertEquals(listOf(8f, 4f, 8f, 8f, 8f, 8f), g.steps.map { it.pad })
        assertEquals("Life Director plans your days for you", g.steps[0].title)
        assertTrue(g.steps[0].hint)
        assertEquals(listOf("Save sleep"), g.steps[1].text.filter { it.b }.map { it.t })
        assertEquals("Let’s go", g.labels.last)
        assertEquals("setup", p.help)
    }

    @Test
    fun theMiniDialStepWearsBedtimeAndWakeUp() {
        val g = repository().state.value!!.guide!!
        val tags = g.steps[1].tags
        assertEquals(listOf("Bedtime", "Wake up"), tags.map { it.text })
        assertEquals(listOf("sleep", "sleep"), tags.map { it.kind })
        assertTrue(tags.all { it.x in 0f..360f && it.y in 0f..360f })
        assertTrue(g.steps.filterIndexed { k, _ -> k != 1 }.all { it.tags.isEmpty() })
    }

    @Test
    fun miniGuideTagsAreTheMiniStepsOnly() {
        val p = repository().state.value!!
        assertEquals(p.guide!!.steps[1].tags, miniGuideTags(p, GuideRun("setup", 1)))
        assertTrue(miniGuideTags(p, GuideRun("setup", 0)).isEmpty())
        assertTrue(miniGuideTags(p, GuideRun("setup", 9)).isEmpty())
        assertTrue(miniGuideTags(p, null).isEmpty())
        assertTrue(miniGuideTags(p, GuideRun("today", 1)).isEmpty())
        // Today's guide never puts tags on the mini dial
        val today = repository("guide-today").state.value!!
        assertTrue(miniGuideTags(today, GuideRun("today", 0)).isEmpty())
    }

    @Test
    fun helpIsOptionalButNeverMalformed() {
        assertNull(
            WebLifeDirectorRepository()
                .apply { accept(fixture().also { it.remove("help") }, true) }
                .state
                .value!!
                .help
        )
        assertNull(
            WebLifeDirectorRepository()
                .apply { accept(fixture().put("help", JSONObject.NULL), true) }
                .state
                .value!!
                .help
        )
        rejects(fixture().put("help", 3))
        rejects(fixture().put("help", ""))
        rejects(fixture().put("help", true))
    }

    /** Today's guide fixture as the page sends it on another section: no guide, help as given. */
    private fun onSection(tab: String, help: Any?, sequence: Long): JSONObject =
        fixture("guide-today").apply {
            put("tab", tab)
            put("guide", JSONObject.NULL)
            put("today", JSONObject.NULL)
            put("help", help ?: JSONObject.NULL)
            put("sequence", sequence)
        }

    private fun arrived(sequence: Long, guide: Boolean): JSONObject =
        fixture("guide-today").apply {
            put("help", "today")
            put("sequence", sequence)
            if (!guide) put("guide", JSONObject.NULL)
        }

    @Test
    fun theQuestionMarkFromGoalsGoesToTodayAndPlaysItsGuide() {
        val repo =
            WebLifeDirectorRepository().apply { accept(onSection("goals", "today", 50), true) }
        val commands = DirectorRecorder()
        val vm = LifeDirectorViewModel(repo, commands).apply { opened() }
        val emitted = mutableListOf<String>()
        DirectorActions(vm) { a, _ -> emitted += a }.tap(Tap("tour"))
        assertEquals(listOf("today"), commands.sections)
        assertTrue(emitted.isEmpty())
        assertNull(vm.ui.value.guide)
        repo.accept(arrived(51, guide = true))
        vm.projected()
        assertEquals(GuideRun("today", 0), vm.ui.value.guide)
        assertFalse(vm.guideHelp.handOverDue.value)
    }

    @Test
    fun aSectionArrivingWithoutItsGuideHandsTheQuestionMarkOver() {
        val repo =
            WebLifeDirectorRepository().apply { accept(onSection("settings", "today", 50), true) }
        val vm = LifeDirectorViewModel(repo, DirectorRecorder()).apply { opened() }
        DirectorActions(vm) { _, _ -> }.tap(Tap("tour"))
        repo.accept(arrived(51, guide = false))
        vm.projected()
        assertNull(vm.ui.value.guide)
        assertTrue(vm.guideHelp.handOverDue.value)
        assertTrue(vm.guideHelp.takeHandOver())
        assertFalse(vm.guideHelp.takeHandOver()) // once
    }

    @Test
    fun withoutHelpTheQuestionMarkIsHandedOver() {
        val repo = WebLifeDirectorRepository().apply { accept(onSection("goals", null, 50), true) }
        val commands = DirectorRecorder()
        val vm = LifeDirectorViewModel(repo, commands).apply { opened() }
        val emitted = mutableListOf<Pair<String, String>>()
        DirectorActions(vm) { a, id -> emitted += a to id }.tap(Tap("tour"))
        assertEquals(listOf("tour" to ""), emitted)
        assertTrue(commands.sections.isEmpty())
    }

    @Test
    fun aGuideOfASectionTheScreenDoesNotDrawIsHandedOver() {
        // set-up's guide named from Goals while this screen does not draw My time yet
        val repo =
            WebLifeDirectorRepository().apply { accept(onSection("goals", "nowhere", 50), true) }
        val commands = DirectorRecorder()
        val vm = LifeDirectorViewModel(repo, commands).apply { opened() }
        val emitted = mutableListOf<String>()
        DirectorActions(vm) { a, _ -> emitted += a }.tap(Tap("tour"))
        assertEquals(listOf("tour"), emitted)
        assertTrue(commands.sections.isEmpty())
    }

    @Test
    fun aRefusedSectionForgetsTheQuestionMark() {
        val repo =
            WebLifeDirectorRepository().apply { accept(onSection("goals", "today", 50), true) }
        val vm = LifeDirectorViewModel(repo, DirectorRecorder()).apply { opened() }
        DirectorActions(vm) { _, _ -> }.tap(Tap("tour"))
        vm.onReply(
            com.lifemax.rpg.feature.lifedirector.domain.DirectorReply(
                command = "section",
                requestId = "section-1",
                ok = false,
                message = "",
            )
        )
        repo.accept(onSection("goals", "today", 51))
        vm.projected()
        // Today arriving later, by another way, does not start the guide the ? asked for before
        repo.accept(arrived(52, guide = true))
        vm.projected()
        assertNull(vm.ui.value.guide)
        assertFalse(vm.guideHelp.handOverDue.value)
    }

    @Test
    fun onASectionWithItsOwnGuideTheQuestionMarkPlaysIt() {
        val repo = repository("guide-today")
        val commands = DirectorRecorder()
        val vm = LifeDirectorViewModel(repo, commands).apply { opened() }
        DirectorActions(vm) { _, _ -> }.tap(Tap("tour"))
        assertEquals(GuideRun("today", 0), vm.ui.value.guide)
        assertTrue(commands.sections.isEmpty())
    }
}
