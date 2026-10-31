package com.lifemax.rpg.app.shell.swipe

/** The model after an input, and what to do once. */
data class SwipeStep(val model: SwipeModel, val effects: List<SwipeEffect> = emptyList())

/**
 * The swipe as a pure state machine (CONTRACT A34): Idle → Armed → Dragging →
 * Settling(commit|cancel) → AwaitPage → Idle. It knows the page's truth (which screens exist and
 * which are open, the latest drawn state), the gesture in flight, and nothing about Views.
 * Immutable; every input returns a new model.
 *
 * Rules:
 * - A swipe moves pixels, never ownership. Only the page's open and close change which screen is
 *   open, and they win: any open or close that is not the hand-over in flight aborts to Idle.
 * - A commit waits for the page: the destination's open adopts the running spring, the source's
 *   close is deferred until it is off screen, and if the source closes first (the destination fell
 *   back to web) the swipe cuts to Idle. A refusal springs back from where the screen is.
 * - Nothing waits forever: a settle snaps at [SwipeTune.SETTLE_CAP_SECONDS], and the watchdog gives
 *   up on the page's part [WATCHDOG_NANOS] after a commit.
 * - At Idle the scene is the rest scene ([restScene]): the open screens at 0, the composed native
 *   neighbours of the owner parked INVISIBLE at ±W, everything else GONE.
 *
 * Driving it (TabSwipe, on the main thread):
 * - Build a [SwipeEnv] for every call: the event's or the frame's time, the width of
 *   android.R.id.content, the density, and `ValueAnimator.getDurationScale()`.
 * - Feed [on] every [SwipeInput]: the plugin hooks ([SwipeInput.Register], [SwipeInput.Opened],
 *   [SwipeInput.Closed], [SwipeInput.Removed]), NativeScreens' [SwipeInput.Drop], each drawn bar
 *   scene ([SwipeInput.Scene]), the page's [SwipeInput.Settle], and every input a screen's
 *   [SwipeArbiter] emits. At a DOWN the arbiter needs [startGate] and [tune].
 * - Run the step's [SwipeEffect]s once, in order.
 * - Apply [scene] to the Views after each input, and in a Choreographer animation callback, with a
 *   [SwipeInput.Tick] first, for as long as [needsFrame] holds.
 * - While [deadline] is set, post a [SwipeInput.Tick] for it.
 * - While [awaitingDraw] names a screen, send [SwipeInput.Drawn] after its first OnPreDraw.
 * - After each input, a screen whose gesture the model no longer [tracking]s calls
 *   [SwipeArbiter.yieldGesture].
 * - Back at Idle, check the Views against [restScene] and repair any difference.
 */
data class SwipeModel(
    val phase: SwipePhase = SwipePhase.Idle,
    /** The latest drawn scene's `swipe` state. */
    val state: SwipeState? = null,
    /** Panes whose screen exists, and whether each may start a swipe. */
    val panes: Map<String, Boolean> = emptyMap(),
    /** The page's open screens, oldest first. */
    val open: List<String> = emptyList(),
    /** Commits so far, for unique tokens. */
    val serial: Int = 0,
) {
    /** The newest open screen. */
    val owner: String?
        get() = open.lastOrNull()

    /** The feel the page asks for now: the arbiter's slop, ratio and edge zone at a DOWN. */
    val tune: SwipeTune
        get() = state?.tune ?: SwipeTune.DEFAULT

    /**
     * When the watchdog fires, while a commit waits for the page's part (the destination's open and
     * first draw, the source's close). Post a [SwipeInput.Tick] for it.
     */
    val deadline: Long?
        get() = phase.commit?.takeUnless { it.complete }?.let { it.at + WATCHDOG_NANOS }

    /**
     * The commit's destination while it has not drawn: a screen opened cold under the veil. It may
     * not exist yet; watch it for its first OnPreDraw from its [SwipeInput.Register] on.
     */
    val awaitingDraw: String?
        get() = phase.commit?.takeUnless { it.drawn }?.dest

    /** Whether [pane]'s gesture is still the model's; if not, [SwipeArbiter.yieldGesture]. */
    fun tracking(pane: String): Boolean =
        (phase is SwipePhase.Armed || phase is SwipePhase.Dragging) && phase.gesture?.source == pane

    /** Whether the next frame draws something new without new input. */
    fun needsFrame(env: SwipeEnv): Boolean =
        when (val p = phase) {
            is SwipePhase.Dragging ->
                p.gesture.motion && !p.drag.tension.isAtRest(env.now, env.epsilon)
            is SwipePhase.Settling -> true
            else -> false
        }

    /** The model's gates for a DOWN on [pane]; null when a swipe may start. */
    fun startGate(pane: String): SwipeGate? {
        val s = state
        return when {
            s == null || !s.on -> SwipeGate.Off
            open != listOf(pane) -> SwipeGate.NotOwner
            panes[pane] != true -> SwipeGate.NotSource
            s.neighbours(pane, panes.keys) == null -> SwipeGate.StaleView
            phase != SwipePhase.Idle -> SwipeGate.Busy
            else -> null
        }
    }

    /**
     * The model after [input] at [env]'s time, then after any time-driven move that time brings
     * (landing, the hand-over completing, the watchdog), with the effects to run once.
     */
    fun on(input: SwipeInput, env: SwipeEnv): SwipeStep {
        val step =
            when (input) {
                is SwipeInput.Register ->
                    SwipeStep(copy(panes = panes + (input.pane to input.source)))
                is SwipeInput.Opened -> opened(input.pane)
                is SwipeInput.Closed -> closed(input.pane)
                is SwipeInput.Removed -> removed(input.pane)
                is SwipeInput.Drawn -> drawn(input.pane)
                SwipeInput.Drop -> SwipeStep(SwipeModel(panes = panes, serial = serial))
                is SwipeInput.Scene -> scene(input.state, env)
                is SwipeInput.Settle -> settle(input, env)
                is SwipeInput.Arm -> arm(input.pane)
                is SwipeInput.DragStart -> dragStart(input, env)
                is SwipeInput.DragMove -> dragMove(input.x, env)
                is SwipeInput.DragEnd -> release(input.velocityX, env)
                SwipeInput.DragCancel,
                SwipeInput.Disarm -> letGo(env)
                SwipeInput.Tick -> SwipeStep(this)
            }
        val advanced = step.model.advance(env)
        return SwipeStep(advanced.model, step.effects + advanced.effects)
    }

    private fun opened(pane: String): SwipeStep {
        // a screen opened before it registered counts as a source, as most are
        val m = copy(panes = panes + (pane to (panes[pane] ?: true)), open = open - pane + pane)
        val commit = phase.commit
        return when {
            phase == SwipePhase.Idle -> SwipeStep(m)
            commit != null && pane == commit.dest -> SwipeStep(m.with(commit.copy(opened = true)))
            else -> m.abort("open $pane")
        }
    }

    private fun closed(pane: String): SwipeStep {
        if (pane !in open) return SwipeStep(this)
        val m = copy(open = open - pane)
        val commit = phase.commit
        return when {
            phase == SwipePhase.Idle -> SwipeStep(m)
            commit != null && pane == phase.gesture?.source ->
                if (commit.opened) SwipeStep(m.with(commit.copy(sourceClosed = true)))
                else m.abort("cut: $pane closed before ${commit.dest} opened")
            else -> m.abort("close $pane")
        }
    }

    private fun removed(pane: String): SwipeStep {
        val m = copy(panes = panes - pane, open = open - pane)
        val involved = phase.gesture?.involved.orEmpty()
        return if (pane in involved) m.abort("removed $pane") else SwipeStep(m)
    }

    private fun drawn(pane: String): SwipeStep {
        val commit = phase.commit
        return if (commit != null && pane == commit.dest) SwipeStep(with(commit.copy(drawn = true)))
        else SwipeStep(this)
    }

    /**
     * A new state. Before the claim it must still allow this screen's swipe; during the drag a
     * state that no longer does springs back; while settling, a scene for any view but the source
     * or the destination means the page went elsewhere.
     */
    private fun scene(next: SwipeState?, env: SwipeEnv): SwipeStep {
        val m = copy(state = next)
        val gesture = phase.gesture ?: return SwipeStep(m)
        val allows = next != null && next.on && next.view == gesture.source
        return when (val p = phase) {
            is SwipePhase.Armed -> SwipeStep(if (allows) m else m.copy(phase = SwipePhase.Idle))
            is SwipePhase.Dragging -> if (allows) SwipeStep(m) else m.springBack(p, env)
            is SwipePhase.Sliding -> {
                val dest = gesture.neighbours[p.slide.dir].tab?.view
                val elsewhere = next != null && next.view != gesture.source && next.view != dest
                if (elsewhere) m.abort("scene for ${next?.view}") else SwipeStep(m)
            }
            SwipePhase.Idle -> SwipeStep(m)
        }
    }

    /** A native answer only confirms the wait; a refusal springs back from where the screen is. */
    private fun settle(answer: SwipeInput.Settle, env: SwipeEnv): SwipeStep {
        val p = phase as? SwipePhase.Sliding ?: return SwipeStep(this)
        val commit = p.commit
        if (commit == null || answer.token != commit.token) return SwipeStep(this)
        if (answer.outcome == SettleOutcome.Native) return SwipeStep(this)
        if (commit.opened) return abort("refused after ${commit.dest} opened")
        return SwipeStep(copy(phase = p.back(env)), p.gesture.haptic(HapticMoment.Refused))
    }

    private fun arm(pane: String): SwipeStep {
        val s = state
        if (startGate(pane) != null || s == null) return SwipeStep(this)
        val neighbours = s.neighbours(pane, panes.keys) ?: return SwipeStep(this)
        val gesture = Gesture(pane, neighbours, s.sequence, s.motion, s.haptics, s.tune)
        return SwipeStep(copy(phase = SwipePhase.Armed(gesture)))
    }

    private fun dragStart(start: SwipeInput.DragStart, env: SwipeEnv): SwipeStep {
        val armed = phase as? SwipePhase.Armed ?: return SwipeStep(this)
        val drag = Drag(start.anchorX, start.dir, start.anchorX, Tension())
        return copy(phase = SwipePhase.Dragging(armed.gesture, drag)).dragMove(start.x, env)
    }

    /**
     * The finger moved. Once it is back past the anchor, and the screen with it, the drag turns to
     * the other side, with the residual mirrored so nothing jumps.
     */
    private fun dragMove(x: Float, env: SwipeEnv): SwipeStep {
        val p = phase as? SwipePhase.Dragging ?: return SwipeStep(this)
        val g = p.gesture
        val haptics = mutableListOf<SwipeEffect>()
        fun Drag.follow(): Drag {
            val followed = tension.follow(u(env), env.now, g.scale(env), g.tune, g.isWall(dir))
            haptics +=
                when (followed.flip) {
                    Flip.Latch -> g.haptic(HapticMoment.Latch)
                    Flip.Unlatch -> g.haptic(HapticMoment.Unlatch)
                    null -> emptyList()
                }
            return copy(tension = followed.tension)
        }
        var drag = p.drag.copy(x = x).follow()
        val u = drag.u(env)
        if (u < 0f && drag.tension.raw(u, env.now, g.tune, g.isWall(drag.dir)) <= 0f) {
            drag = drag.copy(dir = -drag.dir, tension = drag.tension.reversed(env.now)).follow()
        }
        return SwipeStep(copy(phase = SwipePhase.Dragging(g, drag)), haptics)
    }

    /** The release rule, then the settle from where the screen is, as fast as it moves. */
    private fun release(velocityX: Float, env: SwipeEnv): SwipeStep {
        val p = phase as? SwipePhase.Dragging ?: return letGo(env)
        val g = p.gesture
        val drag = p.drag
        val toward = -drag.dir * velocityX
        val verdict =
            ReleaseRule.decide(
                g.isWall(drag.dir),
                drag.tension.latched,
                drag.u(env),
                toward / env.density,
                g.tune,
            )
        val side = g.neighbours[drag.dir]
        val tab = side.tab
        val via = verdict.via
        if (via == null || tab == null) return springBack(p, env, toward)
        val token = "${g.source}#${serial + 1}"
        val commit = Commit(token, tab.view, env.now, drawn = side is Neighbour.Pane)
        val request = SwipeRequest(token, g.source, tab.key, drag.dir, g.sequence, via)
        val slide = g.settle(drag, toward, commit = true, env)
        return SwipeStep(
            copy(phase = SwipePhase.Settling(g, slide, commit), serial = serial + 1),
            listOf(SwipeEffect.Send(request)) +
                if (via == Via.Fling) g.haptic(HapticMoment.Fling) else emptyList(),
        )
    }

    /** A cancel before or after the claim. */
    private fun letGo(env: SwipeEnv): SwipeStep =
        when (val p = phase) {
            is SwipePhase.Armed -> SwipeStep(copy(phase = SwipePhase.Idle))
            is SwipePhase.Dragging -> springBack(p, env)
            else -> SwipeStep(this)
        }

    /** The drag springs back to 0, leaving as fast as the screen moves ([toward] in px/s). */
    private fun springBack(p: SwipePhase.Dragging, env: SwipeEnv, toward: Float = 0f): SwipeStep =
        SwipeStep(
            copy(
                phase =
                    SwipePhase.Settling(
                        p.gesture,
                        p.gesture.settle(p.drag, toward, false, env),
                        null,
                    )
            )
        )

    /** Time-driven moves: landing, the hand-over completing, the watchdog. */
    private fun advance(env: SwipeEnv): SwipeStep {
        val p = phase as? SwipePhase.Sliding ?: return SwipeStep(this)
        val commit = p.commit
        val deadline = deadline
        return when {
            commit != null && deadline != null && env.now >= deadline -> {
                val note = SwipeEffect.Trace("watchdog: ${commit.token} toward ${commit.dest}")
                val stillOpen = p.gesture.source in open && !commit.opened
                SwipeStep(
                    copy(phase = if (stillOpen) p.back(env) else SwipePhase.Idle),
                    listOf(note),
                )
            }
            !p.slide.landed(env) -> SwipeStep(this)
            commit == null || commit.complete -> SwipeStep(copy(phase = SwipePhase.Idle))
            p is SwipePhase.Settling ->
                SwipeStep(copy(phase = SwipePhase.AwaitPage(p.gesture, p.slide, commit)))
            else -> SwipeStep(this)
        }
    }

    /** The settle turned back to 0 from where it is, keeping its velocity: a cancel now. */
    private fun SwipePhase.Sliding.back(env: SwipeEnv): SwipePhase.Settling {
        val turned = gesture.settle(slide.dir, slide.offset(env), slide.velocity(env), false, env)
        return SwipePhase.Settling(gesture, turned, null)
    }

    private fun abort(reason: String): SwipeStep =
        SwipeStep(copy(phase = SwipePhase.Idle), listOf(SwipeEffect.Trace("abort: $reason")))

    private fun with(commit: Commit): SwipeModel =
        copy(
            phase =
                when (val p = phase) {
                    is SwipePhase.Settling -> p.copy(commit = commit)
                    is SwipePhase.AwaitPage -> p.copy(commit = commit)
                    else -> p
                }
        )

    companion object {
        /**
         * How long a commit waits for the page's part (A23's flush). Then the source springs back
         * if the destination never opened; otherwise the wait ends and the open screens rest.
         */
        const val WATCHDOG_NANOS = 2_500_000_000L
    }
}
