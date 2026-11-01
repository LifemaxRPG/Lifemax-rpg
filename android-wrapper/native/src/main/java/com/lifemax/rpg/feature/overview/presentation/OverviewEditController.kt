package com.lifemax.rpg.feature.overview.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lifemax.rpg.feature.overview.data.OverviewCommandSender
import com.lifemax.rpg.feature.overview.domain.EditorBuzz
import com.lifemax.rpg.feature.overview.domain.EditorRow
import com.lifemax.rpg.feature.overview.domain.OverviewCards
import com.lifemax.rpg.feature.overview.domain.OverviewCommands
import com.lifemax.rpg.feature.overview.domain.OverviewLayout
import com.lifemax.rpg.feature.overview.domain.OverviewReply
import com.lifemax.rpg.feature.overview.domain.editorRows

/**
 * The native Overview editor's state (plan §4 phase 2): whether it is open, and the layout the
 * screen shows ahead of the page's answer. The layout is the page's: each change is sent whole
 * (`layout {order, hidden}`, or `layoutReset`) and stored by the page's own layout save.
 * - A change shows at once ([pending]) until the page answers the latest one.
 * - A refused answer drops it, so the rows snap back to the page's layout (the one the reply's
 *   projection carries), and the page's words for why are said ([say]).
 * - The drop, eye, Reset and TalkBack-move buzzes play only once the page has stored the change, as
 *   the page's editor buzzes after its save; the lift, crossing and open buzzes are the editor's.
 * - The screen going behind the page ends edit mode quietly (the changes are already saved), and an
 *   answer that never came is waited for no more.
 */
internal class OverviewEditController(
    private val commands: OverviewCommands,
    private val cards: () -> OverviewCards?,
    private val say: (String) -> Unit,
    private val buzz: (EditorBuzz) -> Unit,
) {
    /** Whether the editor shows in place of the tiles. */
    var editing by mutableStateOf(false)
        private set

    /** The layout sent and not yet answered, shown ahead of the page's; null when none. */
    var pending by mutableStateOf<OverviewLayout?>(null)
        private set

    /** The latest change's request: only its answer settles [pending]. */
    private var latest: String? = null
    /** Each change waiting for its answer, with the buzz it earns once stored. */
    private val waiting = HashMap<String, EditorBuzz>()

    /** The layout the editor shows: the one sent, or the page's. */
    val layout: OverviewLayout?
        get() = pending ?: cards()?.layout

    /** The editor's rows, in [layout]'s order. */
    val rows: List<EditorRow>
        get() {
            val c = cards() ?: return emptyList()
            return editorRows(pending ?: c.layout, c.tileInfo)
        }

    /** Edit: opens the editor, or closes it when open (the page's ovEditOpen does the same). */
    fun toggleEditing() {
        if (editing) close() else if (cards() != null) editing = true
    }

    /** Done or Back: the changes are already saved; answers still on their way are still taken. */
    fun close() {
        editing = false
    }

    /** A drop or a TalkBack move gave [order]; [then] is its buzz once stored. */
    fun reorder(order: List<String>, then: EditorBuzz) {
        val base = layout ?: return
        if (order == base.order) return
        send(OverviewLayout(order, base.hidden.filter { it in order }), then)
    }

    /** The eye: tile [id] hidden, or shown again. */
    fun toggle(id: String) {
        val base = layout ?: return
        if (id !in base.order) return
        val hidden = if (id in base.hidden) base.hidden - id else base.hidden + id
        send(OverviewLayout(base.order, hidden), EditorBuzz.Toggle)
    }

    /** Reset to default: the tiles in the page's default order (tileInfo's), none hidden. */
    fun reset() {
        val c = cards() ?: return
        pending = OverviewLayout(c.tileInfo.map { it.id }, emptyList())
        track(commands.layoutReset(), EditorBuzz.Reset)
    }

    /** Whether [reply] answered one of the editor's changes. */
    fun onReply(reply: OverviewReply): Boolean {
        if (
            reply.command != OverviewCommandSender.LAYOUT &&
                reply.command != OverviewCommandSender.LAYOUT_RESET
        )
            return false
        val then = waiting.remove(reply.requestId) ?: return true
        val last = reply.requestId == latest
        if (reply.ok) buzz(then)
        if (!last) return true // the latest change answers for the layout shown
        latest = null
        pending = null
        if (!reply.ok) say(reply.message.ifBlank { NOT_SAVED })
        return true
    }

    /** The screen opened again, or went behind the page: no editor, no answer awaited. */
    fun forget() {
        editing = false
        latest = null
        pending = null
        waiting.clear()
    }

    private fun send(next: OverviewLayout, then: EditorBuzz) {
        pending = next
        track(commands.layout(next.order, next.hidden), then)
    }

    private fun track(request: String, then: EditorBuzz) {
        latest = request
        waiting[request] = then
    }

    private companion object {
        const val NOT_SAVED = "Nothing was changed."
    }
}
