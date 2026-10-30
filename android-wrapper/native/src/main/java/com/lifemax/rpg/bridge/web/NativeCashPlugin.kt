package com.lifemax.rpg.bridge.web

import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.lifemax.rpg.feature.cash.CashActivity
import com.lifemax.rpg.feature.cash.CashFeedback
import com.lifemax.rpg.feature.cash.CashHost
import com.lifemax.rpg.feature.cash.CashSession
import org.json.JSONObject

@CapacitorPlugin(name = "NativeCash")
class NativeCashPlugin : Plugin() {
    @PluginMethod
    fun open(call: PluginCall) {
        val id = call.getString("session")
        val snapshot = call.getObject("snapshot")
        if (id.isNullOrBlank() || snapshot == null) {
            call.reject("Missing Cash state")
            return
        }
        activity.runOnUiThread {
            try {
                if (CashHost.session != null) {
                    call.reject("Cash is already open")
                    return@runOnUiThread
                }
                CashHost.session =
                    CashSession(id, JSONObject(snapshot.toString())) {
                        notifyListeners("action", it)
                    }
                CashHost.closed = { session, view ->
                    val e = JSObject()
                    e.put("session", session)
                    e.put("view", view)
                    notifyListeners("closed", e)
                }
                activity.startActivity(Intent(activity, CashActivity::class.java))
                call.resolve()
            } catch (e: Exception) {
                CashHost.session = null
                CashHost.closed = null
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
                    s.feedback =
                        CashFeedback(
                            request,
                            call.getBoolean("ok", false) == true,
                            call.getString("message", "Saved") ?: "Saved",
                        )
                }
            }
            call.resolve()
        }
    }
}
