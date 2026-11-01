package com.lifemax.rpg.feature.settings.presentation

import androidx.lifecycle.ViewModel
import com.lifemax.rpg.feature.settings.domain.ControlOp
import com.lifemax.rpg.feature.settings.domain.SettingsCommand
import com.lifemax.rpg.feature.settings.domain.SettingsCommands
import com.lifemax.rpg.feature.settings.domain.SettingsControl
import com.lifemax.rpg.feature.settings.domain.SettingsPageAction
import com.lifemax.rpg.feature.settings.domain.SettingsProjection
import com.lifemax.rpg.feature.settings.domain.SettingsReply
import com.lifemax.rpg.feature.settings.domain.SettingsRepository
import java.util.UUID
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The screen's side of the page's Settings. It applies no rule of its own: a tap becomes the
 * command the projection names for that control, or a reveal, and what the page answers is drawn. A
 * switch, select or choice shows the state asked for, marked pending, until the page answers; the
 * answer comes with the fresh projection, which is then the truth. A chip is selected at once and
 * the page told. The page's toast ([SettingsProjection.notice], each id once) and every message in
 * an answer are shown as the page's toast.
 *
 * What closes the screen (a reveal, the classic page) goes out through [actions], which the screen
 * hands to the page; after one, the screen takes no tap until the page opens it again, so a second
 * quick tap cannot follow the first to the page.
 */
class SettingsViewModel(
    repository: SettingsRepository,
    private val commands: SettingsCommands,
    private val newRequestId: () -> String = { UUID.randomUUID().toString() },
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 },
) : ViewModel() {
    val projection = repository.state
    private val mutable = MutableStateFlow(SettingsUi())
    val ui = mutable.asStateFlow()
    private val leaving = Channel<SettingsPageAction>(Channel.UNLIMITED)

    /** What the page must do after the screen has closed, in the order asked. */
    val actions: ReceiveChannel<SettingsPageAction> = leaving
    private val requests = mutableMapOf<String, Request>()
    private var noticed: Pair<String, Long>? = null
    private var toasts = 0L
    private var left = false

    /**
     * The page opened the screen afresh. Answers to commands sent before are not coming (the page
     * answers only while the screen is open), so the projection alone says what the page holds; the
     * chip is the page's again, and an action of the last opening not yet handed over is dropped. A
     * toast cut short by the screen closing is not shown again.
     */
    fun onOpen() {
        requests.clear()
        while (leaving.tryReceive().isSuccess) Unit
        left = false
        mutable.update { SettingsUi() }
        noticeFrom(projection.value)
    }

    /**
     * The page sent a projection. An answer lost on the way (A14: its outcome is unknown) would
     * leave its control waiting for good; the projection says what the page holds, so a control
     * asked about [ANSWER_WAIT_MS] or longer ago shows that again and takes taps.
     */
    fun onUpdate() {
        val asked = nowMs() - ANSWER_WAIT_MS
        val lost = requests.filterValues { it.sentAt <= asked }
        requests -= lost.keys
        if (lost.isNotEmpty())
            mutable.update { ui ->
                ui.copy(
                    pending = ui.pending - lost.values.map { it.key }.toSet(),
                    category = ui.category?.takeIf { it.requestId !in lost },
                )
            }
        noticeFrom(projection.value)
    }

    /** A tap on a switch: turned the other way by the page, or shown by it when it asks first. */
    fun toggle(control: SettingsControl.Switch) {
        if (!takesTap(control)) return
        when (control.op) {
            ControlOp.Toggle ->
                send(
                    SettingsCommand.Toggle(control.id, !control.on),
                    control.id,
                    Asked.On(!control.on),
                )
            ControlOp.Reveal -> leave(SettingsPageAction.Reveal(control.id))
            else -> {}
        }
    }

    /** [value] picked from a select's or the haptic strength's options. */
    fun choose(control: SettingsControl, value: String) {
        if (!takesTap(control)) return
        val (current, options) =
            when (control) {
                is SettingsControl.Select -> control.value to control.options
                is SettingsControl.Choice -> control.value to control.options
                else -> return
            }
        when (control.op) {
            ControlOp.Choose ->
                if (value != current && options.any { it.value == value })
                    send(SettingsCommand.Choose(control.id, value), control.id, Asked.Value(value))
            ControlOp.Reveal -> leave(SettingsPageAction.Reveal(control.id))
            else -> {}
        }
    }

    /** A tap on a button: pressed on the page while the screen stays, or shown by the page. */
    fun press(control: SettingsControl.Button) {
        if (!takesTap(control)) return
        when (control.op) {
            ControlOp.Press -> send(SettingsCommand.Press(control.id), control.id, Asked.Press)
            ControlOp.Reveal -> leave(SettingsPageAction.Reveal(control.id))
            else -> {}
        }
    }

    /** A control the page shows for the owner to use: a field, the profile picture, a select. */
    fun reveal(control: SettingsControl) {
        if (takesTap(control) && control.op == ControlOp.Reveal)
            leave(SettingsPageAction.Reveal(control.id))
    }

    /** A chip: selected on the screen at once, then on the page, which keeps it. */
    fun selectCategory(id: String) {
        val p = projection.value ?: return
        if (left || p.categories.none { it.id == id } || id == mutable.value.category(p)) return
        val requestId = newRequestId()
        requests[requestId] = Request(CATEGORY, nowMs())
        mutable.update { it.copy(category = ChosenCategory(id, requestId)) }
        commands.send(SettingsCommand.Category(id), requestId)
    }

    /** The classic page, which stays until the owner leaves Settings. */
    fun classic() {
        if (!left) leave(SettingsPageAction.Classic)
    }

    fun onReply(reply: SettingsReply) {
        val request = requests.remove(reply.requestId)
        mutable.update { ui ->
            ui.copy(
                pending = if (request == null) ui.pending else ui.pending - request.key,
                category = ui.category?.takeIf { it.requestId != reply.requestId },
                toast =
                    if (reply.message.isEmpty()) ui.toast
                    else SettingsToast(++toasts, reply.message),
            )
        }
        if (!left) reply.reveal?.let { leave(SettingsPageAction.Reveal(it)) }
    }

    /** The screen has shown toast [id] to the end. */
    fun toastShown(id: Long) {
        mutable.update { if (it.toast?.id == id) it.copy(toast = null) else it }
    }

    private fun takesTap(control: SettingsControl) =
        !left && control.enabled && control.id !in mutable.value.pending

    private fun send(command: SettingsCommand, key: String, asked: Asked) {
        val requestId = newRequestId()
        requests[requestId] = Request(key, nowMs())
        mutable.update { it.copy(pending = it.pending + (key to asked)) }
        commands.send(command, requestId)
    }

    private fun leave(action: SettingsPageAction) {
        left = true
        leaving.trySend(action)
    }

    private fun noticeFrom(p: SettingsProjection?) {
        val notice = p?.notice ?: return
        val seen = p.session to notice.id
        if (seen == noticed) return
        noticed = seen
        mutable.update { it.copy(toast = SettingsToast(++toasts, notice.text)) }
    }

    private class Request(val key: String, val sentAt: Long)

    private companion object {
        /** How long a control waits for its answer (the page takes well under a second). */
        const val ANSWER_WAIT_MS = 5_000L
        const val CATEGORY = "\u0000category"
    }
}

/** What a control has asked the page for and not yet been answered. */
sealed interface Asked {
    data class On(val on: Boolean) : Asked

    data class Value(val value: String) : Asked

    data object Press : Asked
}

/** A chip selected on the screen, until the page has answered [requestId]. */
data class ChosenCategory(val id: String, val requestId: String)

/** A message shown as the page's toast; [id] tells one showing of it from the next. */
data class SettingsToast(val id: Long, val text: String)

/**
 * [category] is the chip chosen on the screen and not yet answered; [pending] holds what each
 * control has asked the page for; [toast] is the message showing.
 */
data class SettingsUi(
    val category: ChosenCategory? = null,
    val pending: Map<String, Asked> = emptyMap(),
    val toast: SettingsToast? = null,
) {
    /** The chip to show selected: the one chosen here while it is a chip, or the page's. */
    fun category(p: SettingsProjection): String =
        category?.id?.takeIf { id -> p.categories.any { it.id == id } } ?: p.category

    /** [control] as the screen shows it: the state asked for while the page has not answered. */
    fun on(control: SettingsControl.Switch): Boolean =
        (pending[control.id] as? Asked.On)?.on ?: control.on

    /** The value a select or choice shows: the one asked for while the page has not answered. */
    fun value(control: SettingsControl): String =
        (pending[control.id] as? Asked.Value)?.value
            ?: when (control) {
                is SettingsControl.Select -> control.value
                is SettingsControl.Choice -> control.value
                else -> ""
            }

    fun pending(control: SettingsControl): Boolean = control.id in pending
}
