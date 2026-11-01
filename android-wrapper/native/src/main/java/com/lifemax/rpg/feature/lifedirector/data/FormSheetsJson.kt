package com.lifemax.rpg.feature.lifedirector.data

import com.lifemax.rpg.core.data.objects
import com.lifemax.rpg.feature.lifedirector.domain.FormButton
import com.lifemax.rpg.feature.lifedirector.domain.GoalFormSheetModel
import com.lifemax.rpg.feature.lifedirector.domain.GoalTypeOption
import com.lifemax.rpg.feature.lifedirector.domain.GoalTypeSheetModel
import com.lifemax.rpg.feature.lifedirector.domain.RuleSheetModel
import org.json.JSONObject

/*
 * My time's sheets of the projection (phase 4B), strict like the rest: a missing or wrong-kind field, a button sending
 * another command than its sheet's, Remove on a new commitment, or a grid without its "Something else" throws, and the
 * projection is refused. A commitment the page no longer finds ({kind, id, gone: true}) is read by SheetsJson.kt.
 */

/**
 * The words the page refuses an empty required field with, by key, as its Do functions say them
 * (ruleSave and goalAdd in logic.js). The schema says only that a field is required (formField's
 * required: true); these say why, so the screen can say it before sending, in the page's words. A
 * word changed there must change here; the page's own refusal after Save stays the authority either
 * way.
 */
internal object FormWords {
    val RULE =
        mapOf(
            "days" to "Pick at least one day",
            "anchor" to "Pick the first day on",
            "start" to "Enter start and end times",
            "end" to "Enter start and end times",
        )
    val GOAL = mapOf("title" to "Give the goal a name")
}

/** The commitment sheet (ruleForm): a new one (id null) has no Remove; an edit has it. */
internal fun ruleSheet(s: JSONObject): RuleSheetModel {
    val id =
        if (s.nul("id")) null
        else s.txt("id").also { require(it.isNotEmpty()) { "A commitment without an id" } }
    val remove = s.buttonOrNull("remove", "ruleRemove")
    require((id != null) == (remove != null)) { "Remove belongs to an edit, and an edit has it" }
    return RuleSheetModel(
        ruleId = id,
        title = s.txt("title"),
        schema = s.formSchema(FormWords.RULE),
        note = s.txt("note"),
        save = s.button("save", "ruleSave"),
        cancel = s.txt("cancel"),
        remove = remove,
    )
}

/** A goal form (goalFormModel) for the kind of goal its id names. */
internal fun goalFormSheet(s: JSONObject): GoalFormSheetModel {
    require(s.nul("remove")) { "A new goal has nothing to remove" }
    return GoalFormSheetModel(
        arch = s.txt("id").also { require(it.isNotEmpty()) { "A goal form without its kind" } },
        title = s.txt("title"),
        schema = s.formSchema(FormWords.GOAL),
        note = s.txt("note"),
        save = s.button("save", "goalAdd"),
        cancel = s.txt("cancel"),
    )
}

/** "Add a goal" (goalTypeModel): the kinds, each once, the last your own (CUSTOM). */
internal fun goalTypeSheet(s: JSONObject): GoalTypeSheetModel {
    val options =
        s.getJSONArray("options").objects().map {
            GoalTypeOption(
                it.txt("arch").also { a ->
                    require(a.isNotEmpty()) { "A kind of goal without its name" }
                },
                it.txt("icon"),
                it.txt("title"),
                it.txt("sub"),
            )
        }
    require(options.lastOrNull()?.arch == CUSTOM) { "The grid ends with Something else" }
    require(options.map { it.arch }.toSet().size == options.size) { "A kind of goal twice" }
    return GoalTypeSheetModel(s.txt("title"), s.txt("text"), options)
}

private const val CUSTOM = "CUSTOM"

private fun JSONObject.button(key: String, command: String): FormButton {
    val b = get(key) as? JSONObject ?: throw IllegalArgumentException("$key is not a button")
    val c = b.txt("command")
    require(c == command) { "$key sends $c, not $command" }
    return FormButton(b.txt("label"), c)
}

private fun JSONObject.buttonOrNull(key: String, command: String): FormButton? =
    if (nul(key)) null else button(key, command)

private fun JSONObject.txt(key: String): String =
    get(key) as? String ?: throw IllegalArgumentException("$key is not text")

/** Whether [key] is there and null: a missing key is malformed, not none. */
private fun JSONObject.nul(key: String): Boolean = get(key) == JSONObject.NULL
