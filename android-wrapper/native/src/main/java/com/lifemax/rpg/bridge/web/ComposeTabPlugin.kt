package com.lifemax.rpg.bridge.web

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.ViewModelStore
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.lifemax.rpg.app.shell.ChromeHost
import com.lifemax.rpg.app.shell.NativeScreens
import com.lifemax.rpg.app.shell.TabTiming
import org.json.JSONObject

/**
 * The native half of a tab (the web half is NativeTabKit). It owns what every native tab does the
 * same way: the handshake, opening, updating and closing a Compose screen over the page, the shared
 * top and bottom bars and the hand-over to the next tab. A tab says only what is its own: how to
 * take a projection from the page ([accept]) and what to draw ([Content]).
 *
 * Calls arrive in the order the page made them. Closing only hides the screen, which stays composed
 * so coming back is instant; a page that starts loading removes it ([NativeScreens]). Contract:
 * A03, A07, A23, A24.
 */
abstract class ComposeTabPlugin : Plugin() {
    /** The page view this tab shows. Also the timing label and the view's tag. */
    protected abstract val tabId: String

    /** Capability names the page checks in the handshake. */
    protected abstract val capabilities: List<String>

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
     * the page did.
     */
    protected open fun onReply(reply: JSONObject) {}

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

    private var view: ComposeView? = null
    private var topInsetDp by mutableFloatStateOf(64f)
    private var bottomInsetDp by mutableFloatStateOf(72f)

    @PluginMethod
    fun handshake(call: PluginCall) {
        call.resolve(
            JSObject().put("protocol", 1).put("capabilities", JSArray(capabilities.toList()))
        )
    }

    @PluginMethod
    fun open(call: PluginCall) {
        val data = readSnapshot(call) ?: return
        activity.runOnUiThread {
            TabTiming.opened()
            try {
                accept(data, true)
                val screen = view ?: createView()
                screen.visibility = View.VISIBLE
                screen.bringToFront()
                visible = true
                showChrome(call.getObject("navScene"))
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
                if (view != null) {
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

    override fun load() {
        NativeScreens.add(this, ::remove)
    }

    override fun handleOnResume() {
        if (view?.visibility == View.VISIBLE) notifyListeners("refresh", JSObject())
    }

    override fun handleOnDestroy() {
        NativeScreens.remove(this)
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

    private fun createView(): ComposeView =
        ComposeView(activity)
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
            }

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

    private fun showChrome(scene: JSONObject?) {
        requireNotNull(scene) { "Missing shared Overview chrome" }
        val chrome =
            ChromeHost.show(this, activity, bridge.webView, scene) { id ->
                notifyListeners("nav-action", JSObject().put("id", id))
            }
        val (top, bottom) = chrome.contentInsetsDp()
        topInsetDp = top
        bottomInsetDp = bottom
    }

    /** Leaving the tab only hides the screen; it stays composed so the next visit is instant. */
    private fun hide() {
        view?.visibility = View.GONE
        visible = false
        ChromeHost.leave(this)
        notifyListeners("closed", JSObject())
    }

    private fun remove() {
        visible = false
        ChromeHost.closeNow(this)
        view?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.disposeComposition()
        }
        view = null
        notifyListeners("closed", JSObject())
    }

    private companion object {
        const val MAX_SNAPSHOT_CHARS = 2_000_000
    }
}
