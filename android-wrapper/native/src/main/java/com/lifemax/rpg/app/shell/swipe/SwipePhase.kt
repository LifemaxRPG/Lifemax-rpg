package com.lifemax.rpg.app.shell.swipe

/** A gesture's facts, frozen at the DOWN from the state of that moment. */
data class Gesture(
    val source: String,
    val neighbours: Neighbours,
    val sequence: Int,
    val motion: Boolean,
    val haptics: HapticLevel,
    val tune: SwipeTune,
) {
    /** Every pane this gesture may move or show. */
    val involved: Set<String>
        get() = setOfNotNull(source, neighbours.prev.tab?.view, neighbours.next.tab?.view)

    fun isWall(dir: Int): Boolean = neighbours[dir] is Neighbour.Wall

    /** The scale for this gesture's springs: 0 (instant) when the page's motion is off. */
    fun scale(env: SwipeEnv): Float = if (motion) env.animatorScale else 0f

    /**
     * A settle toward [dir] from [from] at [velocity]: to 1 for a [commit], else to 0, each with
     * its least stiffness ([Spring1D.settle] adapts it to the speed).
     */
    fun settle(dir: Int, from: Float, velocity: Float, commit: Boolean, env: SwipeEnv): Slide {
        val target = if (commit) 1f else 0f
        val stiffness = if (commit) tune.commitK else tune.cancelK
        val spring =
            Spring1D.settle(
                from,
                velocity,
                target,
                stiffness,
                SwipeTune.MAX_STIFFNESS,
                env.now,
                scale(env),
            )
        return Slide(dir, spring)
    }

    /** A settle that takes over from the finger: from where it holds the screen, as fast. */
    fun settle(drag: Drag, towardPx: Float, commit: Boolean, env: SwipeEnv): Slide =
        settle(drag.dir, drag.offset(env, this), drag.velocity(env, this, towardPx), commit, env)

    fun haptic(moment: HapticMoment): List<SwipeEffect> {
        val silent =
            haptics == HapticLevel.Off ||
                (tune.haptic == HapticStyle.Off && moment != HapticMoment.Refused)
        return if (silent) emptyList() else listOf(SwipeEffect.Haptic(moment, haptics, tune.haptic))
    }
}

/** The finger's part of a drag: the claim point, the side and the tension toward it. */
data class Drag(val anchorX: Float, val dir: Int, val x: Float, val tension: Tension) {
    /** Travel toward [dir] as a fraction of W; negative once the finger is back past the anchor. */
    fun u(env: SwipeEnv): Float = dir * (anchorX - x) / env.width

    /** o: where the drag holds the screen. */
    fun offset(env: SwipeEnv, gesture: Gesture): Float =
        tension.offset(u(env), env.now, gesture.tune, gesture.isWall(dir))

    /** do/dt for a finger moving at [towardPx] px/s toward [dir]'s side. */
    fun velocity(env: SwipeEnv, gesture: Gesture, towardPx: Float): Float =
        tension.velocity(u(env), towardPx / env.width, env.now, gesture.tune, gesture.isWall(dir))
}

/** A settle toward [dir]'s side: the spring carries o to 1 (commit) or 0 (cancel). */
data class Slide(val dir: Int, val spring: Spring1D) {
    /** At rest within half a pixel, or past the settle cap. */
    fun landed(env: SwipeEnv): Boolean =
        spring.isAtRest(env.now, env.epsilon) ||
            spring.time(env.now) >= SwipeTune.SETTLE_CAP_SECONDS

    fun offset(env: SwipeEnv): Float =
        if (landed(env)) spring.target else spring.position(env.now).coerceIn(0f, 1f)

    fun velocity(env: SwipeEnv): Float = if (landed(env)) 0f else spring.velocity(env.now)
}

/**
 * A commit in flight, sent as [token] at [at] toward the pane [dest]. It is complete once the
 * destination has opened (and, opened cold, drawn) and the source has closed. A [reveal] is a
 * commit the page draws (it answered `web`, or answered `native` and then closed the source before
 * any screen opened: the destination fell back to web): the veil takes the destination's place, the
 * shared bars are held, and once the source has closed and the slide has landed the veil reveals
 * the page ([SwipePhase.Reveal]).
 */
data class Commit(
    val token: String,
    val dest: String,
    val at: Long,
    val drawn: Boolean,
    val opened: Boolean = false,
    val sourceClosed: Boolean = false,
    val reveal: Boolean = false,
) {
    val complete: Boolean
        get() = if (reveal) sourceClosed else opened && drawn && sourceClosed
}

/**
 * Idle → Armed → Dragging → Settling(commit|cancel) → AwaitPage → Idle, with Reveal after a commit
 * the page draws, and Entering for a screen the page opened at the end of its own swipe.
 */
sealed interface SwipePhase {
    data object Idle : SwipePhase

    /**
     * Phase 2, web → native: the page's own swipe went to [pane], and its screen slides in from
     * [dir]'s side (+1 from the right) to 0, at [velocity] (fractions of W per second). The
     * [spring] starts at the screen's first draw after the open (null until then: it waits at
     * dir·W, so a screen composed cold does not lose its slide to the composing); [since] is the
     * open.
     */
    data class Entering(
        val pane: String,
        val dir: Int,
        val velocity: Float,
        val since: Long,
        val spring: Spring1D? = null,
    ) : SwipePhase {
        /** Where the screen stands, as a fraction of W from 0 toward its side. */
        fun offset(env: SwipeEnv): Float =
            if (spring == null) 1f else 1f - spring.position(env.now).coerceIn(0f, 1f)

        fun landed(env: SwipeEnv): Boolean =
            spring != null &&
                (spring.isAtRest(env.now, env.epsilon) ||
                    spring.time(env.now) >= SwipeTune.SETTLE_CAP_SECONDS)
    }

    /** Every phase but Idle and Entering belongs to one gesture. */
    sealed interface Active : SwipePhase {
        val gesture: Gesture
    }

    /** A DOWN passed every gate; the arbiter is waiting for the direction. */
    data class Armed(override val gesture: Gesture) : Active

    data class Dragging(override val gesture: Gesture, val drag: Drag) : Active

    /** The finger has let go: the screens follow [slide]. */
    sealed interface Sliding : Active {
        val slide: Slide
        val commit: Commit?
    }

    /** The spring is running; [commit] is null for a cancel. */
    data class Settling(
        override val gesture: Gesture,
        override val slide: Slide,
        override val commit: Commit?,
    ) : Sliding

    /** The commit has landed and waits for the page's hand-over. */
    data class AwaitPage(
        override val gesture: Gesture,
        override val slide: Slide,
        override val commit: Commit,
    ) : Sliding

    /**
     * A commit the page draws has landed and its source has gone: the veil, with [tab]'s icon and
     * label, covers the screen while the WebView draws the page ([SwipeModel.awaitingWeb]), then
     * fades from [fadeAt] (the WebView's answer, or [since] + [SwipeModel.REVEAL_CAP_NANOS]).
     */
    data class Reveal(
        override val gesture: Gesture,
        val token: String,
        val tab: SwipeTab?,
        val since: Long,
        val fadeAt: Long? = null,
    ) : Active {
        /** The veil's alpha: 1 until the fade, then down to 0 over [SwipeModel.FADE_SECONDS]. */
        fun alpha(env: SwipeEnv): Float {
            val from = fadeAt ?: return 1f
            val scale = gesture.scale(env)
            if (scale <= 0f) return 0f
            val t = (env.now - from).coerceAtLeast(0L) / 1e9f / scale
            return (1f - t / SwipeModel.FADE_SECONDS).coerceIn(0f, 1f)
        }

        fun faded(env: SwipeEnv): Boolean = fadeAt != null && alpha(env) <= 0f
    }
}

val SwipePhase.gesture: Gesture?
    get() = (this as? SwipePhase.Active)?.gesture

val SwipePhase.commit: Commit?
    get() = (this as? SwipePhase.Sliding)?.commit
