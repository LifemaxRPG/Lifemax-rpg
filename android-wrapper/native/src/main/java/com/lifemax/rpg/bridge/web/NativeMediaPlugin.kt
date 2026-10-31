package com.lifemax.rpg.bridge.web

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.getcapacitor.annotation.CapacitorPlugin
import com.lifemax.rpg.app.LibraryContainer
import com.lifemax.rpg.feature.library.data.toEvent
import com.lifemax.rpg.feature.library.presentation.MediaRoute
import com.lifemax.rpg.feature.library.presentation.MediaViewModel
import org.json.JSONObject

/**
 * The Library's Media pane: the page keeps the list, the audio and the save; the screen draws the
 * pane, filters it itself and hands every tap to the page's own handler.
 */
@CapacitorPlugin(name = "NativeMedia")
class NativeMediaPlugin : ComposeTabPlugin() {
    private val container = LibraryContainer()
    private val viewModel: MediaViewModel
        get() = ViewModelProvider(store, container.factory)[MediaViewModel::class.java]

    override val tabId = "media"
    override val capabilities = listOf("library.media.projection.v1")

    override fun accept(snapshot: JSONObject, explicitOpen: Boolean) {
        container.mediaRepository.accept(snapshot, explicitOpen)
        if (explicitOpen) viewModel.opened()
        viewModel.onProjection()
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
