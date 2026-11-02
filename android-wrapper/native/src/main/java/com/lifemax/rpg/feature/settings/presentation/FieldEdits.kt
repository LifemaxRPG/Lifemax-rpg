package com.lifemax.rpg.feature.settings.presentation

import com.lifemax.rpg.feature.settings.domain.ControlOp
import com.lifemax.rpg.feature.settings.domain.SettingsCommand
import com.lifemax.rpg.feature.settings.domain.SettingsControl
import com.lifemax.rpg.feature.settings.domain.SettingsProjection
import com.lifemax.rpg.feature.settings.domain.SettingsRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * The text, number and time fields the screen edits itself (settings.fields.v1). It decides no rule
 * of the page's: a text or number is typed into a draft, kept as typed, and a form's Save sends the
 * form's values together for the page to put in its inputs and save with its own function (which
 * clamps them and words the result); a time picked is sent at once, and shows as picked until the
 * page answers.
 *
 * A draft lives only in the screen's state, so reopening the screen starts again from the page
 * (SettingsViewModel.onOpen), and it stays when the page refuses the save, so the owner can mend
 * what was typed. A draft is dropped once the page has stored exactly what was sent. A password
 * never reaches this class: the page projects no key field.
 *
 * [send] sends a command under the control's id and remembers the drafts it carried, so the answer
 * can drop them.
 */
internal class FieldEdits(
    private val ui: MutableStateFlow<SettingsUi>,
    private val projection: () -> SettingsProjection?,
    private val send:
        (SettingsCommand, key: String, asked: Asked, carried: Map<String, String>) -> Unit,
) {
    /** [text] typed in [control]; a draft equal to the page's own value is no draft. */
    fun type(control: SettingsControl.Field, text: String) {
        if (control.op != ControlOp.Edit || !control.enabled) return
        ui.update { s ->
            s.copy(
                drafts =
                    if (text == control.value) s.drafts - control.id
                    else s.drafts + (control.id to text)
            )
        }
    }

    /** A time picked for [control] ([minutes] after midnight): the page sets it at once. */
    fun time(control: SettingsControl.Field, minutes: Int) {
        if (control.op != ControlOp.Edit || !control.enabled) return
        val value = "%02d:%02d".format(minutes / 60 % 24, minutes % 60)
        if (value == control.value) return
        send(
            SettingsCommand.SetField(control.id, value),
            control.id,
            Asked.Value(value),
            emptyMap(),
        )
    }

    /** Save's tap: every field of the button's form, as typed or as the page holds it. */
    fun save(button: SettingsControl.Button) {
        val fields = fieldsOf(button.group)
        if (button.op != ControlOp.Save || fields.isEmpty()) return
        val drafts = ui.value.drafts
        val values = fields.associate { it.id to (drafts[it.id] ?: it.value) }
        send(
            SettingsCommand.SaveForm(button.group, values.mapKeys { inputId(it.key) }),
            button.id,
            Asked.Press,
            values.filterKeys { it in drafts },
        )
    }

    /**
     * The page answered a request that carried [carried]: stored, so those drafts are done with.
     */
    fun answered(ok: Boolean, carried: Map<String, String>) {
        if (!ok || carried.isEmpty()) return
        ui.update { s -> s.copy(drafts = s.drafts.filterNot { (id, text) -> carried[id] == text }) }
    }

    private fun fieldsOf(group: String): List<SettingsControl.Field> =
        projection()
            ?.cards
            ?.flatMap { it.rows }
            ?.flatMap { row ->
                when (row) {
                    is SettingsRow.Line -> row.controls
                    is SettingsRow.Field -> row.controls
                    is SettingsRow.Controls -> row.controls
                    else -> emptyList()
                }
            }
            ?.filterIsInstance<SettingsControl.Field>()
            ?.filter { it.edit?.group == group } ?: emptyList()

    /** The page's input id: a control id of a form field is "#" and the input's own id. */
    private fun inputId(controlId: String) = controlId.removePrefix("#")
}
