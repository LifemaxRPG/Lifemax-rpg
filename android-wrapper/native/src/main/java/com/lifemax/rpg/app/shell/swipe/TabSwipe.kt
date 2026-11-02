package com.lifemax.rpg.app.shell.swipe

import android.animation.ValueAnimator
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.core.view.OneShotPreDrawListener
import com.lifemax.rpg.app.shell.ChromeHost
import com.lifemax.rpg.app.shell.TabTiming
import org.json.JSONObject

/**
 * The tab swipe on the main thread (CONTRACT A34), a sibling of ChromeHost. It drives the pure
 * [SwipeModel] with the page's side (the plugins' open, close and remove, a page that starts
 * loading, each drawn bar scene, the page's answer) and with the screens' gestures, and it is the
 * only writer of the registered screens' translationX, visibility and accessibility, and of the
 * veil. A swipe moves pixels, never ownership: only the page's open and close change which screen
 * is open, and they always win.
 * - The page's side is drawn at once: a plugin's open returns with its screen in place.
 * - A gesture is stepped at its event's time and drawn in a Choreographer animation callback, which
 *   runs after input in the same frame: the screen moves in the frame that read the finger. Frames
 *   go on while the model [needs them][SwipeModel.needsFrame].
 * - A screen going from GONE to parked needs a layout, so it is parked at an idle moment, not in a
 *   frame that moves. At each idle moment at rest the Views are checked against [restScene]; a
 *   difference (some other writer) is repaired and recorded.
 * - The watchdog ([SwipeModel.deadline]) and a cold destination's first draw
 *   ([SwipeModel.awaitingDraw]) come back in as inputs.
 * - A page that starts loading ([pageStarted]) is forgotten, and the veil goes with it.
 * - Phase 2: while a reveal needs the shared bars it holds them (ChromeHost.hold), and it asks the
 *   WebView (`postVisualStateCallback`) when the page under the veil has drawn.
 */
internal object TabSwipe {
    private class Screen(
        val view: TabScreenView,
        val send: (SwipeRequest) -> Unit,
        val onRest: () -> Unit,
    ) {
        /** Moving with no finger on it: a touch that starts on it is swallowed. */
        var shielded = false

        /** The visibility last placed, for the rest rule. */
        var placed: PaneVisibility? = null
    }

    private var model = SwipeModel()
    private val screens = LinkedHashMap<String, Screen>()

    /** android.R.id.content, while a screen is registered. */
    private var root: ViewGroup? = null
    private var veil: SwipeVeil? = null
    private val notes = ArrayDeque<String>()

    private var framePosted = false
    private val frame =
        Choreographer.FrameCallback { nanos ->
            framePosted = false
            step(SwipeInput.Tick, nanos)
        }
    private var idleQueued = false
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private var watchdogAt: Long? = null
    private val watchdog = Runnable {
        watchdogAt = null
        step(SwipeInput.Tick)
    }
    private var drawWatch: View? = null

    /** The reveal holding the shared bars, and the one the WebView was last asked about. */
    private var chromeHeld: String? = null
    private var webAsked: String? = null
    private var webAsks = 0L

    /** The views above the screens (the bars' frames) with a touch in progress. */
    private val touchedAbove = HashSet<Any>()

    /**
     * The latest notes, oldest first: aborts, cuts, refusals, the watchdog and repairs. A start on
     * A29's trace; each is also logged.
     */
    val trace: List<String>
        get() = notes.toList()

    /**
     * A plugin created [pane]'s screen as a child of android.R.id.content. [source] is its
     * `swipeSource`; [send] delivers the `swipe` event on its plugin; [onRest] is called when the
     * screen, not open, stops being drawn ([rests]).
     */
    fun register(
        pane: String,
        view: TabScreenView,
        source: Boolean,
        onRest: () -> Unit = {},
        send: (SwipeRequest) -> Unit,
    ) {
        root = view.parent as? ViewGroup ?: root
        screens[pane] = Screen(view, send, onRest)
        step(SwipeInput.Register(pane, source))
    }

    /**
     * The page opened [pane]: in place of VISIBLE. The awaited destination keeps its slide; one the
     * page reached by its own swipe ([enter], phase 2) slides in from that side.
     */
    fun opened(pane: String, enter: SwipeEnter? = null) = step(SwipeInput.Opened(pane, enter))

    /** The page closed [pane]: in place of GONE. A source sliding out stays until it is off. */
    fun closed(pane: String) = step(SwipeInput.Closed(pane))

    /** [pane]'s screen is being removed and disposed. */
    fun removed(pane: String) {
        screens.remove(pane)
        step(SwipeInput.Removed(pane))
        if (screens.isEmpty()) detach()
    }

    /**
     * A page started loading (NativeScreens calls this before its hosts drop their screens): forget
     * the page and take the veil away. The plugins then remove their own screens.
     */
    fun pageStarted() {
        step(SwipeInput.Drop)
        removeVeil()
    }

    /** The page's answer, `swipeSettle`. [reason] says why it refused. */
    fun settle(token: String, outcome: SettleOutcome, reason: String?) {
        if (outcome == SettleOutcome.Refused) record("refused $token: ${reason ?: "no reason"}")
        step(SwipeInput.Settle(token, outcome))
    }

    /** A bar scene that was drawn (ChromeHost): its `swipe` field and its sequence. */
    fun scene(swipe: JSONObject?, sequence: Int) =
        step(SwipeInput.Scene(SwipeState.parse(swipe, sequence)))

    /** The model's gates for a DOWN on [pane]. */
    fun gate(pane: String): SwipeGate? = model.startGate(pane)

    /** The feel the page asks for now. */
    val tune: SwipeTune
        get() = model.tune

    fun shields(pane: String): Boolean = screens[pane]?.shielded == true

    /** Whether a finger landing on [pane] now takes hold of it, a screen still sliding (A34). */
    fun catching(pane: String): Boolean = model.catching(env(System.nanoTime()))?.pane == pane

    /**
     * Where a touch that lands on [pane], a screen that is sliding, should go instead: the screen a
     * finger may catch, when that is another one (a commit's destination slides in beside the
     * source, which may be what the finger lands on). Null when [pane] keeps the touch itself.
     */
    fun catchHolder(pane: String): TabScreenView? {
        val holder = model.catching(env(System.nanoTime()))?.pane
        return if (holder == null || holder == pane) null else screens[holder]?.view
    }

    /** The label the page gives [pane]'s tab (its bar button), for the screen's pane title. */
    fun label(pane: String): String? =
        model.state?.tabs?.firstOrNull { it.view == pane }?.label?.takeIf { it.isNotBlank() }

    fun isOpen(pane: String): Boolean = pane in model.open

    /** No swipe or hand-over is in flight: the moment to compose a screen ahead ([prime]). */
    fun idle(): Boolean = model.phase == SwipePhase.Idle

    /** A screen's gesture input at [now] (event time, ns): drawn in this frame's animation pass. */
    fun gesture(input: SwipeInput, now: Long) = step(input, now, drawNow = false)

    /**
     * A touch that lands off the swipe's own screen: on a screen or the veil moving with no finger
     * on it, or on the shared bars. While a finger is on the source it is a second finger, wherever
     * it lands: before the claim the swipe lets the gesture go, after it the drag springs back.
     * Returns whether it cut a drag short; the caller then swallows the rest of its touch, so
     * nothing under it (a bar's tab) acts mid-swipe.
     */
    fun stray(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_DOWN) return false
        val input =
            when (model.phase) {
                is SwipePhase.Armed -> SwipeInput.Disarm
                is SwipePhase.Dragging -> SwipeInput.DragCancel
                else -> return false
            }
        gesture(input, event.eventTime * NANOS_PER_MILLI)
        return input == SwipeInput.DragCancel
    }

    /** [view], above the screens (the shared bars), has a touch in progress, or no longer. */
    fun touched(view: Any, touching: Boolean) {
        if (touching) touchedAbove += view else touchedAbove -= view
    }

    /**
     * The fingers down at a screen's DOWN: its event's [own], and one for each view above the
     * screens with a touch in progress, which the screen's events never count (android.R.id.content
     * splits touches). So no swipe starts beside a finger resting on a bar.
     */
    fun fingers(own: Int): Int = own + touchedAbove.size

    private fun step(input: SwipeInput, now: Long = System.nanoTime(), drawNow: Boolean = true) {
        val env = env(now)
        val before = model.phase.gesture
        val (next, effects) = model.on(input, env)
        model = next
        val source = (next.phase.gesture ?: before)?.source
        effects.forEach { run(it, source) }
        screens.values.forEach { if (!next.tracking(it.view.pane)) it.view.yieldGesture() }
        holdChrome(next.holdsChrome)
        if (drawNow) draw(env) else postFrame()
        schedule(env)
    }

    /**
     * The shared bars stay while a reveal needs them (phase 2); released when it no longer does.
     */
    private fun holdChrome(token: String?) {
        if (token == chromeHeld) return
        chromeHeld?.let(ChromeHost::unhold)
        chromeHeld = token
        token?.let(ChromeHost::hold)
    }

    /**
     * Asks the WebView to say when it has drawn the page as it is now, the page the veil reveals.
     * Without a WebView the reveal's cap fades the veil anyway.
     */
    private fun askWeb(token: String) {
        val web = root?.let(::findWeb) ?: return
        web.postVisualStateCallback(
            ++webAsks,
            object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) {
                    if (webAsked == token) step(SwipeInput.WebDrawn(token))
                }
            },
        )
    }

    /** The WebView under the screens (in android.R.id.content's first child). */
    private fun findWeb(view: View): WebView? =
        when (view) {
            is WebView -> view
            is TabScreenView,
            is SwipeVeil -> null
            is ViewGroup ->
                (0 until view.childCount).firstNotNullOfOrNull { findWeb(view.getChildAt(it)) }
            else -> null
        }

    private fun env(now: Long): SwipeEnv {
        val root = root ?: return SwipeEnv(now, 0, 1f)
        val density = root.resources.displayMetrics.density
        return SwipeEnv(now, root.width, density, ValueAnimator.getDurationScale())
    }

    private fun run(effect: SwipeEffect, source: String?) {
        when (effect) {
            is SwipeEffect.Send -> {
                TabTiming.swiped(effect.request.to)
                screens[effect.request.from]?.send?.invoke(effect.request)
            }
            is SwipeEffect.Haptic ->
                source?.let(screens::get)?.let { SwipeHaptics.play(it.view, effect) }
            is SwipeEffect.Trace -> record(effect.note)
        }
    }

    private fun draw(env: SwipeEnv) {
        val scene = model.scene(env)
        for ((pane, screen) in screens) scene.panes[pane]?.let {
            screen.place(it, park = false, open = pane in model.open)
        }
        showVeil(scene.veil)
        if (model.phase == SwipePhase.Idle) idleLater()
        if (model.needsFrame(env)) postFrame()
    }

    /**
     * Puts the screen where [stage] says. From GONE to parked needs a layout, which waits for an
     * idle moment unless [park].
     */
    private fun Screen.place(stage: PaneStage, park: Boolean, open: Boolean) {
        val visibility = stage.visibility.flag
        view.translationX = stage.tx.toFloat()
        if (park || view.visibility != View.GONE || visibility != View.INVISIBLE)
            view.visibility = visibility
        view.importantForAccessibility =
            if (stage.a11yHidden) View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            else View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        shielded = stage.shielded
        val before = placed
        placed = stage.visibility
        if (rests(before, stage.visibility, open)) onRest()
    }

    /** What differs from [stage], apart from a park still waiting for an idle moment. */
    private fun Screen.mismatch(stage: PaneStage): String? {
        val visibility = stage.visibility.flag
        val parking = view.visibility == View.GONE && visibility == View.INVISIBLE
        val hidden =
            view.importantForAccessibility == View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        return listOfNotNull(
                "tx ${view.translationX} for ${stage.tx}"
                    .takeIf { view.translationX != stage.tx.toFloat() },
                "visibility ${view.visibility} for $visibility"
                    .takeIf { view.visibility != visibility && !parking },
                "accessibility".takeIf { hidden != stage.a11yHidden },
            )
            .joinToString()
            .ifEmpty { null }
    }

    private fun showVeil(stage: VeilStage?) {
        if (stage == null) {
            veil?.visibility = View.GONE
            return
        }
        val veil = veil() ?: return
        veil.show(stage.tab, model.state?.colors)
        veil.translationX = stage.tx.toFloat()
        veil.alpha = stage.alpha
        veil.visibility = View.VISIBLE
    }

    /** The veil, made on first use: index 1, above the WebView and below every screen and bar. */
    private fun veil(): SwipeVeil? =
        veil
            ?: root?.let { root ->
                SwipeVeil(root.context, ::stray).also {
                    it.visibility = View.GONE
                    root.addView(it, minOf(1, root.childCount), FrameLayout.LayoutParams(-1, -1))
                    veil = it
                }
            }

    private fun removeVeil() {
        veil?.let { (it.parent as? ViewGroup)?.removeView(it) }
        veil = null
    }

    private fun postFrame() {
        if (framePosted || root == null) return
        framePosted = true
        Choreographer.getInstance().postFrameCallback(frame)
    }

    private fun idleLater() {
        if (idleQueued || root == null) return
        idleQueued = true
        Looper.myQueue().addIdleHandler {
            idleQueued = false
            atIdle()
            false
        }
    }

    /**
     * An idle moment: the veil is made once the page allows a swipe, and at rest the screens are
     * checked against the rest scene, repaired where they differ, and parked.
     */
    private fun atIdle() {
        if (root == null) return
        if (veil == null && model.state?.on == true) veil()
        if (model.phase != SwipePhase.Idle) return
        val rest = model.restScene(env(System.nanoTime()))
        for ((pane, screen) in screens) {
            val stage = rest[pane] ?: continue
            screen.mismatch(stage)?.let { record("repair $pane: $it") }
            screen.place(stage, park = true, open = pane in model.open)
        }
    }

    /**
     * The watchdog (while a commit waits for the page, a reveal for the WebView, an entering screen
     * for its first draw), the WebView's answer for a reveal, and a cold destination's first draw.
     */
    private fun schedule(env: SwipeEnv) {
        val web = model.awaitingWeb
        if (web != webAsked) {
            webAsked = web
            web?.let(::askWeb)
        }
        val deadline = model.deadline
        if (deadline != watchdogAt) {
            if (watchdogAt != null) handler.removeCallbacks(watchdog)
            watchdogAt = deadline
            if (deadline != null)
                handler.postDelayed(
                    watchdog,
                    (deadline - env.now).coerceAtLeast(0) / NANOS_PER_MILLI + 1,
                )
        }
        val pane = model.awaitingDraw ?: return
        val view = screens[pane]?.view ?: return
        if (view === drawWatch) return
        drawWatch = view
        OneShotPreDrawListener.add(view) {
            if (drawWatch === view) drawWatch = null
            step(SwipeInput.Drawn(pane))
        }
    }

    /** No screen is left: nothing to draw on, and nothing to wait for. */
    private fun detach() {
        removeVeil()
        root = null
        if (framePosted) Choreographer.getInstance().removeFrameCallback(frame)
        framePosted = false
        if (watchdogAt != null) handler.removeCallbacks(watchdog)
        watchdogAt = null
        drawWatch = null
        webAsked = null
        holdChrome(null)
    }

    private fun record(note: String) {
        Log.i(TAG, note)
        notes.addLast("${SystemClock.uptimeMillis()} $note")
        while (notes.size > TRACE_SIZE) notes.removeFirst()
    }

    private val PaneVisibility.flag: Int
        get() =
            when (this) {
                PaneVisibility.Shown -> View.VISIBLE
                PaneVisibility.Parked -> View.INVISIBLE
                PaneVisibility.Gone -> View.GONE
            }

    private const val TAG = "TabSwipe"
    private const val TRACE_SIZE = 40
    private const val NANOS_PER_MILLI = 1_000_000L
}
