package com.lifemax.rpg.feature.shopping.domain

/**
 * What the page tells the shopping screen (`shopping` in the Food projection, capability
 * [CAPABILITY]). The page owns the week's list, the recipes and the saved product matches; the
 * screen only draws them and sends commands back.
 *
 * [session] identifies the page load and [revision] rises with each projection, so a late older
 * projection is ignored and a new page load is accepted.
 */
data class ShoppingProjection(
    val session: String,
    val revision: Long,
    val weekStart: String,
    val nextWeek: Boolean,
    val weekItems: List<WeekItem>,
    val recipes: List<RecipeSource>,
    val prefs: List<PreferredProduct>,
    val answer: WriteAnswer?,
) {
    companion object {
        /** The capability string the Food plugin advertises when it draws this pane. */
        const val CAPABILITY = "food.shopping.v1"

        /** Bounds shared by the decoder and the page's strict command. */
        const val MAX_WEEK_ITEMS = 300
        const val MAX_RECIPES = 150
        const val MAX_RECIPE_LINES = 40
        const val MAX_PREFS = 200
        const val MAX_KEY = 64
    }
}

/** A row of the Smart Diet week list, already scaled by the page's portions. */
data class WeekItem(
    val id: String,
    val name: String,
    val aisleName: String,
    val grams: Double,
    val packGrams: Double?,
    val packLabel: String,
    val home: Boolean,
) {
    /** The key a preferred product for this ingredient is stored under. */
    val key: String
        get() = KEY_PREFIX + id

    companion object {
        const val KEY_PREFIX = "sd:"
    }
}

/** A recipe of the recipe book and its ingredient lines as written. */
data class RecipeSource(
    val id: String,
    val name: String,
    val servings: Int,
    val lines: List<String>,
)

/** The page's answer to the last write: [ok] only when the save succeeded. */
data class WriteAnswer(val op: String, val ok: Boolean, val changed: Boolean, val message: String)
