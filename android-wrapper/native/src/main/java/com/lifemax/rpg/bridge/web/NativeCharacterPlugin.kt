package com.lifemax.rpg.bridge.web

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.getcapacitor.*
import com.getcapacitor.annotation.CapacitorPlugin
import com.lifemax.rpg.LiveOverviewShell
import com.lifemax.rpg.feature.character.presentation.*
import org.json.JSONArray

@CapacitorPlugin(name = "NativeCharacter")
class NativeCharacterPlugin : Plugin() {
    private val container = com.lifemax.rpg.app.CharacterContainer()
    private val repository = container.repository
    private val store = ViewModelStore()
    private var view: ComposeView? = null
    private var chrome: LiveOverviewShell? = null

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
                                CharacterRoute(vm) { action, id ->
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
                showNav(call.getObject("navScene"))
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
            remove()
            call.resolve()
        }
    }

    private fun showNav(scene: org.json.JSONObject?) {
        requireNotNull(scene) { "Missing shared Overview chrome" }
        if (chrome == null)
            chrome =
                LiveOverviewShell(activity, bridge.webView, chromeOnly = true) { id ->
                    notifyListeners("nav-action", JSObject().put("id", id))
                }

        chrome?.show(scene)
    }

    private fun remove() {
        chrome?.close()
        chrome = null
        view?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.disposeComposition()
        }
        view = null
        notifyListeners("closed", JSObject())
    }

    override fun handleOnResume() {
        if (view != null) notifyListeners("refresh", JSObject())
    }

    override fun handleOnDestroy() {
        activity.runOnUiThread {
            remove()
            store.clear()
        }
        super.handleOnDestroy()
    }
}
