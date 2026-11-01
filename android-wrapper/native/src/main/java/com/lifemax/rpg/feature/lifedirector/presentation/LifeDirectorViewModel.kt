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
import com.lifemax.rpg.feature.lifedirector.domain.OpenSheet
import com.lifemax.rpg.feature.lifedirector.domain.Sections
import com.lifemax.rpg.feature.lifedirector.domain.SessionSheetModel
import com.lifemax.rpg.feature.lifedirector.domain.SheetKinds
import com.lifemax.rpg.feature.lifedirector.domain.SheetRowButton
import com.lifemax.rpg.feature.lifedirector.domain.Tap
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
    private val sheets = SheetController(commands, projection, mutableUi, ::show)
    /** Move, Something came up and the banner's answers (phase 4A) */
    internal val moves = MoveDisruptController(sheets, projection, ui) { blocked }
    private var handedOver = false
    private var dayRequest: String? = null
    private var sectionRequest: String? = null
    /** the guides started by themselves since the plugin started: each starts by itself once */
    private val autoGuides = HashSet<String>()
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
        changeRequest = null
        letGo()
        sheets.reset()
        mutableUi.update { it.copy(pendingDay = null, pendingSection = null, pendingChange = null) }
    }

    /**
     * The screen went behind the page. A held or pending handle goes back and the confirmation
     * closes: the page holds nothing, and no reply reaches a hidden screen.
     */
    fun hidden() {
        letGo()
        // closed, not seen: it may start by itself again when the screen comes back (A24); the page
        // starts none of its own
        ui.value.guide?.let { autoGuides.remove(it.key) }
        // sheets close too (no reply reaches a hidden screen); the restore draft, the minutes typed
        // and the operation ids of Today's writes stay
        sheets.reset()
        mutableUi.update { it.copy(guide = null, confirm = null, restoring = false) }
    }

    /** A new projection arrived: a guide whose section has gone ends. */
    fun projected() {
        guideStillShown()
        sheets.projected()
    }

    /** A handle is held or the page is asked about one: the rest of the screen waits. */
    val busy: Boolean
        get() = blocked || ui.value.sheet != null

    /** A handle, a guide or a sheet of Goals and Settings holds the screen (not Today's sheet). */
    private val blocked: Boolean
        get() =
            ui.value.hold != null ||
                ui.value.ask != null ||
                ui.value.guide != null ||
                ui.value.confirm != null ||
                ui.value.restoring

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

    // Goals and Settings (phase 3B)

    private var changeRequest: String? = null

    /**
     * A change to a goal or a setting: sent to the page, one at a time (the control shows it
     * pending); the page re-plans or saves and the projection that comes with the reply shows the
     * result. A refused one shows the page's words.
     */
    fun change(name: String, args: Map<String, Any> = emptyMap()) {
        if (busy || ui.value.pendingChange != null) return
        mutableUi.update { it.copy(pendingChange = name) }
        changeRequest = commands.change(name, args)
    }

    /** Remove [goalId], once the player has said yes to the page's question. */
    fun askRemoveGoal(goalId: String) {
        val g = projection.value?.goals ?: return
        if (busy) return
        val card = g.cards.firstOrNull { it.id == goalId } ?: return
        mutableUi.update {
            it.copy(
                confirm =
                    ChangeConfirm(
                        card.title,
                        g.removeConfirm,
                        card.remove,
                        "goalRemove",
                        mapOf("id" to goalId),
                    )
            )
        }
    }

    /** Reset Life Director, once the player has said yes to the page's question. */
    fun askReset() {
        val d = projection.value?.settings?.data ?: return
        if (busy) return
        mutableUi.update {
            it.copy(confirm = ChangeConfirm(d.reset, d.resetConfirm, d.reset, "reset", emptyMap()))
        }
    }

    fun confirmChange() {
        val c = ui.value.confirm ?: return
        mutableUi.update { it.copy(confirm = null) }
        change(c.name, c.args)
    }

    fun cancelChange() = mutableUi.update { it.copy(confirm = null) }

    /**
     * Restore backup: the sheet with a field to paste into; its draft stays while the screen hides
     * (A24 drafts).
     */
    fun openRestore() {
        if (busy) return
        mutableUi.update { it.copy(restoring = true) }
    }

    fun restoreDraft(text: String) = mutableUi.update { it.copy(restoreDraft = text) }

    fun closeRestore() = mutableUi.update { it.copy(restoring = false) }

    fun submitRestore() {
        val text = ui.value.restoreDraft.trim()
        if (text.isEmpty()) return
        mutableUi.update { it.copy(restoring = false) }
        if (ui.value.pendingChange != null) return
        mutableUi.update { it.copy(pendingChange = "importBackup") }
        changeRequest = commands.change("importBackup", mapOf("text" to text))
    }

    /** The backup's text reached the clipboard (or could not); the screen says which. */
    fun copied(ok: Boolean) {
        mutableUi.update { it.copy(copyText = null) }
        show(if (ok) "Backup copied to clipboard" else "Could not copy the backup.")
    }

    private fun changed(reply: DirectorReply) {
        changeRequest = null
        val name = ui.value.pendingChange
        mutableUi.update { it.copy(pendingChange = null) }
        when {
            !reply.ok -> {
                show(reply.message.ifBlank { "Nothing was changed." })
                if (name == "importBackup")
                    mutableUi.update {
                        it.copy(restoring = true)
                    } // the draft is still there to fix
            }
            name == "exportBackup" -> mutableUi.update { it.copy(copyText = reply.text) }
            name == "importBackup" -> {
                mutableUi.update { it.copy(restoreDraft = "") }
                show(reply.message.ifBlank { "Backup restored" })
            }
        }
    }

    // The guide (coach marks)

    /**
     * A guide whose section has gone (another section, a guide no longer sent) ends; it starts
     * again from ?.
     */
    private fun guideStillShown() {
        val run = ui.value.guide ?: return
        val p = projection.value
        val g = p?.guide
        if (g == null || g.key != run.key || p.tab != run.key || run.step > g.steps.lastIndex)
            mutableUi.update { it.copy(guide = null) }
    }

    /**
     * The section's guide, if the screen draws it: from the ? button, or by itself once when it is
     * not seen yet.
     */
    fun startGuide(auto: Boolean = false) {
        val g = projection.value?.guide ?: return
        if (busy || ui.value.guide != null || g.key != projection.value?.tab) return
        if (auto && (!g.auto || !autoGuides.add(g.key))) return
        mutableUi.update { it.copy(guide = GuideRun(g.key, 0)) }
    }

    /** Next, or the last step's Got it (the guide is finished and the page records it). */
    fun guideNext() {
        val run = ui.value.guide ?: return
        val g = projection.value?.guide?.takeIf { it.key == run.key }
        if (g == null || run.step >= g.steps.lastIndex) endGuide(finished = true)
        else mutableUi.update { it.copy(guide = run.copy(step = run.step + 1)) }
    }

    fun guideBack() =
        mutableUi.update { u ->
            u.guide?.takeIf { it.step > 0 }?.let { u.copy(guide = it.copy(step = it.step - 1)) }
                ?: u
        }

    /** Skip guide, or Android Back: ended unfinished, and the page says how to see it again. */
    fun guideSkip() = endGuide(finished = false)

    private fun endGuide(finished: Boolean) {
        val run = ui.value.guide ?: return
        mutableUi.update { it.copy(guide = null) }
        commands.tourSeen(run.key, finished)
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
        if (sheets.onReply(reply)) return
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
            "do" -> if (reply.requestId == changeRequest) changed(reply)
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

    // Today's sheets and actions (phase 4A)

    /** Whether the page draws Today's sheets and runs Today's actions for this screen. */
    val sheetsAvailable: Boolean
        get() = sheets.available

    /** The session sheet on show, once the page has sent its model. */
    val session: SessionSheetModel?
        get() = sheets.session

    /** A session's sheet: asked of the page, which sends its model with the reply. */
    fun openSession(id: String) {
        if (busy || !sheets.available || id.isEmpty()) return
        sheets.open(SheetKinds.SESSION, id)
    }

    fun closeSheet() = sheets.close()

    fun sessionMinutes(text: String) = sheets.minutes(text)

    fun sessionStep(delta: Int) = sheets.session?.let { sheets.step(it, delta) }

    /** The operation id the next [command] on [target] carries: kept until a terminal reply. */
    fun operation(command: String, target: String) = sheets.operation(command, target)

    /** Start a session's timer (up next's Start, the centre's Start). */
    fun startTimer(id: String) = today("timer", id, mapOf("act" to "start"), fromSheet = false)

    /** Mark a session done as planned (up next's Done). */
    fun doneQuick(id: String) = today("done", id, emptyMap(), fromSheet = false)

    /** The sheet's main button: start the timer, or a tap for the page (its timer, for now). */
    fun sessionPrimary(): Tap? {
        val s = sheets.session ?: return null
        val p = s.primary ?: return null
        if (p.command == "timer" && p.act != null)
            today("timer", s.id, mapOf("act" to p.act), fromSheet = true)
        return if (p.sheet == SheetKinds.TIMER) Tap("focusOpen") else null
    }

    /** Mark done, or Log it for today, with the minutes in the field. */
    fun sessionComplete() {
        val s = sheets.session ?: return
        val c = s.complete ?: return
        today(c.command, s.id, mapOf("minutes" to sheets.minutesOf(s)), fromSheet = true)
    }

    /**
     * A button of the sheet's row: pin, skip and Move here, or a tap for the page (Open in Plan).
     */
    fun sessionRow(b: SheetRowButton): Tap? {
        val s = sheets.session ?: return null
        when (b.command) {
            "pin" -> today("pin", s.id, mapOf("on" to (b.on == true)), fromSheet = true)
            "skip" -> today("skip", s.id, emptyMap(), fromSheet = true)
            "openPlan" -> return Tap("openPlan", b.key.orEmpty())
        }
        if (b.sheet == SheetKinds.MOVE) moves.openMove(s.id)
        return null
    }

    /** A tap from the sheet the page answers: the sheet closes first, as the screen does. */
    fun handOverFromSheet(): Boolean {
        if (blocked || handedOver) return false
        sheets.close()
        return handOver()
    }

    private fun today(name: String, id: String, args: Map<String, Any>, fromSheet: Boolean) {
        if (blocked || !sheets.available || (!fromSheet && ui.value.sheet != null)) return
        sheets.today(name, id, args, fromSheet)
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

/** A change that asks first: the page's question, its button, and the change it then sends. */
data class ChangeConfirm(
    val title: String,
    val text: String,
    val button: String,
    val name: String,
    val args: Map<String, Any>,
)

/** A guide being shown: which, and its step. */
data class GuideRun(val key: String, val step: Int)

data class DirectorUi(
    val pendingDay: Int? = null,
    /** the section asked for, shown as chosen until the page answers */
    val pendingSection: String? = null,
    val guide: GuideRun? = null,
    /** the change sent to the page and not answered yet */
    val pendingChange: String? = null,
    val confirm: ChangeConfirm? = null,
    val restoring: Boolean = false,
    val restoreDraft: String = "",
    /** the backup the page answered with, for the screen to put on the clipboard */
    val copyText: String? = null,
    val earlierOpen: Boolean = false,
    val toast: DirectorToast? = null,
    val hold: DialHold? = null,
    val ask: DialAsk? = null,
    /** Today's sheet the screen asked for (phase 4A), shown once the page sends its model */
    val sheet: OpenSheet? = null,
    /** the minutes typed in a session sheet's field */
    val minutesDraft: MinutesDraft? = null,
    /** Today's write sent to the page and not answered yet ("done", "timer.start", ...) */
    val pendingToday: String? = null,
)
