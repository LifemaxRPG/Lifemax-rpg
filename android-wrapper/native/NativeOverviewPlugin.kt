package com.lifemax.rpg

import android.content.Intent
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import org.json.JSONObject
import java.lang.ref.WeakReference

internal class OverviewSession(val id: String, var snapshot: JSONObject, val emit: (String, JSObject) -> Unit)
internal object OverviewHost {
    var session: OverviewSession? = null
    var screen = WeakReference<OverviewActivity>(null)
}

@CapacitorPlugin(name = "NativeOverview")
class NativeOverviewPlugin : Plugin() {
    private fun valid(scene: JSONObject?): Boolean = scene != null && scene.optInt("version") == 1 &&
        scene.optDouble("width") in 1.0..2000.0 && scene.optDouble("height") in 1.0..100000.0 &&
        scene.toString().length < 12_000_000

    @PluginMethod fun open(call: PluginCall) {
        val id = call.getString("session")
        val scene = call.getObject("snapshot")
        if (id.isNullOrBlank() || !valid(scene)) { call.reject("Invalid Overview snapshot"); return }
        activity.runOnUiThread {
            if (OverviewHost.session != null) { call.reject("Overview already open"); return@runOnUiThread }
            try {
                OverviewHost.session = OverviewSession(id, JSONObject(scene.toString())) { event, data -> notifyListeners(event, data) }
                activity.startActivity(Intent(activity, OverviewActivity::class.java))
                call.resolve()
            } catch (e: Exception) { OverviewHost.session = null; call.reject("Could not open Overview", e) }
        }
    }
    @PluginMethod fun update(call: PluginCall) {
        val scene = call.getObject("snapshot")
        if (!valid(scene)) { call.reject("Invalid Overview snapshot"); return }
        activity.runOnUiThread {
            val s = OverviewHost.session
            if (s != null && s.id == call.getString("session") && scene!!.optInt("sequence") > s.snapshot.optInt("sequence")) {
                s.snapshot = JSONObject(scene.toString())
                OverviewHost.screen.get()?.showSnapshot(s.snapshot)
            }
            call.resolve()
        }
    }
    @PluginMethod fun close(call: PluginCall) {
        activity.runOnUiThread {
            if (OverviewHost.session?.id == call.getString("session")) OverviewHost.screen.get()?.finish()
            call.resolve()
        }
    }
    override fun handleOnDestroy() {
        activity.runOnUiThread { OverviewHost.screen.get()?.finish(); OverviewHost.session = null }
        super.handleOnDestroy()
    }
}
