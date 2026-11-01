package com.lifemax.rpg.feature.lifedirector.presentation

import com.lifemax.rpg.feature.lifedirector.domain.DirectorReply
import com.lifemax.rpg.feature.lifedirector.domain.GoneSheet
import com.lifemax.rpg.feature.lifedirector.domain.LifeDirectorCommands
import com.lifemax.rpg.feature.lifedirector.domain.LifeDirectorProjection
import com.lifemax.rpg.feature.lifedirector.domain.OpenSheet
import com.lifemax.rpg.feature.lifedirector.domain.SessionSheetModel
import com.lifemax.rpg.feature.lifedirector.domain.SheetKinds
import com.lifemax.rpg.feature.lifedirector.domain.answers
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Today's sheets and actions (phase 4A): the sheet the screen asked the page for, and Today's
 * writes, one at a time. Each write has an operation id per (command, target) that is kept until a
 * terminal reply (ok, also the unchanged ok a repeat earns, or a definite refusal), so a write
 * whose reply was lost because the screen hid is sent again with the same id and the page answers
 * "Already recorded." instead of doing it twice. After a terminal reply the next tap is a new
 * operation.
 */
internal class SheetController(
    private val commands: LifeDirectorCommands,
    private val projection: StateFlow<LifeDirectorProjection?>,
    private val ui: MutableStateFlow<DirectorUi>,
    private val show: (String) -> Unit,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private var sheetRequest: String? = null
    private var todayRequest: String? = null
    private var pending: Pending? = null
    private val ops = HashMap<Pair<String, String>, String>()

    private data class Pending(
        val key: Pair<String, String>,
        val fromSheet: Boolean,
        val keepOnRepeat: Boolean,
        /** the sheet it was sent from: only that one closes when it lands, not one opened since */
        val sheet: OpenSheet?,
    )

    /** Whether the page draws Today's sheets and runs Today's actions for this screen. */
    val available: Boolean
        get() = projection.value?.sheets == true

    /** The session sheet on show: the page's model of the one the screen asked for. */
    val session: SessionSheetModel?
        get() {
            val open = ui.value.sheet ?: return null
            return (projection.value?.sheet as? SessionSheetModel)?.takeIf { it.matches(open) }
        }

    /** The operation id a write to [target] would carry now (for tests and diagnostics). */
    fun operation(command: String, target: String): String? = ops[command to target]

    fun open(kind: String, id: String) {
        val draft = ui.value.minutesDraft?.takeIf { it.id == id }
        ui.update { it.copy(sheet = OpenSheet(kind, id), minutesDraft = draft) }
        sheetRequest = commands.showSheet(kind, id)
    }

    /** Closed on the screen: the page stops sending its model. */
    fun close() {
        if (ui.value.sheet == null) return
        forget()
        commands.showSheet("", "")
    }

    /**
     * The screen opened or went behind the page: no sheet is open (the page drops its own when the
     * screen opens) and no reply is awaited, but the operation ids stay for the next tap.
     */
    fun reset() {
        todayRequest = null
        pending = null
        forget()
        ui.update { it.copy(pendingToday = null) }
    }

    private fun forget() {
        sheetRequest = null
        ui.update { it.copy(sheet = null) }
    }

    fun minutes(text: String) {
        val id = ui.value.sheet?.id ?: return
        ui.update { it.copy(minutesDraft = MinutesDraft(id, text.filter(Char::isDigit).take(3))) }
    }

    /** The minutes to send: the field's, kept within the page's limits, or the planned length. */
    fun minutesOf(s: SessionSheetModel): Int {
        val m = s.minutes ?: return 0
        val typed = ui.value.minutesDraft?.takeIf { it.id == s.id }?.text?.toIntOrNull()
        return (typed ?: m.value).coerceIn(m.min, m.max)
    }

    /** The stepper: [delta] minutes more or fewer, within the page's limits. */
    fun step(s: SessionSheetModel, delta: Int) {
        val m = s.minutes ?: return
        minutes((minutesOf(s) + delta).coerceIn(m.min, m.max).toString())
    }

    /**
     * Today's write [name] on session [target], with its [args]; nothing while another is in
     * flight. [fromSheet]: the sheet closes once it has landed, as the page's does.
     */
    fun today(name: String, target: String, args: Map<String, Any>, fromSheet: Boolean) {
        val command = (args["act"] as? String)?.let { "$name.$it" } ?: name
        send(name, command to target, args + ("id" to target), fromSheet)
    }

    /**
     * Today's write [name] with its [args] as they are, its operation id keyed by [key] (command,
     * target). [keepOnRepeat]: a landed repeat that changed nothing leaves the sheet open, as the
     * page's Something came up does ("Already added").
     */
    fun send(
        name: String,
        key: Pair<String, String>,
        args: Map<String, Any>,
        fromSheet: Boolean,
        keepOnRepeat: Boolean = false,
    ) {
        if (pending != null) return
        val op = ops.getOrPut(key, newId)
        pending = Pending(key, fromSheet, keepOnRepeat, ui.value.sheet)
        ui.update { it.copy(pendingToday = key.first) }
        todayRequest = commands.today(name, args + ("op" to op))
    }

    /** A new projection: a sheet whose session has left the plan closes, and the screen says so. */
    fun projected() {
        val open = ui.value.sheet ?: return
        val model = projection.value?.sheet
        if (model is GoneSheet && model.matches(open)) {
            // the sheet's own Finish or Stop ends what it showed: its reply, which comes next,
            // closes it with the page's words
            if (pending?.fromSheet == true) return
            close()
            show(GONE)
        } else if (sheetRequest == null && (model == null || !model.matches(open))) forget()
    }

    /** Whether [reply] answered a sheet or Today command of this screen's. */
    fun onReply(reply: DirectorReply): Boolean {
        when (reply.command) {
            "sheet" ->
                if (reply.requestId == sheetRequest) {
                    sheetRequest = null
                    if (!reply.ok) {
                        forget()
                        show(reply.message.ifBlank { CANNOT })
                    } else projected()
                }
            "today" -> if (reply.requestId == todayRequest) answered(reply)
            else -> return false
        }
        return true
    }

    private fun answered(reply: DirectorReply) {
        val p = pending ?: return
        todayRequest = null
        pending = null
        ui.update { it.copy(pendingToday = null) }
        // a save the store refused keeps the change in memory: the same operation again lands it
        if (reply.ok || !reply.message.startsWith(NOT_SAVED)) ops.remove(p.key)
        val same = p.fromSheet && ui.value.sheet == p.sheet
        if (reply.ok) {
            if (same && (reply.changed || !p.keepOnRepeat)) close()
            if (reply.message.isNotBlank()) show(reply.message)
            // Start opens the timer's sheet, as the page's Start does
            if (p.key.first == "timer.start" && available && ui.value.sheet == null)
                open(SheetKinds.TIMER, p.key.second)
        } else {
            if (!reply.said) show(reply.message.ifBlank { NOTHING_CHANGED })
            // a refused Finish can still end what the sheet showed (the page cleared it in memory):
            // a sheet left with nothing to show closes rather than hold the screen invisibly
            val model = projection.value?.sheet
            val open = ui.value.sheet
            if (same && open != null && model is GoneSheet && model.matches(open)) close()
        }
    }

    private fun com.lifemax.rpg.feature.lifedirector.domain.SheetModel.matches(open: OpenSheet) =
        answers(open)

    companion object {
        const val GONE = "That is no longer on the plan."
        private const val CANNOT = "Life Director cannot do that."
        private const val NOTHING_CHANGED = "Nothing was changed."
        private const val NOT_SAVED = "Not saved"
    }
}

/** What was typed in a session sheet's minutes field, kept while the screen hides. */
data class MinutesDraft(val id: String, val text: String)
