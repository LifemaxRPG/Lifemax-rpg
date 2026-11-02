package com.lifemax.rpg.feature.food.data

import com.lifemax.rpg.core.data.objects
import com.lifemax.rpg.feature.food.domain.CreateField
import com.lifemax.rpg.feature.food.domain.GoalField
import com.lifemax.rpg.feature.food.domain.GoalPreset
import com.lifemax.rpg.feature.food.domain.GoalSum
import com.lifemax.rpg.feature.food.domain.SheetCreate
import com.lifemax.rpg.feature.food.domain.SheetEdit
import com.lifemax.rpg.feature.food.domain.SheetGoal
import com.lifemax.rpg.feature.food.domain.SheetQuick
import com.lifemax.rpg.feature.food.domain.SheetWords
import org.json.JSONObject

/*
 * Decoding the sheet's words and its phase 2 forms, strict as FoodSheetJson.kt is:
 *   words  {search, quick, create, searching, back, close, less, more, grams, fav, kcal}
 *   quick  {kcal:{label, hint}, name:{label, small, hint, max}, save}
 *   create {fields:[{key, kind: text|decimal, label, small?, hint?, max?, value?, unit?, group?}], hint, save}
 *   goal   {cal:{label, value, step, min}, presets:[{id, label, sub}], fields:[{key, label, color, value}],
 *           water:{label, value, unit}, sum:{lead, strong, sep, note, bad}, save}
 *   edit   {id, remove, again: text|null}
 * The boxes' values are text, as typed. Keys are unique, so the screen never sends one box for another.
 */

internal fun decodeWords(w: JSONObject) =
    SheetWords(
        search = w.text("search"),
        quick = w.text("quick"),
        create = w.text("create"),
        searching = w.text("searching"),
        back = w.text("back"),
        close = w.text("close"),
        less = w.text("less"),
        more = w.text("more"),
        grams = w.text("grams"),
        fav = w.text("fav"),
        kcal = w.text("kcal"),
    )

internal fun decodeQuick(q: JSONObject): SheetQuick {
    val kcal = q.obj("kcal")
    val name = q.obj("name")
    val max = name.int("max")
    require(max > 0) { "The name's longest is not positive" }
    return SheetQuick(
        kcalLabel = kcal.text("label"),
        kcalHint = kcal.text("hint"),
        nameLabel = name.text("label"),
        nameSmall = name.text("small"),
        nameHint = name.text("hint"),
        nameMax = max,
        save = q.text("save"),
    )
}

internal fun decodeCreate(c: JSONObject): SheetCreate {
    val fields =
        c.list("fields").objects().map { f ->
            val key = f.text("key")
            val decimal =
                when (val kind = f.text("kind")) {
                    "text" -> false
                    "decimal" -> true
                    else -> throw IllegalArgumentException("$key has an unknown kind $kind")
                }
            val max = f.optWhole("max")
            max?.let { require(it > 0) { "$key has a longest that is not positive" } }
            CreateField(
                key = key,
                decimal = decimal,
                label = f.text("label"),
                small = f.optText("small"),
                hint = f.optText("hint"),
                maxLength = max,
                value = f.optText("value") ?: "",
                unit = f.optText("unit"),
                group = f.optWhole("group"),
            )
        }
    unique(fields.map { it.key })
    return SheetCreate(fields, c.text("hint"), c.text("save"))
}

internal fun decodeGoal(g: JSONObject): SheetGoal {
    val cal = g.obj("cal")
    val step = cal.int("step")
    require(step > 0) { "The calories' step is not positive" }
    val water = g.obj("water")
    val sum = g.obj("sum")
    val fields =
        g.list("fields").objects().map {
            GoalField(
                it.text("key"),
                it.text("label"),
                hexColor(it.text("color")),
                it.text("value"),
            )
        }
    unique(fields.map { it.key } + listOf("cal", "water"))
    val presets =
        g.list("presets").objects().map {
            GoalPreset(it.text("id"), it.text("label"), it.text("sub"))
        }
    unique(presets.map { it.id })
    return SheetGoal(
        calLabel = cal.text("label"),
        cal = cal.text("value"),
        calStep = step,
        calMin = cal.int("min"),
        presets = presets,
        fields = fields,
        waterLabel = water.text("label"),
        water = water.text("value"),
        waterUnit = water.text("unit"),
        sum =
            GoalSum(
                sum.text("lead"),
                sum.text("strong"),
                sum.text("sep"),
                sum.text("note"),
                sum.flag("bad"),
            ),
        save = g.text("save"),
    )
}

internal fun decodeEdit(e: JSONObject) =
    SheetEdit(
        id = e.text("id"),
        remove = e.text("remove"),
        // null for today's entry, which the page offers no Log today for; absent is malformed
        again = if (e.has("again") && e.isNull("again")) null else e.text("again"),
    )

private fun unique(keys: List<String>) {
    require(keys.toSet().size == keys.size) { "Two boxes or choices share a key" }
}
