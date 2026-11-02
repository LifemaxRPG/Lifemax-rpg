package com.lifemax.rpg.feature.journal.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifemax.rpg.feature.journal.domain.CatchUpSentence
import com.lifemax.rpg.feature.journal.domain.CatchUpUi
import com.lifemax.rpg.feature.journal.domain.ClaimChip
import com.lifemax.rpg.feature.journal.domain.DayEntry
import com.lifemax.rpg.feature.journal.domain.JournalAction
import com.lifemax.rpg.feature.journal.domain.JournalCelebration
import com.lifemax.rpg.feature.journal.domain.JournalCommand
import com.lifemax.rpg.feature.journal.domain.JournalCommands
import com.lifemax.rpg.feature.journal.domain.JournalDraft
import com.lifemax.rpg.feature.journal.domain.JournalEntry
import com.lifemax.rpg.feature.journal.domain.JournalFrame
import com.lifemax.rpg.feature.journal.domain.JournalReply
import com.lifemax.rpg.feature.journal.domain.JournalRepository
import com.lifemax.rpg.feature.journal.domain.JournalWrite
import com.lifemax.rpg.feature.journal.domain.ReviewEntry
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The screen's side of the page's Journal. It applies no rule of the page's; it keeps only what a
 * screen must answer at once:
 * - **The highlight.** A tapped tile is highlighted at once and the page told (`select`); once the
 *   page's answer lands, the highlight follows the page's `selection` again.
 * - **Unlock.** Sent once; the card's button waits while it is in flight or the prompt is up.
 * - **The toast.** The page's toast (each one once per page session, in withheld frames too) and a
 *   refusal's message.
 * - **Hand-overs.** What the page answers by closing the screen (`write`, a claim, the catch-up,
 *   another view) goes once per opening, so a second quick tap cannot follow the first. A screen
 *   that writes natively (phase 2, `edit` "native") hands over only to another view.
 * - **Writing** (phase 2). The words typed in a notebook field are a draft ([drafts]): autosaved
 *   1.5 s after the typing stops, sent as a final write on leaving the field, the tab or the app,
 *   and kept while the page has not certainly got them. The welcome chips and the catch-up are
 *   commands; the XP a reply paid is celebrated by the screen.
 */
class JournalViewModel(
    private val repository: JournalRepository,
    commands: JournalCommands,
    schedule: ((Long, () -> Unit) -> () -> Unit)? = null,
    newRequestId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    private val mutable = MutableStateFlow(JournalUi())
    val ui = mutable.asStateFlow()
    private val inFlight = JournalCommandController(commands, newRequestId)
    private val drafts: JournalDraftController =
        JournalDraftController(
            send = inFlight::send,
            schedule =
                schedule
                    ?: { ms, run ->
                        viewModelScope
                            .launch { delay(ms).also { run() } }
                            .let { job -> { job.cancel() } }
                    },
            owes = ::owes,
            changed = { mutable.update { it.copy(draft = drafts.draft) } },
        )
    private var celebrations = 0L
    private var tapped: Pair<String, String>? = null // the highlighted key and its select's request
    private var noticed: Pair<String, Long>? = null
    private var toasts = 0L
    private var handedOver = false

    /** The page opened the screen (again): nothing is in flight, and it takes taps. */
    fun opened() {
        inFlight.reset()
        drafts.lostReply()
        tapped = null
        handedOver = false
        mutable.update { JournalUi(draft = drafts.draft) }
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
        drafts.draft?.let { drafts.page(it.key, pageText(open, it.key)) }
        mutable.update {
            it.copy(
                frame = frame,
                draft = drafts.draft,
                selection = tapped?.first ?: open?.journal?.selection,
                toast = toast ?: it.toast.takeIf { frame !is JournalFrame.Resting },
                unlocking = it.unlocking && frame is JournalFrame.Locked,
            )
        }
    }

    /** The screen stopped being drawn: nothing of the last showing is kept. */
    fun rested() {
        inFlight.reset()
        drafts.rested()
        tapped = null
        onProjection()
        mutable.update {
            it.copy(
                toast = null,
                unlocking = false,
                celebration = null,
                catchUp = CatchUpUi(),
                draft = drafts.draft,
            )
        }
    }

    /**
     * The screen is leaving (the tab closed, the app stopped): the words in the field go to the
     * page now, as the final write, before anything is dropped. Nothing else runs while hidden
     * (A24), and a celebration cut short is not replayed.
     */
    fun hidden() {
        drafts.leave()
        mutable.update { it.copy(celebration = null) }
    }

    /** A tile: highlighted now, decided and drawn by the page. */
    fun select(key: String) {
        val open = mutable.value.frame as? JournalFrame.Open ?: return
        if (key !in open.journal.list.keys || key == mutable.value.selection) return
        drafts.leave() // the entry being left keeps its words, and is paid if it is owed
        tapped = key to inFlight.send(JournalCommand.Select(key))
        mutable.update { it.copy(selection = key) }
    }

    /** The reflection box or a review's answer box: written in the page's field (phase 1). */
    fun write(key: String) {
        val open = mutable.value.frame as? JournalFrame.Open ?: return
        if (open.journal.entry?.key != key || !handOver()) return
        inFlight.send(JournalCommand.Write(key))
    }

    // ----- writing (phase 2) -----

    /** The owner typed [text] in the field of [key] (a day or a review). */
    fun edit(key: String, text: String) {
        val open = mutable.value.frame as? JournalFrame.Open ?: return
        if (open.journal.write == null) return
        val entry = open.journal.entry?.takeIf { it.key == key } ?: return
        drafts.edit(key, text, pageText(entry))
    }

    /** The field lost the focus (another control, the keyboard closed): the final write. */
    fun leaveField() = drafts.leave()

    /** A welcome chip that pays ("pet" or "fresh"). */
    fun claim(kind: String) {
        if (mutable.value.frame !is JournalFrame.Open) return
        if (inFlight.waiting { it == ClaimChip(kind) }) return
        inFlight.send(ClaimChip(kind))
    }

    /** The catch-up chip: its field opens under the welcome card. */
    fun openCatchUp() {
        if (mutable.value.frame !is JournalFrame.Open) return
        mutable.update { it.copy(catchUp = CatchUpUi(open = true)) }
    }

    fun closeCatchUp() {
        mutable.update { if (it.catchUp.sending) it else it.copy(catchUp = CatchUpUi()) }
    }

    /** OK in the catch-up's field: the page checks the sentence and answers in its words. */
    fun sendCatchUp(text: String) {
        if (mutable.value.frame !is JournalFrame.Open || mutable.value.catchUp.sending) return
        inFlight.send(CatchUpSentence(text))
        mutable.update { it.copy(catchUp = CatchUpUi(open = true, sending = true)) }
    }

    /** The celebration [id] has played to its end. */
    fun celebrated(id: Long) {
        mutable.update { if (it.celebration?.id == id) it.copy(celebration = null) else it }
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
        drafts.onReply(reply)
        val open = mutable.value.frame as? JournalFrame.Open
        val awards = reply.celebrations.filter { a -> a.gains.isNotEmpty() }
        // a refusal says why; so does a write whose words were kept but whose reward was not
        val said = reply.message.isNotEmpty() && (!reply.ok || reply.changed)
        mutable.update {
            it.copy(
                draft = drafts.draft,
                celebration =
                    if (awards.isEmpty()) it.celebration
                    else JournalCelebration(++celebrations, awards),
                catchUp =
                    if (command is CatchUpSentence)
                        if (reply.ok) CatchUpUi() else CatchUpUi(open = true)
                    else it.catchUp,
                selection =
                    if (command is JournalCommand.Select) tapped?.first ?: open?.journal?.selection
                    else it.selection,
                unlocking = it.unlocking && command != JournalCommand.Unlock,
                toast = if (said) JournalToast(++toasts, reply.message) else it.toast,
            )
        }
    }

    /** The screen has shown toast [id] to the end. */
    fun toastShown(id: Long) {
        mutable.update { if (it.toast?.id == id) it.copy(toast = null) else it }
    }

    /** The words [key]'s entry holds on the page, or null when it is not the entry shown. */
    private fun pageText(open: JournalFrame.Open?, key: String): String? =
        open?.journal?.entry?.takeIf { it.key == key }?.let(::pageText)

    private fun pageText(entry: JournalEntry): String =
        when (entry) {
            is DayEntry -> entry.reflect.text
            is ReviewEntry -> entry.answer
        }

    /**
     * Whether a final write could still pay for [text] in the entry [key]: its reward is unpaid, a
     * reflection is on a day that can still be paid, and the words are long enough (the page's
     * minimum, trimmed as the page counts). The page decides; this only avoids a write that cannot
     * pay.
     */
    private fun owes(key: String, text: String): Boolean {
        val entry = (repository.state.value as? JournalFrame.Open)?.journal?.entry
        if (entry == null || entry.key != key) return false
        val n = text.trim().length
        return when (entry) {
            is DayEntry -> !entry.reflect.awarded && entry.reflect.canPay && n >= entry.reflect.min
            is ReviewEntry -> !entry.paid && n >= entry.min
        }
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
    /** Phase 2: the words being typed, the catch-up's field and the XP being celebrated. */
    val draft: JournalDraft? = null,
    val catchUp: CatchUpUi = CatchUpUi(),
    val celebration: JournalCelebration? = null,
) {
    /** The write words of an open frame whose editor is the screen's own, else null. */
    val write: JournalWrite?
        get() = (frame as? JournalFrame.Open)?.journal?.write
}
