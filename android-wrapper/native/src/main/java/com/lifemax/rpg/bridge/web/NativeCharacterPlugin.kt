package com.lifemax.rpg.bridge.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelProvider
import com.getcapacitor.annotation.CapacitorPlugin
import com.lifemax.rpg.app.CharacterContainer
import com.lifemax.rpg.feature.character.presentation.CharacterRoute
import com.lifemax.rpg.feature.character.presentation.CharacterViewModel
import org.json.JSONObject

/**
 * Character: a read-only projection of the page. Everything that is not specific to it lives in
 * [ComposeTabPlugin].
 */
@CapacitorPlugin(name = "NativeCharacter")
class NativeCharacterPlugin : ComposeTabPlugin() {
    private val container = CharacterContainer()

    override val tabId = "character"
    override val capabilities = listOf("character.projection.v1")

    override fun accept(snapshot: JSONObject, explicitOpen: Boolean) =
        container.repository.accept(snapshot, explicitOpen)

    @Composable
    override fun Content(
        topInsetDp: Float,
        bottomInsetDp: Float,
        emit: (action: String, id: String) -> Unit,
    ) {
        val viewModel = remember {
            ViewModelProvider(store, container.factory)[CharacterViewModel::class.java]
        }
        CharacterRoute(viewModel, topInsetDp, bottomInsetDp, emit)
    }
}
