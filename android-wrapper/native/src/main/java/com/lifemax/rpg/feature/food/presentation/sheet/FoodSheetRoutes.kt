package com.lifemax.rpg.feature.food.presentation.sheet

import com.lifemax.rpg.feature.food.presentation.FoodContext

/*
 * Where Food's buttons open the add-food sheet: the native sheet when the page offers it, else the
 * page's own sheet (the screen steps aside), so an older page loses nothing. A page that has the
 * sheet but not its forms (phase 1) still hands the targets and an entry over.
 */

/** Whether the page runs create, the targets and the edit view inside the native sheet. */
internal fun FoodContext.hasSheetForms(): Boolean = hasSheet() && data.optBoolean("sheetForms")

/** Add food to [slot] (the search dock, a meal's + and its empty "Add …"). */
internal fun FoodContext.openAddFood(slot: String) {
    if (hasSheet()) dispatch("sheetOpen", mapOf("slot" to slot, "forms" to true))
    else dispatch("add", mapOf("slot" to slot))
}

/** The daily targets (Targets, and the dial's middle). */
internal fun FoodContext.openTargets() {
    if (hasSheetForms()) dispatch("sheetGoal", emptyMap()) else dispatch("goals", emptyMap())
}

/** A logged entry, to change, remove or log again. */
internal fun FoodContext.openEntry(id: String) {
    if (hasSheetForms()) dispatch("sheetEntry", mapOf("id" to id))
    else dispatch("entry", mapOf("id" to id))
}
