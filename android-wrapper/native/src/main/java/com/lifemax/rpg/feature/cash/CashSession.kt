package com.lifemax.rpg.feature.cash

import android.widget.Toast
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
     * Cash would wait for good. Cash closes. The new page starts on its dashboard and does not open
     * Cash again; the owner does, from the Cash tab. The page cannot close Cash itself, so this is
     * how Cash ends when an import reloads the page while Cash is open (Cash opened during the
     * import's folder backup). A change still waiting for its answer may or may not have been
     * saved, so a toast says so.
     */
    fun end() {
        val unconfirmed = session?.busy != null
        val activity = screen.get()
        session = null
        closed = null
        if (unconfirmed && activity != null)
            Toast.makeText(activity.applicationContext, UNCONFIRMED, Toast.LENGTH_LONG).show()
        activity?.finish()
        screen.clear()
    }

    private const val UNCONFIRMED =
        "Cash closed before your change was confirmed. Open Cash to check it."
}
