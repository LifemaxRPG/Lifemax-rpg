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
 * - While [holdsChrome] names a token, hold the shared bars (`ChromeHost.hold`); unhold it after.
 * - When [awaitingWeb] names a token, ask the WebView (`postVisualStateCallback`) and send
 *   [SwipeInput.WebDrawn] with it when it answers.
 * - After each input, a screen whose gesture the model no longer [tracking]s calls
 *   [SwipeArbiter.yieldGesture].
 * - Back at Idle, check the Views against [restScene] and repair any difference.
 *
 * Phase 3 (chained swipes; SwipeCatch.kt): a finger that lands on a screen still sliding may take
 * hold of it ([catching], [SwipeInput.Catch]) and the drag goes on from where it stands.
 *
 * Phase 2 (the edges; SwipeEdges.kt): a commit into a tab the page draws ends in
 * [SwipePhase.Reveal], and a screen the page opens at the end of its own swipe slides in
 * ([SwipePhase.Entering]).
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
     * first draw, the source's close); when a reveal stops waiting for the WebView; when an
     * entering screen that never drew gives up. Post a [SwipeInput.Tick] for it.
     */
    val deadline: Long?
        get() =
            when (val p = phase) {
                is SwipePhase.Reveal -> if (p.fadeAt == null) p.since + REVEAL_CAP_NANOS else null
                is SwipePhase.Entering -> if (p.spring == null) p.since + WATCHDOG_NANOS else null
                else -> phase.commit?.takeUnless { it.complete }?.let { it.at + WATCHDOG_NANOS }
            }

    /**
     * The commit's destination while it has not drawn: a screen opened cold under the veil; or an
     * entering screen before its first draw. It may not exist yet; watch it for its first OnPreDraw
     * from its [SwipeInput.Register] on.
     */
    val awaitingDraw: String?
        get() =
            (phase as? SwipePhase.Entering)?.takeIf { it.spring == null }?.pane
                ?: phase.commit?.takeUnless { it.drawn || it.reveal }?.dest

    /**
     * The token of a commit the page draws, from the page's `web` answer (or the fallback) until
     * the veil has faded: the shared bars must not leave meanwhile, or the veil (under them, over
     * the WebView's own bars) would show where they were.
     */
    val holdsChrome: String?
        get() =
            when (val p = phase) {
                is SwipePhase.Reveal -> p.token
                is SwipePhase.Sliding -> p.commit?.takeIf { it.reveal }?.token
                else -> null
            }

    /** The token of a reveal waiting for the WebView to draw the page under the veil. */
    val awaitingWeb: String?
        get() = (phase as? SwipePhase.Reveal)?.takeIf { it.fadeAt == null }?.token

    /** Whether [pane]'s gesture is still the model's; if not, [SwipeArbiter.yieldGesture]. */
    fun tracking(pane: String): Boolean =
        (phase is SwipePhase.Armed || phase is SwipePhase.Dragging) && phase.gesture?.source == pane

    /** Whether the next frame draws something new without new input. */
    fun needsFrame(env: SwipeEnv): Boolean =
        when (val p = phase) {
            is SwipePhase.Dragging ->
                p.gesture.motion && !p.drag.tension.isAtRest(env.now, env.epsilon)
            is SwipePhase.Settling -> true
            is SwipePhase.Reveal -> p.fadeAt != null
            is SwipePhase.Entering -> p.spring != null
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
                is SwipeInput.Opened -> opened(input.pane).thenEnter(input, env)
                is SwipeInput.Closed -> closed(input.pane)
                is SwipeInput.Removed -> removed(input.pane)
                is SwipeInput.Drawn -> drawn(input.pane, env)
                is SwipeInput.WebDrawn -> webDrawn(input.token, env)
                SwipeInput.Drop -> SwipeStep(SwipeModel(panes = panes, serial = serial))
                is SwipeInput.Scene -> scene(input.state, env)
                is SwipeInput.Settle -> settle(input, env)
                is SwipeInput.Arm -> arm(input.pane)
                is SwipeInput.Catch -> catchGrab(input, env)
                is SwipeInput.DragStart -> dragStart(input, env)
                is SwipeInput.DragMove -> dragMove(input.x, env)
                is SwipeInput.DragEnd -> release(input.velocityX, env)
                SwipeInput.DragCancel,
                SwipeInput.Disarm -> letGo(env)
                SwipeInput.Tick -> SwipeStep(this)
            }
        val (model, later) = step.model.advance(env)
        return when {
            later.isNotEmpty() -> SwipeStep(model, step.effects + later)
            model === step.model -> step // most frames: time brought no move of its own
            else -> SwipeStep(model, step.effects)
        }
    }

    private fun opened(pane: String): SwipeStep {
        // a screen opened before it registered counts as a source, as most are
        val m = copy(panes = panes + (pane to (panes[pane] ?: true)), open = open - pane + pane)
        val commit = phase.commit
        return when {
            phase == SwipePhase.Idle -> SwipeStep(m)
            // the destination's screen opened after all, even after a fallback: it takes the slot
            commit != null && pane == commit.dest ->
                SwipeStep(m.with(commit.copy(opened = true, reveal = false)))
            else -> m.abort("open $pane")
        }
    }

    /**
     * The source closing before the destination opened means the page draws the destination: it
     * answered `web`, or its native screen fell back to web (Plan editing, the Library player),
     * which then swaps to the veil and is revealed the same way.
     */
    private fun closed(pane: String): SwipeStep {
        if (pane !in open) return SwipeStep(this)
        val m = copy(open = open - pane)
        val commit = phase.commit
        val p = phase
        return when {
            phase == SwipePhase.Idle -> SwipeStep(m)
            p is SwipePhase.Entering && pane != p.pane -> SwipeStep(m)
            commit != null && pane == phase.gesture?.source ->
                when {
                    commit.opened || commit.reveal ->
                        SwipeStep(m.with(commit.copy(sourceClosed = true)))
                    else ->
                        SwipeStep(
                            m.with(commit.copy(reveal = true, sourceClosed = true)),
                            listOf(SwipeEffect.Trace("fallback: ${commit.dest} drawn by the page")),
                        )
                }
            else -> m.abort("close $pane")
        }
    }

    private fun removed(pane: String): SwipeStep {
        val m = copy(panes = panes - pane, open = open - pane)
        val involved =
            (phase as? SwipePhase.Entering)?.let { setOf(it.pane) }
                ?: phase.gesture?.involved.orEmpty()
        return if (pane in involved) m.abort("removed $pane") else SwipeStep(m)
    }

    private fun drawn(pane: String, env: SwipeEnv): SwipeStep {
        val p = phase
        if (p is SwipePhase.Entering) return SwipeStep(entered(p, pane, env))
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
            is SwipePhase.Reveal,
            is SwipePhase.Entering,
            SwipePhase.Idle -> SwipeStep(m)
        }
    }

    /**
     * A native answer only confirms the wait; a web answer turns the commit into a reveal (the bars
     * are held from now on, before the page's close of the source can release them); a refusal
     * springs back from where the screen is.
     */
    private fun settle(answer: SwipeInput.Settle, env: SwipeEnv): SwipeStep {
        val p = phase as? SwipePhase.Sliding ?: return SwipeStep(this)
        val commit = p.commit
        if (commit == null || answer.token != commit.token) return SwipeStep(this)
        if (answer.outcome == SettleOutcome.Native) return SwipeStep(this)
        if (answer.outcome == SettleOutcome.Web) {
            return if (commit.opened) SwipeStep(this)
            else SwipeStep(with(commit.copy(reveal = true)))
        }
        // too late to spring back: the destination took over, or the source has gone (a fallback)
        if (commit.opened || commit.sourceClosed) return abort("refused after the page moved on")
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

    /**
     * Time-driven moves: landing, the hand-over completing (or the reveal starting), the watchdog,
     * the reveal's fade and an entering screen's slide.
     */
    private fun advance(env: SwipeEnv): SwipeStep {
        when (val e = phase) {
            is SwipePhase.Reveal -> return revealing(e, env)
            is SwipePhase.Entering -> return entering(e, env)
            else -> {}
        }
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
            commit != null && commit.reveal && commit.sourceClosed -> {
                val tab = p.gesture.neighbours[p.slide.dir].tab
                SwipeStep(copy(phase = SwipePhase.Reveal(p.gesture, commit.token, tab, env.now)))
            }
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

        /** How long a reveal waits for the WebView's answer before the veil fades anyway. */
        const val REVEAL_CAP_NANOS = 400_000_000L

        /** The veil's fade over the revealed page, in seconds of animation time. */
        const val FADE_SECONDS = 0.12f
    }
}
