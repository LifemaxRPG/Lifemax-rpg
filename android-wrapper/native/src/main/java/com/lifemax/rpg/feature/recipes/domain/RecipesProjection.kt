package com.lifemax.rpg.feature.recipes.domain

/** A choice of the page's category menu; [value] is what a recipe's category is compared with. */
data class RecipeCategory(val value: String, val label: String)

/** An ingredient line and its product link, which is empty or an https address. */
data class RecipeIngredient(val text: String, val link: String)

/** A recipe, worded as the page words its card. */
data class Recipe(
    val id: String,
    val name: String,
    val category: String,
    /** Yield, prep and total time: "2 servings · Prep 5 min · Total 35 min". */
    val meta: String,
    /** "350 kcal", or "430 kcal (est.)" for a class recipe. */
    val kcal: String,
    val protein: String,
    val carbs: String,
    val fat: String,
    val footnote: String,
    val ingredients: List<RecipeIngredient>,
    val steps: List<String>,
    /** Empty when the recipe has none. */
    val note: String,
    /** What the page's search looks in: the name, category and ingredients, lowercased. */
    val searchText: String,
)

/**
 * The page's filter (recipeHits): a recipe shows when it is in [category], or [category] is "all",
 * and its search text holds [query] as one phrase, trimmed and lowercased as the page does.
 */
data class RecipeFilter(val query: String = "", val category: String = ALL_RECIPES) {
    fun hits(recipes: List<Recipe>): List<Recipe> {
        val phrase = query.trim(::isJsSpace).lowercase()
        return recipes.filter {
            (category == ALL_RECIPES || it.category == category) && phrase in it.searchText
        }
    }
}

/** The category value that shows every recipe. */
const val ALL_RECIPES = "all"

/** What JavaScript's trim() removes: Kotlin's whitespace but U+001C to U+001F, and U+FEFF. */
private fun isJsSpace(c: Char) = c == '﻿' || (c.isWhitespace() && c !in '\u001C'..'\u001F')

data class RecipesProjection(
    val session: String,
    val sequence: Long,
    val categories: List<RecipeCategory>,
    /** Where the screen starts; [filterRevision] rises whenever the page sets it (a prefill). */
    val filter: RecipeFilter,
    val filterRevision: Long,
    /** The recipes the page shows for the player's class, in its order. */
    val recipes: List<Recipe>,
    val tokens: Map<String, Long>,
    val light: Boolean,
)
