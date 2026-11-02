package com.lifemax.rpg.app.shell.swipe

import com.lifemax.rpg.app.shell.swipe.PaneVisibility.Gone
import com.lifemax.rpg.app.shell.swipe.PaneVisibility.Shown
import com.lifemax.rpg.app.shell.swipe.SwipeFixtures.defaultBar
import com.lifemax.rpg.app.shell.swipe.SwipeFixtures.state
import com.lifemax.rpg.app.shell.swipe.SwipeInput.Closed
import com.lifemax.rpg.app.shell.swipe.SwipeInput.Drawn
import com.lifemax.rpg.app.shell.swipe.SwipeInput.Opened
import com.lifemax.rpg.app.shell.swipe.SwipeInput.Register
import com.lifemax.rpg.app.shell.swipe.SwipeInput.Scene
import com.lifemax.rpg.app.shell.swipe.SwipeInput.Settle
import com.lifemax.rpg.app.shell.swipe.SwipeInput.WebDrawn
import com.lifemax.rpg.app.shell.swipe.SwipeStage.Companion.DENSITY
import com.lifemax.rpg.app.shell.swipe.SwipeStage.Companion.W
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 2 of the tab swipe, the edges (plans/TAB_SWIPE.md §5, §7): the veil that reveals a tab the
 * page draws, the shared bars held until it has faded, the fallback swap, and a screen that slides
 * in at the end of the page's own swipe.
 */
class SwipeEdgesTest {
    /** [view] open, Character and Food composed, on a page that takes swipes into its own tabs. */
    private fun SwipeStage.edges(view: String = "meals", reveal: Boolean = true): SwipeStage {
        listOf("character", "meals").forEach { send(Register(it)) }
        return send(Opened(view), Scene(state(view, reveal = reveal)))
    }

    @Test
    fun aWebTabIsAWallOnAPageFromPhaseOneAndAVeilWithItsLabelOnOneFromPhaseTwo() {
        val composed = setOf("character", "meals")
        val old = state("meals").neighbours("meals", composed)!!
        assertTrue(old.next is Neighbour.Wall)
        val now = state("meals", reveal = true).neighbours("meals", composed)!!
        assertEquals("Cat", (now.next as Neighbour.Reveal).tab.label)
        assertTrue("Character is still the real screen", now.prev is Neighbour.Pane)
        // Money stays a stop and the bar's ends stay ends, whatever the page takes
        val library = state("media", reveal = true).neighbours("media", composed)!!
        assertTrue(library.next is Neighbour.Wall)
        assertEquals("money", library.next.tab?.key)
        val overview = state("dashboard", reveal = true)
        assertTrue(overview.tabs.first().kind == TabKind.Web)
        assertTrue(
            state("character", reveal = true).neighbours("character", composed)!!.prev
                is Neighbour.Reveal
        )
        assertTrue(SwipeState.parse(SwipeFixtures.swipeJson("meals", reveal = true), 3)!!.reveal)
    }

    @Test
    fun aCommitIntoAWebTabRevealsThePageOnceTheWebViewHasDrawnIt() {
        val s = SwipeStage().edges()
        s.grab().dragTo(0.3f)
        val cat = s.scene().veil
        assertNotNull("the labelled veil slides in as the neighbour", cat?.tab)
        assertEquals("Cat", cat?.tab?.label)
        assertEquals(s.pane("meals").tx + W, cat?.tx)
        s.dragTo(0.55f).release(dp = 100f)
        val request = s.sent.single()
        assertEquals("pet", request.to)
        assertTrue(request.toJson().getBoolean("reveal"))
        assertNull("the bars are held only once the page answers web", s.model.holdsChrome)

        // the page answers web before it navigates, then closes the source at once (A23 item 2)
        s.send(Settle(request.token, SettleOutcome.Web))
        assertEquals(request.token, s.model.holdsChrome)
        assertNull("nothing to draw: the page draws it", s.model.awaitingDraw)
        s.wait(30)
        s.send(Closed("meals"))
        val sliding = s.scene()
        assertEquals(
            "the source slides on until it is off",
            Shown,
            sliding.panes["meals"]?.visibility,
        )
        assertEquals("Cat", sliding.veil?.tab?.label)
        s.wait(500)

        // landed: the veil covers the screen until the WebView answers
        val reveal = s.model.phase as SwipePhase.Reveal
        assertEquals(VeilStage(0, reveal.tab, 1f), s.scene().veil)
        assertEquals(Gone, s.pane("meals").visibility)
        assertEquals(request.token, s.model.awaitingWeb)
        assertEquals(request.token, s.model.holdsChrome)
        assertTrue("nothing moves while it waits", !s.model.needsFrame(s.env()))
        s.wait(100).send(WebDrawn("meals#9")) // another reveal's answer changes nothing
        assertEquals(1f, s.scene().veil?.alpha)
        s.send(WebDrawn(request.token))
        assertNull(s.model.awaitingWeb)
        s.wait(56)
        val alpha = s.scene().veil!!.alpha
        assertTrue("fading: $alpha", alpha in 0.3f..0.7f)
        assertEquals("the bars stay until it has faded", request.token, s.model.holdsChrome)
        s.wait(80)
        assertEquals(SwipePhase.Idle, s.model.phase)
        assertNull(s.scene().veil)
        assertNull("then they may go", s.model.holdsChrome)
        assertTrue(s.traces.isEmpty())
    }

    @Test
    fun theVeilFadesAnywayWhenTheWebViewNeverAnswers() {
        val s = SwipeStage().edges()
        val request = s.commitNext()
        s.send(Settle(request.token, SettleOutcome.Web), Closed("meals"))
        while (s.model.phase !is SwipePhase.Reveal) s.wait(8)
        val since = (s.model.phase as SwipePhase.Reveal).since
        assertEquals(since + SwipeModel.REVEAL_CAP_NANOS, s.model.deadline)
        s.wait(390)
        assertEquals(1f, s.scene().veil?.alpha)
        s.wait(20)
        assertTrue(s.scene().veil!!.alpha < 1f)
        s.wait(150)
        assertEquals(SwipePhase.Idle, s.model.phase)
        assertNull(s.model.holdsChrome)
    }

    @Test
    fun theOverviewsLiveShellClosesTheSourceLateAndTheRevealWaitsForIt() {
        // Character → Overview with the cards off: the Overview's body is the web page, and the
        // hand-over keeps Character until the shell has opened (it is not a TabSwipe screen)
        val s = SwipeStage().edges(view = "character")
        s.grab(dir = -1).dragTo(0.55f, dir = -1).release(dp = 100f, dir = -1)
        val request = s.sent.single()
        assertEquals("dashboard", request.to)
        s.send(Settle(request.token, SettleOutcome.Web)).wait(600)
        assertTrue(s.model.phase is SwipePhase.AwaitPage)
        assertEquals("the veil fills the screen, Character off it", 0, s.scene().veil?.tx)
        assertEquals(W, s.pane("character").tx)
        assertEquals(request.token, s.model.holdsChrome)
        s.send(Closed("character"))
        assertTrue(s.model.phase is SwipePhase.Reveal)
        s.send(WebDrawn(request.token)).wait(200)
        assertEquals(SwipePhase.Idle, s.model.phase)
        assertTrue(s.traces.isEmpty())
    }

    @Test
    fun aNativeAnswerWhoseDestinationFallsBackToWebSwapsToTheVeil() {
        val s = SwipeStage().edges(view = "character")
        val request = s.commitNext()
        assertEquals("food", request.to)
        s.send(Settle(request.token, SettleOutcome.Native)).wait(40)
        assertEquals(Shown, s.pane("meals").visibility)
        assertNull(s.model.holdsChrome)
        // Food would not open after all: the page closes Character at once
        s.send(Closed("character"))
        val swapped = s.scene()
        assertEquals("Food", swapped.veil?.tab?.label)
        assertEquals(swapped.panes.getValue("character").tx + W, swapped.veil?.tx)
        assertTrue(swapped.panes.getValue("meals").visibility != Shown)
        assertEquals(request.token, s.model.holdsChrome)
        s.wait(500)
        assertTrue(s.model.phase is SwipePhase.Reveal)
        s.send(WebDrawn(request.token)).wait(200)
        assertEquals(SwipePhase.Idle, s.model.phase)
        assertNull(s.model.holdsChrome)
    }

    @Test
    fun aDestinationThatOpensAfterAllTakesOverFromTheVeil() {
        val s = SwipeStage().edges(view = "character")
        val request = s.commitNext()
        s.send(Settle(request.token, SettleOutcome.Native), Closed("character"))
        assertEquals(request.token, s.model.holdsChrome)
        // the page opened Food late (its close of Character came first): its screen takes the slot
        s.send(Opened("meals"), Scene(state("meals", reveal = true)))
        assertNull(s.model.holdsChrome)
        val adopted = s.scene()
        assertNull(adopted.veil)
        assertEquals(Shown, adopted.panes.getValue("meals").visibility)
        s.wait(600)
        assertEquals(SwipePhase.Idle, s.model.phase)
        assertEquals(PaneStage(0, Shown), s.pane("meals"))
    }

    @Test
    fun aRefusalOrAWatchdogNeverLeavesTheBarsHeld() {
        val s = SwipeStage().edges()
        val first = s.commitNext()
        s.send(Settle(first.token, SettleOutcome.Refused)).wait(600)
        assertEquals(SwipePhase.Idle, s.model.phase)
        assertNull(s.model.holdsChrome)
        // answered web, but the page never closes the source: the watchdog springs it back
        val second = s.commitNext()
        s.send(Settle(second.token, SettleOutcome.Web))
        assertEquals(second.token, s.model.holdsChrome)
        s.wait(3000)
        assertEquals(SwipePhase.Idle, s.model.phase)
        assertNull(s.model.holdsChrome)
        assertEquals(PaneStage(0, Shown), s.pane("meals"))
        assertTrue(s.traces.single().startsWith("watchdog"))
    }

    @Test
    fun thePageWinsOverAReveal() {
        val s = SwipeStage().edges()
        val request = s.commitNext()
        s.send(Settle(request.token, SettleOutcome.Web), Closed("meals")).wait(500)
        assertTrue(s.model.phase is SwipePhase.Reveal)
        // a bar tap to Character during the reveal: its open wins at once, and the veil goes
        s.send(Opened("character"))
        assertEquals(SwipePhase.Idle, s.model.phase)
        assertNull(s.model.holdsChrome)
        assertNull(s.scene().veil)
        assertEquals(PaneStage(0, Shown), s.pane("character"))
    }

    @Test
    fun withMotionOffTheRevealIsAtOnce() {
        val s = SwipeStage(scale = 0f).edges()
        val request = s.commitNext()
        s.send(Settle(request.token, SettleOutcome.Web), Closed("meals")).wait(8)
        assertTrue(s.model.phase is SwipePhase.Reveal)
        s.send(WebDrawn(request.token))
        assertEquals(SwipePhase.Idle, s.model.phase)
    }

    @Test
    fun aScreenThePageSwipedToSlidesInFromItsSideFromItsFirstDraw() {
        val s = SwipeStage()
        s.send(Register("character"), Register("meals"))
        s.send(Opened("meals", SwipeEnter(dir = 1, velocityDp = 1500f)))
        val entering = s.model.phase as SwipePhase.Entering
        assertEquals(1500f * DENSITY / W, entering.velocity)
        // it waits at the right edge, drawn but off screen, until its first draw
        assertEquals(PaneStage(W, Shown, shielded = true), s.pane("meals"))
        assertEquals("meals", s.model.awaitingDraw)
        s.send(Scene(state("meals", reveal = true))).wait(100)
        assertEquals(W, s.pane("meals").tx)
        s.send(Drawn("meals"))
        assertNull(s.model.awaitingDraw)
        var last = W
        repeat(12) {
            s.wait(8)
            val tx = s.pane("meals").tx
            assertTrue("moving left, never past 0: $tx after $last", tx in 0..last)
            last = tx
        }
        assertTrue("well on its way: $last", last < W / 2)
        s.wait(400)
        assertEquals(SwipePhase.Idle, s.model.phase)
        assertEquals(PaneStage(0, Shown), s.pane("meals"))
        assertTrue(s.traces.isEmpty())
    }

    @Test
    fun aScreenEnteringFromThePreviousSideStartsAtMinusW() {
        val s = SwipeStage()
        s.send(Register("character"), Opened("character", SwipeEnter(-1, 0f)))
        assertEquals(-W, s.pane("character").tx)
        s.send(Drawn("character")).wait(500)
        assertEquals(PaneStage(0, Shown), s.pane("character"))
    }

    @Test
    fun anEnterIsAPlainOpenWhenItCannotSlide() {
        // animator scale 0: the switch is at once
        val instant = SwipeStage(scale = 0f)
        instant.send(Register("meals"), Opened("meals", SwipeEnter(1, 900f)))
        assertEquals(SwipePhase.Idle, instant.model.phase)
        // another screen still open under it: no slide over it
        val busy = SwipeStage()
        busy.send(Register("character"), Register("meals"), Opened("character"))
        busy.send(Opened("meals", SwipeEnter(1, 900f)))
        assertEquals(SwipePhase.Idle, busy.model.phase)
        // a plain open
        val plain = SwipeStage()
        plain.send(Register("meals"), Opened("meals"))
        assertEquals(SwipePhase.Idle, plain.model.phase)
    }

    @Test
    fun thePageWinsOverAnEnteringScreen() {
        val s = SwipeStage()
        s.send(Register("character"), Register("meals"))
        s.send(Opened("meals", SwipeEnter(1, 900f)), Drawn("meals")).wait(40)
        s.send(Opened("character"))
        assertEquals(SwipePhase.Idle, s.model.phase)
        assertTrue(s.traces.single().startsWith("abort"))
        // its own close aborts too; one that never draws gives up at the watchdog
        val closed = SwipeStage()
        closed.send(Register("meals"), Opened("meals", SwipeEnter(1, 0f)), Closed("meals"))
        assertEquals(SwipePhase.Idle, closed.model.phase)
        val stuck = SwipeStage()
        stuck.send(Register("meals"), Opened("meals", SwipeEnter(1, 0f))).wait(2600)
        assertEquals(SwipePhase.Idle, stuck.model.phase)
        assertTrue(stuck.traces.single().startsWith("watchdog"))
        assertEquals(PaneStage(0, Shown), stuck.pane("meals"))
    }

    @Test
    fun theOpenCallsEnterFieldsAreRead() {
        assertEquals(SwipeEnter(1, 1200f), SwipeEnter.of("next", 1200.0))
        assertEquals(SwipeEnter(-1, 0f), SwipeEnter.of("prev", null))
        assertEquals(SwipeEnter(1, 0f), SwipeEnter.of("next", -5.0))
        assertEquals(SwipeEnter(1, SwipeEnter.MAX_VELOCITY_DP), SwipeEnter.of("next", 1e9))
        assertEquals(SwipeEnter(1, 0f), SwipeEnter.of("next", Double.NaN))
        assertNull(SwipeEnter.of(null, 1200.0))
        assertNull(SwipeEnter.of("up", 1200.0))
        assertEquals(SettleOutcome.Web, SettleOutcome.of("web"))
        assertEquals(SettleOutcome.Refused, SettleOutcome.of("elsewhere"))
    }

    /** Random sessions with the edges in play: the invariants hold, and every one comes to rest. */
    @Test
    fun randomSessionsWithTheEdgesAlwaysComeToRestWithTheBarsLetGo() {
        val panes = listOf("character", "meals", "log", "plan", "media")
        var reveals = 0
        var entries = 0
        repeat(300) { seed ->
            val random = Random(seed)
            val s = SwipeStage(scale = listOf(1f, 1f, 0f, 2f)[random.nextInt(4)])
            val view = panes.random(random)
            panes.filter { random.nextBoolean() || it == view }.forEach { s.send(Register(it)) }
            s.send(Opened(view), Scene(state(view, reveal = true)))
            repeat(60) {
                val owner = s.model.owner
                val pane = panes.random(random)
                when (random.nextInt(14)) {
                    0,
                    1,
                    2 -> if (owner != null) s.swipe(random, owner)
                    3,
                    4 -> {
                        val request = s.sent.lastOrNull()
                        if (request != null) {
                            val outcome = SettleOutcome.entries.random(random)
                            s.send(Settle(request.token, outcome))
                            if (random.nextInt(4) != 0) s.send(Closed(request.from))
                        }
                    }
                    5 -> s.send(WebDrawn(s.model.awaitingWeb ?: "x#0"))
                    6 -> {
                        val enter = SwipeEnter(if (random.nextBoolean()) 1 else -1, 800f)
                        owner?.let { s.send(Closed(it)) }
                        s.send(Register(pane), Opened(pane, enter))
                        if (s.model.phase is SwipePhase.Entering) entries++
                        if (random.nextBoolean()) s.send(Drawn(pane))
                        s.send(Scene(state(pane, defaultBar, reveal = true)))
                    }
                    7 -> s.send(Opened(pane), Scene(state(pane, reveal = true)))
                    8 -> s.send(Closed(pane))
                    9 -> s.send(Drawn(pane))
                    10 -> if (random.nextInt(6) == 0) s.send(SwipeInput.Drop)
                    else -> s.wait(random.nextLong(0, 200))
                }
                if (s.model.phase is SwipePhase.Reveal && s.model.holdsChrome != null) reveals++
            }
            if (s.model.phase is SwipePhase.Armed) s.send(SwipeInput.Disarm)
            if (s.model.phase is SwipePhase.Dragging) s.send(SwipeInput.DragCancel)
            s.wait(4000)
            assertEquals("seed $seed", SwipePhase.Idle, s.model.phase)
            assertNull("seed $seed", s.model.holdsChrome)
            SwipeInvariants.rest(s.model, s.env())
        }
        println("edges: $reveals frames in a reveal, $entries screens slid in")
        assertTrue("$reveals", reveals > 50)
        assertTrue("$entries", entries > 50)
    }

    private fun SwipeStage.swipe(random: Random, owner: String) {
        val dir = if (random.nextBoolean()) 1 else -1
        send(SwipeInput.Arm(owner), SwipeInput.DragStart(540f, dir, 540f))
        dragTo(0.3f + random.nextFloat() * 0.5f, dir, steps = random.nextInt(1, 10))
        release(if (random.nextBoolean()) 1500f else 100f, dir)
        if (random.nextBoolean()) wait(random.nextLong(0, 300))
    }
}
