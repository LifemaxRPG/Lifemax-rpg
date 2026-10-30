package com.lifemax.rpg

import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import java.lang.ref.WeakReference
import org.json.JSONObject

// Retained types for the retired Canvas activity; new opens never launch it.
internal class OverviewSession(
    val id: String,
    var snapshot: JSONObject,
    val emit: (String, JSObject) -> Unit,
)

internal object OverviewHost {
    var session: OverviewSession? = null
    var screen = WeakReference<OverviewActivity>(null)
}

@CapacitorPlugin(name = "NativeOverview")
class NativeOverviewPlugin : Plugin() {
    private var shell: LiveOverviewShell? = null
    private var session = ""

    @PluginMethod
    fun capabilities(call: PluginCall) {
        call.resolve(JSObject().put("liveWeb", true))
    }

    private fun valid(scene: JSONObject?): Boolean =
        scene != null &&
            scene.optInt("version") == 2 &&
            scene.optDouble("width") in 1.0..2000.0 &&
            scene.toString().length < 2_000_000 &&
            !scene.has("body")

    @PluginMethod
    fun open(call: PluginCall) {
        val scene = call.getObject("snapshot")
        val id = call.getString("session")
        if (id.isNullOrBlank() || !valid(scene)) {
            call.reject("Invalid live Overview shell")
            return
        }
        activity.runOnUiThread {
            TabTiming.opened()
            try {
                shell?.close()
                session = id
                ChromeHost.closeAll()
                shell =
                    LiveOverviewShell(activity, bridge.webView) { action ->
                            notifyListeners(
                                "activate",
                                JSObject()
                                    .put("session", session)
                                    .put("action", action)
                                    .put("sequence", shell?.sequence ?: 0),
                            )
                        }
                        .also { it.show(scene!!) }
                OverviewBars.shell = shell
                shell?.let { TabTiming.attached(it.root, "overview") }
                call.resolve()
            } catch (e: Exception) {
                shell?.close()
                shell = null
                call.reject("Could not attach Overview controls", e)
            }
        }
    }

    /** the last tab switch as measured on this phone, and a switch for showing it as a toast */
    @PluginMethod
    fun timing(call: PluginCall) {
        call.resolve(JSObject().put("last", TabTiming.lastSummary).put("toasts", TabTiming.toasts))
    }

    @PluginMethod
    fun setTimingToast(call: PluginCall) {
        TabTiming.toasts = call.getBoolean("on", false) == true
        call.resolve()
    }

    @PluginMethod
    fun update(call: PluginCall) {
        val scene = call.getObject("snapshot")
        if (!valid(scene)) {
            call.reject("Invalid live Overview shell")
            return
        }
        activity.runOnUiThread {
            if (call.getString("session") == session) shell?.show(scene!!)
            call.resolve()
        }
    }

    @PluginMethod
    fun close(call: PluginCall) {
        activity.runOnUiThread {
            if (call.getString("session") == session) {
                shell?.close()
                shell = null
                OverviewBars.shell = null
                notifyListeners("closed", JSObject().put("session", session))
            }
            call.resolve()
        }
    }

    override fun handleOnDestroy() {
        activity.runOnUiThread {
            shell?.close()
            shell = null
            OverviewBars.shell = null
        }
        super.handleOnDestroy()
    }
}
