package com.lifemax.rpg.feature.recipes.data

import com.lifemax.rpg.feature.recipes.domain.ALL_RECIPES
import com.lifemax.rpg.feature.recipes.domain.Recipe
import com.lifemax.rpg.feature.recipes.domain.RecipeCategory
import com.lifemax.rpg.feature.recipes.domain.RecipeFilter
import com.lifemax.rpg.feature.recipes.domain.RecipeIngredient
import com.lifemax.rpg.feature.recipes.domain.RecipesProjection
import com.lifemax.rpg.feature.recipes.domain.RecipesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Decodes the page's Recipes projection (50-native-recipes.js). Malformed data is rejected and the
 * last good state kept; an update older than the one held is ignored; a projection from another
 * page session is taken only on an explicit open.
 */
class WebRecipesRepository : RecipesRepository {
    private val mutable = MutableStateFlow<RecipesProjection?>(null)
    override val state = mutable.asStateFlow()

    fun accept(j: JSONObject, establishSession: Boolean = false) {
        require(j.getInt("protocol") == 1) { "Unsupported recipes protocol" }
        val session = j.getString("webSessionId")
        val sequence = j.getLong("sequence")
        require(session.isNotBlank() && sequence > 0)
        val old = mutable.value
        if (old != null && old.session != session && !establishSession) return
        if (old?.session == session && sequence <= old.sequence) return

        val categories =
            j.getJSONArray("categories").objects().map {
                RecipeCategory(it.getString("value"), it.getString("label"))
            }
        val values = categories.map { it.value }
        require(ALL_RECIPES in values && values.size == values.toSet().size) {
            "The category menu needs \"all\" and each category once"
        }
        val f = j.getJSONObject("filter")
        val filter = RecipeFilter(f.getString("query"), f.getString("category"))
        require(filter.category in values) { "The filter's category is not on the menu" }
        val revision = f.getLong("revision")
        require(revision >= 1) { "A filter revision starts at 1" }
        val recipes = j.getJSONArray("recipes").objects().map(::recipe)
        val ids = recipes.map { it.id }
        require(ids.none(String::isBlank) && ids.size == ids.toSet().size) {
            "Each recipe needs an id of its own"
        }
        val colors = j.getJSONObject("tokens")
        mutable.value =
            RecipesProjection(
                session,
                sequence,
                categories,
                filter,
                revision,
                recipes,
                colors.keys().asSequence().associateWith { colors.getLong(it) },
                j.getBoolean("light"),
            )
    }

    private fun recipe(r: JSONObject): Recipe {
        val ingredients =
            r.getJSONArray("ingredients").objects().map {
                RecipeIngredient(it.getString("text"), it.getString("link"))
            }
        require(ingredients.all { it.link.isEmpty() || it.link.startsWith("https://") }) {
            "A product link must be https"
        }
        return Recipe(
            r.getString("id"),
            r.getString("name"),
            r.getString("category"),
            r.getString("meta"),
            r.getString("kcal"),
            r.getString("protein"),
            r.getString("carbs"),
            r.getString("fat"),
            r.getString("footnote"),
            ingredients,
            r.getJSONArray("steps").let { a -> (0 until a.length()).map(a::getString) },
            r.getString("note"),
            r.getString("searchText"),
        )
    }

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
}
