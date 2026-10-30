package com.lifemax.rpg.bridge.web

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.getcapacitor.*
import com.getcapacitor.annotation.CapacitorPlugin
import com.lifemax.rpg.app.shell.ChromeHost
import com.lifemax.rpg.app.shell.TabTiming
import com.lifemax.rpg.feature.character.presentation.*
import org.json.JSONArray

@CapacitorPlugin(name = "NativeCharacter")
class NativeCharacterPlugin : Plugin() {
    private val container = com.lifemax.rpg.app.CharacterContainer()
    private val repository = container.repository
    private val store = ViewModelStore()
    private var view: ComposeView? = null
    private var topInsetDp by mutableFloatStateOf(64f)
    private var bottomInsetDp by mutableFloatStateOf(72f)

    @PluginMethod
    fun handshake(call: PluginCall) {
        call.resolve(
            JSObject()
                .put("protocol", 1)
                .put("capabilities", JSONArray().put("character.projection.v1"))
        )
    }

    @PluginMethod
    fun open(call: PluginCall) {
        val data = call.getObject("snapshot")
        if (data == null || data.toString().length > 2_000_000) {
            call.reject("Invalid Character projection")
            return
        }
        activity.runOnUiThread {
            TabTiming.opened()
            try {
                repository.accept(data, true)
                if (view == null) {
                    val vm =
                        ViewModelProvider(store, container.factory)[CharacterViewModel::class.java]
                    view =
                        ComposeView(activity).apply {
                            id = View.generateViewId()
                            tag = "native-character-screen"
                            setViewCompositionStrategy(
                                ViewCompositionStrategy.DisposeOnDetachedFromWindow
                            )
                            setContent {
                                CharacterRoute(vm, topInsetDp, bottomInsetDp) { action, id ->
                                    val event = JSObject()
                                    if (action == "navigate") event.put("view", id)
                                    else {
                                        event.put("action", action)
                                        event.put("id", id)
                                    }
                                    notifyListeners("action", event)
                                }
                            }
                        }
                    activity
                        .findViewById<ViewGroup>(android.R.id.content)
                        .addView(view, FrameLayout.LayoutParams(-1, -1))
                }
                view?.visibility = View.VISIBLE
                view?.bringToFront()
                showNav(call.getObject("navScene"))
                view?.let { TabTiming.attached(it, "character") }
                call.resolve()
            } catch (e: Exception) {
                remove()
                call.reject("Character could not open", e)
            }
        }
    }

    @PluginMethod
    fun update(call: PluginCall) {
        val data = call.getObject("snapshot")
        if (data == null || data.toString().length > 2_000_000) {
            call.reject("Invalid Character projection")
            return
        }
        activity.runOnUiThread {
            try {
                if (view != null) {
                    repository.accept(data)
                    showNav(call.getObject("navScene"))
                }
                call.resolve()
            } catch (e: Exception) {
                call.reject("Character projection rejected", e)
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

    private fun showNav(scene: org.json.JSONObject?) {
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
        ChromeHost.leave(this)
        notifyListeners("closed", JSObject())
    }

    private fun remove() {
        ChromeHost.closeNow(this)
        view?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.disposeComposition()
        }
        view = null
        notifyListeners("closed", JSObject())
    }

    override fun handleOnResume() {
        if (view?.visibility == View.VISIBLE) notifyListeners("refresh", JSObject())
    }

    override fun handleOnDestroy() {
        activity.runOnUiThread {
            remove()
            store.clear()
        }
        super.handleOnDestroy()
    }
}
