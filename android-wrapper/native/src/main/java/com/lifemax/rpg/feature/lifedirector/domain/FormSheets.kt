package com.lifemax.rpg.feature.lifedirector.domain

import com.lifemax.rpg.core.designsystem.form.FormSchema

/*
 * My time's sheets (phase 4B), as the page projects them for `sheet {kind: rule | goalType | goalForm}`: the commitment
 * sheet and the goal form are form schemas with the page's buttons (ruleForm, goalFormModel in logic.js), the goal-kind
 * grid is its list of kinds (goalTypeModel). The page draws the same models itself, so both drawings read one decision.
 */

/** The kinds of My time's sheets for the `sheet` command (they need director.setup.v1). */
object FormSheetKinds {
    const val RULE = "rule"
    const val GOAL_TYPE = "goalType"
    const val GOAL_FORM = "goalForm"
}

/**
 * A sheet button that writes: its words and the `do` command it sends (ruleSave, goalAdd,
 * ruleRemove).
 */
data class FormButton(val label: String, val command: String)

/**
 * A form sheet: its [title], the page's [schema], the muted [note] under the form, Save, Cancel and
 * (editing a commitment) Remove.
 */
sealed interface FormSheetModel : SheetModel {
    val title: String
    val schema: FormSchema
    val note: String
    val save: FormButton
    val cancel: String
    val remove: FormButton?
}

/**
 * The commitment sheet: a new commitment ([ruleId] null, asked for with id "") or an edit of
 * [ruleId], which alone has Remove.
 */
data class RuleSheetModel(
    val ruleId: String?,
    override val title: String,
    override val schema: FormSchema,
    override val note: String,
    override val save: FormButton,
    override val cancel: String,
    override val remove: FormButton?,
) : FormSheetModel {
    override val kind
        get() = FormSheetKinds.RULE

    override val id
        get() = ruleId.orEmpty()
}

/**
 * The goal form of the kind of goal [arch] (an archetype, or CUSTOM: your own activity), with its
 * optional target.
 */
data class GoalFormSheetModel(
    val arch: String,
    override val title: String,
    override val schema: FormSchema,
    override val note: String,
    override val save: FormButton,
    override val cancel: String,
) : FormSheetModel {
    override val kind
        get() = FormSheetKinds.GOAL_FORM

    override val id
        get() = arch

    override val remove: FormButton?
        get() = null
}

/** One kind of goal in the grid: its icon, name and the sessions it plans ([sub]). */
data class GoalTypeOption(val arch: String, val icon: String, val title: String, val sub: String)

/** "Add a goal": the kinds of goal to pick from, the last always "Something else" (CUSTOM). */
data class GoalTypeSheetModel(
    val title: String,
    val text: String,
    val options: List<GoalTypeOption>,
) : SheetModel {
    override val kind
        get() = FormSheetKinds.GOAL_TYPE

    override val id
        get() = ""
}
