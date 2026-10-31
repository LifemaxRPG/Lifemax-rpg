package com.lifemax.rpg.feature.recipes.data

import com.lifemax.rpg.feature.recipes.domain.RecipesPageAction
import org.json.JSONObject

/** What the page's command "log" reads (50-native-recipes.js): the recipe's id. */
fun RecipesPageAction.Log.toJson(): JSONObject = JSONObject().put("id", recipeId)
