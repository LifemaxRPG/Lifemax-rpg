package com.lifemax.rpg.feature.lifedirector.presentation.drag

import com.lifemax.rpg.feature.lifedirector.domain.DirectorReply
import com.lifemax.rpg.feature.lifedirector.domain.DragEnd
import com.lifemax.rpg.feature.lifedirector.domain.DragModel
import com.lifemax.rpg.feature.lifedirector.domain.DragPoint
import com.lifemax.rpg.feature.lifedirector.domain.DragTarget
import com.lifemax.rpg.feature.lifedirector.domain.LifeDirectorCommands
import com.lifemax.rpg.feature.lifedirector.domain.LifeDirectorProjection
import com.lifemax.rpg.feature.lifedirector.domain.Lifted
import com.lifemax.rpg.feature.lifedirector.domain.OrderRow
import com.lifemax.rpg.feature.lifedirector.presentation.DirectorUi
import com.lifemax.rpg.feature.lifedirector.presentation.UndoOffer
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Where a lifted session is in its journey. */
enum class DragPhase {
    /** the finger holds it */
    HELD,
    /** let go over the trash: it shrinks into it while the page is asked to take it off the day */
    DROPPING,
    /** let go elsewhere (or the removal was refused): it springs back to its place */
    RETURNING,
}

/** The session that is lifted: its row, where in the row the finger took hold, and its phase. */
data class Held(val row: OrderRow, val grab: DragPoint, val phase: DragPhase)

/** What a finger that has stayed on a row should do. */
sealed interface Press {
    /** a block that is not a session, a page that does not offer removal, or the screen busy */
    data object Ignore : Press

    /** a session that cannot be taken off now: the "not now" buzz, and why */
    data class Refuse(val reason: String) : Press

    data class Lift(val row: OrderRow) : Press
}

/**
 * Taking a session off the day (director.remove.v1): the lifted row on its way to the trash, the
 * removal itself and Undo. A removal is the page's own Skip, sent as Today's `skip` with an
 * operation id; Undo is `unskip`. Both are repeat-safe on the page (a repeat answers ok,
 * unchanged), and an operation id is kept until a terminal reply, so a request whose answer was
 * lost goes again with the same id. One request at a time. The page decides everything: what may be
 * removed comes on the row ([OrderRow.removal]), and what removing does is its reply.
 *
 * The finger's position and the trash being armed change many times a second, so they are flows of
 * their own; [held] changes only when a session is lifted, dropped or put back.
 */
class DragRemoveController
internal constructor(
    private val commands: LifeDirectorCommands,
    private val projection: StateFlow<LifeDirectorProjection?>,
    private val ui: MutableStateFlow<DirectorUi>,
    /** whether nothing else holds the screen (a sheet, a guide, a handle) */
    private val free: () -> Boolean,
    private val show: (String) -> Unit,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val mutableHeld = MutableStateFlow<Held?>(null)
    val held = mutableHeld.asStateFlow()
    private val mutableFinger = MutableStateFlow(DragPoint(0f, 0f))
    val finger = mutableFinger.asStateFlow()
    private val mutableArmed = MutableStateFlow(false)
    val armed = mutableArmed.asStateFlow()
    private val mutableTarget = MutableStateFlow<DragTarget?>(null)
    val target = mutableTarget.asStateFlow()

    private var lifted: Lifted? = null
    private var density = 1f
    private var scrollTop = 0f
    private var scrollBottom = 0f
    private var serial = 0
    private var pending: Pending? = null
    private val ops = HashMap<Pair<String, String>, String>()

    private data class Pending(
        val request: String,
        val command: String,
        val id: String,
        /** the offer Undo was asked from, to put back if the take-back was not saved */
        val offer: UndoOffer?,
    )

    /**
     * Where the lifted row's card, the trash and the scrolling edges are, once the screen is
     * measured.
     */
    fun layout(width: Float, height: Float, topInset: Float, bottomInset: Float, density: Float) {
        this.density = density
        val trash = DragModel.target(width, height, bottomInset, density)
        mutableTarget.value = trash
        scrollTop = topInset
        scrollBottom = trash.centre.y - trash.radius
    }

    /** The row with this list [key], from the page's latest Today. */
    fun row(key: Any?): OrderRow? {
        val today = projection.value?.today ?: return null
        return (today.ahead + today.earlier?.rows.orEmpty()).firstOrNull { it.key == key }
    }

    /** What a finger that has stayed on the row with this list [key] means. */
    fun press(key: Any?): Press {
        val row = row(key) ?: return Press.Ignore
        val removal = row.removal ?: return Press.Ignore
        if (!free() || pending != null || mutableHeld.value != null) return Press.Ignore
        return if (removal.ok) Press.Lift(row) else Press.Refuse(removal.reason)
    }

    /** A session that cannot be taken off: why, in the page's words. */
    fun refuse(reason: String) = show(reason)

    /** [row] is lifted by a finger at [finger], having taken hold [grab] into it. */
    fun lift(row: OrderRow, finger: DragPoint, grab: DragPoint) {
        val l = DragModel.lift(row, finger, grab)
        lifted = l
        mutableFinger.value = finger
        mutableArmed.value = false
        mutableHeld.value = Held(row, grab, DragPhase.HELD)
    }

    /** The finger moved to [to]; true when that armed or disarmed the trash. */
    fun move(to: DragPoint): Boolean {
        val l = lifted ?: return false
        val trash = mutableTarget.value ?: return false
        val next = DragModel.move(l, to, trash, density)
        lifted = next
        mutableFinger.value = to
        val flipped = next.armed != l.armed
        if (flipped) mutableArmed.value = next.armed
        return flipped
    }

    /** How far the list should scroll per second now, in pixels. */
    fun scrollSpeed(): Float =
        lifted?.let { DragModel.scroll(it.finger.y, scrollTop, scrollBottom, it.armed, density) }
            ?: 0f

    /** Whether a finger still holds a session. */
    val isHeld: Boolean
        get() = lifted != null

    /** The finger let go: over the trash the session is removed, anywhere else it goes back. */
    fun release() {
        val l = lifted ?: return
        lifted = null
        mutableArmed.value = false
        when (val end = DragModel.release(l)) {
            is DragEnd.Remove -> if (end.id.isEmpty()) putBack() else drop(end.id)
            DragEnd.PutBack -> putBack()
        }
    }

    /** Back, a second finger, a touch the system took, or the screen leaving: it goes back. */
    fun cancel() {
        if (lifted == null) return
        lifted = null
        mutableArmed.value = false
        DragModel.cancel()
        putBack()
    }

    private fun putBack() = mutableHeld.update { it?.copy(phase = DragPhase.RETURNING) }

    private fun drop(id: String) {
        mutableHeld.update { it?.copy(phase = DragPhase.DROPPING) }
        if (!send("skip", id, null)) putBack()
    }

    /**
     * The card has finished its way back or into the trash: the row is in the list again, or gone.
     */
    fun settled() {
        if (mutableHeld.value?.phase == DragPhase.RETURNING) mutableHeld.value = null
    }

    /** TalkBack's "Remove from today": the same removal, with no drag. */
    fun remove(row: OrderRow) {
        val id = row.tap?.id.orEmpty()
        if (id.isEmpty() || row.removal?.ok != true || !free() || pending != null) return
        send("skip", id, null)
    }

    /** Undo on the offer: the page puts the plan back as it was. */
    fun undo() {
        val offer = ui.value.undo ?: return
        if (pending != null) return
        ui.update { it.copy(undo = null) }
        send("unskip", offer.id, offer)
    }

    fun dismissUndo(serial: Int) =
        ui.update { if (it.undo?.serial == serial) it.copy(undo = null) else it }

    /** Whether [text] is what Undo's bar is saying already, so it is not also shown as a toast. */
    fun says(text: String) = ui.value.undo?.text == text

    private fun send(command: String, id: String, offer: UndoOffer?): Boolean {
        if (pending != null) return false
        val op = ops.getOrPut(command to id, newId)
        pending =
            Pending(commands.today(command, mapOf("id" to id, "op" to op)), command, id, offer)
        return true
    }

    /** Whether [reply] answered this controller's request. */
    fun onReply(reply: DirectorReply): Boolean {
        val p = pending ?: return false
        if (reply.command != "today" || reply.requestId != p.request) return false
        pending = null
        // a save the store refused keeps the change in the page's memory: the same operation lands
        // it
        val unsaved = !reply.ok && reply.message.startsWith(NOT_SAVED)
        if (!unsaved) ops.remove(p.command to p.id)
        if (p.command == "skip") skipped(p, reply, unsaved) else unskipped(p, reply, unsaved)
        return true
    }

    private fun skipped(p: Pending, reply: DirectorReply, unsaved: Boolean) {
        when {
            reply.ok -> {
                mutableHeld.value = null
                if (reply.undo.isNotEmpty())
                    ui.update {
                        it.copy(undo = UndoOffer(++serial, p.id, reply.undo), toast = null)
                    }
                else if (reply.message.isNotBlank()) show(reply.message)
            }
            // the page changed its plan in memory, and its banner says it could not save it
            unsaved -> {
                mutableHeld.value = null
                show(reply.message)
            }
            else -> {
                putBack()
                show(reply.message.ifBlank { NOTHING_CHANGED })
            }
        }
    }

    private fun unskipped(p: Pending, reply: DirectorReply, unsaved: Boolean) {
        if (unsaved) ui.update { it.copy(undo = p.offer) }
        if (reply.message.isNotBlank() || !reply.ok) show(reply.message.ifBlank { NOTHING_CHANGED })
    }

    /**
     * A new projection: a session lifted from a row that has gone, or can no longer be removed,
     * goes back.
     */
    fun projected() {
        val h = mutableHeld.value ?: return
        if (h.phase != DragPhase.HELD) return
        if (row(h.row.key)?.removal?.ok != true) cancel()
    }

    /**
     * The screen went behind the page: nothing is held and no reply is awaited; operation ids stay.
     */
    fun hidden() {
        lifted = null
        pending = null
        mutableArmed.value = false
        mutableHeld.value = null
        ui.update { it.copy(undo = null) }
    }

    private companion object {
        const val NOT_SAVED = "Not saved"
        const val NOTHING_CHANGED = "Nothing was changed."
    }
}
