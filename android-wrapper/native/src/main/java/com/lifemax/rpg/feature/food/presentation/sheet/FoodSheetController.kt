package com.lifemax.rpg.feature.food.presentation.sheet

import com.lifemax.rpg.feature.food.domain.FoodAnswer
import java.util.UUID

/**
 * The add-food sheet's actions. Moves (a tab, a step, a meal, Back) go to the page as they are;
 * writes (Log, the star, a saved form, Remove, Undo) carry an operation id per (command, target),
 * kept until the page answers that operation (the projection's `answer`). So a second tap before
 * the answer sends nothing, and a tap after an answer that never came (the screen went away, the
 * update was lost) sends the same operation again, which the page answers "Already recorded."
 * instead of doing twice. Every answer is final: the page takes back a change it could not save, so
 * the next tap is a new operation. One write is awaited at a time, as Life Director's
 * SheetController does.
 */
internal class FoodSheetController(
    var dispatch: (command: String, payload: Map<String, Any>) -> Unit,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val ops = HashMap<Pair<String, String>, String>()
    private var pending: Pair<String, String>? = null
    private var sentAt = 0L

    /** The operation id a write to [target] would carry now (for tests and diagnostics). */
    fun operation(command: String, target: String): String? = ops[command to target]

    /** Whether a write is awaiting its answer. */
    val waiting: Boolean
        get() = pending != null && now() - sentAt < WAIT_MS

    /** A move: the page applies it and the next update shows it. */
    fun send(command: String, payload: Map<String, Any> = emptyMap()) = dispatch(command, payload)

    /** The write [command] on [target] (a food, an entry, a form), with its [payload]. */
    fun write(command: String, target: String, payload: Map<String, Any> = emptyMap()) {
        if (waiting) return
        val key = command to target
        val op = ops.getOrPut(key, newId)
        pending = key
        sentAt = now()
        dispatch(command, payload + ("op" to op))
    }

    /** A new projection: its answer, if it answers the awaited write, ends that operation. */
    fun answered(answer: FoodAnswer?) {
        if (answer == null) return
        // the operation it answers is over, whichever write still holds it (one that waited past
        // WAIT_MS included), so a later write on the same target is a new operation
        ops.values.removeAll { it == answer.op }
        if (pending != null && ops[pending] == null) pending = null
    }

    /**
     * The sheet is closed: its operations end with it. A write whose answer the page dropped on
     * closing (it answers on the next frame) must not lend its id to a write in a later sheet, or
     * the page would answer that one "Already recorded." and not do it.
     */
    fun sheetClosed() {
        ops.clear()
        pending = null
    }

    private companion object {
        /** How long an answer is awaited before a tap may send the operation again. */
        const val WAIT_MS = 4000L
    }
}
