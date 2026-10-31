package com.lifemax.rpg.app.shell.swipe

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign

/**
 * Why a DOWN may not start a swipe (plan §4, gates at DOWN). The first failing gate is reported.
 */
enum class SwipeGate {
    /** No accepted scene with `swipe.v == 1` and `swipe.on`. */
    Off,
    /** This screen is not the only open screen (closed, or the make-before-break overlap). */
    NotOwner,
    /** This screen may not start a swipe (its plugin's `swipeSource` is false). */
    NotSource,
    /** The state is about another view, or the active tab does not resolve to this screen. */
    StaleView,
    /** A swipe is still running (TabSwipe is not Idle). */
    Busy,
    /** Not exactly one pointer down. */
    Pointers,
    /** Not a finger or a stylus. */
    Tool,
    /** The keyboard is showing. */
    Ime,
    /** TalkBack's touch exploration is on. */
    TouchExploration,
    /** A popup or dialog window has focus. */
    NoFocus,
    /** Inside an edge zone, where the system Back gesture lives. */
    Edge,
}

enum class ToolKind {
    Finger,
    Stylus,
    Mouse,
    Other,
}

/**
 * What the screen's View knows at a DOWN. Coordinates are raw (screen) pixels: the screen itself
 * moves during a swipe, so local coordinates would feed its own motion back into the gesture.
 */
data class TouchFacts(
    /** The root view's width, the span of raw x the edge zones are measured in. */
    val widthPx: Int,
    val density: Float,
    /** `ViewConfiguration.scaledTouchSlop`: the slop Compose's own scrollers claim at. */
    val touchSlopPx: Float,
    val pointers: Int = 1,
    val tool: ToolKind = ToolKind.Finger,
    val imeVisible: Boolean = false,
    val touchExploration: Boolean = false,
    val windowFocused: Boolean = true,
    /** `systemGestures()` insets of the root window, the Back gesture's zones. */
    val gestureLeftPx: Int = 0,
    val gestureRightPx: Int = 0,
)

/** The arbiter after one event: what the model hears, and whether the view intercepts on it. */
data class ArbiterStep(
    val arbiter: SwipeArbiter,
    /** Feed this to [SwipeModel.on] when it is not null. */
    val input: SwipeInput? = null,
    /** Return this from `onInterceptTouchEvent` for the same event. */
    val intercept: Boolean = false,
)

/**
 * Decides, per gesture on one screen's View, whether the gesture becomes a tab swipe (plan §4), and
 * turns it into the [SwipeInput]s the model hears. Pure and immutable: the View keeps one per
 * screen, feeds it every event from `dispatchTouchEvent` before passing the event on, and keeps the
 * [ArbiterStep.arbiter] it gets back.
 * - DOWN ([down]): the model's gates, then the platform's, must all hold; otherwise the gesture is
 *   [Phase.Passive] to the end.
 * - Lock, with dx, dy measured from the DOWN: Dead if |dy| ≥ slop and |dx| < ratio·|dy|; a
 *   Candidate if |dx| ≥ slop and |dx| ≥ ratio·|dy|. The view does not intercept on the candidate
 *   event, so a child that crosses its own slop on that same event consumes it and sends disallow
 *   ([childClaimed]). On the next MOVE, if the ratio still holds in the same direction, the view
 *   intercepts ([ArbiterStep.intercept]) and the children get ACTION_CANCEL. The anchor is the DOWN
 *   plus the slop toward the drag, so the screen starts at 0 under the finger.
 * - After the claim the view consumes every event ([consumes]). A second pointer cancels, and the
 *   rest of the gesture is consumed until every pointer is up ([Phase.Draining]).
 *
 * Coordinates are raw (screen) pixels. After any model input, if the model no longer
 * [tracks][SwipeModel.tracking] this screen's gesture (a page open, `on:false`), call
 * [yieldGesture].
 */
data class SwipeArbiter(
    /** The screen this arbiter belongs to: its view id, as in [SwipeInput.Arm]. */
    val pane: String,
    val phase: Phase = Phase.Idle,
) {
    sealed interface Phase {
        /** Between gestures. */
        data object Idle : Phase

        /** Ignoring the rest of this gesture; [gate] says why when a gate failed at the DOWN. */
        data class Passive(val gate: SwipeGate? = null) : Phase

        /** Armed, waiting for the direction. */
        data class Pending(val down: Point, val lock: Lock) : Phase

        /** The candidate event has passed; the next MOVE claims. [sign] is dx's. */
        data class Candidate(val down: Point, val lock: Lock, val sign: Float) : Phase

        /** Dragging. [dir] is +1 toward the next tab (the finger moves left). */
        data class Claimed(val anchorX: Float, val dir: Int) : Phase

        /** Cancelled after the claim; consuming until every pointer is up. */
        data object Draining : Phase
    }

    data class Point(val x: Float, val y: Float)

    data class Lock(val slopPx: Float, val ratio: Float)

    /** Whether the view consumes the event itself (`onTouchEvent` returns true for it). */
    val consumes: Boolean
        get() = phase is Phase.Claimed || phase == Phase.Draining

    /**
     * ACTION_DOWN at raw ([x], [y]). [modelGate] is [SwipeModel.startGate] for this pane and [tune]
     * is [SwipeModel.tune]; the platform gates come from [facts].
     */
    fun down(
        x: Float,
        y: Float,
        facts: TouchFacts,
        modelGate: SwipeGate?,
        tune: SwipeTune,
    ): ArbiterStep {
        // a gesture that never ended (no UP or CANCEL reached us) ends here, and this one is
        // skipped
        when (phase) {
            is Phase.Claimed -> return to(Phase.Passive(), SwipeInput.DragCancel)
            is Phase.Pending,
            is Phase.Candidate -> return to(Phase.Passive(), SwipeInput.Disarm)
            else -> Unit
        }
        val gate = modelGate ?: platformGate(x, facts, tune)
        if (gate != null) return to(Phase.Passive(gate))
        val lock = Lock(facts.touchSlopPx * tune.slopFactor, tune.lockRatio)
        return to(Phase.Pending(Point(x, y), lock), SwipeInput.Arm(pane))
    }

    /** ACTION_MOVE, with the primary pointer at raw ([x], [y]). */
    fun move(x: Float, y: Float): ArbiterStep =
        when (val p = phase) {
            is Phase.Pending ->
                when (val c = classify(p.down, p.lock, x, y)) {
                    Dead -> abandon()
                    is Horizontal -> to(Phase.Candidate(p.down, p.lock, c.sign))
                    Undecided -> to(p)
                }
            is Phase.Candidate ->
                when (val c = classify(p.down, p.lock, x, y)) {
                    Dead -> abandon()
                    is Horizontal ->
                        if (c.sign == p.sign) claim(p, x) else to(p.copy(sign = c.sign))
                    Undecided -> to(Phase.Pending(p.down, p.lock))
                }
            is Phase.Claimed -> to(p, SwipeInput.DragMove(x))
            else -> to(p)
        }

    /** ACTION_POINTER_DOWN. */
    fun pointerDown(): ArbiterStep =
        when (phase) {
            is Phase.Pending,
            is Phase.Candidate -> abandon()
            is Phase.Claimed -> to(Phase.Draining, SwipeInput.DragCancel)
            else -> to(phase)
        }

    /**
     * ACTION_UP: the last pointer lifted. [velocityX] is VelocityTracker's raw x velocity in px/s
     * over the last 100 ms; only a claimed drag uses it.
     */
    fun up(velocityX: Float): ArbiterStep =
        when (phase) {
            is Phase.Claimed -> to(Phase.Idle, SwipeInput.DragEnd(velocityX))
            is Phase.Pending,
            is Phase.Candidate -> to(Phase.Idle, SwipeInput.Disarm)
            else -> to(Phase.Idle)
        }

    /** ACTION_CANCEL, for example the system Back gesture taking the pointers. */
    fun cancel(): ArbiterStep =
        when (phase) {
            is Phase.Claimed -> to(Phase.Idle, SwipeInput.DragCancel)
            is Phase.Pending,
            is Phase.Candidate -> to(Phase.Idle, SwipeInput.Disarm)
            else -> to(Phase.Idle)
        }

    /**
     * A child called `requestDisallowInterceptTouchEvent(true)`: a scroller consumed movement or a
     * `noTabSwipe` region took the DOWN. Before the claim the gesture is the child's.
     */
    fun childClaimed(): ArbiterStep =
        when (phase) {
            is Phase.Pending,
            is Phase.Candidate -> abandon()
            else -> to(phase)
        }

    /** The model is no longer tracking this gesture: let it go, consuming it if it was claimed. */
    fun yieldGesture(): SwipeArbiter =
        when (phase) {
            is Phase.Pending,
            is Phase.Candidate -> copy(phase = Phase.Passive())
            is Phase.Claimed -> copy(phase = Phase.Draining)
            else -> this
        }

    private fun to(next: Phase, input: SwipeInput? = null, intercept: Boolean = false) =
        ArbiterStep(if (next == phase) this else copy(phase = next), input, intercept)

    private fun abandon() = to(Phase.Passive(), SwipeInput.Disarm)

    private fun claim(p: Phase.Candidate, x: Float): ArbiterStep {
        val anchorX = p.down.x + p.sign * p.lock.slopPx
        val dir = if (p.sign < 0f) 1 else -1
        return to(Phase.Claimed(anchorX, dir), SwipeInput.DragStart(anchorX, dir, x), true)
    }

    private sealed interface Direction

    private data object Dead : Direction

    private data object Undecided : Direction

    private data class Horizontal(val sign: Float) : Direction

    private fun classify(down: Point, lock: Lock, x: Float, y: Float): Direction {
        val dx = x - down.x
        val dy = abs(y - down.y)
        return when {
            abs(dx) >= lock.slopPx && abs(dx) >= lock.ratio * dy -> Horizontal(sign(dx))
            dy >= lock.slopPx -> Dead
            else -> Undecided
        }
    }

    companion object {
        /** The first platform gate that fails at a DOWN at raw [x], or null when all hold. */
        fun platformGate(x: Float, facts: TouchFacts, tune: SwipeTune): SwipeGate? {
            val floor = tune.edgeDp * facts.density
            val left = max(facts.gestureLeftPx.toFloat(), floor)
            val right = facts.widthPx - max(facts.gestureRightPx.toFloat(), floor)
            return when {
                facts.pointers != 1 -> SwipeGate.Pointers
                facts.tool != ToolKind.Finger && facts.tool != ToolKind.Stylus -> SwipeGate.Tool
                facts.imeVisible -> SwipeGate.Ime
                facts.touchExploration -> SwipeGate.TouchExploration
                !facts.windowFocused -> SwipeGate.NoFocus
                x < left || x > right -> SwipeGate.Edge
                else -> null
            }
        }
    }
}
