package com.lifemax.rpg.app.shell.swipe

import com.lifemax.rpg.app.shell.swipe.SwipeFixtures.defaultBar
import com.lifemax.rpg.app.shell.swipe.SwipeFixtures.nativeRow
import com.lifemax.rpg.app.shell.swipe.SwipeFixtures.state
import com.lifemax.rpg.app.shell.swipe.SwipeInput.Arm
import com.lifemax.rpg.app.shell.swipe.SwipeInput.Closed
import com.lifemax.rpg.app.shell.swipe.SwipeInput.Drawn
import com.lifemax.rpg.app.shell.swipe.SwipeInput.Opened
import com.lifemax.rpg.app.shell.swipe.SwipeInput.Register
import com.lifemax.rpg.app.shell.swipe.SwipeInput.Scene
import com.lifemax.rpg.app.shell.swipe.SwipeInput.Settle
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Random sessions against the model: every input in any order, with the invariants checked after
 * each one and the rest scene once the session winds down (split from SwipeModelTest).
 */
class SwipeModelRandomTest {
    @Test
    fun randomSequencesAlwaysComeBackToTheRestScene() {
        val panes = listOf("character", "meals", "recipes", "log", "plan", "media", "life-director")
        var commits = 0
        var handovers = 0
        repeat(400) { seed ->
            val random = Random(seed)
            val s = SwipeStage(scale = listOf(1f, 1f, 0f, 2f)[random.nextInt(4)])
            val bar = if (random.nextBoolean()) defaultBar else nativeRow
            if (random.nextInt(4) != 0) {
                val composed = panes.filter { random.nextBoolean() }.ifEmpty { listOf("character") }
                s.home(view = composed.random(random), composed = composed, bar = bar)
            }
            repeat(80) { s.randomInput(random, bar, panes) }
            if (s.model.phase is SwipePhase.Armed) s.send(SwipeInput.Disarm)
            if (s.model.phase is SwipePhase.Dragging) {
                s.send(if (random.nextBoolean()) SwipeInput.DragEnd(0f) else SwipeInput.DragCancel)
            }
            s.wait(4000)
            assertEquals("seed $seed", SwipePhase.Idle, s.model.phase)
            SwipeInvariants.rest(s.model, s.env())
            commits += s.sent.size
            handovers += s.handovers
        }
        println("random sequences: $commits commits, $handovers hand-overs")
        // the sequences really exercise the hand-over, not only aborts
        assertTrue("$commits commits", commits >= 150)
        assertTrue("$handovers hand-overs", handovers >= 70)
    }

    /**
     * One step of a random session: mostly what really happens (bar taps, whole gestures, the
     * page's part of a hand-over, time passing), interleaved with noise in any order.
     */
    private fun SwipeStage.randomInput(
        random: Random,
        bar: List<SwipeFixtures.Entry>,
        panes: List<String>,
    ) {
        val pane = panes.random(random)
        val owner = model.owner
        when (random.nextInt(24)) {
            0,
            1,
            2,
            3 -> gesture(random)
            4,
            5,
            6 -> handOver(random, bar)
            7 -> {
                // a bar tap: make-before-break through the page
                send(Register(pane, source = pane != "life-director"), Opened(pane))
                send(Scene(state(pane, bar, on = random.nextInt(6) != 0)))
                if (owner != null && owner != pane && random.nextInt(4) != 0) send(Closed(owner))
            }
            8 -> send(Register(pane, source = pane != "life-director"))
            9 -> if (random.nextInt(3) == 0) send(Opened(pane))
            10 -> if (random.nextInt(3) == 0) send(Closed(pane))
            11 -> if (random.nextInt(4) == 0) send(SwipeInput.Removed(pane))
            12 -> send(Drawn(pane))
            13 -> if (random.nextInt(8) == 0) send(SwipeInput.Drop)
            14 -> {
                val view = owner ?: pane
                send(
                    Scene(
                        state(
                            view,
                            bar,
                            on = random.nextInt(8) != 0,
                            motion = random.nextInt(6) != 0,
                        )
                    )
                )
            }
            15 -> send(Scene(if (random.nextInt(4) == 0) null else state(pane, bar)))
            16 -> {
                val token =
                    sent.lastOrNull()?.takeIf { random.nextInt(4) != 0 }?.token ?: "character#0"
                val outcome =
                    if (random.nextBoolean()) SettleOutcome.Native else SettleOutcome.Refused
                send(Settle(token, outcome))
            }
            17 -> {
                send(Arm(owner ?: pane))
                if (random.nextBoolean())
                    send(SwipeInput.DragStart(540f, if (random.nextBoolean()) 1 else -1, 540f))
            }
            18 -> send(SwipeInput.DragMove(x + random.nextInt(-150, 150)))
            19 ->
                send(
                    when (random.nextInt(3)) {
                        0 -> SwipeInput.DragEnd(random.nextInt(-9000, 9000).toFloat())
                        1 -> SwipeInput.DragCancel
                        else -> SwipeInput.Disarm
                    }
                )
            20,
            21 -> {
                // a finger on a screen that moves: taken hold of when the model allows it, else
                // nothing
                val holder = model.catching(env())?.pane
                if (holder != null) send(SwipeInput.Catch(holder, 540f))
                else send(SwipeInput.Catch(pane, 540f))
            }
            else -> wait(random.nextLong(0, 160))
        }
    }

    /** A whole gesture from the owner: latched, flung, short or pulled back. */
    private fun SwipeStage.gesture(random: Random) {
        val owner = model.owner ?: return
        val dir = if (random.nextBoolean()) 1 else -1
        send(Arm(owner), SwipeInput.DragStart(540f, dir, 540f))
        val u =
            if (random.nextBoolean()) 0.45f + random.nextFloat() * 0.4f
            else random.nextFloat() * 0.45f
        dragTo(u, dir, steps = random.nextInt(1, 14))
        val dp =
            when (random.nextInt(4)) {
                0 -> 1300f + random.nextFloat() * 1700f
                1 -> -700f - random.nextFloat() * 1300f
                else -> random.nextFloat() * 900f
            }
        release(dp, dir)
        if (random.nextBoolean()) wait(random.nextLong(0, 200))
    }

    /** The page's part of the last commit, in order, sometimes with a step missing. */
    private fun SwipeStage.handOver(random: Random, bar: List<SwipeFixtures.Entry>) {
        val request = sent.lastOrNull() ?: return
        val dest = bar.firstOrNull { it.key == request.to }?.view ?: return
        val refuse = random.nextInt(6) == 0
        val steps =
            if (refuse) listOf(Settle(request.token, SettleOutcome.Refused))
            else
                listOf(
                    Settle(request.token, SettleOutcome.Native),
                    Register(dest),
                    Opened(dest),
                    Scene(state(dest, bar)),
                    Drawn(dest),
                    Closed(request.from),
                )
        val skip = random.nextInt(3) == 0
        for (step in steps) {
            if (skip && random.nextInt(4) == 0) continue
            send(step)
            wait(random.nextLong(0, 80))
        }
    }
}
