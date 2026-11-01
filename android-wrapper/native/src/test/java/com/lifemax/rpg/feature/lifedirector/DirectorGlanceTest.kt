package com.lifemax.rpg.feature.lifedirector

import com.lifemax.rpg.feature.lifedirector.data.DirectorGlanceJson
import com.lifemax.rpg.feature.lifedirector.domain.ArcStyle
import com.lifemax.rpg.feature.lifedirector.domain.GlanceState
import com.lifemax.rpg.feature.lifedirector.domain.Tap
import com.lifemax.rpg.feature.lifedirector.presentation.components.clockText
import com.lifemax.rpg.feature.lifedirector.presentation.dial.DialIntro
import com.lifemax.rpg.feature.lifedirector.presentation.dial.DialIntroLedger
import com.lifemax.rpg.feature.lifedirector.presentation.glance.GlanceTaps
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Life Director's glance on the native Overview, from the page's own LifeDirector.native.glance()
 * (fixtures/director-glance-*.json, tools/gen_director_glance_fixtures.js): the strict decoder, the
 * taps it sends back, the running timer's clock and the arrival's timeline.
 */
class DirectorGlanceTest {
    private fun text(name: String) =
        javaClass.getResource("/fixtures/director-glance-$name.json")!!.readText()

    private fun json(name: String) = JSONObject(text(name))

    private fun glance(name: String) = DirectorGlanceJson.decode(text(name))

    private fun rejects(name: String, edit: JSONObject.() -> Unit) {
        val j = json(name).apply(edit)
        assertThrows(Exception::class.java) { DirectorGlanceJson.decode(j) }
    }

    /** tiles.plan exactly as the Overview's projection carries it (overview-glance-*.json). */
    @Test
    fun theOverviewsTilesPlanDecodes() {
        val text = { n: String ->
            javaClass.getResource("/fixtures/overview-glance-$n.json")!!.readText()
        }
        val setup = DirectorGlanceJson.decode(text("setup"))
        assertEquals(GlanceState.SETUP, setup.state)
        assertEquals("Plan my days", setup.button)
        val day = DirectorGlanceJson.decode(text("day"))
        assertEquals(GlanceState.DAY, day.state)
        assertTrue(day.dial!!.handles.isEmpty() && day.dial!!.build == null)
        assertNotNull(day.foot)
        val timer = DirectorGlanceJson.decode(text("timer")).centre!!.timer!!
        assertEquals(Tap("focusOpen"), timer.tap)
        // fixed time's tip says nothing about a handle the glance does not have
        val bands = day.dial!!.arcs.filter { it.style == ArcStyle.BAND }
        assertTrue(bands.isNotEmpty())
        for (n in listOf("day", "next", "evening")) assertTrue(
            glance(n)
                .dial!!
                .arcs
                .filter { it.style == ArcStyle.BAND }
                .none { it.tip.contains("drag", ignoreCase = true) }
        )
    }

    @Test
    fun setUpIsTheEmptyStateWithItsButton() {
        val g = glance("setup")
        assertEquals(GlanceState.SETUP, g.state)
        assertEquals("Today’s plan", g.title)
        assertEquals("Life Director ›", g.link)
        assertEquals("Plan my days", g.button)
        assertTrue(g.intro!!.startsWith("Tell Life Director"))
        assertNull(g.dial)
        assertNull(g.centre)
        assertNull(g.foot)
    }

    @Test
    fun aDayIsTodaysDialWithoutHandlesOrBuild() {
        val g = glance("day")
        assertEquals(GlanceState.DAY, g.state)
        val dial = g.dial!!
        assertTrue(dial.handles.isEmpty())
        assertTrue(dial.stops.isEmpty())
        assertNull(dial.build)
        assertNotNull(dial.hand)
        assertEquals(4, dial.arcs.count { it.style == ArcStyle.SESSION })
        assertEquals("Working", g.centre!!.title)
        assertEquals("Next in 1h 50m", g.centre!!.next!!.label)
        assertEquals("2 sessions left · 1h 30m", g.foot!!.text)
        assertEquals("1 done", g.foot!!.done)
        assertNull(g.intro)
        assertNull(g.button)
    }

    @Test
    fun theNextSessionAndTheTimersDecode() {
        val next = glance("next").centre!!
        assertTrue(next.next!!.live)
        assertEquals("inst", next.next!!.tap!!.act)
        assertEquals("focusStart", next.button!!.tap.act)
        val running = glance("timer").centre!!.timer!!
        assertNull(running.paused)
        assertEquals(Tap("focusOpen"), running.tap)
        assertNotNull(glance("paused").centre!!.timer!!.paused)
        assertEquals("Every session done", glance("evening").foot!!.text)
    }

    @Test
    fun theFullTitleIsKeptBesideTheCleanedOne() {
        val j = json("timer")
        j.getJSONObject("core").put("title", "Spanish").put("fullTitle", "Plan: Spanish (A1)")
        val g = DirectorGlanceJson.decode(j)
        assertEquals("Spanish", g.centre!!.title)
        assertEquals("Plan: Spanish (A1)", g.fullTitle)
    }

    @Test
    fun theDecoderIsStrict() {
        rejects("day") { remove("foot") }
        rejects("day") { put("foot", JSONObject.NULL) }
        rejects("day") { put("state", "week") }
        rejects("day") { put("title", 3) }
        rejects("day") { put("intro", "words") }
        rejects("day") { remove("intro") }
        rejects("day") { getJSONObject("core").remove("fullTitle") }
        rejects("day") { getJSONObject("core").put("fullTitle", " ") }
        rejects("day") { getJSONObject("foot").remove("done") }
        rejects("day") { getJSONObject("dial").remove("arcs") }
        rejects("day") { getJSONObject("dial").put("hand", 400) }
        // a glance is read-only: what only Today has must not be there, even empty
        rejects("day") { getJSONObject("dial").put("handles", org.json.JSONArray()) }
        rejects("day") { getJSONObject("dial").put("build", JSONObject.NULL) }
        rejects("day") { getJSONObject("dial").put("dragWords", JSONObject()) }
        rejects("setup") { put("button", JSONObject.NULL) }
        rejects("setup") { put("dial", json("day").getJSONObject("dial")) }
        rejects("setup") { remove("core") }
        rejects("setup") { put("button", false) }
    }

    @Test
    fun tapsAreThePagesActsAndTheRestOnlyOpens() {
        val dial = glance("next").dial!!
        val session = dial.arcs.first { it.style == ArcStyle.SESSION }
        assertEquals(session.tap, GlanceTaps.arc(session))
        assertEquals("inst", GlanceTaps.arc(session).act)
        val meal = dial.arcs.first { it.style == ArcStyle.MEAL }
        assertEquals("meal", GlanceTaps.arc(meal).act)
        assertEquals(meal.tap!!.id, GlanceTaps.arc(meal).id)
        // fixed time shows its tip only on Life Director itself: the glance just opens
        val band = dial.arcs.first { it.style == ArcStyle.BAND }
        assertEquals(Tap(""), GlanceTaps.arc(band))
        // the middle lets only the running timer through, as the page's glance does
        assertEquals(Tap("focusOpen"), GlanceTaps.centre(glance("timer").centre!!.timer!!.tap!!))
        assertEquals(Tap("glanceOpen"), GlanceTaps.centre(glance("next").centre!!.button!!.tap))
        assertEquals(Tap("glanceOpen"), GlanceTaps.centre(glance("next").centre!!.next!!.tap!!))
    }

    /** The clock is read from startedAt and pausedMs at each tick, never counted. */
    @Test
    fun theClockIsDerivedFromThePagesTimes() {
        val running = glance("timer").centre!!.timer!!
        val at = running.startedAt + 754_000 // 12 min 34 s in
        assertEquals("12:34", clockText(running.elapsedMs(at)))
        assertEquals("12:34", clockText(running.elapsedMs(at + 999)))
        assertEquals("12:35", clockText(running.elapsedMs(at + 1_000)))
        assertEquals("1:00:00", clockText(running.elapsedMs(running.startedAt + 3_600_000)))
        // the same moment always reads the same, however often it is asked
        assertEquals(running.elapsedMs(at + 5_000), running.elapsedMs(at + 5_000))
        // time spent paused earlier is taken off
        assertEquals("10:34", clockText(running.copy(pausedMs = 120_000).elapsedMs(at)))
        // a clock wound back before the start shows nothing gone
        assertEquals("0:00", clockText(running.elapsedMs(running.startedAt - 5_000)))
        // paused, it stands where it was paused whatever the time now
        val paused = glance("paused").centre!!.timer!!
        val stood = clockText(paused.elapsedMs(paused.paused!!))
        assertEquals("12:34", stood)
        assertEquals(stood, clockText(paused.elapsedMs(paused.paused!! + 3 * 60_000)))
        assertEquals(stood, clockText(paused.elapsedMs(paused.paused!! + 86_400_000)))
    }

    /** The web's ld-dial-in, number for number. */
    @Test
    fun theArrivalFollowsThePagesTimings() {
        assertEquals(0f, DialIntro.outer(0f))
        assertEquals(1f, DialIntro.outer(500f))
        assertEquals(0f, DialIntro.words(200f))
        assertEquals(1f, DialIntro.words(700f))
        assertEquals(0f, DialIntro.session(250f, 0))
        assertEquals(0f, DialIntro.session(390f, 2))
        assertTrue(DialIntro.session(500f, 2) in 0f..1f)
        assertEquals(1f, DialIntro.session(250f + 140f + 450f, 2))
        assertEquals(0f, DialIntro.hand(500f))
        assertEquals(1f, DialIntro.hand(900f))
        // var(--ease) is quick: past half way a fifth of the way in
        assertTrue(DialIntro.outer(100f) > .5f)
        assertEquals(900f, DialIntro.end(0))
        assertEquals(250f + 70f * 5 + 450f, DialIntro.end(6))
        // --i counts sessions only, in the dial's order
        val arcs = glance("day").dial!!.arcs
        val order = DialIntro.order(arcs)
        assertEquals(arcs.count { it.style == ArcStyle.SESSION }, order.size)
        assertEquals(listOf(0, 1, 2, 3), arcs.mapNotNull { order[it] })
    }

    @Test
    fun eachArrivalPlaysOnce() {
        val ledger = DialIntroLedger()
        assertTrue(ledger.take(1))
        assertFalse(ledger.take(1))
        assertTrue(ledger.take(2))
        assertFalse(ledger.take(2))
    }
}
