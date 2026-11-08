package com.lifemax.rpg.feature.lifedirector.presentation

import com.lifemax.rpg.core.designsystem.form.FieldValue
import com.lifemax.rpg.core.designsystem.form.FormValidation
import com.lifemax.rpg.feature.lifedirector.domain.DirectorReply
import com.lifemax.rpg.feature.lifedirector.domain.FormSheetKinds
import com.lifemax.rpg.feature.lifedirector.domain.FormSheetModel
import com.lifemax.rpg.feature.lifedirector.domain.GoalFormSheetModel
import com.lifemax.rpg.feature.lifedirector.domain.GoalTypeSheetModel
import com.lifemax.rpg.feature.lifedirector.domain.LifeDirectorCommands
import com.lifemax.rpg.feature.lifedirector.domain.LifeDirectorProjection
import com.lifemax.rpg.feature.lifedirector.domain.OpenSheet
import com.lifemax.rpg.feature.lifedirector.domain.RuleSheetModel
import com.lifemax.rpg.feature.lifedirector.domain.answers
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject

/**
 * What a form sheet holds on the screen: the drafts typed (by the sheet they belong to, kept while
 * the screen hides, A24), the page's words for the fields it would refuse ([errors]) or did refuse
 * ([refusal]), the write in flight, the kind of goal picked in the grid (the tile the form grows
 * from) and whether the form came from the grid (Back steps back to it), and the question before
 * Remove.
 */
data class FormUi(
    val drafts: Map<OpenSheet, Map<String, FieldValue>> = emptyMap(),
    val errors: Map<String, String> = emptyMap(),
    val refusal: String? = null,
    val pending: String? = null,
    val picked: String? = null,
    val fromGrid: Boolean = false,
    val confirm: ChangeConfirm? = null,
)

/**
 * My time's sheets (phase 4B): the commitment sheet, "Add a goal" and the goal form, asked of the
 * page like Today's (through [sheets], so a sheet whose commitment has gone closes with the page's
 * words), drawn from its form schemas, and saved with the page's `do` writes ruleSave, ruleRemove
 * and goalAdd, one at a time.
 *
 * Before sending, the screen checks the shown fields in the page's words (FormValidation) and keeps
 * numbers within their limits. A new commitment or goal carries an operation id per (command,
 * target) that is kept until a terminal reply, so a reply lost while the screen hid is sent again
 * with the same id and the page answers "Already added" rather than adding twice. A save the store
 * refused ("Not saved: …", ok false but changed) keeps the id and the form: the same request again
 * lands it. A refusal shows the page's words under the field they name, else above the buttons.
 */
internal class FormSheetController(
    private val sheets: SheetController,
    private val commands: LifeDirectorCommands,
    private val projection: StateFlow<LifeDirectorProjection?>,
    private val ui: MutableStateFlow<DirectorUi>,
    private val show: (String) -> Unit,
    /** a handle, a guide or another of the screen's sheets holds it */
    private val blocked: () -> Boolean,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private var request: String? = null
    private var sent: Sent? = null
    private val ops = HashMap<Pair<String, String>, String>()

    /** A write in flight: its op's key (null without one) and the sheet it came from. */
    private data class Sent(val opKey: Pair<String, String>?, val sheet: OpenSheet?)

    private val state: FormUi
        get() = ui.value.forms

    private fun edit(f: (FormUi) -> FormUi) = ui.update { it.copy(forms = f(it.forms)) }

    /**
     * Whether the page draws My time's sheets for this screen (director.setup.v1 puts My time in
     * covers).
     */
    val available: Boolean
        get() = projection.value?.covers?.contains(SETUP) == true

    /** The form sheet on show (commitment or goal form), once the page has sent its model. */
    val form: FormSheetModel?
        get() = shown() as? FormSheetModel

    /** "Add a goal" on show. */
    val goalType: GoalTypeSheetModel?
        get() = shown() as? GoalTypeSheetModel

    private fun shown() =
        ui.value.sheet?.let { open -> projection.value?.sheet?.takeIf { it.answers(open) } }

    /** The operation id a write to [target] would carry now (for tests and diagnostics). */
    fun operation(command: String, target: String): String? = ops[command to target]

    private val free: Boolean
        get() = available && !blocked() && ui.value.sheet == null

    /** The commitment sheet: of commitment [id], or a new one when [id] is empty. */
    fun openRule(id: String) {
        if (free) sheets.open(FormSheetKinds.RULE, id)
    }

    /** "Add a goal": the grid of kinds. */
    fun openAt(epoch: String) {
        if (free && epoch.toLongOrNull() != null) {
            edit { it.copy(fromGrid = false, picked = null, errors = emptyMap(), refusal = null) }
            sheets.open(FormSheetKinds.GOAL_FORM, "at:$epoch")
        }
    }

    fun openGoalType() {
        if (free) sheets.open(FormSheetKinds.GOAL_TYPE, "")
    }

    /**
     * A kind picked in the grid: its form replaces the grid in the same sheet, growing from the
     * tile.
     */
    fun pick(arch: String) {
        val t = goalType ?: return
        if (state.pending != null || t.options.none { it.arch == arch }) return
        edit { it.copy(picked = arch, fromGrid = true, errors = emptyMap(), refusal = null) }
        sheets.open(FormSheetKinds.GOAL_FORM, arch)
    }

    /** The values a form shows: its draft, else the page's. */
    fun values(m: FormSheetModel): Map<String, FieldValue> =
        state.drafts[OpenSheet(m.kind, m.id)] ?: m.schema.initialValues()

    /** A field changed: the draft keeps it, and the page's words about that field go. */
    fun change(key: String, value: FieldValue) {
        val m = form ?: return
        if (state.pending != null) return
        val open = OpenSheet(m.kind, m.id)
        edit { f ->
            f.copy(
                drafts = f.drafts + (open to (values(m) + (key to value))),
                errors = f.errors - key,
                refusal = null,
            )
        }
    }

    /**
     * Save (Add, Add goal): checked in the page's words first, then sent as every shown field's
     * value by key.
     */
    fun save() {
        val m = form ?: return
        if (state.pending != null || state.confirm != null) return
        val values = values(m)
        val errors = FormValidation.errors(m.schema, values)
        if (errors.isNotEmpty()) return edit { it.copy(errors = errors, refusal = null) }
        val args = LinkedHashMap<String, Any>()
        FormValidation.sendable(m.schema, values).forEach { (k, v) -> args[k] = wire(v) }
        var opKey: Pair<String, String>? = null
        when (m) {
            is RuleSheetModel ->
                if (m.ruleId != null) args["id"] = m.ruleId
                else opKey = (m.save.command to "").also { args["op"] = ops.getOrPut(it, newId) }
            is GoalFormSheetModel -> {
                opKey = (m.save.command to m.arch).also { args["op"] = ops.getOrPut(it, newId) }
                args["arch"] = m.arch
            }
        }
        send(m.save.command, args, opKey, OpenSheet(m.kind, m.id))
    }

    /** Remove: the page's question first, in its words (the commitment's name and Remove). */
    fun askRemove() {
        val m = form as? RuleSheetModel ?: return
        val r = m.remove ?: return
        if (state.pending != null) return
        val name =
            (values(m)["title"] as? FieldValue.Text)?.text?.takeIf { it.isNotBlank() } ?: m.title
        // the page asks nothing before its own Remove, so the question has only the page's words:
        // the commitment's name and Remove
        val id = m.ruleId ?: return
        edit { it.copy(confirm = ChangeConfirm(name, "", r.label, r.command, mapOf("id" to id))) }
    }

    fun confirmRemove() {
        val c = state.confirm ?: return
        val open = ui.value.sheet
        edit { it.copy(confirm = null) }
        if (state.pending == null) send(c.name, c.args, null, open)
    }

    fun keep() = edit { it.copy(confirm = null) }

    /**
     * Cancel, the close button or the dimmed screen: the sheet closes; a draft typed stays for next
     * time.
     */
    fun close() {
        edit {
            it.copy(
                picked = null,
                fromGrid = false,
                errors = emptyMap(),
                refusal = null,
                confirm = null,
            )
        }
        sheets.close()
    }

    /**
     * Whether Back has a step of its own here: the question before Remove, or the grid behind a
     * goal form.
     */
    fun stepsBack(open: OpenSheet?, f: FormUi): Boolean =
        f.confirm != null ||
            (open?.kind == FormSheetKinds.GOAL_FORM && f.fromGrid && f.pending == null)

    /**
     * Back: closes the question, or steps back from a goal form to the grid; false when it has no
     * step here.
     */
    fun back(): Boolean {
        if (state.confirm != null) {
            keep()
            return true
        }
        if (!stepsBack(ui.value.sheet, state)) return false
        edit { it.copy(picked = null, fromGrid = false, errors = emptyMap(), refusal = null) }
        sheets.open(FormSheetKinds.GOAL_TYPE, "")
        return true
    }

    /**
     * The screen opened or went behind the page: nothing is awaited; drafts and operation ids stay.
     */
    fun reset() {
        request = null
        sent = null
        edit {
            it.copy(
                pending = null,
                picked = null,
                fromGrid = false,
                confirm = null,
                errors = emptyMap(),
                refusal = null,
            )
        }
    }

    private fun send(
        name: String,
        args: Map<String, Any>,
        opKey: Pair<String, String>?,
        sheet: OpenSheet?,
    ) {
        sent = Sent(opKey, sheet)
        edit { it.copy(pending = name, refusal = null) }
        request = commands.change(name, args)
    }

    /** Whether [reply] answered a write of this controller's. */
    fun onReply(reply: DirectorReply): Boolean {
        if (reply.command != "do" || reply.requestId != request || request == null) return false
        val s = sent
        request = null
        sent = null
        edit { it.copy(pending = null) }
        // a save the store refused keeps the change in memory: the same operation again lands it
        val notSaved = !reply.ok && reply.changed
        if (!notSaved) s?.opKey?.let(ops::remove)
        val stillOpen = s?.sheet != null && ui.value.sheet == s.sheet
        if (reply.ok) {
            // done (or done already: "Already added"): the draft has served, the sheet closes
            s?.sheet?.let { o -> edit { it.copy(drafts = it.drafts - o) } }
            if (stillOpen) close()
            if (reply.message.isNotBlank()) show(reply.message)
            return true
        }
        val words = reply.message.ifBlank { NOTHING_CHANGED }
        if (!stillOpen) {
            show(words)
            return true
        }
        // the page's words under the field they are about, as the screen's own check puts them
        val about =
            form?.schema?.allFields?.filter { it.required == words }?.map { it.key }.orEmpty()
        val shownKeys = form?.let { FormValidation.visibleKeys(it.schema, values(it)) }.orEmpty()
        val keys = about.filter { it in shownKeys }
        edit {
            if (keys.isNotEmpty()) it.copy(errors = keys.associateWith { words }, refusal = null)
            else it.copy(refusal = words)
        }
        return true
    }

    private companion object {
        const val NOTHING_CHANGED = "Nothing was changed."

        /** The part director.setup.v1 adds to covers: My time, whose sheets these are. */
        const val SETUP = "setup"

        /**
         * A value as the page's form reads it (formValues): minutes, numbers or null, the ticked
         * days.
         */
        fun wire(v: FieldValue): Any =
            when (v) {
                is FieldValue.Text -> v.text
                is FieldValue.Number -> v.number ?: JSONObject.NULL
                is FieldValue.Time -> v.minutes
                is FieldValue.Date -> v.date
                is FieldValue.Choice -> v.value
                is FieldValue.Check -> v.on
                is FieldValue.Days -> v.days.sorted()
            }
    }
}
