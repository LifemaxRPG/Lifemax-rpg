package com.lifemax.rpg.feature.plan.presentation

import com.lifemax.rpg.feature.plan.domain.Celebration
import com.lifemax.rpg.feature.plan.domain.PlanReply
import com.lifemax.rpg.feature.plan.domain.PlanWrite
import com.lifemax.rpg.feature.plan.domain.PlanWrites
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Which field of an exercise is being edited. */
enum class FieldKind(val command: String) {
    VALUE("value"),
    LOG("log"),
}

/**
 * A value or session-log field open in its sheet. [error] is the page's refusal, shown under the
 * field while the sheet stays open to be corrected.
 */
data class FieldEdit(
    val date: String,
    val item: String,
    val kind: FieldKind,
    val title: String,
    val label: String,
    val placeholder: String,
    val draft: String,
    val error: String? = null,
)

/**
 * What the runner has typed that the page does not hold yet: the set's amount and weight, which set
 * they are for ([token]), and the little questions the page asked with a prompt or a confirm.
 */
data class RunnerUi(
    val token: String = "",
    val amount: String? = null,
    val weight: String? = null,
    val itinerary: Boolean = false,
    val confirmSkip: Boolean = false,
    val swapping: String? = null,
    val duration: String? = null,
    val error: String? = null,
)

/**
 * The screen's writes beyond ticking a box: fields, Leave plan, the sign-up and the runner's acts.
 * Each is sent to the page as one command with an operation id per (command, target), kept until
 * the page answers, so a write whose answer was lost because the screen hid is sent again with the
 * same id and the page says "Already recorded." instead of doing it twice. One write is in flight
 * at a time; after an answer the next tap is a new operation.
 */
internal class PlanWriteController(
    private val writes: PlanWrites,
    private val ui: MutableStateFlow<PlanUiState>,
    private val say: (message: String, awards: List<Celebration>) -> Unit,
    private val newId: () -> String,
    private val newOperation: () -> String,
) {
    private val ops = HashMap<Pair<String, String>, String>()
    private var inFlight: InFlight? = null

    private class InFlight(
        val requestId: String,
        val key: Pair<String, String>,
        val write: PlanWrite,
    )

    /** The operation id a write to [target] would carry now (for tests). */
    fun operation(command: String, target: String): String? = ops[command to target]

    /**
     * The screen opened afresh: no answer is awaited, but the operation ids stay for the next tap.
     */
    fun reset() {
        inFlight = null
        ui.update { it.copy(busy = false, edit = null, leaving = false, runner = RunnerUi()) }
    }

    val busy: Boolean
        get() = inFlight != null

    /** Sends [write] unless another is awaited. Returns whether it was sent. */
    fun send(write: PlanWrite): Boolean {
        if (inFlight != null) return false
        val key = write.command to write.target
        val operation = ops.getOrPut(key, newOperation)
        val id = newId()
        inFlight = InFlight(id, key, write)
        ui.update { it.copy(busy = true) }
        writes.send(id, operation, write)
        return true
    }

    /**
     * Whether [reply] answers the write awaited; if so, settles it. Returns the write it answered.
     */
    fun answered(reply: PlanReply): PlanWrite? {
        val flight = inFlight?.takeIf { it.requestId == reply.requestId } ?: return null
        inFlight = null
        ops.remove(flight.key)
        ui.update { it.copy(busy = false) }
        return flight.write
    }

    // ---- fields ----

    fun saveEdit() {
        val e = ui.value.edit ?: return
        send(
            PlanWrite(
                e.kind.command,
                "${e.date}/${e.item}",
                mapOf("date" to e.date, "key" to e.item, "text" to e.draft),
            )
        )
    }

    // ---- Leave plan and the sign-up ----

    fun leave() {
        send(PlanWrite("leave", "", emptyMap()))
    }

    fun signUp(start: String) {
        send(PlanWrite("signUp", "", mapOf("start" to start)))
    }

    // ---- the runner ----

    /**
     * One act of the guided workout; [target] says which step it is for, so a retry is the same
     * operation.
     */
    fun act(act: String, target: String, args: Map<String, Any?> = emptyMap()) {
        send(PlanWrite("runner", "$act:$target", args + ("act" to act)))
    }

    /**
     * What the page said of a write: the words to show, and whether the sheet it came from can
     * close.
     */
    fun settle(write: PlanWrite, reply: PlanReply) {
        val refused = !reply.ok
        val message =
            if (refused) reply.message.ifBlank { "Could not save." }
            else if (reply.changed) reply.message else ""
        when (write.command) {
            "value",
            "log" ->
                ui.update {
                    when {
                        refused && !reply.changed -> it.copy(edit = it.edit?.copy(error = message))
                        else -> it.copy(edit = null)
                    }
                }
            "leave" -> ui.update { it.copy(leaving = false) }
            "runner" -> {
                // the answer belongs to the step the act was sent for (its target is "act:step")
                val step = write.target.substringAfter(':')
                val stays = refused && !reply.changed
                ui.update {
                    val base =
                        if (it.runner.token == step) it.runner
                        else RunnerUi(step, itinerary = it.runner.itinerary)
                    it.copy(
                        runner =
                            base.copy(
                                error = if (stays) message else null,
                                confirmSkip = false,
                                swapping =
                                    if (write.args["act"] == "swap" && stays) base.swapping
                                    else null,
                                duration =
                                    if (write.args["act"] == "go" && stays) base.duration else null,
                            )
                    )
                }
            }
        }
        // a refusal that stays on a sheet is told there; anything else is said once, as the
        // toggle's message is
        val onSheet = refused && !reply.changed && write.command in SHEET_BOUND
        if (!onSheet && (message.isNotEmpty() || reply.celebrations.isNotEmpty()))
            say(message, reply.celebrations)
    }

    private companion object {
        val SHEET_BOUND = setOf("value", "log", "runner")
    }
}
