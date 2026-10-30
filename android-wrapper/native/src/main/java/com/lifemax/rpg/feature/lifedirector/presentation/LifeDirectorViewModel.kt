package com.lifemax.rpg.feature.lifedirector.presentation

import androidx.lifecycle.ViewModel
import com.lifemax.rpg.feature.lifedirector.domain.DirectorNotice
import com.lifemax.rpg.feature.lifedirector.domain.LifeDirectorCommands
import com.lifemax.rpg.feature.lifedirector.domain.LifeDirectorRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * What the screen keeps for itself: the day it asked for, whether "Earlier today" is open and the
 * message on show. The plan, the chosen day and every rule stay with the page. A tap the page
 * answers closes the screen, so one is handed over per opening and a second, quick tap is dropped.
 */
class LifeDirectorViewModel(
    repository: LifeDirectorRepository,
    private val commands: LifeDirectorCommands,
) : ViewModel() {
    val projection = repository.state
    private val mutableUi = MutableStateFlow(DirectorUi())
    val ui = mutableUi.asStateFlow()
    private var handedOver = false
    private var shownNotice: Long? = null
    private var toasts = 0

    /** The screen opened (again): the page shows today and takes taps. */
    fun opened() {
        handedOver = false
        mutableUi.update { it.copy(pendingDay = null) }
    }

    fun showDay(index: Int) {
        val today = projection.value?.today ?: return
        if (index !in today.strip.indices || index == (ui.value.pendingDay ?: today.dayIndex))
            return
        mutableUi.update { it.copy(pendingDay = index) }
        commands.showDay(index)
    }

    /** The page's answer to a command; it arrives with the projection it produced. */
    fun onReply(command: String, ok: Boolean, message: String) {
        if (command != "day") return
        mutableUi.update { it.copy(pendingDay = null) }
        if (!ok) show(message.ifBlank { "That day is not on the plan." })
    }

    /** Whether a tap may go to the page, which closes the screen to answer it. */
    fun handOver(): Boolean {
        if (handedOver) return false
        handedOver = true
        return true
    }

    fun notice(notice: DirectorNotice) {
        if (notice.id == shownNotice) return
        shownNotice = notice.id
        show(notice.text)
    }

    /** What fixed time says when tapped; nothing goes to the page. */
    fun tip(text: String) = show(text)

    fun toggleEarlier() = mutableUi.update { it.copy(earlierOpen = !it.earlierOpen) }

    fun dismiss(serial: Int) =
        mutableUi.update { if (it.toast?.serial == serial) it.copy(toast = null) else it }

    private fun show(text: String) =
        mutableUi.update { it.copy(toast = DirectorToast(++toasts, text)) }
}

/** A short message over the screen; [serial] tells a repeat of the same words apart. */
data class DirectorToast(val serial: Int, val text: String)

data class DirectorUi(
    val pendingDay: Int? = null,
    val earlierOpen: Boolean = false,
    val toast: DirectorToast? = null,
)
