package com.lifemax.rpg.feature.log

import com.lifemax.rpg.feature.log.data.WebLogRepository
import com.lifemax.rpg.feature.log.domain.LogCommands
import com.lifemax.rpg.feature.log.domain.LogDraft
import com.lifemax.rpg.feature.log.presentation.LogStatus
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
        assertEquals(46, p.preview.award)
        assertEquals("", p.preview.reason)
        assertEquals(listOf(32, 14), p.preview.split.map { it.xp })
        assertEquals(8, p.attributes.size)
        assertEquals(5 to 1440, p.minMinutes to p.maxMinutes)
        assertEquals(
            "30 min = base XP, up to 3× at 120 min. Under 5 min earns nothing.",
            p.durationRules,
        )
    }

    @Test
    fun aReducedOrRefusedAwardComesWithThePagesReason() {
        val capped = WebLogRepository().apply { accept(fixture("log-capped"), true) }
        val reduced = capped.state.value!!.preview
        assertEquals(46 to 15, reduced.total to reduced.award)
        assertEquals("Reaches the daily XP cap", reduced.reason)
        assertEquals(reduced.award, reduced.split.sumOf { it.xp })
        val refused = WebLogRepository().apply { accept(fixture("log-refused"), true) }
        val nothing = refused.state.value!!.preview
        assertEquals(46 to 0, nothing.total to nothing.award)
        assertEquals("Daily XP limit reached for this activity type", nothing.reason)
        assertEquals(0, nothing.split.size)
    }

    @Test
    fun aRefusalWithoutAReasonAndLimitsOutOfOrderAreRejected() {
        val repo = WebLogRepository()
        repo.accept(fixture(), true)
        val silent = fixture().put("sequence", 2)
        silent.getJSONObject("preview").put("award", 0).put("reason", "")
        val inverted = fixture().put("sequence", 3).put("minMinutes", 2000)
        for (bad in listOf(silent, inverted)) {
            try {
                repo.accept(bad)
                fail("accepted $bad")
            } catch (_: IllegalArgumentException) {}
        }
        assertEquals(1L, repo.state.value!!.sequence)
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
    fun durationStaysWithinThePagesLimits() {
        val (_, vm, _) = setUp()
        vm.setDuration(-20)
        assertEquals(5, vm.draft.value!!.duration)
        vm.setDuration(5000)
        assertEquals(1440, vm.draft.value!!.duration)
    }

    @Test
    fun aSecondPressWhileSavingSendsNothing() {
        val (_, vm, commands) = setUp()
        vm.submit()
        vm.submit()
        assertEquals(1, commands.submits.size)
        assertEquals(true, vm.status.value.saving)
    }

    @Test
    fun aRetryAfterAFailureReusesTheOperationAndAnEditStartsANewOne() {
        val (_, vm, commands) = setUp()
        vm.submit()
        vm.onReply("submit", false, "Could not save to this browser (storage full?)")
        assertEquals(false, vm.status.value.saving)
        assertEquals("Could not save to this browser (storage full?)", vm.status.value.error)
        vm.submit()
        assertEquals(listOf("op-1", "op-1"), commands.submits.map { it.second })
        vm.onReply("submit", false, "still full")
        vm.setNotes("changed my mind")
        vm.submit()
        assertEquals("op-2", commands.submits.last().second)
    }

    @Test
    fun aSuccessfulSaveClearsTheStatusAndPreviewRepliesChangeNothing() {
        val (repo, vm, _) = setUp()
        vm.submit()
        vm.onReply("preview", true, "")
        assertEquals(true, vm.status.value.saving)
        vm.onReply("submit", true, "Activity saved")
        assertEquals(false, vm.status.value.saving)
        assertNull(vm.status.value.error)
        repo.accept(fixture().put("sequence", 2).put("draftRevision", 2))
        vm.adopt(repo.state.value!!)
        assertEquals(LogStatus(), vm.status.value)
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
