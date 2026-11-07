package com.lifemax.rpg.bridge.web

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.getcapacitor.JSObject
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.lifemax.rpg.app.LifeDirectorContainer
import com.lifemax.rpg.feature.lifedirector.data.directorReply
import com.lifemax.rpg.feature.lifedirector.data.timer.TimerBridgeOwner
import com.lifemax.rpg.feature.lifedirector.domain.DialPlace
import com.lifemax.rpg.feature.lifedirector.domain.LifeDirectorCommands
import com.lifemax.rpg.feature.lifedirector.presentation.LifeDirectorRoute
import com.lifemax.rpg.feature.lifedirector.presentation.LifeDirectorViewModel
import org.json.JSONObject

/**
 * Life Director's Today, Week, Quests, Goals and Settings: the page plans, decides and saves; the
 * screen draws its projection, asks for another section or day (section, gotoDay, day), about a
 * dragged handle (dialDrop, dialApply), for a sheet of Today's (sheet) and runs Today's actions
 * (today) and the changes of Goals and Settings (do) with commands, and hands every other tap to
 * the page.
 */
@CapacitorPlugin(name = "NativeLifeDirector")
class NativeLifeDirectorPlugin : ComposeTabPlugin() {
    private var nextRequest = 0L
    private var timerOwner: TimerBridgeOwner? = null
    @Volatile private var timerDestroyed = false

    /** Staging IPC only. Mutations remain disabled until the authority handshake is wired. */
    @PluginMethod
    @Synchronized
    fun timer(call: PluginCall) {
        if (timerDestroyed) {
            call.reject("Timer bridge is closed")
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            call.resolve(JSObject().put("protocol", 1).put("status", "UNSUPPORTED"))
            return
        }
        val request = call.getObject("request")
        if (request == null) {
            call.reject("Missing timer request")
            return
        }
        val owner =
            timerOwner
                ?: TimerBridgeOwner(
                        context.applicationContext.filesDir
                            .toPath()
                            .resolve("life-director-timer.bin")
                    )
                    .also { timerOwner = it }
        owner.submit(request) { reply ->
            if (!timerDestroyed) call.resolve(JSObject.fromJSONObject(reply))
        }
    }

    @Synchronized
    override fun handleOnDestroy() {
        timerDestroyed = true
        timerOwner?.close()
        timerOwner = null
        super.handleOnDestroy()
    }

    private val container =
        LifeDirectorContainer(
            object : LifeDirectorCommands {
                override fun showDay(index: Int) = send("day", JSONObject().put("index", index))

                override fun showSection(id: String) = send("section", JSONObject().put("id", id))

                override fun gotoDay(index: Int) = send("gotoDay", JSONObject().put("index", index))

                override fun change(name: String, args: Map<String, Any>) =
                    send("do", JSONObject(args).put("name", name))

                override fun showSheet(kind: String, id: String) =
                    send("sheet", JSONObject().put("kind", kind).put("id", id))

                override fun today(name: String, args: Map<String, Any>) =
                    send("today", JSONObject(args).put("name", name))

                override fun tourSeen(key: String, finished: Boolean) =
                    send("tourSeen", JSONObject().put("key", key).put("finished", finished))

                override fun dialDrop(key: String, place: DialPlace) =
                    send("dialDrop", place.payload(key))

                override fun dialApply(key: String, place: DialPlace) =
                    send("dialApply", place.payload(key))
            }
        )
    private val viewModel: LifeDirectorViewModel
        get() = ViewModelProvider(store, container.factory)[LifeDirectorViewModel::class.java]

    private fun send(command: String, payload: JSONObject) =
        "director-${++nextRequest}".also { sendCommand(command, payload, it) }

    override val tabId = "life-director"
    override val capabilities =
        listOf(
            "director.today.v2",
            "director.week.v1",
            "director.goals.v1",
            "director.sheets.v1",
            "director.celebrations.v1",
            // a session lifted off the running order and dropped on the trash, with Undo
            "director.remove.v1",
            // My time and the first-run card (phase 4B)
            "director.setup.v1",
        )

    override fun accept(snapshot: JSONObject, explicitOpen: Boolean) {
        container.repository.accept(snapshot, explicitOpen)
        if (explicitOpen) viewModel.opened()
        viewModel.projected()
    }

    override fun onReply(reply: JSONObject) = viewModel.onReply(directorReply(reply))

    /** A handle's key and where it was let go, as the dial commands take them. */
    private fun DialPlace.payload(key: String): JSONObject =
        JSONObject().put("key", key).apply {
            when (val place = this@payload) {
                is DialPlace.Stop -> put("minute", place.minute)
                is DialPlace.Step -> put("step", place.step)
            }
        }

    @Composable
    override fun Content(
        topInsetDp: Float,
        bottomInsetDp: Float,
        emit: (action: String, id: String) -> Unit,
    ) = LifeDirectorRoute(viewModel, visible, topInsetDp, bottomInsetDp, emit)

    /**
     * Its sideways gestures (the section swipe, the dial's handles) are its own: never a source.
     */
    override val swipeSource = false
}
