package com.lifemax.rpg.feature.journal.presentation

import androidx.lifecycle.ViewModel
import com.lifemax.rpg.feature.journal.domain.JournalAction
import com.lifemax.rpg.feature.journal.domain.JournalCommand
import com.lifemax.rpg.feature.journal.domain.JournalCommands
import com.lifemax.rpg.feature.journal.domain.JournalFrame
import com.lifemax.rpg.feature.journal.domain.JournalReply
import com.lifemax.rpg.feature.journal.domain.JournalRepository
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The screen's side of the page's Journal. It applies no rule of the page's; it keeps only what a
 * screen must answer at once:
 * - **The highlight.** A tapped tile is highlighted at once and the page told (`select`); once the
 *   page's answer lands, the highlight follows the page's `selection` again.
 * - **Unlock.** Sent once; the card's button waits while it is in flight or the prompt is up.
 * - **The toast.** The page's toast (each one once per page session, in withheld frames too) and a
 *   refusal's message.
 * - **Hand-overs.** What the page answers by closing the screen (`write`, a claim, the catch-up,
 *   another view) goes once per opening, so a second quick tap cannot follow the first.
 */
class JournalViewModel(
    private val repository: JournalRepository,
    commands: JournalCommands,
    newRequestId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    private val mutable = MutableStateFlow(JournalUi())
    val ui = mutable.asStateFlow()
    private val inFlight = JournalCommandController(commands, newRequestId)
    private var tapped: Pair<String, String>? = null // the highlighted key and its select's request
    private var noticed: Pair<String, Long>? = null
    private var toasts = 0L
    private var handedOver = false

    /** The page opened the screen (again): nothing is in flight, and it takes taps. */
    fun opened() {
        inFlight.reset()
        tapped = null
        handedOver = false
        mutable.update { JournalUi() }
    }

    /** The repository has taken a frame (or locked or rested on its own). */
    fun onProjection() {
        val frame = repository.state.value
        val open = frame as? JournalFrame.Open
        if (tapped != null && (open == null || open.journal.selection == tapped?.first))
            tapped = null
        val notice =
            when (frame) {
                is JournalFrame.Open -> frame.notice
                is JournalFrame.Locked -> frame.notice
                else -> null
            }
        val toast =
            notice?.let {
                val seen = frame!!.chrome.session to it.id
                if (seen == noticed) null
                else {
                    noticed = seen
                    JournalToast(++toasts, it.text)
                }
            }
        mutable.update {
            it.copy(
                frame = frame,
                selection = tapped?.first ?: open?.journal?.selection,
                toast = toast ?: it.toast.takeIf { frame !is JournalFrame.Resting },
                unlocking = it.unlocking && frame is JournalFrame.Locked,
            )
        }
    }

    /** The screen stopped being drawn: nothing of the last showing is kept. */
    fun rested() {
        inFlight.reset()
        tapped = null
        onProjection()
        mutable.update { it.copy(toast = null, unlocking = false) }
    }

    /** A tile: highlighted now, decided and drawn by the page. */
    fun select(key: String) {
        val open = mutable.value.frame as? JournalFrame.Open ?: return
        if (key !in open.journal.list.keys || key == mutable.value.selection) return
        tapped = key to inFlight.send(JournalCommand.Select(key))
        mutable.update { it.copy(selection = key) }
    }

    /** The reflection box or a review's answer box: written in the page's field (phase 1). */
    fun write(key: String) {
        val open = mutable.value.frame as? JournalFrame.Open ?: return
        if (open.journal.entry?.key != key || !handOver()) return
        inFlight.send(JournalCommand.Write(key))
    }

    /** ↻ on day [d]'s question [from]. */
    fun swap(d: String, from: String) {
        if (mutable.value.frame !is JournalFrame.Open) return
        if (inFlight.waiting { it is JournalCommand.Swap }) return
        inFlight.send(JournalCommand.Swap(d, from))
    }

    fun unlock() {
        val locked = mutable.value.frame as? JournalFrame.Locked ?: return
        if (locked.status.prompting || mutable.value.unlocking) return
        inFlight.send(JournalCommand.Unlock)
        mutable.update { it.copy(unlocking = true) }
    }

    fun backup() {
        if (mutable.value.frame !is JournalFrame.Open) return
        if (!inFlight.waiting { it == JournalCommand.Backup }) inFlight.send(JournalCommand.Backup)
    }

    fun dismissTip() {
        val open = mutable.value.frame as? JournalFrame.Open ?: return
        if (open.journal.tip != null && !inFlight.waiting { it == JournalCommand.DismissTip })
            inFlight.send(JournalCommand.DismissTip)
    }

    /**
     * Whether [action] may go to the page, which closes the screen to answer it: once per opening.
     * The switch's other pane needs no frame; everything else needs the open Journal.
     */
    fun handOver(action: JournalAction): Boolean {
        val frame = mutable.value.frame ?: return false
        if (action !is JournalAction.OpenView && frame !is JournalFrame.Open) return false
        return handOver()
    }

    fun onReply(reply: JournalReply) {
        val command = inFlight.answered(reply) ?: return
        if (tapped?.second == reply.requestId) tapped = null
        val open = mutable.value.frame as? JournalFrame.Open
        mutable.update {
            it.copy(
                selection =
                    if (command is JournalCommand.Select) tapped?.first ?: open?.journal?.selection
                    else it.selection,
                unlocking = it.unlocking && command != JournalCommand.Unlock,
                toast =
                    if (!reply.ok && reply.message.isNotEmpty())
                        JournalToast(++toasts, reply.message)
                    else it.toast,
            )
        }
    }

    /** The screen has shown toast [id] to the end. */
    fun toastShown(id: Long) {
        mutable.update { if (it.toast?.id == id) it.copy(toast = null) else it }
    }

    private fun handOver(): Boolean {
        if (handedOver) return false
        handedOver = true
        return true
    }
}

/** A toast the screen shows: the page's notice, or a refusal's message. */
data class JournalToast(val id: Long, val text: String)

/**
 * What the screen draws: the frame, the highlighted key (the tapped one until the page answers,
 * then the page's), the toast, and whether an unlock is in flight.
 */
data class JournalUi(
    val frame: JournalFrame? = null,
    val selection: String? = null,
    val toast: JournalToast? = null,
    val unlocking: Boolean = false,
)
