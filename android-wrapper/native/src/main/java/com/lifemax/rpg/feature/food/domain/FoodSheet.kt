package com.lifemax.rpg.feature.food.domain

/**
 * The add-food sheet as the page describes it (NativeNutrition's `sheet` field): what to draw,
 * nothing to compute.
 */
internal data class FoodSheet(
    val view: SheetView,
    val slot: String,
    val slots: List<SheetSlot>,
    val query: String,
    val tab: SheetTab,
    val searching: Boolean,
    val rows: List<SheetRow>,
    val empty: SheetEmpty?,
    val portion: SheetPortion?,
)

internal enum class SheetView(val wire: String) {
    Search("search"),
    Portion("portion"),
    Quick("quick");

    companion object {
        fun of(wire: String) = entries.firstOrNull { it.wire == wire }
    }
}

internal enum class SheetTab(val wire: String, val label: String) {
    Recent("recent", "Recent"),
    Favorites("fav", "Favorites"),
    Mine("mine", "My foods");

    companion object {
        fun of(wire: String) = entries.firstOrNull { it.wire == wire }
    }
}

/** A meal the food is logged to. */
internal data class SheetSlot(val id: String, val label: String)

/**
 * A food in the list: the words the page gives it and what it comes to at [grams] (what a tap on
 * its + logs).
 */
internal data class SheetRow(
    val index: Int,
    val id: String,
    val name: String,
    val detail: String,
    val grams: Double,
    val kcal: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int,
)

/** What an empty list says (a search with no match, or a tab with nothing in it). */
internal data class SheetEmpty(val title: String, val text: String)

/**
 * A food at an amount: its numbers, its serving and multiple buttons, and what it adds to the day.
 */
internal data class SheetPortion(
    val name: String,
    val sub: String,
    val favorite: Boolean,
    val grams: Double,
    val kcal: Int,
    val fiber: Double,
    val sugar: Double,
    val sodium: Int,
    val servings: List<SheetServing>,
    val multiples: List<SheetMultiple>,
    val impact: List<SheetImpact>,
    val logLabel: String,
)

internal data class SheetServing(
    val label: String,
    val grams: Double,
    val selected: Boolean,
    /** whether the button shows its grams under the words */
    val showsGrams: Boolean,
)

internal data class SheetMultiple(val factor: Double, val label: String)

/**
 * One macro's bar: how much of the day's target is already eaten ([before]) and how much this
 * amount adds ([add]), both as a share of the bar (together at most 1), and the words under it.
 */
internal data class SheetImpact(
    val key: String,
    val name: String,
    val color: Long,
    val before: Float,
    val add: Float,
    val label: String,
)

/**
 * The page's last message to the player, shown once per [id]; [action] (Undo) names its button, if
 * any.
 */
internal data class FoodNotice(val id: Int, val text: String, val action: String)
