package com.lifemax.rpg.feature.recipes.data

import com.lifemax.rpg.feature.recipes.domain.RecipesPageAction

/**
 * The action name and id the page reads (50-native-recipes.js onAction; "navigate" names the view
 * NativeTabKit goes to).
 */
fun RecipesPageAction.toEvent(): Pair<String, String> =
    when (this) {
        is RecipesPageAction.Log -> "log" to recipeId
        RecipesPageAction.Meals -> "navigate" to "meals"
    }
