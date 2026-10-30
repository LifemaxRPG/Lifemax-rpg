package com.lifemax.rpg.bridge.web

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.lifemax.rpg.app.shell.ChromeHost
import com.lifemax.rpg.app.shell.TabTiming
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
 */
@CapacitorPlugin(name = "NativeNutrition")
class NativeNutritionPlugin : Plugin() {
    private var session = ""
    private var layer: ComposeView? = null
    private var snapshot by mutableStateOf<JSONObject?>(null)
    private var shown by mutableStateOf(false)
    private var topInsetDp by mutableFloatStateOf(64f)
    private var bottomInsetDp by mutableFloatStateOf(72f)

    @PluginMethod
    fun capabilities(call: PluginCall) {
        call.resolve(JSObject().put("version", 1).put("authoritativeData", true))
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
                view.visibility = View.VISIBLE
                view.bringToFront()
                shown = true
                showChrome(call.getObject("chromeScene"))
                TabTiming.attached(view, "food")
                call.resolve()
            } catch (e: Exception) {
                dismiss(false)
                call.reject("Native Nutrition unavailable", e)
            }
        }
    }

    private fun createLayer(): ComposeView {
        val view = ComposeView(activity)
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

    override fun handleOnDestroy() {
        dismiss(false)
        super.handleOnDestroy()
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

    /** Leaving the tab only hides the screen. Updates are ignored until it is opened again. */
    private fun hide() {
        session = ""
        shown = false
        layer?.visibility = View.GONE
        ChromeHost.leave(this)
    }

    private fun dismiss(notify: Boolean) {
        shown = false
        ChromeHost.closeNow(this)
        val id = session
        session = ""
        snapshot = null
        layer?.let { view -> (view.parent as? ViewGroup)?.removeView(view) }
        layer = null
        if (notify && id.isNotEmpty()) {
            notifyListeners("closed", JSObject().put("session", id))
        }
    }
}
