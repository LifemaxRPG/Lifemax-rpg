package com.lifemax.rpg.feature.lifedirector

import com.lifemax.rpg.feature.lifedirector.data.WebLifeDirectorRepository
import com.lifemax.rpg.feature.lifedirector.domain.ArcStyle
import com.lifemax.rpg.feature.lifedirector.domain.BlockKind
import com.lifemax.rpg.feature.lifedirector.domain.DirectorNotice
import com.lifemax.rpg.feature.lifedirector.domain.DirectorReply
import com.lifemax.rpg.feature.lifedirector.domain.SectionFlick
import com.lifemax.rpg.feature.lifedirector.domain.SessionState
import com.lifemax.rpg.feature.lifedirector.domain.Tap
import com.lifemax.rpg.feature.lifedirector.presentation.DirectorActions
import com.lifemax.rpg.feature.lifedirector.presentation.GuideRun
import com.lifemax.rpg.feature.lifedirector.presentation.LifeDirectorViewModel
import com.lifemax.rpg.feature.lifedirector.presentation.components.clockText
import com.lifemax.rpg.feature.lifedirector.presentation.dial.DialGeometry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LifeDirectorTest {
    private fun fixture(name: String = "today"): JSONObject =
        JSONObject(javaClass.getResource("/fixtures/director-$name.json")!!.readText())

    private fun repository(name: String = "today") =
        WebLifeDirectorRepository().apply { accept(fixture(name), true) }

    private fun rejects(repo: WebLifeDirectorRepository, j: JSONObject) {
        try {
            repo.accept(j)
        } catch (_: IllegalArgumentException) {
            return
        } catch (_: org.json.JSONException) {
            return
        }
        throw AssertionError("accepted a malformed projection")
    }

    private fun viewModel():
        Triple<WebLifeDirectorRepository, LifeDirectorViewModel, DirectorRecorder> {
        val repo = repository()
        val commands = DirectorRecorder()
        return Triple(repo, LifeDirectorViewModel(repo, commands).apply { opened() }, commands)
    }

    // The decoder

    @Test
    fun decodesTodayAsTheWebProjectedIt() {
        val p = repository().state.value!!
        val t = p.today!!
        assertEquals("39 sessions ahead", p.header.line)
        assertEquals(
            listOf("today", "week", "goals", "quests", "setup", "settings"),
            p.tabs.map { it.id },
        )
        assertEquals(0, t.dayIndex)
        assertEquals(7, t.strip.size)
        assertEquals(
            listOf(SessionState.DONE, SessionState.PLAN, SessionState.OFF, SessionState.PLAN),
            t.strip[0].dots,
        )
        val next = t.dial.arcs.single { it.next }
        assertEquals("Resistance Training", next.title)
        assertEquals(Tap("inst", "i_1ap3yp1xvo91m2q4qdhxpu"), next.tap)
        val dentist = t.dial.arcs.single { it.style == ArcStyle.APPT }
        assertNull(dentist.tap)
        assertEquals("Dentist · 6:30 PM–7:15 PM", dentist.tip)
        assertEquals(Tap("meal", "1793210400"), t.dial.arcs.single { it.title == "Dinner" }.tap)
        assertEquals(BlockKind.WORK, t.centre.kickKind)
        assertEquals(1, t.centre.countdown!!.hours)
        assertEquals(50, t.centre.countdown!!.minutes)
        assertEquals(BlockKind.WORK, t.dial.ring!!.kind)
        assertEquals("1 of 1 sessions done", t.earlier!!.summary)
        assertEquals(9, t.reminders.size)
        assertEquals(t.ahead.size, t.ahead.map { it.key }.toSet().size)
        val upNext = t.upNext!!
        assertEquals(listOf("focusStart", "doneQuick", "inst"), upNext.actions.map { it.tap!!.act })
    }

    @Test
    fun decodesAnotherDayWithItsDateAndNoHand() {
        val t = repository("tomorrow").state.value!!.today!!
        assertEquals(1, t.dayIndex)
        assertFalse(t.isToday)
        assertNull(t.dial.hand)
        assertNull(t.dial.ring)
        assertNull(t.upNext)
        assertNull(t.earlier)
        assertTrue(t.centre.date)
        assertEquals("Oct 29", t.centre.title)
        assertEquals("First · 7:30 AM", t.centre.next!!.label)
    }

    @Test
    fun malformedProjectionsAreRejectedAndTheLastGoodOneKept() {
        val repo = repository()
        fun today(change: (JSONObject) -> Unit) =
            fixture().put("sequence", 2).also { change(it.getJSONObject("today")) }
        rejects(repo, today { it.put("dayIndex", 7) })
        rejects(repo, today { it.getJSONArray("mix").remove(0) })
        rejects(
            repo,
            today { it.getJSONObject("dial").getJSONArray("arcs").getJSONObject(0).put("a1", 400) },
        )
        rejects(
            repo,
            today {
                it.getJSONObject("dial").getJSONArray("arcs").getJSONObject(0).put("kind", "nap")
            },
        )
        rejects(
            repo,
            today {
                it.getJSONObject("dial").getJSONArray("arcs").getJSONObject(2).put("ring", "outer")
            },
        )
        rejects(repo, today { it.getJSONObject("dial").getJSONObject("ring").put("frac", 1.5) })
        rejects(repo, fixture().put("sequence", 2).put("protocol", 2))
        rejects(
            repo,
            fixture().put("sequence", 2).also { it.getJSONObject("tokens").remove("gold") },
        )
        assertEquals(1L, repo.state.value!!.sequence)
    }

    @Test
    fun olderAndForeignProjectionsAreIgnored() {
        val repo = WebLifeDirectorRepository()
        repo.accept(fixture("tomorrow").put("sequence", 5), true)
        repo.accept(fixture().put("sequence", 4))
        assertEquals(1, repo.state.value!!.today!!.dayIndex)
        repo.accept(fixture().put("sequence", 9).put("webSessionId", "another page"))
        assertEquals(1, repo.state.value!!.today!!.dayIndex)
        repo.accept(fixture().put("sequence", 1).put("webSessionId", "another page"), true)
        assertEquals(0, repo.state.value!!.today!!.dayIndex)
    }

    @Test
    fun aProjectionWithoutAPlanKeepsTheDayLastShownOnTodayOnly() {
        val repo = repository()
        repo.accept(fixture().put("sequence", 2).put("today", JSONObject.NULL))
        assertEquals(0, repo.state.value!!.today!!.dayIndex)
        // phase 3A: another section sends no Today, and none is kept for it
        repo.accept(fixture().put("sequence", 3).put("today", JSONObject.NULL).put("tab", "week"))
        assertEquals("week", repo.state.value!!.tab)
        assertNull(repo.state.value!!.today)
    }

    @Test
    fun weekAndQuestsDecodeFromThePage() {
        val repo = repository()
        repo.accept(fixture("week").put("sequence", 1000), true)
        val w = repo.state.value!!.week!!
        assertEquals(
            listOf("today", "week", "quests", "goals", "settings"),
            repo.state.value!!.covers,
        )
        assertEquals(7, w.days.size)
        assertTrue(w.days.first().today && w.days.drop(1).none { it.today })
        assertTrue(
            w.days.all { d -> d.segs.all { it.left >= 0f && it.left + it.width <= 100.01f } }
        )
        repo.accept(fixture("quests").put("sequence", 1001), true)
        val q = repo.state.value!!.quests!!
        assertEquals("Open Quest Board", q.head.open)
        assertTrue(q.rows.isNotEmpty())
    }

    @Test
    fun aMalformedWeekIsRefusedAndTheLastGoodStateKept() {
        val repo = repository()
        repo.accept(fixture("week").put("sequence", 1000), true)
        val bad = fixture("week").put("sequence", 1002)
        bad.getJSONObject("week")
            .getJSONArray("days")
            .getJSONObject(0)
            .getJSONArray("segs")
            .getJSONObject(0)
            .put("kind", "nap")
        assertThrows(IllegalArgumentException::class.java) { repo.accept(bad) }
        assertEquals(7, repo.state.value!!.week!!.days.size)
    }

    @Test
    fun anOlderPageCoversTodayOnly() {
        val repo = repository()
        val old = fixture()
        old.remove("covers")
        repo.accept(old, true)
        assertEquals(listOf("today"), repo.state.value!!.covers)
    }

    @Test
    fun aNewPageWithoutAPlanShowsNoDayOfTheOldOne() {
        val repo = repository()
        repo.accept(fixture("unplanned").put("webSessionId", "a reloaded page"), true)
        assertEquals("setup", repo.state.value!!.tab)
        assertNull(repo.state.value!!.today)
    }

    @Test
    fun rowsOfTheSameBlockGetKeysOfTheirOwn() {
        val j = fixture()
        val ahead = j.getJSONObject("today").getJSONObject("rows").getJSONArray("ahead")
        ahead.put(JSONObject(ahead.getJSONObject(0).toString())).put(ahead.getJSONObject(0))
        val keys =
            WebLifeDirectorRepository()
                .apply { accept(j, true) }
                .state
                .value!!
                .today!!
                .ahead
                .map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertEquals(listOf(keys[0] + "#2", keys[0] + "#3"), keys.takeLast(2))
    }

    @Test
    fun aTimerCountsWhatHasRunAndStopsWhilePaused() {
        val timer = repository("timer").state.value!!.today!!.centre.timer!!
        assertEquals(754_000L, timer.elapsedMs(timer.startedAt + 754_000L))
        assertEquals(0L, timer.elapsedMs(timer.startedAt - 5_000L))
        val paused = timer.copy(pausedMs = 60_000L, paused = timer.startedAt + 600_000L)
        assertEquals(540_000L, paused.elapsedMs(timer.startedAt + 900_000L))
        assertEquals("12:34", clockText(754_000L))
        assertEquals("1:02:09", clockText(3_729_000L))
        assertEquals("0:05", clockText(5_999L))
    }

    // The view model

    @Test
    fun aChangeIsSentOnceAndARefusalShowsThePagesWords() {
        val (repo, vm, commands) = viewModel()
        repo.accept(fixture("goals").put("webSessionId", "c").put("sequence", 9), true)
        vm.opened()
        val id = repo.state.value!!.goals!!.cards.first().id
        vm.change("goalPriority", mapOf("id" to id, "value" to 1))
        vm.change("goalPriority", mapOf("id" to id, "value" to 2)) // one at a time
        assertEquals(1, commands.changes.size)
        assertEquals("goalPriority", vm.ui.value.pendingChange)
        vm.onReply(
            DirectorReply("change-1", "do", false, "Not saved: the quota has been exceeded.")
        )
        assertNull(vm.ui.value.pendingChange)
        assertEquals("Not saved: the quota has been exceeded.", vm.ui.value.toast?.text)
    }

    @Test
    fun removingAGoalAsksFirstWithThePagesWords() {
        val (repo, vm, commands) = viewModel()
        repo.accept(fixture("goals").put("webSessionId", "c").put("sequence", 9), true)
        vm.opened()
        val g = repo.state.value!!.goals!!
        vm.askRemoveGoal(g.cards.first().id)
        assertEquals(g.removeConfirm, vm.ui.value.confirm?.text)
        assertTrue(commands.changes.isEmpty())
        vm.cancelChange()
        assertTrue(commands.changes.isEmpty())
        vm.askRemoveGoal(g.cards.first().id)
        vm.confirmChange()
        assertEquals(
            "goalRemove" to mapOf<String, Any>("id" to g.cards.first().id),
            commands.changes.single(),
        )
    }

    @Test
    fun copyingABackupPutsThePagesTextOnTheClipboardAndAFailedRestoreKeepsTheDraft() {
        val (repo, vm, commands) = viewModel()
        repo.accept(fixture("settings").put("webSessionId", "c").put("sequence", 9), true)
        vm.opened()
        vm.change("exportBackup")
        vm.onReply(DirectorReply("change-1", "do", true, "", text = "{backup}"))
        assertEquals("{backup}", vm.ui.value.copyText)
        vm.copied(true)
        assertNull(vm.ui.value.copyText)
        assertEquals("Backup copied to clipboard", vm.ui.value.toast?.text)
        vm.openRestore()
        vm.restoreDraft("  not a backup ")
        vm.submitRestore()
        assertEquals(
            "importBackup" to mapOf<String, Any>("text" to "not a backup"),
            commands.changes.last(),
        )
        assertFalse(vm.ui.value.restoring)
        vm.onReply(DirectorReply("change-2", "do", false, "That is not a Life Director backup."))
        assertTrue("the sheet comes back with the draft to fix", vm.ui.value.restoring)
        assertEquals("  not a backup ", vm.ui.value.restoreDraft)
        vm.hidden()
        assertFalse(vm.ui.value.restoring)
        assertEquals(
            "the draft survives the screen hiding",
            "  not a backup ",
            vm.ui.value.restoreDraft,
        )
    }

    @Test
    fun goalsAndSettingsDecodeStrictly() {
        val repo = repository()
        repo.accept(fixture("goals").put("sequence", 2000), true)
        val g = repo.state.value!!.goals!!
        assertEquals(listOf("Top", "High", "Medium", "Light", "Low"), g.priorities.map { it.label })
        assertTrue(g.cards.isNotEmpty())
        repo.accept(fixture("settings").put("sequence", 2001), true)
        val s = repo.state.value!!.settings!!
        assertEquals(listOf(31, 23, 15), s.ranges.map { it.stops.size })
        assertEquals(16, s.grace.stops.size)
        assertEquals("2h", s.ranges[0].labelAt(120))
        val bad = fixture("settings").put("sequence", 2002)
        bad.getJSONObject("settings")
            .getJSONArray("ranges")
            .getJSONObject(0)
            .getJSONArray("stops")
            .remove(0)
        assertThrows(IllegalArgumentException::class.java) { repo.accept(bad) }
        assertEquals(31, repo.state.value!!.settings!!.ranges[0].stops.size)
    }

    @Test
    fun weeksGuideStartsByItselfOnceAndEndsWithTheSeenCommand() {
        val (repo, vm, commands) = viewModel()
        val due = fixture("week").put("webSessionId", "g").put("sequence", 7)
        due.getJSONObject("guide").put("auto", true)
        repo.accept(due, true)
        vm.opened()
        vm.startGuide(auto = true)
        assertEquals(GuideRun("week", 0), vm.ui.value.guide)
        vm.showSection("quests") // the rest of the screen waits while the guide shows
        assertTrue(commands.sections.isEmpty())
        vm.guideBack()
        assertEquals(0, vm.ui.value.guide!!.step)
        vm.guideNext()
        vm.guideNext()
        assertEquals(2, vm.ui.value.guide!!.step)
        vm.guideNext() // Got it
        assertNull(vm.ui.value.guide)
        assertEquals(listOf("week" to true), commands.seen)
        vm.startGuide(auto = true) // once only by itself
        assertNull(vm.ui.value.guide)
        vm.startGuide() // from ?
        vm.guideSkip()
        assertEquals("week" to false, commands.seen.last())
    }

    @Test
    fun aGuideEndsWhenItsSectionGoes() {
        val (repo, vm, _) = viewModel()
        repo.accept(fixture("week").put("webSessionId", "g").put("sequence", 7), true)
        vm.opened()
        vm.startGuide()
        assertNotNull(vm.ui.value.guide)
        repo.accept(fixture("quests").put("webSessionId", "g").put("sequence", 8))
        vm.projected()
        assertNull(vm.ui.value.guide)
    }

    @Test
    fun aFlickIsSidewaysFarOrFastAndLandsOnTheNeighbourTab() {
        assertEquals(1, SectionFlick.decide(-90f, 10f, 0f))
        assertEquals(-1, SectionFlick.decide(90f, -20f, 0f))
        assertEquals(0, SectionFlick.decide(-50f, 0f, 200f)) // short and slow
        assertEquals(1, SectionFlick.decide(-30f, 0f, -1200f)) // short but fast
        assertEquals(0, SectionFlick.decide(-90f, 80f, 0f)) // as much down as across: a scroll
        val tabs = listOf("today", "week", "goals", "quests")
        assertEquals("goals", SectionFlick.neighbour(tabs, "week", 1))
        assertEquals("today", SectionFlick.neighbour(tabs, "week", -1))
        assertNull(SectionFlick.neighbour(tabs, "today", -1))
    }

    @Test
    fun aSectionTheScreenDrawsIsACommandAndOneItDoesNotDrawIsHandedOver() {
        val (repo, vm, commands) = viewModel()
        assertFalse("a Today-only page: Week is the page's", vm.draws("week"))
        repo.accept(fixture("week").put("webSessionId", "s").put("sequence", 5), true)
        vm.opened()
        vm.showSection("quests")
        vm.showSection("quests")
        assertEquals(listOf("quests"), commands.sections)
        assertEquals("quests", vm.ui.value.pendingSection)
        vm.onReply(DirectorReply("section-1", "section", true, ""))
        assertNull(vm.ui.value.pendingSection)
        assertFalse("My time is not drawn here, so its tap goes to the page", vm.draws("setup"))
        vm.gotoDay(3)
        assertEquals("today@3", commands.sections.last())
        assertEquals(3, vm.ui.value.pendingDay)
        vm.onReply(DirectorReply("section-2", "gotoDay", false, ""))
        assertNull(vm.ui.value.pendingDay)
        assertEquals("That section is not in Life Director.", vm.ui.value.toast?.text)
    }

    @Test
    fun choosingADayAsksThePageOnceAndTheReplyClearsIt() {
        val (repo, vm, commands) = viewModel()
        vm.showDay(1)
        vm.showDay(1)
        assertEquals(listOf(1), commands.days)
        assertEquals(1, vm.ui.value.pendingDay)
        repo.accept(fixture("tomorrow").put("sequence", 2))
        vm.onReply(DirectorReply("day-1", "day", true, ""))
        assertNull(vm.ui.value.pendingDay)
        assertNull(vm.ui.value.toast)
        vm.showDay(1)
        vm.showDay(9)
        assertEquals(listOf(1, 9), commands.days)
        assertEquals(9, vm.ui.value.pendingDay)
    }

    @Test
    fun onlyTheAnswerToTheLatestDaySettlesTheStrip() {
        val (_, vm, commands) = viewModel()
        vm.showDay(1)
        vm.showDay(2)
        vm.onReply(DirectorReply("day-1", "day", true, ""))
        assertEquals(2, vm.ui.value.pendingDay)
        vm.onReply(DirectorReply("day-2", "day", true, ""))
        assertNull(vm.ui.value.pendingDay)
        assertEquals(listOf(1, 2), commands.days)
    }

    @Test
    fun aRefusedDaySaysWhy() {
        val (_, vm, _) = viewModel()
        vm.showDay(3)
        vm.onReply(DirectorReply("day-1", "preview", false, "not mine"))
        assertEquals(3, vm.ui.value.pendingDay)
        vm.onReply(DirectorReply("day-1", "day", false, "That day is not on the plan."))
        assertNull(vm.ui.value.pendingDay)
        assertEquals("That day is not on the plan.", vm.ui.value.toast!!.text)
    }

    @Test
    fun openingAgainForgetsADayStillAskedFor() {
        val (_, vm, _) = viewModel()
        vm.showDay(3)
        vm.opened()
        assertNull(vm.ui.value.pendingDay)
        vm.onReply(DirectorReply("day-1", "day", false, "late"))
        assertNull(vm.ui.value.toast)
    }

    @Test
    fun oneTapGoesToThePagePerOpening() {
        val (_, vm, _) = viewModel()
        assertTrue(vm.handOver())
        assertFalse(vm.handOver())
        vm.opened()
        assertTrue(vm.handOver())
    }

    @Test
    fun aNoticeShowsOnceAndATipEachTime() {
        val (_, vm, _) = viewModel()
        vm.notice("page", DirectorNotice(4, "Marked done"))
        val first = vm.ui.value.toast!!
        vm.notice("page", DirectorNotice(4, "Marked done"))
        assertEquals(first, vm.ui.value.toast)
        vm.tip("Work · 9:00 AM–5:00 PM")
        vm.tip("Work · 9:00 AM–5:00 PM")
        val tip = vm.ui.value.toast!!
        assertEquals(first.serial + 2, tip.serial)
        vm.dismiss(first.serial)
        assertEquals(tip, vm.ui.value.toast)
        vm.dismiss(tip.serial)
        assertNull(vm.ui.value.toast)
    }

    @Test
    fun aReloadedPageCountsItsNoticesAfresh() {
        val (_, vm, _) = viewModel()
        vm.notice("page", DirectorNotice(1, "Plan updated"))
        val first = vm.ui.value.toast!!
        vm.notice("reloaded page", DirectorNotice(1, "Skipped"))
        assertEquals(first.serial + 1, vm.ui.value.toast!!.serial)
        assertEquals("Skipped", vm.ui.value.toast!!.text)
    }

    @Test
    fun aTipLeavesTheTapForThePageAndSeeTomorrowAsksForTheDay() {
        // a page from before phase 4A (no sheet field): a session's tap is still the page's
        val repo =
            WebLifeDirectorRepository().apply {
                accept(fixture("evening").apply { remove("sheet") }, true)
            }
        val commands = DirectorRecorder()
        val vm = LifeDirectorViewModel(repo, commands).apply { opened() }
        val emitted = mutableListOf<Tap>()
        val actions = DirectorActions(vm) { act, id -> emitted += Tap(act, id) }
        val today = repo.state.value!!.today!!
        val work = today.dial.arcs.first { it.tap == null && it.tip.isNotEmpty() }
        actions.arc(work)
        assertEquals(work.tip, vm.ui.value.toast!!.text)
        actions.upNext(today.upNext!!.actions.single())
        assertEquals(listOf(1), commands.days)
        val session = today.dial.arcs.first { it.tap != null }
        actions.arc(session)
        actions.arc(session)
        assertEquals(listOf(session.tap), emitted)
    }

    // The dial's geometry

    @Test
    fun anglesAndTimesAgreeWithTheWebDial() {
        val dial = repository().state.value!!.today!!.dial
        val now = repository().state.value!!.today!!.now
        val angleOf = { epoch: Long ->
            (epoch - dial.start).toFloat() / (dial.end - dial.start) * 360f
        }
        assertEquals(angleOf(now), dial.hand!!, .01f)
        for (arc in dial.arcs) {
            // the web trims a small gap off each end of an arc, never more than its own length
            assertTrue(
                arc.title,
                arc.a0 >= angleOf(arc.start) - .01f && arc.a0 - angleOf(arc.start) <= .71f,
            )
            assertTrue(
                arc.title,
                arc.a1 <= angleOf(arc.end) + .01f && angleOf(arc.end) - arc.a1 <= .71f,
            )
        }
        for (angle in listOf(0f, 45f, 90f, 135f, 227.5f, 359f)) {
            val p = DialGeometry.point(101f, angle)
            assertEquals(angle, DialGeometry.angleAt(p), .001f)
            assertEquals(101f, DialGeometry.radiusAt(p), .001f)
        }
        assertEquals(50f, DialGeometry.point(130f, 0f).y, .001f)
        assertEquals(310f, DialGeometry.point(130f, 90f).x, .001f)
    }

    @Test
    fun wordsAreCentredAlongTheirArcAndDroppedWhenTheyDoNotFit() {
        val glyphs = FloatArray(10) { 6f }
        val angles = DialGeometry.alongArc(130f, 135.45f, 254.55f, glyphs, false)!!
        assertEquals((135.45f + 254.55f) / 2, (angles.first() + angles.last()) / 2, .001f)
        assertTrue(angles.toList().zipWithNext().all { (a, b) -> b > a })
        val flipped = DialGeometry.alongArc(130f, 135.45f, 254.55f, glyphs, true)!!
        assertTrue(flipped.toList().zipWithNext().all { (a, b) -> b < a })
        // 7.5 degrees at r110 is 14.4 units: room for 4.4 units of glyphs between the margins
        assertNotNull(DialGeometry.alongArc(110f, 0f, 7.5f, floatArrayOf(4f), false))
        assertNull(DialGeometry.alongArc(110f, 0f, 7.5f, floatArrayOf(3f, 3f), false))
    }

    @Test
    fun aTapFindsItsArcByRingAndAngle() {
        val dial = repository().state.value!!.today!!.dial
        fun at(r: Float, angle: Float) =
            DialGeometry.arcAt(dial.arcs, dial.radii, DialGeometry.point(r, angle))?.title
        assertEquals("Work", at(130f, 200f))
        assertEquals("Work", at(150f, 200f))
        assertEquals("Resistance Training", at(101f, 296f))
        assertEquals("Resistance Training", at(80f, 296f))
        // the skipped session is 4 units long and still has its own target
        assertEquals("Full care day", at(101f, 305.6f))
        // a short arc is a target 48 units long, no more: downtime beyond that is not a button
        assertEquals("Focused Reading", at(101f, 320f))
        assertNull(at(101f, 326f))
        assertNull(at(101f, 100f))
        assertNull(at(40f, 200f))
        assertNull(at(170f, 200f))
    }
}
