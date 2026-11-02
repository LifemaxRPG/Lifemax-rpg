package com.lifemax.rpg.feature.food.domain

/*
 * The add-food sheet's words and its phase 2 forms, as the page describes them (fv2SheetWords,
 * fv2QuickData, fv2CreateData, fv2GoalView in 50-food-forms.js). Nothing here is computed: the line
 * under the targets, a preset's grams and the edit view's numbers all come from the page.
 */

/** The sheet's own words: the search box, the side actions, and what TalkBack names. */
internal data class SheetWords(
    val search: String,
    val quick: String,
    val create: String,
    val searching: String,
    val back: String,
    val close: String,
    val less: String,
    val more: String,
    val grams: String,
    val fav: String,
    val kcal: String,
)

/** Quick calories: its two boxes (labels, hints, the name's longest) and its button. */
internal data class SheetQuick(
    val kcalLabel: String,
    val kcalHint: String,
    val nameLabel: String,
    val nameSmall: String,
    val nameHint: String,
    val nameMax: Int,
    val save: String,
)

/** Create food: the label's boxes in the page's order, the hint under them and the button. */
internal data class SheetCreate(val fields: List<CreateField>, val hint: String, val save: String)

/**
 * A box of Create food. [decimal] boxes take a number with a point; [value] is what it starts with;
 * [unit] shows beside it; boxes of one [group] share the page's grid of two columns.
 */
internal data class CreateField(
    val key: String,
    val decimal: Boolean,
    val label: String,
    val small: String?,
    val hint: String?,
    val maxLength: Int?,
    val value: String,
    val unit: String?,
    val group: Int?,
)

/**
 * The daily targets: the calorie box with its step, the presets, the three macros, water, the line
 * under the macros and the button. The values are the boxes' text as the page holds it.
 */
internal data class SheetGoal(
    val calLabel: String,
    val cal: String,
    val calStep: Int,
    val calMin: Int,
    val presets: List<GoalPreset>,
    val fields: List<GoalField>,
    val waterLabel: String,
    val water: String,
    val waterUnit: String,
    val sum: GoalSum,
    val save: String,
)

internal data class GoalPreset(val id: String, val label: String, val sub: String)

internal data class GoalField(
    val key: String,
    val label: String,
    val color: Long,
    val value: String,
)

/**
 * "Your macros add up to **2,497 kcal** · matches your target": [lead], the bold [strong], then
 * [sep] and [note] (in the warning colour when [bad]).
 */
internal data class GoalSum(
    val lead: String,
    val strong: String,
    val sep: String,
    val note: String,
    val bad: Boolean,
)

/** The entry the edit view changes, and its two side buttons ([again] only for another day's). */
internal data class SheetEdit(val id: String, val remove: String, val again: String?)

/**
 * The page's answer to the screen's last write: its operation id, whether it was saved, whether
 * anything changed (a repeat is saved but changes nothing) and the page's words, if any.
 */
internal data class FoodAnswer(
    val op: String,
    val ok: Boolean,
    val changed: Boolean,
    val message: String,
)
