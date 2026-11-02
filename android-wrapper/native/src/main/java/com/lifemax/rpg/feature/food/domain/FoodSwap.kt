package com.lifemax.rpg.feature.food.domain

/**
 * The Smart Diet swap sheet as the page describes it (NativeNutrition's `swap` field, read by the
 * page from the same helper its own sheet is drawn from): the words, the other meals of the same
 * kind in the page's order, and the way back to the plan. Nothing here is decided by the screen.
 */
internal data class FoodSwap(
    /** the plan's meal the sheet is for: what a tap sends back */
    val index: Int,
    val title: String,
    /** the note is [lead], the meal's [name] in bold, then [tail] */
    val lead: String,
    val name: String,
    val tail: String,
    val rows: List<SwapRow>,
    /** what an empty list says; null when there are meals to offer */
    val none: String?,
    /** the label of the button that undoes an earlier swap; null while the meal is as planned */
    val reset: String?,
    /** the page's word for the head's close button */
    val close: String,
)

/**
 * A meal on offer: its words and the kcal it comes to in the day's plan, as the page writes them.
 */
internal data class SwapRow(
    val id: String,
    val name: String,
    val detail: String,
    val kcal: String,
    val unit: String,
)
