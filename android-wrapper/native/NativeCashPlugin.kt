package com.lifemax.rpg

import android.content.Intent
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.lang.ref.WeakReference

/** Presentation only: all commands are acknowledged after the shared web state saves. */
internal class CashSession(val id: String, initial: JSONObject, val emit: (JSObject) -> Unit) {
    var snapshot by mutableStateOf(initial)
    var busy by mutableStateOf<String?>(null)
    var feedback by mutableStateOf<CashFeedback?>(null)
    private var sequence = 0
    fun request(action: String, data: JSONObject = JSONObject()) {
        if (busy != null && action != "refresh") return
        val event = JSObject(data.toString())
        event.put("session", id); event.put("action", action)
        if (action != "refresh") {
            val request = "$id:${++sequence}"
            busy = request; event.put("requestId", request)
        }
        emit(event)
    }
}
internal data class CashFeedback(val id: String, val ok: Boolean, val message: String)
internal object CashHost {
    var session: CashSession? = null
    var screen = WeakReference<CashActivity>(null)
    var closed: ((String, String) -> Unit)? = null
}

@CapacitorPlugin(name = "NativeCash")
class NativeCashPlugin : Plugin() {
    @PluginMethod
    fun open(call: PluginCall) {
        val id = call.getString("session")
        val snapshot = call.getObject("snapshot")
        if (id.isNullOrBlank() || snapshot == null) { call.reject("Missing Cash state"); return }
        activity.runOnUiThread {
            try {
                if (CashHost.session != null) { call.reject("Cash is already open"); return@runOnUiThread }
                CashHost.session = CashSession(id, JSONObject(snapshot.toString())) { notifyListeners("action", it) }
                CashHost.closed = { session, view ->
                    val e = JSObject(); e.put("session", session); e.put("view", view)
                    notifyListeners("closed", e)
                }
                activity.startActivity(Intent(activity, CashActivity::class.java))
                call.resolve()
            } catch (e: Exception) {
                CashHost.session = null; CashHost.closed = null
                call.reject("Could not open Cash", e)
            }
        }
    }

    @PluginMethod
    fun update(call: PluginCall) {
        val id = call.getString("session")
        val snapshot = call.getObject("snapshot")
        activity.runOnUiThread {
            val s = CashHost.session
            if (s != null && s.id == id && snapshot != null) {
                s.snapshot = JSONObject(snapshot.toString())
                val request = call.getString("requestId")
                if (request != null && request == s.busy) {
                    s.busy = null
                    s.feedback = CashFeedback(request, call.getBoolean("ok", false) == true, call.getString("message", "Saved") ?: "Saved")
                }
            }
            call.resolve()
        }
    }
}
