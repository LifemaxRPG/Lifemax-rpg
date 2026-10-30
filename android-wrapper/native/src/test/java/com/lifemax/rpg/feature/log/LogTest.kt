package com.lifemax.rpg.feature.log

import com.lifemax.rpg.feature.log.data.WebLogRepository
import com.lifemax.rpg.feature.log.domain.LogCommands
import com.lifemax.rpg.feature.log.domain.LogDraft
import com.lifemax.rpg.feature.log.presentation.LogViewModel
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class LogTest {
    private fun fixture(name: String = "log-default"): JSONObject =
        JSONObject(javaClass.getResource("/$name.json")!!.readText())

    private class Recorder : LogCommands {
        val previews = mutableListOf<LogDraft>()
        val submits = mutableListOf<Pair<LogDraft, String>>()

        override fun preview(draft: LogDraft) {
            previews += draft
        }

        override fun submit(draft: LogDraft, operationId: String) {
            submits += draft to operationId
        }
    }

    private fun setUp(): Triple<WebLogRepository, LogViewModel, Recorder> {
        val repo = WebLogRepository()
        repo.accept(fixture(), true)
        val commands = Recorder()
        var n = 0
        val vm = LogViewModel(repo, commands) { "op-${++n}" }
        vm.adopt(repo.state.value!!)
        return Triple(repo, vm, commands)
    }

    @Test
    fun decodesTheWebProjection() {
        val p = WebLogRepository().apply { accept(fixture("log-strength"), true) }.state.value!!
        assertEquals("strength", p.draft.type)
        assertEquals(45, p.draft.duration)
        assertEquals(46, p.preview.total)
        assertEquals(listOf(32, 14), p.preview.split.map { it.xp })
        assertEquals(8, p.attributes.size)
    }

    @Test
    fun olderAndForeignUpdatesAreIgnored() {
        val repo = WebLogRepository()
        repo.accept(fixture("log-strength"), true)
        repo.accept(fixture("log-default"))
        assertEquals("strength", repo.state.value!!.draft.type)
        repo.accept(fixture("log-default").put("sequence", 9).put("webSessionId", "other"))
        assertEquals("strength", repo.state.value!!.draft.type)
    }

    @Test
    fun malformedProjectionKeepsTheLastGoodOne() {
        val repo = WebLogRepository()
        repo.accept(fixture(), true)
        try {
            repo.accept(fixture().put("sequence", 5).put("attributes", org.json.JSONArray()))
            fail("accepted a projection without attributes")
        } catch (_: IllegalArgumentException) {}
        assertEquals(1L, repo.state.value!!.sequence)
    }

    @Test
    fun choosingAPresetTakesItsSkillAndAttributesAndAsksForAPreview() {
        val (_, vm, commands) = setUp()
        vm.choose("running")
        val d = vm.draft.value!!
        assertEquals("running", d.type)
        assertEquals("Running", d.skill)
        assertEquals("endurance", d.primary)
        assertEquals("wellbeing", d.secondary)
        assertEquals(d, commands.previews.last())
    }

    @Test
    fun durationStaysWithinTheFormLimits() {
        val (_, vm, _) = setUp()
        vm.setDuration(-20)
        assertEquals(0, vm.draft.value!!.duration)
        vm.setDuration(5000)
        assertEquals(1440, vm.draft.value!!.duration)
    }

    @Test
    fun pressingSaveTwiceSendsOneOperation() {
        val (_, vm, commands) = setUp()
        vm.submit()
        vm.submit()
        assertEquals(listOf("op-1", "op-1"), commands.submits.map { it.second })
        vm.setNotes("changed my mind")
        vm.submit()
        assertEquals("op-2", commands.submits.last().second)
    }

    @Test
    fun thePagesDraftReplacesLocalEditsOnlyWhenThePageChangedIt() {
        val (repo, vm, _) = setUp()
        vm.setNotes("typing")
        repo.accept(fixture().put("sequence", 2))
        vm.adopt(repo.state.value!!)
        assertEquals("typing", vm.draft.value!!.notes)
        repo.accept(fixture().put("sequence", 3).put("draftRevision", 2))
        vm.adopt(repo.state.value!!)
        assertEquals("", vm.draft.value!!.notes)
    }

    @Test
    fun noDraftMeansNothingIsSent() {
        val repo = WebLogRepository()
        val commands = Recorder()
        val vm = LogViewModel(repo, commands)
        vm.submit()
        vm.setNotes("x")
        assertNull(vm.draft.value)
        assertEquals(0, commands.submits.size + commands.previews.size)
    }
}
