package com.lifemax.rpg.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lifemax.rpg.feature.recipes.data.WebRecipesRepository
import com.lifemax.rpg.feature.recipes.presentation.RecipesViewModel

/** Composition root for Recipes, scoped to its plugin. */
class RecipesContainer {
    val repository = WebRecipesRepository()
    val factory =
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == RecipesViewModel::class.java)
                @Suppress("UNCHECKED_CAST")
                return RecipesViewModel(repository) as T
            }
        }
}
