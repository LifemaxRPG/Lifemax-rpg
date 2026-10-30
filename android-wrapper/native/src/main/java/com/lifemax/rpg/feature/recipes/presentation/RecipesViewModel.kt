package com.lifemax.rpg.feature.recipes.presentation

import androidx.lifecycle.ViewModel
import com.lifemax.rpg.feature.recipes.domain.Recipe
import com.lifemax.rpg.feature.recipes.domain.RecipeFilter
import com.lifemax.rpg.feature.recipes.domain.RecipesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The screen's own search, category, open recipes and selected ingredient, and the recipes they
 * show, by the page's rule. The page's search and category are taken only when the page sets them
 * (a new session or filter revision: the diet card asking for a recipe); any other projection keeps
 * what the screen holds.
 */
class RecipesViewModel(repository: RecipesRepository) : ViewModel() {
    val projection = repository.state
    private val mutable = MutableStateFlow(RecipesUi())
    val ui = mutable.asStateFlow()
    private var adopted: Pair<String, Long>? = null

    /** The page sent a projection. */
    fun onProjection() {
        val p = projection.value ?: return
        val key = p.session to p.filterRevision
        if (key == adopted) {
            show(ui.value.filter)
        } else {
            adopted = key
            show(p.filter)
        }
    }

    fun search(query: String) = show(ui.value.filter.copy(query = query))

    /** A category from the page's menu. */
    fun choose(category: String) {
        if (projection.value?.categories?.any { it.value == category } == true)
            show(ui.value.filter.copy(category = category))
    }

    /** Open or close a recipe's ingredients and instructions. */
    fun toggle(recipeId: String) =
        mutable.update {
            it.copy(
                expanded =
                    if (recipeId in it.expanded) it.expanded - recipeId else it.expanded + recipeId
            )
        }

    /** Select an ingredient, which shows its product panel; one in the whole list. */
    fun select(ingredient: IngredientRef) = mutable.update { it.copy(selected = ingredient) }

    private fun show(filter: RecipeFilter) {
        val recipes = projection.value?.recipes.orEmpty()
        mutable.update { it.copy(filter = filter, hits = filter.hits(recipes)) }
    }
}

/** The ingredient at [index] of a recipe. */
data class IngredientRef(val recipeId: String, val index: Int)

/** [hits] are the recipes [filter] shows, in the page's order; [expanded] holds open recipes. */
data class RecipesUi(
    val filter: RecipeFilter = RecipeFilter(),
    val hits: List<Recipe> = emptyList(),
    val expanded: Set<String> = emptySet(),
    val selected: IngredientRef? = null,
)
