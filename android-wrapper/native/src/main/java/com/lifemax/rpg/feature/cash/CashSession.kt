package com.lifemax.rpg.feature.cash

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.getcapacitor.JSObject
import java.lang.ref.WeakReference
import org.json.JSONObject

/** Presentation only: all commands are acknowledged after the shared web state saves. */
internal class CashSession(val id: String, initial: JSONObject, val emit: (JSObject) -> Unit) {
    var snapshot by mutableStateOf(initial)
    var busy by mutableStateOf<String?>(null)
    var feedback by mutableStateOf<CashFeedback?>(null)
    private var sequence = 0

    fun request(action: String, data: JSONObject = JSONObject()) {
        if (busy != null && action != "refresh") return
        val event = JSObject(data.toString())
        event.put("session", id)
        event.put("action", action)
        if (action != "refresh") {
            val request = "$id:${++sequence}"
            busy = request
            event.put("requestId", request)
        }
        emit(event)
    }
}

internal data class CashFeedback(val id: String, val ok: Boolean, val message: String)

internal object CashHost {
    var session: CashSession? = null
    var screen = WeakReference<CashActivity>(null)
    var closed: ((String, String) -> Unit)? = null

    /**
     * A new page started loading: the page that owns the session is gone. The new page holds no
     * session and the old one's listeners went with it, so a save would never be acknowledged and
     * Cash would wait for good. Cash closes, and opens afresh from the new page.
     */
    fun end() {
        session = null
        closed = null
        screen.get()?.finish()
        screen.clear()
    }
}
