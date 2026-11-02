package com.lifemax.rpg.bridge.web

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.getcapacitor.annotation.CapacitorPlugin
import com.lifemax.rpg.app.LibraryContainer
import com.lifemax.rpg.feature.library.data.idPayload
import com.lifemax.rpg.feature.library.data.statusPayload
import com.lifemax.rpg.feature.library.data.toEvent
import com.lifemax.rpg.feature.library.data.toMediaReply
import com.lifemax.rpg.feature.library.data.upsertPayload
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

    override fun onReply(reply: JSONObject) {
        reply.toMediaReply()?.let(viewModel::onReply)
    }

    @Composable
    override fun Content(
        topInsetDp: Float,
        bottomInsetDp: Float,
        emit: (action: String, id: String) -> Unit,
    ) =
        MediaRoute(viewModel, visible, topInsetDp, bottomInsetDp) { action ->
            val (name, id) = action.toEvent()
            emit(name, id)
        }
}
