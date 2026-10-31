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
 * destination has opened (and, opened cold, drawn) and the source has closed.
 */
data class Commit(
    val token: String,
    val dest: String,
    val at: Long,
    val drawn: Boolean,
    val opened: Boolean = false,
    val sourceClosed: Boolean = false,
) {
    val complete: Boolean
        get() = opened && drawn && sourceClosed
}

/** Idle → Armed → Dragging → Settling(commit|cancel) → AwaitPage → Idle. */
sealed interface SwipePhase {
    data object Idle : SwipePhase

    /** Every phase but Idle belongs to one gesture. */
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
}

val SwipePhase.gesture: Gesture?
    get() = (this as? SwipePhase.Active)?.gesture

val SwipePhase.commit: Commit?
    get() = (this as? SwipePhase.Sliding)?.commit
