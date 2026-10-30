package com.lifemax.rpg.feature.plan.presentation

import androidx.lifecycle.ViewModel
import com.lifemax.rpg.feature.plan.domain.Celebration
import com.lifemax.rpg.feature.plan.domain.PlanCommands
import com.lifemax.rpg.feature.plan.domain.PlanDay
import com.lifemax.rpg.feature.plan.domain.PlanProjection
import com.lifemax.rpg.feature.plan.domain.PlanReply
import com.lifemax.rpg.feature.plan.domain.PlanRepository
import com.lifemax.rpg.feature.plan.domain.SetRef
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The screen's own selection, and the boxes it has asked the page to tick or clear. A tapped box
 * shows its new state at once, marked pending, until the page answers. The answer comes with the
 * page's fresh projection, so dropping the mark then shows the box as the page holds it. The page's
 * selected day is adopted whenever the page changes it.
 */
class PlanViewModel(
    repository: PlanRepository,
    private val commands: PlanCommands,
    private val newRequestId: () -> String = { UUID.randomUUID().toString() },
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 },
) : ViewModel() {
    val projection = repository.state
    private val mutable = MutableStateFlow(PlanUiState())
    val ui = mutable.asStateFlow()
    private val requests = mutableMapOf<String, Request>()
    private var adopted: Pair<String, Long>? = null
    private var feedbacks = 0L

    /** Follow the page's selected day if the page changed it since the last time. */
    fun adopt(p: PlanProjection) {
        val key = p.session to p.selectedRevision
        if (key == adopted) return
        adopted = key
        mutable.update { it.copy(selected = p.selectedDate) }
    }

    /**
     * The page opened the screen afresh. Answers to toggles sent before are not coming (the page
     * replies only while the screen is open), so the projection alone says what was saved.
     */
    fun onOpen() {
        requests.clear()
        mutable.update { it.copy(pending = emptyMap()) }
    }

    /**
     * The page sent a projection. An answer lost on the way (A14: its outcome is unknown) would
     * leave its box waiting for good; the projection says what the page holds, so a box asked for
     * [ANSWER_WAIT_MS] or longer ago shows that again and takes taps.
     */
    fun onUpdate() {
        val asked = nowMs() - ANSWER_WAIT_MS
        val lost = requests.filterValues { it.sentAt <= asked }
        if (lost.isEmpty()) return
        requests -= lost.keys
        mutable.update { ui -> ui.copy(pending = ui.pending - lost.values.map { it.set }.toSet()) }
    }

    fun selectDay(date: String) {
        if (projection.value?.day(date) != null) mutable.update { it.copy(selected = date) }
    }

    /**
     * The page's week tap: the week's Monday. When the plan starts later that week, the selected
     * day if it is in that week, otherwise the week's first day (where the page falls back to
     * today).
     */
    fun selectWeek(index: Int) {
        val p = projection.value ?: return
        val days = p.weeks.getOrNull(index)?.days ?: return
        val selected = mutable.value.day(p).date
        val target =
            days.first()
                ?: days.firstOrNull { it?.date == selected }
                ?: days.firstNotNullOfOrNull { it }
        if (target != null) selectDay(target.date)
    }

    /** Ask the page to tick or clear one set. A box already waiting for an answer takes no tap. */
    fun toggle(set: SetRef, checked: Boolean) {
        val day = projection.value?.day(set.date) ?: return
        if (day.locked || set in mutable.value.pending) return
        val id = newRequestId()
        requests[id] = Request(set, nowMs())
        mutable.update { it.copy(pending = it.pending + (set to checked)) }
        commands.toggle(id, set, checked)
    }

    fun onReply(reply: PlanReply) {
        val set = requests.remove(reply.requestId)?.set
        val message =
            when {
                !reply.ok -> reply.message.ifBlank { "Could not save." }
                reply.changed -> reply.message
                else -> ""
            }
        mutable.update { ui ->
            ui.copy(
                pending = if (set == null) ui.pending else ui.pending - set,
                feedback =
                    if (message.isEmpty() && reply.celebrations.isEmpty()) ui.feedback
                    else PlanFeedback(++feedbacks, message, reply.celebrations),
            )
        }
    }

    /** The screen has shown [id]'s message and celebration. */
    fun feedbackShown(id: Long) {
        mutable.update { if (it.feedback?.id == id) it.copy(feedback = null) else it }
    }

    private class Request(val set: SetRef, val sentAt: Long)

    private companion object {
        /** How long a box waits for its answer (the page takes well under a second). */
        const val ANSWER_WAIT_MS = 5_000L
    }
}

/**
 * [selected] is the screen's day (null until the page's is adopted); [pending] holds the state
 * asked for each box the page has not answered yet.
 */
data class PlanUiState(
    val selected: String? = null,
    val pending: Map<SetRef, Boolean> = emptyMap(),
    val feedback: PlanFeedback? = null,
) {
    fun day(p: PlanProjection): PlanDay = selected?.let(p::day) ?: p.selectedDay
}

/** What the page said about a toggle: its message, and the XP it awarded, held for the screen. */
data class PlanFeedback(val id: Long, val message: String, val awards: List<Celebration>)
