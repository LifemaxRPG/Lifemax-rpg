package com.lifemax.rpg.bridge.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelProvider
import com.getcapacitor.annotation.CapacitorPlugin
import com.lifemax.rpg.app.CharacterContainer
import com.lifemax.rpg.feature.character.data.CharacterCommandSender
import com.lifemax.rpg.feature.character.data.toCharacterReply
import com.lifemax.rpg.feature.character.presentation.CharacterRoute
import com.lifemax.rpg.feature.character.presentation.CharacterViewModel
import org.json.JSONObject

/**
 * Character: a projection of the page and, once the page lists `character.actions.v1`, the commands
 * behind Luck, the benchmark sheet and Log. Everything that is not specific to it lives in
 * [ComposeTabPlugin].
 */
@CapacitorPlugin(name = "NativeCharacter")
class NativeCharacterPlugin : ComposeTabPlugin() {
    private var nextRequest = 0
    private val container =
        CharacterContainer(
            CharacterCommandSender { command, payload ->
                "character-${++nextRequest}".also { sendCommand(command, payload, it) }
            }
        )
    private val characterVm: CharacterViewModel
        get() = ViewModelProvider(store, container.factory)[CharacterViewModel::class.java]

    override val tabId = "character"
    override val capabilities = listOf("character.projection.v1", "character.actions.v1")

    override fun accept(snapshot: JSONObject, explicitOpen: Boolean) {
        container.repository.accept(snapshot, explicitOpen)
        if (explicitOpen) characterVm.actions.opened()
        characterVm.onProjection()
    }

    override fun onReply(reply: JSONObject) {
        reply.toCharacterReply()?.let(characterVm.actions::onReply)
    }

    @Composable
    override fun Content(
        topInsetDp: Float,
        bottomInsetDp: Float,
        emit: (action: String, id: String) -> Unit,
    ) {
        val viewModel = remember { characterVm }
        CharacterRoute(viewModel, topInsetDp, bottomInsetDp, emit, visible)
    }
}
