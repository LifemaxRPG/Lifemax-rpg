package com.lifemax.rpg.bridge.web

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.lifemax.rpg.app.shell.ChromeHost
import com.lifemax.rpg.app.shell.NativeScreens
import com.lifemax.rpg.app.shell.TabTiming
import com.lifemax.rpg.app.shell.audio.AudioDockHost
import com.lifemax.rpg.app.shell.swipe.SettleOutcome
import com.lifemax.rpg.app.shell.swipe.SwipeEnter
import com.lifemax.rpg.app.shell.swipe.TabScreenView
import com.lifemax.rpg.app.shell.swipe.TabSwipe
import com.lifemax.rpg.feature.food.presentation.NativeNutritionScreen
import org.json.JSONObject

/**
 * Native Jetpack Compose Nutrition preview (`Plan`, `Log`, and `Shopping`) with Card-Anchored AAA
 * Plasma Ribbon & Circumferential Ring-Torch animation.
 *
 * All displayed calories, logged foods, Smart Diet meals, shopping items, goals, and water come
 * from the authoritative live WebView (`50-native-nutrition.js`). All persistent mutations route
 * back through canonical handlers (`fv2Log`, `fv2AddWater`, `fv2CopySlot`, `sdLogMeal`,
 * `setMealsDay`, `fv2Open`, `sdOpenSwap`).
 *
 * Its screen takes part in the native tab swipe as the 'meals' pane, as ComposeTabPlugin's do, with
 * the screen session on the `swipe` event and checked on the page's `swipeSettle`.
 */
@CapacitorPlugin(name = "NativeNutrition")
class NativeNutritionPlugin : Plugin() {
    private var session = ""
    private var layer: TabScreenView? = null
    private var snapshot by mutableStateOf<JSONObject?>(null)
    private var shown by mutableStateOf(false)
    private var topInsetDp by mutableFloatStateOf(64f)
    private var bottomInsetDp by mutableFloatStateOf(72f)

    @PluginMethod
    fun capabilities(call: PluginCall) {
        call.resolve(
            JSObject().put("version", 1).put("authoritativeData", true).put("swipe", SWIPE_VERSION)
        )
    }

    @PluginMethod
    fun open(call: PluginCall) {
        val id = call.getString("session")
        val data = call.getObject("snapshot")
        if (
            id.isNullOrBlank() ||
                data == null ||
                data.optInt("version") != 1 ||
                data.toString().length > 250000 ||
                !data.has("totals") ||
                !data.has("goals")
        ) {
            call.reject("Invalid native Nutrition snapshot")
            return
        }
        activity.runOnUiThread {
            TabTiming.opened()
            try {
                session = id
                snapshot = JSONObject(data.toString())
                // the screen stays composed while another tab is showing, so coming back is instant
                val view = layer ?: createLayer()
                TabSwipe.opened(
                    PANE,
                    SwipeEnter.of(call.getString("enter"), call.getDouble("velocity")),
                )
                view.bringToFront()
                shown = true
                showChrome(call.getObject("chromeScene"), opening = true)
                TabTiming.attached(view, "food")
                call.resolve()
            } catch (e: Exception) {
                dismiss(false)
                call.reject("Native Nutrition unavailable", e)
            }
        }
    }

    private fun createLayer(): TabScreenView {
        val view = TabScreenView(activity, PANE)
        view.tag = "native-nutrition-screen"
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        view.setContent {
            snapshot?.let { current ->
                NativeNutritionScreen(current, topInsetDp, bottomInsetDp) { command, payload ->
                    val evt = JSObject().put("session", session).put("command", command)
                    payload.forEach { (k, v) -> evt.put(k, v) }
                    notifyListeners("action", evt)
                }
            }
        }
        layer = view
        activity
            .findViewById<ViewGroup>(android.R.id.content)
            .addView(view, FrameLayout.LayoutParams(-1, -1))
        TabSwipe.register(PANE, view, source = true) { request ->
            notifyListeners(
                "swipe",
                JSObject.fromJSONObject(request.toJson()).put("session", session),
            )
        }
        return view
    }

    @PluginMethod
    fun update(call: PluginCall) {
        val data = call.getObject("snapshot")
        activity.runOnUiThread {
            try {
                if (
                    call.getString("session") == session &&
                        layer != null &&
                        data != null &&
                        data.optInt("version") == 1 &&
                        data.toString().length <= 250000 &&
                        data.has("totals") &&
                        data.has("goals")
                ) {
                    snapshot = JSONObject(data.toString())
                    showChrome(call.getObject("chromeScene"))
                }
                call.resolve()
            } catch (e: Exception) {
                call.reject("Native Nutrition refresh failed", e)
            }
        }
    }

    @PluginMethod
    fun close(call: PluginCall) {
        activity.runOnUiThread {
            if (call.getString("session") == session) {
                hide()
            }
            call.resolve()
        }
    }

    /** The page's answer to a `swipe` from this screen session; one for another is ignored. */
    @PluginMethod
    fun swipeSettle(call: PluginCall) {
        val token = call.getString("token")
        if (token == null) {
            call.reject("Missing swipe token")
            return
        }
        val outcome = SettleOutcome.of(call.getString("outcome"))
        activity.runOnUiThread {
            if (call.getString("session") == session)
                TabSwipe.settle(token, outcome, call.getString("reason"))
            call.resolve()
        }
    }

    override fun load() {
        NativeScreens.add(this) { dismiss(false) }
        AudioDockHost.listenInsets(dockInsets)
    }

    override fun handleOnDestroy() {
        NativeScreens.remove(this)
        AudioDockHost.forgetInsets(dockInsets)
        dismiss(false)
        super.handleOnDestroy()
    }

    private fun showChrome(scene: JSONObject?, opening: Boolean = false) {
        requireNotNull(scene) { "Missing shared Overview chrome" }
        val chrome =
            ChromeHost.show(this, activity, bridge.webView, scene, opening, PANE) { id ->
                notifyListeners("nav-action", JSObject().put("id", id))
            }
        applyInsets(ChromeHost.insetsDp() ?: chrome.contentInsetsDp())
    }

    private fun applyInsets(insets: Pair<Float, Float>) {
        topInsetDp = insets.first
        bottomInsetDp = insets.second
    }

    /** The audio dock came, went or changed height: the content clears it with no new snapshot. */
    private val dockInsets: () -> Unit = { ChromeHost.insetsDp()?.let(::applyInsets) }

    /** Leaving the tab only hides the screen. Updates are ignored until it is opened again. */
    private fun hide() {
        session = ""
        shown = false
        TabSwipe.closed(PANE)
        ChromeHost.leave(this)
    }

    private fun dismiss(notify: Boolean) {
        shown = false
        ChromeHost.closeNow(this)
        val id = session
        session = ""
        snapshot = null
        if (layer != null) TabSwipe.removed(PANE) // a screen never made was never registered
        layer?.let { view -> (view.parent as? ViewGroup)?.removeView(view) }
        layer = null
        if (notify && id.isNotEmpty()) {
            notifyListeners("closed", JSObject().put("session", id))
        }
    }

    private companion object {
        /** The page view the screen draws, its pane in the tab swipe. */
        const val PANE = "meals"

        /** The tab swipe protocol this build draws: 2, phase 1 and the edges (CONTRACT A34). */
        const val SWIPE_VERSION = 2
    }
}
