package com.lifemax.rpg.bridge.web

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.getcapacitor.annotation.CapacitorPlugin
import com.lifemax.rpg.app.LifeDirectorContainer
import com.lifemax.rpg.feature.lifedirector.domain.LifeDirectorCommands
import com.lifemax.rpg.feature.lifedirector.presentation.LifeDirectorRoute
import com.lifemax.rpg.feature.lifedirector.presentation.LifeDirectorViewModel
import org.json.JSONObject

/**
 * Life Director's Today: the page plans, decides and saves; the screen draws its projection, asks
 * for another day of the strip with a command and hands every other tap to the page.
 */
@CapacitorPlugin(name = "NativeLifeDirector")
class NativeLifeDirectorPlugin : ComposeTabPlugin() {
    private var nextRequest = 0L
    private val container =
        LifeDirectorContainer(
            object : LifeDirectorCommands {
                override fun showDay(index: Int) =
                    sendCommand(
                        "day",
                        JSONObject().put("index", index),
                        "director-${++nextRequest}",
                    )
            }
        )
    private val viewModel: LifeDirectorViewModel
        get() = ViewModelProvider(store, container.factory)[LifeDirectorViewModel::class.java]

    override val tabId = "life-director"
    override val capabilities = listOf("director.today.v1")

    override fun accept(snapshot: JSONObject, explicitOpen: Boolean) {
        container.repository.accept(snapshot, explicitOpen)
        if (explicitOpen) viewModel.opened()
    }

    override fun onReply(reply: JSONObject) =
        viewModel.onReply(
            reply.optString("command"),
            reply.optBoolean("ok"),
            reply.optString("message"),
        )

    @Composable
    override fun Content(
        topInsetDp: Float,
        bottomInsetDp: Float,
        emit: (action: String, id: String) -> Unit,
    ) = LifeDirectorRoute(viewModel, visible, topInsetDp, bottomInsetDp, emit)
}
