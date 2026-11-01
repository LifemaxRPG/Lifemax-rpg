package com.lifemax.rpg.feature.lifedirector.presentation

import androidx.lifecycle.ViewModel
import com.lifemax.rpg.feature.lifedirector.domain.DialConfirm
import com.lifemax.rpg.feature.lifedirector.domain.DialHandle
import com.lifemax.rpg.feature.lifedirector.domain.DialPlace
import com.lifemax.rpg.feature.lifedirector.domain.DialStop
import com.lifemax.rpg.feature.lifedirector.domain.DirectorNotice
import com.lifemax.rpg.feature.lifedirector.domain.DirectorReply
import com.lifemax.rpg.feature.lifedirector.domain.DragWords
import com.lifemax.rpg.feature.lifedirector.domain.LifeDirectorCommands
import com.lifemax.rpg.feature.lifedirector.domain.LifeDirectorRepository
import com.lifemax.rpg.feature.lifedirector.domain.Sections
import com.lifemax.rpg.feature.lifedirector.presentation.dial.DragGeometry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * What the screen keeps for itself: the day it asked for, whether "Earlier today" is open, the
 * message on show, a handle being dragged and the change the page is asked about, and the builds
 * already played. The plan, the chosen day and every rule stay with the page. A tap the page
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
    private var dayRequest: String? = null
    private var sectionRequest: String? = null
    private var dropRequest: String? = null
    private var applyRequest: String? = null
    private var shownNotice: Pair<String, Long>? = null
    private var toasts = 0
    private val played = HashSet<Pair<String, Long>>()

    /**
     * Whether the user has touched the screen since the plugin started. A build buzzes only then,
     * as the page buzzes only after its user activation.
     */
    var touched = false
        private set

    /** The screen opened (again): the page shows its day and takes taps. */
    fun opened() {
        handedOver = false
        dayRequest = null
        sectionRequest = null
        letGo()
        mutableUi.update { it.copy(pendingDay = null, pendingSection = null) }
    }

    /**
     * The screen went behind the page. A held or pending handle goes back and the confirmation
     * closes: the page holds nothing, and no reply reaches a hidden screen.
     */
    fun hidden() = letGo()

    /** A handle is held or the page is asked about one: the rest of the screen waits. */
    val busy: Boolean
        get() = ui.value.hold != null || ui.value.ask != null

    fun showDay(index: Int) {
        val today = projection.value?.today ?: return
        if (busy || index !in today.strip.indices) return
        if (index == (ui.value.pendingDay ?: today.dayIndex)) return
        mutableUi.update { it.copy(pendingDay = index) }
        dayRequest = commands.showDay(index)
    }

    /**
     * Whether this screen draws section [id] now: the page hands it over and the screen knows how
     * to draw it.
     */
    fun draws(id: String): Boolean =
        id in Sections.DRAWN && projection.value?.covers?.contains(id) == true

    /**
     * Another section the screen draws: asked of the page, which renders it underneath too and
     * sends it with the reply; the tab shows as chosen meanwhile. A section the screen does not
     * draw is a tap the page answers (it closes the screen).
     */
    fun showSection(id: String) {
        val p = projection.value ?: return
        if (busy || !draws(id) || id == (ui.value.pendingSection ?: p.tab)) return
        mutableUi.update { it.copy(pendingSection = id) }
        sectionRequest = commands.showSection(id)
    }

    /** A day from Week: Today on that day. */
    fun gotoDay(index: Int) {
        val p = projection.value ?: return
        if (busy || !draws(Sections.TODAY) || p.week?.days?.any { it.index == index } != true)
            return
        mutableUi.update { it.copy(pendingSection = Sections.TODAY, pendingDay = index) }
        sectionRequest = commands.gotoDay(index)
    }

    /**
     * The page's answer to a command; it arrives with the projection it produced. Only the answer
     * to the latest request of its kind counts: an earlier day would move the strip back, and an
     * earlier dial answer belongs to a handle let go since.
     */
    fun onReply(reply: DirectorReply) {
        when (reply.command) {
            "day" ->
                if (reply.requestId == dayRequest) {
                    dayRequest = null
                    mutableUi.update { it.copy(pendingDay = null) }
                    if (!reply.ok) show(reply.message.ifBlank { "That day is not on the plan." })
                }
            "section",
            "gotoDay" ->
                if (reply.requestId == sectionRequest) {
                    sectionRequest = null
                    mutableUi.update { it.copy(pendingSection = null, pendingDay = null) }
                    if (!reply.ok)
                        show(reply.message.ifBlank { "That section is not in Life Director." })
                }
            "dialDrop" -> if (reply.requestId == dropRequest) dropped(reply)
            "dialApply" -> if (reply.requestId == applyRequest) applied(reply)
        }
    }

    /** Whether a tap may go to the page, which closes the screen to answer it. */
    fun handOver(): Boolean {
        if (handedOver || busy) return false
        handedOver = true
        return true
    }

    // The dial's handles

    /**
     * A finger took a handle ([hold]: on its own stop). The handle, its stops and words are kept as
     * they were taken, so a projection update cannot move it while it is held.
     */
    fun hold(hold: DialHold) {
        if (busy || !hold.held || hold.stop !in hold.stops.indices) return
        if (hold.handle.lengths.size != hold.stops.size || hold.handle.ends.size != hold.stops.size)
            return
        mutableUi.update { it.copy(hold = hold) }
    }

    /** The held handle is over stop [stop]. */
    fun move(stop: Int) =
        mutableUi.update { u ->
            val h = u.hold
            if (h == null || !h.held || stop !in h.stops.indices || stop == h.stop) u
            else u.copy(hold = h.copy(stop = stop))
        }

    /**
     * The finger let go, or the touch was [cancelled]. Cancelled, or where the handle already is:
     * it goes back and nothing is sent. Elsewhere it stays there, and the page is asked what that
     * change would mean (its confirmation).
     */
    fun release(cancelled: Boolean) {
        val h = ui.value.hold?.takeIf { it.held } ?: return
        val stop = h.stops[h.stop]
        if (cancelled || stop.m == h.handle.minute) {
            mutableUi.update { it.copy(hold = null) }
            return
        }
        ask(h.handle.key, DialPlace.Stop(stop.m), h.copy(held = false))
    }

    /** TalkBack moved [handle] a quarter hour later (1) or earlier (-1), as the arrow keys do. */
    fun step(handle: DialHandle, step: Int) {
        if (busy || (step != 1 && step != -1)) return
        ask(handle.key, DialPlace.Step(step), null)
    }

    private fun ask(key: String, place: DialPlace, hold: DialHold?) {
        mutableUi.update { it.copy(hold = hold, ask = DialAsk(key, place)) }
        dropRequest = commands.dialDrop(key, place)
    }

    /**
     * Keep, or OK after a problem: the handle goes back. The page holds nothing; nothing is sent.
     */
    fun keep() {
        if (ui.value.ask?.confirm != null) letGo()
    }

    /**
     * Change it: the page applies the same change. The confirmation closes and the handle stays
     * where it was let go until the page answers with the new plan.
     */
    fun change() {
        val ask = ui.value.ask ?: return
        val confirm = ask.confirm ?: return
        if (confirm.apply.isEmpty()) return keep()
        mutableUi.update { it.copy(ask = ask.copy(confirm = null, applying = true)) }
        applyRequest = commands.dialApply(ask.key, ask.place)
    }

    private fun dropped(reply: DirectorReply) {
        dropRequest = null
        val confirm = reply.confirm
        if (reply.ok && reply.changed && confirm != null)
            mutableUi.update { u -> u.ask?.let { u.copy(ask = it.copy(confirm = confirm)) } ?: u }
        else {
            letGo()
            if (!reply.ok) show(reply.message.ifBlank { NOTHING_CHANGED })
        }
    }

    /**
     * Saved: the page's notice comes with the projection, shown once like any other. Refused after
     * the rule changed: the projection carries the page's banner. Refused unchanged: why.
     */
    private fun applied(reply: DirectorReply) {
        applyRequest = null
        letGo()
        if (!reply.ok && !reply.changed) show(reply.message.ifBlank { NOTHING_CHANGED })
    }

    private fun letGo() {
        dropRequest = null
        applyRequest = null
        mutableUi.update { it.copy(hold = null, ask = null) }
    }

    // The build

    /** Whether build [id] of page [session] is still to play: true once per plugin lifetime. */
    fun takeBuild(session: String, id: Long): Boolean = played.add(session to id)

    /** The user touched the screen. */
    fun touch() {
        touched = true
    }

    // Messages

    /** The page's latest message, shown once. Its ids count from 1 again in a reloaded page. */
    fun notice(session: String, notice: DirectorNotice) {
        val key = session to notice.id
        if (key == shownNotice) return
        shownNotice = key
        show(notice.text)
    }

    /** What fixed time says when tapped; nothing goes to the page. */
    fun tip(text: String) = show(text)

    fun toggleEarlier() = mutableUi.update { it.copy(earlierOpen = !it.earlierOpen) }

    fun dismiss(serial: Int) =
        mutableUi.update { if (it.toast?.serial == serial) it.copy(toast = null) else it }

    private fun show(text: String) =
        mutableUi.update { it.copy(toast = DirectorToast(++toasts, text)) }

    private companion object {
        const val NOTHING_CHANGED = "Nothing was changed."
    }
}

/** A short message over the screen; [serial] tells a repeat of the same words apart. */
data class DirectorToast(val serial: Int, val text: String)

/**
 * A handle a finger holds ([held]), or has let go on stop [stop] while the page answers. It keeps
 * the handle, stops and words it was taken with, so an update cannot move it.
 */
data class DialHold(
    val handle: DialHandle,
    val stops: List<DialStop>,
    val words: DragWords,
    val stop: Int,
    val held: Boolean,
) {
    /** What the centre says with the handle here. */
    val preview
        get() = DragGeometry.preview(handle, stops, stop, words)

    /** The arc it spans while held, or null when that is too short or it is let go. */
    val span
        get() = if (held) DragGeometry.span(handle, stops, stop) else null
}

/**
 * A change the page is asked about: the handle's [key] and where it went. [confirm] is the page's
 * answer, shown until Keep or Change it; [applying] once Change it has gone to the page.
 */
data class DialAsk(
    val key: String,
    val place: DialPlace,
    val confirm: DialConfirm? = null,
    val applying: Boolean = false,
)

data class DirectorUi(
    val pendingDay: Int? = null,
    /** the section asked for, shown as chosen until the page answers */
    val pendingSection: String? = null,
    val earlierOpen: Boolean = false,
    val toast: DirectorToast? = null,
    val hold: DialHold? = null,
    val ask: DialAsk? = null,
)
