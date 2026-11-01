package com.lifemax.rpg.bridge.web

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.getcapacitor.annotation.CapacitorPlugin
import com.lifemax.rpg.app.SettingsContainer
import com.lifemax.rpg.feature.settings.data.toEvent
import com.lifemax.rpg.feature.settings.data.toJson
import com.lifemax.rpg.feature.settings.data.toSettingsReply
import com.lifemax.rpg.feature.settings.presentation.SettingsRoute
import com.lifemax.rpg.feature.settings.presentation.SettingsViewModel
import org.json.JSONObject

/**
 * Settings: the screen draws the page's own Settings and changes the settings the page lets it
 * (toggle, choose, press, category) through the page's own handlers while it stays; everything else
 * (fields, pickers, sheets, the data tools, a switch Android asks about first) is revealed on the
 * page after the screen steps aside, and "Classic Settings" leaves the page in front.
 */
@CapacitorPlugin(name = "NativeSettings")
class NativeSettingsPlugin : ComposeTabPlugin() {
    private val container = SettingsContainer { command, requestId ->
        sendCommand(command.name, command.toJson(), requestId)
    }
    private val viewModel: SettingsViewModel
        get() = ViewModelProvider(store, container.factory)[SettingsViewModel::class.java]

    override val tabId = "settings"
    override val capabilities = listOf("settings.projection.v1")

    override fun accept(snapshot: JSONObject, explicitOpen: Boolean) {
        container.repository.accept(snapshot, explicitOpen)
        if (explicitOpen) viewModel.onOpen() else viewModel.onUpdate()
    }

    override fun onReply(reply: JSONObject) {
        reply.toSettingsReply()?.let(viewModel::onReply)
    }

    @Composable
    override fun Content(
        topInsetDp: Float,
        bottomInsetDp: Float,
        emit: (action: String, id: String) -> Unit,
    ) =
        SettingsRoute(viewModel, visible, topInsetDp, bottomInsetDp) { action ->
            val (name, id) = action.toEvent()
            emit(name, id)
        }
}
