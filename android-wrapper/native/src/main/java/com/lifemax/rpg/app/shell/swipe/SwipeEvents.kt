package com.lifemax.rpg.app.shell.swipe

import kotlin.math.max
import org.json.JSONObject

/**
 * The clock and the dimensions an input happens at. TabSwipe builds one for every call: the time
 * from the event or the Choreographer frame, the size of android.R.id.content, the density, and
 * `ValueAnimator.getDurationScale()`.
 */
data class SwipeEnv(
    /** Monotonic nanoseconds: a frame's `frameTimeNanos`, `System.nanoTime()`, or eventTime·10⁶. */
    val now: Long,
    val widthPx: Int,
    val density: Float,
    val animatorScale: Float = 1f,
) {
    /** W in px, at least 1. */
    val width: Int
        get() = max(1, widthPx)

    /** Half a pixel as a fraction of W: closer than this draws the same. */
    val epsilon: Float
        get() = 0.5f / width
}

/** The page's answer to a swipe (`swipeSettle`). Phase 1 knows two; anything else is a refusal. */
enum class SettleOutcome {
    Native,
    Refused;

    companion object {
        fun of(wire: String?): SettleOutcome = if (wire == "native") Native else Refused
    }
}

/** When the swipe plays a haptic (plan §2, haptics table). */
enum class HapticMoment {
    /** Forward over the catch. */
    Latch,
    /** Back under the catch. */
    Unlatch,
    /** A fling commit that was not latched, at the release. */
    Fling,
    /** The page refused the commit. */
    Refused,
}

/**
 * The `swipe` event the owner's plugin sends when a release commits. [to] is the bar key; Food's
 * plugin adds its session. Stamp `TabTiming.tap("swipe:" + to)` at the same moment.
 */
data class SwipeRequest(
    val token: String,
    val from: String,
    val to: String,
    val dir: Int,
    val sequence: Int,
    val via: Via,
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("token", token)
            .put("from", from)
            .put("to", to)
            .put("dir", dir)
            .put("sequence", sequence)
            .put("via", via.wire)
}

/** Something TabSwipe does once, outside the scene: play, send, record. */
sealed interface SwipeEffect {
    /** Already gated on the page's haptics and the `haptic` tune key. */
    data class Haptic(val moment: HapticMoment, val level: HapticLevel, val style: HapticStyle) :
        SwipeEffect

    data class Send(val request: SwipeRequest) : SwipeEffect

    /** A line for the trace: an abort, a cut, the watchdog. */
    data class Trace(val note: String) : SwipeEffect
}

/**
 * Everything the model hears. The plugin hooks, NativeScreens, the scene and the answer come from
 * the page's side; the gesture inputs come from each screen's [SwipeArbiter].
 */
sealed interface SwipeInput {
    /** A pane's screen was created, so it is composed. [source] is the plugin's `swipeSource`. */
    data class Register(val pane: String, val source: Boolean = true) : SwipeInput

    /** The page's `open` for [pane]. Always wins over a swipe, except the awaited destination's. */
    data class Opened(val pane: String) : SwipeInput

    /** The page's `close` for [pane]. The source's close is deferred until it is off screen. */
    data class Closed(val pane: String) : SwipeInput

    /** The pane's screen was removed and disposed. */
    data class Removed(val pane: String) : SwipeInput

    /**
     * The screen of [SwipeModel.awaitingDraw] has passed its first OnPreDraw since it was created:
     * the veil may hand over to it.
     */
    data class Drawn(val pane: String) : SwipeInput

    /** A page started loading (NativeScreens): forget the page, keep the screens' registry. */
    data object Drop : SwipeInput

    /** A drawn bar scene's `swipe` field, parsed with [SwipeState.parse]; null when it has none. */
    data class Scene(val state: SwipeState?) : SwipeInput

    /** The page's `swipeSettle`. A token that does not match the commit in flight is ignored. */
    data class Settle(val token: String, val outcome: SettleOutcome) : SwipeInput

    /** Every gate held at a DOWN on [pane]. */
    data class Arm(val pane: String) : SwipeInput

    /** The view claimed the drag: the screen stands at 0 with the finger at [anchorX]. */
    data class DragStart(val anchorX: Float, val dir: Int, val x: Float) : SwipeInput

    /** The finger is at raw [x] px. */
    data class DragMove(val x: Float) : SwipeInput

    /** The finger lifted, moving at [velocityX] raw px/s. */
    data class DragEnd(val velocityX: Float) : SwipeInput

    /** A system cancel or a second pointer after the claim: spring back. */
    data object DragCancel : SwipeInput

    /** The armed gesture went to a child, a scroll or a second finger before the claim. */
    data object Disarm : SwipeInput

    /** Time passed: a frame, or the [SwipeModel.deadline] timer. */
    data object Tick : SwipeInput
}
