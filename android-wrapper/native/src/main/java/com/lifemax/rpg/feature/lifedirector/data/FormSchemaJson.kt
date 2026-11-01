package com.lifemax.rpg.feature.lifedirector.data

import com.lifemax.rpg.core.data.objects
import com.lifemax.rpg.core.designsystem.form.FieldKind
import com.lifemax.rpg.core.designsystem.form.FieldValue
import com.lifemax.rpg.core.designsystem.form.FormField
import com.lifemax.rpg.core.designsystem.form.FormFold
import com.lifemax.rpg.core.designsystem.form.FormOption
import com.lifemax.rpg.core.designsystem.form.FormSchema
import org.json.JSONArray
import org.json.JSONObject

/*
 * The form schema of the rule, goal-type and goal-form sheets (plan §3.4), strict like every projection decoder:
 *   {fields:[{key, kind, label, value, min?, max?, step?, maxLength?, placeholder?, options?:[{value,label}],
 *             reveals?:[key], hides?:[key], group?, required?, note?}], fold?:{label, fields}}
 * kind is text | number | time | date | select | check | days ("choice" is read as select). An optional key may be
 * absent or null; present, it must be of its kind. A value must suit its kind (A18: a time is minutes 0..1439, a date
 * "YYYY-MM-DD" or "" for none), a select's value one of its options, days the numbers 0..6 among its options, a
 * number within its limits (null only in a box with a placeholder), and every key unique, so a form the screen
 * could send wrongly is refused. The sheet's buttons (save, cancel, remove, help) belong to the sheet's decoder.
 * Kept in one file so it is easy to align with the page's schema.
 */

internal fun JSONObject.formSchema(): FormSchema {
    val fields = getJSONArray("fields").fields()
    val fold =
        optObject("fold")?.let { FormFold(it.text("label"), it.getJSONArray("fields").fields()) }
    val all = fields + fold?.fields.orEmpty()
    val keys = all.map { it.key }
    require(keys.toSet().size == keys.size) { "A form has two fields with one key" }
    all.forEach { f ->
        require((f.reveals + f.hides).all { it in keys && it != f.key }) {
            "${f.key} reveals or hides a field the form does not have"
        }
    }
    return FormSchema(fields, fold)
}

private fun JSONArray.fields(): List<FormField> = objects().map { it.field() }

private fun JSONObject.field(): FormField {
    val key = text("key")
    val kind =
        when (val k = text("kind")) {
            "text" -> FieldKind.TEXT
            "number" -> FieldKind.NUMBER
            "time" -> FieldKind.TIME
            "date" -> FieldKind.DATE
            "select",
            "choice" -> FieldKind.CHOICE
            "check" -> FieldKind.CHECK
            "days" -> FieldKind.DAYS
            else -> throw IllegalArgumentException("$key has an unknown kind $k")
        }
    val options =
        optArray("options")?.objects()?.map { o ->
            if (kind == FieldKind.DAYS) {
                val day = o.int("value")
                require(day in 0..6) { "$key offers a day outside 0..6" }
                FormOption(day.toString(), o.text("label"))
            } else FormOption(o.text("value"), o.text("label"))
        } ?: emptyList()
    val min = intOrNull("min")
    val max = intOrNull("max")
    if (min != null && max != null) require(min <= max) { "$key has min above max" }
    val step = intOrNull("step")
    step?.let { require(it > 0) { "$key has a step that is not positive" } }
    val maxLength = intOrNull("maxLength")
    maxLength?.let { require(it > 0) { "$key has a maxLength that is not positive" } }
    val placeholder = optText("placeholder")
    val reveals = optArray("reveals")?.texts() ?: emptyList()
    val hides = optArray("hides")?.texts() ?: emptyList()
    require(kind == FieldKind.CHECK || (reveals.isEmpty() && hides.isEmpty())) {
        "$key reveals or hides fields but is not a check"
    }
    require((kind == FieldKind.CHOICE || kind == FieldKind.DAYS) == options.isNotEmpty()) {
        "$key has options it cannot show, or none to choose from"
    }
    val value =
        when (kind) {
            FieldKind.TEXT -> FieldValue.Text(text("value"))
            FieldKind.NUMBER -> {
                val n = if (isNull("value")) null else int("value")
                require(n != null || placeholder != null) {
                    "$key has no number and no placeholder"
                }
                n?.let {
                    require((min == null || it >= min) && (max == null || it <= max)) {
                        "$key is outside its limits"
                    }
                }
                FieldValue.Number(n)
            }
            FieldKind.TIME ->
                FieldValue.Time(
                    int("value").also { require(it in 0..1439) { "$key is not a time of day" } }
                )
            FieldKind.DATE ->
                FieldValue.Date(
                    text("value").also {
                        require(it.isEmpty() || DATE.matches(it)) { "$key is not a date" }
                    }
                )
            FieldKind.CHOICE ->
                FieldValue.Choice(
                    text("value").also { v ->
                        require(options.any { it.value == v }) { "$key's value is not offered" }
                    }
                )
            FieldKind.CHECK -> FieldValue.Check(bool("value"))
            FieldKind.DAYS -> {
                val list = getJSONArray("value")
                val days = (0 until list.length()).map { list.int(it, key) }
                require(days.all { d -> options.any { it.value == d.toString() } }) {
                    "$key has a day it does not offer"
                }
                require(days.toSet().size == days.size) { "$key has a day twice" }
                FieldValue.Days(days.toSet())
            }
        }
    return FormField(
        key = key,
        kind = kind,
        label = text("label"),
        value = value,
        min = min,
        max = max,
        step = step,
        maxLength = maxLength,
        placeholder = placeholder,
        options = options,
        reveals = reveals,
        hides = hides,
        group = optText("group"),
        required = optText("required"),
        note = optText("note"),
    )
}

private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")

private fun JSONObject.present(key: String) = has(key) && !isNull(key)

private fun JSONObject.text(key: String): String =
    get(key) as? String ?: throw IllegalArgumentException("$key is not text")

private fun JSONObject.optText(key: String): String? = if (present(key)) text(key) else null

private fun JSONObject.bool(key: String): Boolean =
    get(key) as? Boolean ?: throw IllegalArgumentException("$key is not true or false")

/** A whole number; 3.5 or "3" is malformed, as A18 has every time and count a whole number. */
private fun JSONObject.int(key: String): Int = wholeNumber(get(key), key)

private fun JSONObject.intOrNull(key: String): Int? = if (present(key)) int(key) else null

private fun JSONArray.int(i: Int, key: String): Int = wholeNumber(get(i), key)

private fun wholeNumber(v: Any, key: String): Int =
    when (v) {
        is Int -> v
        is Long -> if (v in Int.MIN_VALUE..Int.MAX_VALUE) v.toInt() else null
        is Number ->
            v.toDouble().takeIf { it == Math.rint(it) && kotlin.math.abs(it) < 1e9 }?.toInt()
        else -> null
    } ?: throw IllegalArgumentException("$key is not a whole number")

private fun JSONObject.optArray(key: String): JSONArray? =
    if (present(key)) get(key) as? JSONArray ?: throw IllegalArgumentException("$key is not a list")
    else null

private fun JSONObject.optObject(key: String): JSONObject? =
    if (present(key))
        get(key) as? JSONObject ?: throw IllegalArgumentException("$key is not an object")
    else null

private fun JSONArray.texts(): List<String> =
    (0 until length()).map { get(it) as? String ?: throw IllegalArgumentException("not text") }
