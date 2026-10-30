package com.lifemax.rpg.feature.lifedirector

import com.lifemax.rpg.feature.lifedirector.data.WebLifeDirectorRepository
import com.lifemax.rpg.feature.lifedirector.domain.ArcStyle
import com.lifemax.rpg.feature.lifedirector.domain.BlockKind
import com.lifemax.rpg.feature.lifedirector.domain.DirectorNotice
import com.lifemax.rpg.feature.lifedirector.domain.LifeDirectorCommands
import com.lifemax.rpg.feature.lifedirector.domain.SessionState
import com.lifemax.rpg.feature.lifedirector.domain.Tap
import com.lifemax.rpg.feature.lifedirector.presentation.LifeDirectorViewModel
import com.lifemax.rpg.feature.lifedirector.presentation.dial.DialGeometry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    private class Recorder : LifeDirectorCommands {
        val days = mutableListOf<Int>()

        override fun showDay(index: Int) {
            days += index
        }
    }

    private fun viewModel(): Triple<WebLifeDirectorRepository, LifeDirectorViewModel, Recorder> {
        val repo = repository()
        val commands = Recorder()
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
    fun aProjectionWithoutAPlanKeepsTheDayLastShown() {
        val repo = repository()
        repo.accept(fixture().put("sequence", 2).put("today", JSONObject.NULL).put("tab", "week"))
        assertEquals("week", repo.state.value!!.tab)
        assertEquals(0, repo.state.value!!.today!!.dayIndex)
    }

    // The view model

    @Test
    fun choosingADayAsksThePageOnceAndTheReplyClearsIt() {
        val (repo, vm, commands) = viewModel()
        vm.showDay(1)
        vm.showDay(1)
        assertEquals(listOf(1), commands.days)
        assertEquals(1, vm.ui.value.pendingDay)
        repo.accept(fixture("tomorrow").put("sequence", 2))
        vm.onReply("day", true, "")
        assertNull(vm.ui.value.pendingDay)
        assertNull(vm.ui.value.toast)
        vm.showDay(1)
        vm.showDay(9)
        assertEquals(listOf(1), commands.days)
    }

    @Test
    fun aRefusedDaySaysWhy() {
        val (_, vm, _) = viewModel()
        vm.showDay(3)
        vm.onReply("preview", false, "not mine")
        assertEquals(3, vm.ui.value.pendingDay)
        vm.onReply("day", false, "That day is not on the plan.")
        assertNull(vm.ui.value.pendingDay)
        assertEquals("That day is not on the plan.", vm.ui.value.toast!!.text)
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
        vm.notice(DirectorNotice(4, "Marked done"))
        val first = vm.ui.value.toast!!
        vm.notice(DirectorNotice(4, "Marked done"))
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
        // downtime is not a button: a tap on it finds a session up to 24 units along the ring
        assertEquals("Focused Reading", at(101f, 320f))
        assertNull(at(101f, 330f))
        assertNull(at(40f, 200f))
        assertNull(at(170f, 200f))
    }
}
