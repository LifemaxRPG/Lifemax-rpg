package com.lifemax.rpg.feature.overview

import com.lifemax.rpg.feature.overview.data.WebOverviewRepository
import com.lifemax.rpg.feature.overview.domain.MoveGo
import com.lifemax.rpg.feature.overview.domain.OverviewNotice
import com.lifemax.rpg.feature.overview.domain.OverviewReply
import com.lifemax.rpg.feature.overview.presentation.OverviewActions
import com.lifemax.rpg.feature.overview.presentation.OverviewViewModel
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The cards' view model: its own state, and the commands its taps send (plan §3.4, §5). */
class OverviewViewModelTest {
    private fun fixture(name: String = "sample") =
        JSONObject(javaClass.getResource("/fixtures/overview-cards-$name.json")!!.readText())

    private var ids = 0

    private inner class Setup(val name: String = "sample") {
        val repo = WebOverviewRepository().apply { accept(fixture(name), true) }
        val sent = OverviewRecorder()
        val views = mutableListOf<String>()
        val vm =
            OverviewViewModel(repo, sent) { "op-${++ids}" }
                .apply {
                    onNavigate = { views += it }
                    opened()
                }
        val actions = OverviewActions(vm)
        var sequence = 1L

        fun update(j: JSONObject = fixture(name)) {
            repo.accept(j.put("sequence", ++sequence))
            vm.projected()
        }
    }

    private fun celebration(id: String, gains: Boolean = true) =
        JSONObject()
            .put("id", id)
            .put("xp", 40)
            .put("origin", JSONObject().put("x", 200).put("y", 800))
            .put(
                "gains",
                if (!gains) JSONArray()
                else
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("attribute", "physique")
                                .put("short", "STR")
                                .put("xp", 40)
                                .put("color", 0xFFFF7A57)
                        ),
            )

    @Test
    fun oneSteppingAsideTapPerOpening() {
        val s = Setup()
        s.actions.move(MoveGo.Attr("physique"))
        s.actions.shop()
        s.actions.go("character")
        assertEquals(listOf("attr" to listOf("physique", "next")), s.sent.sent)
        assertTrue(s.views.isEmpty())
        s.vm.hidden()
        s.vm.opened()
        s.actions.go("character")
        s.actions.luck()
        assertEquals(listOf("character"), s.views)
        assertEquals(1, s.sent.sent.size)
    }

    @Test
    fun everyTapReachesThePage() {
        val taps: List<Pair<(OverviewActions) -> Unit, Pair<String, List<String>>>> =
            listOf(
                { a: OverviewActions ->
                    a.attr("social", "attrs")
                } to ("attr" to listOf("social", "attrs")),
                { a: OverviewActions -> a.move(MoveGo.Luck) } to ("luck" to listOf("op-1")),
                { a: OverviewActions -> a.shop() } to ("shop" to emptyList()),
                { a: OverviewActions -> a.health() } to ("health" to emptyList()),
                { a: OverviewActions -> a.appAdd() } to ("appAdd" to emptyList()),
                { a: OverviewActions -> a.appsEdit() } to ("appsEdit" to emptyList()),
                { a: OverviewActions -> a.edit() } to ("edit" to emptyList()),
                { a: OverviewActions -> a.plan("inst", "s1") } to ("plan" to listOf("inst", "s1")),
            )
        taps.forEach { (tap, want) ->
            ids = 0
            val s = Setup()
            tap(s.actions)
            assertEquals(listOf(want), s.sent.sent)
        }
        val s = Setup()
        s.actions.move(MoveGo.View("quests"))
        assertEquals(listOf("quests"), s.views)
    }

    @Test
    fun appShortcutsStayAndOnlyThePagesAppsLaunch() {
        val s = Setup()
        s.actions.appLaunch("com.example.maps")
        s.actions.appLaunch("com.evil.app")
        s.actions.appLaunch("com.example.two")
        s.actions.shop() // a launch does not use up the opening's stepping-aside tap
        assertEquals(
            listOf("com.example.maps", "com.example.two"),
            s.sent.sent.filter { it.first == "appLaunch" }.map { it.second.single() },
        )
        assertEquals("shop", s.sent.sent.last().first)
    }

    @Test
    fun theLuckRollKeepsItsOperationUntilThePageShowsIt() {
        val s = Setup()
        s.actions.luck()
        s.vm.hidden()
        s.vm.opened()
        s.actions.luck() // its reply never came: the same operation again
        assertEquals(listOf("op-1", "op-1"), s.sent.sent.map { it.second.single() })
        // the page shows today's roll: the next day's is a new operation
        s.update(
            fixture().apply {
                getJSONObject("tiles")
                    .put("luck", fixture("new").getJSONObject("tiles").getJSONObject("luck"))
            }
        )
        assertNull(s.vm.taps.operation("luck", "WEDNESDAY, OCTOBER 14"))
    }

    @Test
    fun theTipIsDismissedOnceAndARefusalSaysWhy() {
        val s = Setup("new")
        s.actions.dismissTip()
        s.actions.dismissTip() // waiting for the page
        assertEquals(listOf("tip" to listOf("op-1")), s.sent.sent)
        s.vm.onReply(
            OverviewReply(
                "tip-1",
                "tip",
                false,
                false,
                "Could not save to this browser (storage full?)",
            )
        )
        assertEquals("Could not save to this browser (storage full?)", s.vm.notice!!.text)
        // the same dismissal again, under the same operation
        s.actions.dismissTip()
        assertEquals("op-1", s.sent.sent.last().second.single())
        s.vm.onReply(OverviewReply("tip-2", "tip", true, true, ""))
        assertNull(s.vm.taps.operation("tip", "dashboard"))
    }

    @Test
    fun aWaitingCommandStopsWaitingOnReopen() {
        val s = Setup("new")
        s.actions.dismissTip()
        assertTrue(s.vm.taps.tipWaiting)
        s.vm.hidden()
        s.vm.opened()
        assertTrue(!s.vm.taps.tipWaiting)
        s.actions.dismissTip()
        assertEquals(2, s.sent.sent.size)
        assertEquals(s.sent.sent[0], s.sent.sent[1])
    }

    @Test
    fun theWeekDaySurvivesProjectionsAndResetsOnANewDay() {
        val s = Setup()
        assertEquals(6, s.vm.weekDay)
        s.actions.pickDay(2)
        s.update()
        assertEquals(2, s.vm.weekDay)
        val moved = fixture()
        val bars = moved.getJSONObject("tiles").getJSONObject("week").getJSONArray("bars")
        bars.getJSONObject(6).put("date", "2026-10-15")
        s.update(moved)
        assertEquals(6, s.vm.weekDay)
        s.actions.pickDay(9)
        assertEquals(6, s.vm.weekDay)
    }

    @Test
    fun aCelebrationPlaysOncePerIdAndNeverWhileHidden() {
        val s = Setup()
        s.vm.hidden()
        s.update(fixture().put("celebrations", JSONArray().put(celebration("a"))))
        assertNull(s.vm.celebration)
        s.vm.opened()
        assertEquals("a", s.vm.celebration!!.id)
        s.update(
            fixture().put("celebrations", JSONArray().put(celebration("a")).put(celebration("b")))
        )
        assertEquals("a", s.vm.celebration!!.id)
        s.vm.celebrationShown(s.vm.celebration!!)
        assertEquals("b", s.vm.celebration!!.id)
        s.vm.celebrationShown(s.vm.celebration!!)
        s.update(
            fixture()
                .put(
                    "celebrations",
                    JSONArray()
                        .put(celebration("a"))
                        .put(celebration("b"))
                        .put(celebration("c", gains = false)),
                )
        )
        assertNull(s.vm.celebration)
    }

    @Test
    fun thePagesMessageShowsOnce() {
        val s = Setup()
        val notice = JSONObject().put("id", 4).put("text", "Could not open Maps")
        s.update(fixture().put("notice", notice))
        assertEquals(OverviewNotice(4, "Could not open Maps"), s.vm.notice)
        s.vm.noticeShown(s.vm.notice!!)
        s.update(fixture().put("notice", notice))
        assertNull(s.vm.notice)
        s.update(fixture().put("notice", JSONObject().put("id", 5).put("text", "Again")))
        assertNotEquals(null, s.vm.notice)
        s.vm.hidden()
        assertNull(s.vm.notice)
    }
}
