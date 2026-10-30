package com.lifemax.rpg.bridge.web

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.getcapacitor.annotation.CapacitorPlugin
import com.lifemax.rpg.app.LogContainer
import com.lifemax.rpg.feature.log.data.toJson
import com.lifemax.rpg.feature.log.domain.LogCommands
import com.lifemax.rpg.feature.log.domain.LogDraft
import com.lifemax.rpg.feature.log.presentation.LogRoute
import com.lifemax.rpg.feature.log.presentation.LogViewModel
import org.json.JSONObject

/**
 * Log activity: the screen edits a draft; previews and saves are commands the page runs with its
 * own rules and save.
 */
@CapacitorPlugin(name = "NativeLog")
class NativeLogPlugin : ComposeTabPlugin() {
    private var nextRequest = 0L
    private val container =
        LogContainer(
            object : LogCommands {
                override fun preview(draft: LogDraft) =
                    sendCommand("preview", JSONObject().put("draft", draft.toJson()), request())

                override fun submit(draft: LogDraft, operationId: String) =
                    sendCommand(
                        "submit",
                        JSONObject().put("draft", draft.toJson()).put("operationId", operationId),
                        request(),
                    )
            }
        )
    private val viewModel: LogViewModel
        get() = ViewModelProvider(store, container.factory)[LogViewModel::class.java]

    override val tabId = "log"
    override val capabilities = listOf("log.projection.v1")

    override fun accept(snapshot: JSONObject, explicitOpen: Boolean) =
        container.repository.accept(snapshot, explicitOpen)

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
    ) = LogRoute(viewModel, topInsetDp, bottomInsetDp)

    private fun request() = "log-${++nextRequest}"
}
