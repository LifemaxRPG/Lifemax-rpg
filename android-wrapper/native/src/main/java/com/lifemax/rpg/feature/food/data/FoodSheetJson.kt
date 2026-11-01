package com.lifemax.rpg.feature.food.data

import com.lifemax.rpg.core.data.objects
import com.lifemax.rpg.feature.food.domain.FoodNotice
import com.lifemax.rpg.feature.food.domain.FoodSheet
import com.lifemax.rpg.feature.food.domain.SheetEmpty
import com.lifemax.rpg.feature.food.domain.SheetImpact
import com.lifemax.rpg.feature.food.domain.SheetMultiple
import com.lifemax.rpg.feature.food.domain.SheetPortion
import com.lifemax.rpg.feature.food.domain.SheetRow
import com.lifemax.rpg.feature.food.domain.SheetServing
import com.lifemax.rpg.feature.food.domain.SheetSlot
import com.lifemax.rpg.feature.food.domain.SheetTab
import com.lifemax.rpg.feature.food.domain.SheetView
import org.json.JSONObject

/*
 * Decoding NativeNutrition's `sheet` and `notice` fields. A field that is missing or of the wrong
 * kind is malformed and throws, so a sheet is never drawn from a half-understood projection.
 */

/** The add-food sheet of a projection: null when the page says none is open. */
internal fun decodeSheet(data: JSONObject): FoodSheet? {
    if (data.isNull("sheet")) return null
    val s = data.getJSONObject("sheet")
    val view = SheetView.of(s.text("view")) ?: throw IllegalArgumentException("Unknown sheet view")
    val tab = SheetTab.of(s.text("tab")) ?: throw IllegalArgumentException("Unknown sheet tab")
    val slots = s.getJSONArray("slots").objects().map { SheetSlot(it.text("id"), it.text("label")) }
    val slot = s.text("slot")
    require(slots.any { it.id == slot }) { "The sheet's meal is not one of its meals" }
    val portion = if (s.isNull("portion")) null else decodePortion(s.getJSONObject("portion"))
    require((view == SheetView.Portion) == (portion != null)) { "A portion view needs its portion" }
    return FoodSheet(
        view = view,
        slot = slot,
        slots = slots,
        query = s.text("query"),
        tab = tab,
        searching = s.flag("searching"),
        rows = s.getJSONArray("rows").objects().map(::decodeRow),
        empty =
            if (s.isNull("empty")) null
            else s.getJSONObject("empty").let { SheetEmpty(it.text("title"), it.text("text")) },
        portion = portion,
    )
}

/** The page's message of a projection: null when it has none. */
internal fun decodeNotice(data: JSONObject): FoodNotice? {
    if (data.isNull("notice")) return null
    val n = data.getJSONObject("notice")
    return FoodNotice(n.getInt("id"), n.text("text"), n.text("action"))
}

private fun decodeRow(r: JSONObject) =
    SheetRow(
        index = r.getInt("index"),
        id = r.text("id"),
        name = r.text("name"),
        detail = r.text("detail"),
        grams = r.getDouble("grams"),
        kcal = r.getInt("kcal"),
        protein = r.getInt("p"),
        carbs = r.getInt("c"),
        fat = r.getInt("f"),
    )

private fun decodePortion(p: JSONObject): SheetPortion {
    val small = p.getJSONObject("small")
    return SheetPortion(
        name = p.text("name"),
        sub = p.text("sub"),
        favorite = p.flag("fav"),
        grams = p.getDouble("grams"),
        kcal = p.getInt("kcal"),
        fiber = small.getDouble("fib"),
        sugar = small.getDouble("sug"),
        sodium = small.getInt("na"),
        servings =
            p.getJSONArray("servings").objects().map {
                SheetServing(
                    it.text("label"),
                    it.getDouble("grams"),
                    it.flag("on"),
                    it.flag("small"),
                )
            },
        multiples =
            p.getJSONArray("mults").objects().map {
                SheetMultiple(it.getDouble("x"), it.text("label"))
            },
        impact = p.getJSONArray("impact").objects().map(::decodeImpact),
        logLabel = p.text("logLabel"),
    )
}

private fun decodeImpact(i: JSONObject) =
    SheetImpact(
        key = i.text("k"),
        name = i.text("name"),
        color = hexColor(i.text("color")),
        before = (i.getDouble("before") / 100.0).toFloat().coerceIn(0f, 1f),
        add = (i.getDouble("add") / 100.0).toFloat().coerceIn(0f, 1f),
        label = i.text("label"),
    )

/** `#rrggbb` as opaque ARGB. */
private fun hexColor(hex: String): Long {
    require(Regex("#[0-9a-fA-F]{6}").matches(hex)) { "Not a colour: $hex" }
    return 0xFF000000L or hex.substring(1).toLong(16)
}

private fun JSONObject.text(name: String): String =
    opt(name) as? String ?: throw IllegalArgumentException("$name is not text")

private fun JSONObject.flag(name: String): Boolean =
    opt(name) as? Boolean ?: throw IllegalArgumentException("$name is not true or false")
