package com.lifemax.rpg.feature.shopping.domain

/**
 * A recipe the owner picked for the list. [recipeServings] is what the recipe book's ingredient
 * lines make; [wantedServings] is how many the owner wants, so every line is scaled by the ratio.
 */
data class RecipePick(
    val id: String,
    val name: String,
    val recipeServings: Int,
    val wantedServings: Double,
    val lines: List<String>,
)

/**
 * Builds the weekly grocery list from data the page already owns, never from a second database:
 * - [weekItems] are the Smart Diet shopping list's rows (the page has already scaled them by
 *   portions and servings, so they are used as they are);
 * - [recipes] are recipe-book ingredient lines, scaled here by servings.
 *
 * Identical ingredients become one row with compatible units added up (`1/2 cup oats (40g)` twice
 * is 80 g). Lines that cannot be read are kept in [GroceryList.review], never dropped or guessed.
 * Pantry ("already own") decisions are the view model's, since they are the owner's.
 */
object GroceryListBuilder {
    fun build(weekItems: List<SourceIngredient>, recipes: List<RecipePick>): GroceryList {
        val sources = ArrayList<SourceIngredient>(weekItems)
        val review = mutableListOf<ReviewLine>()
        for (pick in recipes) {
            val scale = scaleOf(pick)
            for (line in pick.lines) {
                when (val parsed = IngredientParser.parse(line)) {
                    is ParsedIngredient.Parsed ->
                        sources +=
                            SourceIngredient(
                                key = parsed.key,
                                name = parsed.name,
                                amount = parsed.amount * scale,
                                aisle = RECIPE_AISLE,
                                origin = pick.name,
                            )
                    ParsedIngredient.Unreadable -> review += ReviewLine(line.trim(), pick.name)
                }
            }
        }
        return GroceryList(consolidate(sources), review)
    }

    /** Servings wanted over servings made; a recipe that says nothing sensible scales by 1. */
    internal fun scaleOf(pick: RecipePick): Double {
        val made = pick.recipeServings.coerceAtLeast(1)
        val wanted = pick.wantedServings
        return if (wanted.isFinite() && wanted > 0.0) wanted / made else 1.0
    }

    /** One row per ingredient key and unit family, in order of first appearance. */
    fun consolidate(sources: List<SourceIngredient>): List<GroceryItem> {
        val rows = LinkedHashMap<String, GroceryItem>()
        for (s in sources) {
            val id = s.key + "#" + s.amount.family.name
            val row = rows[id]
            rows[id] =
                if (row == null) {
                    GroceryItem(
                        key = s.key,
                        name = s.name,
                        amount = s.amount,
                        packSize = s.packSize,
                        packLabel = s.packLabel,
                        aisle = s.aisle,
                        pantry = s.pantry,
                        origins = listOfNotNull(s.origin.ifEmpty { null }),
                    )
                } else {
                    row.copy(
                        amount = row.amount + s.amount,
                        packSize = row.packSize ?: s.packSize,
                        packLabel = row.packLabel.ifEmpty { s.packLabel },
                        pantry = row.pantry && s.pantry,
                        origins = (row.origins + s.origin).filter { it.isNotEmpty() }.distinct(),
                    )
                }
        }
        return rows.values.toList()
    }

    const val RECIPE_AISLE = "Recipes"
}
