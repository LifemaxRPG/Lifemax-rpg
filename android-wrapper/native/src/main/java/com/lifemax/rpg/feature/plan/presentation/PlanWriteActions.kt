package com.lifemax.rpg.feature.plan.presentation

import com.lifemax.rpg.feature.plan.domain.PlanDay
import com.lifemax.rpg.feature.plan.domain.PlanItem
import com.lifemax.rpg.feature.plan.domain.PlanProjection
import com.lifemax.rpg.feature.plan.domain.PlanRunner
import com.lifemax.rpg.feature.plan.domain.RunnerPhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * What the screen does beyond selecting and ticking, in the screen's words: open a field, Leave
 * plan, sign up, and each button of the runner. The page decides what each does and answers; this
 * only gathers what the player typed and sends it as the page's commands.
 */
class PlanWriteActions
internal constructor(
    private val writer: PlanWriteController,
    private val ui: MutableStateFlow<PlanUiState>,
    private val projection: StateFlow<PlanProjection?>,
) {
    private val runner: PlanRunner?
        get() = projection.value?.runner

    // ---- fields ----

    fun editField(day: PlanDay, item: PlanItem, kind: FieldKind) {
        if (day.locked) return
        val value = item.value
        val edit =
            when (kind) {
                FieldKind.VALUE ->
                    value?.let {
                        FieldEdit(
                            day.date,
                            item.key,
                            kind,
                            item.title,
                            it.placeholder,
                            it.placeholder,
                            it.text,
                        )
                    }
                FieldKind.LOG ->
                    FieldEdit(
                        day.date,
                        item.key,
                        kind,
                        item.title,
                        "Session log",
                        "e.g. 3×10 @ 120lb, felt strong",
                        item.log.text,
                    )
            } ?: return
        ui.update { it.copy(edit = edit) }
    }

    fun draft(text: String) {
        val max = projection.value?.logMax ?: 400
        ui.update { s ->
            s.copy(
                edit =
                    s.edit?.copy(
                        draft =
                            if (s.edit.kind == FieldKind.LOG) text.take(max)
                            else text.take(VALUE_MAX),
                        error = null,
                    )
            )
        }
    }

    fun cancelEdit() = ui.update { it.copy(edit = null) }

    fun saveEdit() = writer.saveEdit()

    // ---- Leave plan and the sign-up ----

    fun askLeave() = ui.update { it.copy(leaving = true) }

    fun cancelLeave() = ui.update { it.copy(leaving = false) }

    fun leave() = writer.leave()

    fun startDate(date: String) = ui.update { it.copy(startDate = date) }

    fun signUp(start: String) = writer.signUp(ui.value.startDate ?: start)

    // ---- the runner ----

    /** The launcher's button: start the day's session, or resume the saved one. */
    fun launch(date: String, resume: Boolean) {
        if (resume) writer.act("resume", "") else writer.act("start", date, mapOf("date" to date))
    }

    /** Where in the session a draft belongs, so a typed amount is never carried to the next set. */
    private fun token(r: PlanRunner) =
        "${r.phase.wire}|${r.date}|${r.step?.key.orEmpty()}|${r.step?.set ?: 0}"

    private fun draftFor(r: PlanRunner): RunnerUi =
        ui.value.runner.takeIf { it.token == token(r) } ?: RunnerUi(token(r))

    private fun target(r: PlanRunner) = token(r)

    private fun act(act: String, args: Map<String, Any?> = emptyMap()) {
        val r = runner ?: return
        writer.act(act, target(r), args)
    }

    fun begin() = act("begin")

    fun pickDay(date: String) = act("date", mapOf("date" to date))

    fun amount(text: String) {
        val r = runner ?: return
        ui.update {
            it.copy(runner = draftFor(r).copy(amount = text.take(AMOUNT_MAX), error = null))
        }
    }

    fun weight(text: String) {
        val r = runner ?: return
        ui.update {
            it.copy(runner = draftFor(r).copy(weight = text.take(AMOUNT_MAX), error = null))
        }
    }

    /** Start the set; a timer with no length of its own first asks the player for one. */
    fun go() {
        val r = runner ?: return
        val step = r.step ?: return
        if (step.askDuration && ui.value.runner.duration == null)
            ui.update {
                it.copy(
                    runner =
                        draftFor(r).copy(duration = step.durationAsk.start.toString(), error = null)
                )
            }
        else if (step.askDuration) act("go", mapOf("amount" to ui.value.runner.duration))
        else act("go")
    }

    fun duration(text: String) {
        val r = runner ?: return
        ui.update {
            it.copy(runner = draftFor(r).copy(duration = text.take(AMOUNT_MAX), error = null))
        }
    }

    fun cancelDuration() {
        val r = runner ?: return
        ui.update { it.copy(runner = draftFor(r).copy(duration = null)) }
    }

    /**
     * Log the set with what is in the boxes (their suggestions until the player types), or the
     * timer's time.
     */
    fun log() {
        val r = runner ?: return
        val step = r.step ?: return
        val draft = draftFor(r)
        val args = mutableMapOf<String, Any?>()
        if (r.phase == RunnerPhase.ACTIVE) {
            if (!step.isCheck) args["amount"] = draft.amount ?: step.suggest
            step.weight?.let { args["weight"] = draft.weight ?: it.suggest }
        }
        act("log", args)
    }

    fun stopTimer() = act("stop")

    fun cancelTimer() = act("cancel")

    fun restartInterval() = act("restart")

    fun back() = act("back")

    fun skipRest() = act("rest")

    fun adjustRest(seconds: Int) = act("adjust", mapOf("delta" to seconds))

    fun undo() = act("undo")

    fun reopen() = act("reopen")

    fun exit() = act("exit")

    fun pause() = act("pause")

    fun itinerary(open: Boolean) {
        val r = runner ?: return
        ui.update { it.copy(runner = draftFor(r).copy(itinerary = open)) }
    }

    fun askSkip() {
        val r = runner ?: return
        ui.update { it.copy(runner = draftFor(r).copy(confirmSkip = true)) }
    }

    fun cancelSkip() {
        val r = runner ?: return
        ui.update { it.copy(runner = draftFor(r).copy(confirmSkip = false)) }
    }

    fun skip() = act("skip")

    fun askSwap() {
        val r = runner ?: return
        val step = r.step ?: return
        ui.update { it.copy(runner = draftFor(r).copy(swapping = step.swapFrom, error = null)) }
    }

    fun swapDraft(text: String) {
        val r = runner ?: return
        ui.update {
            it.copy(runner = draftFor(r).copy(swapping = text.take(SWAP_MAX), error = null))
        }
    }

    fun cancelSwap() {
        val r = runner ?: return
        ui.update { it.copy(runner = draftFor(r).copy(swapping = null)) }
    }

    fun swap() = act("swap", mapOf("name" to (ui.value.runner.swapping.orEmpty())))

    private companion object {
        /**
         * The value box has no limit on the page; this keeps a stray paste from filling the screen.
         */
        const val VALUE_MAX = 100
        const val AMOUNT_MAX = 8
        /** The page cuts a substituted name at 85 characters (gwrSubstituteExercise). */
        const val SWAP_MAX = 85
    }
}
