package com.lifemax.rpg.bridge.web

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.getcapacitor.annotation.CapacitorPlugin
import com.lifemax.rpg.app.LibraryContainer
import com.lifemax.rpg.app.shell.audio.AudioDockHost
import com.lifemax.rpg.app.shell.audio.AudioFilePicker
import com.lifemax.rpg.app.shell.audio.PickedAudio
import com.lifemax.rpg.app.shell.audio.capacitorContentUrl
import com.lifemax.rpg.feature.library.data.idPayload
import com.lifemax.rpg.feature.library.data.statusPayload
import com.lifemax.rpg.feature.library.data.toEvent
import com.lifemax.rpg.feature.library.data.toMediaReply
import com.lifemax.rpg.feature.library.data.upsertPayload
import com.lifemax.rpg.feature.library.domain.MediaAudioPort
import com.lifemax.rpg.feature.library.domain.MediaCommands
import com.lifemax.rpg.feature.library.domain.MediaFields
import com.lifemax.rpg.feature.library.presentation.MediaRoute
import com.lifemax.rpg.feature.library.presentation.MediaViewModel
import java.util.UUID
import org.json.JSONObject

/**
 * The Library's Media pane: the page keeps the list, the audio and the save; the screen draws the
 * pane and filters it itself. The add/edit sheet, the tick and the confirms are the screen's and
 * write through the page's own owners as commands (library.media.write.v1); play and attach hand
 * the tap to the page.
 */
@CapacitorPlugin(name = "NativeMedia")
class NativeMediaPlugin : ComposeTabPlugin() {
    private val container =
        LibraryContainer(
            object : MediaCommands {
                override fun upsert(op: String, id: String?, fields: MediaFields) =
                    send("upsert", upsertPayload(op, id, fields))

                override fun setStatus(id: String, status: String) =
                    send("setStatus", statusPayload(id, status))

                override fun remove(id: String) = send("remove", idPayload(id))

                override fun removeAudio(id: String) = send("removeAudio", idPayload(id))
            }
        )
    private val viewModel: MediaViewModel
        get() = ViewModelProvider(store, container.factory)[MediaViewModel::class.java]

    private var picker: AudioFilePicker? = null
    private var pickFor = ""

    /** The dock's side of Media: Play and the picker, as commands that keep the screen open. */
    private val audio =
        object : MediaAudioPort {
            override val dock: Boolean
                get() = AudioDockHost.isOn

            override val canPick: Boolean
                get() = AudioDockHost.isOn && picker != null

            override fun play(id: String) = sendCommand("play", JSONObject().put("id", id), "")

            override fun pick(id: String) {
                pickFor = id
                picker?.pick()
            }
        }

    override val tabId = "media"
    override val capabilities = listOf("library.media.projection.v1", "library.media.write.v1")

    /** A command under a new request id, which the page's reply names. */
    private fun send(command: String, payload: JSONObject): String =
        UUID.randomUUID().toString().also { sendCommand(command, payload, it) }

    override fun accept(snapshot: JSONObject, explicitOpen: Boolean) {
        container.mediaRepository.accept(snapshot, explicitOpen)
        if (explicitOpen) viewModel.opened()
        viewModel.onProjection()
    }

    /**
     * The picker's launcher is made here, while the activity is being created: a launcher cannot be
     * registered once it has started, and a native tap has no plugin call to start one with.
     */
    override fun load() {
        super.load()
        picker = runCatching { AudioFilePicker(activity, ::picked) }.getOrNull()
    }

    /**
     * The owner chose a file (or backed out). A file the page's list does not take is refused in
     * the page's words; any other goes to the page, which reads it at once through Capacitor's
     * local server (nothing is kept of the grant) and attaches it like the page's own picker.
     */
    private fun picked(file: PickedAudio?) {
        val id = pickFor
        pickFor = ""
        if (file == null || id.isEmpty()) return
        val accept = AudioDockHost.accept
        if (accept != null && !accept.allows(file.name, file.mime)) {
            sendCommand("attachRefused", JSONObject().put("name", file.name), "")
            return
        }
        val page = android.net.Uri.parse(bridge.webView.url.orEmpty())
        val origin = "${page.scheme ?: "http"}://${page.authority ?: "localhost"}"
        sendCommand(
            "attach",
            JSONObject()
                .put("id", id)
                .put("url", capacitorContentUrl(origin, file.uri.toString()))
                .put("name", file.name)
                .put("type", file.mime.orEmpty())
                .put("size", file.size),
            "",
        )
    }

    override fun onReply(reply: JSONObject) {
        reply.toMediaReply()?.let(viewModel::onReply)
    }

    @Composable
    override fun Content(
        topInsetDp: Float,
        bottomInsetDp: Float,
        emit: (action: String, id: String) -> Unit,
    ) =
        MediaRoute(viewModel, visible, topInsetDp, bottomInsetDp, audio) { action ->
            val (name, id) = action.toEvent()
            emit(name, id)
        }
}
