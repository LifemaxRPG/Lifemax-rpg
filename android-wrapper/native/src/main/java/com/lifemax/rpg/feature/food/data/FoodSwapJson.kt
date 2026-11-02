package com.lifemax.rpg.feature.food.data

import com.lifemax.rpg.core.data.objects
import com.lifemax.rpg.feature.food.domain.FoodSwap
import com.lifemax.rpg.feature.food.domain.SwapRow
import org.json.JSONObject

/*
 * Decoding NativeNutrition's `swap` field, as strictly as the add-food sheet's: a field that is
 * missing or of the wrong kind throws, so a sheet is never drawn from half a projection.
 */

/** Whether the page can show the swap sheet (an older page has no field, and Swap hands over). */
internal fun JSONObject.hasSwap(): Boolean = has("swap")

/** The swap sheet of a projection: null when the page says none is open. */
internal fun decodeSwap(data: JSONObject): FoodSwap? {
    if (data.isNull("swap")) return null
    val s = data.obj("swap")
    val rows =
        s.list("rows").objects().map {
            SwapRow(
                it.text("id"),
                it.text("name"),
                it.text("detail"),
                it.text("kcal"),
                it.text("unit"),
            )
        }
    val none = s.optText("none")
    require(rows.none { it.id.isBlank() } && rows.map { it.id }.toSet().size == rows.size) {
        "Each meal on offer needs an id of its own"
    }
    require((none == null) == rows.isNotEmpty()) { "An empty list needs its words, a full one not" }
    return FoodSwap(
        index = s.int("index"),
        title = s.text("title"),
        lead = s.text("lead"),
        name = s.text("name"),
        tail = s.text("tail"),
        rows = rows,
        none = none,
        reset = s.optText("reset"),
        close = s.text("close"),
    )
}
