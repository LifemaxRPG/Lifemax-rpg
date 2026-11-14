package com.lifemax.rpg.bridge.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import com.getcapacitor.annotation.CapacitorPlugin
import com.lifemax.rpg.app.HabitsContainer
import com.lifemax.rpg.core.designsystem.feedback.LocalPageBuzz
import com.lifemax.rpg.core.designsystem.feedback.VibratorPageBuzz
import com.lifemax.rpg.feature.habits.presentation.HabitsRoute
import com.lifemax.rpg.feature.habits.presentation.HabitsViewModel
import org.json.JSONObject

/**
 * Habits: native from its data up. The habits live in a file of the app's own ([HabitsContainer]),
 * every rule runs here and the screen draws everything, sheets and text fields included; no command
 * goes to the page. What the page sends is only the screen's surroundings (palette, theme, motion,
 * haptics, the class name, whether there is a cat), and the shell's open and close, which every tab
 * gets from [ComposeTabPlugin]. The towers are swiped sideways, so a tab swipe never starts on this
 * screen (it can still be swiped to).
 */
@CapacitorPlugin(name = "NativeHabits")
class NativeHabitsPlugin : ComposeTabPlugin() {
    private val container by lazy { HabitsContainer(context.filesDir) }
    private val viewModel: HabitsViewModel
        get() = ViewModelProvider(store, container.factory)[HabitsViewModel::class.java]

    override val tabId = "habits"
    override val capabilities = listOf("habits.v1")
    override val swipeSource = false

    override fun accept(snapshot: JSONObject, explicitOpen: Boolean) {
        container.repository.accept(snapshot, explicitOpen)
        viewModel.onProjection()
    }

    override fun onHidden() = viewModel.hidden()

    @Composable
    override fun Content(
        topInsetDp: Float,
        bottomInsetDp: Float,
        emit: (action: String, id: String) -> Unit,
    ) {
        val vm = remember { viewModel }
        val context = LocalContext.current
        CompositionLocalProvider(
            LocalPageBuzz provides remember(context) { VibratorPageBuzz(context) }
        ) {
            HabitsRoute(vm, visible, topInsetDp, bottomInsetDp)
        }
    }
}
