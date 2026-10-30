package com.lifemax.rpg.feature.plan

import com.lifemax.rpg.feature.plan.data.WebPlanRepository
import com.lifemax.rpg.feature.plan.data.toEvent
import com.lifemax.rpg.feature.plan.data.toPlanReply
import com.lifemax.rpg.feature.plan.data.toToggleJson
import com.lifemax.rpg.feature.plan.domain.Celebration
import com.lifemax.rpg.feature.plan.domain.DayStatus
import com.lifemax.rpg.feature.plan.domain.PlanCommands
import com.lifemax.rpg.feature.plan.domain.PlanPageAction
import com.lifemax.rpg.feature.plan.domain.PlanReply
import com.lifemax.rpg.feature.plan.domain.SetRef
import com.lifemax.rpg.feature.plan.domain.XpGain
import com.lifemax.rpg.feature.plan.presentation.PlanViewModel
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanTest {
    private fun fixture(name: String = "midweek") =
        JSONObject(javaClass.getResource("/fixtures/plan-$name.json")!!.readText())

    /** Today in the fixtures, Wednesday, part done: E0 has two of its three sets. */
    private val today = "2026-10-28"
    private val lastSet = SetRef(today, "E0", 2)

    /** The fixture as the page would send it after ticking [set]. */
    private fun ticked(set: SetRef, sequence: Long): JSONObject {
        val json = fixture().put("sequence", sequence)
        val day = json.getJSONArray("weeks").getJSONObject(0).getJSONArray("days").getJSONObject(2)
        val items = day.getJSONArray("items")
        (0 until items.length())
            .map(items::getJSONObject)
            .first { it.getString("key") == set.item }
            .getJSONArray("sets")
            .put(set.index, true)
        return json
    }

    private class Recorder : PlanCommands {
        val sent = mutableListOf<Triple<String, SetRef, Boolean>>()

        override fun toggle(requestId: String, set: SetRef, checked: Boolean) {
            sent += Triple(requestId, set, checked)
        }
    }

    private fun setUp(): Triple<WebPlanRepository, PlanViewModel, Recorder> {
        val repo = WebPlanRepository().apply { accept(fixture(), true) }
        val commands = Recorder()
        var n = 0
        val vm = PlanViewModel(repo, commands) { "r-${++n}" }
        vm.adopt(repo.state.value!!)
        return Triple(repo, vm, commands)
    }

    private fun reply(
        requestId: String,
        ok: Boolean = true,
        changed: Boolean = true,
        message: String = "",
        celebrations: List<Celebration> = emptyList(),
    ) = PlanReply(requestId, ok, changed, message, celebrations)

    @Test
    fun decodesTheWebProjection() {
        val p = WebPlanRepository().apply { accept(fixture(), true) }.state.value!!
        assertEquals("Athlete · 8-Week Athletic Foundation Program", p.program)
        assertEquals("Week 1 of 8", p.status)
        assertEquals(1 to 48, p.sessionsDone to p.sessionsTotal)
        assertEquals(8, p.weeks.size)
        assertEquals(today, p.selectedDay.date)
        assertEquals(DayStatus.TODAY, p.selectedDay.status)
        val deadlift = p.selectedDay.items.first { it.key == "E0" }
        assertEquals(listOf(true, true, false), deadlift.sets)
        assertEquals("65 lb", deadlift.value!!.text)
        assertEquals("last: 60 lb", deadlift.value!!.last)
        assertTrue(deadlift.log.previous.startsWith("Last time: "))
        assertEquals(DayStatus.MISSED, p.day("2026-10-27")!!.status)
        assertTrue(p.day("2026-10-31")!!.locked)
        assertEquals(4, p.key.size)
        assertTrue(p.progression.isNotEmpty())
    }

    @Test
    fun decodesADayTheProgramDoesNotHaveAndAPausedSession() {
        val p = WebPlanRepository().apply { accept(fixture("scholar"), true) }.state.value!!
        assertNull(p.weeks[0].days[0])
        assertEquals("Workout in progress", p.guidedSession!!.title)
        assertEquals("Resume", p.guidedSession!!.button)
        assertTrue(p.selectedDay.rest)
        assertTrue(p.selectedDay.items.isEmpty())
        assertEquals(9, p.rules.size)
        assertTrue(p.progression.isEmpty())
    }

    @Test
    fun olderAndForeignUpdatesAreIgnored() {
        val repo = WebPlanRepository()
        repo.accept(ticked(lastSet, 2), true)
        repo.accept(fixture())
        assertEquals(2L, repo.state.value!!.sequence)
        repo.accept(fixture().put("sequence", 9).put("webSessionId", "other"))
        assertEquals("plan-fixture", repo.state.value!!.session)
        repo.accept(fixture().put("webSessionId", "other"), establishSession = true)
        assertEquals("other", repo.state.value!!.session)
    }

    @Test
    fun malformedProjectionsKeepTheLastGoodOne() {
        val repo = WebPlanRepository()
        repo.accept(fixture(), true)
        val broken =
            listOf(
                fixture().put("weeks", JSONArray()),
                fixture()
                    .put("selected", JSONObject().put("date", "2030-01-01").put("revision", 2)),
                fixture().put("sessions", JSONObject().put("done", 49).put("total", 48)),
                fixture().also {
                    it.getJSONArray("weeks")
                        .getJSONObject(0)
                        .getJSONArray("days")
                        .getJSONObject(0)
                        .put("status", "glorious")
                },
                fixture().also { it.remove("rules") },
            )
        broken.forEachIndexed { i, json ->
            assertThrows(Exception::class.java) { repo.accept(json.put("sequence", 2L + i)) }
        }
        assertEquals(1L, repo.state.value!!.sequence)
    }

    @Test
    fun theWireShapesAreThePagesOwn() {
        val json = lastSet.toToggleJson(true)
        assertEquals(today, json.getString("date"))
        assertEquals("E0", json.getString("key"))
        assertEquals(2, json.getInt("set"))
        assertTrue(json.getBoolean("checked"))
        assertEquals("value" to "$today/E1", PlanPageAction.EditValue(today, "E1").toEvent())
        assertEquals("guided" to today, PlanPageAction.Guided(today).toEvent())
        assertEquals("leave" to "", PlanPageAction.Leave.toEvent())

        val gain =
            JSONObject()
                .put("attribute", "strength")
                .put("short", "STR")
                .put("xp", 28)
                .put("color", 4294934352L)
        val decoded =
            JSONObject()
                .put("command", "toggle")
                .put("requestId", "r-1")
                .put("ok", true)
                .put("changed", true)
                .put("message", "Strength B complete · +40 XP")
                .put(
                    "celebrations",
                    JSONArray().put(JSONObject().put("xp", 40).put("gains", JSONArray().put(gain))),
                )
                .toPlanReply()!!
        assertEquals(
            listOf(Celebration(40, listOf(XpGain("strength", "STR", 28, 4294934352L)))),
            decoded.celebrations,
        )
        assertNull(JSONObject().put("command", "preview").toPlanReply())
        // a celebration it cannot read is not drawn, but the reply still settles its box
        val unreadable =
            JSONObject()
                .put("command", "toggle")
                .put("requestId", "r-2")
                .put("ok", true)
                .put("changed", true)
                .put("celebrations", JSONArray().put(JSONObject().put("xp", "lots")))
                .toPlanReply()!!
        assertEquals("r-2", unreadable.requestId)
        assertTrue(unreadable.celebrations.isEmpty())
    }

    @Test
    fun thePagesSelectionIsFollowedOnlyWhenThePageChangesIt() {
        val (repo, vm, _) = setUp()
        vm.selectDay("2026-10-30")
        repo.accept(fixture().put("sequence", 2))
        vm.adopt(repo.state.value!!)
        assertEquals("2026-10-30", vm.ui.value.selected)
        repo.accept(
            fixture()
                .put("sequence", 3)
                .put("selected", JSONObject().put("date", "2026-10-26").put("revision", 2))
        )
        vm.adopt(repo.state.value!!)
        assertEquals("2026-10-26", vm.ui.value.selected)
        vm.selectDay("1999-01-01")
        assertEquals("2026-10-26", vm.ui.value.selected)
    }

    @Test
    fun aWeekOpensOnItsMondayOrOnItsFirstPlanDay() {
        val (_, vm, _) = setUp()
        vm.selectWeek(2)
        assertEquals("2026-11-09", vm.ui.value.selected)
        // the scholar plan starts on Tuesday: its first week keeps a day already chosen in it
        val repo = WebPlanRepository().apply { accept(fixture("scholar"), true) }
        val scholar = PlanViewModel(repo, Recorder())
        scholar.selectWeek(0)
        assertEquals("2026-10-28", scholar.ui.value.selected)
        scholar.selectWeek(1)
        scholar.selectWeek(0)
        assertEquals("2026-10-27", scholar.ui.value.selected)
    }

    @Test
    fun aTapAsksThePageAndShowsTheBoxPendingUntilItAnswers() {
        val (repo, vm, commands) = setUp()
        vm.toggle(lastSet, true)
        assertEquals(listOf(Triple("r-1", lastSet, true)), commands.sent)
        assertEquals(mapOf(lastSet to true), vm.ui.value.pending)
        vm.toggle(lastSet, false)
        assertEquals(1, commands.sent.size)

        val award = Celebration(40, listOf(XpGain("strength", "STR", 28, 0xFFFF8A50)))
        repo.accept(ticked(lastSet, 2))
        vm.onReply(
            reply("r-1", message = "Strength B complete · +40 XP", celebrations = listOf(award))
        )
        assertTrue(vm.ui.value.pending.isEmpty())
        val feedback = vm.ui.value.feedback!!
        assertEquals("Strength B complete · +40 XP", feedback.message)
        assertEquals(listOf(award), feedback.awards)
        vm.feedbackShown(feedback.id + 1)
        assertEquals(feedback, vm.ui.value.feedback)
        vm.feedbackShown(feedback.id)
        assertNull(vm.ui.value.feedback)
    }

    @Test
    fun aRefusedTickFallsBackToWhatThePageHolds() {
        val (repo, vm, _) = setUp()
        vm.toggle(lastSet, true)
        vm.onReply(
            reply(
                "r-1",
                ok = false,
                message = "Could not save to this browser (storage full?). Nothing was changed.",
            )
        )
        assertTrue(vm.ui.value.pending.isEmpty())
        val day = vm.ui.value.day(repo.state.value!!)
        assertEquals(false, day.items.first { it.key == "E0" }.sets[2])
        assertEquals(
            "Could not save to this browser (storage full?). Nothing was changed.",
            vm.ui.value.feedback!!.message,
        )
    }

    @Test
    fun aRepeatThatChangedNothingSaysNothing() {
        val (_, vm, _) = setUp()
        vm.toggle(SetRef(today, "E0", 0), true)
        vm.onReply(reply("r-1", changed = false))
        assertTrue(vm.ui.value.pending.isEmpty())
        assertNull(vm.ui.value.feedback)
    }

    @Test
    fun lockedDaysAndUnknownDaysSendNothing() {
        val (_, vm, commands) = setUp()
        vm.toggle(SetRef("2026-10-30", "E0", 0), true)
        vm.toggle(SetRef("1999-01-01", "E0", 0), true)
        assertTrue(commands.sent.isEmpty())
        assertTrue(vm.ui.value.pending.isEmpty())
    }

    @Test
    fun aFreshOpenDropsAnswersThatWillNotCome() {
        val (_, vm, commands) = setUp()
        vm.toggle(lastSet, true)
        vm.onOpen()
        assertTrue(vm.ui.value.pending.isEmpty())
        vm.toggle(lastSet, true)
        assertEquals(listOf("r-1", "r-2"), commands.sent.map { it.first })
        vm.onReply(reply("r-1"))
        assertEquals(mapOf(lastSet to true), vm.ui.value.pending)
    }
}
