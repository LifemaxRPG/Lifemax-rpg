package com.lifemax.rpg.bridge.web

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.ViewCompat
import androidx.lifecycle.ViewModelStore
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.lifemax.rpg.app.shell.ChromeHost
import com.lifemax.rpg.app.shell.NativeScreens
import com.lifemax.rpg.app.shell.TabTiming
import com.lifemax.rpg.app.shell.audio.AudioDockHost
import com.lifemax.rpg.app.shell.swipe.SettleOutcome
import com.lifemax.rpg.app.shell.swipe.SwipeEnter
import com.lifemax.rpg.app.shell.swipe.TabScreenView
import com.lifemax.rpg.app.shell.swipe.TabSwipe
import org.json.JSONObject

/**
 * The native half of a tab (the web half is NativeTabKit). It owns what every native tab does the
 * same way: the handshake, opening, updating and closing a Compose screen over the page, the shared
 * top and bottom bars and the hand-over to the next tab. A tab says only what is its own: how to
 * take a projection from the page ([accept]) and what to draw ([Content]).
 *
 * Calls arrive in the order the page made them. Closing only hides the screen, which stays composed
 * so coming back is instant; a page that starts loading removes it ([NativeScreens]). The screen's
 * place and visibility are TabSwipe's, which also lets the owner swipe to the next native tab: on a
 * release that commits the plugin sends `swipe`, and the page answers with [swipeSettle] before it
 * navigates. Contract: A03, A07, A23, A24.
 */
abstract class ComposeTabPlugin : Plugin() {
    /** The page view this tab shows. Also the timing label and the view's tag. */
    protected abstract val tabId: String

    /** Capability names the page checks in the handshake. */
    protected abstract val capabilities: List<String>

    /** Whether a swipe may start on this tab's screen. It can always be a swipe's destination. */
    protected open val swipeSource = true

    /**
     * Decode and keep a projection from the page. [explicitOpen] binds a new page session. Throws
     * on malformed data.
     */
    protected abstract fun accept(snapshot: JSONObject, explicitOpen: Boolean)

    /**
     * The screen. [emit] sends an action to the page; the action "navigate" carries a view id
     * instead of an action id.
     */
    @Composable
    protected abstract fun Content(
        topInsetDp: Float,
        bottomInsetDp: Float,
        emit: (action: String, id: String) -> Unit,
    )

    /**
     * The page's answer to a [sendCommand], delivered with the update that carries the fresh
     * projection, and delivered even when that projection is rejected: the answer still says what
     * the page did. It reaches only a showing screen: one that closed while the page was answering
     * never gets it (a hidden screen takes no updates, and the page sends none to a closed one), so
     * a tab that waits for an answer stops waiting when it opens again.
     */
    protected open fun onReply(reply: JSONObject) {}

    /**
     * The screen stopped being drawn while it is not open (TabSwipe's rest rule), or it was
     * removed. A tab whose data is private drops it here (A35); a closed screen still sliding out
     * has not rested yet.
     */
    protected open fun onRested() {}

    /**
     * The page closed the screen (the owner left the tab): it is still on its way out, and nothing
     * else runs for it from now on (A24). A tab with words not yet sent flushes them here.
     */
    protected open fun onHidden() {}

    /**
     * Ask the page to run a command (a preview, a save) without closing the screen. The page
     * answers with an update whose reply names the same [requestId]. Commands run on the page one
     * at a time, in the order sent.
     */
    protected fun sendCommand(command: String, payload: JSONObject, requestId: String) {
        notifyListeners(
            "command",
            JSObject().put("command", command).put("payload", payload).put("requestId", requestId),
        )
    }

    /** Survives the view being rebuilt; cleared with the plugin. */
    protected val store = ViewModelStore()

    /** Whether the screen is showing. A hidden screen runs nothing continuous (A24). */
    protected var visible by mutableStateOf(false)
        private set

    private var view: TabScreenView? = null
    private var topInsetDp by mutableFloatStateOf(64f)
    private var bottomInsetDp by mutableFloatStateOf(72f)

    @PluginMethod
    fun handshake(call: PluginCall) {
        call.resolve(
            JSObject()
                .put("protocol", 1)
                .put("capabilities", JSArray(capabilities + SWIPE + SWIPE_EDGES + PRIME))
        )
    }

    /**
     * Composes this tab's screen ahead of its first open, so a swipe to it meets a screen that is
     * ready, not one that composes during the slide (`shell.prime.v1`, CONTRACT A24). The page
     * calls it at idle for a neighbour of the tab it is on, one at a time, with the projection an
     * open would send. The screen is created exactly as an open creates it (the same accept and
     * createView) and then hidden as any closed screen is: not open, not visible, no bars, no
     * focus, no updates. Answers `{primed, javaKb, nativeKb, ms}` once the screen has composed
     * ([PrimeCost]); `primed:false` with a reason when it did nothing (already composed, a swipe or
     * hand-over in progress). A rejected projection removes the screen again and rejects.
     */
    @PluginMethod
    fun prime(call: PluginCall) {
        val data = readSnapshot(call) ?: return
        activity.runOnUiThread {
            when {
                view != null ->
                    call.resolve(JSObject().put("primed", false).put("reason", "composed"))
                !TabSwipe.idle() ->
                    call.resolve(JSObject().put("primed", false).put("reason", "busy"))
                else ->
                    try {
                        val cost = PrimeCost.start()
                        accept(data, true)
                        val screen = createView()
                        // the window's, not the screen's: the answer comes even if the screen is
                        // gone
                        activity.window.decorView.postDelayed(
                            {
                                call.resolve(
                                    cost.read().put("primed", view === screen).put("reason", "")
                                )
                            },
                            PrimeCost.SETTLE_MS,
                        )
                    } catch (e: Exception) {
                        remove()
                        call.reject("$tabId could not be primed", e)
                    }
            }
        }
    }

    @PluginMethod
    fun open(call: PluginCall) {
        val data = readSnapshot(call) ?: return
        activity.runOnUiThread {
            TabTiming.opened()
            try {
                accept(data, true)
                val screen = view ?: createView()
                // TalkBack announces the screen by name as it appears: the bar's label for the tab
                ViewCompat.setAccessibilityPaneTitle(screen, paneTitle(call.getString("title")))
                TabSwipe.opened(
                    tabId,
                    SwipeEnter.of(call.getString("enter"), call.getDouble("velocity")),
                )
                screen.bringToFront()
                visible = true
                showChrome(call.getObject("navScene"), opening = true)
                TabTiming.attached(screen, tabId)
                call.resolve()
            } catch (e: Exception) {
                remove()
                call.reject("$tabId could not open", e)
            }
        }
    }

    @PluginMethod
    fun update(call: PluginCall) {
        val data = readSnapshot(call) ?: return
        activity.runOnUiThread {
            try {
                // a hidden screen takes no updates (A24), not even one sent before the close that
                // overtook it: it would make the hidden tab the owner of the bars
                if (visible) {
                    try {
                        accept(data, false)
                        showChrome(call.getObject("navScene"))
                    } finally {
                        call.getObject("reply")?.let { onReply(it) }
                    }
                }
                call.resolve()
            } catch (e: Exception) {
                call.reject("$tabId projection rejected", e)
            }
        }
    }

    @PluginMethod
    fun close(call: PluginCall) {
        activity.runOnUiThread {
            hide()
            call.resolve()
        }
    }

    /** The page's answer to a `swipe`: the token it names, taken or refused (and why). */
    @PluginMethod
    fun swipeSettle(call: PluginCall) {
        val token = call.getString("token")
        if (token == null) {
            call.reject("Missing swipe token")
            return
        }
        val outcome = SettleOutcome.of(call.getString("outcome"))
        activity.runOnUiThread {
            TabSwipe.settle(token, outcome, call.getString("reason"))
            call.resolve()
        }
    }

    override fun handleOnResume() {
        if (visible) notifyListeners("refresh", JSObject())
    }

    override fun load() {
        NativeScreens.add(this, ::remove)
        AudioDockHost.listenInsets(dockInsets)
    }

    override fun handleOnDestroy() {
        NativeScreens.remove(this)
        AudioDockHost.forgetInsets(dockInsets)
        activity.runOnUiThread {
            remove()
            store.clear()
        }
        super.handleOnDestroy()
    }

    private fun readSnapshot(call: PluginCall): JSONObject? {
        val data = call.getObject("snapshot")
        if (data == null || data.toString().length > MAX_SNAPSHOT_CHARS) {
            call.reject("Invalid $tabId projection")
            return null
        }
        return data
    }

    private fun createView(): TabScreenView =
        TabScreenView(activity, tabId)
            .apply {
                id = View.generateViewId()
                tag = "native-$tabId-screen"
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent { Content(topInsetDp, bottomInsetDp, ::emit) }
            }
            .also {
                activity
                    .findViewById<ViewGroup>(android.R.id.content)
                    .addView(it, FrameLayout.LayoutParams(-1, -1))
                view = it
                TabSwipe.register(tabId, it, swipeSource, ::onRested) { request ->
                    notifyListeners("swipe", JSObject.fromJSONObject(request.toJson()))
                }
            }

    /** The page's title for the tab, else the bar's label in the swipe state, else the tab id. */
    private fun paneTitle(sent: String?): String =
        sent?.trim()?.takeIf { it.isNotEmpty() } ?: TabSwipe.label(tabId) ?: tabId

    private fun emit(action: String, id: String) {
        val event = JSObject()
        if (action == "navigate") {
            event.put("view", id)
        } else {
            event.put("action", action)
            event.put("id", id)
        }
        notifyListeners("action", event)
    }

    private fun showChrome(scene: JSONObject?, opening: Boolean = false) {
        requireNotNull(scene) { "Missing shared Overview chrome" }
        val chrome =
            ChromeHost.show(this, activity, bridge.webView, scene, opening, tabId) { id ->
                notifyListeners("nav-action", JSObject().put("id", id))
            }
        applyInsets(ChromeHost.insetsDp() ?: chrome.contentInsetsDp())
    }

    private fun applyInsets(insets: Pair<Float, Float>) {
        topInsetDp = insets.first
        bottomInsetDp = insets.second
    }

    /**
     * The audio dock came, went or changed height: the content clears it with no new projection.
     */
    private val dockInsets: () -> Unit = { ChromeHost.insetsDp()?.let(::applyInsets) }

    /** Leaving the tab only hides the screen; it stays composed so the next visit is instant. */
    private fun hide() {
        if (visible) onHidden()
        visible = false
        TabSwipe.closed(tabId)
        ChromeHost.leave(this)
        notifyListeners("closed", JSObject())
    }

    private fun remove() {
        visible = false
        if (view != null) TabSwipe.removed(tabId) // a screen never made was never registered
        ChromeHost.closeNow(this)
        view?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.disposeComposition()
        }
        view = null
        onRested()
        notifyListeners("closed", JSObject())
    }

    private companion object {
        const val MAX_SNAPSHOT_CHARS = 2_000_000

        /** This build draws the native tab swipe (protocol v1). */
        const val SWIPE = "shell.swipe.v1"

        /** It also reveals web tabs and slides in a screen the page swiped to (phase 2). */
        const val SWIPE_EDGES = "shell.swipe.v2"

        /** It composes a screen ahead of its first open on the page's `prime` (phase 3). */
        const val PRIME = "shell.prime.v1"
    }
}
