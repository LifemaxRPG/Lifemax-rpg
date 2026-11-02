package com.lifemax.rpg.feature.food.data

import com.lifemax.rpg.core.data.objects
import com.lifemax.rpg.feature.food.domain.FoodAnswer
import com.lifemax.rpg.feature.food.domain.FoodNotice
import com.lifemax.rpg.feature.food.domain.FoodSheet
import com.lifemax.rpg.feature.food.domain.SheetEmpty
import com.lifemax.rpg.feature.food.domain.SheetImpact
import com.lifemax.rpg.feature.food.domain.SheetMultiple
import com.lifemax.rpg.feature.food.domain.SheetPortion
import com.lifemax.rpg.feature.food.domain.SheetRow
import com.lifemax.rpg.feature.food.domain.SheetServing
import com.lifemax.rpg.feature.food.domain.SheetSlot
import com.lifemax.rpg.feature.food.domain.SheetView
import org.json.JSONArray
import org.json.JSONObject

/*
 * Decoding NativeNutrition's `sheet`, `notice` and `answer` fields. A field that is missing or of
 * the wrong kind is malformed and throws (a number is a JSON number, never text, and a count is a
 * whole one), so a sheet is never drawn from a half-understood projection. The phase 2 forms are
 * decoded in FoodSheetFormsJson.kt.
 */

/** The add-food sheet of a projection: null when the page says none is open. */
internal fun decodeSheet(data: JSONObject): FoodSheet? {
    if (data.isNull("sheet")) return null
    val s = data.obj("sheet")
    val view = SheetView.of(s.text("view")) ?: throw IllegalArgumentException("Unknown sheet view")
    val slots = s.options("slots")
    val tabs = s.options("tabs")
    val slot = s.text("slot")
    // an entry keeps the meal it was logged to, even one the pills do not offer
    require(view == SheetView.Edit || slots.any { it.id == slot }) {
        "The sheet's meal is not one of its meals"
    }
    val tab = s.text("tab")
    require(tabs.any { it.id == tab }) { "The sheet's tab is not one of its tabs" }
    val portion = s.optObj("portion")?.let(::decodePortion)
    val showsFood = view == SheetView.Portion || view == SheetView.Edit
    require(showsFood == (portion != null)) { "A portion or edit view needs its food" }
    require((view == SheetView.Edit) == (portion?.edit != null)) {
        "Only the edit view has an entry"
    }
    val quick = s.optObj("quick")?.let(::decodeQuick)
    require((view == SheetView.Quick) == (quick != null)) { "Quick calories needs its words" }
    val create = s.optObj("create")?.let(::decodeCreate)
    require((view == SheetView.Create) == (create != null)) { "Create food needs its form" }
    val goal = s.optObj("goal")?.let(::decodeGoal)
    require((view == SheetView.Goal) == (goal != null)) { "The daily targets need their boxes" }
    return FoodSheet(
        view = view,
        title = s.text("title"),
        back = s.flag("back"),
        slot = slot,
        slots = slots,
        query = s.text("query"),
        tab = tab,
        tabs = tabs,
        words = decodeWords(s.obj("words")),
        searching = s.flag("searching"),
        rows = s.list("rows").objects().map(::decodeRow),
        empty = s.optObj("empty")?.let { SheetEmpty(it.text("title"), it.text("text")) },
        portion = portion,
        quick = quick,
        create = create,
        goal = goal,
    )
}

/** The page's message of a projection: null when it has none. */
internal fun decodeNotice(data: JSONObject): FoodNotice? {
    if (data.isNull("notice")) return null
    val n = data.obj("notice")
    return FoodNotice(n.int("id"), n.text("text"), n.text("action"))
}

/** The page's answer to the screen's last write: null before the first, or from an older page. */
internal fun decodeAnswer(data: JSONObject): FoodAnswer? {
    if (data.isNull("answer")) return null
    val a = data.obj("answer")
    return FoodAnswer(a.text("op"), a.flag("ok"), a.flag("changed"), a.text("message"))
}

private fun JSONObject.options(name: String): List<SheetSlot> =
    list(name).objects().map { SheetSlot(it.text("id"), it.text("label")) }

private fun decodeRow(r: JSONObject) =
    SheetRow(
        index = r.int("index"),
        id = r.text("id"),
        name = r.text("name"),
        detail = r.text("detail"),
        grams = r.num("grams"),
        kcal = r.int("kcal"),
        protein = r.int("p"),
        carbs = r.int("c"),
        fat = r.int("f"),
    )

private fun decodePortion(p: JSONObject): SheetPortion {
    val small = p.obj("small")
    return SheetPortion(
        name = p.text("name"),
        sub = p.text("sub"),
        favorite = p.flag("fav"),
        grams = p.num("grams"),
        kcal = p.int("kcal"),
        fiber = small.num("fib"),
        sugar = small.num("sug"),
        sodium = small.int("na"),
        servings =
            p.list("servings").objects().map {
                SheetServing(it.text("label"), it.num("grams"), it.flag("on"), it.flag("small"))
            },
        multiples = p.list("mults").objects().map { SheetMultiple(it.num("x"), it.text("label")) },
        impact = p.list("impact").objects().map(::decodeImpact),
        logLabel = p.text("logLabel"),
        edit = p.optObj("edit")?.let(::decodeEdit),
    )
}

private fun decodeImpact(i: JSONObject) =
    SheetImpact(
        key = i.text("k"),
        name = i.text("name"),
        color = hexColor(i.text("color")),
        before = (i.num("before") / 100.0).toFloat().coerceIn(0f, 1f),
        add = (i.num("add") / 100.0).toFloat().coerceIn(0f, 1f),
        label = i.text("label"),
    )

/** `#rrggbb` as opaque ARGB. */
internal fun hexColor(hex: String): Long {
    require(Regex("#[0-9a-fA-F]{6}").matches(hex)) { "Not a colour: $hex" }
    return 0xFF000000L or hex.substring(1).toLong(16)
}

/*
 * The strict readers every Food sheet decoder uses. An optional field ([optObj], [optText],
 * [optWhole]) may be absent or null; present, it must be of its kind.
 */

internal fun JSONObject.text(name: String): String =
    opt(name) as? String ?: throw IllegalArgumentException("$name is not text")

internal fun JSONObject.flag(name: String): Boolean =
    opt(name) as? Boolean ?: throw IllegalArgumentException("$name is not true or false")

/** A JSON number (text that looks like one is malformed). */
internal fun JSONObject.num(name: String): Double =
    (opt(name) as? Number)?.toDouble()?.takeIf { it.isFinite() }
        ?: throw IllegalArgumentException("$name is not a number")

/** A whole number: 3.5 or "3" is malformed. */
internal fun JSONObject.int(name: String): Int {
    val d = num(name)
    require(d == Math.rint(d) && kotlin.math.abs(d) < 1e9) { "$name is not a whole number" }
    return d.toInt()
}

internal fun JSONObject.obj(name: String): JSONObject =
    opt(name) as? JSONObject ?: throw IllegalArgumentException("$name is not an object")

internal fun JSONObject.list(name: String): JSONArray =
    opt(name) as? JSONArray ?: throw IllegalArgumentException("$name is not a list")

private fun JSONObject.present(name: String) = has(name) && !isNull(name)

internal fun JSONObject.optObj(name: String): JSONObject? =
    if (has(name) && isNull(name)) null else obj(name)

internal fun JSONObject.optText(name: String): String? = if (present(name)) text(name) else null

internal fun JSONObject.optWhole(name: String): Int? = if (present(name)) int(name) else null
