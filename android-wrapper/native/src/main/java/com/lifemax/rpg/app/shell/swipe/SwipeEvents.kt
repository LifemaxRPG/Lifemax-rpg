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

/**
 * The page's answer to a swipe (`swipeSettle`): a native screen will draw the tab, the page will
 * draw it (phase 2: the veil reveals it), or no. Anything else is a refusal.
 */
enum class SettleOutcome {
    Native,
    Web,
    Refused;

    companion object {
        fun of(wire: String?): SettleOutcome =
            when (wire) {
                "native" -> Native
                "web" -> Web
                else -> Refused
            }
    }
}

/** When the swipe plays a haptic (ADR 0011, the haptics table). */
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
            // this build reveals a page the web draws (phase 2), so the page may answer "web"
            .put("reveal", true)
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

    /**
     * The page's `open` for [pane]. Always wins over a swipe, except the awaited destination's.
     * [enter] is set when the page went there by its own (web) swipe: the screen slides in.
     */
    data class Opened(val pane: String, val enter: SwipeEnter? = null) : SwipeInput

    /** The page's `close` for [pane]. The source's close is deferred until it is off screen. */
    data class Closed(val pane: String) : SwipeInput

    /** The pane's screen was removed and disposed. */
    data class Removed(val pane: String) : SwipeInput

    /**
     * The screen of [SwipeModel.awaitingDraw] has passed its first OnPreDraw since it was created:
     * the veil may hand over to it.
     */
    data class Drawn(val pane: String) : SwipeInput

    /**
     * The WebView has drawn the page as it was when [SwipeModel.awaitingWeb] asked
     * (`postVisualStateCallback`): the veil over it may fade.
     */
    data class WebDrawn(val token: String) : SwipeInput

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

    /**
     * A DOWN on the screen [SwipeModel.catching] names, while it still moves with no finger on it:
     * the finger takes hold of it where it stands (chained swipes) at raw [x].
     */
    data class Catch(val pane: String, val x: Float) : SwipeInput

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

/**
 * The page's own swipe ended on a native tab (phase 2, web → native): the screen slides in from
 * [dir]'s side (+1, the next tab, comes in from the right) at the finger's [velocityDp] (dp/s).
 */
data class SwipeEnter(val dir: Int, val velocityDp: Float) {
    companion object {
        /** The open call's `enter` ("next" or "prev") and `velocity`; null for a plain open. */
        fun of(enter: String?, velocity: Double?): SwipeEnter? {
            val dir =
                when (enter) {
                    "next" -> 1
                    "prev" -> -1
                    else -> return null
                }
            val v = velocity?.takeIf { it.isFinite() }?.toFloat() ?: 0f
            return SwipeEnter(dir, v.coerceIn(0f, MAX_VELOCITY_DP))
        }

        /** Faster than this is a misreading on the page's side; the spring caps it anyway. */
        const val MAX_VELOCITY_DP = 20_000f
    }
}
