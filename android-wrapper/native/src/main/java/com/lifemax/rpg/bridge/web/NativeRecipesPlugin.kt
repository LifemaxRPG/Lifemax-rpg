package com.lifemax.rpg.bridge.web

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.getcapacitor.annotation.CapacitorPlugin
import com.lifemax.rpg.app.RecipesContainer
import com.lifemax.rpg.feature.recipes.data.toJson
import com.lifemax.rpg.feature.recipes.domain.RecipesPageAction
import com.lifemax.rpg.feature.recipes.presentation.RecipesRoute
import com.lifemax.rpg.feature.recipes.presentation.RecipesViewModel
import org.json.JSONObject

/**
 * Recipes: the screen searches, filters and opens recipes itself and opens product links. "Log this
 * recipe" is the page's add-food sheet, opened by the command "log" while the screen still covers
 * the page; Meals, and the sheet over it, take over through the hand-over.
 */
@CapacitorPlugin(name = "NativeRecipes")
class NativeRecipesPlugin : ComposeTabPlugin() {
    private val container = RecipesContainer()
    private var nextRequest = 0L
    private val viewModel: RecipesViewModel
        get() = ViewModelProvider(store, container.factory)[RecipesViewModel::class.java]

    override val tabId = "recipes"
    override val capabilities = listOf("recipes.projection.v1")

    override fun accept(snapshot: JSONObject, explicitOpen: Boolean) {
        container.repository.accept(snapshot, explicitOpen)
        viewModel.onProjection()
    }

    @Composable
    override fun Content(
        topInsetDp: Float,
        bottomInsetDp: Float,
        emit: (action: String, id: String) -> Unit,
    ) =
        RecipesRoute(viewModel, visible, topInsetDp, bottomInsetDp) { action ->
            when (action) {
                is RecipesPageAction.Log ->
                    sendCommand("log", action.toJson(), "recipes-${++nextRequest}")
                RecipesPageAction.Meals -> emit("navigate", "meals")
            }
        }
}
