package com.lifemax.rpg.app.shell.swipe

import com.lifemax.rpg.app.shell.swipe.SwipeFixtures.defaultBar
import com.lifemax.rpg.app.shell.swipe.SwipeFixtures.state
import kotlin.math.abs
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

/**
 * Drives a [SwipeModel] the way TabSwipe will: a fake clock in milliseconds, a 1080 px wide screen
 * at density 3, frames every 8 ms, and the invariants checked after every input.
 */
class SwipeStage(var scale: Float = 1f) {
    var model = SwipeModel()
        private set

    var t = 0L
        private set

    val effects = mutableListOf<SwipeEffect>()

    /** Commits that came to rest cleanly: the destination open, the source gone, no abort. */
    var handovers = 0
        private set

    /** The finger's raw x while dragging. */
    var x = ANCHOR
        private set

    fun env(): SwipeEnv = SwipeEnv(t * MS, W, DENSITY, scale)

    fun scene(): StageScene = model.scene(env())

    fun pane(id: String): PaneStage = scene().panes.getValue(id)

    val sent: List<SwipeRequest>
        get() = effects.filterIsInstance<SwipeEffect.Send>().map { it.request }

    val haptics: List<HapticMoment>
        get() = effects.filterIsInstance<SwipeEffect.Haptic>().map { it.moment }

    val traces: List<String>
        get() = effects.filterIsInstance<SwipeEffect.Trace>().map { it.note }

    fun send(vararg inputs: SwipeInput): SwipeStage {
        for (input in inputs) {
            val inFlight = model.phase.commit
            val step = model.on(input, env())
            model = step.model
            effects += step.effects
            val clean = step.effects.none { it is SwipeEffect.Trace }
            if (inFlight != null && model.phase == SwipePhase.Idle && clean) handovers++
            if (input is SwipeInput.DragMove) x = input.x
            if (input is SwipeInput.DragStart) x = input.x
            SwipeInvariants.check(model, env())
        }
        return this
    }

    /** Let [ms] pass in 8 ms frames. */
    fun wait(ms: Long): SwipeStage {
        val end = t + ms
        while (t < end) {
            t = min(end, t + FRAME)
            send(SwipeInput.Tick)
        }
        return this
    }

    /** [view] open over the bar, [composed] screens alive, and its scene drawn. */
    fun home(
        view: String = "character",
        composed: List<String> = listOf("character", "meals"),
        bar: List<SwipeFixtures.Entry> = defaultBar,
        on: Boolean = true,
        motion: Boolean = true,
        haptics: Any = "normal",
    ): SwipeStage {
        composed.forEach { send(SwipeInput.Register(it, source = it != "life-director")) }
        return send(
            SwipeInput.Opened(view),
            SwipeInput.Scene(state(view, bar, on, motion, haptics)),
        )
    }

    /** A DOWN on the owner and the claim at the anchor, toward [dir]. */
    fun grab(dir: Int = 1): SwipeStage {
        val owner = requireNotNull(model.owner)
        return send(SwipeInput.Arm(owner), SwipeInput.DragStart(ANCHOR, dir, ANCHOR))
    }

    /** Move the finger to [u] toward [dir] in [steps] events, one per frame. */
    fun dragTo(u: Float, dir: Int = 1, steps: Int = 12): SwipeStage {
        val from = x
        val to = ANCHOR - dir * u * W
        for (i in 1..steps) {
            t += FRAME
            send(SwipeInput.DragMove(from + (to - from) * i / steps))
        }
        return this
    }

    /** Release with the finger moving toward [dir] at [dp] dp/s. */
    fun release(dp: Float = 0f, dir: Int = 1): SwipeStage =
        send(SwipeInput.DragEnd(-dir * dp * DENSITY))

    /** A latched commit toward the next tab from [view]. */
    fun commitNext(): SwipeRequest {
        grab().dragTo(0.55f).release(dp = 100f)
        return sent.last()
    }

    companion object {
        /** Character and Food composed, [pane] open on the default bar and its scene drawn. */
        fun homeOn(pane: String): SwipeModel {
            val env = SwipeEnv(0L, W, DENSITY)
            return listOf(
                    SwipeInput.Register("character"),
                    SwipeInput.Register("meals"),
                    SwipeInput.Register(pane),
                    SwipeInput.Opened(pane),
                    SwipeInput.Scene(state(pane)),
                )
                .fold(SwipeModel()) { model, input -> model.on(input, env).model }
        }

        /** [homeOn], and a DOWN armed on [pane]. */
        fun armedOn(pane: String): SwipeModel =
            homeOn(pane).on(SwipeInput.Arm(pane), SwipeEnv(0L, W, DENSITY)).model

        const val W = 1080
        const val DENSITY = 3f
        const val MS = 1_000_000L
        const val FRAME = 8L
        const val ANCHOR = 540f
    }
}

/** What must hold after every input, and the rest scene that must hold at Idle (plan §3). */
object SwipeInvariants {
    fun check(model: SwipeModel, env: SwipeEnv) {
        val scene = model.scene(env)
        assertEquals("a stage for every screen", model.panes.keys, scene.panes.keys)
        val shown = scene.panes.filterValues { it.visibility == PaneVisibility.Shown }
        assertTrue(
            "one screen at most that is not open: $scene",
            shown.keys.count { it !in model.open } <= 1,
        )
        val xs = shown.values.map { it.tx }.toSet()
        xs.forEach { assertTrue("on or beside the screen: $scene", abs(it) <= env.width) }
        assertTrue("two places at most: $scene", xs.size <= 2)
        if (xs.size == 2)
            assertEquals("side by side: $scene", env.width, abs(xs.first() - xs.last()))
        scene.veil?.let { assertTrue("the veil never covers a screen: $scene", it.tx !in xs) }
        if (model.phase == SwipePhase.Idle) rest(model, env)
    }

    /**
     * Idle: no veil, nothing shielded; the open screens VISIBLE at 0; the composed native
     * neighbours of the owner parked INVISIBLE at ±W while the page allows a swipe with motion;
     * every other screen GONE. Worked out from the tab list, not from the model's own code.
     */
    fun rest(model: SwipeModel, env: SwipeEnv) {
        val scene = model.scene(env)
        assertNull("no veil at rest", scene.veil)
        val parked = parking(model, env)
        for ((pane, stage) in scene.panes) {
            when (pane) {
                in model.open -> assertEquals(pane, PaneStage(0, PaneVisibility.Shown), stage)
                in parked ->
                    assertEquals(
                        pane,
                        PaneStage(parked.getValue(pane), PaneVisibility.Parked, a11yHidden = true),
                        stage,
                    )
                else ->
                    assertEquals(pane, PaneStage(0, PaneVisibility.Gone, a11yHidden = true), stage)
            }
        }
    }

    private fun parking(model: SwipeModel, env: SwipeEnv): Map<String, Int> {
        val owner = model.owner ?: return emptyMap()
        val s = model.state ?: return emptyMap()
        if (!s.on || !s.motion || s.view != owner) return emptyMap()
        val i = s.tabs.indexOfFirst { it.key == s.active }
        if (i < 0 || s.tabs[i].view != owner || !s.tabs[i].source) return emptyMap()
        return listOf(-1, 1)
            .mapNotNull { side ->
                s.tabs
                    .getOrNull(i + side)
                    ?.takeIf {
                        it.kind == TabKind.Native &&
                            it.view in model.panes &&
                            it.view !in model.open
                    }
                    ?.let { it.view to side * env.width }
            }
            .toMap()
    }
}
