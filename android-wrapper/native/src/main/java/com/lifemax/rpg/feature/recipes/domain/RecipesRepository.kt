package com.lifemax.rpg.feature.recipes.domain

import kotlinx.coroutines.flow.StateFlow

interface RecipesRepository {
    val state: StateFlow<RecipesProjection?>
}

/** What the page does for the screen. */
sealed interface RecipesPageAction {
    /** One serving in the page's add-food sheet over Meals, which then takes over. */
    data class Log(val recipeId: String) : RecipesPageAction

    /** Go to Meals, which takes over from the screen. */
    data object Meals : RecipesPageAction
}
