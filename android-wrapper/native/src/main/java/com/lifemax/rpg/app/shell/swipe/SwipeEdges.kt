package com.lifemax.rpg.app.shell.swipe

/*
 * Phase 2 of the tab swipe, the edges (plans/TAB_SWIPE.md §5), as more of the pure [SwipeModel]:
 * - native → web: a commit into a tab the page draws ends in [SwipePhase.Reveal]: the veil, with
 *   the tab's icon and label, covers the screen until the WebView has drawn the page under it, then
 *   fades. The shared bars stay ([SwipeModel.holdsChrome]) until it has faded: they sit above the
 *   veil, which covers the WebView's own bars, so letting them go any earlier would flicker.
 * - web → native: the page's own swipe ends on a native tab and its open says so (`enter`): the
 *   screen slides in from that side ([SwipePhase.Entering]), from its first draw, with the commit
 *   spring.
 */

/**
 * After the page's open: a screen it opened at the end of its own swipe slides in, when the open
 * left nothing else moving (an open that aborted a swipe still slides), it is the only open screen
 * (a slide over another open screen would show two places at once), and the animator scale is not 0
 * (then the open is the switch, at once).
 */
internal fun SwipeStep.thenEnter(input: SwipeInput.Opened, env: SwipeEnv): SwipeStep {
    val enter = input.enter ?: return this
    val m = model
    val alone = m.open == listOf(input.pane) && input.pane in m.panes
    if (m.phase != SwipePhase.Idle || !alone || env.animatorScale <= 0f) return this
    val velocity = enter.velocityDp * env.density / env.width
    val entering = SwipePhase.Entering(input.pane, enter.dir, velocity, env.now)
    return SwipeStep(m.copy(phase = entering), effects)
}

/** An entering screen's first draw: its slide starts now, from its side, as fast as the finger. */
internal fun SwipeModel.entered(p: SwipePhase.Entering, pane: String, env: SwipeEnv): SwipeModel {
    if (pane != p.pane || p.spring != null) return this
    val spring =
        Spring1D.settle(
            0f,
            p.velocity,
            1f,
            tune.commitK,
            SwipeTune.MAX_STIFFNESS,
            env.now,
            env.animatorScale,
        )
    return copy(phase = p.copy(spring = spring))
}

/** The WebView has drawn the page under the veil: the fade starts. */
internal fun SwipeModel.webDrawn(token: String, env: SwipeEnv): SwipeStep {
    val p = phase as? SwipePhase.Reveal ?: return SwipeStep(this)
    if (p.token != token || p.fadeAt != null) return SwipeStep(this)
    return SwipeStep(copy(phase = p.copy(fadeAt = env.now)))
}

/** A reveal fades once the WebView answered or the cap passed, then rests. */
internal fun SwipeModel.revealing(p: SwipePhase.Reveal, env: SwipeEnv): SwipeStep {
    val cap = p.since + SwipeModel.REVEAL_CAP_NANOS
    val r = if (p.fadeAt == null && env.now >= cap) p.copy(fadeAt = cap) else p
    return when {
        r.faded(env) -> SwipeStep(copy(phase = SwipePhase.Idle))
        r !== p -> SwipeStep(copy(phase = r))
        else -> SwipeStep(this)
    }
}

/** An entering screen rests once its slide lands; one that never draws gives up at the watchdog. */
internal fun SwipeModel.entering(p: SwipePhase.Entering, env: SwipeEnv): SwipeStep =
    when {
        p.spring == null && env.now >= p.since + SwipeModel.WATCHDOG_NANOS ->
            SwipeStep(
                copy(phase = SwipePhase.Idle),
                listOf(SwipeEffect.Trace("watchdog: ${p.pane} never drew to slide in")),
            )
        p.landed(env) -> SwipeStep(copy(phase = SwipePhase.Idle))
        else -> SwipeStep(this)
    }
